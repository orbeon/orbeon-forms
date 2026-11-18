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

import java.util as ju
import scala.collection.mutable.ListBuffer
import scala.util.control.Breaks.{break, breakable}


case class ObjectCacheEntrySnapshot(
  key             : CacheKey,
  value           : AnyRef,
  validity        : AnyRef,
  createdAtMs     : Long,
  lastAccessedAtMs: Long,
  estimatedBytes  : Long
)

case class ObjectCacheStatsSnapshot(
  name          : String,
  currentSize   : Int,
  maxSize       : Int,
  maxSizeBytes  : Option[Long],
  maxIdleMillis : Option[Long],
  estimatedBytes: Long,
  hitCount      : Long,
  missCount     : Long,
  addCount      : Long,
  evictionCount : Long,
  removeCount   : Long
) {
  def totalLookups: Long = hitCount + missCount
  def hitRateOpt  : Option[Double] = if (totalLookups > 0) Some(hitCount.toDouble / totalLookups.toDouble) else None
}

class MemoryCacheImpl(
  private var maxSize       : Int,
  private var maxSizeBytes  : Option[Long] = None,
  private var maxIdleMillis : Option[Long] = None,
  clockMillis               : () => Long   = () => System.currentTimeMillis()
) extends Cache {

  maxSizeBytes  = maxSizeBytes.filter(_ > 0L)
  maxIdleMillis = maxIdleMillis.filter(_ > 0L)

  private var keyToEntryMap          = new ju.HashMap[CacheKey, CacheEntry]()
  private var linkedList             = new CacheLinkedList[CacheEntry]()
  private var currentSize            = 0
  private var currentEstimatedBytes  = 0L
  private var evictionListenerOpt    = Option.empty[AnyRef => Unit]

  private var hitCount               = 0L
  private var missCount              = 0L
  private var addCount               = 0L
  private var evictionCount          = 0L
  private var removeCount            = 0L

  def setEvictionListener(listener: AnyRef => Unit): Unit = synchronized {
    evictionListenerOpt = Option(listener)
  }

  override def add(key: CacheKey, validity: Any, cacheable: Any): Unit = synchronized {
    val validityRef  = validity.asInstanceOf[AnyRef]
    val cacheableRef = cacheable.asInstanceOf[AnyRef]
    if ((key eq null) || (validityRef eq null) || maxSize == 0)
      return

    val now = clockMillis()
    evictExpiredUnlocked(now)

    val estBytes = MemorySizeEstimator.estimateBytes(cacheableRef)
    val existing = keyToEntryMap.get(key)

    if (existing eq null) {
      // Evict LRU unlocked entries until within both entry-count and byte budgets (always allowing at least 1 entry
      // so a single large form/document still caches cleanly without self-thrashing).
      def needsEviction: Boolean =
        currentSize >= maxSize ||
          maxSizeBytes.exists(maxBytes => currentSize > 0 && currentEstimatedBytes + estBytes > maxBytes)

      while (needsEviction && tryEvictLast(skipEntry = null)) ()

      currentSize += 1
      currentEstimatedBytes += estBytes
      addCount += 1L

      val entry = new CacheEntry
      entry.key              = key
      entry.validity         = validityRef
      entry.cacheable        = cacheableRef
      entry.createdAtMs      = now
      entry.lastAccessedAtMs = now
      entry.estimatedBytes   = estBytes
      keyToEntryMap.put(key, entry)
      entry.listEntry        = linkedList.addFirst(entry)

      notifyAdded(entry.cacheable)
    } else {
      currentEstimatedBytes = math.max(0L, currentEstimatedBytes - existing.estimatedBytes) + estBytes
      addCount += 1L

      existing.validity         = validityRef
      existing.cacheable        = cacheableRef
      existing.lastAccessedAtMs = now
      existing.estimatedBytes   = estBytes
      linkedList.remove(existing.listEntry)
      existing.listEntry        = linkedList.addFirst(existing)

      // If an in-place update grew the entry past maxSizeBytes, evict older unlocked LRU entries (never `existing`)
      while (
        currentSize > 1 &&
        maxSizeBytes.exists(currentEstimatedBytes > _) &&
        tryEvictLast(skipEntry = existing)
      ) ()
    }
  }

  private def isExpired(entry: CacheEntry, now: Long): Boolean =
    maxIdleMillis.exists(maxIdle => (now - entry.lastAccessedAtMs) > maxIdle)

  override def evictExpired(): Int = synchronized {
    evictExpiredUnlocked(clockMillis())
  }

  // Because every insertion/hit moves an entry to the head with monotonic `now`, the tail (`reverseIterator`)
  // has the oldest `lastAccessedAtMs`, allowing us to stop scanning at the first non-expired entry.
  private def evictExpiredUnlocked(now: Long): Int =
    if (maxIdleMillis.isEmpty || currentSize == 0) {
      0
    } else {
      val candidates = ListBuffer.empty[CacheEntry]
      val it = linkedList.reverseIterator()
      breakable {
        while (it.hasNext) {
          val entry = it.next()
          if (isExpired(entry, now))
            candidates += entry
          else
            break()
        }
      }
      var evicted = 0
      for (entry <- candidates)
        if (keyToEntryMap.containsKey(entry.key) && tryEvict(entry))
          evicted += 1
      evicted
    }

  private def tryEvictLast(skipEntry: CacheEntry): Boolean = {
    val it = linkedList.reverseIterator()
    while (it.hasNext) {
      val entryToTry = it.next()
      if ((entryToTry ne skipEntry) && tryEvict(entryToTry))
        return true
    }
    false
  }

  private def tryEvict(entry: CacheEntry): Boolean = {
    assert(keyToEntryMap.containsKey(entry.key))

    val (lock, canEvict) = entry.cacheable match {
      case c: Cacheable =>
        val l = c.getEvictionLock
        (l, (l eq null) || l.tryLock())
      case _ =>
        (null, true)
    }

    if (canEvict) {
      try {
        removeInternal(entry.key, isEvict = true, isRemove = false, countExplicitRemove = false)
      } finally {
        if (lock ne null)
          lock.unlock()
      }
    }

    canEvict
  }

  override def remove(key: CacheKey): Unit = synchronized {
    removeInternal(key, isEvict = false, isRemove = true, countExplicitRemove = true)
  }

  private def removeInternal(
    key                : CacheKey,
    isEvict            : Boolean,
    isRemove           : Boolean,
    countExplicitRemove: Boolean
  ): Unit = {
    val entry = keyToEntryMap.get(key)
    if (entry ne null) {
      keyToEntryMap.remove(key)
      linkedList.remove(entry.listEntry)
      currentSize -= 1
      currentEstimatedBytes = math.max(0L, currentEstimatedBytes - entry.estimatedBytes)

      if (isEvict) {
        evictionCount += 1L
        notifyEvicted(entry.cacheable)
      } else if (isRemove) {
        if (countExplicitRemove)
          removeCount += 1L
        notifyRemoved(entry.cacheable)
      }
    }
  }

  private def notifyAdded(obj: AnyRef): Unit =
    obj match {
      case c: Cacheable => c.added()
      case _            =>
    }

  private def notifyRemoved(obj: AnyRef): Unit =
    obj match {
      case c: Cacheable => c.removed()
      case _            =>
    }

  private def notifyEvicted(obj: AnyRef): Unit = {
    obj match {
      case c: Cacheable => c.evicted()
      case _            =>
    }
    evictionListenerOpt.foreach(_(obj))
  }

  override def removeAll(): Int = synchronized {
    val previousSize = currentSize

    val it = linkedList.iterator()
    while (it.hasNext)
      notifyRemoved(it.next().cacheable)

    keyToEntryMap         = new ju.HashMap[CacheKey, CacheEntry]()
    linkedList            = new CacheLinkedList[CacheEntry]()
    currentSize           = 0
    currentEstimatedBytes = 0L
    removeCount          += previousSize.toLong
    previousSize
  }

  override def findValidWithValidity(key: CacheKey, validity: Any): Option[(AnyRef, AnyRef)] = {
    val entry = getValidEntry(key, validity.asInstanceOf[AnyRef], remove = false)
    if (entry eq null) None else Some((entry.cacheable, entry.validity))
  }

  override def findValid(key: CacheKey, validity: Any): AnyRef = {
    val result = getValidEntry(key, validity.asInstanceOf[AnyRef], remove = false)
    if (result eq null) null else result.cacheable
  }

  override def takeValid(key: CacheKey, validity: Any): AnyRef = {
    val result = getValidEntry(key, validity.asInstanceOf[AnyRef], remove = true)
    if (result eq null) null else result.cacheable
  }

  private def getValidEntry(key: CacheKey, validity: AnyRef, remove: Boolean): CacheEntry = synchronized {
    val now   = clockMillis()
    val entry = keyToEntryMap.get(key)
    if (entry ne null) {
      if (isExpired(entry, now)) {
        tryEvict(entry)
        missCount += 1L
        null
      } else if (lowerOrEqual(validity, entry.validity)) {
        hitCount += 1L
        entry.lastAccessedAtMs = now

        if (remove) {
          removeInternal(key, isEvict = false, isRemove = true, countExplicitRemove = false)
        } else if (linkedList.getFirst ne entry) {
          linkedList.remove(entry.listEntry)
          entry.listEntry = linkedList.addFirst(entry)
        }

        entry
      } else {
        missCount += 1L
        null
      }
    } else {
      missCount += 1L
      null
    }
  }

  override def findAny(key: CacheKey): CacheEntry = synchronized {
    val entry = keyToEntryMap.get(key)
    if ((entry ne null) && isExpired(entry, clockMillis())) {
      tryEvict(entry)
      null
    } else {
      entry
    }
  }

  override def getCurrentSize: Int           = synchronized { currentSize }
  override def getMaxSize: Int               = synchronized { maxSize }
  override def getMaxSizeBytes: Option[Long] = synchronized { maxSizeBytes }
  override def getMaxIdleMillis: Option[Long]= synchronized { maxIdleMillis }

  override def setMaxSize(newMaxSize: Int): Unit = synchronized {
    if (newMaxSize != this.maxSize) {
      var tryCount = 0
      val maxTries = currentSize
      while (currentSize > newMaxSize && tryCount < maxTries) {
        tryEvictLast(skipEntry = null)
        tryCount += 1
      }
      this.maxSize = newMaxSize
    }
  }

  override def setMaxSizeBytes(newMaxSizeBytes: Option[Long]): Unit = synchronized {
    val normalized = newMaxSizeBytes.filter(_ > 0L)
    if (normalized != this.maxSizeBytes) {
      this.maxSizeBytes = normalized
      normalized.foreach { maxBytes =>
        while (currentSize > 1 && currentEstimatedBytes > maxBytes && tryEvictLast(skipEntry = null)) ()
      }
    }
  }

  override def setMaxIdleMillis(newMaxIdleMillis: Option[Long]): Unit = synchronized {
    val normalized = newMaxIdleMillis.filter(_ > 0L)
    if (normalized != this.maxIdleMillis) {
      this.maxIdleMillis = normalized
      evictExpiredUnlocked(clockMillis())
    }
  }

  override def getCurrentEstimatedBytes: Long = synchronized { currentEstimatedBytes }
  override def getHitCount             : Long = synchronized { hitCount }
  override def getMissCount            : Long = synchronized { missCount }
  override def getAddCount             : Long = synchronized { addCount }
  override def getEvictionCount        : Long = synchronized { evictionCount }
  override def getRemoveCount          : Long = synchronized { removeCount }

  override def resetStats(): Unit = synchronized {
    hitCount      = 0L
    missCount     = 0L
    addCount      = 0L
    evictionCount = 0L
    removeCount   = 0L
  }

  def statsSnapshot(name: String): ObjectCacheStatsSnapshot = synchronized {
    ObjectCacheStatsSnapshot(
      name           = name,
      currentSize    = currentSize,
      maxSize        = maxSize,
      maxSizeBytes   = maxSizeBytes,
      maxIdleMillis  = maxIdleMillis,
      estimatedBytes = currentEstimatedBytes,
      hitCount       = hitCount,
      missCount      = missCount,
      addCount       = addCount,
      evictionCount  = evictionCount,
      removeCount    = removeCount
    )
  }

  def entriesSnapshot: List[ObjectCacheEntrySnapshot] = synchronized {
    evictExpiredUnlocked(clockMillis())

    val buf = ListBuffer.empty[ObjectCacheEntrySnapshot]
    val it  = linkedList.iterator()
    var recomputedTotal = 0L
    while (it.hasNext) {
      val e = it.next()
      // Re-evaluate mutable objects (like live XFormsContainingDocuments) so reported heap usage reflects latest state
      val freshBytes = MemorySizeEstimator.estimateBytes(e.cacheable)
      e.estimatedBytes = freshBytes
      recomputedTotal += freshBytes
      buf += ObjectCacheEntrySnapshot(
        key              = e.key,
        value            = e.cacheable,
        validity         = e.validity,
        createdAtMs      = e.createdAtMs,
        lastAccessedAtMs = e.lastAccessedAtMs,
        estimatedBytes   = freshBytes
      )
    }
    currentEstimatedBytes = recomputedTotal
    buf.toList
  }

  override def iterateCacheKeys(): ju.Iterator[CacheKey] = synchronized {
    val buf = new ju.ArrayList[CacheKey](currentSize)
    val it  = linkedList.iterator()
    while (it.hasNext)
      buf.add(it.next().key)
    buf.iterator()
  }

  override def iterateCacheObjects(): ju.Iterator[AnyRef] = synchronized {
    val buf = new ju.ArrayList[AnyRef](currentSize)
    val it  = linkedList.iterator()
    while (it.hasNext)
      buf.add(it.next().cacheable)
    buf.iterator()
  }

  private def lowerOrEqual(left: AnyRef, right: AnyRef): Boolean =
    (left, right) match {
      case (leftList: ju.List[?], rightList: ju.List[?]) =>
        if (leftList.size() != rightList.size()) {
          false
        } else {
          val leftIt  = leftList.iterator()
          val rightIt = rightList.iterator()
          var ok      = true
          while (ok && leftIt.hasNext) {
            if (! lowerOrEqual(leftIt.next().asInstanceOf[AnyRef], rightIt.next().asInstanceOf[AnyRef]))
              ok = false
          }
          ok
        }
      case (l: java.lang.Long, r: java.lang.Long) =>
        l.longValue() <= r.longValue()
      case _ =>
        false
    }
}
