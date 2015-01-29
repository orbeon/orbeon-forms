/**
 * Copyright (C) 2011 Orbeon, Inc.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version
 * 2.1 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.fr


import java.net.URI
import javax.xml.transform.stream.StreamResult

import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.externalcontext.URLRewriter
import org.orbeon.oxf.http.Headers._
import org.orbeon.oxf.http.{Headers, StreamedContent}
import org.orbeon.oxf.pipeline.api.ExternalContext.{Request, Response}
import org.orbeon.oxf.pipeline.api.PipelineContext
import org.orbeon.oxf.processor.ProcessorImpl
import org.orbeon.oxf.processor.generator.RequestGenerator
import org.orbeon.oxf.properties.Properties
import org.orbeon.oxf.util.ScalaUtils._
import org.orbeon.oxf.util._
import org.orbeon.oxf.xforms.action.XFormsAPI
import org.orbeon.oxf.xml.TransformerUtils
import org.orbeon.scaxon.XML._

import scala.collection.JavaConverters._

/**
 * The persistence proxy processor:
 *
 * - proxies GET, PUT, DELETE and POST to the appropriate persistence implementation
 * - sets persistence implementation headers
 * - calls all active persistence implementations to aggregate form metadata
 */
class FormRunnerPersistenceProxy extends ProcessorImpl {

    private val FormPath                   = """/fr/service/persistence(/crud/([^/]+)/([^/]+)/form/([^/]+))""".r
    private val DataPath                   = """/fr/service/persistence(/crud/([^/]+)/([^/]+)/(data|draft)/([^/]+)/([^/]+))""".r
    private val DataCollectionPath         = """/fr/service/persistence(/crud/([^/]+)/([^/]+)/data/)""".r
    private val SearchPath                 = """/fr/service/persistence(/search/([^/]+)/([^/]+))""".r
    private val PublishedFormsMetadataPath = """/fr/service/persistence/form(/([^/]+)(/([^/]+))?)?""".r

    // Start the processor
    override def start(pipelineContext: PipelineContext) {
        val ec = NetUtils.getExternalContext
        proxyRequest(ec.getRequest, ec.getResponse)
    }

    // Proxy the request to the appropriate persistence implementation
    def proxyRequest(request: Request, response: Response) {
        val incomingPath = request.getRequestPath
        incomingPath match {
            case FormPath(path, app, form, _)                   ⇒ proxyRequest(request, response, app, form, "form", path)
            case DataPath(path, app, form, _, _, _)             ⇒ proxyRequest(request, response, app, form, "data", path)
            case DataCollectionPath(path, app, form)            ⇒ proxyRequest(request, response, app, form, "data", path)
            case SearchPath(path, app, form)                    ⇒ proxyRequest(request, response, app, form, "data", path)
            case PublishedFormsMetadataPath(path, app, _, form) ⇒ proxyPublishedFormsMetadata(request, response, Option(app), Option(form), path)
            case _ ⇒ throw new OXFException(s"Unsupported path: $incomingPath")
        }
    }

    // Proxy the request depending on app/form name and whether we are accessing form or data
    private def proxyRequest(
        request    : Request,
        response   : Response,
        app        : String,
        form       : String,
        formOrData : String,
        path       : String
    ): Unit = {

        def buildQueryString =
            NetUtils.encodeQueryString(request.getParameterMap)

        // Get persistence implementation target URL and configuration headers
        val (persistenceBaseURL, headers) = FormRunner.getPersistenceURLHeaders(app, form, formOrData)

        val cxr =
            proxyEstablishConnection(request, NetUtils.appendQueryString(dropTrailingSlash(persistenceBaseURL) + path, buildQueryString), headers)

        useAndClose(cxr) { cxr ⇒
            // Proxy status code
            response.setStatus(cxr.statusCode)
            // Proxy incoming headers
            cxr.content.contentType foreach (response.setHeader(Headers.ContentType, _))
            proxyCapitalizeAndCombineHeaders(cxr.headers, request = false) foreach (response.setHeader _).tupled
            copyStream(cxr.content.inputStream, response.getOutputStream)
        }
    }

    private def proxyEstablishConnection(request: Request, uri: String, headers: Map[String, String]) = {
        // Create the absolute outgoing URL
        val outgoingURL =
            new URI(URLRewriterUtils.rewriteServiceURL(NetUtils.getExternalContext.getRequest, uri, URLRewriter.REWRITE_MODE_ABSOLUTE))

        val persistenceHeaders =
            for ((name, value) ← headers)
            yield capitalizeCommonOrSplitHeader(name) → List(value)

        // Forwards all incoming headers, with exceptions like connection headers and, importantly, cookie headers
        val proxiedHeaders =
            proxyAndCapitalizeHeaders(request.getHeaderValuesMap.asScala mapValues (_.toList), request = true)

        implicit val logger = new IndentedLogger(ProcessorImpl.logger, "")

        val allHeaders =
            Connection.buildConnectionHeadersLowerIfNeeded(
                scheme           = outgoingURL.getScheme,
                hasCredentials   = false,
                customHeaders    = persistenceHeaders ++ proxiedHeaders,
                headersToForward = Set(),                                  // handled by proxyAndCapitalizeHeaders()
                cookiesToForward = Connection.cookiesToForwardFromProperty // NOT handled by proxyAndCapitalizeHeaders()
            )

        val method = request.getMethod

        if (! Set("GET", "DELETE", "PUT", "POST")(method))
            throw new OXFException(s"Unsupported method: $method")

        val requestContent =
            Set("PUT", "POST")(method) option {
                // Ask the request generator first, as the body might have been read already
                // Q: Could this be handled automatically in ExternalContext?
                val is = RequestGenerator.getRequestBody(PipelineContext.get) match {
                    case bodyURL: String ⇒ NetUtils.uriToInputStream(bodyURL)
                    case _               ⇒ request.getInputStream
                }

                StreamedContent(
                    is,
                    Option(request.getContentType),
                    Some(request.getContentLength.toLong) filter (_ >= 0L),
                    None
                )
            }

        Connection(
            httpMethodUpper = method,
            url             = outgoingURL,
            credentials     = None,
            content         = requestContent,
            headers         = allHeaders,
            loadState       = true,
            logBody         = false
        ).connect(
            saveState = true
        )
    }

    /**
     * Proxies the request to every configured persistence layer to get the list of the forms, and aggregates the
     * results. So the response is not simply proxied, unlike for other persistence layer calls.
     */
    private def proxyPublishedFormsMetadata(
        request  : Request,
        response : Response,
        app      : Option[String],
        form     : Option[String],
        path     : String
    ): Unit = {
        val propertySet = Properties.instance.getPropertySet

        val providers = {
            (app, form) match {
                case (Some(appName), Some(formName)) ⇒
                    // Get the specific provider for this app/form
                    FormRunner.findProvider(appName, formName, "form").toList
                case _ ⇒
                    // Get providers independently from app/form
                    // NOTE: Could also optimize case where only app is provided, but no callers as of 2013-10-21.
                    def providersUsedInPropertiesForFormDefinition = (
                        propertySet.propertiesStartsWith(FormRunner.PersistenceProviderPropertyPrefix, matchWildcards = false)
                        filterNot (_ endsWith ".data") // exclude `.data` to handle the case of `.*` which covers both `.data` and `.form` endings
                        map       propertySet.getString
                        distinct
                    )

                    // See https://github.com/orbeon/orbeon-forms/issues/1186
                    providersUsedInPropertiesForFormDefinition filter FormRunner.isActiveProvider
            }
        }

        val allFormElements =
            providers map
            FormRunner.getPersistenceURLHeadersFromProvider flatMap {
                case (baseURI, headers) ⇒
                    // Read all the forms for the current service
                    val serviceURI = baseURI + "/form" + Option(path).getOrElse("")
                    val cxr        = proxyEstablishConnection(request, serviceURI, headers)

                    ConnectionResult.withSuccessConnection(cxr, closeOnSuccess = true) { is ⇒
                        val forms = TransformerUtils.readTinyTree(XPath.GlobalConfiguration, is, serviceURI, false, false)
                        forms \\ "forms" \\ "form"
                    }
            }

        val filteredFormElements = FormRunner.filterFormsAndAnnotateWithOperations(allFormElements)

        // Aggregate and serialize
        val documentElement = elementInfo("forms")
        XFormsAPI.insert(into = documentElement, origin = filteredFormElements)

        response.setContentType("application/xml")
        TransformerUtils.getXMLIdentityTransformer.transform(documentElement, new StreamResult(response.getOutputStream))
    }
}