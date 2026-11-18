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

import org.orbeon.dom
import org.orbeon.dom.saxon.DocumentWrapper
import org.orbeon.oxf.cache.*
import org.orbeon.oxf.common.Version
import org.orbeon.oxf.util.StaticXPath.DocumentNodeInfoType
import org.orbeon.oxf.util.StringUtils.*
import org.orbeon.oxf.util.{DateUtils, StaticXPath, XPathCache}
import org.orbeon.oxf.xforms.action.XFormsAPI
import org.orbeon.oxf.xforms.state.{InFlightDocumentInfo, XFormsDocumentCache, XFormsStateManager, XFormsStaticStateCache}
import org.orbeon.oxf.xforms.xbl.{BindingIndex, GlobalBindingIndex}
import org.orbeon.oxf.xforms.{XFormsContainingDocument, XFormsProperties, XFormsServerSharedInstancesCache, XFormsStaticStateImpl, XFormsStores}

import java.lang.management.{ManagementFactory, MemoryType}
import java.time.Instant
import java.util.Locale
import scala.jdk.CollectionConverters.*


trait FormRunnerConsole {

  self: FormBuilderPermissionsOps =>

  import FormRunnerConsole.*

  //@XPathFunction
  def isConsoleAuthorized: Boolean = {
    val configurationOpt = formBuilderPermissionsConfiguration
    formBuilderPermissions(configurationOpt, FormRunner.orbeonRolesFromCurrentRequest).nonEmpty
  }

  //@XPathFunction
  def consoleSnapshotXml(): DocumentNodeInfoType = {
    if (! isConsoleAuthorized)
      throw new SecurityException("Unauthorized access to Form Runner Admin Console")
    new DocumentWrapper(buildConsoleSnapshotDom(), null, StaticXPath.GlobalConfiguration)
  }

  //@XPathFunction
  def clearConsoleCache(cacheKind: String, cacheName: String): Boolean = {
    if (! isConsoleAuthorized)
      throw new SecurityException("Unauthorized access to Form Runner Admin Console")
    clearCacheInternal(cacheKind, cacheName)
  }

  //@XPathFunction
  def resetConsoleCacheStats(cacheName: String): Boolean = {
    if (! isConsoleAuthorized)
      throw new SecurityException("Unauthorized access to Form Runner Admin Console")
    resetCacheStatsInternal(cacheName)
  }
}

object FormRunnerConsole {

  private val SafeClearableObjectCaches: Set[String] = Set(
    ObjectCache.DefaultCacheName,
    "xforms.cache.static-states",
    "xforms.cache.shared-instances",
    "core.cache.xpath"
  )

  private val SafeClearableProviderCaches: Set[String] = Set(
    "fr.cache.form-metadata",
    "core.cache.properties"
  )

  private val SafeJvmFlagPrefixes: List[String] = List(
    "-Xmx",
    "-Xms",
    "-Xmn",
    "-Xss",
    "-XX:+Use",
    "-XX:-Use",
    "-XX:MaxRAM",
    "-XX:InitialRAM",
    "-XX:MinRAM",
    "-XX:MaxMetaspaceSize",
    "-XX:MetaspaceSize",
    "-XX:ReservedCodeCacheSize",
    "-XX:SoftRefLRUPolicyMSPerMB",
    "-XX:MaxGCPauseMillis",
    "-XX:G1HeapRegionSize"
  )

  def clearCacheInternal(cacheKind: String, cacheName: String): Boolean =
    cacheKind match {
      case "object-cache" if SafeClearableObjectCaches(ObjectCache.canonicalCacheName(cacheName)) =>
        ObjectCache.findCache(cacheName).exists { c =>
          c.removeAll()
          true
        }
      case "provider-cache" if SafeClearableProviderCaches(CacheSupport.toCanonicalCacheName(cacheName)) =>
        CacheSupport.findCache(cacheName, store = false).exists { c =>
          c.clear()
          true
        }
      case _ =>
        false
    }

  def resetCacheStatsInternal(cacheName: String): Boolean =
    ObjectCache.findCache(cacheName) match {
      case Some(mc: MemoryCacheImpl) =>
        mc.resetStats()
        true
      case _ =>
        false
    }

  def buildConsoleSnapshotDom(): dom.Document = {
    // Ensure standard caches/stores are registered so they always appear in the console table,
    // and sync ObjectCache limits with the active PropertySet.
    val _ = (
      ObjectCache.instance,
      XFormsStaticStateCache.getCacheForDiagnostics,
      XFormsDocumentCache.getCacheForDiagnostics,
      XFormsServerSharedInstancesCache.getCacheForDiagnostics,
      XPathCache.getCacheForDiagnostics,
      XFormsStores.stateStore,
      XFormsStores.staticStateStore,
      XFormsStores.assetBundlesStore,
      CacheSupport.findCache("fr.store.external-modes", store = true),
      CacheSupport.findCache("fr.cache.form-metadata", store = false),
      CacheSupport.findCache("core.cache.properties", store = false)
    )
    ObjectCache.configureAllIfNeeded(org.orbeon.oxf.properties.Properties.instance.getPropertySet)

    val nowMs      = System.currentTimeMillis()
    val runtimeMx  = ManagementFactory.getRuntimeMXBean
    val memoryMx   = ManagementFactory.getMemoryMXBean
    val heapUsage  = memoryMx.getHeapMemoryUsage
    val nonHeap    = memoryMx.getNonHeapMemoryUsage

    val heapUsed      = math.max(0L, heapUsage.getUsed)
    val heapCommitted = math.max(0L, heapUsage.getCommitted)
    val heapMax       = if (heapUsage.getMax > 0) heapUsage.getMax else math.max(Runtime.getRuntime.maxMemory(), heapCommitted)
    val nonHeapUsed   = math.max(0L, nonHeap.getUsed)

    val doc  = dom.Document()
    val root = doc.addElement("console")

    root.addAttribute("timestamp-iso",        DateUtils.formatIsoDateTimeUtc(Instant.ofEpochMilli(nowMs)))
    root.addAttribute("uptime-ms",            runtimeMx.getUptime.toString)
    root.addAttribute("uptime-formatted",     formatDurationMs(runtimeMx.getUptime))
    root.addAttribute("orbeon-version",       Version.VersionNumber)
    root.addAttribute("orbeon-edition",       Version.Edition)
    root.addAttribute("java-version",         System.getProperty("java.version", ""))
    root.addAttribute("java-vendor",          System.getProperty("java.vendor", ""))
    root.addAttribute("available-processors", Runtime.getRuntime.availableProcessors().toString)

    // 1. Snapshot ObjectCaches (and pull entry snapshots for static-state and documents)
    val staticStateEntries =
      XFormsStaticStateCache.getCacheForDiagnostics match {
        case mc: MemoryCacheImpl => mc.entriesSnapshot
        case _                   => Nil
      }

    val documentEntries =
      XFormsDocumentCache.getCacheForDiagnostics match {
        case mc: MemoryCacheImpl => mc.entriesSnapshot
        case _                   => Nil
      }

    // Recompute entry bytes on remaining ObjectCaches so heap totals are fresh
    val objectCacheSnaps: List[ObjectCacheStatsSnapshot] =
      ObjectCache.allCaches.map {
        case (name, mc: MemoryCacheImpl) =>
          val _ = mc.entriesSnapshot
          mc.statsSnapshot(name)
        case (name, other) =>
          ObjectCacheStatsSnapshot(
            name           = name,
            currentSize    = other.getCurrentSize,
            maxSize        = other.getMaxSize,
            maxSizeBytes   = other.getMaxSizeBytes,
            maxIdleMillis  = other.getMaxIdleMillis,
            estimatedBytes = other.getCurrentEstimatedBytes,
            hitCount       = other.getHitCount,
            missCount      = other.getMissCount,
            addCount       = other.getAddCount,
            evictionCount  = other.getEvictionCount,
            removeCount    = other.getRemoveCount
          )
      }

    // 2. Snapshot Infinispan / Provider caches
    val providerCacheSnaps: List[CacheProviderStats] =
      CacheSupport.allRunningCacheSnapshots

    // 3. Snapshot GlobalBindingIndex
    val (globalXblCount, globalXblBytes) =
      GlobalBindingIndex.currentIndex match {
        case Some(index) =>
          val distinct = BindingIndex.distinctBindings(index)
          val bytes    = distinct.iterator.map { b =>
            val domBytes = MemorySizeEstimator.estimateOrbeonDomElement(b.bindingElement, maxElements = 5000)
            val constBytes = b.commonBinding.constantInstances.valuesIterator.map(MemorySizeEstimator.estimateBytes).sum
            512L + domBytes + constBytes
          }.sum
          (distinct.size, bytes)
        case None =>
          (0, 0L)
      }

    // 4. Merge cached XFormsContainingDocuments with checked-out / actively initializing documents
    val cachedDocs: List[LiveDocSnapshot] =
      documentEntries.collect {
        case entry if entry.value.isInstanceOf[XFormsContainingDocument] =>
          val xfcd = entry.value.asInstanceOf[XFormsContainingDocument]
          LiveDocSnapshot(
            uuid           = xfcd.uuid,
            requestPath    = Option(xfcd.getRequestPath).getOrElse(""),
            sequence       = xfcd.sequence,
            controlCount   = xfcd.liveControlCount,
            estimatedBytes = entry.estimatedBytes,
            idleMs         = math.max(0L, nowMs - entry.lastAccessedAtMs),
            idleFormatted  = formatDurationMs(math.max(0L, nowMs - entry.lastAccessedAtMs)),
            status         = "cached"
          )
      }
    val cachedUuids = cachedDocs.iterator.map(_.uuid).toSet

    val currentThreadDocInfoOpt: Option[InFlightDocumentInfo] =
      XFormsAPI.inScopeContainingDocumentOpt
        .filter(doc =>
          ! doc.disableUpdates &&
            doc.staticState.propertyMaybeAsExpression(XFormsProperties.NoUpdates).fold(_.toString != "true", _ => true)
        )
        .map { doc =>
          val controls =
            if (doc.initializing && doc.liveControlCount == 0)
              doc.staticState.topLevelPart.controlAnalysisMap.size
            else
              doc.liveControlCount
          InFlightDocumentInfo(
            uuid           = doc.uuid,
            requestPath    = Option(doc.getRequestPath).getOrElse(""),
            sequence       = doc.sequence,
            controlCount   = controls,
            estimatedBytes = doc.estimatedMemoryBytes
          )
        }

    val inFlightByUuid: Map[String, InFlightDocumentInfo] =
      (XFormsStateManager.inFlightDocumentsSnapshot.map(i => i.uuid -> i) ++
        currentThreadDocInfoOpt.map(i => i.uuid -> i)).toMap

    val inFlightDocs: List[LiveDocSnapshot] =
      inFlightByUuid.valuesIterator
        .filterNot(info => cachedUuids.contains(info.uuid))
        .map { info =>
          LiveDocSnapshot(
            uuid           = info.uuid,
            requestPath    = info.requestPath,
            sequence       = info.sequence,
            controlCount   = info.controlCount,
            estimatedBytes = info.estimatedBytes,
            idleMs         = -1L,
            idleFormatted  = "—",
            status         = "in-request"
          )
        }
        .toList

    val allLiveDocs: List[LiveDocSnapshot] = cachedDocs ++ inFlightDocs
    val inFlightTotalBytes: Long           = inFlightDocs.iterator.map(_.estimatedBytes).sum

    val totalEstimatedCacheHeapBytes =
      objectCacheSnaps.iterator.map(_.estimatedBytes).sum +
        inFlightTotalBytes +
        providerCacheSnaps.iterator.map(_.estimatedHeapBytes).sum +
        globalXblBytes

    val totalDiskStoreBytes =
      providerCacheSnaps.iterator.flatMap(_.diskStoreBytes).sum

    val totalDiskStoreFiles =
      providerCacheSnaps.iterator.flatMap(_.diskStoreFiles).sum

    // ==== <jvm> ====
    val jvmElem = root.addElement("jvm")
    val heapPercent = if (heapMax > 0) math.min(100.0, (heapUsed.toDouble / heapMax.toDouble) * 100.0) else 0.0
    val cacheHeapPercent = if (heapMax > 0) math.min(100.0, (totalEstimatedCacheHeapBytes.toDouble / heapMax.toDouble) * 100.0) else 0.0

    jvmElem.addAttribute("heap-used-bytes",               heapUsed.toString)
    jvmElem.addAttribute("heap-used-formatted",           formatBytes(heapUsed))
    jvmElem.addAttribute("heap-committed-bytes",          heapCommitted.toString)
    jvmElem.addAttribute("heap-committed-formatted",      formatBytes(heapCommitted))
    jvmElem.addAttribute("heap-max-bytes",                heapMax.toString)
    jvmElem.addAttribute("heap-max-formatted",            formatBytes(heapMax))
    jvmElem.addAttribute("heap-used-percent",             formatPercentValue(heapPercent))
    jvmElem.addAttribute("non-heap-used-bytes",           nonHeapUsed.toString)
    jvmElem.addAttribute("non-heap-used-formatted",       formatBytes(nonHeapUsed))
    jvmElem.addAttribute("estimated-caches-heap-bytes",     totalEstimatedCacheHeapBytes.toString)
    jvmElem.addAttribute("estimated-caches-heap-formatted", formatBytes(totalEstimatedCacheHeapBytes))
    jvmElem.addAttribute("estimated-caches-heap-percent",   formatPercentValue(cacheHeapPercent))
    jvmElem.addAttribute("total-disk-store-bytes",        totalDiskStoreBytes.toString)
    jvmElem.addAttribute("total-disk-store-formatted",    formatBytes(totalDiskStoreBytes))
    jvmElem.addAttribute("total-disk-store-files",        totalDiskStoreFiles.toString)
    jvmElem.addAttribute("active-sessions",               XFormsStateManager.activeSessionsCount.toString)
    jvmElem.addAttribute("active-document-locks",         XFormsStateManager.activeDocumentLocksCount.toString)

    val poolsElem = jvmElem.addElement("memory-pools")
    ManagementFactory.getMemoryPoolMXBeans.asScala
      .toList
      .sortBy(p => Option(p.getName).getOrElse("").toLowerCase(Locale.ROOT))
      .foreach { pool =>
        Option(pool.getUsage).foreach { usage =>
          val pElem     = poolsElem.addElement("pool")
          val used      = math.max(0L, usage.getUsed)
          val committed = math.max(0L, usage.getCommitted)
          val max       = usage.getMax
          pElem.addAttribute("name",                pool.getName)
          pElem.addAttribute("type",                if (pool.getType == MemoryType.HEAP) "Heap" else "Non-Heap")
          pElem.addAttribute("used-bytes",          used.toString)
          pElem.addAttribute("used-formatted",      formatBytes(used))
          pElem.addAttribute("committed-bytes",     committed.toString)
          pElem.addAttribute("committed-formatted", formatBytes(committed))
          pElem.addAttribute("max-bytes",           (if (max > 0L) max else 9007199254740991L).toString)
          pElem.addAttribute("max-formatted",       if (max > 0L) formatBytes(max) else "Unbounded")
        }
      }

    val gcsElem = jvmElem.addElement("garbage-collectors")
    ManagementFactory.getGarbageCollectorMXBeans.asScala
      .toList
      .sortBy(gc => Option(gc.getName).getOrElse("").toLowerCase(Locale.ROOT))
      .foreach { gc =>
        val gcElem = gcsElem.addElement("gc")
        val count  = math.max(0L, gc.getCollectionCount)
        val timeMs = math.max(0L, gc.getCollectionTime)
        gcElem.addAttribute("name",                      gc.getName)
        gcElem.addAttribute("collection-count",          count.toString)
        gcElem.addAttribute("collection-time-ms",        timeMs.toString)
        gcElem.addAttribute("collection-time-formatted", formatDurationMs(timeMs))
      }

    val flagsElem = jvmElem.addElement("vm-flags")
    runtimeMx.getInputArguments.asScala
      .filter(arg => SafeJvmFlagPrefixes.exists(arg.startsWith))
      .foreach { flag =>
        flagsElem.addElement("flag").addText(flag)
      }

    // ==== <caches> ====
    val cachesElem = root.addElement("caches")
    val cacheRows  = scala.collection.mutable.ListBuffer.empty[(String, dom.Element)]

    objectCacheSnaps.foreach { snap =>
      val canonicalName = ObjectCache.canonicalCacheName(snap.name)
      val cElem         = dom.Element("cache")

      cElem.addAttribute("kind",                     "object-cache")
      cElem.addAttribute("name",                     snap.name)
      cElem.addAttribute("canonical-key",            canonicalName.replace('.', '-'))
      cElem.addAttribute("engine",                   "ObjectCache (JVM Heap)")
      cElem.addAttribute("heap-entries",             snap.currentSize.toString)
      cElem.addAttribute("total-entries",            snap.currentSize.toString)
      cElem.addAttribute("total-entries-raw",        snap.currentSize.toString)
      cElem.addAttribute("max-entries",              snap.maxSize.toString)
      cElem.addAttribute("max-entries-raw",          snap.maxSize.toString)
      cElem.addAttribute("max-bytes",                snap.maxSizeBytes.map(_.toString).getOrElse(""))
      cElem.addAttribute("max-idle-ms",              snap.maxIdleMillis.map(_.toString).getOrElse(""))
      cElem.addAttribute("limit-summary",            formatObjectCacheLimitSummary(snap))
      cElem.addAttribute("estimated-heap-bytes",     snap.estimatedBytes.toString)
      cElem.addAttribute("estimated-heap-formatted", formatBytes(snap.estimatedBytes))
      cElem.addAttribute("disk-bytes",               "0")
      cElem.addAttribute("disk-bytes-raw",           "-1")
      cElem.addAttribute("disk-formatted",           "—")
      cElem.addAttribute("disk-files",               "0")
      cElem.addAttribute("hits",                     snap.hitCount.toString)
      cElem.addAttribute("hits-raw",                 snap.hitCount.toString)
      cElem.addAttribute("misses",                   snap.missCount.toString)
      cElem.addAttribute("hit-rate",                 snap.hitRateOpt.map(r => formatPercentValue(r * 100.0) + "%").getOrElse("—"))
      cElem.addAttribute("hit-rate-raw",             snap.hitRateOpt.map(r => (r * 100.0).toString).getOrElse("-1"))
      cElem.addAttribute("evictions",                snap.evictionCount.toString)
      cElem.addAttribute("evictions-raw",            snap.evictionCount.toString)
      cElem.addAttribute("can-clear",                SafeClearableObjectCaches(canonicalName).toString)
      cacheRows += (canonicalName -> cElem)
    }

    providerCacheSnaps.foreach { snap =>
      val canonicalName = CacheSupport.toCanonicalCacheName(snap.name)
      val cElem         = dom.Element("cache")
      val engineLabel =
        if (snap.diskStoreBytes.isDefined) s"${snap.provider.capitalize} (Heap + Disk)"
        else s"${snap.provider.capitalize} (Heap)"

      val limitParts = List(
        snap.maxEntriesLocalHeap.map(n => s"$n heap"),
        snap.maxBytesLocalHeap.map(b => s"${formatBytes(b)} heap"),
        Option.when(snap.passivation)("disk offload"),
        snap.maxIdleMs.map(ms => s"idle ${formatDurationMs(ms)}"),
        snap.lifespanMs.map(ms => s"max ${formatDurationMs(ms)}")
      ).flatten

      cElem.addAttribute("kind",                     "provider-cache")
      cElem.addAttribute("name",                     snap.name)
      cElem.addAttribute("canonical-key",            canonicalName.replace('.', '-'))
      cElem.addAttribute("engine",                   engineLabel)
      cElem.addAttribute("heap-entries",             snap.heapEntries.toString)
      cElem.addAttribute("total-entries",            snap.totalEntries.map(_.toString).getOrElse("—"))
      cElem.addAttribute("total-entries-raw",        snap.totalEntries.getOrElse(-1L).toString)
      cElem.addAttribute("max-entries-raw",          snap.maxEntriesLocalHeap.getOrElse(-1L).toString)
      cElem.addAttribute("limit-summary",            if (limitParts.nonEmpty) limitParts.mkString(", ") else "Default")
      cElem.addAttribute("estimated-heap-bytes",     snap.estimatedHeapBytes.toString)
      cElem.addAttribute("estimated-heap-formatted", formatBytes(snap.estimatedHeapBytes))
      cElem.addAttribute("disk-bytes",               snap.diskStoreBytes.getOrElse(0L).toString)
      cElem.addAttribute("disk-bytes-raw",           snap.diskStoreBytes.getOrElse(-1L).toString)
      cElem.addAttribute("disk-formatted",           snap.diskStoreBytes.map(formatBytes).getOrElse("—"))
      cElem.addAttribute("disk-files",               snap.diskStoreFiles.map(_.toString).getOrElse("—"))
      cElem.addAttribute("disk-path",                snap.diskStorePathOpt.getOrElse(""))
      cElem.addAttribute("hits",                     snap.hitCount.map(_.toString).getOrElse("—"))
      cElem.addAttribute("hits-raw",                 snap.hitCount.getOrElse(-1L).toString)
      cElem.addAttribute("misses",                   snap.missCount.map(_.toString).getOrElse("—"))
      cElem.addAttribute("hit-rate",                 snap.hitRateOpt.map(r => formatPercentValue(r * 100.0) + "%").getOrElse("—"))
      cElem.addAttribute("hit-rate-raw",             snap.hitRateOpt.map(r => (r * 100.0).toString).getOrElse("-1"))
      cElem.addAttribute("evictions",                snap.evictionCount.map(_.toString).getOrElse("—"))
      cElem.addAttribute("evictions-raw",            snap.evictionCount.getOrElse(-1L).toString)
      cElem.addAttribute("can-clear",                SafeClearableProviderCaches(canonicalName).toString)
      cacheRows += (canonicalName -> cElem)
    }

    locally {
      val canonicalName = "xforms.cache.xbl-bindings"
      val xblElem       = dom.Element("cache")
      xblElem.addAttribute("kind",                     "global-index")
      xblElem.addAttribute("name",                     canonicalName)
      xblElem.addAttribute("canonical-key",            canonicalName.replace('.', '-'))
      xblElem.addAttribute("engine",                   "Global Singleton (JVM Heap)")
      xblElem.addAttribute("heap-entries",             globalXblCount.toString)
      xblElem.addAttribute("total-entries",            globalXblCount.toString)
      xblElem.addAttribute("total-entries-raw",        globalXblCount.toString)
      xblElem.addAttribute("max-entries-raw",          "-1")
      xblElem.addAttribute("limit-summary",            "Global XBL library")
      xblElem.addAttribute("estimated-heap-bytes",     globalXblBytes.toString)
      xblElem.addAttribute("estimated-heap-formatted", formatBytes(globalXblBytes))
      xblElem.addAttribute("disk-bytes",               "0")
      xblElem.addAttribute("disk-bytes-raw",           "-1")
      xblElem.addAttribute("disk-formatted",           "—")
      xblElem.addAttribute("disk-files",               "0")
      xblElem.addAttribute("hits",                     "—")
      xblElem.addAttribute("hits-raw",                 "-1")
      xblElem.addAttribute("misses",                   "—")
      xblElem.addAttribute("hit-rate",                 "—")
      xblElem.addAttribute("hit-rate-raw",             "-1")
      xblElem.addAttribute("evictions",                "—")
      xblElem.addAttribute("evictions-raw",            "-1")
      xblElem.addAttribute("can-clear",                "false")
      cacheRows += (canonicalName -> xblElem)
    }

    cachesElem.addAttribute("count", cacheRows.size.toString)
    cacheRows.sortBy(_._1).foreach { case (_, elem) => cachesElem.add(elem) }

    // ==== <static-states> ====
    val staticStatesElem = root.addElement("static-states")
    val staticTotalBytes = staticStateEntries.iterator.map(_.estimatedBytes).sum
    staticStatesElem.addAttribute("count",           staticStateEntries.size.toString)
    staticStatesElem.addAttribute("total-bytes",     staticTotalBytes.toString)
    staticStatesElem.addAttribute("total-formatted", formatBytes(staticTotalBytes))

    staticStateEntries.sortBy(e => -e.estimatedBytes).foreach { entry =>
      entry.value match {
        case ss: XFormsStaticStateImpl =>
          val sElem       = staticStatesElem.addElement("static-state")
          val samplePath  = XFormsStaticStateCache.sampleRequestPathOpt(ss.digest).getOrElse("")
          val frMetaOpt   = ss.formRunnerMetadataOpt
          val displayName = frMetaOpt match {
            case Some((app, form, Some(title))) => s"$app/$form ($title)"
            case Some((app, form, None))        => s"$app/$form"
            case None if samplePath.nonEmpty    => samplePath
            case None                           => ss.digest.take(12)
          }
          val idleMs      = math.max(0L, nowMs - entry.lastAccessedAtMs)

          sElem.addAttribute("digest",               ss.digest)
          sElem.addAttribute("short-digest",         ss.digest.take(12))
          sElem.addAttribute("display-name",         displayName)
          sElem.addAttribute("app",                  frMetaOpt.map(_._1).getOrElse(""))
          sElem.addAttribute("form",                 frMetaOpt.map(_._2).getOrElse(""))
          sElem.addAttribute("sample-path",          samplePath)
          sElem.addAttribute("controls",             ss.topLevelPart.controlAnalysisMap.size.toString)
          sElem.addAttribute("models",               ss.topLevelPart.iterateModels.size.toString)
          sElem.addAttribute("xpath-analysis",       ss.isXPathAnalysis.toString)
          sElem.addAttribute("template-bytes",       ss.templateSizeBytes.toString)
          sElem.addAttribute("template-formatted",   formatBytes(ss.templateSizeBytes))
          sElem.addAttribute("static-dom-bytes",     ss.staticDomSizeBytes.toString)
          sElem.addAttribute("static-dom-formatted", formatBytes(ss.staticDomSizeBytes))
          sElem.addAttribute("encoded-bytes",        ss.encodedStateBytes.toString)
          sElem.addAttribute("encoded-formatted",    formatBytes(ss.encodedStateBytes))
          sElem.addAttribute("estimated-bytes",      entry.estimatedBytes.toString)
          sElem.addAttribute("estimated-formatted",  formatBytes(entry.estimatedBytes))
          sElem.addAttribute("idle-ms",              idleMs.toString)
          sElem.addAttribute("idle-formatted",       formatDurationMs(idleMs))
        case _ =>
      }
    }

    // ==== <live-documents> ====
    val liveDocsElem         = root.addElement("live-documents")
    val docsTotalBytes       = allLiveDocs.iterator.map(_.estimatedBytes).sum
    val docCacheLimitSummary =
      objectCacheSnaps
        .find(s => ObjectCache.canonicalCacheName(s.name) == "xforms.cache.live-documents")
        .map(formatObjectCacheLimitSummary)
        .getOrElse("")
    val stateStoreEntries    =
      providerCacheSnaps
        .find(s => CacheSupport.toCanonicalCacheName(s.name) == "xforms.store.dynamic-states")
        .flatMap(_.totalEntries)
        .map(_.toString)
        .getOrElse("—")

    liveDocsElem.addAttribute("count",               allLiveDocs.size.toString)
    liveDocsElem.addAttribute("cached-count",        cachedDocs.size.toString)
    liveDocsElem.addAttribute("in-flight-count",     inFlightDocs.size.toString)
    liveDocsElem.addAttribute("active-locks",        XFormsStateManager.activeDocumentLocksCount.toString)
    liveDocsElem.addAttribute("cache-limit-summary", docCacheLimitSummary)
    liveDocsElem.addAttribute("state-store-entries", stateStoreEntries)
    liveDocsElem.addAttribute("total-bytes",         docsTotalBytes.toString)
    liveDocsElem.addAttribute("total-formatted",     formatBytes(docsTotalBytes))

    val groupsElem = liveDocsElem.addElement("groups")
    allLiveDocs
      .groupBy(d => d.requestPath.trimAllToOpt.getOrElse("(unknown)"))
      .toList
      .map { case (path, items) =>
        val sumBytes = items.iterator.map(_.estimatedBytes).sum
        val maxSeq   = items.iterator.map(_.sequence).maxOption.getOrElse(1L)
        (path, items.size, sumBytes, maxSeq)
      }
      .sortBy { case (path, _, sumBytes, _) => (-sumBytes, path) }
      .foreach { case (path, count, sumBytes, maxSeq) =>
        val gElem = groupsElem.addElement("group")
        gElem.addAttribute("request-path",    path)
        gElem.addAttribute("count",           count.toString)
        gElem.addAttribute("total-bytes",     sumBytes.toString)
        gElem.addAttribute("total-formatted", formatBytes(sumBytes))
        gElem.addAttribute("max-sequence",    maxSeq.toString)
      }

    val itemsElem = liveDocsElem.addElement("documents")
    allLiveDocs
      .sortBy(d => (-d.estimatedBytes, d.uuid))
      .take(200)
      .foreach { d =>
        val dElem = itemsElem.addElement("document")
        dElem.addAttribute("uuid",                d.uuid)
        dElem.addAttribute("short-uuid",          d.uuid.take(12))
        dElem.addAttribute("request-path",        d.requestPath)
        dElem.addAttribute("status",              d.status)
        dElem.addAttribute("sequence",            d.sequence.toString)
        dElem.addAttribute("controls",            d.controlCount.toString)
        dElem.addAttribute("estimated-bytes",     d.estimatedBytes.toString)
        dElem.addAttribute("estimated-formatted", formatBytes(d.estimatedBytes))
        dElem.addAttribute("idle-ms",             d.idleMs.toString)
        dElem.addAttribute("idle-formatted",      d.idleFormatted)
      }

    doc
  }

  private final case class LiveDocSnapshot(
    uuid           : String,
    requestPath    : String,
    sequence       : Long,
    controlCount   : Int,
    estimatedBytes : Long,
    idleMs         : Long,
    idleFormatted  : String,
    status         : String
  )

  private def formatObjectCacheLimitSummary(snap: ObjectCacheStatsSnapshot): String =
    List(
      Some(s"${snap.maxSize} entries"),
      snap.maxSizeBytes.map(b => s"${formatBytes(b)} max"),
      snap.maxIdleMillis.map(ms => s"idle ${formatDurationMs(ms)}")
    ).flatten.mkString(", ")

  def formatBytes(bytes: Long): String =
    if (bytes < 1024L) {
      s"$bytes B"
    } else if (bytes < 1024L * 1024L) {
      String.format(Locale.ROOT, "%.1f KB", bytes / 1024.0)
    } else if (bytes < 1024L * 1024L * 1024L) {
      String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0))
    } else {
      String.format(Locale.ROOT, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
    }

  def formatPercentValue(pct: Double): String =
    String.format(Locale.ROOT, "%.1f", pct)

  def formatDurationMs(ms: Long): String = {
    val totalSec = math.max(0L, ms / 1000L)
    if (totalSec == 0L) {
      s"${math.max(0L, ms)} ms"
    } else if (totalSec < 60L) {
      s"${totalSec}s"
    } else if (totalSec < 3600L) {
      val m = totalSec / 60L
      val s = totalSec % 60L
      if (s == 0L) s"${m}m" else s"${m}m ${s}s"
    } else if (totalSec < 86400L) {
      val h = totalSec / 3600L
      val m = (totalSec % 3600L) / 60L
      if (m == 0L) s"${h}h" else s"${h}h ${m}m"
    } else {
      val d = totalSec / 86400L
      val h = (totalSec % 86400L) / 3600L
      if (h == 0L) s"${d}d" else s"${d}d ${h}h"
    }
  }
}
