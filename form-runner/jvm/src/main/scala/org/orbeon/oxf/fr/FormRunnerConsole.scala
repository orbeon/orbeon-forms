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
import org.orbeon.oxf.xforms.state.{XFormsDocumentCache, XFormsStateManager, XFormsStaticStateCache}
import org.orbeon.oxf.xforms.xbl.{BindingIndex, GlobalBindingIndex}
import org.orbeon.oxf.xforms.{XFormsContainingDocument, XFormsServerSharedInstancesCache, XFormsStaticStateImpl, XFormsStores}

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
    "xforms.cache.static-state",
    "xforms.cache.shared-instances",
    "cache.xpath"
  )

  private val SafeClearableProviderCaches: Set[String] = Set(
    "form-runner.persistence.form-definition",
    "orbeon.properties"
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
      case "object-cache" if SafeClearableObjectCaches(cacheName) =>
        ObjectCache.findCache(cacheName).exists { c =>
          c.removeAll()
          true
        }
      case "provider-cache" if SafeClearableProviderCaches(cacheName) =>
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
    // Ensure standard caches/stores are registered so they always appear in the console table
    val _ = (
      ObjectCache.instance,
      XFormsStaticStateCache.getCacheForDiagnostics,
      XFormsDocumentCache.getCacheForDiagnostics,
      XFormsServerSharedInstancesCache.getCacheForDiagnostics,
      XPathCache.getCacheForDiagnostics,
      XFormsStores.stateStore,
      XFormsStores.staticStateStore,
      XFormsStores.resourcesStore
    )

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

    val totalEstimatedCacheHeapBytes =
      objectCacheSnaps.iterator.map(_.estimatedBytes).sum +
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
    ManagementFactory.getMemoryPoolMXBeans.asScala.foreach { pool =>
      Option(pool.getUsage).foreach { usage =>
        val pElem = poolsElem.addElement("pool")
        val used  = math.max(0L, usage.getUsed)
        val max   = usage.getMax
        pElem.addAttribute("name",           pool.getName)
        pElem.addAttribute("type",           if (pool.getType == MemoryType.HEAP) "Heap" else "Non-Heap")
        pElem.addAttribute("used-bytes",     used.toString)
        pElem.addAttribute("used-formatted", formatBytes(used))
        pElem.addAttribute("committed-formatted", formatBytes(math.max(0L, usage.getCommitted)))
        pElem.addAttribute("max-formatted",  if (max > 0) formatBytes(max) else "Unbounded")
      }
    }

    val gcsElem = jvmElem.addElement("garbage-collectors")
    ManagementFactory.getGarbageCollectorMXBeans.asScala.foreach { gc =>
      val gcElem = gcsElem.addElement("gc")
      val count  = math.max(0L, gc.getCollectionCount)
      val timeMs = math.max(0L, gc.getCollectionTime)
      gcElem.addAttribute("name",                gc.getName)
      gcElem.addAttribute("collection-count",    count.toString)
      gcElem.addAttribute("collection-time-ms",  timeMs.toString)
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
    cachesElem.addAttribute("count", (objectCacheSnaps.size + providerCacheSnaps.size + 1).toString)

    objectCacheSnaps.foreach { snap =>
      val cElem = cachesElem.addElement("cache")
      cElem.addAttribute("kind",                     "object-cache")
      cElem.addAttribute("name",                     snap.name)
      cElem.addAttribute("engine",                   "ObjectCache (JVM Heap)")
      cElem.addAttribute("heap-entries",             snap.currentSize.toString)
      cElem.addAttribute("total-entries",            snap.currentSize.toString)
      cElem.addAttribute("limit-summary",            s"${snap.maxSize} entries")
      cElem.addAttribute("estimated-heap-bytes",     snap.estimatedBytes.toString)
      cElem.addAttribute("estimated-heap-formatted", formatBytes(snap.estimatedBytes))
      cElem.addAttribute("disk-bytes",               "0")
      cElem.addAttribute("disk-formatted",           "—")
      cElem.addAttribute("disk-files",               "0")
      cElem.addAttribute("hits",                     snap.hitCount.toString)
      cElem.addAttribute("misses",                   snap.missCount.toString)
      cElem.addAttribute("hit-rate",                 snap.hitRateOpt.map(r => formatPercentValue(r * 100.0) + "%").getOrElse("—"))
      cElem.addAttribute("evictions",                snap.evictionCount.toString)
      cElem.addAttribute("can-clear",                SafeClearableObjectCaches(snap.name).toString)
    }

    providerCacheSnaps.foreach { snap =>
      val cElem = cachesElem.addElement("cache")
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
      cElem.addAttribute("engine",                   engineLabel)
      cElem.addAttribute("heap-entries",             snap.heapEntries.toString)
      cElem.addAttribute("total-entries",            snap.totalEntries.map(_.toString).getOrElse("—"))
      cElem.addAttribute("limit-summary",            if (limitParts.nonEmpty) limitParts.mkString(", ") else "Default")
      cElem.addAttribute("estimated-heap-bytes",     snap.estimatedHeapBytes.toString)
      cElem.addAttribute("estimated-heap-formatted", formatBytes(snap.estimatedHeapBytes))
      cElem.addAttribute("disk-bytes",               snap.diskStoreBytes.getOrElse(0L).toString)
      cElem.addAttribute("disk-formatted",           snap.diskStoreBytes.map(formatBytes).getOrElse("—"))
      cElem.addAttribute("disk-files",               snap.diskStoreFiles.map(_.toString).getOrElse("—"))
      cElem.addAttribute("disk-path",                snap.diskStorePathOpt.getOrElse(""))
      cElem.addAttribute("hits",                     snap.hitCount.map(_.toString).getOrElse("—"))
      cElem.addAttribute("misses",                   snap.missCount.map(_.toString).getOrElse("—"))
      cElem.addAttribute("hit-rate",                 snap.hitRateOpt.map(r => formatPercentValue(r * 100.0) + "%").getOrElse("—"))
      cElem.addAttribute("evictions",                snap.evictionCount.map(_.toString).getOrElse("—"))
      cElem.addAttribute("can-clear",                SafeClearableProviderCaches(snap.name).toString)
    }

    locally {
      val xblElem = cachesElem.addElement("cache")
      xblElem.addAttribute("kind",                     "global-index")
      xblElem.addAttribute("name",                     "xbl.global-binding-index")
      xblElem.addAttribute("engine",                   "Global Singleton (JVM Heap)")
      xblElem.addAttribute("heap-entries",             globalXblCount.toString)
      xblElem.addAttribute("total-entries",            globalXblCount.toString)
      xblElem.addAttribute("limit-summary",            "Global XBL library")
      xblElem.addAttribute("estimated-heap-bytes",     globalXblBytes.toString)
      xblElem.addAttribute("estimated-heap-formatted", formatBytes(globalXblBytes))
      xblElem.addAttribute("disk-bytes",               "0")
      xblElem.addAttribute("disk-formatted",           "—")
      xblElem.addAttribute("disk-files",               "0")
      xblElem.addAttribute("hits",                     "—")
      xblElem.addAttribute("misses",                   "—")
      xblElem.addAttribute("hit-rate",                 "—")
      xblElem.addAttribute("evictions",                "—")
      xblElem.addAttribute("can-clear",                "false")
    }

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

          sElem.addAttribute("digest",              ss.digest)
          sElem.addAttribute("short-digest",        ss.digest.take(12))
          sElem.addAttribute("display-name",        displayName)
          sElem.addAttribute("app",                 frMetaOpt.map(_._1).getOrElse(""))
          sElem.addAttribute("form",                frMetaOpt.map(_._2).getOrElse(""))
          sElem.addAttribute("sample-path",         samplePath)
          sElem.addAttribute("controls",            ss.topLevelPart.controlAnalysisMap.size.toString)
          sElem.addAttribute("models",              ss.topLevelPart.iterateModels.size.toString)
          sElem.addAttribute("xpath-analysis",      ss.isXPathAnalysis.toString)
          sElem.addAttribute("template-formatted",  formatBytes(ss.templateSizeBytes))
          sElem.addAttribute("static-dom-formatted", formatBytes(ss.staticDomSizeBytes))
          sElem.addAttribute("encoded-formatted",   formatBytes(ss.encodedStateBytes))
          sElem.addAttribute("estimated-bytes",     entry.estimatedBytes.toString)
          sElem.addAttribute("estimated-formatted", formatBytes(entry.estimatedBytes))
          sElem.addAttribute("idle-formatted",      formatDurationMs(math.max(0L, nowMs - entry.lastAccessedAtMs)))
        case _ =>
      }
    }

    // ==== <live-documents> ====
    val liveDocsElem  = root.addElement("live-documents")
    val docsTotalBytes = documentEntries.iterator.map(_.estimatedBytes).sum
    liveDocsElem.addAttribute("count",           documentEntries.size.toString)
    liveDocsElem.addAttribute("active-locks",    XFormsStateManager.activeDocumentLocksCount.toString)
    liveDocsElem.addAttribute("total-bytes",     docsTotalBytes.toString)
    liveDocsElem.addAttribute("total-formatted", formatBytes(docsTotalBytes))

    val typedDocs = documentEntries.collect {
      case entry if entry.value.isInstanceOf[XFormsContainingDocument] =>
        (entry, entry.value.asInstanceOf[XFormsContainingDocument])
    }

    val groupsElem = liveDocsElem.addElement("groups")
    typedDocs
      .groupBy { case (_, xfcd) => Option(xfcd.getRequestPath).flatMap(_.trimAllToOpt).getOrElse("(unknown)") }
      .toList
      .map { case (path, items) =>
        val sumBytes = items.iterator.map(_._1.estimatedBytes).sum
        val maxSeq   = items.iterator.map(_._2.sequence).maxOption.getOrElse(1L)
        (path, items.size, sumBytes, maxSeq)
      }
      .sortBy { case (_, _, sumBytes, _) => -sumBytes }
      .foreach { case (path, count, sumBytes, maxSeq) =>
        val gElem = groupsElem.addElement("group")
        gElem.addAttribute("request-path",    path)
        gElem.addAttribute("count",           count.toString)
        gElem.addAttribute("total-bytes",     sumBytes.toString)
        gElem.addAttribute("total-formatted", formatBytes(sumBytes))
        gElem.addAttribute("max-sequence",    maxSeq.toString)
      }

    val itemsElem = liveDocsElem.addElement("documents")
    typedDocs
      .sortBy { case (entry, _) => -entry.estimatedBytes }
      .take(200)
      .foreach { case (entry, xfcd) =>
        val dElem = itemsElem.addElement("document")
        dElem.addAttribute("uuid",                xfcd.uuid)
        dElem.addAttribute("short-uuid",          xfcd.uuid.take(12))
        dElem.addAttribute("request-path",        Option(xfcd.getRequestPath).getOrElse(""))
        dElem.addAttribute("sequence",            xfcd.sequence.toString)
        dElem.addAttribute("controls",            xfcd.liveControlCount.toString)
        dElem.addAttribute("estimated-bytes",     entry.estimatedBytes.toString)
        dElem.addAttribute("estimated-formatted", formatBytes(entry.estimatedBytes))
        dElem.addAttribute("idle-formatted",      formatDurationMs(math.max(0L, nowMs - entry.lastAccessedAtMs)))
      }

    doc
  }

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
