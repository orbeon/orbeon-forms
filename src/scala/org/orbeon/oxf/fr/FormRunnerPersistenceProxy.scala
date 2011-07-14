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

import org.orbeon.oxf.processor.ProcessorImpl
import org.orbeon.oxf.pipeline.api.ExternalContext.{Response, Request}
import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.pipeline.api.{ExternalContext, PipelineContext}
import org.orbeon.oxf.resources.URLFactory
import org.orbeon.oxf.util.ScalaUtils._
import scala.collection.JavaConversions._
import org.orbeon.oxf.resources.handler.HTTPURLConnection
import org.orbeon.oxf.processor.generator.RequestGenerator
import org.orbeon.oxf.properties.Properties
import org.orbeon.oxf.xml.TransformerUtils
import org.orbeon.oxf.util.{XPathCache, NetUtils}
import org.orbeon.scaxon.XML._
import javax.xml.transform.stream.StreamResult
import org.orbeon.oxf.xforms.action.XFormsAPI

/**
 * The persistence proxy processor:
 *
 * - proxies GET, PUT, DELETE and POST to the appropriate persistence implementation
 * - sets persistence implementation headers
 */
class FormRunnerPersistenceProxy extends ProcessorImpl {

    private val FormPath = """/fr/service/persistence(/crud/([^/]+)/([^/]+)/form/([^/]+))""".r
    private val DataPath = """/fr/service/persistence(/crud/([^/]+)/([^/]+)/data/([^/]+)/([^/]+))""".r
    private val DataCollectionPath = """/fr/service/persistence(/crud/([^/]+)/([^/]+)/data/)""".r
    private val SearchPath = """/fr/service/persistence(/search/([^/]+)/([^/]+))""".r
    private val FormDefinitionPath = """/fr/service/persistence/form""".r

    // Start the processor
    override def start(pipelineContext: PipelineContext) {
        val ec = NetUtils.getExternalContext
        proxyRequest(ec.getRequest, ec.getResponse)
    }

    // Proxy the request to the appropriate persistence implementation
    def proxyRequest(request: Request, response: Response) {
        val incomingPath = request.getRequestPath
        incomingPath match {
            case FormPath(path, app, form, _) => proxyRequest(request, response, app, form, "form", path)
            case DataPath(path, app, form, _, _) => proxyRequest(request, response, app, form, "data", path)
            case DataCollectionPath(path, app, form) => proxyRequest(request, response, app, form, "data", path)
            case SearchPath(path, app, form) => proxyRequest(request, response, app, form, "data", path)
            case FormDefinitionPath() => proxyFormDefinition(request, response)
            case _ => throw new OXFException("Unsupported path: " + incomingPath)
        }
    }

    private def proxyHeaders(headers: => Traversable[(String, Seq[String])], set: (String, String) => Unit, out: Boolean): Unit =
        for {
            (name, values) <- headers
            if !Set("transfer-encoding", "connection", "host")(name.toLowerCase)
            if !out || name.toLowerCase != "content-length"
            if values != null && values.nonEmpty
        } yield
            set(capitalizeHeader(name), values mkString ",")

    // Proxy the request depending on app/form name and whether we are accessing form or data
    private def proxyRequest(request: Request, response: Response, app: String, form: String, formOrData: String, path: String) {

        // Get persistence implementation target URL and configuration headers
        val (persistenceBaseURL, headers) = FormRunner.getPersistenceURLHeaders(app, form, formOrData)
        val connection = proxyEstablishConnection(request, dropTrailingSlash(persistenceBaseURL) + path, headers)
        // Proxy incoming headers
        proxyHeaders(connection.getHeaderFields map {case (name, values) => (name, values.toSeq)}, response.setHeader _, out = false)
        copyStream(connection.getInputStream, response.getOutputStream)
    }

    private def proxyEstablishConnection(request: Request, uri: String, headers: Map[String, String]) = {
        // Create the absolute outgoing URL
        val outgoingURL = {
            val persistenceBaseAbsoluteURL = NetUtils.getExternalContext.rewriteServiceURL(uri, ExternalContext.Response.REWRITE_MODE_ABSOLUTE)
            URLFactory.createURL(persistenceBaseAbsoluteURL)
        }

        def setPersistenceHeaders(connection: HTTPURLConnection) {
            for ((name, value) <- headers)
                connection.setRequestProperty(capitalizeHeader(name), value)
        }

        def proxyOutgoingHeaders(connection: HTTPURLConnection) =
            proxyHeaders(request.getHeaderValuesMap map {case (name, values) => (name, values.toSeq)}, connection.setRequestProperty _, out = true)

        if (! Set("GET", "DELETE", "PUT", "POST")(request.getMethod))
            throw new OXFException("Unsupported method: " + request.getMethod)

        // Prepare connection
        val doOutput = Set("PUT", "POST")(request.getMethod)
        val connection = outgoingURL.openConnection.asInstanceOf[HTTPURLConnection]

        connection.setDoInput(true)
        connection.setDoOutput(doOutput)
        connection.setRequestMethod(request.getMethod)

        setPersistenceHeaders(connection)
        proxyOutgoingHeaders(connection)

        // Write body if needed
        // NOTE: HTTPURLConnection requires setting the body before calling connect()
        if (doOutput) {
            // Ask the request generator first, as the body might have been read already
            // Q: Could this be handled automatically in ExternalContext?
            val is = RequestGenerator.getRequestBody(request) match {
                case bodyURL: String => NetUtils.uriToInputStream(bodyURL)
                case _ => request.getInputStream
            }

            copyStream(is, connection.getOutputStream)
        }

        connection.connect()
        connection
    }

    /**
     * Proxies the request to every configured persistence layer to get the list of the forms,
     * and aggregates the results.
     */
    private def proxyFormDefinition(request: Request, response: Response) {
        val propertySet = Properties.instance.getPropertySet
        val providers = {
            // All the oxf.fr.persistence.provider.*.*.form properties, removing the data-only mappings
            val properties = propertySet getPropertiesStartsWith "oxf.fr.persistence.provider" filterNot (_ endsWith ".data")
            // Value of the property is the name of a provider, e.g. oracle-finance, oracle-hr
            properties map (propertySet getString _) distinct
        }

        val formElements = providers map (FormRunner getPersistenceURLHeadersFromProvider _) flatMap { case (baseURI, headers) =>
            // Read all the forms for the current service
            val serviceURI = baseURI + "/form"
            val inputStream = proxyEstablishConnection(request, serviceURI, headers) getInputStream
            val forms = TransformerUtils.readTinyTree(XPathCache.getGlobalConfiguration, inputStream, serviceURI, false, false)
            forms \\ "forms" \\ "form"
        }

        // Wrap with element and serialize
        val documentElement = elementInfo("forms")
        XFormsAPI.insert(into = documentElement, origin = formElements)
        TransformerUtils.getXMLIdentityTransformer.transform(documentElement, new StreamResult(response.getOutputStream))
    }
}