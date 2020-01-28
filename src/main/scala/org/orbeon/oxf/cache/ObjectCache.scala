/**
  * Copyright (C) 2010 Orbeon, Inc.
  *
  * This program is free software; you can redistribute it and/or modify it under the terms of the
  * GNU Lesser General Public License as published by the Free Software Foundation; either version
  * 2.1 of the License, or (at your option) any later version.
  *
  * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  * See the GNU Lesser General Public License for more details.
  *
  * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
  */
package org.orbeon.oxf.cache

import java.{util => ju}

import org.orbeon.oxf.properties.Properties

object ObjectCache {

  private val DefaultCacheName = "cache.main"
  private val DefaultSize      = 200

  private val CachePropertyNamePrefix     = "oxf"
  private val CachePropertyNameSizeSuffix = "size"

  private val namedObjectCaches = new ju.concurrent.ConcurrentHashMap[String, Cache]

  locally {
    namedObjectCaches.put(DefaultCacheName, new MemoryCacheImpl(DefaultSize))
  }

  // Get the instance of the main object cache
  def instance: Cache = namedObjectCaches.get(DefaultCacheName)

  // Get the instance of the object cache specified
  def instance(cacheName: String, defaultSize: Int): Cache =
    namedObjectCaches.computeIfAbsent(
      cacheName,
      _ => {

        val propertyName = s"$CachePropertyNamePrefix.$cacheName.$CachePropertyNameSizeSuffix"
        val size         = Properties.instance.getPropertySetOrThrow.getInteger(propertyName, defaultSize)

        new MemoryCacheImpl(size)
      }
    )
}
