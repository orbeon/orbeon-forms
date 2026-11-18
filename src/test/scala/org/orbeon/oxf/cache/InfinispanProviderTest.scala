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

import org.orbeon.oxf.properties.PropertySet
import org.orbeon.oxf.properties.PropertySet.PropertyParams
import org.orbeon.oxf.test.ResourceManagerSupport
import org.orbeon.oxf.xml.XMLConstants.XS_STRING_QNAME
import org.scalatest.funspec.AnyFunSpecLike

import java.nio.charset.StandardCharsets
import java.nio.file.Files


class InfinispanProviderTest
  extends ResourceManagerSupport
     with AnyFunSpecLike {

  private def propsFrom(pairs: (String, String)*): PropertySet =
    PropertySet.forTests(
      pairs.map { case (name, value) =>
        PropertyParams(Map.empty, name, XS_STRING_QNAME, value)
      }
    )

  describe("InfinispanProvider configuration loading and DefaultCacheManager deduplication (#7431)") {

    it("must share a single DefaultCacheManager when store and cache use the same configuration") {
      val customXml =
        """<?xml version="1.0" encoding="UTF-8"?>
          |<infinispan
          |    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          |    xsi:schemaLocation="urn:infinispan:config:16.2 https://infinispan.org/schemas/infinispan-config-16.2.xsd"
          |    xmlns="urn:infinispan:config:16.2">
          |  <cache-container name="shared-test-container" statistics="true">
          |    <local-cache name="shared.cache" statistics="true">
          |      <memory max-count="25" when-full="REMOVE"/>
          |    </local-cache>
          |  </cache-container>
          |</infinispan>
          |""".stripMargin

      val tempFile = Files.createTempFile("orbeon-infinispan-shared-", ".xml")
      try {
        Files.write(tempFile, customXml.getBytes(StandardCharsets.UTF_8))
        val props = propsFrom("oxf.xforms.cache.infinispan.uri" -> tempFile.toUri.toString)

        val storeProvider = new InfinispanProvider(store = true,  Some(props))
        val cacheProvider = new InfinispanProvider(store = false, Some(props))
        try {
          assert(storeProvider.underlyingCacheManagerForTests eq cacheProvider.underlyingCacheManagerForTests)
          assert(storeProvider.get("shared.cache").flatMap(_.getMaxEntriesLocalHeap).contains(25L))
          assert(cacheProvider.get("non.existent.cache").isEmpty)

          // Closing one provider must keep the shared DefaultCacheManager alive for the sibling provider
          storeProvider.close()
          val cache = cacheProvider.get("shared.cache").get
          cache.put("k1", "v1")
          assert(cache.get("k1").contains("v1"))
        } finally {
          storeProvider.close()
          cacheProvider.close()
        }
      } finally {
        Files.deleteIfExists(tempFile)
      }
    }

    it("must resolve `oxf:` URIs and Resource Manager paths via properties (#7431)") {
      val uriProps = propsFrom("oxf.xforms.cache.infinispan.uri" -> "oxf:/config/infinispan.xml")
      val p1       = new InfinispanProvider(store = true, Some(uriProps))
      try {
        assert(p1.findConfigured("xforms.store.dynamic-states").isDefined)
        assert(p1.findConfigured("xforms.store.static-states").isDefined)
        assert(p1.findConfigured("xforms.store.asset-bundles").isDefined)
        assert(p1.findConfigured("fr.store.external-modes").isDefined)
        assert(p1.findConfigured("fr.cache.form-metadata").isDefined)
        assert(p1.findConfigured("core.cache.properties").isDefined)
      } finally {
        p1.close()
      }

      val resProps = propsFrom("oxf.xforms.store.infinispan.resource" -> "/config/infinispan.xml")
      val p2       = new InfinispanProvider(store = true, Some(resProps))
      try {
        assert(p2.findConfigured("xforms.store.dynamic-states").flatMap(_.getMaxEntriesLocalHeap).contains(500L))
        assert(p2.findConfigured("xforms.store.static-states").flatMap(_.getMaxEntriesLocalHeap).contains(200L))
        assert(p2.findConfigured("fr.cache.form-metadata").flatMap(_.getMaxEntriesLocalHeap).contains(100L))
      } finally {
        p2.close()
      }
    }

    it("must map legacy provider cache names to canonical names and support legacy Infinispan XML configs") {
      assert(CacheSupport.toCanonicalCacheName("xforms.state") == "xforms.store.dynamic-states")
      assert(CacheSupport.toCanonicalCacheName("xforms.static-state") == "xforms.store.static-states")
      assert(CacheSupport.toCanonicalCacheName("xforms.resources") == "xforms.store.asset-bundles")
      assert(CacheSupport.toCanonicalCacheName("orbeon.properties") == "core.cache.properties")
      assert(CacheSupport.toCanonicalCacheName("form-runner.persistence.form-definition") == "fr.cache.form-metadata")
      assert(CacheSupport.toCanonicalCacheName("form-runner.external-mode-store") == "fr.store.external-modes")

      val legacyXml =
        """<?xml version="1.0" encoding="UTF-8"?>
          |<infinispan
          |    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          |    xsi:schemaLocation="urn:infinispan:config:16.2 https://infinispan.org/schemas/infinispan-config-16.2.xsd"
          |    xmlns="urn:infinispan:config:16.2">
          |  <cache-container name="legacy-test-container" statistics="true">
          |    <local-cache name="xforms.state" statistics="true">
          |      <memory max-count="77" when-full="REMOVE"/>
          |    </local-cache>
          |  </cache-container>
          |</infinispan>
          |""".stripMargin

      val tempFile = Files.createTempFile("orbeon-infinispan-legacy-", ".xml")
      try {
        Files.write(tempFile, legacyXml.getBytes(StandardCharsets.UTF_8))
        val props    = propsFrom("oxf.xforms.store.infinispan.uri" -> tempFile.toUri.toString)
        val provider = new InfinispanProvider(store = true, Some(props))
        try {
          assert(provider.findConfigured("xforms.store.dynamic-states").isEmpty)
          val legacyFallback =
            provider.findConfigured("xforms.store.dynamic-states")
              .orElse(CacheSupport.LegacyProviderCacheNames.get("xforms.store.dynamic-states").flatMap(provider.findConfigured))
          assert(legacyFallback.flatMap(_.getMaxEntriesLocalHeap).contains(77L))
        } finally {
          provider.close()
        }
      } finally {
        Files.deleteIfExists(tempFile)
      }
    }

    it("must isolate DefaultCacheManagers when store and cache configure distinct XML files") {
      def customXml(containerName: String, cacheName: String, maxCount: Int): String =
        s"""<?xml version="1.0" encoding="UTF-8"?>
           |<infinispan
           |    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
           |    xsi:schemaLocation="urn:infinispan:config:16.2 https://infinispan.org/schemas/infinispan-config-16.2.xsd"
           |    xmlns="urn:infinispan:config:16.2">
           |  <cache-container name="$containerName" statistics="true">
           |    <local-cache name="$cacheName" statistics="true">
           |      <memory max-count="$maxCount" when-full="REMOVE"/>
           |    </local-cache>
           |  </cache-container>
           |</infinispan>
           |""".stripMargin

      val storeFile = Files.createTempFile("orbeon-infinispan-store-", ".xml")
      val cacheFile = Files.createTempFile("orbeon-infinispan-cache-", ".xml")
      try {
        Files.write(storeFile, customXml("store-c", "only.in.store", 11).getBytes(StandardCharsets.UTF_8))
        Files.write(cacheFile, customXml("cache-c", "only.in.cache", 22).getBytes(StandardCharsets.UTF_8))

        val props = propsFrom(
          "oxf.xforms.store.infinispan.uri" -> storeFile.toUri.toString,
          "oxf.xforms.cache.infinispan.uri" -> cacheFile.toUri.toString
        )

        val storeProvider = new InfinispanProvider(store = true,  Some(props))
        val cacheProvider = new InfinispanProvider(store = false, Some(props))
        try {
          assert(storeProvider.underlyingCacheManagerForTests ne cacheProvider.underlyingCacheManagerForTests)
          assert(storeProvider.get("only.in.store").flatMap(_.getMaxEntriesLocalHeap).contains(11L))
          assert(storeProvider.get("only.in.cache").isEmpty)
          assert(cacheProvider.get("only.in.cache").flatMap(_.getMaxEntriesLocalHeap).contains(22L))
          assert(cacheProvider.get("only.in.store").isEmpty)
        } finally {
          storeProvider.close()
          cacheProvider.close()
        }
      } finally {
        Files.deleteIfExists(storeFile)
        Files.deleteIfExists(cacheFile)
      }
    }
  }
}
