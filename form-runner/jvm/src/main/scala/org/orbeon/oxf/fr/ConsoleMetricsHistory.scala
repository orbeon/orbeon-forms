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

import org.orbeon.oxf.cache.{CacheSupport, ObjectCache}
import org.orbeon.oxf.properties.Properties
import org.orbeon.oxf.xforms.state.{XFormsDocumentCache, XFormsStateManager}

import java.lang.management.ManagementFactory
import java.util.concurrent.{Executors, ScheduledExecutorService, ScheduledFuture, TimeUnit}
import scala.jdk.CollectionConverters.*
import scala.util.control.NonFatal


object ConsoleMetricsHistory {

  val HistoryEnableProperty   = "oxf.fr.console.history.enable"
  val SampleIntervalProperty  = "oxf.fr.console.history.sample-interval"

  val DefaultSampleIntervalMs = 15000L
  val MinSampleIntervalMs     = 1000L
  val CoarseBucketIntervalMs  = 5L * 60L * 1000L // 5 minutes

  val FineMaxSamples          = 240 // 1h at 15s
  val CoarseMaxSamples        = 288 // 24h at 5m

  val Window15mMs             = 15L * 60L * 1000L
  val Window1hMs              = 60L * 60L * 1000L
  val Window24hMs             = 24L * 60L * 60L * 1000L

  final case class MetricsSample(
    timestampMs              : Long,
    heapUsedBytes            : Long,
    heapCommittedBytes       : Long,
    heapMaxBytes             : Long,
    nonHeapUsedBytes         : Long,
    estimatedCachesHeapBytes : Long,
    diskStoreBytes           : Long,
    totalCacheHits           : Long,
    totalCacheMisses         : Long,
    totalCacheEvictions      : Long,
    activeSessions           : Int,
    liveDocsInHeap           : Int,
    stateStoreEntries        : Long,
    gcCollectionCount        : Long,
    gcCollectionTimeMs       : Long
  )

  final case class MetricsPointWithDeltas(
    sample         : MetricsSample,
    elapsedSec     : Double,
    hitsPerSec     : Double,
    missesPerSec   : Double,
    evictionsDelta : Long,
    gcPauseMsDelta : Long,
    gcCountDelta   : Long
  )

  private final class RingBuffer(capacity: Int) {
    private val buf  = new Array[MetricsSample](capacity)
    private var head = 0
    private var size = 0

    def append(sample: MetricsSample): Unit = {
      if (size < capacity) {
        buf((head + size) % capacity) = sample
        size += 1
      } else {
        buf(head) = sample
        head = (head + 1) % capacity
      }
    }

    def replaceLast(sample: MetricsSample): Unit =
      if (size == 0) {
        append(sample)
      } else {
        val lastIdx = (head + size - 1) % capacity
        buf(lastIdx) = sample
      }

    def lastOpt: Option[MetricsSample] =
      if (size == 0) None else Some(buf((head + size - 1) % capacity))

    def toList: List[MetricsSample] = {
      val b = List.newBuilder[MetricsSample]
      var i = 0
      while (i < size) {
        b += buf((head + i) % capacity)
        i += 1
      }
      b.result()
    }

    def clear(): Unit = {
      var i = 0
      while (i < capacity) {
        buf(i) = null
        i += 1
      }
      head = 0
      size = 0
    }
  }

  private val lock = new AnyRef

  private val fineRing   = new RingBuffer(FineMaxSamples)
  private val coarseRing = new RingBuffer(CoarseMaxSamples)

  // Cached baselines updated whenever `/fr/console` computes exact GlobalBindingIndex / InFlight sizes
  @volatile private var lastGlobalXblBytes: Long = 0L

  private var schedulerOpt         : Option[ScheduledExecutorService] = None
  private var scheduledTaskOpt     : Option[ScheduledFuture[?]]       = None
  private var configuredIntervalMs : Long                             = -1L
  private var configuredEnabled    : Boolean                          = false

  CacheSupport.addCloseListener(() => shutdown())

  def updateGlobalXblBytesBaseline(bytes: Long): Unit =
    lastGlobalXblBytes = math.max(0L, bytes)

  def ensureConfigured(): Unit =
    try {
      val psOpt = Option(Properties.instance).flatMap(p => Option(p.getPropertySet))
      val enabled = psOpt.forall(_.getBoolean(HistoryEnableProperty, default = true))
      val intervalMs =
        psOpt
          .flatMap(_.getNonBlankString(SampleIntervalProperty))
          .flatMap(ObjectCache.parseDurationMillis)
          .filter(_ >= MinSampleIntervalMs)
          .getOrElse(DefaultSampleIntervalMs)

      lock.synchronized {
        if (! enabled) {
          cancelScheduledTaskUnlocked()
          configuredEnabled    = false
          configuredIntervalMs = -1L
        } else if (! configuredEnabled || configuredIntervalMs != intervalMs || scheduledTaskOpt.forall(_.isCancelled)) {
          cancelScheduledTaskUnlocked()
          val scheduler =
            schedulerOpt.filterNot(_.isShutdown).getOrElse {
              val exec = Executors.newSingleThreadScheduledExecutor { runnable =>
                val t = new Thread(runnable, "orbeon-console-metrics-sampler")
                t.setDaemon(true)
                t
              }
              schedulerOpt = Some(exec)
              exec
            }

          if (fineRing.lastOpt.isEmpty)
            try recordSampleUnlocked(collectFastSample(System.currentTimeMillis()))
            catch { case NonFatal(_) => () }

          val future =
            scheduler.scheduleAtFixedRate(
              (() => {
                try {
                  val sample = collectFastSample(System.currentTimeMillis())
                  lock.synchronized(recordSampleUnlocked(sample))
                } catch {
                  case NonFatal(_) => ()
                }
              }): Runnable,
              intervalMs,
              intervalMs,
              TimeUnit.MILLISECONDS
            )

          scheduledTaskOpt     = Some(future)
          configuredEnabled    = true
          configuredIntervalMs = intervalMs
        }
      }
    } catch {
      case NonFatal(_) => ()
    }

  def recordConsoleSnapshotSample(sample: MetricsSample, minAgeMs: Long = 2000L): Unit =
    lock.synchronized {
      fineRing.lastOpt match {
        case Some(prev) if sample.timestampMs - prev.timestampMs < minAgeMs =>
          // Refresh the most recent point in-place when user clicks Refresh rapidly so charts stay live
          // without crowding the 240-slot 1h buffer.
          fineRing.replaceLast(sample)
          coarseRing.lastOpt match {
            case Some(cPrev) if sample.timestampMs - cPrev.timestampMs < CoarseBucketIntervalMs =>
              coarseRing.replaceLast(sample)
            case _ =>
              coarseRing.append(sample)
          }
        case _ =>
          recordSampleUnlocked(sample)
      }
    }

  private[fr] def recordSampleForTests(sample: MetricsSample): Unit =
    lock.synchronized(recordSampleUnlocked(sample))

  private[fr] def clearForTests(): Unit =
    lock.synchronized {
      fineRing.clear()
      coarseRing.clear()
    }

  def snapshotWindow(window: String, nowMs: Long = System.currentTimeMillis()): List[MetricsPointWithDeltas] = {
    val normalizedWindow = normalizeWindow(window)
    val rawSamples =
      lock.synchronized {
        normalizedWindow match {
          case "15m" =>
            val cutoff = nowMs - Window15mMs
            val list   = fineRing.toList.filter(_.timestampMs >= cutoff)
            if (list.nonEmpty) list else fineRing.lastOpt.toList
          case "24h" =>
            val cutoff     = nowMs - Window24hMs
            val coarseList = coarseRing.toList.filter(_.timestampMs >= cutoff)
            // Append the newest fine sample if newer than the last coarse bucket so the right edge is always current
            val withTail =
              (coarseList.lastOption, fineRing.lastOpt) match {
                case (Some(lastCoarse), Some(lastFine)) if lastFine.timestampMs > lastCoarse.timestampMs =>
                  coarseList :+ lastFine
                case (None, Some(lastFine)) =>
                  List(lastFine)
                case _ =>
                  coarseList
              }
            withTail
          case _ => // "1h"
            val cutoff = nowMs - Window1hMs
            val list   = fineRing.toList.filter(_.timestampMs >= cutoff)
            if (list.nonEmpty) list else fineRing.lastOpt.toList
        }
      }

    computeDeltas(rawSamples)
  }

  def normalizeWindow(window: String): String =
    Option(window).map(_.trim.toLowerCase) match {
      case Some("15m") => "15m"
      case Some("24h") => "24h"
      case _           => "1h"
    }

  def computeDeltas(samples: List[MetricsSample]): List[MetricsPointWithDeltas] =
    samples match {
      case Nil =>
        Nil
      case head :: tail =>
        val b = List.newBuilder[MetricsPointWithDeltas]
        b += MetricsPointWithDeltas(
          sample         = head,
          elapsedSec     = 0.0,
          hitsPerSec     = 0.0,
          missesPerSec   = 0.0,
          evictionsDelta = 0L,
          gcPauseMsDelta = 0L,
          gcCountDelta   = 0L
        )
        var prev = head
        tail.foreach { curr =>
          val dtMs       = math.max(1L, curr.timestampMs - prev.timestampMs)
          val dtSec      = math.max(0.25, dtMs / 1000.0)
          val dHits      = math.max(0L, curr.totalCacheHits      - prev.totalCacheHits)
          val dMisses    = math.max(0L, curr.totalCacheMisses    - prev.totalCacheMisses)
          val dEvictions = math.max(0L, curr.totalCacheEvictions - prev.totalCacheEvictions)
          val dGcMs      = math.max(0L, curr.gcCollectionTimeMs  - prev.gcCollectionTimeMs)
          val dGcCount   = math.max(0L, curr.gcCollectionCount   - prev.gcCollectionCount)

          b += MetricsPointWithDeltas(
            sample         = curr,
            elapsedSec     = dtSec,
            hitsPerSec     = dHits / dtSec,
            missesPerSec   = dMisses / dtSec,
            evictionsDelta = dEvictions,
            gcPauseMsDelta = dGcMs,
            gcCountDelta   = dGcCount
          )
          prev = curr
        }
        b.result()
    }

  def shutdown(): Unit =
    lock.synchronized {
      cancelScheduledTaskUnlocked()
      schedulerOpt.foreach(_.shutdownNow())
      schedulerOpt         = None
      configuredEnabled    = false
      configuredIntervalMs = -1L
    }

  private def cancelScheduledTaskUnlocked(): Unit = {
    scheduledTaskOpt.foreach(_.cancel(false))
    scheduledTaskOpt = None
  }

  private def recordSampleUnlocked(sample: MetricsSample): Unit = {
    fineRing.append(sample)
    coarseRing.lastOpt match {
      case None =>
        coarseRing.append(sample)
      case Some(prevCoarse) if sample.timestampMs - prevCoarse.timestampMs >= CoarseBucketIntervalMs =>
        coarseRing.append(sample)
      case _ =>
        ()
    }
  }

  // O(1) counter-only background sample (never walks DOM or static-state object graphs)
  private def collectFastSample(nowMs: Long): MetricsSample = {
    val memoryMx      = ManagementFactory.getMemoryMXBean
    val heapUsage     = memoryMx.getHeapMemoryUsage
    val nonHeap       = memoryMx.getNonHeapMemoryUsage
    val heapUsed      = math.max(0L, heapUsage.getUsed)
    val heapCommitted = math.max(0L, heapUsage.getCommitted)
    val heapMax       = if (heapUsage.getMax > 0L) heapUsage.getMax else math.max(Runtime.getRuntime.maxMemory(), heapCommitted)
    val nonHeapUsed   = math.max(0L, nonHeap.getUsed)

    var gcCount  = 0L
    var gcTimeMs = 0L
    ManagementFactory.getGarbageCollectorMXBeans.asScala.foreach { gc =>
      gcCount  += math.max(0L, gc.getCollectionCount)
      gcTimeMs += math.max(0L, gc.getCollectionTime)
    }

    var objBytes     = 0L
    var totalHits    = 0L
    var totalMisses  = 0L
    var totalEvicts  = 0L
    ObjectCache.allCaches.foreach { case (_, c) =>
      objBytes    += math.max(0L, c.getCurrentEstimatedBytes)
      totalHits   += math.max(0L, c.getHitCount)
      totalMisses += math.max(0L, c.getMissCount)
      totalEvicts += math.max(0L, c.getEvictionCount)
    }

    val providerSnaps = CacheSupport.allRunningCacheSnapshots
    var provHeapBytes = 0L
    var diskBytes     = 0L
    var stateEntries  = 0L
    providerSnaps.foreach { snap =>
      provHeapBytes += math.max(0L, snap.estimatedHeapBytes)
      diskBytes     += snap.diskStoreBytes.getOrElse(0L)
      totalHits     += snap.hitCount.getOrElse(0L)
      totalMisses   += snap.missCount.getOrElse(0L)
      totalEvicts   += snap.evictionCount.getOrElse(0L)
      if (CacheSupport.toCanonicalCacheName(snap.name) == "xforms.store.dynamic-states")
        stateEntries = snap.totalEntries.getOrElse(snap.heapEntries)
    }

    val inFlightSnapshots = XFormsStateManager.inFlightDocumentsSnapshot
    val inFlightBytes     = inFlightSnapshots.iterator.map(_.estimatedBytes).sum
    val cachedDocCount    = XFormsDocumentCache.getCacheForDiagnostics.getCurrentSize
    val liveDocsCount     = cachedDocCount + inFlightSnapshots.size

    MetricsSample(
      timestampMs              = nowMs,
      heapUsedBytes            = heapUsed,
      heapCommittedBytes       = heapCommitted,
      heapMaxBytes             = heapMax,
      nonHeapUsedBytes         = nonHeapUsed,
      estimatedCachesHeapBytes = objBytes + inFlightBytes + provHeapBytes + lastGlobalXblBytes,
      diskStoreBytes           = diskBytes,
      totalCacheHits           = totalHits,
      totalCacheMisses         = totalMisses,
      totalCacheEvictions      = totalEvicts,
      activeSessions           = XFormsStateManager.activeSessionsCount,
      liveDocsInHeap           = liveDocsCount,
      stateStoreEntries        = stateEntries,
      gcCollectionCount        = gcCount,
      gcCollectionTimeMs       = gcTimeMs
    )
  }
}
