/**
  * Copyright (C) 2017 Orbeon, Inc.
  *
  * This program is free software; you can redistribute it and/or modify it under the terms of the
  * GNU Lesser General Public License as published by the Free Software Foundation; either version
  *  2.1 of the License, or (at your option) any later version.
  *
  * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  * See the GNU Lesser General Public License for more details.
  *
  * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
  */
package org.orbeon.oxf.cache

import cats.syntax.option.*
import net.sf.ehcache
import net.sf.ehcache.CacheManager
import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.util.CoreUtils.*

import java.io
import scala.util.control.NonFatal


@deprecated("Use JCacheProvider instead", ";Orbeon Forms 2025.1")
object Ehcache2Provider extends CacheProviderApi {

  val ProviderName: String = "ehcache2"

  import CacheSupport.Logger.*
  import Private.*

  def get(cacheName: String): Option[CacheApi] =
    cacheManager.getCache(cacheName) match {
      case cache: ehcache.Cache =>
        debug(s"found Ehcache 2 cache for `$cacheName`")
        new EhcacheCacheApi(cache).some
      case _ =>
        debug(s"did not find Ehcache 2 cache for `$cacheName`")
        None
    }

  // This replicates what the Ehcache 2.x shutdown hook does, but we need to do it ourselves because we don't
  // necessarily use Ehcache 2.x anymore.
  def close(): Unit = {
    val knownCacheManagers = CacheManager.ALL_CACHE_MANAGERS
    debug(s"Shutting down ${knownCacheManagers.size} Ehcache 2.x CacheManagers.")
    while (! knownCacheManagers.isEmpty)
      CacheManager.ALL_CACHE_MANAGERS.get(0).shutdown()
  }

  def listRunningCacheSnapshots: List[CacheProviderStats] =
    cacheManager.getCacheNames.toList.sorted.flatMap(get).flatMap(_.statsSnapshot)

  private class EhcacheCacheApi(private val cache: ehcache.Cache) extends CacheApi {
    def put(k: io.Serializable, v: io.Serializable): Unit         = { trace("put");                        cache.put(new ehcache.Element(k, v)) }
    def putIfAbsent(k: io.Serializable, v: io.Serializable): Unit = { trace("putIfAbsent");                cache.put(new ehcache.Element(k, v)) }
    def get(k: io.Serializable): Option[io.Serializable]          = { trace("get");                        Option(cache.get(k)).map(_.getObjectValue.asInstanceOf[io.Serializable]) }
    def remove(k: io.Serializable): Boolean                       = { trace("remove");                     cache.remove(k) }
    def clear(): Unit                                             = { trace("clear");                      cache.removeAll() }
    def getName: String                                           = { trace("getName");                    cache.getName }
    def getCurrentEntriesLocalHeap: Option[Long]                  = { trace("getCurrentEntriesLocalHeap"); cache.getStatistics.getLocalHeapSize.some }
    def getMaxEntriesLocalHeap: Option[Long]                      = { trace("getMaxEntriesLocalHeap");     Some(cache.getCacheConfiguration.getMaxEntriesLocalHeap).filter(_ > 0) }
    def getMaxBytesLocalHeap: Option[Long]                        = { trace("getMaxBytesLocalHeap");       Some(cache.getCacheConfiguration.getMaxBytesLocalHeap).filter(_ > 0) }
    def statsSnapshot: Option[CacheProviderStats]                 = {
      val stats = cache.getStatistics
      CacheProviderStats(
        name                = cache.getName,
        provider            = ProviderName,
        isStore             = cache.getCacheConfiguration.isDiskPersistent || cache.getCacheConfiguration.isOverflowToDisk,
        heapEntries         = stats.getLocalHeapSize,
        totalEntries        = Some(stats.getSize),
        maxEntriesLocalHeap = getMaxEntriesLocalHeap,
        maxBytesLocalHeap   = getMaxBytesLocalHeap,
        estimatedHeapBytes  = stats.getLocalHeapSizeInBytes,
        diskStoreBytes      = Some(stats.getLocalDiskSizeInBytes).filter(_ >= 0),
        diskStoreFiles      = None,
        diskStorePathOpt    = None,
        maxIdleMs           = Some(cache.getCacheConfiguration.getTimeToIdleSeconds * 1000L).filter(_ > 0),
        lifespanMs          = Some(cache.getCacheConfiguration.getTimeToLiveSeconds * 1000L).filter(_ > 0),
        passivation         = cache.getCacheConfiguration.isOverflowToDisk,
        hitCount            = Some(stats.cacheHitCount()),
        missCount           = Some(stats.cacheMissCount()),
        storeCount          = Some(stats.cachePutCount()),
        evictionCount       = Some(stats.cacheEvictedCount())
      ).some
    }
  }

  private object Private {

    val EhcacheResourcePath = "/config/ehcache.xml"

    val cacheManager =
      try
        new CacheManager(getClass.getResource(EhcacheResourcePath)) |!>
          (_ => debug(s"initialized Ehcache 2 cache manager from `$EhcacheResourcePath`"))
      catch {
        case NonFatal(t) =>
          throw new OXFException(s"unable to initialize Ehcache 2 cache manager from `$EhcacheResourcePath`", t)
      }
  }
}