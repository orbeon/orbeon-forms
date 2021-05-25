/**
 * Copyright (C) 2019 Orbeon, Inc.
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
package org.orbeon.oxf.fr.persistence.proxy

import org.apache.http.HttpStatus
import org.orbeon.dom.QName
import org.orbeon.io.IOUtils
import org.orbeon.dom.saxon.{DocumentWrapper, NodeWrapper}
import org.orbeon.io.CharsetNames
import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.externalcontext.ExternalContext.{Request, Response}
import org.orbeon.oxf.externalcontext.URLRewriter._
import org.orbeon.oxf.fr.FormRunnerPersistence._
import org.orbeon.oxf.fr._
import org.orbeon.oxf.fr.datamigration.MigrationSupport
import org.orbeon.oxf.fr.datamigration.MigrationSupport.MigrationsFromForm
import org.orbeon.oxf.fr.persistence.relational.index.status.Backend
import org.orbeon.oxf.http.Headers._
import org.orbeon.oxf.http.HttpMethod.HttpMethodsWithRequestBody
import org.orbeon.oxf.http.{HttpMethod, _}
import org.orbeon.oxf.pipeline.api.PipelineContext
import org.orbeon.oxf.processor.ProcessorImpl
import org.orbeon.oxf.processor.generator.RequestGenerator
import org.orbeon.oxf.properties.Properties
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.util.PathUtils._
import org.orbeon.oxf.util._
import org.orbeon.oxf.xforms.NodeInfoFactory.elementInfo
import org.orbeon.oxf.xforms.action.XFormsAPI
import org.orbeon.oxf.xml.{ElementFilterXMLReceiver, TransformerUtils, XMLParsing}
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.Implicits._
import org.orbeon.scaxon.SimplePath._
import org.orbeon.xforms.RelevanceHandling
import org.orbeon.xforms.RelevanceHandling._

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, InputStream, OutputStream}
import java.net.URI
import javax.xml.transform.OutputKeys
import javax.xml.transform.stream.StreamResult
import scala.annotation.tailrec
import scala.collection.JavaConverters._


/**
 * The persistence proxy processor:
 *
 * - proxies GET, PUT, DELETE and POST to the appropriate persistence implementation
 * - sets persistence implementation headers
 * - calls all active persistence implementations to aggregate form metadata
 */
class PersistenceProxyProcessor extends ProcessorImpl {

  import PersistenceProxyProcessor._

  // Start the processor
  override def start(pipelineContext: PipelineContext): Unit = {
    val ec = NetUtils.getExternalContext
    proxyRequest(ec.getRequest, ec.getResponse)
  }
}

private object PersistenceProxyProcessor {

  val FormPath                       = """/fr/service/persistence(/crud/([^/]+)/([^/]+)/form/([^/]+))""".r
  val DataPath                       = """/fr/service/persistence(/crud/([^/]+)/([^/]+)/(data|draft)/([^/]+)/([^/]+))""".r
  val DataCollectionPath             = """/fr/service/persistence(/crud/([^/]+)/([^/]+)/data/)""".r
  val SearchPath                     = """/fr/service/persistence(/search/([^/]+)/([^/]+))""".r
  val PublishedFormsMetadataPath     = """/fr/service/persistence/form(/([^/]+)(?:/([^/]+))?)?""".r
  val ReindexPath                    =   "/fr/service/persistence/reindex"

  val RawDataFormatVersion           = "raw"
  val AllowedDataFormatVersionParams = Set() ++ (DataFormatVersion.values map (_.entryName)) + RawDataFormatVersion

  val FRRelevantQName                = QName("relevant", XMLNames.FRNamespace)

  val SupportedMethods               = Set[HttpMethod](HttpMethod.GET, HttpMethod.DELETE, HttpMethod.PUT, HttpMethod.POST, HttpMethod.LOCK, HttpMethod.UNLOCK)
  val GetOrPutMethods                = Set[HttpMethod](HttpMethod.GET, HttpMethod.PUT)

  implicit val Logger                = new IndentedLogger(LoggerFactory.createLogger(PersistenceProxyProcessor.getClass))

  // Proxy the request to the appropriate persistence implementation
  def proxyRequest(request: Request, response: Response): Unit = {
    val incomingPath = request.getRequestPath
    incomingPath match {
      case FormPath(path, app, form, _)                => proxyRequest               (request, response, app, form, FormOrData.Form, None          , path)
      case DataPath(path, app, form, _, _, filename)   => proxyRequest               (request, response, app, form, FormOrData.Data, Some(filename), path)
      case DataCollectionPath(path, app, form)         => proxyRequest               (request, response, app, form, FormOrData.Data, None          , path)
      case SearchPath(path, app, form)                 => proxyRequest               (request, response, app, form, FormOrData.Data, None          , path)
      case PublishedFormsMetadataPath(path, app, form) => proxyPublishedFormsMetadata(request, response, Option(app), Option(form), path)
      case ReindexPath                                 => proxyReindex(request, response)
      case _                                           => throw new OXFException(s"Unsupported path: $incomingPath")
    }
  }

  // Proxy the request depending on app/form name and whether we are accessing form or data
  def proxyRequest(
    request        : Request,
    response       : Response,
    app            : String,
    form           : String,
    formOrData     : FormOrData,
    filename       : Option[String],
    path           : String
  ): Unit = {

    // Throws if there is an incompatibility
    checkDataFormatVersionIfNeeded(request, app, form, formOrData)

    val isDataXmlRequest = formOrData == FormOrData.Data && filename.contains("data.xml")
    val isFormBuilder    = app == "orbeon" && form == "builder"

    val requestContent = HttpMethodsWithRequestBody(request.getMethod) option {

      val requestInputStream = RequestGenerator.getRequestBody(PipelineContext.get) match {
        case Some(bodyURL) => NetUtils.uriToInputStream(bodyURL)
        case None          => request.getInputStream
      }

      val (bodyInputStream, bodyContentLength) =
        FieldEncryption.encryptDataIfNecessary(request, requestInputStream, app, form, filename)
          .getOrElse(requestInputStream -> request.contentLengthOpt)

      StreamedContent(
        bodyInputStream,
        Option(request.getContentType),
        bodyContentLength,
        None
      )
    }

    // Get persistence implementation target URL and configuration headers
    val (persistenceBaseURL, headers) = getPersistenceURLHeaders(app, form, formOrData)

    val serviceURI = PathUtils.appendQueryString(
      persistenceBaseURL.dropTrailingSlash + path,
      NetUtils.encodeQueryString(request.getParameterMap)
    )

    val requestForData = formOrData == FormOrData.Data && request.getMethod == HttpMethod.GET
    val transforms     = requestForData.flatList {

      if (isDataXmlRequest && ! isFormBuilder) {

        // Prune non-relevant nodes, if we're asked to do it
        // https://doc.orbeon.com/form-runner/apis/persistence-api/crud#url-parameters
        val removeNonRelevantTransform = {
          val nonrelevantParamValue      = request.getFirstParamAsString(NonRelevantName)
          val requestedRelevanceHandling = nonrelevantParamValue.flatMap(RelevanceHandling.withNameLowercaseOnlyOption)
          requestedRelevanceHandling match {
            case Some(Keep)      | None => None
            case Some(Remove)           => Some(parsePruneAndSerializeXmlData _)
            case Some(r @ Empty)        => throw new UnsupportedOperationException(s"${r.entryName}")
          }
        }

        List(removeNonRelevantTransform).flatten

      } else if (isDataXmlRequest && isFormBuilder | formOrData == FormOrData.Form) {

        // Special case of form definitions
        request.getFirstParamAsString(FormDefinitionFormatVersionName).map(DataFormatVersion.withName) match {
          case Some(dstVersion) =>
            // We are explicitly asked to downgrade a form definition format
            // The database may contain a form definition in any format

            def migrate(is: InputStream, os: OutputStream): Unit = {

              val formDoc =
                new DocumentWrapper(TransformerUtils.readDom4j(is, null, false, false), null, XPath.GlobalConfiguration)

              val migrationsFromForm =
                new MigrationsFromForm(
                  outerDocument        = formDoc,
                  availableXBLBindings = None, // XXX ok?
                  legacyGridsOnly      = false
                )

              // 1. All grids must have ids for what follows
//                FormBuilder.addMissingGridIds(ctx.bodyElem)

              // 2. Migrate inline instance data
              val frDocCtx: FormRunnerDocContext = new FormRunnerDocContext {
                val formDefinitionRootElem: NodeInfo = formDoc.rootElement
              }

              // If we don't find a version in the form definition, it means it was last updated with a version older than 2018.2
              // TODO: We should discriminate between 4.8.0 and 4.0.0 ideally. Currently we don't have a user use case but it would
              //   be good for correctness.
              val srcVersionFromMetadataOrGuess =
                findFormDefinitionFormatFromStringVersions(
                  (frDocCtx.metadataRootElem / "updated-with-version" ++ frDocCtx.metadataRootElem / "created-with-version") map
                    (_.stringValue)
                ) getOrElse DataFormatVersion.V480

              MigrationSupport.migrateDataInPlace(
                dataRootElem     = (frDocCtx.dataInstanceElem child *).head.asInstanceOf[NodeWrapper],
                srcVersion       = srcVersionFromMetadataOrGuess,
                dstVersion       = dstVersion,
                findMigrationSet = migrationsFromForm
              )

              // 3. Migrate other aspects such as binds and controls
              MigrationSupport.migrateOtherInPlace(
                formRootElem     = formDoc,
                srcVersion       = srcVersionFromMetadataOrGuess,
                dstVersion       = dstVersion,
                findMigrationSet = migrationsFromForm
              )

              // Serialize out the result
              val receiver =
                TransformerUtils.getIdentityTransformerHandler |!>
                  (_.setResult(new StreamResult(os)))

              receiver.getTransformer |!>
                (_.setOutputProperty(OutputKeys.ENCODING,                     CharsetNames.Utf8)) |!>
                (_.setOutputProperty(OutputKeys.METHOD,                       "xml"))             |!>
                (_.setOutputProperty(OutputKeys.VERSION,                      "1.0"))             |!>
                (_.setOutputProperty(OutputKeys.INDENT,                       "no"))              |!>
                (_.setOutputProperty(TransformerUtils.INDENT_AMOUNT_PROPERTY, "0"))

              TransformerUtils.writeTinyTree(formDoc, receiver)
            }

            List(migrate _)

          case None => Nil
        }
      } else {
        Nil
      }
    }

    proxyRequest(request, requestContent, serviceURI, headers, response, transforms)
  }

  def checkDataFormatVersionIfNeeded(
    request    : Request,
    app        : String,
    form       : String,
    formOrData : FormOrData
  ): Unit =
    if (formOrData == FormOrData.Data && GetOrPutMethods(request.getMethod))
      // https://github.com/orbeon/orbeon-forms/issues/4861
      request.getFirstParamAsString(DataFormatVersionName) foreach { incomingVersion =>

        val providerVersion =
          providerDataFormatVersionOrThrow(app, form)

        require(
          AllowedDataFormatVersionParams(incomingVersion),
          s"`$FormRunnerPersistence.DataFormatVersionName` parameter must be one of ${AllowedDataFormatVersionParams mkString ", "}"
        )

        // We can remove this once we are able to perform conversions here, see:
        // https://github.com/orbeon/orbeon-forms/issues/3110
        if (! Set(RawDataFormatVersion, providerVersion.entryName)(incomingVersion))
          throw HttpStatusCodeException(StatusCode.BadRequest)
      }

  def parsePruneAndSerializeXmlData(is: InputStream, os: OutputStream): Unit = {

    val receiver = TransformerUtils.getIdentityTransformerHandler
    receiver.setResult(new StreamResult(os))

    IOUtils.useAndClose(is) { is =>
      IOUtils.useAndClose(os) { _ =>
        XMLParsing.inputStreamToSAX(
          is,
          null,
          new ElementFilterXMLReceiver(
            xmlReceiver = receiver,
            filter      = (_, _, _, atts) =>
              atts.getValue(FRRelevantQName.namespace.uri, FRRelevantQName.localName) != "false"
          ),
          XMLParsing.ParserConfiguration.PLAIN,
          true
        )
      }
    }
  }

  def proxyRequest(
    request            : Request,
    requestContent     : Option[StreamedContent],
    serviceURI         : String,
    headers            : Map[String, String],
    response           : Response,
    responseTransforms : List[(InputStream, OutputStream) => Unit]
  ): Unit =
    IOUtils.useAndClose(proxyEstablishConnection(request, requestContent, serviceURI, headers)) { cxr =>

      // Proxy status code
      response.setStatus(cxr.statusCode)
      // Proxy incoming headers
      cxr.content.contentType foreach (response.setHeader(Headers.ContentType, _))
      proxyCapitalizeAndCombineHeaders(cxr.headers, request = false) foreach (response.setHeader _).tupled

      @tailrec
      def applyTransforms(
        startInputStream : InputStream,
        endOutputStream  : OutputStream,
        transforms       : List[(InputStream, OutputStream) => Unit]
      ): Unit = {
        transforms match {
          case Nil                             => IOUtils.copyStreamAndClose(startInputStream, endOutputStream)
          case List(transform)                 => transform(startInputStream, endOutputStream)
          case headTransform :: tailTransforms =>
            val intermediateOutputStream = new ByteArrayOutputStream
            headTransform(startInputStream, intermediateOutputStream)
            val intermediateInputStream = new ByteArrayInputStream(intermediateOutputStream.toByteArray)
            applyTransforms(intermediateInputStream, endOutputStream, tailTransforms)
        }
      }

      val doTransforms =
        cxr.statusCode == HttpStatus.SC_OK
      applyTransforms(
        cxr.content.inputStream,
        response.getOutputStream,
        if (doTransforms) responseTransforms else Nil
      )
    }

  def proxyEstablishConnection(
    request        : Request,
    requestContent : Option[StreamedContent],
    uri            : String,
    headers        : Map[String, String]
  ): ConnectionResult = {

    implicit val externalContext          = NetUtils.getExternalContext
    implicit val coreCrossPlatformSupport = CoreCrossPlatformSupport

    val outgoingURL =
      new URI(URLRewriterUtils.rewriteServiceURL(externalContext.getRequest, uri, REWRITE_MODE_ABSOLUTE))

    val persistenceHeaders =
      for ((name, value) <- headers)
      yield capitalizeCommonOrSplitHeader(name) -> List(value)

    // Forwards all incoming headers, with exceptions like connection headers and, importantly, cookie headers
    val proxiedHeaders =
      proxyAndCapitalizeHeaders(request.getHeaderValuesMap.asScala mapValues (_.toList), request = true)

    val allHeaders =
      Connection.buildConnectionHeadersCapitalizedIfNeeded(
        url              = outgoingURL,
        hasCredentials   = false,
        customHeaders    = persistenceHeaders ++ proxiedHeaders,
        headersToForward = Set(),                                   // handled by proxyAndCapitalizeHeaders()
        cookiesToForward = Connection.cookiesToForwardFromProperty, // NOT handled by proxyAndCapitalizeHeaders()
        getHeader        = Connection.getHeaderFromRequest(request)
      )

    val method = request.getMethod

    if (! SupportedMethods(method))
      throw new OXFException(s"Unsupported method: $method")

    Connection.connectNow(
      method      = method,
      url         = outgoingURL,
      credentials = None,
      content     = requestContent,
      headers     = allHeaders,
      loadState   = true,
      saveState   = true,
      logBody     = false
    )
  }

  /**
   * Proxies the request to every configured persistence layer to get the list of the forms, and aggregates the
   * results. So the response is not simply proxied, unlike for other persistence layer calls.
   */
  def proxyPublishedFormsMetadata(
    request  : Request,
    response : Response,
    app      : Option[String],
    form     : Option[String],
    path     : String
  ): Unit = {

    val providers = {
      (app, form) match {
        case (Some(appName), Some(formName)) =>
          // Get the specific provider for this app/form
          findProvider(appName, formName, FormOrData.Form).toList
        case _ =>
          // Get providers independently from app/form
          // NOTE: Could also optimize case where only app is provided, but there are no callers as of 2013-10-21.
          getProviders(usableFor = FormOrData.Form)
      }
    }

    val parameters = NetUtils.encodeQueryString(request.getParameterMap)

    val allFormElements =
      for {
        provider           <- providers
        (baseURI, headers) = getPersistenceURLHeadersFromProvider(provider)
      } yield {
        // Read all the forms for the current service
        val serviceURI = PathUtils.appendQueryString(baseURI + "/form" + Option(path).getOrElse(""), parameters)
        val cxr        = proxyEstablishConnection(request, None, serviceURI, headers)

        ConnectionResult.withSuccessConnection(cxr, closeOnSuccess = true) { is =>
          val forms = TransformerUtils.readTinyTree(XPath.GlobalConfiguration, is, serviceURI, false, false)
          forms descendant "forms" descendant "form"
        }
      }

    returnAggregatedDocument(
      root     = "forms",
      content  =
        FormRunner.filterFormsAndAnnotateWithOperations(
          formsEls = allFormElements.flatten,
          allForms = request.getFirstParamAsString("all-forms") contains "true"
        ),
      response = response
    )
  }

  def proxyReindex(
    request  : Request,
    response : Response
  ): Unit = {
    val dataProviders = getProviders(usableFor = FormOrData.Data)
    val dataProvidersWithIndexSupport =
      dataProviders.filter { provider =>
        FormRunner.providerPropertyAsBoolean(provider, "reindex", default = false)
      }
    Backend.reindexingProviders(
      dataProvidersWithIndexSupport, p => {
        val (baseURI, headers) = getPersistenceURLHeadersFromProvider(p)
        val serviceURI = baseURI + "/reindex"
        proxyRequest(request, None, serviceURI, headers, response, Nil)
      }
    )
  }

  private def returnAggregatedDocument(
    root     : String,
    content  : List[NodeInfo],
    response : Response
  ): Unit = {
    val documentElement = elementInfo(root)
    XFormsAPI.insert(into = documentElement, origin = content)
    response.setContentType(ContentTypes.XmlContentType)
    TransformerUtils.getXMLIdentityTransformer.transform(documentElement, new StreamResult(response.getOutputStream))
  }

  // Get all providers that can be used either for form data or for form definitions
  def getProviders(usableFor: FormOrData): List[String] = {
    val propertySet = Properties.instance.getPropertySet
    propertySet.propertiesStartsWith(PersistenceProviderPropertyPrefix, matchWildcards = false)
      .filter (propName => propName.endsWith(".*") ||
                          propName.endsWith(s".${usableFor.entryName}"))
      .flatMap(propertySet.getNonBlankString)
      .distinct
      .filter(FormRunner.isActiveProvider)
  }

  def findRequestedRelevanceHandlingForGet(request: Request, formOrData: FormOrData): Option[RelevanceHandling] =
    if (formOrData == FormOrData.Data && request.getMethod == HttpMethod.GET)
      request.getFirstParamAsString(NonRelevantName) flatMap RelevanceHandling.withNameLowercaseOnlyOption
    else
      None
}
