package org.orbeon.oxf.properties

import cats.data.NonEmptyList
import org.orbeon.concurrent.ResourceLock
import org.orbeon.oxf.cache.{CacheApi, CacheSupport}
import org.orbeon.oxf.externalcontext.ExternalContext.Request
import org.orbeon.oxf.util.ServiceProviderSupport
import org.orbeon.oxf.util.StringUtils.*
import org.orbeon.properties.api
import org.orbeon.properties.api.PropertyProvider
import org.slf4j

import java.net.URI
import java.util as ju
import scala.collection.immutable.ArraySeq
import scala.jdk.CollectionConverters.*
import scala.jdk.OptionConverters.*
import scala.util.chaining.scalaUtilChainingOps


trait PropertyLoaderPlatform extends PropertyLoaderTrait {

  private type CacheEntry = (api.ETag, PropertyStore)

  private val BootPropertyProviderClassName           = "org.orbeon.oxf.properties.ResourcesPropertyProvider"
  private val PropertyProvidersClassnamesPropertyName = "oxf.properties.providers.classnames"
  private val ProviderEtagCacheName                    = "orbeon.properties"

  import java.util.concurrent.atomic.AtomicInteger
  import scala.collection.mutable

  class ProviderStats {
    val hits   = new AtomicInteger(0)
    val misses = new AtomicInteger(0)
  }

  private class Statistics {
    var providersCount  : Int         = 0
    var orderedProviders: Seq[String] = Seq.empty
    val callsCount                    = new AtomicInteger(0)
    val callsWithoutRequestCount      = new AtomicInteger(0)
    val cacheHits                     = new AtomicInteger(0)
    val cacheMisses                   = new AtomicInteger(0)
    val providerStats                 = mutable.Map[String, ProviderStats]()

    def recordRequest(requestOpt: Option[Request]): Unit = {
      callsCount.incrementAndGet()
      if (requestOpt.isEmpty)
        callsWithoutRequestCount.incrementAndGet()
    }

    def recordHit(provider: String): Unit = {
      cacheHits.incrementAndGet()
      providerStats.getOrElseUpdate(provider, new ProviderStats).hits.incrementAndGet()
    }

    def recordMiss(provider: String): Unit = {
      cacheMisses.incrementAndGet()
      providerStats.getOrElseUpdate(provider, new ProviderStats).misses.incrementAndGet()
    }

    def asReadableString: String = {
      s"""
         |Property Loader Statistics:
         |  Total Providers: $providersCount
         |  Ordered Providers: ${orderedProviders.mkString(", ")}
         |  Calls: ${callsCount.get()}
         |  Calls without Request: ${callsWithoutRequestCount.get()}
         |  Cache Hits: ${cacheHits.get()}
         |  Cache Misses: ${cacheMisses.get()}
         |  Provider-specific stats:
         |${providerStats.map { case (name, stats) => s"    $name: ${stats.hits} hits, ${stats.misses} misses" }.mkString("\n")}
         |""".stripMargin
    }
  }

  private val stats = new Statistics

  @volatile
  private var initialized  = false

  def initialize(): Unit =
    if (! initialized) {
      // 1. We read the boot property store without going through `CacheSupport`, to avoid recursion, as `CacheSupport`
      //    requires the use of properties. This will call `ResourcesPropertyProvider`, which will use the XML parser and
      //    pipelines, which may call `PropertyLoader` recursively. We specifically handle this to prevent infinite
      //    recursion.
      val bootPropertyStore = Initialization.getInitializationPropertyStore
      // 2. Once we have a boot property store, we make sure that `CacheSupport` is initialized. We do this first, because
      //    if this is done as a result of a call to `getPropertyStoreImpl`, we may again have recursion.
      val cacheOpt = findCache
      // 3. We compute the ordered list of property providers, using the boot property store we have read above, so no
      //    recursion should occur. This is computed once and for all. We could have used a `lazy val` except that
      //    we want to be more explicit in passing the boot property store.
      propertyProvidersInIncreasingPriorityOpt = computePropertyProvidersInIncreasingPriorityOpt(bootPropertyStore)
      // 4. We store the boot property store ETag and `PropertyStore` in the cache used by `CacheSupport`, so that future
      //    calls to `getPropertyStoreImpl` can use it without having to reread the boot properties.
      Initialization.getAndClearBootPropertyStoreCacheEntryOpt.foreach { bootPropertyStoreCacheEntry =>
        cacheOpt.foreach(_.put(BootPropertyProviderClassName, bootPropertyStoreCacheEntry))
      }
      // 5. We mark initialization as done. After this point, the boot property store must no longer be used.
      initialized = true
    } else {
      // Re-initialization is used for tests only: invalidate cached entries for all providers without custom cache keys
      propertyProvidersInIncreasingPriorityOpt.foreach { providersNel =>
        providersNel.map { provider =>
          findCache
            .foreach { cache =>
              cache.remove(providerClassName(provider))
            }
        }
      }
    }

  private object Initialization {

    @volatile
    private var bootPropertyStoreCacheEntryOpt: Option[CacheEntry] = None

    private val initializationResourceLock = new ResourceLock

    // We want to control initialization to avoid infinite recursion.
    // So the first caller only is allowed to perform initialization. It takes a lock and then calls
    // `getBootPropertyStore()`. This can recursively call `getPropertyStoreImpl()`, but we detect that condition and
    // return an empty property store to avoid infinite recursion. The assumption is that the callers are few and don't
    // require important properties, or ones that have defaults, including: XML parser security manager; processors
    // validation settings (only for reading `properties-*.xml`).
    def getInitializationPropertyStore: PropertyStore =
      initializationResourceLock.withAcquiredResourceOrNone(allowBlocking = true) {
        getBootPropertyStore
      }
      .getOrElse(PropertyStore.empty)

    // This returns a `PropertyStore` without recursively without using `CacheSupport`. The property provider will
    // recursively call `PropertyLoader.getPropertyStore()`, but this will not use `CacheSupport` as we detect that
    // condition.
    private def getBootPropertyStore: PropertyStore =
      propertyProvidersOpt match {
        case Some(propertyProvidersNel) =>
          propertyProvidersNel.find(_.getClass.getName.contains(BootPropertyProviderClassName)) match {
            case Some(bootProvider) =>
              bootProvider.getPropertiesIfNeeded(
                cacheKey    = ju.Optional.empty(),
                eTag        = bootPropertyStoreCacheEntryOpt.map(_._1).toJava,
                credentials = ju.Optional.empty(),
                request     = ju.Optional.empty(),
                session     = ju.Optional.empty(),
                extension   = Map.empty.asJava,
              )
              .toScala
              .map { newPropertyDefinitionsWithEtag =>
                val newPropertyStore =
                  PropertyStore.fromPropertyDefinitions(
                    newPropertyDefinitionsWithEtag.getProperties.asScala,
                    newPropertyDefinitionsWithEtag.getETag
                  )
                bootPropertyStoreCacheEntryOpt =
                  Some(newPropertyDefinitionsWithEtag.getETag -> newPropertyStore)
                newPropertyStore
              }
              .orElse {
                bootPropertyStoreCacheEntryOpt.map(_._2)
              }
              .getOrElse {
                logger.warn("`getBootPropertyStore`: boot property provider returned no properties")
                PropertyStore.empty
              }
            case None =>
              logger.warn(s"`getBootPropertyStore`: boot property provider `$BootPropertyProviderClassName` not found")
              PropertyStore.empty
          }
        case None =>
          logger.warn("`getBootPropertyStore`: no property providers found")
          PropertyStore.empty
      }

    def getAndClearBootPropertyStoreCacheEntryOpt: Option[CacheEntry] = {
      val entryOpt = bootPropertyStoreCacheEntryOpt
      bootPropertyStoreCacheEntryOpt = None
      entryOpt
    }
  }

  private def providerClassName(provider: api.PropertyProvider): String =
    provider.getClass.getName

  private def findCache: Option[CacheApi] =
    CacheSupport.findCache(ProviderEtagCacheName, store = false)

  def getPropertyStoreImpl(requestOpt: Option[Request]): PropertyStore = {
    stats.recordRequest(requestOpt)
    if (initialized)
      getPropertyStoresFromProviders(requestOpt)
    else
      Initialization.getInitializationPropertyStore
  }.tap(_ => logger.debug(stats.asReadableString))

  private def getPropertyStoresFromProviders(requestOpt: Option[Request]): PropertyStore =
    propertyProvidersInIncreasingPriorityOpt match {
      case Some(propertyProvidersInIncreasingPriorityNel) =>

        val credentialsJavaOpt =
          requestOpt.flatMap(_.credentials).map(credentials =>
            new api.Credentials {
              def getUsername     : String                          = credentials.userAndGroup.username
              def getGroupname    : ju.Optional[String]             = credentials.userAndGroup.groupname.toJava
              def getRoles        : ju.Collection[String]           = credentials.roles.view.map(_.roleName).asJavaCollection // TODO: this doesn't handle `ParametrizedRole`
              def getOrganizations: ju.Collection[api.Organization] = credentials.organizations.view.map(organization => new api.Organization {
                def getLevels: ju.Collection[String] = organization.levels.asJavaCollection
              }).asJavaCollection
            }
          ).toJava

        val sessionJavaOpt =
          requestOpt.flatMap(_.sessionOpt).map(session =>
            new api.Session {
              def getId: String = session.getId
              def getAttribute   (name: String)               : ju.Optional[AnyRef] = session.getAttribute(name).toJava
              def setAttribute   (name: String, value: AnyRef): Unit                = session.setAttribute(name, value)
              def removeAttribute(name: String)               : Unit                = session.removeAttribute(name)
            }
          ).toJava

        val requestJavaOpt =
          requestOpt.map(request =>
            new api.Request {
              def getMethod: String =
                request.getMethod.entryName
              def getRequestUri: URI =
                new URI(
                  request.getScheme,
                  null,
                  request.getRemoteHost,
                  request.getServerPort,
                  request.getContextPath + request.getRequestPath,
                  request.getQueryString,
                  null
                )
              def getHeaders: ju.Map[String, ju.Collection[String]] =
                request.getHeaderValuesMap.asScala.view.mapValues(a => ArraySeq.unsafeWrapArray(a).asJavaCollection).toMap.asJava // xxx `toMap`
            }
          ).toJava


        val propertyStoresInIncreasingPriorityNel: NonEmptyList[Option[PropertyStore]] =
          propertyProvidersInIncreasingPriorityNel.map { provider =>
            val pcn = providerClassName(provider)

            val (providerCacheKeyX, cacheKey) =
              provider.getCacheKey(
                request     = requestJavaOpt,
                credentials = credentialsJavaOpt,
                session     = sessionJavaOpt,
                extension   = Map.empty.asJava,
              )
              .toScala
              .map(cacheKey => Some(cacheKey) -> s"$pcn:$cacheKey")
              .getOrElse(None -> pcn)

            val cacheEntryOpt =
              findCache
                .flatMap(_.get(cacheKey))
                .map(_.asInstanceOf[CacheEntry])


            val eTag = cacheEntryOpt.map(_._1).toJava

            provider.getPropertiesIfNeeded(
              cacheKey     = providerCacheKeyX.toJava,
              eTag         = eTag,
              credentials  = credentialsJavaOpt,
              request      = requestJavaOpt,
              session      = sessionJavaOpt,
              extension    = Map.empty.asJava,
            )
            .toScala
            .map { newPropertyDefinitionsWithEtag =>
              stats.recordMiss(providerClassName(provider))
              val newETag = newPropertyDefinitionsWithEtag.getETag

              val newPropertyStore =
                PropertyStore.fromPropertyDefinitions(
                  newPropertyDefinitionsWithEtag.getProperties.asScala,
                  newETag
                )
              findCache
                .foreach(_.put(cacheKey, newETag -> newPropertyStore))
              newPropertyStore
            }
            .orElse {
              stats.recordHit(providerClassName(provider))
              cacheEntryOpt.map(_._2)
            }
          }

        // Combine all
        CombinedPropertyStore
          .combine(propertyStoresInIncreasingPriorityNel)
          .getOrElse(PropertyStore.empty)
      case None =>
        logger.warn("`getPropertySet`: no property providers found")
        PropertyStore.empty
  }

  // This filters and orders the property providers according to the `oxf.properties.providers.classnames` property.
  // The boot property provider is always first and only occurs exactly once.
  private def computePropertyProvidersInIncreasingPriorityOpt(bootPropertyStore: PropertyStore): Option[NonEmptyList[PropertyProvider]] =
    propertyProvidersOpt.flatMap { propertyProviders =>
      stats.providersCount = propertyProviders.length
      val orderedProviders =
        NonEmptyList.fromList(
          propertyProviders.find(provider =>
            provider.getClass.getName.contains(BootPropertyProviderClassName)
          ).toList :::
            bootPropertyStore
              .globalPropertySet
              .getNonBlankString(PropertyProvidersClassnamesPropertyName)
              .map(_.splitTo[List]().reverse) // internally, we want to go in increasing priority order; property is in decreasing priority order
              .getOrElse(Nil)
              .flatMap { providerRe =>
                propertyProviders.find { provider =>
                  !provider.getClass.getName.contains(BootPropertyProviderClassName) &&
                    provider.getClass.getName.matches(providerRe)
                }
              }
        )
      stats.orderedProviders = orderedProviders.map(_.toList.map(providerClassName)).getOrElse(Seq.empty)
      orderedProviders
    }

  @volatile
  private var propertyProvidersInIncreasingPriorityOpt: Option[NonEmptyList[api.PropertyProvider]] = None

  // Unordered list of property providers
  private lazy val propertyProvidersOpt: Option[NonEmptyList[api.PropertyProvider]] = {
    implicit val slf4jLogger: slf4j.Logger = logger.logger
    val res = NonEmptyList.fromList(ServiceProviderSupport.loadProviders[api.PropertyProvider]("property"))
    res
  }
}
