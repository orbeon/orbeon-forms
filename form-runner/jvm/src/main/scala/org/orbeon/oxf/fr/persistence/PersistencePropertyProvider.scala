package org.orbeon.oxf.fr.persistence

import org.orbeon.concurrent.ResourceLock
import org.orbeon.io.IOUtils.useAndClose
import org.orbeon.oxf.fr.*
import org.orbeon.oxf.fr.persistence.api.PersistenceApi
import org.orbeon.oxf.fr.persistence.relational.RelationalUtils
import org.orbeon.oxf.http.{Headers, HttpMethod, StatusCode}
import org.orbeon.oxf.properties.{PropertyLoader, PropertyStore}
import org.orbeon.oxf.util.{CoreCrossPlatformSupport, CoreCrossPlatformSupportTrait, IndentedLogger, JvmUrlEncoderDecoder, PathUtils}
import org.orbeon.oxf.webapp.ProcessorService
import org.orbeon.oxf.xml.dom.IOSupport
import org.orbeon.properties.api

import java.util as ju
import scala.jdk.OptionConverters.*
import scala.util.control.NonFatal


class PersistencePropertyProvider extends api.PropertyProvider {

  import PersistencePropertyProvider.*

  def getPropertiesIfNeeded(
    cacheKey   : ju.Optional[api.CacheKey],
    eTag       : ju.Optional[api.ETag],
    request    : ju.Optional[api.Request],
    credentials: ju.Optional[api.Credentials],
    session    : ju.Optional[api.Session],
    extension  : ju.Map[String, Any]
  ): ju.Optional[api.PropertyDefinitionsWithETag] = {

    if (isLoading.get())
      return ju.Optional.empty()

    val ecOpt =
      Option(CoreCrossPlatformSupport.externalContext)
        .filter { ec =>
          ec.getRequest != null &&
          ec.getWebAppContext != null &&
          ProcessorService.currentProcessorService.value.isDefined &&
          ! (
            ec.getWebAppContext.getClass.getName == "org.orbeon.oxf.externalcontext.TestWebAppContext" &&
            ec.getWebAppContext.getRealPath("WEB-INF/orbeon-demo.sqlite") == null
          )
        }

    if (ecOpt.isEmpty)
      return ju.Optional.empty()

    val currentTime = System.currentTimeMillis()

    if (eTag.isPresent && ! hasEnoughTimeElapsed(currentTime))
      return ju.Optional.empty()

    val ec = ecOpt.get

    resourceLock.withAcquiredResourceOrNone(allowBlocking = false) {
      isLoading.set(true)
      val attrs = ec.getRequest.getAttributesMap
      val hadRequestProperties = attrs.containsKey(PropertyLoader.RequestPropertiesAttributeName)
      try {
        if (! hadRequestProperties)
          PropertyLoader.getBootPropertyStoreOpt.foreach(attrs.put(PropertyLoader.RequestPropertiesAttributeName, _))

        fetchPropertiesIfNeeded(eTag.toScala, currentTime)
      } catch {
        case NonFatal(t) =>
          PropertyLoader.logger.error(t)(s"Error loading properties from persistence provider")
          lastCheckedTimestampOpt = Some(currentTime)
          None
      } finally {
        if (! hadRequestProperties)
          attrs.remove(PropertyLoader.RequestPropertiesAttributeName)
        isLoading.set(false)
      }
    }
    .flatten
    .map { case (newPropertyDefinitions, newETag) =>
      new api.PropertyDefinitionsWithETag {
        val getProperties: api.PropertyDefinitions = newPropertyDefinitions
        val getETag      : api.ETag                = newETag
      }: api.PropertyDefinitionsWithETag
    }
    .toJava
  }
}

object PersistencePropertyProvider {

  val ProviderClassName       = classOf[PersistencePropertyProvider].getName
  val ReloadDelayPropertyName = "oxf.properties.persistence.reload-delay"
  val DocumentIdPropertyName  = "oxf.properties.persistence.document-id"
  val DefaultReloadDelayMs    = 5000L
  val DefaultDocumentId       = "_"
  val NotFoundETag            = "not-found"

  private val DefaultTokenValidity = java.time.Duration.ofSeconds(20)
  private val resourceLock         = new ResourceLock
  private val isLoading            = ThreadLocal.withInitial[Boolean](() => false)

  @volatile
  private var lastCheckedTimestampOpt: Option[Long] = None

  def invalidateCache(): Unit = {
    lastCheckedTimestampOpt = None
    val requestOpt = Option(CoreCrossPlatformSupport.externalContext).flatMap(ec => Option(ec.getRequest))
    PropertyLoader.invalidateProviderCache(ProviderClassName, requestOpt)
  }

  def reloadDelayMs: Long =
    PropertyLoader.getBootPropertyStoreOpt
      .flatMap(_.globalPropertySet.getIntOpt(ReloadDelayPropertyName))
      .map(_.toLong)
      .getOrElse(DefaultReloadDelayMs)

  def documentId: String =
    PropertyLoader.getBootPropertyStoreOpt
      .flatMap(_.globalPropertySet.getNonBlankString(DocumentIdPropertyName))
      .getOrElse(DefaultDocumentId)

  private def hasEnoughTimeElapsed(currentTime: Long): Boolean =
    lastCheckedTimestampOpt match {
      case Some(lastCheckedTimestamp) if currentTime <= lastCheckedTimestamp + reloadDelayMs =>
        false
      case _ =>
        true
    }

  private def extractETag(headers: Map[String, List[String]], fallbackTime: Long): String =
    Headers.firstItemIgnoreCase(headers, Headers.ETag)
      .orElse(Headers.firstItemIgnoreCase(headers, Headers.OrbeonLastModified))
      .orElse(Headers.firstItemIgnoreCase(headers, Headers.LastModified))
      .getOrElse(fallbackTime.toString)

  private def fetchPropertiesIfNeeded(
    currentETagOpt: Option[api.ETag],
    currentTime   : Long
  ): Option[(api.PropertyDefinitions, api.ETag)] = {

    implicit val logger                  : IndentedLogger                = RelationalUtils.newIndentedLogger
    implicit val coreCrossPlatformSupport: CoreCrossPlatformSupportTrait = CoreCrossPlatformSupport

    val tokenParamList =
      FormRunnerAdminToken.encryptToken(
        validity            = DefaultTokenValidity,
        isInternalAdminUser = true
      ).map(FormRunner.InternalAdminTokenParam -> _).toList

    val dataPathQuery =
      PathUtils.recombineQuery(
        FormRunner.createFormDataBasePath(
          AppForm.FormConfiguration.app,
          AppForm.FormConfiguration.form,
          isDraft = false,
          documentId
        ) + FormRunnerPersistence.DataXml,
        tokenParamList
      )

    val formVersionOpt = Some(Left(FormDefinitionVersion.Specific(1)))

    // If we already have an ETag, perform a lightweight HEAD check first
    currentETagOpt match {
      case Some(currentETag) =>
        val headCxr =
          PersistenceApi.connectPersistence(
            method         = HttpMethod.HEAD,
            pathQuery      = dataPathQuery,
            formVersionOpt = formVersionOpt
          )
        val mustDoGet =
          try {
            headCxr.statusCode match {
              case StatusCode.NotFound | StatusCode.Gone =>
                lastCheckedTimestampOpt = Some(currentTime)
                if (currentETag == NotFoundETag)
                  return None
                else
                  return Some((ju.Collections.emptyList[api.PropertyDefinition](), NotFoundETag))
              case StatusCode.Ok =>
                val headETag = extractETag(headCxr.headers, currentTime)
                if (headETag == currentETag) {
                  lastCheckedTimestampOpt = Some(currentTime)
                  return None
                } else {
                  true
                }
              case otherStatus =>
                PropertyLoader.logger.warn(s"Unexpected status code `$otherStatus` when checking persistence properties")
                lastCheckedTimestampOpt = Some(currentTime)
                return None
            }
          } finally {
            headCxr.close()
          }

        if (! mustDoGet)
          return None

      case None =>
        // No cached ETag yet: proceed directly to GET
    }

    val getCxr =
      PersistenceApi.connectPersistence(
        method         = HttpMethod.GET,
        pathQuery      = dataPathQuery,
        formVersionOpt = formVersionOpt
      )

    try {
      getCxr.statusCode match {
        case StatusCode.NotFound | StatusCode.Gone =>
          lastCheckedTimestampOpt = Some(currentTime)
          Some((ju.Collections.emptyList[api.PropertyDefinition](), NotFoundETag))
        case StatusCode.Ok =>
          val doc =
            useAndClose(getCxr.content.stream) { is =>
              IOSupport.readOrbeonDom(is)
            }
          val newETag = extractETag(getCxr.headers, currentTime)
          val defs    = PropertyStore.parseToPropertyDefinitions(doc)
          lastCheckedTimestampOpt = Some(currentTime)
          Some((defs, newETag))
        case otherStatus =>
          PropertyLoader.logger.warn(s"Unexpected status code `$otherStatus` when reading persistence properties")
          lastCheckedTimestampOpt = Some(currentTime)
          None
      }
    } finally {
      getCxr.close()
    }
  }
}
