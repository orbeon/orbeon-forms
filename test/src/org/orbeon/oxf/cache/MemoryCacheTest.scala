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

import org.scalatest.junit.AssertionsForJUnit
import org.junit.Test
import java.util.concurrent.locks.{ReentrantLock, Lock}
import scala.actors.Futures._
import scala.collection.JavaConversions._

class MemoryCacheTest extends AssertionsForJUnit {

    class MyCacheable(val getEvictionLock: Lock) extends Cacheable {

        var wasEvicted = false
        var wasRemoved = false

        def evicted() { wasEvicted = true }
        def removed() { wasRemoved = true }
        def added() {}
    }

    case class Key(key: String) extends InternalCacheKey("test", key)
    val VALIDITY = 0L

    @Test def testFindKeepsInCache() {
        val cache = new MemoryCacheImpl("test", 1)

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

    @Test def testTakeRemovesFromCache() {
        val cache = new MemoryCacheImpl("test", 1)

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

    @Test def testRemoveNotifies() {
        val cache = new MemoryCacheImpl("test", 1)

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

    @Test def testRemoveAllNotifies() {
        val cache = new MemoryCacheImpl("test", 1)

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

    @Test def testReduceSizeEvicts() {
        val cache = new MemoryCacheImpl("test", 1)

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

    @Test def testReduceSizeWithLock() {
        val cache = new MemoryCacheImpl("test", 1)
        val lock = new ReentrantLock

        val o1 = new MyCacheable(lock)

        // Add object
        val key1 = Key("o1")
        cache.add(key1, VALIDITY, o1)

        // Reduce size in other thread
        lock.lock()
        try {
            // Run in separate thread and wait
            future { cache.setMaxSize(0) } apply()
        } finally {
            lock.unlock()
        }

        assert(!o1.wasEvicted)
        assert(!o1.wasRemoved)
        assert(cache.getCurrentSize === 1)
    }

    @Test def testEvictedIfLockAvailable() {
        val cache = new MemoryCacheImpl("test", 1)

        val o1 = new MyCacheable(new ReentrantLock)

        // Add first object
        cache.add(Key("o1"), VALIDITY, o1)
        // Push first object out with second object
        cache.add(Key("o2"), VALIDITY, new AnyRef)

        assert(o1.wasEvicted)
        assert(!o1.wasRemoved)
        assert(cache.getCurrentSize === 1)
    }

    @Test def testNotEvictedIfLockUnavailable() {
        val cache = new MemoryCacheImpl("test", 1)
        val lock = new ReentrantLock

        val o1 = new MyCacheable(lock)

        // Add first object
        cache.add(Key("o1"), VALIDITY, o1)

        lock.lock()
        try {
            // Run in separate thread and wait
            future { cache.add(Key("o2"), VALIDITY, new AnyRef) } apply()
        } finally {
            lock.unlock()
        }

        assert(!o1.wasEvicted)
        assert(!o1.wasRemoved)
        assert(cache.getCurrentSize === 2)
    }

    @Test def testNextToLastEvicted() {
        val cache = new MemoryCacheImpl("test", 2)
        val lock = new ReentrantLock


        // First object will be last and has a lock. It must not be evicted.
        val o1 = new MyCacheable(lock)

        // Second object will be next-to-last and doesn't have a lock. It must be evicted.
        val o2 = new MyCacheable(null)

        // Add objects
        cache.add(Key("o1"), VALIDITY, o1)
        cache.add(Key("o2"), VALIDITY, o2)

        lock.lock()
        try {
            // Run in separate thread and wait
            future { cache.add(Key("o3"), VALIDITY, new AnyRef) } apply()
        } finally {
            lock.unlock()
        }

        assert(!o1.wasEvicted)
        assert(!o1.wasRemoved)
        assert(o2.wasEvicted)
        assert(!o2.wasRemoved)
        assert(cache.getCurrentSize === 2)
    }

    @Test def testIterators() {
        val size = 100
        val cache = new MemoryCacheImpl("test", size)

        val range = 1 to size
        
        for (i <- range.reverse)
            cache.add(Key("o" + i), VALIDITY, i)

        val keysAsInts = cache.iterateCacheKeys map (_.asInstanceOf[Key].key.tail.toInt) toSeq
        val values = cache.iterateCacheObjects map (_.asInstanceOf[Int]) toSeq

        assert(range === keysAsInts)
        assert(range === values)
    }
}