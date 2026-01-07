package org.orbeon.oxf.cache

import org.infinispan.commons.configuration.io.ConfigurationResourceResolvers
import org.infinispan.commons.dataconversion.MediaType
import org.infinispan.configuration.parsing.ParserRegistry
import org.infinispan.manager.DefaultCacheManager
import org.orbeon.io.FileUtils
import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.properties.PropertyLoader
import org.orbeon.oxf.util.ExternalContextSupport
import org.orbeon.oxf.util.StringUtils.OrbeonStringOps

import java.io
import java.util.Properties
import scala.util.{Try, Using}


object InfinispanProvider extends CacheProviderApi {

  private val DefaultResourcePath  = "/config/infinispan.xml"
  private val WebappTmpDirProperty = "orbeon.webapp.tmpdir"

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
        .map(url => Using.resource(url.openStream()) { configurationStream =>

          import org.orbeon.oxf.util.PathUtils.*

          // Prefix the temporary directory with the web app context path to avoid clashes between multiple web apps
          // https://github.com/orbeon/orbeon-forms/issues/7415
          val contextPathOpt = ExternalContextSupport.externalContextOpt.map(_.getWebAppContext.getContextPath)
          val contextOpt     = contextPathOpt.map(_.dropStartingSlash.dropTrailingSlash).flatMap(_.trimAllToOpt)

          val webappTmpDir =
            contextOpt match {
              case Some(context) => FileUtils.canonicalTemporaryDirectoryPath + java.io.File.separatorChar + context
              case None          => FileUtils.canonicalTemporaryDirectoryPath
            }

          val properties = new Properties(System.getProperties)
          properties.put(WebappTmpDirProperty, webappTmpDir)

          new DefaultCacheManager(new ParserRegistry(getClass.getClassLoader, false, properties)
            .parse(configurationStream, ConfigurationResourceResolvers.DEFAULT, MediaType.APPLICATION_XML), true)
        })
        .get
    } catch {
      case t: Throwable => // don't use `NonFatal()` here as we want to catch all for example `NoClassDefFoundError`
        Try(PropertyLoader.getPropertyStore(None).globalPropertySet)
          .foreach(CacheSupport.logProperties(_, CacheSupport.Logger.error))

        throw new OXFException(s"unable to initialize Infinispan cache manager from `$DefaultResourcePath``", t)
    }
}
