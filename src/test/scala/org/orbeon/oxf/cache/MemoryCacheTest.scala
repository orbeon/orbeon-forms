/**
 * Copyright (C) 2011 Orbeon, Inc.
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

import org.junit.Test
import java.util.concurrent.locks.{ReentrantLock, Lock}
import collection.JavaConverters._
import concurrent.{Await, Future}
import concurrent.duration._
import concurrent.ExecutionContext.Implicits.global
import org.scalatestplus.junit.AssertionsForJUnit

class MemoryCacheTest extends AssertionsForJUnit {

  class MyCacheable(val getEvictionLock: Lock) extends Cacheable {

    var wasEvicted = false
    var wasRemoved = false

    def evicted(): Unit = { wasEvicted = true }
    def removed(): Unit = { wasRemoved = true }
    def added(): Unit = {}
  }

  case class Key(key: String) extends InternalCacheKey("test", key)
  val VALIDITY = 0L

  @Test def testFindKeepsInCache(): Unit = {
    val cache = new MemoryCacheImpl(1)

    val o1 = new MyCacheable(null)

    // Add object
    val key1 = Key("o1")
    cache.add(key1, VALIDITY, o1)

    // Find object
    val result1 = cache.findValid(key1, VALIDITY)

    assert(result1 eq o1)
    assert(!o1.wasEvicted)
    assert(!o1.wasRemoved)
    assert(cache.getCurrentSize === 1)
  }

  @Test def testTakeRemovesFromCache(): Unit = {
    val cache = new MemoryCacheImpl(1)

    val o1 = new MyCacheable(null)

    // Add object
    val key1 = Key("o1")
    cache.add(key1, VALIDITY, o1)

    // Find object
    val result1 = cache.takeValid(key1, VALIDITY)

    assert(result1 eq o1)
    assert(!o1.wasEvicted)
    assert(o1.wasRemoved)
    assert(cache.getCurrentSize === 0)
  }

  @Test def testRemoveNotifies(): Unit = {
    val cache = new MemoryCacheImpl(1)

    val o1 = new MyCacheable(null)

    // Add object
    val key1 = Key("o1")
    cache.add(key1, VALIDITY, o1)

    // Remove object
    cache.remove(key1)

    assert(!o1.wasEvicted)
    assert(o1.wasRemoved)
    assert(cache.getCurrentSize === 0)
  }

  @Test def testRemoveAllNotifies(): Unit = {
    val cache = new MemoryCacheImpl(1)

    val o1 = new MyCacheable(null)

    // Add object
    val key1 = Key("o1")
    cache.add(key1, VALIDITY, o1)

    // Remove all
    cache.removeAll()

    assert(!o1.wasEvicted)
    assert(o1.wasRemoved)
    assert(cache.getCurrentSize === 0)
  }

  @Test def testReduceSizeEvicts(): Unit = {
    val cache = new MemoryCacheImpl(1)

    val o1 = new MyCacheable(null)

    // Add object
    val key1 = Key("o1")
    cache.add(key1, VALIDITY, o1)

    // Remove all
    cache.setMaxSize(0)

    assert(o1.wasEvicted)
    assert(!o1.wasRemoved)
    assert(cache.getCurrentSize === 0)
  }

  @Test def testReduceSizeWithLock(): Unit = {
    val cache = new MemoryCacheImpl(1)
    val lock = new ReentrantLock

    val o1 = new MyCacheable(lock)

    // Add object
    val key1 = Key("o1")
    cache.add(key1, VALIDITY, o1)

    // Reduce size in other thread
    lock.lock()
    Await.ready(Future(cache.setMaxSize(0)), Duration.Inf)
    lock.unlock()

    assert(!o1.wasEvicted)
    assert(!o1.wasRemoved)
    assert(cache.getCurrentSize === 1)
  }

  @Test def testEvictedIfLockAvailable(): Unit = {
    val cache = new MemoryCacheImpl(1)

    val o1 = new MyCacheable(new ReentrantLock)

    // Add first object
    cache.add(Key("o1"), VALIDITY, o1)
    // Push first object out with second object
    cache.add(Key("o2"), VALIDITY, new AnyRef)

    assert(o1.wasEvicted)
    assert(!o1.wasRemoved)
    assert(cache.getCurrentSize === 1)
  }

  @Test def testNotEvictedIfLockUnavailable(): Unit = {
    val cache = new MemoryCacheImpl(1)
    val lock = new ReentrantLock

    val o1 = new MyCacheable(lock)

    // Add first object
    cache.add(Key("o1"), VALIDITY, o1)

    // Run in separate thread and wait
    lock.lock()
    Await.ready(Future(cache.add(Key("o2"), VALIDITY, new AnyRef)), Duration.Inf)
    lock.unlock()

    assert(!o1.wasEvicted)
    assert(!o1.wasRemoved)
    assert(cache.getCurrentSize === 2)
  }

  @Test def testNextToLastEvicted(): Unit = {
    val cache = new MemoryCacheImpl(2)
    val lock = new ReentrantLock


    // First object will be last and has a lock. It must not be evicted.
    val o1 = new MyCacheable(lock)

    // Second object will be next-to-last and doesn't have a lock. It must be evicted.
    val o2 = new MyCacheable(null)

    // Add objects
    cache.add(Key("o1"), VALIDITY, o1)
    cache.add(Key("o2"), VALIDITY, o2)

    // Run in separate thread and wait
    lock.lock()
    Await.ready(Future(cache.add(Key("o3"), VALIDITY, new AnyRef)), Duration.Inf)
    lock.unlock()

    assert(!o1.wasEvicted)
    assert(!o1.wasRemoved)
    assert(o2.wasEvicted)
    assert(!o2.wasRemoved)
    assert(cache.getCurrentSize === 2)
  }

  @Test def testIterators(): Unit = {
    val size = 100
    val cache = new MemoryCacheImpl(size)

    val range = 1 to size

    for (i <- range.reverse)
      cache.add(Key("o" + i), VALIDITY, i)

    val keysAsInts = cache.iterateCacheKeys.asScala map (_.asInstanceOf[Key].key.tail.toInt) toSeq
    val values = cache.iterateCacheObjects.asScala map (_.asInstanceOf[Int]) toSeq

    assert(range === keysAsInts)
    assert(range === values)
  }
}