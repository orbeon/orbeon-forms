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
package org.orbeon.oxf.fr.analytics

import org.orbeon.oxf.cache.CacheSupport
import org.orbeon.oxf.fr.{AppForm, Names}
import org.orbeon.oxf.util.CoreCrossPlatformSupport
import org.orbeon.oxf.util.StringUtils.*
import org.orbeon.oxf.xforms.{XFormsContainingDocument, XFormsStaticStateImpl}
import org.orbeon.oxf.xforms.analytics.{DocumentRemovalReason, SubmissionCategory, XFormsAnalyticsListener, XFormsAnalyticsSupport}
import org.orbeon.scaxon.SimplePath.*

import java.util as ju
import scala.collection.mutable
import scala.jdk.CollectionConverters.*


object InMemoryFormSessionAnalyticsStore {

  val EnableProperty            = "oxf.fr.analytics.enable"
  val ShowUsernamesProperty     = "oxf.fr.analytics.show-usernames"
  val MaxActiveSessionsProperty = "oxf.fr.analytics.max-active-sessions"
  val MaxRecentSessionsProperty = "oxf.fr.analytics.max-recent-sessions"

  val DefaultMaxActiveSessions  = 20000
  val DefaultMaxRecentSessions  = 500
  val MaxTrackedFormsPerBucket  = 512

  val ActiveFormRecencyMs       = 5L * 60L * 1000L
  val ActiveHttpRecencyMs       = 15L * 60L * 1000L

  private val ExcludedModes: Set[String] = Set(
    "",
    "landing",
    "home",
    "console",
    "analytics",
    "summary",
    "test",
    "test-pdf",
    "schema",
    "controls",
    "compile"
  )

  private val ReadonlyDetailModes: Set[String] = Set(
    "view",
    "pdf",
    "tiff",
    "export",
    "excel-export",
    "confirmation",
    "fr:confirmation"
  )

  private val HistogramUpperBoundsMs: Array[Long] = Array(
    1L, 2L, 5L, 10L, 20L, 35L, 50L, 75L, 100L, 150L, 250L, 400L,
    600L, 1000L, 1500L, 2500L, 4000L, 6000L, 10000L, 15000L, 30000L, 60000L, 120000L, Long.MaxValue
  )

  private[analytics] final class LatencyHistogram {
    private val counts = new Array[Long](HistogramUpperBoundsMs.length)
    var count      : Long = 0L
    var totalMs    : Long = 0L
    var maxMs      : Long = 0L
    var errorCount : Long = 0L

    def record(durationMs: Long, succeeded: Boolean): Unit = {
      val clamped = math.max(0L, durationMs)
      count   += 1L
      totalMs += clamped
      if (clamped > maxMs)
        maxMs = clamped
      if (! succeeded)
        errorCount += 1L

      var idx = 0
      while (idx < HistogramUpperBoundsMs.length - 1 && clamped > HistogramUpperBoundsMs(idx))
        idx += 1
      counts(idx) += 1L
    }

    def mergeFrom(other: LatencyHistogram): Unit =
      if (other.count > 0L || other.errorCount > 0L) {
        count      += other.count
        totalMs    += other.totalMs
        errorCount += other.errorCount
        if (other.maxMs > maxMs)
          maxMs = other.maxMs
        var i = 0
        while (i < counts.length) {
          counts(i) += other.counts(i)
          i += 1
        }
      }

    def toSummary: TimingSummary =
      if (count <= 0L) {
        TimingSummary(0L, 0L, 0.0, 0L, 0L, 0L, errorCount)
      } else {
        TimingSummary(
          count      = count,
          totalMs    = totalMs,
          avgMs      = totalMs.toDouble / count.toDouble,
          p50Ms      = math.min(maxMs, percentileBound(0.50)),
          p95Ms      = math.min(maxMs, percentileBound(0.95)),
          maxMs      = maxMs,
          errorCount = errorCount
        )
      }

    private def percentileBound(q: Double): Long = {
      val target = math.max(1L, math.ceil(count.toDouble * q).toLong)
      var cum    = 0L
      var i      = 0
      while (i < counts.length) {
        cum += counts(i)
        if (cum >= target) {
          val ub = HistogramUpperBoundsMs(i)
          return if (ub == Long.MaxValue) maxMs else ub
        }
        i += 1
      }
      maxMs
    }
  }

  final case class ExtractedFormRunnerDocInfo(
    appForm            : AppForm,
    version            : Int,
    mode               : String,
    isInteractiveEntry : Boolean
  )

  def extractFormRunnerDocInfo(doc: XFormsContainingDocument): Option[ExtractedFormRunnerDocInfo] = {
    val fromParamsOpt =
      doc.findInstance("fr-parameters-instance").flatMap { inst =>
        val root = inst.rootElement
        val app  = root.elemValue("app").trimAllToOpt
        val form = root.elemValue("form").trimAllToOpt
        val mode = root.elemValue("mode").trimAllToOpt.getOrElse("")
        val ver  = root.elemValue("form-version").trimAllToOpt.flatMap(_.toIntOption).getOrElse(1)
        for {
          a <- app
          f <- form
          if isTrackableForm(a, f, mode)
        } yield ExtractedFormRunnerDocInfo(
          appForm            = AppForm(a, f),
          version            = ver,
          mode               = mode,
          isInteractiveEntry = ! ReadonlyDetailModes(mode)
        )
      }

    fromParamsOpt.orElse {
      doc.staticState match {
        case ss: XFormsStaticStateImpl =>
          for {
            meta <- ss.formRunnerStaticStateMetadataOpt
            app  <- meta.appOpt
            form <- meta.formOpt
            mode = extractModeFromPath(Option(doc.getRequestPath).getOrElse(""), app, form)
            if isTrackableForm(app, form, mode)
          } yield ExtractedFormRunnerDocInfo(
            appForm            = AppForm(app, form),
            version            = meta.versionOpt.getOrElse(1),
            mode               = mode,
            isInteractiveEntry = ! ReadonlyDetailModes(mode)
          )
        case _ =>
          None
      }
    }
  }

  def extractAppFormFromPath(requestPath: String): Option[AppForm] =
    requestPath.splitTo[List]("/") match {
      case "fr" :: "service" :: _ => None
      case "fr" :: app :: form :: mode :: _ if isTrackableForm(app, form, mode) =>
        Some(AppForm(app, form))
      case _ =>
        None
    }

  private def extractModeFromPath(requestPath: String, app: String, form: String): String =
    requestPath.splitTo[List]("/") match {
      case "fr" :: a :: f :: mode :: _ if a == app && f == form => mode
      case _                                                    => "new"
    }

  private def isTrackableForm(app: String, form: String, mode: String): Boolean =
    app != "*" &&
      form != "*" &&
      form != Names.LibraryFormName &&
      ! (app == "orbeon" && (form == "builder" || form == "configuration")) &&
      ! ExcludedModes(mode)
}

final class InMemoryFormSessionAnalyticsStore extends FormSessionAnalyticsStore {

  import InMemoryFormSessionAnalyticsStore.*

  private final class MutableHttpSession(
    val sessionId          : String,
    val createdAtMs        : Long,
    var lastActivityAtMs   : Long,
    var maxInactiveSec     : Int,
    var usernameOpt        : Option[String],
    val activeFormUuids    : mutable.LinkedHashSet[String] = mutable.LinkedHashSet.empty,
    val touchedApps        : mutable.HashSet[String]       = mutable.HashSet.empty
  ) {
    def isExpiredAt(nowMs: Long): Boolean =
      maxInactiveSec > 0 && (nowMs - lastActivityAtMs) > (maxInactiveSec.toLong * 1000L + 60000L)

    def expiresInMs(nowMs: Long): Long =
      if (maxInactiveSec <= 0) -1L
      else math.max(0L, (lastActivityAtMs + maxInactiveSec.toLong * 1000L) - nowMs)
  }

  private final class MutableFormSession(
    val uuid               : String,
    var sessionIdOpt       : Option[String],
    val appForm            : AppForm,
    var version            : Int,
    val mode               : String,
    val isInteractiveEntry : Boolean,
    var usernameOpt        : Option[String],
    val openedAtMs         : Long,
    var lastActivityAtMs   : Long,
    var maxInactiveSec     : Int
  ) {
    var ajaxCount           : Int     = 0
    var errorCount          : Int     = 0
    var hasInteracted       : Boolean = false
    var hasSavedDraft       : Boolean = false
    var hasSubmitted        : Boolean = false
    var hasError            : Boolean = false
    var inHeap              : Boolean = true
    var lastPhaseChangeMs   : Long    = openedAtMs
    var accumulatedHeapMs   : Long    = 0L
    var accumulatedSerialMs : Long    = 0L
    var closedStateOpt      : Option[String] = None
    var closedAtMs          : Long    = -1L

    def transitionHeapState(nowInHeap: Boolean, atMs: Long): Unit =
      if (closedStateOpt.isEmpty && inHeap != nowInHeap) {
        val delta = math.max(0L, atMs - lastPhaseChangeMs)
        if (inHeap) accumulatedHeapMs += delta else accumulatedSerialMs += delta
        inHeap            = nowInHeap
        lastPhaseChangeMs = atMs
      }

    def close(finalState: String, atMs: Long): Unit =
      if (closedStateOpt.isEmpty) {
        val delta = math.max(0L, atMs - lastPhaseChangeMs)
        if (inHeap) accumulatedHeapMs += delta else accumulatedSerialMs += delta
        lastPhaseChangeMs = atMs
        closedStateOpt    = Some(finalState)
        closedAtMs        = atMs
      }

    def toRow(nowMs: Long, showUsernames: Boolean, httpSessionOpt: Option[MutableHttpSession]): FormSessionRow = {
      val (heapMs, serialMs) =
        if (closedStateOpt.isDefined) {
          (accumulatedHeapMs, accumulatedSerialMs)
        } else {
          val liveDelta = math.max(0L, nowMs - lastPhaseChangeMs)
          if (inHeap) (accumulatedHeapMs + liveDelta, accumulatedSerialMs)
          else (accumulatedHeapMs, accumulatedSerialMs + liveDelta)
        }

      val stateStr =
        closedStateOpt.getOrElse {
          if (hasSubmitted) "submitted"
          else if (! inHeap) "passivated"
          else "active-heap"
        }

      val effectiveLastHttp = httpSessionOpt.map(_.lastActivityAtMs).getOrElse(lastActivityAtMs)
      val effectiveMaxInact = httpSessionOpt.map(_.maxInactiveSec).filter(_ > 0).getOrElse(maxInactiveSec)
      val expiresMs =
        if (closedStateOpt.isDefined || effectiveMaxInact <= 0) -1L
        else math.max(0L, (effectiveLastHttp + effectiveMaxInact.toLong * 1000L) - nowMs)

      val cleanUserOpt = usernameOpt.flatMap(_.trimAllToOpt)
      val userDisplay = cleanUserOpt match {
        case Some(u) if showUsernames => u
        case Some(_)                  => "***"
        case None                     => ""
      }

      FormSessionRow(
        uuid             = uuid,
        appForm          = appForm,
        version          = version,
        mode             = mode,
        state            = stateStr,
        isActive         = closedStateOpt.isEmpty,
        userDisplay      = userDisplay,
        isAuthenticated  = cleanUserOpt.isDefined,
        openedAtMs       = openedAtMs,
        lastActivityAtMs = lastActivityAtMs,
        timeSpentMs      = math.max(0L, lastActivityAtMs - openedAtMs),
        ajaxCount        = ajaxCount,
        activeHeapMs     = heapMs,
        serializedMs     = serialMs,
        expiresInMs      = expiresMs,
        errorCount       = errorCount
      )
    }
  }

  private final class PerFormCounters {
    var opened           : Long = 0L
    var bounced          : Long = 0L
    var interacted       : Long = 0L
    var draftSaved       : Long = 0L
    var submitted        : Long = 0L
    var abandoned        : Long = 0L
    var withErrors       : Long = 0L
    var totalTimeSpentMs : Long = 0L
    var closedOrSubCount : Long = 0L

    val timings: mutable.Map[TimingCategory, LatencyHistogram] = mutable.HashMap.empty

    def histogram(cat: TimingCategory): LatencyHistogram =
      timings.getOrElseUpdate(cat, new LatencyHistogram)

    def mergeFrom(other: PerFormCounters): Unit = {
      opened           += other.opened
      bounced          += other.bounced
      interacted       += other.interacted
      draftSaved       += other.draftSaved
      submitted        += other.submitted
      abandoned        += other.abandoned
      withErrors       += other.withErrors
      totalTimeSpentMs += other.totalTimeSpentMs
      closedOrSubCount += other.closedOrSubCount
      other.timings.foreach { case (cat, hist) =>
        histogram(cat).mergeFrom(hist)
      }
    }
  }

  private final class TimeBucket(val bucketStartMs: Long) {
    val byForm = new mutable.LinkedHashMap[AppForm, PerFormCounters]()
    val unscopedTimings = new mutable.HashMap[TimingCategory, LatencyHistogram]()
    var maxConnectedSessions : Int = 0
    var maxActiveUsers       : Int = 0

    def countersFor(appForm: AppForm): PerFormCounters =
      byForm.get(appForm) match {
        case Some(c) => c
        case None =>
          if (byForm.size >= MaxTrackedFormsPerBucket) {
            // Bound per-bucket form cardinality under adversarial traffic
            byForm.head._2
          } else {
            val c = new PerFormCounters
            byForm.put(appForm, c)
            c
          }
      }

    def unscopedHistogram(cat: TimingCategory): LatencyHistogram =
      unscopedTimings.getOrElseUpdate(cat, new LatencyHistogram)

    def updateSessionGauges(connected: Int, activeUsers: Int): Unit = {
      if (connected > maxConnectedSessions) maxConnectedSessions = connected
      if (activeUsers > maxActiveUsers)     maxActiveUsers       = activeUsers
    }
  }

  private final class ResolutionRing(val window: AnalyticsWindow) {
    private val slots = new ju.LinkedHashMap[Long, TimeBucket](window.bucketCount + 4, 0.75f, false)

    def align(atMs: Long): Long =
      (atMs / window.bucketWidthMs) * window.bucketWidthMs

    def bucketFor(atMs: Long): TimeBucket = {
      val startMs = align(atMs)
      var b = slots.get(startMs)
      if (b eq null) {
        b = new TimeBucket(startMs)
        slots.put(startMs, b)
        while (slots.size() > window.bucketCount + 2) {
          val it = slots.keySet().iterator()
          if (it.hasNext) {
            it.next()
            it.remove()
          }
        }
      }
      b
    }

    def bucketsInRange(nowMs: Long): List[(Long, Option[TimeBucket])] = {
      val currentAligned = align(nowMs)
      val firstAligned   = currentAligned - (window.bucketCount - 1).toLong * window.bucketWidthMs
      (0 until window.bucketCount).map { i =>
        val ts = firstAligned + i.toLong * window.bucketWidthMs
        ts -> Option(slots.get(ts))
      }.toList
    }

    def clear(): Unit =
      slots.clear()
  }

  private val lock = new AnyRef

  private val httpSessions       = new mutable.LinkedHashMap[String, MutableHttpSession]()
  private val activeFormSessions = new mutable.LinkedHashMap[String, MutableFormSession]()
  private val recentClosedForms  = new ju.ArrayDeque[MutableFormSession](DefaultMaxRecentSessions + 8)
  private val discoveredForms    = new mutable.HashMap[String, mutable.HashSet[String]]()

  private val rings: Map[AnalyticsWindow, ResolutionRing] =
    AnalyticsWindow.All.map(w => w -> new ResolutionRing(w)).toMap

  private val listenerBridge: XFormsAnalyticsListener = new XFormsAnalyticsListener {

    def onHttpSessionCreated(sessionId: String, createdAtMs: Long, maxInactiveSec: Int): Unit =
      if (isEnabled && sessionId.nonAllBlank)
        recordHttpSessionCreated(sessionId, createdAtMs, maxInactiveSec)

    def onHttpSessionDestroyed(sessionId: String, destroyedAtMs: Long, documentUuids: List[String]): Unit =
      if (isEnabled)
        recordHttpSessionDestroyed(sessionId, destroyedAtMs, documentUuids)

    def onDocumentInitialized(
      doc            : XFormsContainingDocument,
      sessionIdOpt   : Option[String],
      usernameOpt    : Option[String],
      maxInactiveSec : Int,
      durationMs     : Long
    ): Unit =
      if (isEnabled) {
        val nowMs = System.currentTimeMillis()
        sessionIdOpt.filter(_.nonAllBlank).foreach { sid =>
          recordHttpSessionActivity(sid, usernameOpt, nowMs, maxInactiveSec)
        }
        extractFormRunnerDocInfo(doc).foreach { info =>
          recordFormSessionOpened(
            FormSessionOpenEvent(
              uuid               = doc.uuid,
              sessionIdOpt       = sessionIdOpt.filter(_.nonAllBlank),
              appForm            = info.appForm,
              version            = info.version,
              mode               = info.mode,
              isInteractiveEntry = info.isInteractiveEntry,
              usernameOpt        = usernameOpt.flatMap(_.trimAllToOpt),
              openedAtMs         = nowMs,
              maxInactiveSec     = maxInactiveSec,
              initDurationMs     = durationMs
            )
          )
        }
      }

    def onDocumentInitFailed(requestPath: String, durationMs: Long): Unit =
      if (isEnabled)
        recordFormLoadFailed(extractAppFormFromPath(requestPath), durationMs, System.currentTimeMillis())

    def onAjaxUpdateCompleted(
      doc            : XFormsContainingDocument,
      sessionIdOpt   : Option[String],
      usernameOpt    : Option[String],
      maxInactiveSec : Int,
      isInteraction  : Boolean,
      durationMs     : Long,
      hadServerError : Boolean
    ): Unit =
      if (isEnabled)
        recordFormInteraction(
          uuid           = doc.uuid,
          sessionIdOpt   = sessionIdOpt.filter(_.nonAllBlank),
          usernameOpt    = usernameOpt.flatMap(_.trimAllToOpt),
          maxInactiveSec = maxInactiveSec,
          isInteraction  = isInteraction,
          ajaxDurationMs = durationMs,
          hadServerError = hadServerError,
          atMs           = System.currentTimeMillis()
        )

    def onDocumentPassivated(uuid: String, atMs: Long): Unit =
      if (isEnabled)
        recordFormPassivationChange(uuid, passivated = true, atMs)

    def onDocumentActivatedFromStore(uuid: String, atMs: Long): Unit =
      if (isEnabled)
        recordFormPassivationChange(uuid, passivated = false, atMs)

    def onDocumentRemoved(uuid: String, reason: DocumentRemovalReason, atMs: Long): Unit =
      if (isEnabled) {
        val closeReason = reason match {
          case DocumentRemovalReason.Error            => SessionCloseReason.ServerError
          case DocumentRemovalReason.SessionOverflow  => SessionCloseReason.SessionOverflow
          case DocumentRemovalReason.SessionDestroyed => SessionCloseReason.SessionExpired
        }
        recordFormSessionClosed(uuid, closeReason, atMs)
      }

    def onSubmissionCompleted(
      docOpt     : Option[XFormsContainingDocument],
      targetUri  : String,
      category   : SubmissionCategory,
      durationMs : Long,
      succeeded  : Boolean
    ): Unit =
      if (isEnabled) {
        val timingCatOpt = category match {
          case SubmissionCategory.Persistence => Some(TimingCategory.PersistenceCall)
          case SubmissionCategory.Service     => Some(TimingCategory.ServiceCall)
          case SubmissionCategory.Other       => None
        }
        timingCatOpt.foreach { timingCat =>
          val extractedAppFormOpt = docOpt.flatMap(extractFormRunnerDocInfo).map(_.appForm)
          recordSubmissionTiming(
            uuidOpt    = docOpt.map(_.uuid),
            appFormOpt = extractedAppFormOpt,
            category   = timingCat,
            durationMs = durationMs,
            succeeded  = succeeded,
            atMs       = System.currentTimeMillis()
          )
        }
      }
  }

  def registerGlobalBridge(): Unit = {
    XFormsAnalyticsSupport.listenerOpt = Some(listenerBridge)
  }

  CacheSupport.addCloseListener(() => shutdown())

  private def currentPropertySetOpt =
    scala.util.Try(CoreCrossPlatformSupport.properties).toOption

  private def isEnabled: Boolean =
    currentPropertySetOpt.forall(_.getBoolean(EnableProperty, default = true))

  private def maxActiveSessions: Int =
    currentPropertySetOpt
      .map(_.getInteger(MaxActiveSessionsProperty, DefaultMaxActiveSessions).intValue())
      .map(v => math.max(100, v))
      .getOrElse(DefaultMaxActiveSessions)

  private def maxRecentSessions: Int =
    currentPropertySetOpt
      .map(_.getInteger(MaxRecentSessionsProperty, DefaultMaxRecentSessions).intValue())
      .map(v => math.max(20, v))
      .getOrElse(DefaultMaxRecentSessions)

  private def withAllBucketsAt(atMs: Long)(f: TimeBucket => Unit): Unit =
    rings.valuesIterator.foreach { ring =>
      val b = ring.bucketFor(atMs)
      f(b)
    }

  private def refreshGaugeSnapshotsUnlocked(atMs: Long): Unit = {
    pruneStaleHttpSessionsUnlocked(atMs)
    val connected = httpSessions.size
    val activeUsers = computeActiveUsersUnlocked()._1
    withAllBucketsAt(atMs)(_.updateSessionGauges(connected, activeUsers))
  }

  private def computeActiveUsersUnlocked(): (Int, Int, Int) = {
    val authUsers = mutable.HashSet.empty[String]
    var anonCount = 0
    val anonSeenSessions = mutable.HashSet.empty[String]
    activeFormSessions.valuesIterator.foreach { fs =>
      fs.usernameOpt.flatMap(_.trimAllToOpt) match {
        case Some(u) =>
          authUsers += u
        case None =>
          val key = fs.sessionIdOpt.getOrElse(fs.uuid)
          if (anonSeenSessions.add(key))
            anonCount += 1
      }
    }
    (authUsers.size + anonCount, authUsers.size, anonCount)
  }

  private def pruneStaleHttpSessionsUnlocked(nowMs: Long): Unit = {
    val staleIds =
      httpSessions.valuesIterator
        .filter(s => s.activeFormUuids.isEmpty && s.isExpiredAt(nowMs))
        .map(_.sessionId)
        .toList
    staleIds.foreach(httpSessions.remove)

    val cap = maxActiveSessions
    while (httpSessions.size > cap) {
      httpSessions.headOption.foreach { case (oldestId, _) => httpSessions.remove(oldestId) }
    }
  }

  def recordHttpSessionCreated(sessionId: String, createdAtMs: Long, maxInactiveSec: Int): Unit =
    if (sessionId.nonAllBlank) {
      lock.synchronized {
        if (! httpSessions.contains(sessionId)) {
          httpSessions.put(
            sessionId,
            new MutableHttpSession(
              sessionId        = sessionId,
              createdAtMs      = createdAtMs,
              lastActivityAtMs = createdAtMs,
              maxInactiveSec   = maxInactiveSec,
              usernameOpt      = None
            )
          )
        }
        refreshGaugeSnapshotsUnlocked(createdAtMs)
      }
    }

  def recordHttpSessionActivity(sessionId: String, usernameOpt: Option[String], atMs: Long, maxInactiveSec: Int): Unit =
    if (sessionId.nonAllBlank) {
      lock.synchronized {
        val entry = httpSessions.getOrElseUpdate(
          sessionId,
          new MutableHttpSession(
            sessionId        = sessionId,
            createdAtMs      = atMs,
            lastActivityAtMs = atMs,
            maxInactiveSec   = maxInactiveSec,
            usernameOpt      = usernameOpt.flatMap(_.trimAllToOpt)
          )
        )
        entry.lastActivityAtMs = math.max(entry.lastActivityAtMs, atMs)
        if (maxInactiveSec > 0)
          entry.maxInactiveSec = maxInactiveSec
        usernameOpt.flatMap(_.trimAllToOpt).foreach(u => entry.usernameOpt = Some(u))
      }
    }

  def recordHttpSessionDestroyed(sessionId: String, destroyedAtMs: Long, documentUuids: List[String]): Unit =
    lock.synchronized {
      val trackedUuids =
        httpSessions.remove(sessionId).map(_.activeFormUuids.toList).getOrElse(Nil)
      val allUuids = (documentUuids ++ trackedUuids).distinct
      allUuids.foreach { uuid =>
        closeFormSessionUnlocked(uuid, SessionCloseReason.SessionExpired, destroyedAtMs)
      }
      refreshGaugeSnapshotsUnlocked(destroyedAtMs)
    }

  def recordFormSessionOpened(event: FormSessionOpenEvent): Unit =
    lock.synchronized {
      discoveredForms.getOrElseUpdate(event.appForm.app, mutable.HashSet.empty) += event.appForm.form

      val session = new MutableFormSession(
        uuid               = event.uuid,
        sessionIdOpt       = event.sessionIdOpt,
        appForm            = event.appForm,
        version            = event.version,
        mode               = event.mode,
        isInteractiveEntry = event.isInteractiveEntry,
        usernameOpt        = event.usernameOpt,
        openedAtMs         = event.openedAtMs,
        lastActivityAtMs   = event.openedAtMs,
        maxInactiveSec     = event.maxInactiveSec
      )

      activeFormSessions.put(event.uuid, session)
      val cap = maxActiveSessions
      while (activeFormSessions.size > cap) {
        activeFormSessions.headOption.foreach { case (oldestUuid, _) =>
          closeFormSessionUnlocked(oldestUuid, SessionCloseReason.SessionOverflow, event.openedAtMs)
        }
      }

      event.sessionIdOpt.filter(_.nonAllBlank).foreach { sid =>
        val hs = httpSessions.getOrElseUpdate(
          sid,
          new MutableHttpSession(
            sessionId        = sid,
            createdAtMs      = event.openedAtMs,
            lastActivityAtMs = event.openedAtMs,
            maxInactiveSec   = event.maxInactiveSec,
            usernameOpt      = event.usernameOpt
          )
        )
        hs.lastActivityAtMs = math.max(hs.lastActivityAtMs, event.openedAtMs)
        hs.activeFormUuids += event.uuid
        hs.touchedApps     += event.appForm.app
        event.usernameOpt.foreach(u => hs.usernameOpt = Some(u))
      }

      withAllBucketsAt(event.openedAtMs) { b =>
        val c = b.countersFor(event.appForm)
        if (event.isInteractiveEntry)
          c.opened += 1L
        c.histogram(TimingCategory.FormLoad).record(event.initDurationMs, succeeded = true)
      }
      refreshGaugeSnapshotsUnlocked(event.openedAtMs)
    }

  def recordFormLoadFailed(appFormOpt: Option[AppForm], durationMs: Long, atMs: Long): Unit =
    lock.synchronized {
      withAllBucketsAt(atMs) { b =>
        appFormOpt match {
          case Some(appForm) =>
            discoveredForms.getOrElseUpdate(appForm.app, mutable.HashSet.empty) += appForm.form
            val c = b.countersFor(appForm)
            c.withErrors += 1L
            c.histogram(TimingCategory.FormLoad).record(durationMs, succeeded = false)
          case None =>
            b.unscopedHistogram(TimingCategory.FormLoad).record(durationMs, succeeded = false)
        }
      }
    }

  def recordFormInteraction(
    uuid           : String,
    sessionIdOpt   : Option[String],
    usernameOpt    : Option[String],
    maxInactiveSec : Int,
    isInteraction  : Boolean,
    ajaxDurationMs : Long,
    hadServerError : Boolean,
    atMs           : Long
  ): Unit =
    lock.synchronized {
      sessionIdOpt.filter(_.nonAllBlank).foreach { sid =>
        recordHttpSessionActivity(sid, usernameOpt, atMs, maxInactiveSec)
      }
      activeFormSessions.get(uuid).foreach { fs =>
        fs.transitionHeapState(nowInHeap = true, atMs = atMs)
        fs.lastActivityAtMs = math.max(fs.lastActivityAtMs, atMs)
        if (maxInactiveSec > 0)
          fs.maxInactiveSec = maxInactiveSec
        usernameOpt.flatMap(_.trimAllToOpt).foreach(u => fs.usernameOpt = Some(u))

        val firstInteraction =
          if (isInteraction) {
            fs.ajaxCount += 1
            if (! fs.hasInteracted && fs.isInteractiveEntry) {
              fs.hasInteracted = true
              true
            } else {
              false
            }
          } else {
            false
          }

        val firstError =
          if (hadServerError) {
            fs.errorCount += 1
            if (! fs.hasError) {
              fs.hasError = true
              true
            } else {
              false
            }
          } else {
            false
          }

        withAllBucketsAt(atMs) { b =>
          val c = b.countersFor(fs.appForm)
          if (firstInteraction)
            c.interacted += 1L
          if (firstError)
            c.withErrors += 1L
          c.histogram(TimingCategory.AjaxUpdate).record(ajaxDurationMs, succeeded = ! hadServerError)
        }
      }
      refreshGaugeSnapshotsUnlocked(atMs)
    }

  def recordFormSave(
    uuid       : String,
    appForm    : AppForm,
    version    : Int,
    isDraft    : Boolean,
    durationMs : Long,
    succeeded  : Boolean,
    atMs       : Long
  ): Unit =
    lock.synchronized {
      discoveredForms.getOrElseUpdate(appForm.app, mutable.HashSet.empty) += appForm.form

      var firstDraft     = false
      var firstSubmitted = false
      var firstError     = false
      var timeSpentOpt   : Option[Long] = None

      activeFormSessions.get(uuid) match {
        case Some(fs) =>
          fs.version          = version
          fs.lastActivityAtMs = math.max(fs.lastActivityAtMs, atMs)
          if (! fs.hasInteracted && fs.isInteractiveEntry) {
            fs.hasInteracted = true
          }
          if (succeeded) {
            if (isDraft) {
              if (! fs.hasSavedDraft) {
                fs.hasSavedDraft = true
                firstDraft = true
              }
            } else {
              if (! fs.hasSubmitted) {
                fs.hasSubmitted = true
                firstSubmitted  = true
                timeSpentOpt    = Some(math.max(0L, atMs - fs.openedAtMs))
              }
            }
          } else {
            fs.errorCount += 1
            if (! fs.hasError) {
              fs.hasError = true
              firstError  = true
            }
          }
        case None =>
          // Background service save or untracked session
          if (succeeded) {
            if (isDraft) firstDraft = true else firstSubmitted = true
          } else {
            firstError = true
          }
      }

      withAllBucketsAt(atMs) { b =>
        val c = b.countersFor(appForm)
        if (firstDraft)
          c.draftSaved += 1L
        if (firstSubmitted)
          c.submitted += 1L
        if (firstError)
          c.withErrors += 1L
        timeSpentOpt.foreach { spent =>
          c.totalTimeSpentMs += spent
          c.closedOrSubCount += 1L
        }
        c.histogram(TimingCategory.SaveAction).record(durationMs, succeeded = succeeded)
      }
    }

  def recordFormProcessOutcome(
    uuid       : String,
    appForm    : AppForm,
    outcome    : ProcessOutcome,
    durationMs : Long,
    atMs       : Long
  ): Unit =
    lock.synchronized {
      discoveredForms.getOrElseUpdate(appForm.app, mutable.HashSet.empty) += appForm.form

      var firstSubmitted = false
      var firstError     = false
      var timeSpentOpt   : Option[Long] = None

      val isErrorOutcome = outcome == ProcessOutcome.ValidationError || outcome == ProcessOutcome.ProcessError

      activeFormSessions.get(uuid).foreach { fs =>
        fs.lastActivityAtMs = math.max(fs.lastActivityAtMs, atMs)
        outcome match {
          case ProcessOutcome.SubmittedSend =>
            if (! fs.hasSubmitted) {
              fs.hasSubmitted = true
              firstSubmitted  = true
              timeSpentOpt    = Some(math.max(0L, atMs - fs.openedAtMs))
            }
          case ProcessOutcome.ValidationError | ProcessOutcome.ProcessError =>
            fs.errorCount += 1
            if (! fs.hasError) {
              fs.hasError = true
              firstError  = true
            }
          case _ =>
            ()
        }
      }

      withAllBucketsAt(atMs) { b =>
        val c = b.countersFor(appForm)
        if (firstSubmitted)
          c.submitted += 1L
        if (firstError)
          c.withErrors += 1L
        timeSpentOpt.foreach { spent =>
          c.totalTimeSpentMs += spent
          c.closedOrSubCount += 1L
        }
        if (outcome == ProcessOutcome.SubmittedSend || outcome == ProcessOutcome.EmailSent || outcome == ProcessOutcome.PdfCreated || outcome == ProcessOutcome.ProcessError) {
          c.histogram(TimingCategory.SendEmailPdf).record(durationMs, succeeded = ! isErrorOutcome)
        }
      }
    }

  def recordFormPassivationChange(uuid: String, passivated: Boolean, atMs: Long): Unit =
    lock.synchronized {
      activeFormSessions.get(uuid).foreach { fs =>
        fs.transitionHeapState(nowInHeap = ! passivated, atMs = atMs)
      }
    }

  def recordFormSessionClosed(uuid: String, reason: SessionCloseReason, atMs: Long): Unit =
    lock.synchronized {
      closeFormSessionUnlocked(uuid, reason, atMs)
      refreshGaugeSnapshotsUnlocked(atMs)
    }

  private def closeFormSessionUnlocked(uuid: String, reason: SessionCloseReason, atMs: Long): Unit =
    activeFormSessions.remove(uuid).foreach { fs =>
      fs.sessionIdOpt.flatMap(httpSessions.get).foreach(_.activeFormUuids -= uuid)

      val wasBounced   = fs.isInteractiveEntry && ! fs.hasInteracted && ! fs.hasSavedDraft && ! fs.hasSubmitted
      val wasAbandoned = fs.isInteractiveEntry && ! fs.hasSavedDraft && ! fs.hasSubmitted

      val finalState =
        if (reason == SessionCloseReason.ServerError) "error"
        else if (fs.hasSubmitted) "submitted"
        else if (fs.hasSavedDraft) "draft-saved"
        else if (wasBounced) "bounced"
        else if (wasAbandoned) "abandoned"
        else "closed"

      fs.close(finalState, atMs)

      recentClosedForms.addFirst(fs)
      val cap = maxRecentSessions
      while (recentClosedForms.size() > cap)
        recentClosedForms.removeLast()

      withAllBucketsAt(atMs) { b =>
        val c = b.countersFor(fs.appForm)
        if (wasBounced)
          c.bounced += 1L
        if (wasAbandoned)
          c.abandoned += 1L
        if (! fs.hasSubmitted && fs.isInteractiveEntry) {
          c.totalTimeSpentMs += math.max(0L, fs.lastActivityAtMs - fs.openedAtMs)
          c.closedOrSubCount += 1L
        }
      }
    }

  def recordSubmissionTiming(
    uuidOpt    : Option[String],
    appFormOpt : Option[AppForm],
    category   : TimingCategory,
    durationMs : Long,
    succeeded  : Boolean,
    atMs       : Long
  ): Unit =
    lock.synchronized {
      val resolvedAppFormOpt =
        appFormOpt.orElse(uuidOpt.flatMap(activeFormSessions.get).map(_.appForm))

      withAllBucketsAt(atMs) { b =>
        resolvedAppFormOpt match {
          case Some(appForm) =>
            discoveredForms.getOrElseUpdate(appForm.app, mutable.HashSet.empty) += appForm.form
            b.countersFor(appForm).histogram(category).record(durationMs, succeeded)
          case None =>
            b.unscopedHistogram(category).record(durationMs, succeeded)
        }
      }
    }

  def snapshot(query: AnalyticsQuery, nowMs: Long): SessionAnalyticsSnapshot =
    lock.synchronized {
      pruneStaleHttpSessionsUnlocked(nowMs)

      val matchingActiveForms =
        activeFormSessions.valuesIterator
          .filter(fs => query.matchesAppForm(fs.appForm))
          .toList

      val activeHeapCount   = matchingActiveForms.count(_.inHeap)
      val passivatedCount   = matchingActiveForms.size - activeHeapCount
      val recentActiveCount = matchingActiveForms.count(fs => (nowMs - fs.lastActivityAtMs) <= ActiveFormRecencyMs)

      // Scope HTTP sessions: global scope with no app/form filter sees all HTTP sessions;
      // app/form-scoped users see HTTP sessions that interacted with at least one allowed app/form.
      val isUnfilteredGlobal = query.scope.hasGlobalAccess && query.appFilterOpt.isEmpty && query.formFilterOpt.isEmpty
      val matchingSessionIds = matchingActiveForms.flatMap(_.sessionIdOpt).toSet

      val visibleHttpSessions =
        httpSessions.valuesIterator.filter { hs =>
          if (isUnfilteredGlobal)
            true
          else
            matchingSessionIds.contains(hs.sessionId) ||
              (query.formFilterOpt.isEmpty && hs.touchedApps.exists(app => query.scope.allowsApp(app) && query.appFilterOpt.forall(_ == app)))
        }.toList

      val connectedHttpCount  = visibleHttpSessions.size
      val recentHttpCount     = visibleHttpSessions.count(hs => (nowMs - hs.lastActivityAtMs) <= ActiveHttpRecencyMs)

      val authUserSet = mutable.HashSet.empty[String]
      var anonUserCnt = 0
      val anonSeen    = mutable.HashSet.empty[String]
      matchingActiveForms.foreach { fs =>
        fs.usernameOpt.flatMap(_.trimAllToOpt) match {
          case Some(u) => authUserSet += u
          case None    =>
            val k = fs.sessionIdOpt.getOrElse(fs.uuid)
            if (anonSeen.add(k)) anonUserCnt += 1
        }
      }

      val activeUserRows =
        visibleHttpSessions
          .map { hs =>
            val cleanUserOpt = hs.usernameOpt.flatMap(_.trimAllToOpt)
            val display = cleanUserOpt match {
              case Some(u) if query.showUsernames => u
              case Some(_)                        => "***"
              case None                           => ""
            }
            val scopedActiveFormsCount =
              hs.activeFormUuids.count(uuid => activeFormSessions.get(uuid).exists(fs => query.matchesAppForm(fs.appForm)))

            ActiveUserSessionRow(
              sessionIdShort   = hs.sessionId.take(12),
              userDisplay      = display,
              isAuthenticated  = cleanUserOpt.isDefined,
              activeFormsCount = scopedActiveFormsCount,
              createdAtMs      = hs.createdAtMs,
              lastActivityAtMs = hs.lastActivityAtMs,
              sessionAgeMs     = math.max(0L, nowMs - hs.createdAtMs),
              expiresInMs      = hs.expiresInMs(nowMs)
            )
          }
          .sortBy(r => (-r.activeFormsCount, -r.lastActivityAtMs))
          .take(200)

      val matchingRecentClosed =
        recentClosedForms.asScala.iterator
          .filter(fs => query.matchesAppForm(fs.appForm))
          .toList

      val sessionRows =
        (matchingActiveForms.sortBy(s => -s.lastActivityAtMs) ++ matchingRecentClosed)
          .take(250)
          .map { fs =>
            fs.toRow(nowMs, query.showUsernames, fs.sessionIdOpt.flatMap(httpSessions.get))
          }

      val ring       = rings(query.window)
      val rawBuckets = ring.bucketsInRange(nowMs)

      val mergedByForm    = new mutable.HashMap[AppForm, PerFormCounters]()
      val categoryHistMap = TimingCategory.All.map(c => c -> new LatencyHistogram).toMap

      val seriesPoints =
        rawBuckets.map { case (tsMs, bucketOpt) =>
          var bOpened     = 0L
          var bInteracted = 0L
          var bDraft      = 0L
          var bSubmitted  = 0L
          var bAbandoned  = 0L
          var bBounced    = 0L
          var bErrors     = 0L

          val loadHist = new LatencyHistogram
          val ajaxHist = new LatencyHistogram
          val saveHist = new LatencyHistogram
          val svcHist  = new LatencyHistogram

          bucketOpt.foreach { b =>
            b.byForm.foreach { case (appForm, c) =>
              if (query.matchesAppForm(appForm)) {
                mergedByForm.getOrElseUpdate(appForm, new PerFormCounters).mergeFrom(c)

                bOpened     += c.opened
                bInteracted += c.interacted
                bDraft      += c.draftSaved
                bSubmitted  += c.submitted
                bAbandoned  += c.abandoned
                bBounced    += c.bounced
                bErrors     += c.withErrors

                c.timings.foreach { case (cat, h) =>
                  categoryHistMap(cat).mergeFrom(h)
                  cat match {
                    case TimingCategory.FormLoad        => loadHist.mergeFrom(h)
                    case TimingCategory.AjaxUpdate      => ajaxHist.mergeFrom(h)
                    case TimingCategory.SaveAction      => saveHist.mergeFrom(h)
                    case TimingCategory.ServiceCall     => svcHist.mergeFrom(h)
                    case TimingCategory.SendEmailPdf    => ()
                    case TimingCategory.PersistenceCall => ()
                  }
                }
              }
            }
            if (isUnfilteredGlobal) {
              b.unscopedTimings.foreach { case (cat, h) =>
                categoryHistMap(cat).mergeFrom(h)
                if (cat == TimingCategory.FormLoad) loadHist.mergeFrom(h)
                else if (cat == TimingCategory.ServiceCall) svcHist.mergeFrom(h)
              }
            }
          }

          SessionTimeBucketPoint(
            timestampMs       = tsMs,
            opened            = bOpened,
            interacted        = bInteracted,
            draftSaved        = bDraft,
            submitted         = bSubmitted,
            abandoned         = bAbandoned,
            bounced           = bBounced,
            withErrors        = bErrors,
            connectedSessions = if (isUnfilteredGlobal) bucketOpt.map(_.maxConnectedSessions).getOrElse(0) else 0,
            activeUsers       = if (isUnfilteredGlobal) bucketOpt.map(_.maxActiveUsers).getOrElse(0)       else 0,
            avgFormLoadMs     = loadHist.toSummary.avgMs,
            avgAjaxMs         = ajaxHist.toSummary.avgMs,
            avgSaveMs         = saveHist.toSummary.avgMs,
            avgServiceMs      = svcHist.toSummary.avgMs
          )
        }

      var wOpened     = 0L
      var wBounced    = 0L
      var wInteracted = 0L
      var wDraft      = 0L
      var wSubmitted  = 0L
      var wAbandoned  = 0L
      var wErrors     = 0L

      val funnelRows =
        mergedByForm.toList
          .map { case (appForm, c) =>
            wOpened     += c.opened
            wBounced    += c.bounced
            wInteracted += c.interacted
            wDraft      += c.draftSaved
            wSubmitted  += c.submitted
            wAbandoned  += c.abandoned
            wErrors     += c.withErrors

            val rate = if (c.opened > 0L) math.min(100.0, (c.submitted.toDouble / c.opened.toDouble) * 100.0) else 0.0
            val avgSpent = if (c.closedOrSubCount > 0L) c.totalTimeSpentMs / c.closedOrSubCount else 0L

            FormFunnelRow(
              appForm        = appForm,
              opened         = c.opened,
              bounced        = c.bounced,
              interacted     = c.interacted,
              draftSaved     = c.draftSaved,
              submitted      = c.submitted,
              abandoned      = c.abandoned,
              withErrors     = c.withErrors,
              completionRate = rate,
              avgTimeSpentMs = avgSpent
            )
          }
          .filter(r => r.opened > 0L || r.submitted > 0L || r.draftSaved > 0L || r.withErrors > 0L)
          .sortBy(r => (-r.opened, r.appForm.app, r.appForm.form))

      val formTimingRows =
        mergedByForm.toList
          .map { case (appForm, c) =>
            FormTimingRow(
              appForm      = appForm,
              loads        = c.timings.get(TimingCategory.FormLoad).map(_.toSummary).getOrElse(TimingSummary.Empty),
              ajaxUpdates  = c.timings.get(TimingCategory.AjaxUpdate).map(_.toSummary).getOrElse(TimingSummary.Empty),
              saves        = c.timings.get(TimingCategory.SaveAction).map(_.toSummary).getOrElse(TimingSummary.Empty),
              sends        = c.timings.get(TimingCategory.SendEmailPdf).map(_.toSummary).getOrElse(TimingSummary.Empty),
              serviceCalls = c.timings.get(TimingCategory.ServiceCall).map(_.toSummary).getOrElse(TimingSummary.Empty)
            )
          }
          .filter(r => r.loads.count > 0L || r.ajaxUpdates.count > 0L || r.saves.count > 0L || r.sends.count > 0L || r.serviceCalls.count > 0L)
          .sortBy(r => (-r.loads.avgMs - r.saves.avgMs, r.appForm.app, r.appForm.form))

      val scopedApps =
        discoveredForms.keysIterator.filter(query.scope.allowsApp).toSet

      val scopedFormsByApp =
        discoveredForms.iterator.flatMap { case (app, forms) =>
          val allowed = forms.filter(f => query.scope.allowsAppForm(AppForm(app, f))).toSet
          Option.when(allowed.nonEmpty)(app -> allowed)
        }.toMap

      val windowCompletionRate =
        if (wOpened > 0L) math.min(100.0, (wSubmitted.toDouble / wOpened.toDouble) * 100.0) else 0.0

      SessionAnalyticsSnapshot(
        activeFormSessionsCount     = matchingActiveForms.size,
        activeInHeapCount           = activeHeapCount,
        passivatedCount             = passivatedCount,
        recentlyActiveSessionsCount = recentActiveCount,
        connectedHttpSessionsCount  = connectedHttpCount,
        recentlyActiveHttpCount     = recentHttpCount,
        activeUsersCount            = authUserSet.size + anonUserCnt,
        authenticatedActiveUsers    = authUserSet.size,
        anonymousActiveUsers        = anonUserCnt,
        windowOpened                = wOpened,
        windowBounced               = wBounced,
        windowInteracted            = wInteracted,
        windowDraftSaved            = wDraft,
        windowSubmitted             = wSubmitted,
        windowAbandoned             = wAbandoned,
        windowWithErrors            = wErrors,
        windowCompletionRate        = windowCompletionRate,
        funnelByForm                = funnelRows,
        sessionRows                 = sessionRows,
        activeUserSessions          = activeUserRows,
        categoryTimings             = categoryHistMap.view.mapValues(_.toSummary).toMap,
        formTimings                 = formTimingRows,
        timeSeries                  = seriesPoints,
        discoveredApps              = scopedApps,
        discoveredFormsByApp        = scopedFormsByApp
      )
    }

  def reset(): Unit =
    lock.synchronized {
      recentClosedForms.clear()
      rings.valuesIterator.foreach(_.clear())
    }

  def shutdown(): Unit =
    lock.synchronized {
      if (XFormsAnalyticsSupport.listenerOpt.contains(listenerBridge))
        XFormsAnalyticsSupport.listenerOpt = None
      httpSessions.clear()
      activeFormSessions.clear()
      recentClosedForms.clear()
      discoveredForms.clear()
      rings.valuesIterator.foreach(_.clear())
    }
}
