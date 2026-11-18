/**
 * Copyright (C) 2009 Orbeon, Inc.
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

import java.util as ju


trait Cache {
  def add(key: CacheKey, validity: Any, cacheable: Any): Unit
  def remove(key: CacheKey): Unit
  def removeAll(): Int
  def findValidWithValidity(key: CacheKey, validity: Any): Option[(AnyRef, AnyRef)]
  def findValid(key: CacheKey, validity: Any): AnyRef
  def takeValid(key: CacheKey, validity: Any): AnyRef
  def findAny(key: CacheKey): CacheEntry
  def iterateCacheKeys(): ju.Iterator[CacheKey]
  def iterateCacheObjects(): ju.Iterator[AnyRef]
  def getCurrentSize: Int
  def getMaxSize: Int
  def setMaxSize(maxSize: Int): Unit
  def getMaxSizeBytes: Option[Long]
  def setMaxSizeBytes(maxSizeBytes: Option[Long]): Unit
  def getMaxIdleMillis: Option[Long]
  def setMaxIdleMillis(maxIdleMillis: Option[Long]): Unit
  def evictExpired(): Int
  def getCurrentEstimatedBytes: Long
  def getHitCount: Long
  def getMissCount: Long
  def getAddCount: Long
  def getEvictionCount: Long
  def getRemoveCount: Long
  def resetStats(): Unit
}
