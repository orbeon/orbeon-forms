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

import org.orbeon.dom
import org.orbeon.oxf.xml.SAXStore
import org.scalatest.funspec.AnyFunSpecLike
import org.xml.sax.helpers.AttributesImpl


class MemoryCacheStatsAndSizingTest extends AnyFunSpecLike {

  private case class TestKey(id: String) extends InternalCacheKey("stats-test", id)

  private class FixedSizeItem(val estimatedMemoryBytes: Long) extends SizedCacheable

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
