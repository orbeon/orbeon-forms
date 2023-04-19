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
import org.orbeon.io.IOUtils
import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.externalcontext.ExternalContext.{Request, Response}
import org.orbeon.oxf.externalcontext._
import org.orbeon.oxf.fr.FormRunnerCommon.frc
import org.orbeon.oxf.fr.FormRunnerParams.AppFormVersion
import org.orbeon.oxf.fr.FormRunnerPersistence._
import org.orbeon.oxf.fr._
import org.orbeon.oxf.fr.permission.PermissionsAuthorization.findCurrentCredentialsFromSession
import org.orbeon.oxf.fr.permission.{Operations, PermissionsAuthorization}
import org.orbeon.oxf.fr.persistence.PersistenceMetadataSupport
import org.orbeon.oxf.fr.persistence.proxy.PersistenceProxyPermissions.{ResponseHeaders, extractResponseHeaders}
import org.orbeon.oxf.fr.persistence.relational.Version
import org.orbeon.oxf.fr.persistence.relational.index.status.Backend
import org.orbeon.oxf.http.Headers._
import org.orbeon.oxf.http._
import org.orbeon.oxf.pipeline.api.PipelineContext
import org.orbeon.oxf.processor.ProcessorImpl
import org.orbeon.oxf.processor.generator.RequestGenerator
import org.orbeon.oxf.properties.Properties
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.util.Logging.debug
import org.orbeon.oxf.util.PathUtils._
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.util._
import org.orbeon.oxf.xforms.NodeInfoFactory.elementInfo
import org.orbeon.oxf.xforms.action.XFormsAPI
import org.orbeon.oxf.xml.{ElementFilterXMLReceiver, ParserConfiguration, TransformerUtils, XMLParsing}
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.Implicits._
import org.orbeon.scaxon.SimplePath._
import org.orbeon.xforms.RelevanceHandling
import org.orbeon.xforms.RelevanceHandling._

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, InputStream, OutputStream}
import java.net.URI
import javax.xml.transform.stream.StreamResult
import scala.annotation.tailrec
import scala.jdk.CollectionConverters._
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}


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
    implicit val ec = NetUtils.getExternalContext
    proxyRequest(ec.getRequest, ec.getResponse)
  }
}

private object PersistenceProxyProcessor {

  val FrEncryptClass                 = "fr-encrypt"

  val RawDataFormatVersion           = "raw"
  val AllowedDataFormatVersionParams = Set() ++ (DataFormatVersion.values map (_.entryName)) + RawDataFormatVersion

  val SupportedMethods               = Set[HttpMethod](HttpMethod.GET, HttpMethod.HEAD, HttpMethod.DELETE, HttpMethod.PUT, HttpMethod.POST, HttpMethod.LOCK, HttpMethod.UNLOCK)
  val GetOrPutMethods                = Set[HttpMethod](HttpMethod.GET, HttpMethod.PUT)

  implicit val Logger                = new IndentedLogger(LoggerFactory.createLogger(PersistenceProxyProcessor.getClass))

  sealed trait VersionAction
    object VersionAction {
      case object Reject                                           extends VersionAction
      case class  AcceptForData      (v: Option[Version.Specific]) extends VersionAction
      case class  AcceptAndUseForForm(v: Version.Specific)         extends VersionAction
      case class  HeadData           (v: Version.ForDocument)      extends VersionAction
      case object LatestForm                                       extends VersionAction
      case object NextForm                                         extends VersionAction
      case object Ignore                                           extends VersionAction
    }

    private val VersionActionMap: Map[HttpMethod, Map[FormOrData, Version => VersionAction]] = Map(
      HttpMethod.GET -> Map(
        FormOrData.Form ->
          {
            case v @ Version.ForDocument(_, _) => VersionAction.HeadData(v) // only for this case
            case     Version.Unspecified       => VersionAction.LatestForm
            case v @ Version.Specific(_)       => VersionAction.AcceptAndUseForForm(v)
            case     Version.Next              => VersionAction.Reject
          },
        FormOrData.Data ->
          {
            case     Version.ForDocument(_, _) => VersionAction.Reject
            case     Version.Unspecified       => VersionAction.AcceptForData(None)
            case v @ Version.Specific(_)       => VersionAction.AcceptForData(Some(v))
            case     Version.Next              => VersionAction.Reject
          }
      ),
      HttpMethod.PUT -> Map(
        FormOrData.Form ->
          {
            case     Version.ForDocument(_, _) => VersionAction.Reject
            case     Version.Unspecified       => VersionAction.LatestForm
            case v @ Version.Specific(_)       => VersionAction.AcceptAndUseForForm(v)
            case     Version.Next              => VersionAction.NextForm // only for this case
          },
        FormOrData.Data ->
          {
            case     Version.ForDocument(_, _) => VersionAction.Reject
            case     Version.Unspecified       => VersionAction.AcceptForData(None)
            case v @ Version.Specific(_)       => VersionAction.AcceptForData(Some(v))
            case     Version.Next              => VersionAction.Reject
          }
      ),
      HttpMethod.DELETE -> Map(
        FormOrData.Form ->
          {
            case     Version.ForDocument(_, _) => VersionAction.Reject
            case     Version.Unspecified       => VersionAction.LatestForm
            case v @ Version.Specific(_)       => VersionAction.AcceptAndUseForForm(v)
            case     Version.Next              => VersionAction.Reject
          },
        FormOrData.Data ->
          {
            case     Version.ForDocument(_, _) => VersionAction.Reject
            case     Version.Unspecified       => VersionAction.AcceptForData(None)
            case v @ Version.Specific(_)       => VersionAction.AcceptForData(Some(v))// could disallow (used to)
            case     Version.Next              => VersionAction.Reject
          }
      ),
      HttpMethod.LOCK -> Map(
        FormOrData.Form -> (_ => VersionAction.Ignore),
        FormOrData.Data -> (_ => VersionAction.Ignore),
      ),
      HttpMethod.UNLOCK -> Map(
        FormOrData.Form -> (_ => VersionAction.Ignore),
        FormOrData.Data -> (_ => VersionAction.Ignore),
      )
    )

  // Proxy the request to the appropriate persistence implementation
  private def proxyRequest(request: Request, response: Response)(implicit externalContext: ExternalContext): Unit = {
    val incomingPath = request.getRequestPath
    incomingPath match {
      case FormPath(path, app, form, _)                     => proxyRequest               (request, response, AppForm(app, form), FormOrData.Form, None          , path)
      case DataPath(path, app, form, _, document, filename) => proxyRequest               (request, response, AppForm(app, form), FormOrData.Data, Some(filename), path, Some(document))
      case DataCollectionPath(path, app, form)              => proxyRequest               (request, response, AppForm(app, form), FormOrData.Data, None          , path)
      case SearchPath(path, app, form)                      => proxyRequest               (request, response, AppForm(app, form), FormOrData.Data, None          , path)
      case ReEncryptAppFormPath(path, app, form)            => proxyReEncryptAppForm      (request, response, AppForm(app, form), path)
      case PublishedFormsMetadataPath(path, app, form)      => proxyPublishedFormsMetadata(request, response, Option(app), Option(form), path)
      case ReindexPath                                      => proxyReindex               (request, response)
      case ReEncryptStatusPath                              => proxyReEncryptStatus       (request, response)
      case _                                                => throw new OXFException(s"Unsupported path: $incomingPath")
    }
  }

  private def proxyReEncryptAppForm(
    request  : Request,
    response : Response,
    appForm  : AppForm,
    path     : String,
  ): Unit = {
    val (persistenceBaseURL, outgoingPersistenceHeaders) =
      getPersistenceURLHeaders(appForm, FormOrData.Form) // Q: Why `FormOrData.Form`? xxx check! was Form, but Data makes more sense!

    val serviceURI = PathUtils.appendQueryString(
      persistenceBaseURL.dropTrailingSlash + path,
      PathUtils.encodeQueryString(request.parameters)
    )

    // NOTE: No form definition version handled!
    proxyRequestImpl(
      proxyEstablishConnection(request, None, serviceURI, outgoingPersistenceHeaders),
      response,
      Nil
    )
  }

  // Proxy the request depending on app/form name and whether we are accessing form or data
  private def proxyRequest(
    request         : Request,
    response        : Response,
    appForm         : AppForm,
    formOrData      : FormOrData,
    filename        : Option[String],
    path            : String,
    documentIdOpt   : Option[String] = None)(implicit
    externalContext : ExternalContext
  ): Unit = {

    // Throws if there is an incompatibility
    checkDataFormatVersionIfNeeded(request, appForm, formOrData)

    val isDataXmlRequest = formOrData == FormOrData.Data && filename.contains("data.xml")
    val isFormBuilder    = appForm == AppForm.FormBuilder

    // Get persistence implementation target URL and configuration headers
    //
    // Headers example:
    //
    // - `Orbeon-Versioning` -> true
    // - `Orbeon-Reencrypt`  -> true
    // - `Orbeon-Reindex`    -> true
    // - `Orbeon-Lease`      -> true
    // - `Orbeon-Datasource` -> mysql
    val (persistenceBaseURL, outgoingPersistenceHeaders) = getPersistenceURLHeaders(appForm, formOrData)

    val serviceUri = PathUtils.appendQueryString(
      persistenceBaseURL.dropTrailingSlash + path,
      PathUtils.encodeQueryString(request.parameters)
    )

    val incomingVersion =
      Version(
        documentId = request.getFirstHeaderIgnoreCase(Version.OrbeonForDocumentId), // this gets precedence
        isDraft    = request.getFirstHeaderIgnoreCase(Version.OrbeonForDocumentIsDraft),
        version    = request.getFirstHeaderIgnoreCase(Version.OrbeonFormDefinitionVersion),
      )

    val (cxrOpt, effectiveFormDefinitionVersionOpt) =
      request.getMethod match {
        case crudMethod: HttpMethod.CrudMethod
          if formOrData == FormOrData.Form && filename.isEmpty || formOrData == FormOrData.Data && filename.isDefined =>

          // CRUD operations on form definitions or data

          val versionAction =
            VersionActionMap.getOrElse(
              if (crudMethod == HttpMethod.HEAD) HttpMethod.GET else crudMethod,
              throw HttpStatusCodeException(StatusCode.MethodNotAllowed)
            ).getOrElse(
              formOrData,
              throw HttpStatusCodeException(StatusCode.InternalServerError)
            )(incomingVersion)

          val incomingTokenOpt = request.getFirstParamAsString(frc.AccessTokenParam)

          def queryForToken: List[(String, String)] =
            incomingTokenOpt
              .flatMap(_.trimAllToOpt)
              .map(frc.AccessTokenParam ->).toList

          versionAction match {
            case VersionAction.Reject =>
              throw HttpStatusCodeException(StatusCode.BadRequest)
            case VersionAction.AcceptAndUseForForm(v) if crudMethod == HttpMethod.DELETE =>

              connectToObtainResponseHeadersAndCheckVersionPutOrDelete(
                request,
                crudMethod,
                serviceUri,
                outgoingPersistenceHeaders,
                Some(v.version),
                queryForToken,
                filterVersioningHeaders = false
              )

              (None, Some(v.version))
            case VersionAction.AcceptAndUseForForm(v) =>
              (None, Some(v.version))
            case VersionAction.AcceptForData(v) =>

              val (cxrOpt, effectiveFormDefinitionVersion, responseHeadersOpt) =
                connectToObtainResponseHeadersAndCheckVersion(
                  request,
                  crudMethod,
                  serviceUri,
                  outgoingPersistenceHeaders,
                  v.map(_.version),
                  queryForToken
                )

              val operations =
                try {
                  permissionCheck(
                    (appForm, effectiveFormDefinitionVersion),
                    crudMethod,
                    documentIdOpt.getOrElse(throw new IllegalArgumentException), // for data there must be a `documentId`,
                    incomingTokenOpt,
                    responseHeadersOpt
                  )
                } catch {
                  case NonFatal(t) =>
                    // We must close any `ConnectionResult` if permissions are not ok
                    cxrOpt.foreach(_.close())
                    throw t
                }

              response.setHeader(
                FormRunnerPersistence.OrbeonOperations,
                Operations.serialize(operations, normalized = true).mkString(" ")
              )

              (cxrOpt, Some(effectiveFormDefinitionVersion))

            case VersionAction.HeadData(v) =>
              // TODO: couldn't this use `connectToObtainResponseHeaders()`
              val versionFromHead =
                PersistenceMetadataSupport.readDocumentFormVersion(appForm, v.documentId, v.isDraft, queryForToken)
                  .getOrElse(throw HttpStatusCodeException(StatusCode.NotFound))
              (None, Some(versionFromHead))
            case VersionAction.LatestForm =>
              val versionFromMetadata =
                PersistenceMetadataSupport.readLatestVersion(appForm).getOrElse(1)
              (None, Some(versionFromMetadata))
            case VersionAction.NextForm =>
              val versionFromMetadata =
                PersistenceMetadataSupport.readLatestVersion(appForm).map(_ + 1).getOrElse(1)
              (None, Some(versionFromMetadata))
            case VersionAction.Ignore =>
              (None, None)
          }

        case _ =>
          (None, None)
      }

    val requestContent = HttpMethod.HttpMethodsWithRequestBody(request.getMethod) option {

      val requestInputStream = RequestGenerator.getRequestBody(PipelineContext.get) match {
        case Some(bodyURL) => NetUtils.uriToInputStream(bodyURL)
        case None          => request.getInputStream
      }

      val inputData = requestInputStream -> request.contentLengthOpt
      val (bodyInputStream, bodyContentLength) = formOrData match {
        case FormOrData.Form =>
          // Don't encrypt form definitions
          inputData
        case FormOrData.Data if isFormBuilder =>
          // Don't encrypt Form Builder form data either
          inputData
        case FormOrData.Data =>
          FieldEncryption.encryptDataIfNecessary(
            request,
            requestInputStream,
            appForm,
            isDataXmlRequest
          ).getOrElse(inputData)
      }

      StreamedContent(
        bodyInputStream,
        Option(request.getContentType),
        bodyContentLength,
        None
      )
    }

    def maybeMigrateFormDefinition: Option[(InputStream, OutputStream) => Unit] =
      request.getFirstParamAsString(FormDefinitionFormatVersionName).map(DataFormatVersion.withName) map { dstVersion =>
        Transforms.migrateFormDefinition(
          dstVersion,
          appForm
        )
      }

    val responseTransforms =
      if (formOrData == FormOrData.Data && request.getMethod == HttpMethod.GET) {
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

          // Decrypt data
          val decryptTransform = Some(FieldEncryption.decryptDataXmlTransform _)

          List(removeNonRelevantTransform, decryptTransform).flatten

        } else if (isDataXmlRequest && isFormBuilder) {
          maybeMigrateFormDefinition.toList
        } else {
          // Decrypt attachment if we're told to do so
          filename.isDefined.flatList {
            val decryptHeader     = request.getFirstHeaderIgnoreCase(FormRunnerPersistence.OrbeonDecryptHeader)
            val isEncryptedAtRest = decryptHeader.contains(true.toString)
            isEncryptedAtRest.list(FieldEncryption.decryptAttachmentTransform _)
          }
        }
      } else if (formOrData == FormOrData.Form && request.getMethod == HttpMethod.GET) {
        maybeMigrateFormDefinition.toList
      } else {
        Nil
      }

    val outgoingVersionHeaderOpt =
      effectiveFormDefinitionVersionOpt.map(v => Version.OrbeonFormDefinitionVersion -> v.toString)

    // A connection might have been opened above, and if so we use it
    proxyRequestImpl(
      cxrOpt.getOrElse(proxyEstablishConnection(request, requestContent, serviceUri, outgoingPersistenceHeaders ++ outgoingVersionHeaderOpt)),
      response,
      responseTransforms
    )
  }

  private def compareVersionAgainstIncomingIfNeeded(effectiveFormDefinitionVersionOpt: Option[Int],responseHeaders: ResponseHeaders): Int = {
    val versionFromProvider = responseHeaders.formVersion.getOrElse(1)
    effectiveFormDefinitionVersionOpt.foreach { incomingVersion =>
      if (incomingVersion != versionFromProvider)
        throw HttpStatusCodeException(StatusCode.BadRequest)
    }
    versionFromProvider
  }

  private def connectToObtainResponseHeadersAndCheckVersion(
    request                   : Request, // for incoming headers and method only // TODO: then pass for example `OutgoingRequest`
    requestMethod             : HttpMethod.CrudMethod,
    serviceUri                : String,
    outgoingPersistenceHeaders: Map[String, String],
    specificIncomingVersionOpt: Option[Int],
    queryForToken             : List[(String, String)]
  ): (Option[ConnectionResult], Int, Option[ResponseHeaders]) =
    requestMethod match {
      case HttpMethod.GET | HttpMethod.HEAD =>
        // We had `AcceptAndCheckForData` or `AcceptUnspecifiedForData` above

        val cxr =
          ConnectionResult.trySuccessConnection(
            proxyEstablishConnection(request, None, serviceUri, outgoingPersistenceHeaders)
          ).get // throws `HttpStatusCodeException` if not successful, including `NotFound`

        val responseHeaders     = extractResponseHeaders(cxr.getFirstHeaderIgnoreCase)
        val versionFromProvider = compareVersionAgainstIncomingIfNeeded(specificIncomingVersionOpt, responseHeaders)

        (Some(cxr), versionFromProvider, Some(responseHeaders))
      case HttpMethod.PUT | HttpMethod.DELETE =>

        val (versionForPutOrDelete, responseHeadersOpt) =
          connectToObtainResponseHeadersAndCheckVersionPutOrDelete(
            request,
            requestMethod,
            serviceUri,
            outgoingPersistenceHeaders,
            specificIncomingVersionOpt,
            queryForToken,
            filterVersioningHeaders = true
          )

        (None, versionForPutOrDelete, responseHeadersOpt)
    }

  private def connectToObtainResponseHeadersAndCheckVersionPutOrDelete(
    request                   : Request, // for incoming headers and method only // TODO: then pass for example `OutgoingRequest`
    requestMethod             : HttpMethod.CrudMethod,
    serviceUri                : String,
    outgoingPersistenceHeaders: Map[String, String],
    specificIncomingVersionOpt: Option[Int],
    queryForToken             : List[(String, String)],
    filterVersioningHeaders   : Boolean
  ): (Int, Option[ResponseHeaders]) = {

      // The call to `HEAD` will require permissions so we need to forward the token
      val headServiceUri =
        PathUtils.recombineQuery(serviceUri, queryForToken)

      getHeadersFromHeadCallProviderViaProxy(
        request.getHeaderValuesMap,
        headServiceUri,
        outgoingPersistenceHeaders,
        filterVersioningHeaders
      ) match {
        case Success(responseHeaders) =>
          // Existing data
          val versionFromProvider = compareVersionAgainstIncomingIfNeeded(specificIncomingVersionOpt, responseHeaders)
          // NOTE: If we get here, we have already passed successful permissions checks for `GET`/`HEAD` AKA
          // `Read` and we must have the allowed operations. However here we are doing a `PUT`/`DELETE` AKA
          // `Update`/`Delete` and we need to check operations again below. But we could skip actually getting
          // the permissions! We also need `ResponseHeaders` to include `Orbeon-Operations`.
          // TODO: Check above optimization.
          (versionFromProvider, Some(responseHeaders))
        case Failure(HttpStatusCodeException(statusCode @ (StatusCode.NotFound | StatusCode.Gone), _, _)) =>
          // Non-existing data
          specificIncomingVersionOpt match {
            case Some(version) if requestMethod == HttpMethod.PUT =>
              (version, None)
            case None if requestMethod == HttpMethod.PUT =>
              // We can't write new data if we don't know the version
              throw HttpStatusCodeException(StatusCode.BadRequest)
            case _ =>
              // We wouldn't need a version to delete data *except* that we need to get form permissions!
              throw HttpStatusCodeException(statusCode)
          }
        case Failure(t) =>
          throw t
      }
    }

  private def permissionCheck(
    appFormVersion    : AppFormVersion,
    method            : HttpMethod.CrudMethod,
    documentId        : String,
    incomingTokenOpt  : Option[String],
    responseHeadersOpt: Option[ResponseHeaders],
  ) = {

    // TODO: Check possible optimization above to avoid retrieving form permissions twice.
    val formPermissions =
      FormRunner.permissionsFromElemOrProperties(
        PersistenceMetadataSupport.readFormPermissions(appFormVersion._1, FormDefinitionVersion.Specific(appFormVersion._2)),
        appFormVersion._1
      ) |!>
        (formPermissions => debug("CRUD: form permissions", List("permissions" -> formPermissions.toString)))

    PersistenceProxyPermissions.findAuthorizedOperationsOrThrow(
      formPermissions,
      findCurrentCredentialsFromSession,
      method,
      appFormVersion,
      documentId,
      incomingTokenOpt,
      responseHeadersOpt,
    )
  }

  private def checkDataFormatVersionIfNeeded(
    request    : Request,
    appForm    : AppForm,
    formOrData : FormOrData
  ): Unit =
    if (formOrData == FormOrData.Data && GetOrPutMethods(request.getMethod))
      // https://github.com/orbeon/orbeon-forms/issues/4861
      request.getFirstParamAsString(DataFormatVersionName) foreach { incomingVersion =>

        val providerVersion =
          providerDataFormatVersionOrThrow(appForm)

        require(
          AllowedDataFormatVersionParams(incomingVersion),
          s"`$FormRunnerPersistence.DataFormatVersionName` parameter must be one of ${AllowedDataFormatVersionParams mkString ", "}"
        )

        // We can remove this once we are able to perform conversions here, see:
        // https://github.com/orbeon/orbeon-forms/issues/3110
        if (! Set(RawDataFormatVersion, providerVersion.entryName)(incomingVersion))
          throw HttpStatusCodeException(StatusCode.BadRequest)
      }

  private def parsePruneAndSerializeXmlData(is: InputStream, os: OutputStream): Unit = {

    val receiver = TransformerUtils.getIdentityTransformerHandler
    receiver.setResult(new StreamResult(os))

    IOUtils.useAndClose(is) { is =>
      IOUtils.useAndClose(os) { _ =>
        XMLParsing.inputStreamToSAX(
          is,
          null,
          new ElementFilterXMLReceiver(
            xmlReceiver = receiver,
            keep        = (_, _, _, atts) =>
              atts.getValue(XMLNames.FRRelevantQName.namespace.uri, XMLNames.FRRelevantQName.localName) != "false"
          ),
          ParserConfiguration.Plain,
          handleLexical = true,
          null
        )
      }
    }
  }

  private def proxyRequestImpl(
    cxr                : ConnectionResult,
    response           : Response,
    responseTransforms : List[(InputStream, OutputStream) => Unit]
  ): Unit =
    IOUtils.useAndClose(cxr) { connectionResult =>
      // Proxy status code
      response.setStatus(connectionResult.statusCode)
      // Proxy incoming headers
      proxyCapitalizeAndCombineHeaders(connectionResult.headers, request = false) foreach (response.setHeader _).tupled

      // Proxy content type
      val responseContentType = connectionResult.content.contentType
      responseContentType.foreach(response.setHeader(Headers.ContentType, _))

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
        connectionResult.statusCode == HttpStatus.SC_OK

      applyTransforms(
        connectionResult.content.inputStream,
        response.getOutputStream,
        if (doTransforms) responseTransforms else Nil
      )
    }

  private def proxyEstablishConnection(
    request        : Request, // for incoming headers and method only // TODO: then pass for example `OutgoingRequest`
    requestContent : Option[StreamedContent],
    uri            : String,
    headers        : Map[String, String]
  ): ConnectionResult = {

    implicit val externalContext          = NetUtils.getExternalContext
    implicit val coreCrossPlatformSupport = CoreCrossPlatformSupport

    val outgoingURL =
      URI.create(URLRewriterUtils.rewriteServiceURL(externalContext.getRequest, uri, UrlRewriteMode.Absolute))

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
        customHeaders    = proxiedHeaders.toMap ++ persistenceHeaders, // give precedence to persistence headers
        headersToForward = Set.empty,                                  // handled by proxyAndCapitalizeHeaders()
        cookiesToForward = Connection.cookiesToForwardFromProperty,    // NOT handled by proxyAndCapitalizeHeaders()
        getHeader        = Connection.getHeaderFromRequest(request)
      )

    val method = request.getMethod

    if (! SupportedMethods(method))
      throw HttpStatusCodeException(StatusCode.MethodNotAllowed)

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
  private def proxyPublishedFormsMetadata(
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
          findProvider(AppForm(appName, formName), FormOrData.Form).toList
        case _ =>
          // Get providers independently from app/form
          // NOTE: Could also optimize case where only app is provided, but there are no callers as of 2013-10-21.
          getProviders(usableFor = FormOrData.Form)
      }
    }

    val parameters = PathUtils.encodeQueryString(request.parameters)

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
          formsEls               = allFormElements.flatten,
          allForms               = request.getFirstParamAsString("all-forms")                contains "true",
          ignoreAdminPermissions = request.getFirstParamAsString("ignore-admin-permissions") contains "true"
        ),
      response = response
    )
  }

  private def proxyReindex(
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

        val (baseURI, outgoingPersistenceHeaders) = getPersistenceURLHeadersFromProvider(p)
        val serviceURI = baseURI + "/reindex"

        proxyRequestImpl(
          proxyEstablishConnection(request, None, serviceURI, outgoingPersistenceHeaders),
          response,
          Nil
        )
      }
    )
  }

  private def proxyReEncryptStatus(
    request  : Request,
    response : Response
  ): Unit = {

    val dataProviders = getProviders(usableFor = FormOrData.Data)

    val dataProvidersWithReEncryptSupport =
      dataProviders.filter { provider =>
        FormRunner.providerPropertyAsBoolean(provider, "reencrypt", default = false)
      }

    returnAggregatedDocument(
      root     = "forms",
      content  =
        dataProvidersWithReEncryptSupport.flatMap { provider =>
          val (baseURI, headers) = getPersistenceURLHeadersFromProvider(provider)
          val serviceURI         = baseURI + "/reencrypt"
          val cxr                = proxyEstablishConnection(request, None, serviceURI, headers)
          ConnectionResult.withSuccessConnection(cxr, closeOnSuccess = true) { is =>
            val forms = TransformerUtils.readTinyTree(XPath.GlobalConfiguration, is, serviceURI, false, false)
            forms / "forms" / "form"
          }
        },
      response = response
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
  private def getProviders(usableFor: FormOrData): List[String] = {
    val propertySet = Properties.instance.getPropertySet
    propertySet.propertiesStartsWith(PersistenceProviderPropertyPrefix, matchWildcards = false)
      .filter (propName => propName.endsWith(".*") ||
                          propName.endsWith(s".${usableFor.entryName}"))
      .flatMap(propertySet.getNonBlankString)
      .distinct
      .filter(FormRunner.isActiveProvider)
  }

  private def getHeadersFromHeadCallProviderViaProxy(
    requestHeaders            : java.util.Map[String, Array[String]],
    serviceURI                : String,
    outgoingPersistenceHeaders: Map[String, String],
    filterVersioningHeaders   : Boolean
  ): Try[ResponseHeaders] = {

    val headRequest =
      new RequestAdapter {

        // We filter out incoming versioning headers as we want to hit the `Version.Unspecified` case
        val filteredRequestHeaders =
          if (filterVersioningHeaders)
            requestHeaders.asScala.filterKeys(k => ! Version.AllVersionHeadersLower(k.toLowerCase))
          else
            requestHeaders.asScala

        override val getMethod: HttpMethod = HttpMethod.HEAD
        override def getHeaderValuesMap: java.util.Map[String, Array[String]] = filteredRequestHeaders.asJava
      }

    // `HEAD` directly through the proxy!
    val getHeaderIgnoreCaseFromHeadResponseTry =
      IOUtils.useAndClose(proxyEstablishConnection(headRequest, None, serviceURI, outgoingPersistenceHeaders)) { cxr =>
        ConnectionResult.trySuccessConnection(cxr).map(cxr => cxr.getFirstHeaderIgnoreCase _)
      }

    getHeaderIgnoreCaseFromHeadResponseTry match {
      case Success(getHeaderIgnoreCaseFromHeadResponse) =>
        Success(extractResponseHeaders(getHeaderIgnoreCaseFromHeadResponse))
      case Failure(t @ HttpStatusCodeException(StatusCode.NotFound | StatusCode.Gone, _, _)) =>
        Failure(t)
      case Failure(t) =>
        Failure(t)
    }
  }
}
