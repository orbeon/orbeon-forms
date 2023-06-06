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

import cats.syntax.option._
import net.sf.ehcache
import net.sf.ehcache.CacheManager
import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.resources.URLFactory
import org.orbeon.oxf.util.CoreUtils.PipeOps

import java.io
import scala.util.control.NonFatal


object Eh2CacheSupport extends CacheProvider {

  import Private._

  def get(cacheName: String): Option[CacheApi] =
    cacheManager.getCache(cacheName) match {
      case cache: ehcache.Cache =>
        CacheSupport.Logger.debug(s"found Ehcache 2 cache for `$cacheName`")
        new JCacheCacheApi(cache).some
      case _ =>
        CacheSupport.Logger.debug(s"did not find Ehcache 2 cache for `$cacheName`")
        None
    }

  // This replicates what the Ehcache 2.x shutdown hook does, but we need to do it ourselves because we don't
  // necessarily use Ehcache 2.x anymore.
  def close(): Unit = {
    val knownCacheManagers = CacheManager.ALL_CACHE_MANAGERS
    CacheSupport.Logger.debug(s"Shutting down ${knownCacheManagers.size} Ehcache 2.x CacheManagers.")
    while (! knownCacheManagers.isEmpty)
      CacheManager.ALL_CACHE_MANAGERS.get(0).shutdown()
  }

  class JCacheCacheApi(private val cache: ehcache.Cache) extends CacheApi {
    def put(k: io.Serializable, v: io.Serializable): Unit = cache.put(new ehcache.Element(k, v))
    def putIfAbsent(k: io.Serializable, v: io.Serializable): Unit = put(k, v) // TODO: does Ehache 2.x have a more efficient equivalent?
    def get(k: io.Serializable): Option[io.Serializable] = Option(cache.get(k)).map(_.getObjectValue.asInstanceOf[io.Serializable])
    def remove(k: io.Serializable): Boolean = cache.remove(k)
    def getName: String = cache.getName
    def getMaxEntriesLocalHeap: Option[Long] = cache.getCacheConfiguration.getMaxEntriesLocalHeap.some
    def getLocalHeapSize: Option[Long] = cache.getCacheConfiguration.getMaxBytesLocalHeap.some
  }

  private object Private {

    val EhcachePath = "oxf:/config/ehcache.xml"

    val cacheManager =
      try
        new CacheManager(URLFactory.createURL(EhcachePath)) |!>
          (_ => CacheSupport.Logger.debug(s"initialized Ehcache 2 cache manager from `$EhcachePath`"))
      catch {
        case NonFatal(t) =>
          throw new OXFException(s"unable to initialize Ehcache 2 cache manager from `$EhcachePath`", t)
      }
  }
}