package org.orbeon.oxf.cache

import cats.data.NonEmptyList
import cats.syntax.option._
import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.properties.Properties
import org.orbeon.oxf.util.CoreUtils.PipeOps

import java.io
import java.net.URI
import javax.cache.Caching
import scala.jdk.CollectionConverters.iterableAsScalaIterableConverter
import scala.util.Try
import scala.util.control.NonFatal


object JCacheSupport extends CacheProviderApi {

  import CacheSupport.Logger._

  def get(cacheName: String): Option[CacheApi] =
    cacheManager.getCache[io.Serializable, io.Serializable](cacheName) match {
      case null =>
        debug(s"did not find JCache cache for `$cacheName`")
        None
      case cache =>
        debug(s"found JCache cache for `$cacheName`")
        new JCacheCacheApi(cache).some
    }

  def close(): Unit =
    provider.close() // xxx TODO: what if provider used by others? should we close it, or just the cache manager?

  class JCacheCacheApi(private val cache: javax.cache.Cache[io.Serializable, io.Serializable]) extends CacheApi  {
    def put(k: io.Serializable, v: io.Serializable): Unit         = { trace("put");                    cache.put(k, v) }
    def putIfAbsent(k: io.Serializable, v: io.Serializable): Unit = { trace("putIfAbsent");            cache.putIfAbsent(k, v) }
    def get(k: io.Serializable): Option[io.Serializable]          = { trace("get");                    Option(cache.get(k)) }
    def remove(k: io.Serializable): Boolean                       = { trace("remove");                 cache.remove(k) }
    def getName: String                                           = { trace("getName");                cache.getName }
    def getMaxEntriesLocalHeap: Option[Long]                      = { trace("getMaxEntriesLocalHeap"); None }
    def getLocalHeapSize: Option[Long]                            = { trace("getLocalHeapSize");       None }
  }

  private lazy val (provider, cacheManager) =
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

            properties.getNonBlankString(CacheSupport.ClassnameRePropertyName) match {
              case Some(re) =>
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
                debug(s"No `${CacheSupport.ClassnameRePropertyName}` property set")
                if (providers.size > 1)
                  debug(s"  picking first provider found out of ${providers.size} providers")
                else
                  debug(s"  only one provider found")
                providers.head
            }
        }

      info(s"Using the JCache provider found in the classpath with class name: `${provider.getClass.getName}`")

      def fromResource: Option[URI] =
        properties
          .getNonBlankString(CacheSupport.ResourcePropertyName)
          .flatMap(p => Option(getClass.getResource(p)))
          .map(_.toURI)

      def fromUri: Option[URI] =
        properties
          .getNonBlankString(CacheSupport.UriPropertyName)
          .map(URI.create)

      val configUri =
        fromResource.orElse(fromUri).getOrElse(provider.getDefaultURI)

      (
        provider,
        provider.getCacheManager(configUri, getClass.getClassLoader) |!>
          (_ => debug(s"initialized JCache cache manager"))
      )
    } catch {
      case NonFatal(t) =>

        Try(Properties.instance.getPropertySetOrThrow)
          .foreach(CacheSupport.logProperties(_, CacheSupport.Logger.error))

        throw new OXFException(s"unable to initialize JCache cache manager", t)
    }
}