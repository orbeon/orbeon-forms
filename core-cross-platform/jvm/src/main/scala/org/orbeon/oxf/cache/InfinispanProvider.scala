package org.orbeon.oxf.cache

import org.infinispan.manager.DefaultCacheManager
import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.properties.PropertyLoader

import java.io
import scala.util.{Try, Using}


object InfinispanProvider extends CacheProviderApi {

  private val DefaultResourcePath = "/config/infinispan.xml"

  import CacheSupport.Logger.*

  def get(cacheName: String): Option[CacheApi] =
    Option(cacheManager.getCache[io.Serializable, io.Serializable](cacheName))
      .map(new InfinispanCacheApi(_))

  def close(): Unit =
    cacheManager.close()

  private class InfinispanCacheApi(private val cache: org.infinispan.Cache[io.Serializable, io.Serializable]) extends CacheApi {
    def put(k: io.Serializable, v: io.Serializable): Unit         = { trace("put");                    cache.put(k, v) }
    def putIfAbsent(k: io.Serializable, v: io.Serializable): Unit = { trace("putIfAbsent");            cache.putIfAbsent(k, v) }
    def get(k: io.Serializable): Option[io.Serializable]          = { trace("get");                    Option(cache.get(k)) }
    def remove(k: io.Serializable): Boolean                       = { trace("remove");                 cache.remove(k) != null }
    def getName: String                                           = { trace("getName");                cache.getName }
    def getMaxEntriesLocalHeap: Option[Long]                      = { trace("getMaxEntriesLocalHeap"); Some(cache.getCacheConfiguration.memory().maxCount()) }
    def getLocalHeapSize: Option[Long]                            = { trace("getLocalHeapSize");       Some(cache.getCacheConfiguration.memory().maxSizeBytes()) }
  }

  private lazy val cacheManager: DefaultCacheManager =
    try {
      Option(getClass.getResource(DefaultResourcePath))
        .map(url => Using.resource(url.openStream())(new DefaultCacheManager(_)))
        .get
    } catch {
      case t: Throwable => // don't use `NonFatal()` here as we want to catch all for example `NoClassDefFoundError`
        Try(PropertyLoader.getPropertyStore(None).globalPropertySet)
          .foreach(CacheSupport.logProperties(_, CacheSupport.Logger.error))

        throw new OXFException(s"unable to initialize Infinispan cache manager from `$DefaultResourcePath``", t)
    }
}
