/**
 * Copyright (C) 2026 Orbeon, Inc.
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

import java.util.concurrent.locks.{Lock, ReentrantLock}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.*
import scala.concurrent.{Await, Future}

import org.orbeon.dom
import org.orbeon.oxf.properties.PropertySet
import org.orbeon.oxf.properties.PropertySet.PropertyParams
import org.orbeon.oxf.xml.SAXStore
import org.orbeon.oxf.xml.XMLConstants.{XS_INTEGER_QNAME, XS_STRING_QNAME}
import org.scalatest.funspec.AnyFunSpecLike
import org.xml.sax.helpers.AttributesImpl


class MemoryCacheStatsAndSizingTest extends AnyFunSpecLike {

  private case class TestKey(id: String) extends InternalCacheKey("stats-test", id)

  private class FixedSizeItem(
    val estimatedMemoryBytes: Long,
    val getEvictionLock     : Lock = null
  ) extends SizedCacheable
       with Cacheable {

    var wasEvicted = false
    var wasRemoved = false

    def added()  : Unit = ()
    def evicted(): Unit = wasEvicted = true
    def removed(): Unit = wasRemoved = true
  }

  describe("MemoryCacheImpl statistics and estimated byte tracking") {

    it("must accurately track hits, misses, adds, evictions, removes, and estimated bytes") {
      val cache = new MemoryCacheImpl(2)

      assert(cache.getCurrentSize == 0)
      assert(cache.getCurrentEstimatedBytes == 0L)

      // Miss on empty cache
      assert(cache.findValid(TestKey("k1"), java.lang.Long.valueOf(0L)) == null)
      assert(cache.getMissCount == 1L)
      assert(cache.getHitCount == 0L)

      // Add two sized items
      cache.add(TestKey("k1"), java.lang.Long.valueOf(0L), new FixedSizeItem(1000L))
      cache.add(TestKey("k2"), java.lang.Long.valueOf(0L), new FixedSizeItem(2500L))

      assert(cache.getCurrentSize == 2)
      assert(cache.getAddCount == 2L)
      assert(cache.getCurrentEstimatedBytes == 3500L)

      // Hit k1 (moves k1 to MRU, so k2 becomes LRU)
      assert(cache.findValid(TestKey("k1"), java.lang.Long.valueOf(0L)) != null)
      assert(cache.getHitCount == 1L)

      // Adding k3 evicts LRU entry k2 (2500 bytes)
      cache.add(TestKey("k3"), java.lang.Long.valueOf(0L), new FixedSizeItem(4000L))
      assert(cache.getCurrentSize == 2)
      assert(cache.getEvictionCount == 1L)
      assert(cache.getCurrentEstimatedBytes == 5000L) // k1 (1000) + k3 (4000)

      // Updating k1 updates estimated bytes in place
      cache.add(TestKey("k1"), java.lang.Long.valueOf(1L), new FixedSizeItem(1500L))
      assert(cache.getCurrentSize == 2)
      assert(cache.getCurrentEstimatedBytes == 5500L)

      // Explicit remove of k3
      cache.remove(TestKey("k3"))
      assert(cache.getCurrentSize == 1)
      assert(cache.getRemoveCount == 1L)
      assert(cache.getCurrentEstimatedBytes == 1500L)

      // Clear remaining
      assert(cache.removeAll() == 1)
      assert(cache.getCurrentSize == 0)
      assert(cache.getCurrentEstimatedBytes == 0L)
    }
  }

  describe("MemoryCacheImpl byte-bounded eviction (#1973)") {

    it("must evict multiple LRU entries to stay within maxSizeBytes while allowing a single oversized entry") {
      val cache = new MemoryCacheImpl(maxSize = 50, maxSizeBytes = Some(5000L))

      val i1 = new FixedSizeItem(2000L)
      val i2 = new FixedSizeItem(2000L)
      val i3 = new FixedSizeItem(1000L)
      cache.add(TestKey("k1"), 0L, i1)
      cache.add(TestKey("k2"), 0L, i2)
      cache.add(TestKey("k3"), 0L, i3)

      assert(cache.getCurrentSize == 3)
      assert(cache.getCurrentEstimatedBytes == 5000L)

      // Adding k4 (3500B) requires evicting both k1 (2000B) and k2 (2000B), leaving k3 (1000B) + k4 (3500B) = 4500B
      val i4 = new FixedSizeItem(3500L)
      cache.add(TestKey("k4"), 0L, i4)

      assert(i1.wasEvicted && i2.wasEvicted && ! i3.wasEvicted && ! i4.wasEvicted)
      assert(cache.getCurrentSize == 2)
      assert(cache.getCurrentEstimatedBytes == 4500L)
      assert(cache.getEvictionCount == 2L)

      // In-place update growing k3 to 4800B must evict k4 (now LRU) while preserving k3 itself
      val i3Grown = new FixedSizeItem(4800L)
      cache.add(TestKey("k3"), 0L, i3Grown)
      assert(i4.wasEvicted && ! i3Grown.wasEvicted)
      assert(cache.getCurrentSize == 1)
      assert(cache.getCurrentEstimatedBytes == 4800L)

      // Single oversized entry (12000B > 5000B) evicts existing k3 and still caches cleanly as the sole entry
      val iGiant = new FixedSizeItem(12000L)
      cache.add(TestKey("giant"), 0L, iGiant)
      assert(i3Grown.wasEvicted && ! iGiant.wasEvicted)
      assert(cache.getCurrentSize == 1)
      assert(cache.getCurrentEstimatedBytes == 12000L)

      // Lowering maxSizeBytes dynamically evicts excess unlocked entries down to >= 1 entry
      cache.add(TestKey("small"), 0L, new FixedSizeItem(1000L))
      assert(iGiant.wasEvicted)
      assert(cache.getCurrentSize == 1)
      assert(cache.getCurrentEstimatedBytes == 1000L)

      cache.add(TestKey("small2"), 0L, new FixedSizeItem(1500L))
      assert(cache.getCurrentSize == 2)
      cache.setMaxSizeBytes(Some(1600L))
      assert(cache.getCurrentSize == 1)
      assert(cache.getCurrentEstimatedBytes == 1500L)
    }

    it("must skip locked entries under byte pressure and evict the next oldest unlocked entry") {
      val lock  = new ReentrantLock
      val cache = new MemoryCacheImpl(maxSize = 50, maxSizeBytes = Some(4000L))

      val lockedLru   = new FixedSizeItem(2000L, lock)
      val unlockedMru = new FixedSizeItem(2000L, null)

      cache.add(TestKey("locked"), 0L, lockedLru)
      cache.add(TestKey("unlocked"), 0L, unlockedMru)

      lock.lock()
      try {
        Await.result(Future(cache.add(TestKey("new"), 0L, new FixedSizeItem(2000L))), 5.seconds)
      } finally {
        lock.unlock()
      }

      assert(! lockedLru.wasEvicted)
      assert(unlockedMru.wasEvicted)
      assert(cache.getCurrentSize == 2)
      assert(cache.getCurrentEstimatedBytes == 4000L)
    }
  }

  describe("MemoryCacheImpl idle-time expiration (#1572)") {

    it("must expire idle entries on lookup and sweep expired LRU entries on add") {
      var nowMs = 1_000_000L
      var listenerEvictions = List.empty[AnyRef]

      val cache = new MemoryCacheImpl(
        maxSize       = 50,
        maxSizeBytes  = None,
        maxIdleMillis = Some(10_000L),
        clockMillis   = () => nowMs
      )
      cache.setEvictionListener(obj => listenerEvictions ::= obj)

      val i1 = new FixedSizeItem(1000L)
      val i2 = new FixedSizeItem(1000L)
      cache.add(TestKey("k1"), 0L, i1)
      nowMs += 4_000L
      cache.add(TestKey("k2"), 0L, i2)

      // Advance 7s (total 11s since k1, 7s since k2): touch k2 so k2 refreshes, while k1 is expired (> 10s)
      nowMs += 7_000L
      assert(cache.findValid(TestKey("k2"), 0L) eq i2)
      assert(cache.findValid(TestKey("k1"), 0L) == null)
      assert(i1.wasEvicted)
      assert(listenerEvictions.contains(i1))
      assert(cache.getCurrentSize == 1)
      assert(cache.getEvictionCount == 1L)

      // Advance past k2's idle window; adding k3 must sweep expired k2 automatically
      nowMs += 11_000L
      val i3 = new FixedSizeItem(500L)
      cache.add(TestKey("k3"), 0L, i3)
      assert(i2.wasEvicted)
      assert(listenerEvictions.contains(i2))
      assert(cache.getCurrentSize == 1)
      assert(cache.getCurrentEstimatedBytes == 500L)
    }
  }

  describe("ObjectCache byte and duration property parsers") {

    it("must parse byte percentages, human-readable byte units, and durations") {
      val heap = 2L * 1024L * 1024L * 1024L // 2 GiB

      assert(ObjectCache.parseMaxSizeBytes("", heap).isEmpty)
      assert(ObjectCache.parseMaxSizeBytes("0", heap).isEmpty)
      assert(ObjectCache.parseMaxSizeBytes("-1", heap).isEmpty)
      assert(ObjectCache.parseMaxSizeBytes("15%", heap).contains((heap * 0.15).toLong))
      assert(ObjectCache.parseMaxSizeBytes("512k", heap).contains(512L * 1024L))
      assert(ObjectCache.parseMaxSizeBytes("256m", heap).contains(256L * 1024L * 1024L))
      assert(ObjectCache.parseMaxSizeBytes("256MB", heap).contains(256L * 1024L * 1024L))
      assert(ObjectCache.parseMaxSizeBytes("1g", heap).contains(1024L * 1024L * 1024L))
      assert(ObjectCache.parseMaxSizeBytes("1048576", heap).contains(1048576L))

      assert(ObjectCache.parseDurationMillis("").isEmpty)
      assert(ObjectCache.parseDurationMillis("0").isEmpty)
      assert(ObjectCache.parseDurationMillis("-1").isEmpty)
      assert(ObjectCache.parseDurationMillis("500ms").contains(500L))
      assert(ObjectCache.parseDurationMillis("30s").contains(30_000L))
      assert(ObjectCache.parseDurationMillis("15m").contains(15L * 60_000L))
      assert(ObjectCache.parseDurationMillis("2h").contains(2L * 3_600_000L))
      assert(ObjectCache.parseDurationMillis("1d").contains(86_400_000L))
      assert(ObjectCache.parseDurationMillis("PT30M").contains(30L * 60_000L))
      assert(ObjectCache.parseDurationMillis("120000").contains(120_000L))
    }

    it("must honor legacy ObjectCache properties over untouched built-in defaults while preferring explicit canonical overrides") {
      assert(ObjectCache.canonicalCacheName("cache.main") == "core.cache.pipelines")
      assert(ObjectCache.canonicalCacheName("cache.xpath") == "core.cache.xpath")
      assert(ObjectCache.canonicalCacheName("xforms.cache.documents") == "xforms.cache.live-documents")
      assert(ObjectCache.canonicalCacheName("xforms.cache.static-state") == "xforms.cache.static-states")

      val cache = new MemoryCacheImpl(50)

      // 1. Legacy `oxf.xforms.cache.documents.size = 2` wins when `oxf.xforms.cache.live-documents.size` is at its built-in default `50`
      val legacyOverrideProps = PropertySet.forTests(Seq(
        PropertyParams(Map.empty, "oxf.xforms.cache.live-documents.size",          XS_INTEGER_QNAME, "50"),
        PropertyParams(Map.empty, "oxf.xforms.cache.live-documents.max-size-bytes", XS_STRING_QNAME,  "15%"),
        PropertyParams(Map.empty, "oxf.xforms.cache.documents.size",               XS_INTEGER_QNAME, "2"),
        PropertyParams(Map.empty, "oxf.xforms.cache.documents.max-size-bytes",     XS_STRING_QNAME,  "0")
      ))
      ObjectCache.configureCacheFromPropertySet("xforms.cache.live-documents", 50, cache, legacyOverrideProps)
      assert(cache.getMaxSize == 2)
      assert(cache.getMaxSizeBytes.isEmpty)

      // 2. Explicit canonical override (`oxf.xforms.cache.live-documents.size = 25`) wins when both legacy and canonical are set
      val bothProps = PropertySet.forTests(Seq(
        PropertyParams(Map.empty, "oxf.xforms.cache.live-documents.size",          XS_INTEGER_QNAME, "25"),
        PropertyParams(Map.empty, "oxf.xforms.cache.live-documents.max-size-bytes", XS_STRING_QNAME,  "128m"),
        PropertyParams(Map.empty, "oxf.xforms.cache.documents.size",               XS_INTEGER_QNAME, "2"),
        PropertyParams(Map.empty, "oxf.xforms.cache.documents.max-size-bytes",     XS_STRING_QNAME,  "0")
      ))
      ObjectCache.configureCacheFromPropertySet("xforms.cache.live-documents", 50, cache, bothProps)
      assert(cache.getMaxSize == 25)
      assert(cache.getMaxSizeBytes.contains(128L * 1024L * 1024L))
    }
  }

  describe("MemorySizeEstimator") {

    it("must estimate SAXStore and Orbeon DOM footprints") {
      val saxStore = new SAXStore
      saxStore.startDocument()
      val atts = new AttributesImpl
      atts.addAttribute("", "id", "id", "CDATA", "control-1")
      saxStore.startElement("http://www.w3.org/1999/xhtml", "div", "xh:div", atts)
      val text = "Hello Orbeon Cache Metrics".toCharArray
      saxStore.characters(text, 0, text.length)
      saxStore.endElement("http://www.w3.org/1999/xhtml", "div", "xh:div")
      saxStore.endDocument()

      val saxBytes = MemorySizeEstimator.estimateBytes(saxStore)
      assert(saxBytes > 200L)

      val doc  = dom.Document()
      val root = doc.addElement("form")
      for (i <- 1 to 20)
        root.addElement("field").addAttribute("name", s"field-$i").addText("sample value")

      val domBytes = MemorySizeEstimator.estimateBytes(doc)
      assert(domBytes > 2000L)
    }
  }
}
