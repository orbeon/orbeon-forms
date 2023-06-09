package org.orbeon.oxf.fr.persistence

import cats.Eval
import org.orbeon.oxf.cache.{CacheApi, CacheSupport}
import org.orbeon.oxf.fr.persistence.api.PersistenceApi
import org.orbeon.oxf.fr.persistence.proxy.FieldEncryption
import org.orbeon.oxf.fr.persistence.relational.EncryptionAndIndexDetails
import org.orbeon.oxf.fr.persistence.relational.index.Index
import org.orbeon.oxf.fr.{AppForm, FormDefinitionVersion, FormRunnerPersistence, Names}
import org.orbeon.oxf.properties.Properties
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.util.Logging._
import org.orbeon.oxf.util.TryUtils._
import org.orbeon.oxf.util.{CoreCrossPlatformSupport, IndentedLogger, LoggerFactory}
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.SimplePath._

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

  // Use `lazy val`s so we get an exception other than `ExceptionInInitializerError`
  private lazy val formDefinitionCache = cacheEnabled option CacheSupport.getOrElseThrow("form-runner.persistence.form-definition")
  private lazy val formMetadataCache   = cacheEnabled option CacheSupport.getOrElseThrow("form-runner.persistence.form-metadata")

  private type CacheKey = (String, String, Int) // app/form/version

  import Private._

  // When publishing a form, we need to invalidate the caches. This doesn't cover cases where form definitions are
  // updated directly in the database, but it's the most frequent case.
  def maybeInvalidateCachesFor(appForm: AppForm, version: Int): Unit = {

    val cacheKey: CacheKey = (appForm.app, appForm.form, version)

    def log(cache: CacheApi)(removed: Boolean): Unit =
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
      implicit val coreCrossPlatformSupport: CoreCrossPlatformSupport.type = CoreCrossPlatformSupport
      withDebug("reading published form for indexing/encryption details") {
        PersistenceApi.readPublishedFormDefinition(appForm.app, appForm.form, version) map { case (_, formDefinitionDoc) =>
          EncryptionAndIndexDetails(
            encryptedFieldsPaths = Eval.later(FieldEncryption.getFieldsToEncrypt(formDefinitionDoc, appForm).map(_.path)),
            indexedFieldsXPaths  = Eval.later(Index.findIndexedControls(
              formDefinitionDoc,
              FormRunnerPersistence.providerDataFormatVersionOrThrow(appForm),
              // We only need the fields XPaths, no need to evaluate settings against user roles
              forUserRoles = None
            ).map(_.xpath))
          )
        }
      }
    }

  def readLatestVersion(appForm: AppForm): Option[Int] = {
    implicit val coreCrossPlatformSupport: CoreCrossPlatformSupport.type = CoreCrossPlatformSupport
    PersistenceApi.readFormMetadataOpt(appForm, FormDefinitionVersion.Latest)
      .flatMap(_.firstChildOpt(Names.FormVersion))
      .map(_.getStringValue.toInt)
  }

  def readFormPermissions(appForm: AppForm, version: FormDefinitionVersion): Option[NodeInfo] = {
    implicit val coreCrossPlatformSupport: CoreCrossPlatformSupport.type = CoreCrossPlatformSupport
    PersistenceApi.readFormMetadataOpt(appForm, version)
      .flatMap(_.firstChildOpt(Names.Permissions))
  }

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

    def readMaybeFromCache[T <: Serializable](
      appForm  : AppForm,
      version  : FormDefinitionVersion,
      cacheOpt : Option[CacheApi])(
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

          cache.get(cacheKey) match {
            case Some(cacheElem) =>
              debug(s"got elem from cache for `$cacheKey` from `${cache.getName}`")
              Success(cacheElem.asInstanceOf[T])
            case None =>
              debug(s"did not get elem from cache for `$cacheKey` from `${cache.getName}`")
              read |!> (t => cache.put(cacheKey, t))
          }
      }
  }
}
