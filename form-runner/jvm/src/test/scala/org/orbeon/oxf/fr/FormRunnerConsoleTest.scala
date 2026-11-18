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
package org.orbeon.oxf.fr

import org.orbeon.oxf.http.HttpMethod.GET
import org.orbeon.oxf.test.TestHttpClient.StaticState
import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport, TestHttpClient}
import org.orbeon.oxf.xforms.state.XFormsStaticStateCache
import org.orbeon.oxf.xml.dom.Extensions.*
import org.scalatest.funspec.AnyFunSpecLike


class FormRunnerConsoleTest
  extends DocumentTestBase
     with ResourceManagerSupport
     with AnyFunSpecLike
     with FormRunnerSupport {

  describe("Form Runner /fr/console access control and cache diagnostics") {

    it("must deny access (403) to /fr/console when no admin roles are configured") {
      val (_, consoleResponse, _, _) =
        TestHttpClient.connect(
          url     = "/fr/console",
          method  = GET,
          headers = Map.empty,
          content = None
        )

      assert(consoleResponse.statusCode == 403)
    }

    it("must produce a complete JVM, ObjectCache, Infinispan, static-state, and live-document snapshot") {
      // Load a Form Runner form so static state, document cache, and Infinispan state store are populated
      val (_, docOpt, _) =
        runFormRunner(
          app  = "tests",
          form = "wizard",
          mode = "new"
        )

      assert(docOpt.isDefined)

      val snapshotDoc = FormRunnerConsole.buildConsoleSnapshotDom()
      val root        = snapshotDoc.getRootElement

      // 1. JVM section
      val jvmElem = root.elementOpt("jvm")
      assert(jvmElem.isDefined)
      assert(jvmElem.get.attributeValue("heap-max-bytes").toLong > 0L)
      assert(jvmElem.get.attributeValue("estimated-caches-heap-bytes").toLong > 0L)
      assert(jvmElem.get.element("memory-pools").elements("pool").nonEmpty)

      // 2. Unified caches section
      val cacheElems = root.element("caches").elements("cache").toList
      val cacheNames = cacheElems.map(_.attributeValue("name")).toSet

      assert(cacheNames.contains("cache.main"))
      assert(cacheNames.contains("xforms.cache.static-state"))
      assert(cacheNames.contains("xforms.cache.documents"))
      assert(cacheNames.contains("xforms.cache.shared-instances"))
      assert(cacheNames.contains("cache.xpath"))
      assert(cacheNames.contains("xforms.state"))
      assert(cacheNames.contains("xforms.static-state"))
      assert(cacheNames.contains("xbl.global-binding-index"))

      val stateStoreElem = cacheElems.find(_.attributeValue("name") == "xforms.state").get
      // Statistics must be enabled on Infinispan `xforms.state`
      assert(stateStoreElem.attributeValue("hits") != "—")
      assert(stateStoreElem.attributeValue("can-clear") == "false")

      val staticStateStoreElem = cacheElems.find(_.attributeValue("name") == "xforms.static-state").get
      assert(staticStateStoreElem.attributeValue("can-clear") == "false")

      // Default 15% max-heap byte budgets must be active on both `xforms.cache.documents` and `xforms.cache.static-state` (#1973, #1572)
      val docsObjectCacheElem   = cacheElems.find(_.attributeValue("name") == "xforms.cache.documents").get
      val staticObjectCacheElem = cacheElems.find(_.attributeValue("name") == "xforms.cache.static-state").get
      assert(docsObjectCacheElem.attributeValue("max-bytes").toLong > 0L)
      assert(staticObjectCacheElem.attributeValue("max-bytes").toLong > 0L)
      assert(docsObjectCacheElem.attributeValue("limit-summary").contains("max"))
      assert(staticObjectCacheElem.attributeValue("limit-summary").contains("max"))

      // 3. Static state drill-down
      val staticStatesElem = root.element("static-states")
      assert(staticStatesElem.attributeValue("count").toInt >= 1)
      assert(staticStatesElem.attributeValue("total-bytes").toLong > 0L)
      val wizardStaticOpt =
        staticStatesElem
          .elements("static-state")
          .find(e => e.attributeValue("app") == "tests" && e.attributeValue("form") == "wizard")
      assert(wizardStaticOpt.isDefined)
      assert(wizardStaticOpt.get.attributeValue("controls").toInt > 0)
      assert(wizardStaticOpt.get.attributeValue("estimated-bytes").toLong > 0L)

      // 4. Live documents drill-down
      val liveDocsElem = root.element("live-documents")
      assert(liveDocsElem.attributeValue("count").toInt >= 1)
      assert(liveDocsElem.attributeValue("total-bytes").toLong > 0L)
      val wizardDocOpt =
        liveDocsElem
          .element("documents")
          .elements("document")
          .find(_.attributeValue("request-path").contains("/fr/tests/wizard/new"))
      assert(wizardDocOpt.isDefined)

      // 5. Safe cache clearing policy & stats reset
      assert(! FormRunnerConsole.clearCacheInternal("object-cache", "xforms.cache.documents"))
      assert(! FormRunnerConsole.clearCacheInternal("provider-cache", "xforms.state"))
      assert(FormRunnerConsole.resetCacheStatsInternal("xforms.cache.static-state"))
      assert(FormRunnerConsole.clearCacheInternal("object-cache", "xforms.cache.static-state"))

      val snapshotAfterClear = FormRunnerConsole.buildConsoleSnapshotDom()
      assert(snapshotAfterClear.getRootElement.element("static-states").attributeValue("count").toInt == 0)

      // 6. Store-backed static-state recovery (#1572): reloading `/fr/tests/wizard/new` after `xforms.cache.static-state`
      // was cleared/evicted must restore directly from `xforms.static-state` without re-reading `InputAnnotatedDocument`.
      val (_, docAfterClearOpt, eventsAfterClear) =
        runFormRunner(
          app  = "tests",
          form = "wizard",
          mode = "new"
        )
      assert(docAfterClearOpt.isDefined)
      val staticStateTraceOpt = eventsAfterClear.collectFirst { case s: StaticState => s }
      assert(staticStateTraceOpt.exists(s => ! s.read && s.found))
      assert(XFormsStaticStateCache.findDocument(staticStateTraceOpt.get.digest).isDefined)
    }
  }
}
