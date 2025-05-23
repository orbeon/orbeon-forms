package org.orbeon.oxf.fr.persistence

import cats.Eval
import org.orbeon.oxf.cache.{CacheApi, CacheSupport}
import org.orbeon.oxf.fr.*
import org.orbeon.oxf.fr.permission.{Operation, Permission, Permissions, SpecificOperations}
import org.orbeon.oxf.fr.persistence.api.PersistenceApi
import org.orbeon.oxf.fr.persistence.proxy.FieldEncryption
import org.orbeon.oxf.fr.persistence.relational.FormStorageDetails
import org.orbeon.oxf.fr.persistence.relational.index.Index
import org.orbeon.oxf.properties.Properties
import org.orbeon.oxf.util.CoreUtils.*
import org.orbeon.oxf.util.Logging.*
import org.orbeon.oxf.util.TryUtils.*
import org.orbeon.oxf.util.{CoreCrossPlatformSupport, IndentedLogger}
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.SimplePath.*

import scala.util.{Success, Try}


// This handles query the persistence layer for various metadata. including:
//
// - controls to encrypt
// - controls to index
// - querying the form version
//
// To increase performance, especially when writing to the persistence layer, we cache the results of these queries with
// a configurable time-to-live.
//
object PersistenceMetadataSupport {

  private def cacheEnabled =
    ! Properties.instance.getPropertySet.getBooleanOpt("oxf.fr.persistence.form-definition-cache.enable").contains(false)

  // Use `lazy val`s so we get an exception other than `ExceptionInInitializerError`
  private lazy val formDefinitionCache = cacheEnabled option CacheSupport.getOrElseThrow("form-runner.persistence.form-definition", store = false)
  private lazy val formMetadataCache   = cacheEnabled option CacheSupport.getOrElseThrow("form-runner.persistence.form-metadata",   store = false)

  private type CacheKey = (String, String, Int) // app/form/version

  import Private._

  // When publishing a form, we need to invalidate the caches. This doesn't cover cases where form definitions are
  // updated directly in the database, but it's the most frequent case.
  def maybeInvalidateCachesFor(appForm: AppForm, version: Int)(implicit indentedLogger: IndentedLogger): Unit = {

    val cacheKey: CacheKey = (appForm.app, appForm.form, version)

    def log(cache: CacheApi)(removed: Boolean): Unit =
      if (removed)
        debug(s"removed form definition from cache `${cache.getName}` for `$cacheKey`")

    formDefinitionCache.foreach(cache => cache.remove(cacheKey).kestrel(log(cache)))
    formMetadataCache  .foreach(cache => cache.remove(cacheKey).kestrel(log(cache)))
  }

  def readPublishedFormStorageDetails(
    appForm : AppForm,
    version : FormDefinitionVersion
  )(implicit
    indentedLogger: IndentedLogger
  ): Try[FormStorageDetails] =
    readMaybeFromCache(appForm, version, formDefinitionCache) {
      implicit val coreCrossPlatformSupport: CoreCrossPlatformSupport.type = CoreCrossPlatformSupport
      withDebug("reading published form for storage details") {
        PersistenceApi.readPublishedFormDefinition(appForm.app, appForm.form, version) map { case ((_, formDefinitionDoc), _) =>
          val docContext  = new InDocFormRunnerDocContext(formDefinitionDoc)
          val formIsSingleton = docContext.metadataRootElemOpt.flatMap(_.firstChildOpt("singleton")).exists(_.stringValue == "true")

          FormStorageDetails(
            encryptedControlsPaths = Eval.later(FieldEncryption.getControlsToEncrypt(formDefinitionDoc, appForm).map(_.path)),
            indexedControlsXPaths  = Eval.later(Index.searchableValues(
              formDefinitionDoc,
              appForm,
              // We only need the controls XPaths, no need to specify the version (used to call the distinct values API)
              searchVersionOpt = None,
              FormRunnerPersistence.providerDataFormatVersionOrThrow(appForm)
            ).controls.toList.map(_.xpath)),
            isSingleton = formIsSingleton
          )
        }
      }
    }

  def readLatestVersion(appForm: AppForm)(implicit indentedLogger: IndentedLogger): Option[Int] = {
    implicit val coreCrossPlatformSupport: CoreCrossPlatformSupport.type = CoreCrossPlatformSupport

    PersistenceApi.readFormMetadataOpt(appForm, FormDefinitionVersion.Latest)._2
      .flatMap(_.firstChildOpt(Names.FormVersion))
      .map(_.getStringValue.toInt)
  }

  def isInternalAdminUser(requestParam: String => Option[String]): Boolean =
    requestParam(FormRunner.InternalAdminTokenParam)
      .exists(param =>
        FormRunnerAdminToken.decryptTokenPayloadCheckExpiration((), param)
          .getOrElse(throw new IllegalArgumentException)
      )

  def readFormPermissionsMaybeWithAdminSupport(
    isInternalAdminUser: Boolean,
    appForm            : AppForm,
    version            : FormDefinitionVersion
  )(implicit
    indentedLogger     : IndentedLogger
  ): Permissions =
    // TODO: Check possible optimization above to avoid retrieving form permissions twice.
    // TODO: Check if/why we are not using the caching mechanism.
    (
      if (isInternalAdminUser)
        Permissions.Defined(List(Permission(Nil, SpecificOperations(Set(Operation.Read, Operation.Delete)))))
      else
        FormRunner.permissionsFromElemOrProperties(
          readFormPermissions(appForm, version),
          appForm
        )
    ) |!>
      (formPermissions => debug("CRUD: form permissions", List("permissions" -> formPermissions.toString)))

  private def readFormPermissions(appForm: AppForm, version: FormDefinitionVersion)(implicit indentedLogger: IndentedLogger): Option[NodeInfo] = {
    implicit val coreCrossPlatformSupport: CoreCrossPlatformSupport.type = CoreCrossPlatformSupport
    PersistenceApi.readFormMetadataOpt(appForm, version)
      ._2
      .flatMap(_.firstChildOpt(Names.Permissions))
  }

  // The persistence proxy should pre-process and post-process the information, see:
  // https://github.com/orbeon/orbeon-forms/issues/5741
  // Used by search/distinct values only
  def getEffectiveFormVersionForSearchMaybeCallApi(
    appForm        : AppForm,
    incomingVersion: SearchVersion
  )(implicit
    indentedLogger : IndentedLogger
  ): FormDefinitionVersion =
    incomingVersion match {
      case SearchVersion.Unspecified  => PersistenceMetadataSupport.readLatestVersion(appForm).map(FormDefinitionVersion.Specific.apply).getOrElse(FormDefinitionVersion.Latest)
      case SearchVersion.All          => FormDefinitionVersion.Latest
      case SearchVersion.Specific(v)  => FormDefinitionVersion.Specific(v)
    }

  private object Private {

    def readMaybeFromCache[T <: Serializable](
      appForm       : AppForm,
      version       : FormDefinitionVersion,
      cacheOpt      : Option[CacheApi]
    )(
      read          : => Try[T]
    )(implicit
      indentedLogger: IndentedLogger
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
