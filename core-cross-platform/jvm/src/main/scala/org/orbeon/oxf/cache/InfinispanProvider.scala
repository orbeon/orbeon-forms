package org.orbeon.oxf.cache

import org.infinispan.commons.configuration.io.ConfigurationResourceResolvers
import org.infinispan.commons.dataconversion.MediaType
import org.infinispan.configuration.parsing.ParserRegistry
import org.infinispan.manager.DefaultCacheManager
import org.infinispan.persistence.sifs.configuration.SoftIndexFileStoreConfiguration
import org.orbeon.io.FileUtils
import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.properties.{PropertyLoader, PropertySet}
import org.orbeon.oxf.util.ExternalContextSupport
import org.orbeon.oxf.util.StringUtils.OrbeonStringOps

import java.io
import java.io.File
import java.net.URI
import java.util.Properties
import scala.collection.mutable
import scala.jdk.CollectionConverters.*
import scala.util.chaining.*
import scala.util.{Try, Using}


object InfinispanProvider {

  val ProviderName                     = "infinispan"
  private val DefaultResourcePath      = "/config/infinispan.xml"
  private val WebappTmpDirProperty     = "orbeon.webapp.tmpdir"

  private final class SharedManager(
    val manager    : DefaultCacheManager,
    val description: String,
    var refCount   : Int
  )

  private case class ResolvedConfigSource(
    key        : String,
    description: String,
    openStream : () => io.InputStream
  )

  private val sharedManagers = mutable.Map.empty[String, SharedManager]

  private def resolveConfigSource(store: Boolean, properties: PropertySet): ResolvedConfigSource = {
    def resolveClasspathStream(rawPath: String): Option[() => io.InputStream] = {
      val normalized = if (rawPath.startsWith("/")) rawPath else "/" + rawPath
      Option(getClass.getResource(normalized)).map(url => () => url.openStream())
    }

    CacheSupport.nonBlankString(store, CacheSupport.resourcePropertyName(_, ProviderName))(properties) match {
      case Some((propName, rawPath)) =>
        val openFn =
          CacheSupport.configResolverOpt
            .flatMap(_.resolveResource(rawPath))
            .orElse(resolveClasspathStream(rawPath))
            .getOrElse(
              throw new OXFException(s"Infinispan configuration resource `$rawPath` specified by `$propName` was not found")
            )
        ResolvedConfigSource(s"resource:$rawPath", s"resource `$rawPath` (`$propName`)", openFn)

      case None =>
        CacheSupport.nonBlankString(store, CacheSupport.uriPropertyName(_, ProviderName))(properties) match {
          case Some((propName, rawUri)) =>
            val openFn: () => io.InputStream =
              CacheSupport.configResolverOpt
                .flatMap(_.resolveUri(rawUri))
                .getOrElse(() => URI.create(rawUri).toURL.openStream())
            ResolvedConfigSource(s"uri:$rawUri", s"URI `$rawUri` (`$propName`)", openFn)

          case None =>
            val openFn =
              CacheSupport.configResolverOpt
                .flatMap(_.resolveResource(DefaultResourcePath))
                .orElse(resolveClasspathStream(DefaultResourcePath))
                .getOrElse(
                  throw new OXFException(s"Default Infinispan configuration `$DefaultResourcePath` was not found")
                )
            ResolvedConfigSource(s"default:$DefaultResourcePath", s"default resource `$DefaultResourcePath`", openFn)
        }
    }
  }

  private def acquireManager(store: Boolean, properties: PropertySet): (String, DefaultCacheManager) =
    synchronized {
      import org.orbeon.oxf.util.PathUtils.*

      val source         = resolveConfigSource(store, properties)
      val siblingKeyOpt  = Try(resolveConfigSource(! store, properties).key).toOption
      val sharesConfig   = siblingKeyOpt.contains(source.key)

      val contextPathOpt = ExternalContextSupport.externalContextOpt.map(_.getWebAppContext.getContextPath)
      val contextOpt     = contextPathOpt.map(_.dropStartingSlash.dropTrailingSlash).flatMap(_.trimAllToOpt)

      val rolePrefix =
        if (sharesConfig) "infinispan"
        else if (store)   "store"
        else              "cache"

      val dirLeaf =
        if (source.key == s"default:$DefaultResourcePath")
          rolePrefix
        else
          s"$rolePrefix-${Integer.toHexString(source.key.hashCode)}"

      val managerKey = s"${contextOpt.getOrElse("")}|$dirLeaf|${source.key}"

      sharedManagers.get(managerKey) match {
        case Some(existing) =>
          existing.refCount += 1
          CacheSupport.Logger.debug(s"Reusing Infinispan DefaultCacheManager for ${if (store) "store" else "cache"} from ${existing.description} (refCount = ${existing.refCount})")
          (managerKey, existing.manager)

        case None =>
          val webappTmpDir =
            contextOpt match {
              case Some(context) => FileUtils.canonicalTemporaryDirectoryPath + File.separatorChar + context + File.separatorChar + dirLeaf
              case None          => FileUtils.canonicalTemporaryDirectoryPath + File.separatorChar + dirLeaf
            }

          val sysProps = new Properties(System.getProperties)
          sysProps.put(WebappTmpDirProperty, webappTmpDir)

          val manager =
            Using.resource(source.openStream()) { configurationStream =>
              new DefaultCacheManager(
                new ParserRegistry(getClass.getClassLoader, false, sysProps)
                  .parse(configurationStream, ConfigurationResourceResolvers.DEFAULT, MediaType.APPLICATION_XML),
                true
              )
            }

          CacheSupport.Logger.info(s"Initialized Infinispan DefaultCacheManager from ${source.description}")
          sharedManagers.put(managerKey, new SharedManager(manager, source.description, refCount = 1))
          (managerKey, manager)
      }
    }

  private def releaseManager(managerKey: String): Unit =
    synchronized {
      sharedManagers.get(managerKey).foreach { shared =>
        shared.refCount -= 1
        if (shared.refCount <= 0) {
          sharedManagers.remove(managerKey)
          shared.manager.close()
        }
      }
    }
}

class InfinispanProvider(store: Boolean, propertySetOpt: Option[PropertySet] = None) extends CacheProviderApi {

  def this(store: Boolean) = this(store, None)

  val ProviderName: String = InfinispanProvider.ProviderName

  import CacheSupport.Logger.*

  def findConfigured(cacheName: String): Option[CacheApi] =
    if (cacheManager.cacheConfigurationExists(cacheName) || cacheManager.getCacheNames.contains(cacheName))
      Option(cacheManager.getCache[io.Serializable, io.Serializable](cacheName))
        .map(new InfinispanCacheApi(_))
    else
      None

  def listRunningCacheSnapshots: List[CacheProviderStats] =
    synchronized {
      if (! open || closed) {
        Nil
      } else {
        cacheManager
          .getCacheNames
          .asScala
          .filter(cacheManager.isRunning)
          .toList
          .sorted
          .flatMap(name => Option(cacheManager.getCache[io.Serializable, io.Serializable](name)))
          .map(buildStatsSnapshot)
      }
    }

  private var open                      = false
  private var closed                    = false
  private var acquiredKeyOpt: Option[String] = None

  private[cache] def underlyingCacheManagerForTests: DefaultCacheManager = cacheManager

  def close(): Unit =
    synchronized {
      if (open && ! closed) {
        acquiredKeyOpt.foreach(InfinispanProvider.releaseManager)
        acquiredKeyOpt = None
      }
      closed = true
    }

  private class InfinispanCacheApi(private val cache: org.infinispan.Cache[io.Serializable, io.Serializable]) extends CacheApi {
    def put(k: io.Serializable, v: io.Serializable): Unit         = { trace("put");                        cache.put(k, v) }
    def putIfAbsent(k: io.Serializable, v: io.Serializable): Unit = { trace("putIfAbsent");                cache.putIfAbsent(k, v) }
    def get(k: io.Serializable): Option[io.Serializable]          = { trace("get");                        Option(cache.get(k)) }
    def remove(k: io.Serializable): Boolean                       = { trace("remove");                     cache.remove(k) != null }
    def clear(): Unit                                             = { trace("clear");                      cache.clear() }
    def getName: String                                           = { trace("getName");                    cache.getName }
    def getCurrentEntriesLocalHeap: Option[Long]                  = { trace("getCurrentEntriesLocalHeap"); Some(cache.getAdvancedCache.getDataContainer.size().toLong) }
    def getMaxEntriesLocalHeap: Option[Long]                      = {
      trace("getMaxEntriesLocalHeap")
      Some(cache.getCacheConfiguration.memory().maxCount()).filter(_ > 0)
    }
    def getMaxBytesLocalHeap: Option[Long]                        = {
      trace("getMaxBytesLocalHeap")
      Some(cache.getCacheConfiguration.memory().maxSizeBytes()).filter(_ > 0)
    }
    def statsSnapshot: Option[CacheProviderStats]                 = Some(buildStatsSnapshot(cache))
  }

  private def buildStatsSnapshot(cache: org.infinispan.Cache[io.Serializable, io.Serializable]): CacheProviderStats = {
    val adv           = cache.getAdvancedCache
    val cfg           = cache.getCacheConfiguration
    val dataContainer = adv.getDataContainer
    val statsEnabled  = cfg.statistics().enabled()
    val stats         = adv.getStats

    // Estimate in-heap bytes directly from DataContainer without triggering disk store loads
    var heapEntries = 0L
    var heapBytes   = 0L
    val it          = dataContainer.iterator()
    while (it.hasNext) {
      val ice = it.next()
      heapEntries += 1L
      heapBytes   += 64L + MemorySizeEstimator.estimateBytes(ice.getKey) + MemorySizeEstimator.estimateBytes(ice.getValue)
    }

    val maxCountOpt    = Some(cfg.memory().maxCount()).filter(_ > 0)
    val maxBytesOpt    = Some(cfg.memory().maxSizeBytes()).filter(_ > 0)
    val maxIdleOpt     = Some(cfg.expiration().maxIdle()).filter(_ > 0)
    val lifespanOpt    = Some(cfg.expiration().lifespan()).filter(_ > 0)
    val hasPersistence = cfg.persistence().usingStores()
    val isPassivation  = hasPersistence && cfg.persistence().passivation()

    val (diskBytesOpt, diskFilesOpt, diskPathOpt) =
      if (hasPersistence)
        inspectSifsDiskUsage(cache.getName, cfg)
      else
        (None, None, None)

    val totalEntriesOpt =
      if (statsEnabled) {
        val approx = stats.getApproximateEntries
        if (approx >= 0) Some(math.max(heapEntries, approx)) else Some(heapEntries)
      } else if (! hasPersistence) {
        Some(heapEntries)
      } else {
        None
      }

    CacheProviderStats(
      name                = cache.getName,
      provider            = ProviderName,
      isStore             = store,
      heapEntries         = heapEntries,
      totalEntries        = totalEntriesOpt,
      maxEntriesLocalHeap = maxCountOpt,
      maxBytesLocalHeap   = maxBytesOpt,
      estimatedHeapBytes  = heapBytes,
      diskStoreBytes      = diskBytesOpt,
      diskStoreFiles      = diskFilesOpt,
      diskStorePathOpt    = diskPathOpt,
      maxIdleMs           = maxIdleOpt,
      lifespanMs          = lifespanOpt,
      passivation         = isPassivation,
      hitCount            = Option.when(statsEnabled)(stats.getHits).filter(_ >= 0),
      missCount           = Option.when(statsEnabled)(stats.getMisses).filter(_ >= 0),
      storeCount          = Option.when(statsEnabled)(stats.getStores).filter(_ >= 0),
      evictionCount       = Option.when(statsEnabled)(stats.getEvictions).filter(_ >= 0)
    )
  }

  private def inspectSifsDiskUsage(
    cacheName: String,
    cfg      : org.infinispan.configuration.cache.Configuration
  ): (Option[Long], Option[Int], Option[String]) = {
    val persistentBase =
      Try(cacheManager.getCacheManagerConfiguration.globalState().persistentLocation()).toOption.filter(_ ne null)

    def resolveDir(loc: String): Option[File] =
      Option(loc).flatMap(_.trimAllToOpt).map { p =>
        val f = new File(p)
        if (f.isAbsolute || persistentBase.isEmpty) f
        else new File(persistentBase.get, p)
      }

    val candidateDirs = mutable.LinkedHashSet.empty[File]
    cfg.persistence().stores().asScala.foreach {
      case sifs: SoftIndexFileStoreConfiguration =>
        resolveDir(sifs.dataLocation()).foreach(candidateDirs += _)
        resolveDir(sifs.indexLocation()).foreach(candidateDirs += _)
      case _ =>
    }
    persistentBase.foreach { base =>
      val byCacheName = new File(base, cacheName)
      if (byCacheName.exists())
        candidateDirs += byCacheName
    }

    val visitedFiles = mutable.HashSet.empty[String]
    var totalBytes   = 0L
    var fileCount    = 0

    def walk(file: File): Unit =
      if ((file ne null) && file.exists()) {
        if (file.isFile) {
          val canonical = Try(file.getCanonicalPath).getOrElse(file.getAbsolutePath)
          if (visitedFiles.add(canonical)) {
            totalBytes += file.length()
            fileCount  += 1
          }
        } else if (file.isDirectory) {
          Option(file.listFiles()).foreach(_.foreach(walk))
        }
      }

    candidateDirs.foreach(walk)

    val primaryPathOpt = candidateDirs.headOption.map(_.getAbsolutePath)
    (Some(totalBytes), Some(fileCount), primaryPathOpt)
  }

  private lazy val cacheManager: DefaultCacheManager =
    try {
      val properties =
        propertySetOpt
          .getOrElse(PropertyLoader.getPropertyStore(None).globalPropertySet) // boot phase when no PropertySet passed
          .tap(CacheSupport.logProperties(_, CacheSupport.Logger.debug))

      val (key, manager) = InfinispanProvider.acquireManager(store, properties)
      synchronized {
        acquiredKeyOpt = Some(key)
        open           = true
      }
      manager
    } catch {
      case t: Throwable => // don't use `NonFatal()` here as we want to catch all for example `NoClassDefFoundError`
        Try(propertySetOpt.getOrElse(PropertyLoader.getPropertyStore(None).globalPropertySet))
          .foreach(CacheSupport.logProperties(_, CacheSupport.Logger.error))

        throw new OXFException("unable to initialize Infinispan cache manager", t)
    }
}
