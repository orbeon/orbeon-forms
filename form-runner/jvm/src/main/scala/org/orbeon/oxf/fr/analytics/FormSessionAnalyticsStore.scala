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

import org.orbeon.oxf.fr.AppForm


sealed abstract class AnalyticsWindow(
  val code          : String,
  val durationMs    : Long,
  val bucketWidthMs : Long,
  val bucketCount   : Int
)

object AnalyticsWindow {
  case object Window1h  extends AnalyticsWindow("1h",  60L * 60L * 1000L,            60L * 1000L,             60)
  case object Window24h extends AnalyticsWindow("24h", 24L * 60L * 60L * 1000L,      15L * 60L * 1000L,       96)
  case object Window7d  extends AnalyticsWindow("7d",  7L * 24L * 60L * 60L * 1000L, 60L * 60L * 1000L,       168)
  case object Window30d extends AnalyticsWindow("30d", 30L * 24L * 60L * 60L * 1000L, 6L * 60L * 60L * 1000L, 120)

  val All: List[AnalyticsWindow] = List(Window1h, Window24h, Window7d, Window30d)

  def fromString(raw: String): AnalyticsWindow =
    raw match {
      case "1h"  => Window1h
      case "7d"  => Window7d
      case "30d" => Window30d
      case _     => Window24h
    }
}

sealed abstract class SessionCloseReason(val code: String)
object SessionCloseReason {
  case object SessionExpired  extends SessionCloseReason("expired")
  case object SessionOverflow extends SessionCloseReason("evicted")
  case object ServerError     extends SessionCloseReason("error")
}

sealed abstract class ProcessOutcome(val code: String)
object ProcessOutcome {
  case object SavedDraft      extends ProcessOutcome("saved-draft")
  case object SavedFinal      extends ProcessOutcome("saved-final")
  case object SubmittedSend   extends ProcessOutcome("submitted-send")
  case object ValidationError extends ProcessOutcome("validation-error")
  case object ProcessError    extends ProcessOutcome("process-error")
  case object EmailSent       extends ProcessOutcome("email-sent")
  case object PdfCreated      extends ProcessOutcome("pdf-created")
}

sealed abstract class TimingCategory(val key: String)
object TimingCategory {
  case object FormLoad        extends TimingCategory("form-load")
  case object AjaxUpdate      extends TimingCategory("ajax-update")
  case object SaveAction      extends TimingCategory("save-action")
  case object SendEmailPdf    extends TimingCategory("send-process")
  case object ServiceCall     extends TimingCategory("service-call")
  case object PersistenceCall extends TimingCategory("persistence-call")

  val All: List[TimingCategory] = List(
    FormLoad,
    AjaxUpdate,
    SaveAction,
    SendEmailPdf,
    ServiceCall,
    PersistenceCall
  )
}

final case class FormSessionOpenEvent(
  uuid               : String,
  sessionIdOpt       : Option[String],
  appForm            : AppForm,
  version            : Int,
  mode               : String,
  isInteractiveEntry : Boolean,
  usernameOpt        : Option[String],
  openedAtMs         : Long,
  maxInactiveSec     : Int,
  initDurationMs     : Long
)

final case class AnalyticsQuery(
  scope         : AnalyticsScope,
  appFilterOpt  : Option[String],
  formFilterOpt : Option[String],
  window        : AnalyticsWindow,
  showUsernames : Boolean
) {
  def matchesAppForm(appForm: AppForm): Boolean =
    scope.allowsAppForm(appForm) &&
      appFilterOpt.forall(_ == appForm.app) &&
      formFilterOpt.forall(_ == appForm.form)
}

final case class TimingSummary(
  count      : Long,
  totalMs    : Long,
  avgMs      : Double,
  p50Ms      : Long,
  p95Ms      : Long,
  maxMs      : Long,
  errorCount : Long
)

object TimingSummary {
  val Empty: TimingSummary = TimingSummary(0L, 0L, 0.0, 0L, 0L, 0L, 0L)
}

final case class FormFunnelRow(
  appForm         : AppForm,
  opened          : Long,
  bounced         : Long,
  interacted      : Long,
  draftSaved      : Long,
  submitted       : Long,
  abandoned       : Long,
  withErrors      : Long,
  completionRate  : Double,
  avgTimeSpentMs  : Long
)

final case class FormSessionRow(
  uuid             : String,
  appForm          : AppForm,
  version          : Int,
  mode             : String,
  state            : String, // "active-heap" | "passivated" | "submitted" | "draft-saved" | "abandoned" | "bounced" | "error" | "closed"
  isActive         : Boolean,
  userDisplay      : String,
  isAuthenticated  : Boolean,
  openedAtMs       : Long,
  lastActivityAtMs : Long,
  timeSpentMs      : Long,
  ajaxCount        : Int,
  activeHeapMs     : Long,
  serializedMs     : Long,
  expiresInMs      : Long, // -1 if closed or unknown
  errorCount       : Int
)

final case class ActiveUserSessionRow(
  sessionIdShort   : String,
  userDisplay      : String,
  isAuthenticated  : Boolean,
  activeFormsCount : Int,
  createdAtMs      : Long,
  lastActivityAtMs : Long,
  sessionAgeMs     : Long,
  expiresInMs      : Long
)

final case class FormTimingRow(
  appForm      : AppForm,
  loads        : TimingSummary,
  ajaxUpdates  : TimingSummary,
  saves        : TimingSummary,
  sends        : TimingSummary,
  serviceCalls : TimingSummary
)

final case class SessionTimeBucketPoint(
  timestampMs       : Long,
  opened            : Long,
  interacted        : Long,
  draftSaved        : Long,
  submitted         : Long,
  abandoned         : Long,
  bounced           : Long,
  withErrors        : Long,
  connectedSessions : Int,
  activeUsers       : Int,
  avgFormLoadMs     : Double,
  avgAjaxMs         : Double,
  avgSaveMs         : Double,
  avgServiceMs      : Double
)

final case class SessionAnalyticsSnapshot(
  activeFormSessionsCount     : Int,
  activeInHeapCount           : Int,
  passivatedCount             : Int,
  recentlyActiveSessionsCount : Int, // active in last 5 minutes
  connectedHttpSessionsCount  : Int,
  recentlyActiveHttpCount     : Int, // HTTP session active in last 15 minutes
  activeUsersCount            : Int,
  authenticatedActiveUsers    : Int,
  anonymousActiveUsers        : Int,
  windowOpened                : Long,
  windowBounced               : Long,
  windowInteracted            : Long,
  windowDraftSaved            : Long,
  windowSubmitted             : Long,
  windowAbandoned             : Long,
  windowWithErrors            : Long,
  windowCompletionRate        : Double,
  funnelByForm                : List[FormFunnelRow],
  sessionRows                 : List[FormSessionRow],
  activeUserSessions          : List[ActiveUserSessionRow],
  categoryTimings             : Map[TimingCategory, TimingSummary],
  formTimings                 : List[FormTimingRow],
  timeSeries                  : List[SessionTimeBucketPoint],
  discoveredApps              : Set[String],
  discoveredFormsByApp        : Map[String, Set[String]]
)

/**
 * Backend-agnostic SPI for Form Runner session lifecycle and timing analytics.
 *
 * Initial implementation (`InMemoryFormSessionAnalyticsStore`) keeps bounded state in JVM memory;
 * a future `RelationalFormSessionAnalyticsStore` can implement this trait to persist session funnels
 * and timings across restarts and cluster nodes.
 */
trait FormSessionAnalyticsStore {

  def recordHttpSessionCreated(sessionId: String, createdAtMs: Long, maxInactiveSec: Int): Unit
  def recordHttpSessionActivity(sessionId: String, usernameOpt: Option[String], atMs: Long, maxInactiveSec: Int): Unit
  def recordHttpSessionDestroyed(sessionId: String, destroyedAtMs: Long, documentUuids: List[String]): Unit

  def recordFormSessionOpened(event: FormSessionOpenEvent): Unit
  def recordFormLoadFailed(appFormOpt: Option[AppForm], durationMs: Long, atMs: Long): Unit
  def recordFormInteraction(
    uuid            : String,
    sessionIdOpt    : Option[String],
    usernameOpt     : Option[String],
    maxInactiveSec  : Int,
    isInteraction   : Boolean,
    ajaxDurationMs  : Long,
    hadServerError  : Boolean,
    atMs            : Long
  ): Unit

  def recordFormSave(
    uuid       : String,
    appForm    : AppForm,
    version    : Int,
    isDraft    : Boolean,
    durationMs : Long,
    succeeded  : Boolean,
    atMs       : Long
  ): Unit

  def recordFormProcessOutcome(
    uuid       : String,
    appForm    : AppForm,
    outcome    : ProcessOutcome,
    durationMs : Long,
    atMs       : Long
  ): Unit

  def recordFormPassivationChange(uuid: String, passivated: Boolean, atMs: Long): Unit
  def recordFormSessionClosed(uuid: String, reason: SessionCloseReason, atMs: Long): Unit

  def recordSubmissionTiming(
    uuidOpt    : Option[String],
    appFormOpt : Option[AppForm],
    category   : TimingCategory,
    durationMs : Long,
    succeeded  : Boolean,
    atMs       : Long
  ): Unit

  def snapshot(query: AnalyticsQuery, nowMs: Long): SessionAnalyticsSnapshot
  def reset(): Unit
  def shutdown(): Unit
}
