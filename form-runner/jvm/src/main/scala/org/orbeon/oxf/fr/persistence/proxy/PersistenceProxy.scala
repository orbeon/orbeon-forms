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
import org.log4s
import org.orbeon.connection.{ConnectionResult, StreamedContent}
import org.orbeon.io.IOUtils
import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.controller.NativeRoute
import org.orbeon.oxf.externalcontext.*
import org.orbeon.oxf.externalcontext.ExternalContext.{Request, Response}
import org.orbeon.oxf.fr.*
import org.orbeon.oxf.fr.FormRunnerPersistence.*
import org.orbeon.oxf.fr.permission.*
import org.orbeon.oxf.fr.permission.PermissionsAuthorization.findCurrentCredentialsFromSession
import org.orbeon.oxf.fr.persistence.PersistenceMetadataSupport
import org.orbeon.oxf.fr.persistence.proxy.PersistenceProxyPermissions.ResponseHeaders
import org.orbeon.oxf.fr.persistence.relational.form.FormProxyLogic
import org.orbeon.oxf.fr.persistence.relational.index.status.Backend
import org.orbeon.oxf.http.*
import org.orbeon.oxf.http.Headers.*
import org.orbeon.oxf.pipeline.api.PipelineContext
import org.orbeon.oxf.processor.RegexpMatcher.MatchResult
import org.orbeon.oxf.processor.generator.RequestGenerator
import org.orbeon.oxf.util.*
import org.orbeon.oxf.util.CoreUtils.*
import org.orbeon.oxf.util.PathUtils.*
import org.orbeon.oxf.util.StringUtils.*
import org.orbeon.oxf.xforms.NodeInfoFactory.elementInfo
import org.orbeon.oxf.xforms.action.XFormsAPI
import org.orbeon.oxf.xml.{ElementFilterXMLReceiver, ParserConfiguration, TransformerUtils, XMLParsing}
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.Implicits.*
import org.orbeon.scaxon.SimplePath.*
import org.orbeon.xforms.RelevanceHandling
import org.orbeon.xforms.RelevanceHandling.*

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, InputStream, OutputStream}
import java.net.URI
import javax.xml.transform.stream.StreamResult
import scala.annotation.tailrec
import scala.jdk.CollectionConverters.*
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}


/**
 * The persistence proxy processor:
 *
 * - proxies GET, PUT, DELETE and POST to the appropriate persistence implementation
 * - sets persistence implementation headers
 * - calls all active persistence implementations to aggregate form metadata
 */
object PersistenceProxyRoute extends NativeRoute {

  import PersistenceProxy.*

  def process(
    matchResult: MatchResult
  )(implicit
    pc         : PipelineContext,
    ec         : ExternalContext
  ): Unit = {
    implicit val indentedLogger: IndentedLogger = new IndentedLogger(PersistenceProxy.Logger)
    proxyRequest(ec.getRequest, ec.getResponse)
  }
}

private[persistence] object PersistenceProxy extends FormProxyLogic {

  val RawDataFormatVersion           = "raw"
  val AllowedDataFormatVersionParams = Set() ++ (DataFormatVersion.values map (_.entryName)) + RawDataFormatVersion

  val SupportedMethods               = Set[HttpMethod](HttpMethod.GET, HttpMethod.HEAD, HttpMethod.DELETE, HttpMethod.PUT, HttpMethod.POST, HttpMethod.LOCK, HttpMethod.UNLOCK)
  val GetOrPutMethods                = Set[HttpMethod](HttpMethod.GET, HttpMethod.PUT)

  val Logger: log4s.Logger = LoggerFactory.createLogger(PersistenceProxy.getClass)

  case class OutgoingRequest(
    method : HttpMethod,
    headers: Map[String, List[String]]
  )

  private[persistence] object OutgoingRequest {
    def apply(request: Request): OutgoingRequest =
      OutgoingRequest(
        request.getMethod,
        headersFromRequest(request)
      )

    def headersFromRequest(request: Request): Map[String, List[String]] =
      request.getHeaderValuesMap.asScala.view.mapValues(_.toList).toMap
  }

  sealed trait VersionAction
  private object VersionAction {
    case class  Reject                (reason: String)              extends VersionAction
    case class  AcceptForData         (v: Option[Version.Specific]) extends VersionAction
    case class  AcceptAndUseForForm   (v: Version.Specific)         extends VersionAction
    case class  HeadDataForFormGetOnly(v: Version.ForDocument)      extends VersionAction
    case object LatestForm                                          extends VersionAction
    case object NextForFormPutOnly                                  extends VersionAction
    case object Ignore                                              extends VersionAction
  }

  private val VersionActionMap: Map[HttpMethod, Map[FormOrData, Version => VersionAction]] = Map(
    HttpMethod.GET -> Map(
      FormOrData.Form ->
        {
          case v @ Version.ForDocument(_, _) => VersionAction.HeadDataForFormGetOnly(v) // only for this case
          case     Version.Unspecified       => VersionAction.LatestForm
          case v @ Version.Specific(_)       => VersionAction.AcceptAndUseForForm(v)
          case     Version.Next              => VersionAction.Reject("form GET for next version")
        },
      FormOrData.Data ->
        {
          case     Version.ForDocument(_, _) => VersionAction.Reject("data GET for document version")
          case     Version.Unspecified       => VersionAction.AcceptForData(None)
          case v @ Version.Specific(_)       => VersionAction.AcceptForData(Some(v))
          case     Version.Next              => VersionAction.Reject("data GET for next version")
        }
    ),
    HttpMethod.PUT -> Map(
      FormOrData.Form ->
        {
          case     Version.ForDocument(_, _) => VersionAction.Reject("form PUT for document version")
          case     Version.Unspecified       => VersionAction.LatestForm
          case v @ Version.Specific(_)       => VersionAction.AcceptAndUseForForm(v)
          case     Version.Next              => VersionAction.NextForFormPutOnly // only for this case
        },
      FormOrData.Data ->
        {
          case     Version.ForDocument(_, _) => VersionAction.Reject("data PUT for document version")
          case     Version.Unspecified       => VersionAction.AcceptForData(None)
          case v @ Version.Specific(_)       => VersionAction.AcceptForData(Some(v))
          case     Version.Next              => VersionAction.Reject("data PUT for next version")
        }
    ),
    HttpMethod.DELETE -> Map(
      FormOrData.Form ->
        {
          case     Version.ForDocument(_, _) => VersionAction.Reject("form DELETE for document version")
          case     Version.Unspecified       => VersionAction.LatestForm
          case v @ Version.Specific(_)       => VersionAction.AcceptAndUseForForm(v)
          case     Version.Next              => VersionAction.Reject("form DELETE for next version")
        },
      FormOrData.Data ->
        {
          case     Version.ForDocument(_, _) => VersionAction.Reject("data DELETE for document version")
          case     Version.Unspecified       => VersionAction.AcceptForData(None)
          case v @ Version.Specific(_)       => VersionAction.AcceptForData(Some(v))// could disallow (used to)
          case     Version.Next              => VersionAction.Reject("data DELETE for next version")
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

  // TODO: Could just pass `ec` and no separate `request`/`response`
  // Proxy the request to the appropriate persistence implementation
  def proxyRequest(
    request        : Request,
    response       : Response
  )(implicit
    externalContext: ExternalContext,
    indentedLogger : IndentedLogger
  ): Unit =
    (request.getMethod, request.getRequestPath) match {
      case (_,               FormPath(path, app, form, _))                       => proxyRequest               (request, response, AppForm(app, form), FormOrData.Form, None            , path)
      case (_,               DataPath(path, app, form, _, documentId, filename)) => proxyRequest               (request, response, AppForm(app, form), FormOrData.Data, Some(filename)  , path, Some(documentId))
      case (_,               DataCollectionPath(path, app, form))                => proxyRequest               (request, response, AppForm(app, form), FormOrData.Data, None            , path)
      case (HttpMethod.POST, SearchPath(path, app, form))                        => proxySimpleRequest         (request, response, AppForm(app, form), FormOrData.Data, path)
      case (HttpMethod.POST, ReEncryptAppFormPath(path, app, form))              => proxySimpleRequest         (request, response, AppForm(app, form), FormOrData.Form, path)
      case (HttpMethod.GET,  HistoryPath(path, app, form, _, _))                 => proxySimpleRequest         (request, response, AppForm(app, form), FormOrData.Data, path)
      case (HttpMethod.POST, DistinctValuesPath(path, app, form))                => proxySimpleRequest         (request, response, AppForm(app, form), FormOrData.Data, path)
      case (_,               PublishedFormsMetadataPath(_, app, form))           => proxyPublishedFormsMetadata(request, response, AppFormOpt(Option(app), Option(form)))
      case (HttpMethod.GET,  ReindexPath)                                        => proxyReindex               (request, response) // TODO: should be `POST`
      case (HttpMethod.GET,  ReEncryptStatusPath)                                => proxyReEncryptStatus       (request, response)
      case (_, incomingPath)                                                     => throw new OXFException(s"Unsupported path: $incomingPath") // TODO: bad request?
    }

  // TODO: test
  private def proxySimpleRequest(
    request       : Request,
    response      : Response,
    appForm       : AppForm,
    formOrData    : FormOrData, // for finding the provider
    path          : String
  )(implicit
    indentedLogger: IndentedLogger
  ): Unit = {

    val (persistenceBaseURL, outgoingPersistenceHeaders) =
      getPersistenceURLHeaders(appForm, formOrData)

    val serviceURI = PathUtils.appendQueryString(
      persistenceBaseURL.dropTrailingSlash + path,
      PathUtils.encodeQueryString(request.parameters)
    )

    // Proxy the request body
    val bodyContentOpt =
      bodyContent(
        request          = request,
        isDataXmlRequest = false,
        isFormBuilder    = false,
        appForm          = appForm,
        encrypt          = false
      )

    // Proxy the version header
    val incomingVersionHeaderOpt =
      request.getFirstHeaderIgnoreCase(Version.OrbeonFormDefinitionVersion)

    val outgoingVersionHeaderOpt =
      incomingVersionHeaderOpt.map(v => Version.OrbeonFormDefinitionVersion -> v)

    proxyRequestImpl(
      proxyEstablishConnection(
        OutgoingRequest(request),
        bodyContentOpt,
        serviceURI,
        outgoingPersistenceHeaders ++ outgoingVersionHeaderOpt
      ),
      request,
      response,
      Nil,
      None
    )
  }

  // Proxy the request depending on app/form name and whether we are accessing form or data
  private def proxyRequest(
    request        : Request,
    response       : Response,
    appForm        : AppForm,
    formOrData     : FormOrData,
    filename       : Option[String],
    path           : String,
    documentIdOpt  : Option[String] = None
  )(implicit
    externalContext: ExternalContext,
    indentedLogger : IndentedLogger
  ): Unit = {

    // Throws if there is an incompatibility
    checkDataFormatVersionIfNeeded(indentedLogger, request, appForm, formOrData)

    val isDataXmlRequest = formOrData == FormOrData.Data && filename.contains(DataXml)
    val isFormBuilder    = appForm == AppForm.FormBuilder
    val isAttachment     = ! isDataXmlRequest && filename.isDefined

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

    val isVersioningSupported = FormRunner.isFormDefinitionVersioningSupported(appForm.app, appForm.form)

    val serviceUri = PathUtils.appendQueryString(
      persistenceBaseURL.dropTrailingSlash + path,
      PathUtils.encodeQueryString(request.parameters)
    )

    // TODO: what about permissions for the attachments? is that a thing?
    val (cxrOpt, effectiveFormDefinitionVersionOpt, responseHeadersOpt) =
      request.getMethod match {
        case crudMethod: HttpMethod.CrudMethod
          if formOrData == FormOrData.Form && filename.isEmpty || formOrData == FormOrData.Data && filename.isDefined =>

          // Do not move this outside of the match/case, as it might lead to trying to deserialize 'all' as a Version
          // instead of a SearchVersion, which will lead to an exception (see #6017)
          val incomingVersion =
            Version(
              documentId = request.getFirstHeaderIgnoreCase(Version.OrbeonForDocumentId), // this gets precedence
              isDraft    = request.getFirstHeaderIgnoreCase(Version.OrbeonForDocumentIsDraft),
              version    = request.getFirstHeaderIgnoreCase(Version.OrbeonFormDefinitionVersion),
            )

          // CRUD operations on form definitions or data

          val versionAction =
            VersionActionMap.getOrElse(
              if (crudMethod == HttpMethod.HEAD) HttpMethod.GET else crudMethod,
              throw HttpStatusCodeException(StatusCode.MethodNotAllowed)
            ).getOrElse(
              formOrData,
              throw HttpStatusCodeException(StatusCode.InternalServerError)
            )(incomingVersion)

          val incomingTokenOpt = request.getFirstParamAsString(FormRunner.AccessTokenParam)

          def queryForToken: List[(String, String)] =
            incomingTokenOpt
              .flatMap(_.trimAllToOpt)
              .map(FormRunner.AccessTokenParam ->).toList

          def throw400ForUnsupportedVersion(v: Int): Nothing = {
            indentedLogger.logInfo("", s"400 Bad Request: request for version $v, but versioning not supported")
            throw HttpStatusCodeException(StatusCode.BadRequest)
          }

          versionAction match {
            case VersionAction.Reject(reason) =>
              indentedLogger.logInfo("", s"400 Bad Request: $reason")
              throw HttpStatusCodeException(StatusCode.BadRequest)
            case VersionAction.AcceptAndUseForForm(v) =>

              if (! isVersioningSupported && v.version != 1)
                throw400ForUnsupportedVersion(v.version)

              val responseHeadersOpt =
                connectToObtainResponseHeadersAndCheckVersionPutOrDelete(
                  request                     = OutgoingRequest(request),
                  serviceUri                  = serviceUri,
                  outgoingPersistenceHeaders  = outgoingPersistenceHeaders,
                  specificIncomingVersionOpt  = Some(v.version),
                  queryForToken               = queryForToken,
                  mustFilterVersioningHeaders = false
                )._2

              (None, Some(v.version), responseHeadersOpt)

            case VersionAction.AcceptForData(vOpt) =>

              if (! isVersioningSupported)
                vOpt.foreach { v =>
                  if (v.version != 1)
                    throw400ForUnsupportedVersion(v.version)
                }

              val (cxrOpt, effectiveFormDefinitionVersion, responseHeadersOpt) =
                connectToObtainResponseHeadersAndCheckVersion(
                  request                    = OutgoingRequest(request),
                  serviceUri                 = serviceUri,
                  outgoingPersistenceHeaders = outgoingPersistenceHeaders,
                  specificIncomingVersionOpt = vOpt.map(_.version),
                  queryForToken              = queryForToken
                )

              // 2024-07-23: Here, we read the form permissions by calling `/fr/service/persistence/form`, then we
              // extract the `<permissions>` element if any, and obtain a `Permissions` object. I wondered why we
              // couldn't just use the `operations` attribute from the form metadata, and the answer is that we have
              // two ways of obtaining operations: one "without data" (see `authorizedOperationsForNoData()`), and one
              // with data. The latter is used here, and it requires the availability of the `<permissions>` element.
              val operations =
                try {
                  val formPermissions =
                    PersistenceMetadataSupport.readFormPermissionsMaybeWithAdminSupport(
                      PersistenceMetadataSupport.isInternalAdminUser(request.getFirstParamAsString),
                      appForm,
                      FormDefinitionVersion.Specific(effectiveFormDefinitionVersion)
                    )

                  PersistenceProxyPermissions.findAuthorizedOperationsOrThrow(
                    formPermissions    = formPermissions,
                    credentialsOpt     = findCurrentCredentialsFromSession,
                    crudMethod         = crudMethod,
                    appFormVersion     = (appForm, effectiveFormDefinitionVersion),
                    documentId         = documentIdOpt.getOrElse(throw new IllegalArgumentException), // for data there must be a `documentId`,
                    incomingTokenOpt   = incomingTokenOpt,
                    responseHeadersOpt = responseHeadersOpt, // `None` in case of non-existing data
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

              (cxrOpt, Some(effectiveFormDefinitionVersion), responseHeadersOpt)

            case VersionAction.HeadDataForFormGetOnly(v) =>

              // Create path directly to the correct provider so we avoid a callback to the proxy through the PFC
              val (persistenceBaseUrl, dataOutgoingPersistenceHeaders) = getPersistenceURLHeaders(appForm, FormOrData.Data)

              val dataServiceUri =
                PathUtils.recombineQuery(
                  persistenceBaseUrl.dropTrailingSlash                                                      ::
                    "crud"                                                                                  ::
                    FormRunner.createFormDataBasePathNoPrefix(appForm, None, v.isDraft, Some(v.documentId)) ::
                    DataXml                                                                                 ::
                    Nil mkString "/",
                  queryForToken
                )

              // This can throw if the connection fails (it is usually internal but can be external)
              // This can throw an `HttpStatusCodeException`, including for a 404
              val (cxr, effectiveFormDefinitionVersion, responseHeaders) =
                connectToObtainResponseHeadersAndCheckVersionGetOrHead(
                  request                    = OutgoingRequest(HttpMethod.HEAD, filterVersioningHeaders(OutgoingRequest.headersFromRequest(request))),
                  serviceUri                 = dataServiceUri,
                  outgoingPersistenceHeaders = dataOutgoingPersistenceHeaders,
                  specificIncomingVersionOpt = None
                )

              IOUtils.useAndClose(cxr)(identity)

              (None, Some(effectiveFormDefinitionVersion), Some(responseHeaders))
            case VersionAction.LatestForm if ! isVersioningSupported =>
              (None, Some(1), None)
            case VersionAction.NextForFormPutOnly if ! isVersioningSupported =>
              indentedLogger.logInfo("", s"400 Bad Request: request for next version and versioning not supported")
              throw HttpStatusCodeException(StatusCode.BadRequest)
            case VersionAction.NextForFormPutOnly =>
              PersistenceMetadataSupport.readLatestVersion(appForm) match {
                case Some(versionFromMetadata) =>
                  // There is at least one published form, and we will create the next version. We don't need to do a
                  // `HEAD` on this form, since it doesn't exist: either it was never created in the database, or it
                  // was but was deleted, in which case it is ok to start over with a new creator, etc. This behavior
                  // might be different from when we were reading the `existingRow` in the provider upon `PUT`.
                  (None, Some(versionFromMetadata + 1), None)
                case None =>
                  // No form published, start with 1
                  (None, Some(1), None)
              }
            case VersionAction.LatestForm =>
              PersistenceMetadataSupport.readLatestVersion(appForm) match {
                case Some(versionFromMetadata) if crudMethod == HttpMethod.GET || crudMethod == HttpMethod.HEAD =>
                  // There is at least one published form
                  // We now know the version and return it, but we let the rest of the proxying happen further below
                  (None, Some(versionFromMetadata), None)
                case Some(versionFromMetadata) =>
                  // There is at least one published form
                  // We need to obtain the response headers which tell us the existing metadata, so that we can pass
                  // it down to the provider, so that the `PUT`/`DELETE` can propagate the metadata.

                  val (persistenceBaseUrl, dataOutgoingPersistenceHeaders) = getPersistenceURLHeaders(appForm, FormOrData.Form)

                  val serviceUri =
                    PathUtils.recombineQuery(
                      persistenceBaseUrl.dropTrailingSlash                             ::
                        "crud"                                                         ::
                        FormRunner.createFormDefinitionBasePathNoPrefix(appForm, None) ::
                        FormXhtml                                                      ::
                        Nil mkString "/",
                      queryForToken
                    )

                  val outgoingVersionHeader =
                    Version.OrbeonFormDefinitionVersion -> versionFromMetadata.toString

                  val (cxr, _, responseHeaders) =
                    connectToObtainResponseHeadersAndCheckVersionGetOrHead(
                      request                    = OutgoingRequest(HttpMethod.HEAD, filterVersioningHeaders(OutgoingRequest.headersFromRequest(request))),
                      serviceUri                 = serviceUri,
                      outgoingPersistenceHeaders = dataOutgoingPersistenceHeaders + outgoingVersionHeader,
                      specificIncomingVersionOpt = Some(versionFromMetadata)
                    )

                  IOUtils.useAndClose(cxr)(identity)

                  (None, Some(versionFromMetadata), Some(responseHeaders))
                case None =>
                  // No form published, start with 1
                  (None, Some(1), None)
              }
            case VersionAction.Ignore =>
              (None, None, None)
          }
        case _ =>
          (None, None, None)
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

    // https://github.com/orbeon/orbeon-forms/issues/5741
    val existingFormOrDataHeaders =
      responseHeadersOpt.map(ResponseHeaders.toHeaders).getOrElse(Nil)

    val bodyContentOpt =
      bodyContent(
        request          = request,
        isDataXmlRequest = isDataXmlRequest,
        isFormBuilder    = isFormBuilder,
        appForm          = appForm,
        encrypt          = formOrData match {
          case FormOrData.Form                  => false // don't encrypt form definitions
          case FormOrData.Data if isFormBuilder => false // don't encrypt Form Builder form data
          case FormOrData.Data                  => true
        }
      )

    val attachmentsProviderCxrOpt = attachmentsProviderCxr(
      isAttachment,
      request,
      appForm,
      formOrData,
      path,
      bodyContentOpt,
      outgoingVersionHeaderOpt
    )

    val cxr = cxrOpt.getOrElse {
      // If an attachments provider is available, always use it to store the actual attachment
      val outgoingRequestContent = if (attachmentsProviderCxrOpt.isDefined && request.getMethod == HttpMethod.PUT) {
        // Body content processed by attachments provider, no need to pass it further
        None
      } else {
        bodyContentOpt
      }

      proxyEstablishConnection(
        OutgoingRequest(request),
        outgoingRequestContent,
        serviceUri,
        outgoingPersistenceHeaders ++ outgoingVersionHeaderOpt ++ existingFormOrDataHeaders
      )
    }

    // A connection might have been opened above, and if so we use it
    proxyRequestImpl(
      cxr,
      request,
      response,
      responseTransforms,
      attachmentsProviderCxrOpt
    )
  }

  private def bodyContent(
    request         : Request,
    isDataXmlRequest: Boolean,
    isFormBuilder   : Boolean,
    appForm         : AppForm,
    encrypt         : Boolean
  )(implicit
    indentedLogger: IndentedLogger
  ): Option[StreamedContent] =
    HttpMethod.HttpMethodsWithRequestBody(request.getMethod).option {

      val withEncryptionOpt =
        encrypt.flatOption {
          FieldEncryption.encryptDataIfNecessary(
            request,
            appForm,
            isDataXmlRequest
          )
        }

      val (bodyInputStream, bodyContentLength) =
        withEncryptionOpt.getOrElse(request.getInputStream -> request.contentLengthOpt)

      StreamedContent(
        bodyInputStream,
        Option(request.getContentType),
        bodyContentLength,
        None
      )
    }

  private def attachmentsProviderCxr(
    isAttachment            : Boolean,
    request                 : Request,
    appForm                 : AppForm,
    formOrData              : FormOrData,
    path                    : String,
    streamedContent         : Option[StreamedContent],
    outgoingVersionHeaderOpt: Option[(String, String)]
  )(implicit
    indentedLogger          : IndentedLogger
  ): Option[ConnectionResult] =
    if (isAttachment) {
      findAttachmentsProvider(appForm, formOrData).map { provider =>
        val (baseURI, headers) = getPersistenceURLHeadersFromProvider(provider)
        val serviceURI         = baseURI + path

        proxyEstablishConnection(OutgoingRequest(request), streamedContent, serviceURI, headers ++ outgoingVersionHeaderOpt)
      }
    } else {
      None
    }

  private def compareVersionAgainstIncomingIfNeeded(
    indentedLogger                    : IndentedLogger,
    effectiveFormDefinitionVersionOpt : Option[Int],
    responseHeaders                   : ResponseHeaders
  ): Int = {
    val versionFromProvider = responseHeaders.formVersion.getOrElse(1)
    effectiveFormDefinitionVersionOpt.foreach { incomingVersion =>
      if (incomingVersion != versionFromProvider) {
        indentedLogger.logInfo("",
          s"400 Bad Request: incoming version ($incomingVersion) " +
          s"doesn't match version from provider ($versionFromProvider)")
        throw HttpStatusCodeException(StatusCode.BadRequest)
      }
    }
    versionFromProvider
  }

  private def connectToObtainResponseHeadersAndCheckVersion(
    request                    : OutgoingRequest,
    serviceUri                 : String,
    outgoingPersistenceHeaders : Map[String, String],
    specificIncomingVersionOpt : Option[Int],
    queryForToken              : List[(String, String)]
  )(implicit
    indentedLogger             : IndentedLogger
  ): (Option[ConnectionResult], Int, Option[ResponseHeaders]) =
    request.method match {
      case HttpMethod.GET | HttpMethod.HEAD =>

        val (cxr, versionFromProvider, responseHeaders) =
          connectToObtainResponseHeadersAndCheckVersionGetOrHead(
            request                    = request,
            serviceUri                 = serviceUri,
            outgoingPersistenceHeaders = outgoingPersistenceHeaders,
            specificIncomingVersionOpt = specificIncomingVersionOpt
          )

        (Some(cxr), versionFromProvider, Some(responseHeaders))
      case HttpMethod.PUT | HttpMethod.DELETE =>

        val (versionForPutOrDelete, responseHeadersOpt) =
          connectToObtainResponseHeadersAndCheckVersionPutOrDelete(
            request                     = request,
            serviceUri                  = serviceUri,
            outgoingPersistenceHeaders  = outgoingPersistenceHeaders,
            specificIncomingVersionOpt  = specificIncomingVersionOpt,
            queryForToken               = queryForToken,
            mustFilterVersioningHeaders = true
          )

        (None, versionForPutOrDelete, responseHeadersOpt)
      case other =>
        throw new IllegalStateException(other.entryName)
    }

  private def connectToObtainResponseHeadersAndCheckVersionGetOrHead(
    request                    : OutgoingRequest,
    serviceUri                 : String,
    outgoingPersistenceHeaders : Map[String, String],
    specificIncomingVersionOpt : Option[Int]
  )(implicit
    indentedLogger             : IndentedLogger
  ): (ConnectionResult, Int, ResponseHeaders) = {

    val cxr =
      ConnectionResult.trySuccessConnection(
        proxyEstablishConnection(request, None, serviceUri, outgoingPersistenceHeaders)
      ).get // throws `HttpStatusCodeException` if not successful, including `NotFound`

    val responseHeaders     = ResponseHeaders.fromHeaders(cxr.getFirstHeaderIgnoreCase)
    val versionFromProvider = compareVersionAgainstIncomingIfNeeded(indentedLogger, specificIncomingVersionOpt, responseHeaders)

    (cxr, versionFromProvider, responseHeaders)
  }

  private def connectToObtainResponseHeadersAndCheckVersionPutOrDelete(
    request                    : OutgoingRequest,
    serviceUri                 : String,
    outgoingPersistenceHeaders : Map[String, String],
    specificIncomingVersionOpt : Option[Int],
    queryForToken              : List[(String, String)],
    mustFilterVersioningHeaders: Boolean // will be `true` for data, `false` for form
  )(implicit
    indentedLogger             : IndentedLogger
  ): (Int, Option[ResponseHeaders]) = { // `ResponseHeaders` are `None` in case of non-existing data

      // The call to `HEAD` will require permissions so we need to forward the token
      val headServiceUri =
        PathUtils.recombineQuery(serviceUri, queryForToken)

      getHeadersFromHeadCallProviderViaProxy(
        request.headers,
        headServiceUri,
        outgoingPersistenceHeaders,
        mustFilterVersioningHeaders
      ) match {
        case Success(responseHeaders) =>
          // Existing data
          val versionFromProvider = compareVersionAgainstIncomingIfNeeded(indentedLogger, specificIncomingVersionOpt, responseHeaders)
          // NOTE: If we get here, we have already passed successful permissions checks for `GET`/`HEAD` AKA
          // `Read` and we must have the allowed operations. However here we are doing a `PUT`/`DELETE` AKA
          // `Update`/`Delete` and we need to check operations again below. But we could skip actually getting
          // the permissions! We also need `ResponseHeaders` to include `Orbeon-Operations`.
          // TODO: Check above optimization.
          (versionFromProvider, Some(responseHeaders))
        case Failure(HttpStatusCodeException(statusCode @ (StatusCode.NotFound | StatusCode.Gone), _, _)) =>
          // Non-existing data
          specificIncomingVersionOpt match {
            case Some(version) if request.method == HttpMethod.PUT =>
              (version, None)
            case None if request.method == HttpMethod.PUT =>
              indentedLogger.logInfo("", s"400 Bad Request: can't write new data without knowing the version")
              throw HttpStatusCodeException(StatusCode.BadRequest)
            case _ =>
              // We wouldn't need a version to delete data *except* that we need to get form permissions!
              throw HttpStatusCodeException(statusCode)
          }
        case Failure(t) =>
          throw t
      }
    }

  private def checkDataFormatVersionIfNeeded(
    indentedLogger : IndentedLogger,
    request        : Request,
    appForm        : AppForm,
    formOrData     : FormOrData
  ): Unit =
    if (formOrData == FormOrData.Data && GetOrPutMethods(request.getMethod))
      // https://github.com/orbeon/orbeon-forms/issues/4861
      request.getFirstParamAsString(DataFormatVersionName) foreach { incomingVersion =>

        val dataFormatVersionSupportedByProvider =
          providerDataFormatVersionOrThrow(appForm).entryName

        require(
          AllowedDataFormatVersionParams(incomingVersion),
          s"`$FormRunnerPersistence.DataFormatVersionName` parameter must be one of ${AllowedDataFormatVersionParams mkString ", "}"
        )

        // We can remove this once we are able to perform conversions here, see:
        // https://github.com/orbeon/orbeon-forms/issues/3110
        if (! Set(RawDataFormatVersion, dataFormatVersionSupportedByProvider)(incomingVersion)) {
          indentedLogger.logInfo("",
            s"400 Bad Request: incoming data format version ($incomingVersion) is neither `raw`" +
            s"nor the data format version supported by the provider ($dataFormatVersionSupportedByProvider)")
          throw HttpStatusCodeException(StatusCode.BadRequest)
        }
      }

  private def parsePruneAndSerializeXmlData(
    is: InputStream,
    os: OutputStream
  ): Unit = {

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
    cxr                       : ConnectionResult,
    request                   : Request,
    response                  : Response,
    responseTransforms        : List[(InputStream, OutputStream) => Unit],
    attachmentsProviderCxrOpt : Option[ConnectionResult]
  ): Unit =
    IOUtils.useAndClose(cxr) { connectionResult =>
      // Proxy status code
      response.setStatus(connectionResult.statusCode)

      // Proxy incoming headers
      proxyCapitalizeAndCombineHeaders(connectionResult.headers, request = false) foreach (response.setHeader _).tupled

      request.getMethod match {
        case HttpMethod.GET | HttpMethod.HEAD if StatusCode.isSuccessCode(connectionResult.statusCode) =>
          // Forward HTTP range headers and status to client
          attachmentsProviderCxrOpt.foreach { attachmentsProviderCxr =>
            HttpRanges.forwardRangeHeaders(attachmentsProviderCxr, response)
            response.setStatus(attachmentsProviderCxr.statusCode)
          }

        case _ =>
      }

      // Proxy content type
      val responseContentType = connectionResult.content.contentType
      responseContentType.foreach(response.setHeader(Headers.ContentType, _))

      @tailrec
      def applyTransforms(
        startInputStream: InputStream,
        endOutputStream : OutputStream,
        transforms      : List[(InputStream, OutputStream) => Unit]
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

      // If an attachments provider is available, always use it to retrieve the actual attachment
      val inputStream = attachmentsProviderCxrOpt match {
        case Some(attachmentsProviderCxr) if request.getMethod == HttpMethod.GET &&
                                             StatusCode.isSuccessCode(connectionResult.statusCode) =>
          attachmentsProviderCxr.content.stream

        case _ =>
          connectionResult.content.stream
      }

      try {
        applyTransforms(
          inputStream,
          response.getOutputStream,
          if (doTransforms) responseTransforms else Nil
        )
      } finally {
        attachmentsProviderCxrOpt.foreach(_.close())
      }
    }

  // Unneeded for JVM platform
  private implicit val resourceResolver: Option[ResourceResolver] = None

  protected def proxyEstablishConnection(
    request        : OutgoingRequest,
    requestContent : Option[StreamedContent],
    uri            : String,
    headers        : Map[String, String],
    credentials    : Option[BasicCredentials] = None
  )(implicit
    indentedLogger : IndentedLogger
  ): ConnectionResult = {

    implicit val externalContext: ExternalContext    = NetUtils.getExternalContext
    implicit val safeRequestCtx : SafeRequestContext = SafeRequestContext(externalContext)

    val outgoingURL =
      URI.create(URLRewriterUtils.rewriteServiceURL(externalContext.getRequest, uri, UrlRewriteMode.Absolute))

    val persistenceHeaders =
      for ((name, value) <- headers)
      yield capitalizeCommonOrSplitHeader(name) -> List(value)

    // Forwards all incoming headers, with exceptions like connection headers and, importantly, cookie headers
    val proxiedHeaders =
      proxyAndCapitalizeHeaders(request.headers, request = true)

    val allHeaders =
      Connection.buildConnectionHeadersCapitalizedIfNeeded(
        url              = outgoingURL,
        hasCredentials   = false,
        customHeaders    = proxiedHeaders.toMap ++ persistenceHeaders, // give precedence to persistence headers
        headersToForward = Set.empty,                                  // handled by proxyAndCapitalizeHeaders()
        cookiesToForward = Connection.cookiesToForwardFromProperty,    // NOT handled by proxyAndCapitalizeHeaders()
        getHeader        = request.headers.get
      )

    if (! SupportedMethods(request.method))
      throw HttpStatusCodeException(StatusCode.MethodNotAllowed)

    Connection.connectNow(
      method      = request.method,
      url         = outgoingURL,
      credentials = credentials,
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
    request          : Request, // for params, headers, and method
    response         : Response,
    appFormFromUrlOpt: Option[AppFormOpt]
  )(implicit
    indentedLogger   : IndentedLogger
  ): Unit =
    if (request.getMethod == HttpMethod.GET || request.getMethod == HttpMethod.POST) {
      Try(localAndRemoteFormsMetadata(request, appFormFromUrlOpt)) match {
        case Success(nodeInfo) =>
          streamDocument(nodeInfo, response)

        case Failure(t) =>
          // TODO: the API should return detailed error messages in the body itself
          indentedLogger.logError("", s"Form Metadata API error: ${t.getMessage}")
          throw t
      }
    } else {
      throw HttpStatusCodeException(StatusCode.MethodNotAllowed)
    }

  private def proxyReindex(
    request       : Request,
    response      : Response
  )(implicit
    indentedLogger: IndentedLogger
  ): Unit = {
    val dataProviders = getProviders(appOpt = None, formOpt = None, FormOrData.Data)

    val dataProvidersWithIndexSupport =
      dataProviders.filter { provider =>
        FormRunner.providerPropertyAsBoolean(provider, "reindex", default = false)
      }
    Backend.reindexingProviders(
      dataProvidersWithIndexSupport, p => {

        val (baseURI, outgoingPersistenceHeaders) = getPersistenceURLHeadersFromProvider(p)
        val serviceURI = baseURI + "/reindex"

        proxyRequestImpl(
          proxyEstablishConnection(OutgoingRequest(request), None, serviceURI, outgoingPersistenceHeaders),
          request,
          response,
          Nil,
          None
        )
      }
    )
  }

  private def proxyReEncryptStatus(
    request       : Request,
    response      : Response
  )(implicit
    indentedLogger: IndentedLogger
  ): Unit = {

    val dataProviders = getProviders(appOpt = None, formOpt = None, FormOrData.Data)

    val dataProvidersWithReEncryptSupport =
      dataProviders.filter { provider =>
        FormRunner.providerPropertyAsBoolean(provider, "reencrypt", default = false)
      }

    streamDocument(
      aggregateDocument(
        root    = "forms",
        content =
          dataProvidersWithReEncryptSupport.flatMap { provider =>
            val (baseURI, headers) = getPersistenceURLHeadersFromProvider(provider)
            val serviceURI         = baseURI + "/reencrypt"
            val cxr                = proxyEstablishConnection(OutgoingRequest(request), None, serviceURI, headers)
            ConnectionResult.withSuccessConnection(cxr, closeOnSuccess = true) { is =>
              val forms = TransformerUtils.readTinyTree(XPath.GlobalConfiguration, is, serviceURI, false, false)
              forms / "forms" / "form"
            }
          }
      ),
      response
    )
  }

  private def aggregateDocument(
    root   : String,
    content: List[NodeInfo]
  ): NodeInfo = {
    val documentElement = elementInfo(root)
    XFormsAPI.insert(into = documentElement, origin = content)
    documentElement
  }

  private def streamDocument(
    documentElement: NodeInfo,
    response       : Response
  ): Unit = {
    response.setContentType(ContentTypes.XmlContentType)
    TransformerUtils.getXMLIdentityTransformer.transform(documentElement, new StreamResult(response.getOutputStream))
  }

  private def getHeadersFromHeadCallProviderViaProxy(
    requestHeaders             : Map[String, List[String]],
    serviceURI                 : String,
    outgoingPersistenceHeaders : Map[String, String],
    mustFilterVersioningHeaders: Boolean // will be `true` for data, `false` for form
  )(implicit
    indentedLogger             : IndentedLogger
  ): Try[ResponseHeaders] = {

    val headRequest =
      OutgoingRequest(
        method  = HttpMethod.HEAD,
        headers =
          if (mustFilterVersioningHeaders)
            filterVersioningHeaders(requestHeaders) // case of data: filter out incoming versioning headers as we want to hit the `Version.Unspecified` case
          else
            requestHeaders
      )

    // `HEAD` directly through the proxy!
    val getHeaderIgnoreCaseFromHeadResponseTry =
      IOUtils.useAndClose(proxyEstablishConnection(headRequest, None, serviceURI, outgoingPersistenceHeaders)) { cxr =>
        ConnectionResult.trySuccessConnection(cxr).map(cxr => cxr.getFirstHeaderIgnoreCase _)
      }

    getHeaderIgnoreCaseFromHeadResponseTry match {
      case Success(getHeaderIgnoreCaseFromHeadResponse) =>
        Success(ResponseHeaders.fromHeaders(getHeaderIgnoreCaseFromHeadResponse))
      case Failure(t @ HttpStatusCodeException(StatusCode.NotFound | StatusCode.Gone, _, _)) =>
        Failure(t)
      case Failure(t) =>
        Failure(t)
    }
  }

  private def filterVersioningHeaders(headers: Map[String, List[String]]): Map[String, List[String]] =
    headers.view.filterKeys(k => ! Version.AllVersionHeadersLower(k.toLowerCase)).to(Map)

  private[persistence] def requestInputStream(request: => Request): InputStream =
    RequestGenerator.getRequestBody(PipelineContext.get) match {
      case Some(bodyURL) => NetUtils.uriToInputStream(bodyURL)
      case None          => request.getInputStream
    }
}
