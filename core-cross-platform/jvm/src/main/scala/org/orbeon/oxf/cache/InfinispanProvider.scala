package org.orbeon.oxf.cache

import org.infinispan.commons.configuration.io.ConfigurationResourceResolvers
import org.infinispan.commons.dataconversion.MediaType
import org.infinispan.configuration.parsing.ParserRegistry
import org.infinispan.manager.DefaultCacheManager
import org.orbeon.io.FileUtils
import org.orbeon.oxf.cache.CacheSupport.nonBlankString
import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.properties.PropertyLoader
import org.orbeon.oxf.util.ExternalContextSupport
import org.orbeon.oxf.util.StringUtils.OrbeonStringOps

import java.io
import java.net.{URI, URL}
import java.util.Properties
import scala.util.chaining.*
import scala.util.{Try, Using}


// xxx TODO
class InfinispanProvider(store: Boolean) extends CacheProviderApi {

  val ProviderName         = "infinispan"

  private val DefaultResourcePath  = "/config/infinispan.xml"
  private val WebappTmpDirProperty = "orbeon.webapp.tmpdir"

  import CacheSupport.Logger.*

  def get(cacheName: String): Option[CacheApi] =
    Option(cacheManager.getCache[io.Serializable, io.Serializable](cacheName))
      .map(new InfinispanCacheApi(_))

  private var open   = false
  private var closed = false

  def close(): Unit =
    synchronized {
      if (open && ! closed)
        cacheManager.close()
      closed = true
    }

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

      val properties =
        PropertyLoader
          .getPropertyStore(None) // we are typically in the boot phase here so there is no request
          .globalPropertySet
          .tap(CacheSupport.logProperties(_, CacheSupport.Logger.debug))

//      // Use `URL` instead of `URI` for now as we want to eventually support `oxf:/`
//      def fromResource: Option[URL] =
//        nonBlankString(store, CacheSupport.resourcePropertyName(_, ProviderName))(properties)
//          .flatMap { case (_, p) => Option(getClass.getResource(p)) }
//
//      def fromUri: Option[URL] =
//        nonBlankString(store, CacheSupport.uriPropertyName(_, ProviderName))(properties)
//          .map { case (_, p) => URI.create(p).toURL }
//
//      val configUri =
//        fromResource.orElse(fromUri).getOrElse(URI.create(DefaultResourcePath))

      Option(getClass.getResource(DefaultResourcePath))
        .map(url => Using.resource(url.openStream()) { configurationStream =>

          import org.orbeon.oxf.util.PathUtils.*

          // Prefix the temporary directory with the web app context path to avoid clashes between multiple web apps
          // https://github.com/orbeon/orbeon-forms/issues/7415
          val contextPathOpt = ExternalContextSupport.externalContextOpt.map(_.getWebAppContext.getContextPath)
          val contextOpt     = contextPathOpt.map(_.dropStartingSlash.dropTrailingSlash).flatMap(_.trimAllToOpt)

          val webappTmpDir =
            contextOpt match {
              case Some(context) => FileUtils.canonicalTemporaryDirectoryPath + java.io.File.separatorChar + context + java.io.File.separatorChar + (if (store) "store" else "cache")
              case None          => FileUtils.canonicalTemporaryDirectoryPath + java.io.File.separatorChar + (if (store) "store" else "cache")
            }

          val properties = new Properties(System.getProperties)
          properties.put(WebappTmpDirProperty, webappTmpDir)

          new DefaultCacheManager(new ParserRegistry(getClass.getClassLoader, false, properties)
            .parse(configurationStream, ConfigurationResourceResolvers.DEFAULT, MediaType.APPLICATION_XML), true)
        })
        .get
        .tap(_ => open = true)
    } catch {
      case t: Throwable => // don't use `NonFatal()` here as we want to catch all for example `NoClassDefFoundError`
        Try(PropertyLoader.getPropertyStore(None).globalPropertySet)
          .foreach(CacheSupport.logProperties(_, CacheSupport.Logger.error))

        throw new OXFException(s"unable to initialize Infinispan cache manager from `$DefaultResourcePath``", t)
    }
}
