package org.orbeon.oxf.cache

import cats.syntax.option._
import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.properties.Properties
import org.orbeon.oxf.util.CoreUtils.PipeOps

import java.io
import java.net.URI
import javax.cache.Caching
import scala.util.control.NonFatal


object JCacheSupport extends CacheProvider {

  def get(cacheName: String): Option[CacheApi] =
    cacheManager.getCache[io.Serializable, io.Serializable](cacheName) match {
      case null =>
        CacheSupport.Logger.debug(s"did not find JCache cache for `$cacheName`")
        None
      case cache =>
        CacheSupport.Logger.debug(s"found JCache cache for `$cacheName`")
        new JCacheCacheApi(cache).some
    }

  def close(): Unit =
    provider.close()

  class JCacheCacheApi(private val cache: javax.cache.Cache[io.Serializable, io.Serializable]) extends CacheApi  {
    def put(k: io.Serializable, v: io.Serializable): Unit = cache.put(k, v)
    def putIfAbsent(k: io.Serializable, v: io.Serializable): Unit = cache.putIfAbsent(k, v)
    def get(k: io.Serializable): Option[io.Serializable] = Option(cache.get(k))
    def remove(k: io.Serializable): Boolean = cache.remove(k)
    def getName: String = cache.getName
    def getMaxEntriesLocalHeap: Option[Long] = None
    def getLocalHeapSize: Option[Long] = None
  }

  private lazy val (provider, cacheManager) =
    try {
      val provider = Caching.getCachingProvider

      val properties = Properties.instance.getPropertySetOrThrow

      def fromResource: Option[URI] =
        properties
          .getNonBlankString(CacheSupport.ResourcePropertyName)
          .map(getClass.getResource(_).toURI)

      def fromUri: Option[URI] =
        properties
          .getNonBlankString(CacheSupport.UriPropertyName)
          .map(URI.create)

      val configUri =
        fromResource.orElse(fromUri).getOrElse(provider.getDefaultURI)

      (
        provider,
        provider.getCacheManager(configUri, getClass.getClassLoader) |!>
          (_ => CacheSupport.Logger.debug(s"initialized JCache cache manager"))
      )
    } catch {
      case NonFatal(t) =>
        throw new OXFException(s"unable to initialize JCache cache manager", t)
    }
}