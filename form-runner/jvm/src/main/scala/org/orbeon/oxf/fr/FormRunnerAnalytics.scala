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
import io.circe.syntax.*
import org.orbeon.dom
import org.orbeon.dom.saxon.DocumentWrapper
import org.orbeon.oxf.fr.analytics.*
import org.orbeon.oxf.util.StaticXPath.DocumentNodeInfoType
import org.orbeon.oxf.util.StringUtils.*
import org.orbeon.oxf.util.{CoreCrossPlatformSupport, DateUtils, StaticXPath}

import java.time.Instant
import java.util.Locale


trait FormRunnerAnalytics {

  self: FormBuilderPermissionsOps & FormRunnerLang =>

  import FormRunnerAnalytics.*

  // Ensure the XForms analytics listener bridge is registered as soon as FormRunner initializes
  ensureConfigured()

  //@XPathFunction
  def isAnalyticsAuthorized: Boolean =
    AnalyticsScope.forCurrentRequest().isAuthorized

  //@XPathFunction
  def analyticsSnapshotXml(window: String, appFilter: String, formFilter: String): DocumentNodeInfoType = {
    val scope = AnalyticsScope.forCurrentRequest()
    if (! scope.isAuthorized)
      throw new SecurityException("Unauthorized access to Form Runner Analytics")

    val preferredLangOpt = scala.util.Try(currentLang).toOption.flatMap(_.trimAllToOpt)
    val domDoc = buildAnalyticsSnapshotDom(
      scope            = scope,
      windowRaw        = window,
      appFilterRaw     = appFilter,
      formFilterRaw    = formFilter,
      preferredLangOpt = preferredLangOpt
    )
    new DocumentWrapper(domDoc, null, StaticXPath.GlobalConfiguration)
  }

  //@XPathFunction
  def resetAnalyticsSessionStats(): Boolean = {
    val scope = AnalyticsScope.forCurrentRequest()
    if (! scope.hasGlobalAccess)
      throw new SecurityException("Unauthorized reset of Form Runner Analytics")
    store.reset()
    true
  }
}

object FormRunnerAnalytics {

  private val defaultInMemoryStore = new InMemoryFormSessionAnalyticsStore

  @volatile private var activeStore: FormSessionAnalyticsStore = defaultInMemoryStore

  def store: FormSessionAnalyticsStore = activeStore

  // Allows plugging in an alternative `FormSessionAnalyticsStore` (e.g. a future relational backend or test double)
  def setStore(customStore: FormSessionAnalyticsStore): Unit = {
    activeStore = customStore
  }

  def resetToDefaultInMemoryStore(): Unit = {
    defaultInMemoryStore.reset()
    defaultInMemoryStore.registerGlobalBridge()
    activeStore = defaultInMemoryStore
  }

  def ensureConfigured(): Unit =
    if (activeStore eq defaultInMemoryStore)
      defaultInMemoryStore.registerGlobalBridge()

  def isShowUsernames: Boolean =
    scala.util.Try(CoreCrossPlatformSupport.properties)
      .toOption
      .forall(_.getBoolean(InMemoryFormSessionAnalyticsStore.ShowUsernamesProperty, default = true))

  def buildAnalyticsSnapshotDom(
    scope            : AnalyticsScope = AnalyticsScope.forCurrentRequest(),
    windowRaw        : String         = "24h",
    appFilterRaw     : String         = "",
    formFilterRaw    : String         = "",
    preferredLangOpt : Option[String] = Some("en"),
    nowMs            : Long           = System.currentTimeMillis()
  ): dom.Document = {
    ensureConfigured()

    val window       = AnalyticsWindow.fromString(windowRaw.trimAllToOpt.getOrElse("24h"))
    val cleanAppOpt  = appFilterRaw.trimAllToOpt.filter(scope.allowsApp)
    val cleanFormOpt = formFilterRaw.trimAllToOpt.filter { f =>
      cleanAppOpt match {
        case Some(a) => scope.allowsAppForm(AppForm(a, f))
        case None    => true
      }
    }
    val showUsers = isShowUsernames

    val query = AnalyticsQuery(
      scope         = scope,
      appFilterOpt  = cleanAppOpt,
      formFilterOpt = cleanFormOpt,
      window        = window,
      showUsernames = showUsers
    )

    val sessionSnap = store.snapshot(query, nowMs)
    val persistSnap = FormRunnerAnalyticsPersistence.queryPersistenceSnapshot(query, preferredLangOpt, nowMs)

    val doc  = dom.Document()
    val root = doc.addElement("analytics")

    root.addAttribute("timestamp-iso",     DateUtils.formatIsoDateTimeUtc(Instant.ofEpochMilli(nowMs)))
    root.addAttribute("window",            window.code)
    root.addAttribute("app-filter",        cleanAppOpt.getOrElse(""))
    root.addAttribute("form-filter",       cleanFormOpt.getOrElse(""))
    root.addAttribute("has-global-access", scope.hasGlobalAccess.toString)
    root.addAttribute("show-usernames",    showUsers.toString)

    // ==== <filters> (all authorized discovered apps & forms) ====
    val filtersElem = root.addElement("filters")
    val explicitApps = scope.permissionsByApp.keysIterator.filter(_ != "*").toSet
    val allApps =
      (sessionSnap.discoveredApps ++ persistSnap.discoveredApps ++ explicitApps)
        .filter(scope.allowsApp)
        .toList
        .sorted

    allApps.foreach { app =>
      val aElem = filtersElem.addElement("app")
      aElem.addAttribute("name", app)
      val explicitForms = scope.permissionsByApp.getOrElse(app, Set.empty).filter(_ != "*")
      val forms =
        (sessionSnap.discoveredFormsByApp.getOrElse(app, Set.empty) ++
          persistSnap.discoveredFormsByApp.getOrElse(app, Set.empty) ++
          explicitForms)
          .filter(f => scope.allowsAppForm(AppForm(app, f)))
          .toList
          .sorted
      forms.foreach { f =>
        aElem.addElement("form").addAttribute("name", f)
      }
    }

    // ==== <kpis> ====
    val kpisElem  = root.addElement("kpis")
    val errorRate =
      if (sessionSnap.windowOpened > 0L)
        math.min(100.0, (sessionSnap.windowWithErrors.toDouble / sessionSnap.windowOpened.toDouble) * 100.0)
      else
        0.0

    kpisElem.addAttribute("active-sessions",         sessionSnap.activeFormSessionsCount.toString)
    kpisElem.addAttribute("active-heap",             sessionSnap.activeInHeapCount.toString)
    kpisElem.addAttribute("passivated",              sessionSnap.passivatedCount.toString)
    kpisElem.addAttribute("recent-active-sessions",  sessionSnap.recentlyActiveSessionsCount.toString)
    kpisElem.addAttribute("connected-http-sessions", sessionSnap.connectedHttpSessionsCount.toString)
    kpisElem.addAttribute("recent-http-sessions",    sessionSnap.recentlyActiveHttpCount.toString)
    kpisElem.addAttribute("active-users",            sessionSnap.activeUsersCount.toString)
    kpisElem.addAttribute("auth-active-users",       sessionSnap.authenticatedActiveUsers.toString)
    kpisElem.addAttribute("anon-active-users",       sessionSnap.anonymousActiveUsers.toString)
    kpisElem.addAttribute("window-opened",           sessionSnap.windowOpened.toString)
    kpisElem.addAttribute("window-bounced",          sessionSnap.windowBounced.toString)
    kpisElem.addAttribute("window-interacted",       sessionSnap.windowInteracted.toString)
    kpisElem.addAttribute("window-draft-saved",      sessionSnap.windowDraftSaved.toString)
    kpisElem.addAttribute("window-submitted",        sessionSnap.windowSubmitted.toString)
    kpisElem.addAttribute("window-abandoned",        sessionSnap.windowAbandoned.toString)
    kpisElem.addAttribute("window-with-errors",      sessionSnap.windowWithErrors.toString)
    kpisElem.addAttribute("completion-rate",         FormRunnerConsole.formatPercentValue(sessionSnap.windowCompletionRate))
    kpisElem.addAttribute("error-rate",              FormRunnerConsole.formatPercentValue(errorRate))
    kpisElem.addAttribute("total-submitted",         persistSnap.totalSubmitted.toString)
    kpisElem.addAttribute("window-data-submitted",   persistSnap.windowSubmitted.toString)
    kpisElem.addAttribute("total-drafts",            persistSnap.totalDrafts.toString)
    kpisElem.addAttribute("window-data-drafts",      persistSnap.windowDrafts.toString)
    kpisElem.addAttribute("published-forms",         persistSnap.publishedFormsCount.toString)
    kpisElem.addAttribute("available-forms",         persistSnap.availableFormsCount.toString)
    kpisElem.addAttribute("unavailable-forms",       persistSnap.unavailableFormsCount.toString)
    kpisElem.addAttribute("distinct-apps",           persistSnap.distinctAppsCount.toString)

    val summaryAllowedSet: Set[AppForm] =
      persistSnap.publishedForms.iterator.filter(_.isSummaryAllowed).map(_.appForm).toSet

    val fbPerms = FormRunner.formBuilderPermissions(FormRunner.formBuilderPermissionsConfiguration, AnalyticsScope.currentRequestRolesSafe)

    def canOpenSummary(af: AppForm): Boolean =
      summaryAllowedSet.contains(af) || FormRunner.hasFormBuilderPermissionForAppForm(fbPerms, af)

    // ==== 1. <form-sessions> ====
    val formSessionsElem = root.addElement("form-sessions")
    val funnelElem       = formSessionsElem.addElement("funnel")
    funnelElem.addAttribute("count", sessionSnap.funnelByForm.size.toString)

    sessionSnap.funnelByForm.foreach { r =>
      val e = funnelElem.addElement("form")
      e.addAttribute("app",                       r.appForm.app)
      e.addAttribute("form",                      r.appForm.form)
      e.addAttribute("display-name",              s"${r.appForm.app}/${r.appForm.form}")
      e.addAttribute("opened",                    r.opened.toString)
      e.addAttribute("bounced",                   r.bounced.toString)
      e.addAttribute("interacted",                r.interacted.toString)
      e.addAttribute("draft-saved",               r.draftSaved.toString)
      e.addAttribute("submitted",                 r.submitted.toString)
      e.addAttribute("abandoned",                 r.abandoned.toString)
      e.addAttribute("with-errors",               r.withErrors.toString)
      e.addAttribute("completion-rate",           FormRunnerConsole.formatPercentValue(r.completionRate) + "%")
      e.addAttribute("completion-rate-raw",       r.completionRate.toString)
      e.addAttribute("avg-time-spent-ms",         r.avgTimeSpentMs.toString)
      e.addAttribute("avg-time-spent-formatted",  if (r.avgTimeSpentMs > 0L) FormRunnerConsole.formatDurationMs(r.avgTimeSpentMs) else "—")
      e.addAttribute("can-summary",               canOpenSummary(r.appForm).toString)
    }

    val sessionsElem = formSessionsElem.addElement("sessions")
    sessionsElem.addAttribute("count", sessionSnap.sessionRows.size.toString)

    sessionSnap.sessionRows.foreach { s =>
      val e = sessionsElem.addElement("session")
      val heapFmt   = FormRunnerConsole.formatDurationMs(s.activeHeapMs)
      val serialFmt = FormRunnerConsole.formatDurationMs(s.serializedMs)
      e.addAttribute("uuid",                  s.uuid)
      e.addAttribute("short-uuid",            s.uuid.take(12))
      e.addAttribute("app",                   s.appForm.app)
      e.addAttribute("form",                  s.appForm.form)
      e.addAttribute("display-name",          s"${s.appForm.app}/${s.appForm.form}")
      e.addAttribute("version",               s.version.toString)
      e.addAttribute("mode",                  s.mode)
      e.addAttribute("state",                 s.state)
      e.addAttribute("is-active",             s.isActive.toString)
      e.addAttribute("user",                  s.userDisplay)
      e.addAttribute("is-authenticated",      s.isAuthenticated.toString)
      e.addAttribute("opened-ms",             s.openedAtMs.toString)
      e.addAttribute("opened-iso",            DateUtils.formatIsoDateTimeUtc(Instant.ofEpochMilli(s.openedAtMs)))
      e.addAttribute("time-spent-ms",         s.timeSpentMs.toString)
      e.addAttribute("time-spent-formatted",  FormRunnerConsole.formatDurationMs(s.timeSpentMs))
      e.addAttribute("ajax-count",            s.ajaxCount.toString)
      e.addAttribute("active-heap-ms",        s.activeHeapMs.toString)
      e.addAttribute("serialized-ms",         s.serializedMs.toString)
      e.addAttribute("active-serial-summary", s"$heapFmt / $serialFmt")
      e.addAttribute("expires-in-ms",         s.expiresInMs.toString)
      e.addAttribute("expires-in-formatted",  if (s.expiresInMs < 0L) "—" else FormRunnerConsole.formatDurationMs(s.expiresInMs))
      e.addAttribute("error-count",           s.errorCount.toString)
    }

    // ==== 2. <submitted-data> ====
    val submittedDataElem = root.addElement("submitted-data")
    val dataFormsElem     = submittedDataElem.addElement("forms")
    dataFormsElem.addAttribute("count", persistSnap.submittedByForm.size.toString)

    persistSnap.submittedByForm.foreach { r =>
      val e = dataFormsElem.addElement("form")
      e.addAttribute("app",                 r.appForm.app)
      e.addAttribute("form",                r.appForm.form)
      e.addAttribute("display-name",        s"${r.appForm.app}/${r.appForm.form}")
      e.addAttribute("title",               r.titleOpt.getOrElse(""))
      e.addAttribute("version",             r.version.toString)
      e.addAttribute("submitted",           r.submittedCount.toString)
      e.addAttribute("window-submitted",    r.windowSubmittedCount.toString)
      e.addAttribute("drafts",              r.draftCount.toString)
      e.addAttribute("latest-modified-ms",  r.latestModifiedMsOpt.getOrElse(-1L).toString)
      e.addAttribute("latest-modified-iso", r.latestModifiedMsOpt.map(ts => DateUtils.formatIsoDateTimeUtc(Instant.ofEpochMilli(ts))).getOrElse("—"))
      e.addAttribute("can-summary",         r.isSummaryAllowed.toString)
    }

    val stagesElem = submittedDataElem.addElement("stages")
    stagesElem.addAttribute("count", persistSnap.workflowStages.size.toString)

    persistSnap.workflowStages.foreach { st =>
      val e = stagesElem.addElement("stage")
      e.addAttribute("app",               st.appForm.app)
      e.addAttribute("form",              st.appForm.form)
      e.addAttribute("display-name",      s"${st.appForm.app}/${st.appForm.form}")
      e.addAttribute("stage",             st.stage)
      e.addAttribute("submitted",         st.submittedCount.toString)
      e.addAttribute("share-percent",     FormRunnerConsole.formatPercentValue(st.sharePercent) + "%")
      e.addAttribute("share-percent-raw", st.sharePercent.toString)
    }

    // ==== 3. <published-forms> ====
    val publishedElem = root.addElement("published-forms")
    val appsInvElem   = publishedElem.addElement("apps")
    appsInvElem.addAttribute("count", persistSnap.appInventory.size.toString)

    persistSnap.appInventory.foreach { a =>
      val e = appsInvElem.addElement("app")
      e.addAttribute("name",            a.app)
      e.addAttribute("published-forms", a.publishedForms.toString)
      e.addAttribute("total-versions",  a.totalVersions.toString)
      e.addAttribute("available",       a.availableForms.toString)
      e.addAttribute("unavailable",     a.unavailableForms.toString)
      e.addAttribute("submitted",       a.totalSubmitted.toString)
      e.addAttribute("drafts",          a.totalDrafts.toString)
    }

    val pubFormsElem = publishedElem.addElement("forms")
    pubFormsElem.addAttribute("count", persistSnap.publishedForms.size.toString)

    persistSnap.publishedForms.foreach { pf =>
      val e = pubFormsElem.addElement("form")
      e.addAttribute("app",               pf.appForm.app)
      e.addAttribute("form",              pf.appForm.form)
      e.addAttribute("display-name",      s"${pf.appForm.app}/${pf.appForm.form}")
      e.addAttribute("title",             pf.titleOpt.getOrElse(""))
      e.addAttribute("latest-version",    pf.latestVersion.toString)
      e.addAttribute("versions-count",    pf.versionsCount.toString)
      e.addAttribute("available",         pf.available.toString)
      e.addAttribute("last-modified-ms",  pf.lastModifiedMs.toString)
      e.addAttribute("last-modified-iso", if (pf.lastModifiedMs > 0L) DateUtils.formatIsoDateTimeUtc(Instant.ofEpochMilli(pf.lastModifiedMs)) else "—")
      e.addAttribute("last-modified-by",  pf.lastModifiedByOpt.getOrElse("—"))
      e.addAttribute("submitted",         pf.submittedCount.toString)
      e.addAttribute("drafts",            pf.draftCount.toString)
      e.addAttribute("can-summary",       pf.isSummaryAllowed.toString)
    }

    // ==== 4. <users> ====
    val usersElem = root.addElement("users")
    usersElem.addAttribute("auth-submitted",            persistSnap.authenticatedSubmitted.toString)
    usersElem.addAttribute("anon-submitted",            persistSnap.anonymousSubmitted.toString)
    usersElem.addAttribute("distinct-submitting-users", persistSnap.distinctSubmittingUsers.toString)

    val subUsersElem = usersElem.addElement("submitting-users")
    subUsersElem.addAttribute("count", persistSnap.userSubmissions.size.toString)

    persistSnap.userSubmissions.foreach { u =>
      val e = subUsersElem.addElement("user")
      e.addAttribute("display-name",      u.userDisplay)
      e.addAttribute("is-authenticated",  u.isAuthenticated.toString)
      e.addAttribute("distinct-forms",    u.distinctForms.toString)
      e.addAttribute("submitted",         u.submittedCount.toString)
      e.addAttribute("share-percent",     FormRunnerConsole.formatPercentValue(u.sharePercent) + "%")
      e.addAttribute("share-percent-raw", u.sharePercent.toString)
    }

    val activeUsersElem = usersElem.addElement("active-sessions")
    activeUsersElem.addAttribute("count", sessionSnap.activeUserSessions.size.toString)

    sessionSnap.activeUserSessions.foreach { us =>
      val e      = activeUsersElem.addElement("user-session")
      val idleMs = math.max(0L, nowMs - us.lastActivityAtMs)
      e.addAttribute("session-id",            us.sessionIdShort)
      e.addAttribute("user",                  us.userDisplay)
      e.addAttribute("is-authenticated",      us.isAuthenticated.toString)
      e.addAttribute("active-forms",          us.activeFormsCount.toString)
      e.addAttribute("session-age-ms",        us.sessionAgeMs.toString)
      e.addAttribute("session-age-formatted", FormRunnerConsole.formatDurationMs(us.sessionAgeMs))
      e.addAttribute("idle-ms",               idleMs.toString)
      e.addAttribute("idle-formatted",        FormRunnerConsole.formatDurationMs(idleMs))
      e.addAttribute("expires-in-ms",         us.expiresInMs.toString)
      e.addAttribute("expires-in-formatted",  if (us.expiresInMs < 0L) "—" else FormRunnerConsole.formatDurationMs(us.expiresInMs))
    }

    // ==== 5. <timings> ====
    val timingsElem = root.addElement("timings")
    val catsElem    = timingsElem.addElement("categories")
    catsElem.addAttribute("count", TimingCategory.All.size.toString)

    def formatMsOrDash(summary: TimingSummary, ms: Double): String =
      if (summary.count == 0L) "—" else formatMillis(ms)

    TimingCategory.All.foreach { cat =>
      val sum = sessionSnap.categoryTimings.getOrElse(cat, TimingSummary.Empty)
      val e   = catsElem.addElement("category")
      e.addAttribute("key",           cat.key)
      e.addAttribute("count",         sum.count.toString)
      e.addAttribute("avg-ms-raw",    if (sum.count == 0L) "-1" else sum.avgMs.toString)
      e.addAttribute("avg-formatted", formatMsOrDash(sum, sum.avgMs))
      e.addAttribute("p50-ms",        if (sum.count == 0L) "-1" else sum.p50Ms.toString)
      e.addAttribute("p50-formatted", formatMsOrDash(sum, sum.p50Ms.toDouble))
      e.addAttribute("p95-ms",        if (sum.count == 0L) "-1" else sum.p95Ms.toString)
      e.addAttribute("p95-formatted", formatMsOrDash(sum, sum.p95Ms.toDouble))
      e.addAttribute("max-ms",        if (sum.count == 0L) "-1" else sum.maxMs.toString)
      e.addAttribute("max-formatted", formatMsOrDash(sum, sum.maxMs.toDouble))
      e.addAttribute("errors",        sum.errorCount.toString)
    }

    val formTimingsElem = timingsElem.addElement("forms")
    formTimingsElem.addAttribute("count", sessionSnap.formTimings.size.toString)

    sessionSnap.formTimings.foreach { ft =>
      val e = formTimingsElem.addElement("form")
      e.addAttribute("app",                   ft.appForm.app)
      e.addAttribute("form",                  ft.appForm.form)
      e.addAttribute("display-name",          s"${ft.appForm.app}/${ft.appForm.form}")
      e.addAttribute("loads",                 ft.loads.count.toString)
      e.addAttribute("load-avg-ms-raw",       if (ft.loads.count == 0L) "-1" else ft.loads.avgMs.toString)
      e.addAttribute("load-avg-formatted",    formatMsOrDash(ft.loads, ft.loads.avgMs))
      e.addAttribute("load-p95-ms",           if (ft.loads.count == 0L) "-1" else ft.loads.p95Ms.toString)
      e.addAttribute("load-p95-formatted",    formatMsOrDash(ft.loads, ft.loads.p95Ms.toDouble))
      e.addAttribute("ajax-count",            ft.ajaxUpdates.count.toString)
      e.addAttribute("ajax-avg-ms-raw",       if (ft.ajaxUpdates.count == 0L) "-1" else ft.ajaxUpdates.avgMs.toString)
      e.addAttribute("ajax-avg-formatted",    formatMsOrDash(ft.ajaxUpdates, ft.ajaxUpdates.avgMs))
      e.addAttribute("ajax-p95-formatted",    formatMsOrDash(ft.ajaxUpdates, ft.ajaxUpdates.p95Ms.toDouble))
      e.addAttribute("saves",                 ft.saves.count.toString)
      e.addAttribute("save-avg-ms-raw",       if (ft.saves.count == 0L) "-1" else ft.saves.avgMs.toString)
      e.addAttribute("save-avg-formatted",    formatMsOrDash(ft.saves, ft.saves.avgMs))
      e.addAttribute("save-p95-formatted",    formatMsOrDash(ft.saves, ft.saves.p95Ms.toDouble))
      e.addAttribute("service-calls",         ft.serviceCalls.count.toString)
      e.addAttribute("service-avg-ms-raw",    if (ft.serviceCalls.count == 0L) "-1" else ft.serviceCalls.avgMs.toString)
      e.addAttribute("service-avg-formatted", formatMsOrDash(ft.serviceCalls, ft.serviceCalls.avgMs))
    }

    // ==== <charts> ====
    val chartsElem = root.addElement("charts")
    chartsElem.addAttribute("window", window.code)

    def addChartElem(id: String, spec: ConsoleChartSpec): Unit = {
      val cElem = chartsElem.addElement("chart")
      cElem.addAttribute("id", id)
      cElem.addText(spec.asJson.noSpaces)
    }

    def truncateCat(label: String, maxLen: Int = 28): String =
      if (label.length <= maxLen) label else label.take(maxLen - 1) + "…"

    val sessionTimestamps = sessionSnap.timeSeries.map(_.timestampMs)
    val dataTimestamps    = persistSnap.timeSeries.map(_.timestampMs)

    // 1A. Session Activity Over Time
    addChartElem(
      "sessions-activity-trend",
      ConsoleChartSpec(
        kind         = "timeseries",
        valueUnit    = "count",
        window       = window.code,
        timestampsMs = sessionTimestamps,
        series       = List(
          ConsoleChartSeries("sessions-opened",    sessionSnap.timeSeries.map(_.opened.toDouble),     "area", colorSlot = 0),
          ConsoleChartSeries("sessions-submitted", sessionSnap.timeSeries.map(_.submitted.toDouble),  "line", colorSlot = 1),
          ConsoleChartSeries("sessions-abandoned", sessionSnap.timeSeries.map(_.abandoned.toDouble),  "line", colorSlot = 2, dashed = true),
          ConsoleChartSeries("sessions-errors",    sessionSnap.timeSeries.map(_.withErrors.toDouble), "bar",  colorSlot = 3)
        )
      )
    )

    // 1B. Conversion Funnel (Window)
    val isEn = ! preferredLangOpt.exists(_.startsWith("fr"))
    val funnelStageLabels =
      if (isEn) List("Opened", "Interacted", "Draft Saved", "Submitted", "Bounced (0 int.)", "Abandoned", "With Errors")
      else      List("Ouvertes", "Interagies", "Brouillon", "Soumises", "Rebond (0 int.)", "Abandonnées", "Avec erreurs")
    val funnelStageValues = List(
      sessionSnap.windowOpened,
      sessionSnap.windowInteracted,
      sessionSnap.windowDraftSaved,
      sessionSnap.windowSubmitted,
      sessionSnap.windowBounced,
      sessionSnap.windowAbandoned,
      sessionSnap.windowWithErrors
    )
    val hasFunnelData = sessionSnap.windowOpened > 0L || funnelStageValues.exists(_ > 0L)
    addChartElem(
      "sessions-funnel",
      ConsoleChartSpec(
        kind           = "hbar",
        valueUnit      = "count",
        categories     = if (hasFunnelData) funnelStageLabels else Nil,
        fullCategories = if (hasFunnelData) funnelStageLabels else Nil,
        series         = List(
          ConsoleChartSeries("funnel-sessions", if (hasFunnelData) funnelStageValues.map(_.toDouble) else Nil, "bar", colorSlot = 0)
        )
      )
    )

    // 2A. Persisted Submissions & Drafts Over Time
    addChartElem(
      "data-submissions-trend",
      ConsoleChartSpec(
        kind         = "timeseries",
        valueUnit    = "count",
        stacked      = true,
        window       = window.code,
        timestampsMs = dataTimestamps,
        series       = List(
          ConsoleChartSeries("data-submitted", persistSnap.timeSeries.map(_.submittedCount.toDouble), "bar", colorSlot = 1),
          ConsoleChartSeries("data-drafts",    persistSnap.timeSeries.map(_.draftCount.toDouble),     "bar", colorSlot = 2)
        )
      )
    )

    // 2B. Top Forms by Submitted & Draft Volume
    locally {
      val topDataForms =
        persistSnap.submittedByForm
          .groupBy(_.appForm)
          .toList
          .map { case (af, rows) =>
            val sub = rows.iterator.map(_.submittedCount).sum
            val drf = rows.iterator.map(_.draftCount).sum
            (s"${af.app}/${af.form}", sub, drf)
          }
          .sortBy { case (lbl, sub, drf) => (-(sub + drf), lbl) }
          .take(10)

      addChartElem(
        "data-top-forms",
        ConsoleChartSpec(
          kind           = "hbar",
          valueUnit      = "count",
          stacked        = true,
          categories     = topDataForms.map(r => truncateCat(r._1, 28)),
          fullCategories = topDataForms.map(_._1),
          series         = List(
            ConsoleChartSeries("data-submitted", topDataForms.map(_._2.toDouble), "bar", colorSlot = 1),
            ConsoleChartSeries("data-drafts",    topDataForms.map(_._3.toDouble), "bar", colorSlot = 2)
          )
        )
      )
    }

    // 3A. Published Forms by App
    locally {
      val topApps = persistSnap.appInventory.sortBy(a => (-a.publishedForms, a.app)).take(10)
      addChartElem(
        "inventory-forms-by-app",
        ConsoleChartSpec(
          kind           = "hbar",
          valueUnit      = "count",
          stacked        = true,
          categories     = topApps.map(a => truncateCat(a.app, 28)),
          fullCategories = topApps.map(_.app),
          series         = List(
            ConsoleChartSeries("forms-available",   topApps.map(_.availableForms.toDouble),   "bar", colorSlot = 1),
            ConsoleChartSeries("forms-unavailable", topApps.map(_.unavailableForms.toDouble), "bar", colorSlot = 6)
          )
        )
      )
    }

    // 3B. Submitted Data by App
    locally {
      val topDataApps = persistSnap.appInventory.filter(a => a.totalSubmitted > 0L || a.totalDrafts > 0L).take(10)
      addChartElem(
        "inventory-data-by-app",
        ConsoleChartSpec(
          kind           = "hbar",
          valueUnit      = "count",
          stacked        = true,
          categories     = topDataApps.map(a => truncateCat(a.app, 28)),
          fullCategories = topDataApps.map(_.app),
          series         = List(
            ConsoleChartSeries("data-submitted", topDataApps.map(_.totalSubmitted.toDouble), "bar", colorSlot = 0),
            ConsoleChartSeries("data-drafts",    topDataApps.map(_.totalDrafts.toDouble),    "bar", colorSlot = 2)
          )
        )
      )
    }

    // 4A. User Submissions Breakdown
    locally {
      val anonLbl = if (isEn) "(anonymous)" else "(anonyme)"
      val topUsers =
        persistSnap.userSubmissions
          .take(10)
          .map { u =>
            val lbl = if (u.isAuthenticated) u.userDisplay else anonLbl
            (lbl, u.submittedCount)
          }
      addChartElem(
        "users-submissions-breakdown",
        ConsoleChartSpec(
          kind           = "hbar",
          valueUnit      = "count",
          categories     = topUsers.map(u => truncateCat(u._1, 28)),
          fullCategories = topUsers.map(_._1),
          series         = List(
            ConsoleChartSeries("data-submitted", topUsers.map(_._2.toDouble), "bar", colorSlot = 4)
          )
        )
      )
    }

    // 4B. Active Users & Connected Sessions Over Time
    addChartElem(
      "users-activity-trend",
      ConsoleChartSpec(
        kind         = "timeseries",
        valueUnit    = "count",
        window       = window.code,
        timestampsMs = sessionTimestamps,
        series       = List(
          ConsoleChartSeries("active-users",       sessionSnap.timeSeries.map(_.activeUsers.toDouble),       "area", colorSlot = 4),
          ConsoleChartSeries("connected-sessions", sessionSnap.timeSeries.map(_.connectedSessions.toDouble), "line", colorSlot = 0, dashed = true)
        )
      )
    )

    // 5A. Request & Action Latency Over Time
    addChartElem(
      "timings-latency-trend",
      ConsoleChartSpec(
        kind         = "timeseries",
        valueUnit    = "duration-ms",
        window       = window.code,
        timestampsMs = sessionTimestamps,
        series       = List(
          ConsoleChartSeries("avg-form-load",    sessionSnap.timeSeries.map(_.avgFormLoadMs), "line", colorSlot = 0),
          ConsoleChartSeries("avg-ajax-update",  sessionSnap.timeSeries.map(_.avgAjaxMs),     "line", colorSlot = 1),
          ConsoleChartSeries("avg-save-action",  sessionSnap.timeSeries.map(_.avgSaveMs),     "line", colorSlot = 2),
          ConsoleChartSeries("avg-service-call", sessionSnap.timeSeries.map(_.avgServiceMs),  "line", colorSlot = 4, dashed = true)
        )
      )
    )

    // 5B. Slowest Forms (Avg Load + Save Latency)
    locally {
      val topSlowForms =
        sessionSnap.formTimings
          .take(10)
          .map { ft =>
            (s"${ft.appForm.app}/${ft.appForm.form}", ft.loads.avgMs, ft.saves.avgMs)
          }
      addChartElem(
        "timings-slowest-forms",
        ConsoleChartSpec(
          kind           = "hbar",
          valueUnit      = "duration-ms",
          stacked        = true,
          categories     = topSlowForms.map(r => truncateCat(r._1, 28)),
          fullCategories = topSlowForms.map(_._1),
          series         = List(
            ConsoleChartSeries("avg-form-load",   topSlowForms.map(_._2), "bar", colorSlot = 0),
            ConsoleChartSeries("avg-save-action", topSlowForms.map(_._3), "bar", colorSlot = 2)
          )
        )
      )
    }

    doc
  }

  def formatMillis(ms: Double): String =
    if (ms < 1000.0)
      String.format(Locale.ROOT, "%d ms", java.lang.Long.valueOf(math.round(ms)))
    else
      String.format(Locale.ROOT, "%.2fs", java.lang.Double.valueOf(ms / 1000.0))
}
