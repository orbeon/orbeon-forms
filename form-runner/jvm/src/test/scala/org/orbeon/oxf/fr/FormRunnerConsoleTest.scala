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

import io.circe.generic.auto.*
import io.circe.parser.decode
import org.orbeon.oxf.http.HttpMethod.GET
import org.orbeon.oxf.test.TestHttpClient.StaticState
import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport, TestHttpClient}
import org.orbeon.oxf.xforms.action.XFormsAPI
import org.orbeon.oxf.xforms.control.controls.{XFormsRepeatControl, XFormsTriggerControl}
import org.orbeon.oxf.xforms.state.{RequestParameters, XFormsDocumentCache, XFormsStateManager, XFormsStaticStateCache}
import org.orbeon.oxf.xml.dom.Converter.*
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
      val poolElems = jvmElem.get.element("memory-pools").elements("pool").toList
      assert(poolElems.nonEmpty)
      assert(poolElems.forall(p => p.attributeValue("committed-bytes").toLongOption.isDefined))
      assert(poolElems.forall(p => p.attributeValue("max-bytes").toLongOption.isDefined))

      // 2. Unified caches section (all 12 standardized `<domain>.<cache|store>.<subject>` names)
      val cacheElems = root.element("caches").elements("cache").toList
      val cacheNames = cacheElems.map(_.attributeValue("name")).toSet

      assert(cacheNames.contains("core.cache.pipelines"))
      assert(cacheNames.contains("core.cache.properties"))
      assert(cacheNames.contains("core.cache.xpath"))
      assert(cacheNames.contains("fr.cache.form-metadata"))
      assert(cacheNames.contains("fr.store.external-modes"))
      assert(cacheNames.contains("xforms.cache.live-documents"))
      assert(cacheNames.contains("xforms.cache.shared-instances"))
      assert(cacheNames.contains("xforms.cache.static-states"))
      assert(cacheNames.contains("xforms.cache.xbl-bindings"))
      assert(cacheNames.contains("xforms.store.asset-bundles"))
      assert(cacheNames.contains("xforms.store.dynamic-states"))
      assert(cacheNames.contains("xforms.store.static-states"))
      assert(cacheElems.forall(_.attributeValue("canonical-key").nonEmpty))
      // Every `<cache>` element must carry raw numeric sort attributes so `/fr/console` never sorts formatted strings
      assert(cacheElems.forall(e => e.attributeValue("total-entries-raw").toLongOption.isDefined))
      assert(cacheElems.forall(e => e.attributeValue("max-entries-raw").toLongOption.isDefined))
      assert(cacheElems.forall(e => e.attributeValue("disk-bytes-raw").toLongOption.isDefined))
      assert(cacheElems.forall(e => e.attributeValue("hits-raw").toLongOption.isDefined))
      assert(cacheElems.forall(e => e.attributeValue("hit-rate-raw").toDoubleOption.isDefined))
      assert(cacheElems.forall(e => e.attributeValue("evictions-raw").toLongOption.isDefined))

      val stateStoreElem = cacheElems.find(_.attributeValue("name") == "xforms.store.dynamic-states").get
      // Statistics must be enabled on Infinispan `xforms.store.dynamic-states`
      assert(stateStoreElem.attributeValue("hits") != "—")
      assert(stateStoreElem.attributeValue("can-clear") == "false")

      val staticStateStoreElem = cacheElems.find(_.attributeValue("name") == "xforms.store.static-states").get
      assert(staticStateStoreElem.attributeValue("can-clear") == "false")

      // Default 15% max-heap byte budgets must be active on both `xforms.cache.live-documents` and `xforms.cache.static-states` (#1973, #1572)
      val docsObjectCacheElem   = cacheElems.find(_.attributeValue("name") == "xforms.cache.live-documents").get
      val staticObjectCacheElem = cacheElems.find(_.attributeValue("name") == "xforms.cache.static-states").get
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
      assert(wizardStaticOpt.get.attributeValue("template-bytes").toLong > 0L)
      assert(wizardStaticOpt.get.attributeValue("static-dom-bytes").toLong > 0L)
      assert(wizardStaticOpt.get.attributeValue("encoded-bytes").toLong > 0L)
      assert(wizardStaticOpt.get.attributeValue("estimated-bytes").toLong > 0L)
      assert(wizardStaticOpt.get.attributeValue("idle-ms").toLong >= 0L)

      // 4. Live documents drill-down (cached state + cache limit & state-store context)
      val liveDocsElem = root.element("live-documents")
      assert(liveDocsElem.attributeValue("count").toInt >= 1)
      assert(liveDocsElem.attributeValue("cached-count").toInt >= 1)
      assert(liveDocsElem.attributeValue("in-flight-count") == "0")
      assert(liveDocsElem.attributeValue("cache-limit-summary").contains("entries"))
      assert(liveDocsElem.attributeValue("state-store-entries").toInt >= 1)
      assert(liveDocsElem.attributeValue("total-bytes").toLong > 0L)
      val wizardDocOpt =
        liveDocsElem
          .element("documents")
          .elements("document")
          .find(_.attributeValue("request-path").contains("/fr/tests/wizard/new"))
      assert(wizardDocOpt.isDefined)
      assert(wizardDocOpt.get.attributeValue("status") == "cached")
      assert(wizardDocOpt.get.attributeValue("idle-ms").toLong >= 0L)

      // 4b. Checked-out (in-flight Ajax update) and initializing documents must remain visible as `in-request`
      val wizardDoc = docOpt.get
      val checkedOutOpt =
        XFormsStateManager.beforeUpdate(
          RequestParameters(wizardDoc.uuid, None, None, None, None),
          disableDocumentCache = false
        )
      assert(checkedOutOpt.contains(wizardDoc))
      try {
        val inFlightSnap = FormRunnerConsole.buildConsoleSnapshotDom().getRootElement.element("live-documents")
        assert(inFlightSnap.attributeValue("in-flight-count") == "1")
        val inFlightWizardOpt =
          inFlightSnap
            .element("documents")
            .elements("document")
            .find(_.attributeValue("uuid") == wizardDoc.uuid)
        assert(inFlightWizardOpt.isDefined)
        assert(inFlightWizardOpt.get.attributeValue("status") == "in-request")
        assert(inFlightWizardOpt.get.attributeValue("idle-ms") == "-1")
        assert(inFlightWizardOpt.get.attributeValue("idle-formatted") == "—")
      } finally {
        XFormsStateManager.unregisterInFlightDocument(wizardDoc.uuid)
      }

      // Also verify current-thread `XFormsAPI.inScopeContainingDocumentOpt` visibility (initial GET before `afterInitialResponse`)
      try {
        XFormsAPI.withContainingDocument(wizardDoc) {
          val initSnap = FormRunnerConsole.buildConsoleSnapshotDom().getRootElement.element("live-documents")
          assert(initSnap.attributeValue("in-flight-count") == "1")
          assert(
            initSnap
              .element("documents")
              .elements("document")
              .exists(e => e.attributeValue("uuid") == wizardDoc.uuid && e.attributeValue("status") == "in-request")
          )
        }
      } finally {
        XFormsDocumentCache.put(wizardDoc)
      }

      // 5. Safe cache clearing policy & stats reset (both canonical and legacy aliases)
      assert(! FormRunnerConsole.clearCacheInternal("object-cache", "xforms.cache.live-documents"))
      assert(! FormRunnerConsole.clearCacheInternal("object-cache", "xforms.cache.documents"))
      assert(! FormRunnerConsole.clearCacheInternal("provider-cache", "xforms.store.dynamic-states"))
      assert(! FormRunnerConsole.clearCacheInternal("provider-cache", "xforms.state"))
      assert(FormRunnerConsole.resetCacheStatsInternal("xforms.cache.static-states"))
      assert(FormRunnerConsole.clearCacheInternal("object-cache", "xforms.cache.static-states"))

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

    it("must sort console tables by column ascending and descending on raw numeric and text keys via fr:sortable-th") {
      val consoleTestDom =
        <xh:html
            xmlns:xh="http://www.w3.org/1999/xhtml"
            xmlns:xs="http://www.w3.org/2001/XMLSchema"
            xmlns:xf="http://www.w3.org/2002/xforms"
            xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
            xmlns:fr="http://orbeon.org/oxf/xml/form-runner">
          <xh:head>
            <xf:model
                id="fr-form-model"
                xxf:xpath-analysis="true"
                xxf:expose-xpath-types="true"
                xxf:function-library="org.orbeon.oxf.fr.library.FormRunnerFunctionLibrary">
              <xf:instance id="fr-parameters-instance">
                <parameters>
                  <app>orbeon</app>
                  <form>console</form>
                  <form-version>1</form-version>
                  <mode>console</mode>
                </parameters>
              </xf:instance>
              <xf:instance id="fr-console-instance" xxf:exclude-result-prefixes="#all">
                <console>
                  <caches count="3">
                    <!-- Deliberately craft formatted strings whose alphabetical order ('40.2 KB' > '30.1 MB' > '3.5 MB')
                         differs from raw byte order (41164 < 3670016 < 31562137) -->
                    <cache name="xforms.store.asset-bundles"  engine="Infinispan (Heap + Disk)" heap-entries="12"  total-entries-raw="12"  max-entries-raw="200"  estimated-heap-bytes="41164"    estimated-heap-formatted="40.2 KB" disk-bytes-raw="279"   hits-raw="0"    hit-rate-raw="-1"   evictions-raw="0"/>
                    <cache name="core.cache.pipelines"        engine="ObjectCache (JVM Heap)"   heap-entries="875" total-entries-raw="875" max-entries-raw="1000" estimated-heap-bytes="31562137" estimated-heap-formatted="30.1 MB" disk-bytes-raw="-1"    hits-raw="4548" hit-rate-raw="83.7" evictions-raw="0"/>
                    <cache name="xforms.cache.live-documents" engine="ObjectCache (JVM Heap)"   heap-entries="2"   total-entries-raw="2"   max-entries-raw="50"   estimated-heap-bytes="3670016"  estimated-heap-formatted="3.5 MB"  disk-bytes-raw="-1"    hits-raw="6"    hit-rate-raw="100"  evictions-raw="3"/>
                  </caches>
                </console>
              </xf:instance>
              <xf:instance id="fr-console-sort-instance" xxf:exclude-result-prefixes="#all">
                <sort>
                  <announcement/>
                  <table id="caches">
                    <column>name</column>
                    <direction>asc</direction>
                  </table>
                </sort>
              </xf:instance>
              <xf:instance id="labels-instance" xxf:exclude-result-prefixes="#all">
                <labels>
                  <cache-name>Cache / Store</cache-name>
                  <est-heap>Est. Heap</est-heap>
                  <limits>Limits &amp; Expiration</limits>
                  <sorted-announcement>Sorted by {{0}}, {{1}}</sorted-announcement>
                  <sort-ascending>ascending</sort-ascending>
                  <sort-descending>descending</sort-descending>
                </labels>
              </xf:instance>
              <xf:action event="fr-order-by">
                <xf:var name="table-id"   value="string(event('table'))"/>
                <xf:var name="column"     value="string(event('column'))"/>
                <xf:var name="col-label"  value="string(event('label'))"/>
                <xf:var name="table-sort" value="instance('fr-console-sort-instance')/table[@id = $table-id]"/>
                <xf:action if="exists($table-sort)">
                  <xf:var
                      name="next-dir"
                      value="
                          if ($table-sort/column = $column) then
                              (if ($table-sort/direction = 'asc') then 'desc' else 'asc')
                          else
                              'asc'"/>
                  <xf:setvalue ref="$table-sort/column"    value="$column"/>
                  <xf:setvalue ref="$table-sort/direction" value="$next-dir"/>
                  <xf:setvalue
                      ref="instance('fr-console-sort-instance')/announcement"
                      value="
                          xxf:format-message(
                              instance('labels-instance')/sorted-announcement,
                              (
                                  $col-label,
                                  if ($next-dir = 'asc') then
                                      string(instance('labels-instance')/sort-ascending)
                                  else
                                      string(instance('labels-instance')/sort-descending)
                              )
                          )"/>
                </xf:action>
              </xf:action>
            </xf:model>
          </xh:head>
          <xh:body>
            <xf:group ref="instance('fr-console-instance')">
              <xf:var name="c"                value="."/>
              <xf:var name="caches-sort"      value="instance('fr-console-sort-instance')/table[@id = 'caches']"/>
              <xf:var name="caches-col"       value="string($caches-sort/column)"/>
              <xf:var name="caches-dir"       value="if ($caches-sort/direction = 'desc') then 'descending' else 'ascending'"/>
              <xf:var name="caches-data-type" value="if ($caches-col = ('name', 'engine')) then 'text' else 'number'"/>
              <xh:table>
                <xh:thead>
                  <xh:tr>
                    <fr:sortable-th id="th-name"   label="instance('labels-instance')/cache-name" instance="$caches-sort" table="caches" column="name"/>
                    <fr:sortable-th id="th-limits" label="instance('labels-instance')/limits"     instance="$caches-sort" table="caches" column="limits"/>
                    <fr:sortable-th id="th-heap"   label="instance('labels-instance')/est-heap"   instance="$caches-sort" table="caches" column="estimated-heap"/>
                  </xh:tr>
                </xh:thead>
                <xh:tbody>
                  <xf:repeat
                      id="caches-repeat"
                      ref="
                          xxf:sort(
                              $c/caches/cache,
                              if      ($caches-col = 'limits')         then xs:double(@max-entries-raw)
                              else if ($caches-col = 'estimated-heap') then xs:double(@estimated-heap-bytes)
                              else                                          lower-case(string(@name)),
                              $caches-data-type,
                              $caches-dir
                          )">
                    <xh:tr>
                      <xh:td><xf:output id="row-name" value="@name"/></xh:td>
                    </xh:tr>
                  </xf:repeat>
                </xh:tbody>
              </xh:table>
            </xf:group>
          </xh:body>
        </xh:html>.toDocument

      withTestExternalContext { implicit ec =>
        withXFormsDocument(consoleTestDom) { doc =>
          withActionAndDoc(doc) {

            def currentRowNames: List[String] = {
              val repeat = doc.getControlByEffectiveId("caches-repeat").asInstanceOf[XFormsRepeatControl]
              (1 to repeat.getSize).map(idx => getControlValue(s"row-name⊙$idx")).toList
            }

            def currentAnnouncement: String =
              instance("fr-console-sort-instance").get.underlyingDocumentOpt.get.getRootElement.element("announcement").getText

            def findInnerSortTriggerId(thPrefix: String): String =
              doc.controls.getCurrentControlTree.effectiveIdsToControls
                .collectFirst {
                  case (id, _: XFormsTriggerControl) if id.startsWith(s"$thPrefix≡") => id
                }
                .getOrElse(throw new NoSuchElementException(s"Trigger not found under $thPrefix"))

            // Initial alphabetical order (`name` asc)
            assert(currentRowNames == List(
              "core.cache.pipelines",
              "xforms.cache.live-documents",
              "xforms.store.asset-bundles"
            ))

            // 1st click on Est. Heap: sorts ascending by raw bytes (40.2 KB < 3.5 MB < 30.1 MB)
            activateControlWithEvent(findInnerSortTriggerId("th-heap"))
            assert(currentRowNames == List(
              "xforms.store.asset-bundles",
              "xforms.cache.live-documents",
              "core.cache.pipelines"
            ))
            assert(currentAnnouncement == "Sorted by Est. Heap, ascending")

            // 2nd click on Est. Heap: toggles to descending (30.1 MB > 3.5 MB > 40.2 KB)
            activateControlWithEvent(findInnerSortTriggerId("th-heap"))
            assert(currentRowNames == List(
              "core.cache.pipelines",
              "xforms.cache.live-documents",
              "xforms.store.asset-bundles"
            ))
            assert(currentAnnouncement == "Sorted by Est. Heap, descending")

            // Switching to Limits & Expiration starts ascending again on numeric max entries (50 < 200 < 1000)
            activateControlWithEvent(findInnerSortTriggerId("th-limits"))
            assert(currentRowNames == List(
              "xforms.cache.live-documents",
              "xforms.store.asset-bundles",
              "core.cache.pipelines"
            ))
            assert(currentAnnouncement == "Sorted by Limits & Expiration, ascending")
          }
        }
      }
    }

    it("must bound ConsoleMetricsHistory ring buffers, filter 15m/1h/24h windows, and clamp counter resets to non-negative rates") {
      ConsoleMetricsHistory.clearForTests()
      try {
        val baseTs = 1_800_000_000_000L

        def mkSample(
          ts        : Long,
          hits      : Long,
          misses    : Long,
          evictions : Long,
          gcCount   : Long,
          gcTimeMs  : Long
        ): ConsoleMetricsHistory.MetricsSample =
          ConsoleMetricsHistory.MetricsSample(
            timestampMs              = ts,
            heapUsedBytes            = 256L * 1024L * 1024L,
            heapCommittedBytes       = 512L * 1024L * 1024L,
            heapMaxBytes             = 1024L * 1024L * 1024L,
            nonHeapUsedBytes         = 64L * 1024L * 1024L,
            estimatedCachesHeapBytes = 48L * 1024L * 1024L,
            diskStoreBytes           = 16L * 1024L,
            totalCacheHits           = hits,
            totalCacheMisses         = misses,
            totalCacheEvictions      = evictions,
            activeSessions           = 2,
            liveDocsInHeap           = 1,
            stateStoreEntries        = 3L,
            gcCollectionCount        = gcCount,
            gcCollectionTimeMs       = gcTimeMs
          )

        // Append 300 samples spaced 15s apart (75 minutes total > 240-slot 1h fine ring capacity)
        (0 until 300).foreach { idx =>
          val ts = baseTs + idx.toLong * 15000L
          // Simulate a cache-stats reset at idx == 298 where cumulative hits/misses drop
          val hits   = if (idx >= 298) 10L * (idx - 298) else 100L * idx
          val misses = if (idx >= 298) 2L * (idx - 298)  else 20L * idx
          ConsoleMetricsHistory.recordSampleForTests(
            mkSample(
              ts        = ts,
              hits      = hits,
              misses    = misses,
              evictions = if (idx >= 298) 0L else idx.toLong,
              gcCount   = idx.toLong / 10L,
              gcTimeMs  = idx.toLong * 5L
            )
          )
        }

        val latestTs  = baseTs + 299L * 15000L
        val points1h  = ConsoleMetricsHistory.snapshotWindow("1h",  latestTs)
        val points15m = ConsoleMetricsHistory.snapshotWindow("15m", latestTs)
        val points24h = ConsoleMetricsHistory.snapshotWindow("24h", latestTs)

        // Fine ring must never exceed `FineMaxSamples` (240)
        assert(points1h.size <= ConsoleMetricsHistory.FineMaxSamples)
        assert(points1h.size >= 235)
        // 15m at 15s intervals yields ~61 samples (inclusive of boundary)
        assert(points15m.size >= 60 && points15m.size <= 62)
        // 75m of 5m coarse buckets + latest fine tail yields ~16 points
        assert(points24h.size >= 15 && points24h.size <= 17)
        assert(points24h.last.sample.timestampMs == latestTs)

        // Normal intervals before reset have 100 hits / 15s ~= 6.666 hits/s; reset step at idx 298 clamps to 0.0
        assert(points1h.forall(p => p.hitsPerSec >= 0.0 && p.missesPerSec >= 0.0 && p.evictionsDelta >= 0L && p.gcPauseMsDelta >= 0L))
        val resetPointOpt = points1h.find(_.sample.timestampMs == baseTs + 298L * 15000L)
        assert(resetPointOpt.exists(p => p.hitsPerSec == 0.0 && p.missesPerSec == 0.0 && p.evictionsDelta == 0L))
        val postResetPointOpt = points1h.find(_.sample.timestampMs == latestTs)
        assert(postResetPointOpt.exists(p => math.abs(p.hitsPerSec - (10.0 / 15.0)) < 0.01))
      } finally {
        ConsoleMetricsHistory.clearForTests()
      }
    }

    it("must emit valid Circe ConsoleChartSpec payloads for all 8 console charts across 15m, 1h, and 24h windows") {
      val (_, docOpt, _) =
        runFormRunner(
          app  = "tests",
          form = "wizard",
          mode = "new"
        )
      assert(docOpt.isDefined)

      val expectedChartIds = Set(
        "caches-memory-trend",
        "caches-activity-trend",
        "caches-heap-breakdown",
        "static-states-anatomy",
        "live-docs-trend",
        "live-docs-by-path",
        "jvm-gc-trend",
        "jvm-memory-pools"
      )

      val expectedSeriesKeys = Set(
        "heap-used", "cache-heap", "disk-store", "heap-committed",
        "hits-rate", "misses-rate", "evictions",
        "static-dom", "template", "analysis-other",
        "live-docs", "state-store", "sessions", "live-doc-heap",
        "gc-pause-ms", "gc-collections",
        "pool-used", "pool-headroom"
      )

      List("15m", "1h", "24h").foreach { win =>
        val snapRoot   = FormRunnerConsole.buildConsoleSnapshotDom(win).getRootElement
        val chartsElem = snapRoot.element("charts")
        assert(chartsElem.attributeValue("window") == win)
        assert(chartsElem.attributeValue("sample-count").toInt >= 1)

        val chartElems = chartsElem.elements("chart").toList
        assert(chartElems.map(_.attributeValue("id")).toSet == expectedChartIds)

        chartElems.foreach { elem =>
          val id   = elem.attributeValue("id")
          val spec = decode[ConsoleChartSpec](elem.getText).fold(err => fail(s"Failed to decode chart $id: $err"), identity)

          assert(spec.series.nonEmpty, s"Chart $id must declare series")
          assert(spec.series.map(_.key).forall(expectedSeriesKeys.contains), s"Unexpected series key in $id: ${spec.series.map(_.key)}")

          if (spec.kind == "timeseries") {
            assert(spec.window == win)
            assert(spec.timestampsMs.nonEmpty)
            assert(spec.series.forall(_.values.size == spec.timestampsMs.size))
          } else {
            assert(spec.kind == "hbar")
            assert(spec.categories.nonEmpty, s"Expected populated categories on $id")
            assert(spec.fullCategories.size == spec.categories.size)
            assert(spec.series.forall(_.values.size == spec.categories.size))
          }
        }
      }
    }
  }
}
