package org.orbeon.oxf.fr.persistence

import cats.Eval
import net.sf.ehcache.{Cache, Element => EhElement}
import org.orbeon.oxf.cache.Caches
import org.orbeon.oxf.externalcontext.{ExternalContext, UrlRewriteMode}
import org.orbeon.oxf.fr.FormRunner.{createFormDataBasePath, createFormDefinitionBasePath, createFormMetadataPathAndQuery}
import org.orbeon.oxf.fr.persistence.proxy.FieldEncryption
import org.orbeon.oxf.fr.persistence.relational.Version.OrbeonFormDefinitionVersion
import org.orbeon.oxf.fr.persistence.relational.index.Index
import org.orbeon.oxf.fr.persistence.relational.{EncryptionAndIndexDetails, Version}
import org.orbeon.oxf.fr.{AppForm, FormDefinitionVersion, FormRunnerPersistence, Names}
import org.orbeon.oxf.http.HttpMethod
import org.orbeon.oxf.properties.Properties
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.util.Logging._
import org.orbeon.oxf.util.StaticXPath.DocumentNodeInfoType
import org.orbeon.oxf.util.TryUtils._
import org.orbeon.oxf.util.{Connection, ConnectionResult, CoreCrossPlatformSupport, IndentedLogger, LoggerFactory, PathUtils, URLRewriterUtils, XPath}
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.SimplePath._
import org.orbeon.xforms.XFormsCrossPlatformSupport

import java.net.URI
import scala.util.{Success, Try}


// This handles query the persistence layer for various metadata. including:
//
// - fields to encrypt
// - fields to index
// - querying the form version
//
// To increase performance, especially when writing to the persistence layer, we cache the results of these queries with
// a configurable time-to-live.
//
object PersistenceMetadataSupport {

  private implicit val Logger: IndentedLogger =
    new IndentedLogger(LoggerFactory.createLogger("org.orbeon.fr.persistence.form-definition-cache"))

  private def cacheEnabled =
    ! Properties.instance.getPropertySet.getBooleanOpt("oxf.fr.persistence.form-definition-cache.enable").contains(false)

  private val formDefinitionCache = cacheEnabled option Caches.getOrElseThrow("form-runner.persistence.form-definition")
  private val formMetadataCache   = cacheEnabled option Caches.getOrElseThrow("form-runner.persistence.form-metadata")

  private type CacheKey = (String, String, Int) // app/form/version

  import Private._

  // When publishing a form, we need to invalidate the caches. This doesn't cover cases where form definitions are
  // updated directly in the database, but it's the most frequent case.
  def maybeInvalidateCachesFor(appForm: AppForm, version: Int): Unit = {

    val cacheKey: CacheKey = (appForm.app, appForm.form, version)

    def log(cache: Cache)(removed: Boolean): Unit =
      if (removed)
        debug(s"removed form definition from cache `${cache.getName}` for `$cacheKey`")

    formDefinitionCache.foreach(cache => cache.remove(cacheKey).kestrel(log(cache)))
    formMetadataCache  .foreach(cache => cache.remove(cacheKey).kestrel(log(cache)))
  }

  // Retrieves a form definition from the persistence layer
  def readPublishedFormEncryptionAndIndexDetails(
    appForm : AppForm,
    version : FormDefinitionVersion
  ): Try[EncryptionAndIndexDetails] =
    readMaybeFromCache(appForm, version, formDefinitionCache) {
      withDebug("reading published form for indexing/encryption details") {
        val path = createFormDefinitionBasePath(appForm.app, appForm.form) + "form.xhtml"
        val customHeaders = version match {
          case FormDefinitionVersion.Latest            => Map.empty[String, List[String]]
          case FormDefinitionVersion.Specific(version) => Map(OrbeonFormDefinitionVersion -> List(version.toString))
        }
        readDocument(path, customHeaders) map { formDefinitionDoc =>
          EncryptionAndIndexDetails(
            encryptedFieldsPaths = Eval.later(FieldEncryption.getFieldsToEncrypt(formDefinitionDoc, appForm).map(_.path)),
            indexedFieldsXPaths  = Eval.later(Index.findIndexedControls(formDefinitionDoc, FormRunnerPersistence.providerDataFormatVersionOrThrow(appForm)).map(_.xpath))
          )
        }
      }
    }

  // TODO: This should return a `Try`. Right now it throws if the document cannot be read.
  // Retrieves from the persistence layer the metadata for a form, return an `Option[<form>]`
  def readFormMetadataOpt(
    appForm : AppForm,
    version : FormDefinitionVersion
  ): Option[NodeInfo] =
    readMaybeFromCache(appForm, version, formMetadataCache) {

      val formsDocTry =
        withDebug("reading published form for metadata only") {
          readDocument(
            createFormMetadataPathAndQuery(
              app         = appForm.app,
              form        = appForm.form,
              allVersions = version != FormDefinitionVersion.Latest,
              allForms    = true
            ),
            Map.empty
          )
        }

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

  // TODO: Since  this is called by the persistence proxy and must indirectly reaches the persistence proxy again, could
  //  we directly call the persistence proxy?
  // TODO: Clarify conditions of failure: form definition missing in database, connection failing, etc. Do we get a
  //  `None` in all cases, or can an exception be thrown?
  def readDocumentFormVersion(
    appForm    : AppForm,
    documentId : String,
    isDraft    : Boolean,
    query      : List[(String, String)]
  ): Option[Int] = {
    val path = createFormDataBasePath(appForm.app, appForm.form, isDraft, documentId) + "data.xml"
    val headers = readHeaders(PathUtils.recombineQuery(path, query), Map.empty)
    headers.get(Version.OrbeonFormDefinitionVersion).map(_.head).map(_.toInt)
  }

  def readLatestVersion(appForm: AppForm): Option[Int] =
    readFormMetadataOpt(appForm, FormDefinitionVersion.Latest)
      .flatMap(_.firstChildOpt(Names.FormVersion))
      .map(_.getStringValue.toInt)

  def readFormPermissions(appForm: AppForm, version: FormDefinitionVersion): Option[NodeInfo]=
    readFormMetadataOpt(appForm, version)
      .flatMap(_.firstChildOpt(Names.Permissions))

  // Used by search only
  def getEffectiveFormVersionForSearchMaybeCallApi(
    appForm        : AppForm,
    incomingVersion: SearchVersion
  ): FormDefinitionVersion =
    incomingVersion match {
      case SearchVersion.Unspecified  => PersistenceMetadataSupport.readLatestVersion(appForm).map(FormDefinitionVersion.Specific).getOrElse(FormDefinitionVersion.Latest)
      case SearchVersion.All          => FormDefinitionVersion.Latest
      case SearchVersion.Specific(v)  => FormDefinitionVersion.Specific(v)
    }

  private object Private {

    def readMaybeFromCache[T](
      appForm  : AppForm,
      version  : FormDefinitionVersion,
      cacheOpt : Option[Cache])(
      read     : => Try[T]
    ): Try[T] =
      (version, cacheOpt) match {
        case (_, None) =>
          debug(s"cache is disabled, reading directly")
          read
        case (FormDefinitionVersion.Latest, Some(cache)) =>
          // We don't know the version number, so we can't try the cache. We could check the resulting version number
          // returned from headers and cache the document afterwards. Unclear if it helps.
          debug(s"version is `Latest`, not using cache `${cache.getName}`")
          read
        case (FormDefinitionVersion.Specific(versionNumber), Some(cache)) =>

          val cacheKey: CacheKey = (appForm.app, appForm.form, versionNumber)

          Option(cache.get(cacheKey)) match {
            case Some(cacheElem) =>
              debug(s"got elem from cache for `$cacheKey` from `${cache.getName}`")
              Success(cacheElem.getObjectValue.asInstanceOf[T])
            case None =>
              debug(s"did not get elem from cache for `$cacheKey` from `${cache.getName}`")
              read |!> (t => cache.put(new EhElement(cacheKey, t)))
          }
      }

    // Reads a document forwarding headers. The URL is rewritten, and is expected to be like "/fr/…"
    def readDocument(
      urlString     : String,
      customHeaders : Map[String, List[String]]
    ): Try[DocumentNodeInfoType] =
      withDebug("reading document", List("url" -> urlString, "headers" -> customHeaders.toString)) {
        // Libraries are typically not present. In that case, the persistence layer should return a 404 (thus the test
        // on status code),  but the MySQL persistence layer returns a [200 with an empty body][1] (thus a body is
        // required).
        //   [1]: https://github.com/orbeon/orbeon-forms/issues/771
        ConnectionResult.tryWithSuccessConnection(
          readConnectionResult(HttpMethod.GET, urlString, customHeaders),
          closeOnSuccess = true
        ) { is =>
          XFormsCrossPlatformSupport.readTinyTree(
            XPath.GlobalConfiguration,
            is,
            urlString,
            handleXInclude = true, // do process XInclude, so FB's model gets included
            handleLexical  = false
          )
        }
      }

      def readHeaders(
        urlString     : String,
        customHeaders : Map[String, List[String]]
      ): Map[String, List[String]] =
        withDebug("reading headers", List("url" -> urlString, "headers" -> customHeaders.toString)) {
          readConnectionResult(HttpMethod.HEAD, urlString, customHeaders).headers
        }
    }

    private def readConnectionResult(
      method        : HttpMethod,
      urlString     : String,
      customHeaders : Map[String, List[String]]
    ): ConnectionResult = {

      implicit val externalContext         : ExternalContext = CoreCrossPlatformSupport.externalContext
      implicit val coreCrossPlatformSupport: CoreCrossPlatformSupport.type = CoreCrossPlatformSupport

      val request = externalContext.getRequest

      val rewrittenURLString =
        URLRewriterUtils.rewriteServiceURL(
          request,
          urlString,
          UrlRewriteMode.Absolute
        )

      val url = URI.create(rewrittenURLString)

      val headers = Connection.buildConnectionHeadersCapitalizedIfNeeded(
        url              = url,
        hasCredentials   = false,
        customHeaders    = customHeaders,
        headersToForward = Connection.headersToForwardFromProperty,
        cookiesToForward = Connection.cookiesToForwardFromProperty,
        Connection.getHeaderFromRequest(request)
      )

      Connection.connectNow(
        method      = method,
        url         = url,
        credentials = None,
        content     = None,
        headers     = headers,
        loadState   = true,
        saveState   = true,
        logBody     = false
      )
    }
}
