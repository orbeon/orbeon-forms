package org.orbeon.oxf.cache

import cats.data.NonEmptyList
import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.properties.Properties
import org.orbeon.oxf.util.CoreUtils.*

import java.io
import java.net.URI
import javax.cache.configuration.MutableConfiguration
import javax.cache.{CacheManager, Caching}
import scala.jdk.CollectionConverters.*
import scala.util.Try


class JCacheSupport(store: Boolean) extends CacheProviderApi {

  import CacheSupport.*
  import CacheSupport.Logger.*

  def get(cacheName: String): Option[CacheApi] = {
    val cacheOpt = Option(cacheManager.getCache[io.Serializable, io.Serializable](cacheName))
    val cache = cacheOpt.getOrElse(
      cacheManager.createCache
        [io.Serializable, io.Serializable, MutableConfiguration[io.Serializable, io.Serializable]]
        (
          cacheName,
          new MutableConfiguration[io.Serializable, io.Serializable]() |!> { config =>
            // Force storing by reference if we are a plain cache, otherwise leave it to the default
            if (! store)
              config.setStoreByValue(false)
          }
        )
    )
    // We always return a `Some` since our implementation for JCache always creates a cache if one doesn't
    // already exist, while our implementation for EHCache 2 doesn't.
    Some(new JCacheCacheApi(cache))
  }

  // Calling `close()` on the provider is a shortcut to closing all its cache managers, so we just close the cache
  // manager as that's what makes sense here. If any other code had obtained cache managers separately, we wouldn't
  // want to close them as a side effect of this.
  def close(): Unit =
    cacheManager.close()

  class JCacheCacheApi(private val cache: javax.cache.Cache[io.Serializable, io.Serializable]) extends CacheApi  {
    def put(k: io.Serializable, v: io.Serializable): Unit         = { trace("put");                    cache.put(k, v) }
    def putIfAbsent(k: io.Serializable, v: io.Serializable): Unit = { trace("putIfAbsent");            cache.putIfAbsent(k, v) }
    def get(k: io.Serializable): Option[io.Serializable]          = { trace("get");                    Option(cache.get(k)) }
    def remove(k: io.Serializable): Boolean                       = { trace("remove");                 cache.remove(k) }
    def getName: String                                           = { trace("getName");                cache.getName }
    def getMaxEntriesLocalHeap: Option[Long]                      = { trace("getMaxEntriesLocalHeap"); None }
    def getLocalHeapSize: Option[Long]                            = { trace("getLocalHeapSize");       None }
  }

  private lazy val cacheManager: CacheManager =
    try {

      val properties =
        Properties.instance.getPropertySetOrThrow |!>
          (CacheSupport.logProperties(_, CacheSupport.Logger.debug))

      // For debugging only, indicate the default provider set using the Java system property
      Try(System.getProperty(Caching.JAVAX_CACHE_CACHING_PROVIDER)).toOption.flatMap(Option.apply).foreach { provider =>
        debug(s"A default JCache provider is set to `$provider` via the system property `${Caching.JAVAX_CACHE_CACHING_PROVIDER}`")
      }

      val provider =
        NonEmptyList.fromList(Caching.getCachingProviders.asScala.toList) match {
          case None =>
            val msg = "No JCache provider was found in the classpath"
            error(msg)
            throw new IllegalStateException(msg)
          case Some(providers) =>

            debug(s"JCache providers class names found in the classpath:")
            providers.iterator.zipWithIndex.foreach { case (provider, index) =>
              debug(s"  ${index + 1}: `${provider.getClass.getName}`")
            }

            nonBlankString(store, classnameRePropertyName)(properties) match {
              case Some((_, re)) =>
                providers.find(_.getClass.getName.matches(re)) match {
                  case Some(provider) =>
                    debug(s"Found JCache provider in the classpath with class name matching `$re`")
                    provider
                  case None =>
                    val msg = s"No JCache provider found in the classpath with class name matching `$re`"
                    error(msg)
                    throw new IllegalStateException(msg)
                }
              case None =>
                if (store)
                  debug(s"No `${classnameRePropertyName(true)}` or `${classnameRePropertyName(false)}` property set")
                else
                  debug(s"No `${classnameRePropertyName(false)}` property set")
                if (providers.size > 1)
                  debug(s"  picking first provider found out of ${providers.size} providers")
                else
                  debug(s"  only one provider found")
                providers.head
            }
        }

      info(s"Using the JCache provider found in the classpath with class name: `${provider.getClass.getName}`")

      def fromResource: Option[URI] =
        nonBlankString(store, CacheSupport.resourcePropertyName)(properties)
          .flatMap { case (_, p) => Option(getClass.getResource(p)) }
          .map(_.toURI)

      def fromUri: Option[URI] =
        nonBlankString(store, CacheSupport.uriPropertyName)(properties)
          .map { case (_, p) => URI.create(p) }

      val configUri =
        fromResource.orElse(fromUri).getOrElse(provider.getDefaultURI)

      provider.getCacheManager(configUri, getClass.getClassLoader) |!>
        (_ => debug(s"initialized JCache cache manager with URI `$configUri`"))
    } catch {
      case t: Throwable => // don't use `NonFatal()` here as we want to catch all for example `NoClassDefFoundError`

        Try(Properties.instance.getPropertySetOrThrow)
          .foreach(CacheSupport.logProperties(_, CacheSupport.Logger.error))

        throw new OXFException(s"unable to initialize JCache cache manager", t)
    }
}