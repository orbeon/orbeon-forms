package org.orbeon.oxf.fr.persistence.api

import cats.syntax.option._
import org.orbeon.io.CharsetNames
import org.orbeon.oxf.externalcontext.ExternalContext.Request
import org.orbeon.oxf.externalcontext.{ExternalContext, RequestAdapter, UrlRewriteMode}
import org.orbeon.oxf.fr.FormRunner.createFormDataBasePath
import org.orbeon.oxf.fr.FormRunnerParams.AppFormVersion
import org.orbeon.oxf.fr._
import org.orbeon.oxf.fr.persistence.relational.Version
import org.orbeon.oxf.fr.persistence.relational.Version.OrbeonFormDefinitionVersion
import org.orbeon.oxf.http.{HttpMethod, StreamedContent}
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.util.Logging._
import org.orbeon.oxf.util.StaticXPath.DocumentNodeInfoType
import org.orbeon.oxf.util.{Connection, ConnectionResult, ContentTypes, CoreCrossPlatformSupportTrait, IndentedLogger, URLRewriterUtils, XPath}
import org.orbeon.saxon.om
import org.orbeon.scaxon.SimplePath._
import org.orbeon.xforms.XFormsCrossPlatformSupport

import java.net.URI
import java.time.Instant
import java.{util => ju}
import scala.util.Try


// Provide a simple API to access the persistence layer from Scala
trait PersistenceApiTrait {

  private val SearchPageSize = 100

  case class DataDetails(
    createdTime     : Instant,
    lastModifiedTime: Instant,
    documentId      : String,
    isDraft         : Boolean
  )

  def search(
    appName                 : String,
    formName                : String,
    formVersion             : Int)(implicit
    logger                  : IndentedLogger,
    coreCrossPlatformSupport: CoreCrossPlatformSupportTrait
  ): Iterator[DataDetails] = {

    debug(s"calling search for `$appName`/`$formName`/`$formVersion`")

    val queryUrl =
      s"/fr/service/persistence/search/$appName/$formName"

    def readPage(pageNumber: Int): Try[DocumentNodeInfoType] = {

      debug(s"reading search page `$pageNumber`")

      val queryXml =
        <search>
            <query/>
            <page-size>{SearchPageSize}</page-size>
            <page-number>{pageNumber}</page-number>
        </search>

      val documentsXmlTry =
        ConnectionResult.tryWithSuccessConnection(
          connectPersistence(
            method             = HttpMethod.POST,
            path               = queryUrl,
            requestBodyContent = StreamedContent.fromBytes(queryXml.toString.getBytes(CharsetNames.Utf8), ContentTypes.XmlContentType.some).some,
            formVersionOpt     = formVersion.some
          ),
          closeOnSuccess = true
        ) { is =>
          XFormsCrossPlatformSupport.readTinyTree(
            XPath.GlobalConfiguration,
            is,
            queryUrl,
            handleXInclude = false,
            handleLexical  = false
          )
        }

      documentsXmlTry
    }

    def pageToDataDetails(page: DocumentNodeInfoType): (Iterator[DataDetails], Int, Int) =
      (
        (page.rootElement / "document").iterator map { documentElem =>
          DataDetails(
            createdTime      = Instant.parse(documentElem.attValue("created")),
            lastModifiedTime = Instant.parse(documentElem.attValue("last-modified")),
            documentId       = documentElem.attValue("name"),
            isDraft          = documentElem.attValue("draft") == "true"
          )
        },
        page.rootElement.attValue("search-total").toInt,
        (page.rootElement / "document").size
      )

    var searchTotal: Option[Int] = None
    var currentPage  = 1
    var currentCount = 0

    Iterator.continually[Option[Iterator[DataDetails]]]({
      searchTotal.isEmpty || searchTotal.exists(currentCount < _) flatOption {
        val (it, total, count) = readPage(currentPage).map(pageToDataDetails).get// can throw xxx
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

  def readFormData(
    appFormVersion: AppFormVersion,
    documentId     : String
  )(implicit
    logger                  : IndentedLogger,
    coreCrossPlatformSupport: CoreCrossPlatformSupportTrait
  ): Try[(Map[String, List[String]], DocumentNodeInfoType)] = {
    debug(s"reading form data for `$appFormVersion`/`$documentId`")
    readDocument(
      urlString     = FormRunner.createFormDataBasePath(appFormVersion._1.app, appFormVersion._1.form, isDraft = false, documentId) + "data.xml",
      customHeaders = Map(OrbeonFormDefinitionVersion -> List(appFormVersion._2.toString))
    )
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
    val path = createFormDataBasePath(appName, formName, isDraft, documentId) + "data.xml"
    val headers = readHeaders(path, Map.empty)
    headers.get(Version.OrbeonFormDefinitionVersion).map(_.head).map(_.toInt)
  }

  // Reads a document forwarding headers. The URL is rewritten, and is expected to be like "/fr/…"
  def readDocument(
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
        path          = urlString,
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

  // Retrieves a form definition from the persistence layer
  def readPublishedFormDefinition(
    appName  : String,
    formName : String,
    version  : FormDefinitionVersion
  )(implicit
    logger                  : IndentedLogger,
    coreCrossPlatformSupport: CoreCrossPlatformSupportTrait
  ): Try[(Map[String, List[String]], DocumentNodeInfoType)] = {

    debug(s"reading published form definition for `$appName`/`$formName`/`$version`")

    val path = FormRunner.createFormDefinitionBasePath(appName, formName) + "form.xhtml"
    val customHeaders = version match {
      case FormDefinitionVersion.Latest            => Map.empty[String, List[String]]
      case FormDefinitionVersion.Specific(version) => Map(OrbeonFormDefinitionVersion -> List(version.toString))
    }

    readDocument(path, customHeaders)
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

    val formsDocTry = PersistenceApi.readDocument(
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

  // TODO: call proxy directly?
  def connectPersistence(
    method                  : HttpMethod,
    path                    : String,
    requestBodyContent      : Option[StreamedContent]   = None,
    formVersionOpt          : Option[Int]               = None,
    customHeaders           : Map[String, List[String]] = Map.empty)(implicit
    logger                  : IndentedLogger,
    coreCrossPlatformSupport: CoreCrossPlatformSupportTrait
  ): ConnectionResult = {

    implicit val ec: ExternalContext = coreCrossPlatformSupport.externalContext

    val resolvedUri =
      new URI(
        URLRewriterUtils.rewriteServiceURL(
          ec.getRequest,
          path,
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

  private def readHeaders(
    urlString    : String,
    customHeaders: Map[String, List[String]]
  )(implicit
    logger                  : IndentedLogger,
    coreCrossPlatformSupport: CoreCrossPlatformSupportTrait
  ): Map[String, List[String]] =
    connectPersistence(HttpMethod.HEAD, urlString, customHeaders = customHeaders).headers
}
