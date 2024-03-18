package org.orbeon.oxf.fr.persistence.api

import cats.syntax.option._
import org.orbeon.connection.{ConnectionResult, StreamedContent}
import org.orbeon.io.CharsetNames
import org.orbeon.oxf.externalcontext.ExternalContext.Request
import org.orbeon.oxf.externalcontext.{ExternalContext, RequestAdapter, UrlRewriteMode}
import org.orbeon.oxf.fr.FormRunner.{createFormDataBasePath, providerPropertyAsBoolean}
import org.orbeon.oxf.fr.FormRunnerParams.AppFormVersion
import org.orbeon.oxf.fr.FormRunnerPersistence.{DataXml, FormXhtml, findProvider}
import org.orbeon.oxf.fr._
import org.orbeon.oxf.fr.persistence.relational.Version
import org.orbeon.oxf.fr.persistence.relational.Version.OrbeonFormDefinitionVersion
import org.orbeon.oxf.http._
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.util.Logging._
import org.orbeon.oxf.util.StaticXPath.DocumentNodeInfoType
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.util.{Connection, ContentTypes, CoreCrossPlatformSupportTrait, IndentedLogger, PathUtils, URLRewriterUtils, XPath}
import org.orbeon.saxon.om
import org.orbeon.scaxon.SimplePath._
import org.orbeon.xforms.XFormsCrossPlatformSupport

import java.net.URI
import java.time.Instant
import java.{util => ju}
import scala.util.Try
import scala.xml.Elem


// Provide a simple API to access the persistence layer from Scala
//
// This uses `Connection.connectNow()`, through `connectPersistence()`. which create an internal `Connection`. This
// results eventually in calling a page flow and then `PersistenceProxyProcessor`. So there is overhead. Possibly
// `connectPersistence()` could call directly `PersistenceProxyProcessor` instead.
trait PersistenceApiTrait {

  private val SearchPageSize = 100

  case class DataDetails(
    createdTime : Instant,
    modifiedTime: Instant,
    documentId  : String,
    isDraft     : Boolean
  )

  case class DistinctValues(
     controls      : Seq[Control],
     createdBy     : Seq[String],
     lastModifiedBy: Seq[String],
     workflowStage : Seq[String]
  )

  case class Control(path: String, distinctValues: Seq[String])

  case class DataHistoryDetails(

    total              : Int,
    minLastModifiedTime: Option[Instant],
    maxLastModifiedTime: Option[Instant],

    modifiedTime       : Instant,
    modifiedUsername   : Option[String],
    ownerUsername      : Option[String],
    ownerGroup         : Option[String],
    stage              : Option[String],
    isDeleted          : Boolean
  )

  private def documentsXmlTry(
    servicePath             : String,
    queryXml                : Elem,
    version                 : Int
  )(implicit
    logger                  : IndentedLogger,
    coreCrossPlatformSupport: CoreCrossPlatformSupportTrait
  ): Try[DocumentNodeInfoType] =
    ConnectionResult.tryWithSuccessConnection(
      connectPersistence(
        method             = HttpMethod.POST,
        pathQuery          = servicePath,
        requestBodyContent = StreamedContent.fromBytes(queryXml.toString.getBytes(CharsetNames.Utf8), ContentTypes.XmlContentType.some).some,
        formVersionOpt     = version.some
      ),
      closeOnSuccess = true
    ) { is =>
      XFormsCrossPlatformSupport.readTinyTree(
        XPath.GlobalConfiguration,
        is,
        servicePath,
        handleXInclude = false,
        handleLexical = false
      )
    }

  // Largely more time than needed for an internal request to come through
  private val DefaultTokenValidity = java.time.Duration.ofSeconds(20)

  def createInternalAdminUserTokenParam(
    isInternalAdminUser: Boolean
  )(implicit
    coreCrossPlatformSupport: CoreCrossPlatformSupportTrait
  ): Option[(String, String)] =
    isInternalAdminUser
      .flatOption(
        createInternalAdminUserToken(coreCrossPlatformSupport.externalContext.getRequest.getRequestPath)
          .map(FormRunner.InternalAdminTokenParam -> _)
      )

  def createInternalAdminUserToken(requestPath: String): Option[String] = {

    // Only whitelisted callers can use this function
    requestPath match {
      case "/fr/admin"         =>
      case _                   => throw HttpStatusCodeException(StatusCode.Forbidden)
    }

    FormRunnerAdminToken.encryptToken(
      validity            = DefaultTokenValidity,
      isInternalAdminUser = true
    )
  }

  def search(
    appFormVersion     : AppFormVersion,
    isInternalAdminUser: Boolean,
  )(implicit
    logger             : IndentedLogger,
    coreCrossPlatformSupport: CoreCrossPlatformSupportTrait
  ): Iterator[DataDetails] = {

    debug(s"calling search for `$appFormVersion`")

    val servicePathQuery =
      PathUtils.recombineQuery(
        s"/fr/service/persistence/search/${appFormVersion._1.app}/${appFormVersion._1.form}",
        createInternalAdminUserTokenParam(isInternalAdminUser).toList,
        overwrite = true
      )

    def readPage(pageNumber: Int): Try[DocumentNodeInfoType] = {

      debug(s"reading search page `$pageNumber`")

      val queryXml =
        <search>
            <query/>
            <page-size>{SearchPageSize}</page-size>
            <page-number>{pageNumber}</page-number>
        </search>

      documentsXmlTry(servicePathQuery, queryXml, appFormVersion._2)
    }

    def pageToDataDetails(page: DocumentNodeInfoType): (Iterator[DataDetails], Int, Int) =
      (
        (page.rootElement / "document").iterator map { documentElem =>
          DataDetails(
            createdTime      = Instant.parse(documentElem.attValue("created")),
            modifiedTime = Instant.parse(documentElem.attValue("last-modified")),
            documentId       = documentElem.attValue("name"),
            isDraft          = documentElem.attValue("draft") == "true"
          )
        },
        page.rootElement.attValue("search-total").toInt,
        (page.rootElement / "document").size
      )

    callPagedService(pageNumber => readPage(pageNumber).map(pageToDataDetails))
  }

  def distinctValues(
    appFormVersion          : AppFormVersion,
    controlPaths            : Seq[String])(implicit
    logger                  : IndentedLogger,
    coreCrossPlatformSupport: CoreCrossPlatformSupportTrait
  ): DistinctValues = {

    debug(s"calling distinct values for `$appFormVersion`")

    val provider = findProvider(appFormVersion._1, FormOrData.Data).get

    if (providerPropertyAsBoolean(provider, "distinct", default  = false)) {
      val servicePath = s"/fr/service/persistence/distinct-values/${appFormVersion._1.app}/${appFormVersion._1.form}"

      // Ideally, we'd like to use the distinct values ADT, but we don't depend on it from here
      val CreatedBy      = "created-by"
      val LastModifiedBy = "last-modified-by"
      val WorkflowStage  = "workflow-stage"

      val metadataValues = Seq(CreatedBy, LastModifiedBy, WorkflowStage)

      val queryXml =
        <distinct-values>{
          controlPaths.map { controlPath =>
            <query path={controlPath}/>
          } ++
          metadataValues.map { metadata =>
            <query metadata={metadata}/>
          }
        }</distinct-values>

      val result = documentsXmlTry(servicePath, queryXml, appFormVersion._2).get.rootElement

      val controls = for {
        query <- result / "query"
        path  <- query.attValueOpt("path")
      } yield Control(
        path           = path,
        distinctValues = (query / "value").map(_.getStringValue)
      )

      val metadata = (for {
        query    <- result / "query"
        metadata <- query.attValueOpt("metadata")
        if metadataValues.contains(metadata)
      } yield metadata -> (query / "value").map(_.getStringValue)).toMap

      DistinctValues(
        controls       = controls,
        createdBy      = metadata.getOrElse(CreatedBy     , Seq.empty),
        lastModifiedBy = metadata.getOrElse(LastModifiedBy, Seq.empty),
        workflowStage  = metadata.getOrElse(WorkflowStage , Seq.empty))
    } else {
      // For providers that don't support distinct values, we return empty lists
      DistinctValues(
        controls       = Seq.empty,
        createdBy      = Seq.empty,
        lastModifiedBy = Seq.empty,
        workflowStage  = Seq.empty)
      }
  }

  def dataHistory(
    appForm              : AppForm,
    documentId           : String,
    attachmentFilenameOpt: Option[String],
    isInternalAdminUser  : Boolean,
  )(implicit
    logger                  : IndentedLogger,
    coreCrossPlatformSupport: CoreCrossPlatformSupportTrait
  ): Iterator[DataHistoryDetails] = {

    debug(s"calling data history for `$appForm`/`$documentId`")

    val servicePathQuery =
      PathUtils.recombineQuery(
        s"/fr/service/persistence/history/${appForm.app}/${appForm.form}/$documentId${attachmentFilenameOpt.map(f => s"/$f").getOrElse("")}",
        createInternalAdminUserTokenParam(isInternalAdminUser).toList,
        overwrite = true
      )

    def readPage(pageNumber: Int): Try[DocumentNodeInfoType] = {

      debug(s"reading data history page `$pageNumber`")

      val servicePathParams =
        PathUtils.recombineQuery(servicePathQuery, List("page-size" -> SearchPageSize.toString, "page-number" -> pageNumber.toString))

      val documentsXmlTry =
        ConnectionResult.tryWithSuccessConnection(
          connectPersistence(
            method    = HttpMethod.GET,
            pathQuery = servicePathParams
          ),
          closeOnSuccess = true
        ) { is =>
          XFormsCrossPlatformSupport.readTinyTree(
            XPath.GlobalConfiguration,
            is,
            servicePathParams,
            handleXInclude = false,
            handleLexical  = false
          )
        }

      documentsXmlTry
    }

    def pageToDataDetails(page: DocumentNodeInfoType): (Iterator[DataHistoryDetails], Int, Int) = {

      val rootElem = page.rootElement

      val total        = rootElem.attValue("total").toInt
      val minLastModifiedTime = rootElem.attValue("min-last-modified-time").trimAllToOpt
      val maxLastModifiedTime = rootElem.attValue("max-last-modified-time").trimAllToOpt

      val currentTotal = (rootElem / "document").size

      (
        (page.rootElement / "document").iterator map { documentElem =>
          DataHistoryDetails(
            total               = total,
            minLastModifiedTime = minLastModifiedTime.map(Instant.parse),
            maxLastModifiedTime = maxLastModifiedTime.map(Instant.parse),
            modifiedTime        = Instant.parse(documentElem.attValue("modified-time")),
            modifiedUsername    = documentElem.attValueOpt("modified-username").flatMap(_.trimAllToOpt),
            ownerUsername       = documentElem.attValueOpt("owner-username").flatMap(_.trimAllToOpt),
            ownerGroup          = documentElem.attValueOpt("owner-group").flatMap(_.trimAllToOpt),
            stage               = documentElem.attValueOpt("stage").flatMap(_.trimAllToOpt),
            isDeleted           = documentElem.attValue("deleted") == true.toString,
          )
        },
        total,
        currentTotal
      )
    }

    callPagedService(pageNumber => readPage(pageNumber).map(pageToDataDetails))
  }

  def readFormData(
    appFormVersion     : AppFormVersion,
    documentId         : String,
    lastModifiedTime   : Option[Instant],
    isInternalAdminUser: Boolean,
  )(implicit
    logger                  : IndentedLogger,
    coreCrossPlatformSupport: CoreCrossPlatformSupportTrait
  ): Try[((Map[String, List[String]], DocumentNodeInfoType), String)] = {

    debug(s"reading form data for `$appFormVersion`/`$documentId`/`$lastModifiedTime`")

    val path = PathUtils.recombineQuery(
      FormRunner.createFormDataBasePath(appFormVersion._1.app, appFormVersion._1.form, isDraft = false, documentId) + DataXml,
      lastModifiedTime.toList.map("last-modified-time" -> _.toString) :::
      createInternalAdminUserTokenParam(isInternalAdminUser).toList,
      overwrite = true
    )
    val customHeaders = Map(OrbeonFormDefinitionVersion -> List(appFormVersion._2.toString))

    readHeadersAndDocument(path, customHeaders).map(_ -> path)
  }

  def readDocumentFormVersion(
    appName         : String,
    formName        : String,
    documentId      : String,
    isDraft         : Boolean
  )(implicit
    logger                  : IndentedLogger,
    coreCrossPlatformSupport: CoreCrossPlatformSupportTrait
  ): Option[Int] = {
    val path = createFormDataBasePath(appName, formName, isDraft, documentId) + DataXml
    val headers = readHeaders(path, Map.empty)
    headers.get(Version.OrbeonFormDefinitionVersion).map(_.head).map(_.toInt)
  }

  // Retrieves a form definition from the persistence layer
  def readPublishedFormDefinition(
    appName  : String,
    formName : String,
    version  : FormDefinitionVersion
  )(implicit
    logger                  : IndentedLogger,
    coreCrossPlatformSupport: CoreCrossPlatformSupportTrait
  ): Try[((Map[String, List[String]], DocumentNodeInfoType), String)] = {

    debug(s"reading published form definition for `$appName`/`$formName`/`$version`")

    val path = FormRunner.createFormDefinitionBasePath(appName, formName) + FormXhtml
    val customHeaders = version match {
      case FormDefinitionVersion.Latest            => Map.empty[String, List[String]]
      case FormDefinitionVersion.Specific(version) => Map(OrbeonFormDefinitionVersion -> List(version.toString))
    }

    readHeadersAndDocument(path, customHeaders).map(_ -> path)
  }

  // TODO: This should return a `Try`. Right now it throws if the document cannot be read.
  // Retrieves from the persistence layer the metadata for a form, return an `Option[<form>]`
  def readFormMetadataOpt(
    appForm : AppForm,
    version : FormDefinitionVersion
  )(implicit
    logger                  : IndentedLogger,
    coreCrossPlatformSupport: CoreCrossPlatformSupportTrait
  ): Option[om.NodeInfo] = {

    val formsDocTry = readHeadersAndDocument(
      FormRunner.createFormMetadataPathAndQuery(
        app         = appForm.app,
        form        = appForm.form,
        allVersions = version != FormDefinitionVersion.Latest,
        allForms    = true
      ),
      Map.empty
    ).map(_._2)

    formsDocTry map { formsDoc =>
      val formElements = formsDoc / "forms" / "form"
      val formByVersion = version match {
        case FormDefinitionVersion.Specific(v) =>
          formElements.find(_.child("form-version").stringValue == v.toString)
        case FormDefinitionVersion.Latest =>
          None
      }

      formByVersion.orElse(formElements.headOption)
    }
  } .get

  def doDelete(
    path               : String,
    modifiedTimeOpt    : Option[Instant],
    forceDelete        : Boolean,
    isInternalAdminUser: Boolean
  )(implicit
    logger                  : IndentedLogger,
    coreCrossPlatformSupport: CoreCrossPlatformSupportTrait
  ): Option[Instant] = { // `None` indicates that the document was not found

    // xxx TODO: see what proxy will do wrt version and checking existence of previous doc

    val pathWithParams =
      PathUtils.recombineQuery(
        path,
        (forceDelete list ("force-delete" -> true.toString)) :::
        createInternalAdminUserTokenParam(isInternalAdminUser).toList :::
        modifiedTimeOpt.toList.map(modifiedTime => "last-modified-time" -> modifiedTime.toString),
        // Overwrite last-modified-time parameter (might already exist if we got the URL from the history API)
        overwrite = true
      )

    val cxr =
      connectPersistence(
        method         = HttpMethod.DELETE,
        pathQuery      = pathWithParams,
        formVersionOpt = None // xxx TODO
      )

    if (cxr.statusCode == StatusCode.NotFound) {
      cxr.close()
      None
    } else {
      val lastModificationDateOpt = ConnectionResult.tryWithSuccessConnection(cxr, closeOnSuccess = true) { _ =>
        headerFromRFC1123OrIso(cxr.headers, Headers.OrbeonLastModified, Headers.LastModified)
      }.get // can throw

      if (lastModificationDateOpt.isEmpty && ! forceDelete) {
        // require implementation to return modified date, unless we're force deleting
        throw HttpStatusCodeException(StatusCode.InternalServerError)
      }

      lastModificationDateOpt
    }
  }

  def headerFromRFC1123OrIso(
    headers          : Map[String, List[String]],
    isoHeaderName    : String,
    rfc1123HeaderName: String
  ): Option[Instant] =
    Headers.firstItemIgnoreCase(headers, isoHeaderName).map(Instant.parse)
      .orElse(DateHeaders.firstDateHeaderIgnoreCase(headers, rfc1123HeaderName).map(Instant.ofEpochMilli))

  // TODO: call proxy directly?
  def connectPersistence(
    method            : HttpMethod,
    pathQuery         : String,
    requestBodyContent: Option[StreamedContent]   = None,
    formVersionOpt    : Option[Int]               = None,
    customHeaders     : Map[String, List[String]] = Map.empty
  )(implicit
    logger                  : IndentedLogger,
    coreCrossPlatformSupport: CoreCrossPlatformSupportTrait
  ): ConnectionResult = {

    implicit val ec: ExternalContext = coreCrossPlatformSupport.externalContext

    val resolvedUri =
      URI.create(
        URLRewriterUtils.rewriteServiceURL(
          ec.getRequest,
          pathQuery,
          UrlRewriteMode.Absolute
        )
      )

    val allHeaders =
      Connection.buildConnectionHeadersCapitalizedIfNeeded(
        url              = resolvedUri,
        hasCredentials   = false,
        customHeaders    = customHeaders ++ (formVersionOpt.toList map (v => OrbeonFormDefinitionVersion -> List(v.toString))),
        headersToForward = Set.empty, // xxx Connection.headersToForwardFromProperty ?
        cookiesToForward = Connection.cookiesToForwardFromProperty,
        getHeader        = Connection.getHeaderFromRequest(ec.getRequest)
      )

    Connection.connectNow(
      method      = method,
      url         = resolvedUri,
      credentials = None,
      content     = requestBodyContent,
      headers     = allHeaders,
      loadState   = true,
      saveState   = true,
      logBody     = false
    )
  }

  protected def makeOutgoingRequest(
    method : HttpMethod,
    headers: ju.Map[String, Array[String]],
    params : Iterable[(String, String)]
  ): Request =
    new RequestAdapter {
      override def getMethod: HttpMethod = method
      override def getHeaderValuesMap: ju.Map[String, Array[String]] = headers
      override def parameters: collection.Map[String, Array[AnyRef]] =
        params.map{ case (k, v) => k -> Array(v: AnyRef) }.toMap
    }

  // Reads a document forwarding headers. The URL is rewritten, and is expected to be like "/fr/…"
  private def readHeadersAndDocument(
    urlString               : String,
    customHeaders           : Map[String, List[String]]
  )(implicit
    logger                  : IndentedLogger,
    coreCrossPlatformSupport: CoreCrossPlatformSupportTrait
  ): Try[(Map[String, List[String]], DocumentNodeInfoType)] =
    withDebug("reading document", List("url" -> urlString, "headers" -> customHeaders.toString)) {

    val cxr =
      connectPersistence(
        method        = HttpMethod.GET,
        pathQuery     = urlString,
        customHeaders = customHeaders
      )

    // Libraries are typically not present. In that case, the persistence layer should return a 404 (thus the test
    // on status code),  but the MySQL persistence layer returns a [200 with an empty body][1] (thus a body is
    // required).
    //   [1]: https://github.com/orbeon/orbeon-forms/issues/771
    ConnectionResult.tryWithSuccessConnection(cxr, closeOnSuccess = true) { is =>
      (
        cxr.headers,
        XFormsCrossPlatformSupport.readTinyTree(
          XPath.GlobalConfiguration,
          is,
          urlString,
          handleXInclude = true, // do process XInclude, so Form Builder's model gets included
          handleLexical  = false
        )
      )
    }
  }

  private def readHeaders(
    urlString    : String,
    customHeaders: Map[String, List[String]]
  )(implicit
    logger                  : IndentedLogger,
    coreCrossPlatformSupport: CoreCrossPlatformSupportTrait
  ): Map[String, List[String]] =
    connectPersistence(HttpMethod.HEAD, urlString, customHeaders = customHeaders).headers

  private def callPagedService[R](
    readPage: Int => Try[(Iterator[R], Int, Int)]
  )(implicit
    logger                  : IndentedLogger
  ): Iterator[R] = {

    var searchTotal: Option[Int] = None
    var currentPage  = 1
    var currentCount = 0

    Iterator.continually[Option[Iterator[R]]]({
      searchTotal.isEmpty || searchTotal.exists(currentCount < _) flatOption {
        val (it, total, count) = readPage(currentPage).get// can throw
        count > 0 option {
          if (searchTotal.isEmpty) {
            debug(s"search total is `$total`")
            searchTotal = total.some
          }
          currentPage  += 1
          currentCount += count
          it
        }
      }
    }).takeWhile(_.isDefined).flatten.flatten
  }
}
