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
import org.orbeon.io.IOUtils.useAndClose
import org.orbeon.oxf.externalcontext.{Credentials, SimpleRole, UserAndGroup}
import org.orbeon.oxf.fr.analytics.*
import org.orbeon.oxf.http.HttpMethod.GET
import org.orbeon.oxf.properties.PropertySet
import org.orbeon.oxf.properties.PropertySet.PropertyParams
import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport, TestHttpClient}
import org.orbeon.oxf.util.XPath
import org.orbeon.oxf.xml.XMLConstants.{XS_BOOLEAN_QNAME, XS_STRING_QNAME}
import org.orbeon.oxf.xml.dom.Extensions.*
import org.orbeon.xforms.XFormsCrossPlatformSupport
import org.scalatest.funspec.AnyFunSpecLike

import java.sql.{Connection, Timestamp, Types}
import java.util.concurrent.atomic.AtomicInteger


class FormRunnerAnalyticsTest
  extends DocumentTestBase
     with ResourceManagerSupport
     with AnyFunSpecLike
     with FormRunnerSupport {

  private val ExpectedChartIds: Set[String] = Set(
    "sessions-activity-trend",
    "sessions-funnel",
    "data-submissions-trend",
    "data-top-forms",
    "inventory-forms-by-app",
    "inventory-data-by-app",
    "users-submissions-breakdown",
    "users-activity-trend",
    "timings-latency-trend",
    "timings-slowest-forms"
  )

  describe("Form Runner /fr/analytics access control, session funnel, timings, and persistence aggregation") {

    it("must deny access (403) to /fr/analytics when neither form-builder-permissions nor oxf.fr.analytics.roles grants access") {
      val (_, anonResp, _, _) =
        TestHttpClient.connect(
          url     = "/fr/analytics",
          method  = GET,
          headers = Map.empty,
          content = None
        )
      assert(anonResp.statusCode == 403)

      val unprivilegedCreds = Credentials(UserAndGroup("mallory", Some("guests")), List(SimpleRole("guest")), Nil)
      val (_, unprivResp, _, _) =
        TestHttpClient.connect(
          url         = "/fr/analytics",
          method      = GET,
          headers     = Map.empty,
          content     = None,
          credentials = Some(unprivilegedCreds),
          attributes  = propertiesOverride(
            PropertyParams(Map.empty, "oxf.fr.analytics.roles.*.*", XS_STRING_QNAME, "business-analyst")
          )
        )
      assert(unprivResp.statusCode == 403)
    }

    it("must union form-builder-permissions with oxf.fr.analytics.roles.*.* and render /fr/analytics cleanly when authorized") {
      val fbConfigDoc =
        XFormsCrossPlatformSupport.stringToTinyTree(
          XPath.GlobalConfiguration,
          """<roles>
            |  <role name="acme-admin" app="acme" form="*"/>
            |</roles>""".stripMargin,
          handleXInclude = false,
          handleLexical  = false
        )

      val propSet = PropertySet.forTests(List(
        PropertyParams(Map.empty, "oxf.fr.analytics.roles.hr.onboarding", XS_STRING_QNAME, "hr-analyst, audit"),
        PropertyParams(Map.empty, "oxf.fr.analytics.roles.finance.*",     XS_STRING_QNAME, "cfo")
      ))

      val scope = AnalyticsScope.fromPermissionsAndProperties(
        fbConfigurationOpt = Some(fbConfigDoc),
        propertySet        = propSet,
        incomingRoleNames  = Set("acme-admin", "hr-analyst")
      )

      assert(scope.isAuthorized)
      assert(! scope.hasGlobalAccess)
      assert(scope.allowsApp("acme"))
      assert(scope.allowsApp("hr"))
      assert(! scope.allowsApp("finance"))
      assert(scope.allowsAppForm(AppForm("acme", "order")))
      assert(scope.allowsAppForm(AppForm("acme", "invoice")))
      assert(scope.allowsAppForm(AppForm("hr",   "onboarding")))
      assert(! scope.allowsAppForm(AppForm("hr", "salary-review")))
      assert(! scope.allowsAppForm(AppForm("finance", "ledger")))

      // End-to-end HTTP GET `/fr/analytics` with `oxf.fr.analytics.roles.acme.*`
      val analystCreds = Credentials(UserAndGroup("dana", Some("analysts")), List(SimpleRole("acme-analyst")), Nil)
      val (_, analyticsResp, _, _) =
        TestHttpClient.connect(
          url         = "/fr/analytics",
          method      = GET,
          headers     = Map.empty,
          content     = None,
          credentials = Some(analystCreds),
          attributes  = propertiesOverride(
            PropertyParams(Map.empty, "oxf.fr.analytics.roles.acme.*", XS_STRING_QNAME, "acme-analyst")
          )
        )
      assert(analyticsResp.statusCode == 200)

      // Also verify `/fr/console` classifies `/fr/analytics` static state as `Forms Analytics`
      val consoleStaticStates =
        FormRunnerConsole.buildConsoleSnapshotDom().getRootElement
          .element("static-states")
          .elements("static-state")
          .toList
      assert(
        consoleStaticStates.exists(e =>
          e.attributeValue("system-page")  == "analytics" &&
          e.attributeValue("major-mode")   == "analytics" &&
          e.attributeValue("display-name") == "Forms Analytics"
        )
      )
    }

    it("must track live Form Runner loads, conversion funnels, read-only exclusion, passivation durations, and latency percentiles") {
      FormRunnerAnalytics.resetToDefaultInMemoryStore()
      try {
        // 1. Real XForms initialization hook via `runFormRunner`
        val (_, wizardDocOpt, _) =
          runFormRunner(
            app  = "tests",
            form = "wizard",
            mode = "new"
          )
        assert(wizardDocOpt.isDefined)

        val liveRoot = FormRunnerAnalytics.buildAnalyticsSnapshotDom(scope = AnalyticsScope.GlobalScope).getRootElement
        assert(liveRoot.element("kpis").attributeValue("window-opened").toLong >= 1L)
        assert(
          liveRoot.element("form-sessions").element("sessions").elements("session")
            .exists(e => e.attributeValue("app") == "tests" && e.attributeValue("form") == "wizard" && e.attributeValue("state") == "active-heap")
        )

        // 2. Deterministic multi-session scenario on `InMemoryFormSessionAnalyticsStore`
        val store  = new InMemoryFormSessionAnalyticsStore
        val baseMs = 1_760_000_000_000L
        val acmeOrder      = AppForm("acme",   "order")
        val secretInternal = AppForm("secret", "internal")

        // Session 1: `new` mode bounced & abandoned on HTTP session expiration (0 meaningful interactions, 1 non-interaction heartbeat)
        store.recordHttpSessionCreated("http-sess-1", baseMs, maxInactiveSec = 1800)
        store.recordFormSessionOpened(FormSessionOpenEvent(
          uuid               = "uuid-bounced-1",
          sessionIdOpt       = Some("http-sess-1"),
          appForm            = acmeOrder,
          version            = 2,
          mode               = "new",
          isInteractiveEntry = true,
          usernameOpt        = None,
          openedAtMs         = baseMs + 1_000L,
          maxInactiveSec     = 1800,
          initDurationMs     = 40L
        ))
        // Heartbeat / poll must NOT count as user interaction
        store.recordFormInteraction(
          uuid           = "uuid-bounced-1",
          sessionIdOpt   = Some("http-sess-1"),
          usernameOpt    = None,
          maxInactiveSec = 1800,
          isInteraction  = false,
          ajaxDurationMs = 5L,
          hadServerError = false,
          atMs           = baseMs + 5_000L
        )
        store.recordHttpSessionDestroyed("http-sess-1", baseMs + 60_000L, List("uuid-bounced-1"))

        // Session 2: `view` mode (read-only) closed without save -> must NOT inflate `opened`, `bounced`, or `abandoned`
        store.recordHttpSessionCreated("http-sess-2", baseMs + 2_000L, maxInactiveSec = 1800)
        store.recordFormSessionOpened(FormSessionOpenEvent(
          uuid               = "uuid-readonly-2",
          sessionIdOpt       = Some("http-sess-2"),
          appForm            = acmeOrder,
          version            = 2,
          mode               = "view",
          isInteractiveEntry = false,
          usernameOpt        = Some("viewer"),
          openedAtMs         = baseMs + 2_000L,
          maxInactiveSec     = 1800,
          initDurationMs     = 25L
        ))
        store.recordHttpSessionDestroyed("http-sess-2", baseMs + 30_000L, List("uuid-readonly-2"))

        // Session 3: `new` mode full journey (Ajax interaction -> validation error -> draft save -> passivation -> activation -> service call -> final save)
        store.recordHttpSessionCreated("http-sess-3", baseMs + 3_000L, maxInactiveSec = 1800)
        store.recordFormSessionOpened(FormSessionOpenEvent(
          uuid               = "uuid-completed-3",
          sessionIdOpt       = Some("http-sess-3"),
          appForm            = acmeOrder,
          version            = 2,
          mode               = "new",
          isInteractiveEntry = true,
          usernameOpt        = Some("alice"),
          openedAtMs         = baseMs + 3_000L,
          maxInactiveSec     = 1800,
          initDurationMs     = 120L
        ))
        store.recordFormInteraction(
          uuid           = "uuid-completed-3",
          sessionIdOpt   = Some("http-sess-3"),
          usernameOpt    = Some("alice"),
          maxInactiveSec = 1800,
          isInteraction  = true,
          ajaxDurationMs = 35L,
          hadServerError = false,
          atMs           = baseMs + 13_000L
        )
        store.recordFormProcessOutcome(
          uuid       = "uuid-completed-3",
          appForm    = acmeOrder,
          outcome    = ProcessOutcome.ValidationError,
          durationMs = 12L,
          atMs       = baseMs + 18_000L
        )
        store.recordFormSave(
          uuid       = "uuid-completed-3",
          appForm    = acmeOrder,
          version    = 2,
          isDraft    = true,
          durationMs = 85L,
          succeeded  = true,
          atMs       = baseMs + 23_000L
        )
        // Evicted from heap at +33s (30s in heap), restored from store at +93s (60s serialized)
        store.recordFormPassivationChange("uuid-completed-3", passivated = true,  atMs = baseMs + 33_000L)
        store.recordFormPassivationChange("uuid-completed-3", passivated = false, atMs = baseMs + 93_000L)

        store.recordSubmissionTiming(
          uuidOpt    = Some("uuid-completed-3"),
          appFormOpt = Some(acmeOrder),
          category   = TimingCategory.ServiceCall,
          durationMs = 240L,
          succeeded  = true,
          atMs       = baseMs + 95_000L
        )
        store.recordFormSave(
          uuid       = "uuid-completed-3",
          appForm    = acmeOrder,
          version    = 2,
          isDraft    = false,
          durationMs = 150L,
          succeeded  = true,
          atMs       = baseMs + 103_000L
        )

        // Session 4: Out-of-scope form (`secret/internal`) currently passivated
        store.recordHttpSessionCreated("http-sess-4", baseMs + 10_000L, maxInactiveSec = 1800)
        store.recordFormSessionOpened(FormSessionOpenEvent(
          uuid               = "uuid-secret-4",
          sessionIdOpt       = Some("http-sess-4"),
          appForm            = secretInternal,
          version            = 1,
          mode               = "new",
          isInteractiveEntry = true,
          usernameOpt        = Some("root"),
          openedAtMs         = baseMs + 10_000L,
          maxInactiveSec     = 1800,
          initDurationMs     = 60L
        ))
        store.recordFormPassivationChange("uuid-secret-4", passivated = true, atMs = baseMs + 20_000L)

        val nowMs = baseMs + 113_000L

        // Query scoped strictly to `acme/*`
        val acmeScope = AnalyticsScope(Map("acme" -> Set("*")))
        val scopedSnap = store.snapshot(
          AnalyticsQuery(
            scope         = acmeScope,
            appFilterOpt  = None,
            formFilterOpt = None,
            window        = AnalyticsWindow.Window1h,
            showUsernames = true
          ),
          nowMs = nowMs
        )

        // `secret/internal` must be excluded completely from scoped metrics and rows
        assert(scopedSnap.discoveredApps == Set("acme"))
        assert(scopedSnap.windowOpened     == 2L) // Session 1 + Session 3 (`view` Session 2 excluded from interactive funnel)
        assert(scopedSnap.windowBounced    == 1L) // Session 1
        assert(scopedSnap.windowAbandoned  == 1L) // Session 1
        assert(scopedSnap.windowInteracted == 1L) // Session 3
        assert(scopedSnap.windowDraftSaved == 1L) // Session 3
        assert(scopedSnap.windowSubmitted  == 1L) // Session 3
        assert(scopedSnap.windowWithErrors == 1L) // Session 3 validation error
        assert(math.abs(scopedSnap.windowCompletionRate - 50.0) < 0.01)

        val orderFunnel = scopedSnap.funnelByForm.find(_.appForm == acmeOrder).get
        assert(orderFunnel.opened     == 2L)
        assert(orderFunnel.bounced    == 1L)
        assert(orderFunnel.abandoned  == 1L)
        assert(orderFunnel.interacted == 1L)
        assert(orderFunnel.draftSaved == 1L)
        assert(orderFunnel.submitted  == 1L)
        assert(orderFunnel.withErrors == 1L)

        val completedRow = scopedSnap.sessionRows.find(_.uuid == "uuid-completed-3").get
        assert(completedRow.state           == "submitted")
        assert(completedRow.userDisplay     == "alice")
        assert(completedRow.isAuthenticated)
        assert(completedRow.activeHeapMs    == 50_000L) // 30s before passivation + 20s after activation (`113s - 93s`)
        assert(completedRow.serializedMs    == 60_000L) // `93s - 33s`
        assert(completedRow.timeSpentMs     == 100_000L)
        assert(completedRow.ajaxCount       == 1)
        assert(completedRow.errorCount      == 1)

        // Timings (`FormLoad` has 3 loads on `acme/order`: 40ms, 25ms, 120ms; `SaveAction`: 85ms, 150ms; `ServiceCall`: 240ms)
        val loadTimings = scopedSnap.categoryTimings(TimingCategory.FormLoad)
        assert(loadTimings.count == 3L)
        assert(loadTimings.maxMs >= 115L)
        val saveTimings = scopedSnap.categoryTimings(TimingCategory.SaveAction)
        assert(saveTimings.count == 2L)
        assert(saveTimings.p95Ms >= 140L)
        val svcTimings = scopedSnap.categoryTimings(TimingCategory.ServiceCall)
        assert(svcTimings.count == 1L)
        assert(svcTimings.p50Ms >= 230L)

        // Privacy masking (`showUsernames = false`)
        val maskedSnap = store.snapshot(
          AnalyticsQuery(
            scope         = acmeScope,
            appFilterOpt  = None,
            formFilterOpt = None,
            window        = AnalyticsWindow.Window1h,
            showUsernames = false
          ),
          nowMs = nowMs
        )
        assert(maskedSnap.sessionRows.find(_.uuid == "uuid-completed-3").get.userDisplay == "***")
        assert(maskedSnap.activeUserSessions.forall(_.userDisplay != "alice"))
      } finally {
        FormRunnerAnalytics.resetToDefaultInMemoryStore()
      }
    }

    it("must support plugging in a custom FormSessionAnalyticsStore SPI implementation") {
      val snapshotCalls = new AtomicInteger(0)
      val delegate      = new InMemoryFormSessionAnalyticsStore

      val customStore = new FormSessionAnalyticsStore {
        def recordHttpSessionCreated(sessionId: String, createdAtMs: Long, maxInactiveSec: Int): Unit =
          delegate.recordHttpSessionCreated(sessionId, createdAtMs, maxInactiveSec)
        def recordHttpSessionActivity(sessionId: String, usernameOpt: Option[String], atMs: Long, maxInactiveSec: Int): Unit =
          delegate.recordHttpSessionActivity(sessionId, usernameOpt, atMs, maxInactiveSec)
        def recordHttpSessionDestroyed(sessionId: String, destroyedAtMs: Long, documentUuids: List[String]): Unit =
          delegate.recordHttpSessionDestroyed(sessionId, destroyedAtMs, documentUuids)
        def recordFormSessionOpened(event: FormSessionOpenEvent): Unit =
          delegate.recordFormSessionOpened(event)
        def recordFormLoadFailed(appFormOpt: Option[AppForm], durationMs: Long, atMs: Long): Unit =
          delegate.recordFormLoadFailed(appFormOpt, durationMs, atMs)
        def recordFormInteraction(uuid: String, sessionIdOpt: Option[String], usernameOpt: Option[String], maxInactiveSec: Int, isInteraction: Boolean, ajaxDurationMs: Long, hadServerError: Boolean, atMs: Long): Unit =
          delegate.recordFormInteraction(uuid, sessionIdOpt, usernameOpt, maxInactiveSec, isInteraction, ajaxDurationMs, hadServerError, atMs)
        def recordFormSave(uuid: String, appForm: AppForm, version: Int, isDraft: Boolean, durationMs: Long, succeeded: Boolean, atMs: Long): Unit =
          delegate.recordFormSave(uuid, appForm, version, isDraft, durationMs, succeeded, atMs)
        def recordFormProcessOutcome(uuid: String, appForm: AppForm, outcome: ProcessOutcome, durationMs: Long, atMs: Long): Unit =
          delegate.recordFormProcessOutcome(uuid, appForm, outcome, durationMs, atMs)
        def recordFormPassivationChange(uuid: String, passivated: Boolean, atMs: Long): Unit =
          delegate.recordFormPassivationChange(uuid, passivated, atMs)
        def recordFormSessionClosed(uuid: String, reason: SessionCloseReason, atMs: Long): Unit =
          delegate.recordFormSessionClosed(uuid, reason, atMs)
        def recordSubmissionTiming(uuidOpt: Option[String], appFormOpt: Option[AppForm], category: TimingCategory, durationMs: Long, succeeded: Boolean, atMs: Long): Unit =
          delegate.recordSubmissionTiming(uuidOpt, appFormOpt, category, durationMs, succeeded, atMs)
        def snapshot(query: AnalyticsQuery, nowMs: Long): SessionAnalyticsSnapshot = {
          snapshotCalls.incrementAndGet()
          delegate.snapshot(query, nowMs)
        }
        def reset(): Unit    = delegate.reset()
        def shutdown(): Unit = delegate.shutdown()
      }

      try {
        FormRunnerAnalytics.setStore(customStore)
        val _ = FormRunnerAnalytics.buildAnalyticsSnapshotDom(scope = AnalyticsScope.GlobalScope)
        assert(snapshotCalls.get() == 1)
      } finally {
        FormRunnerAnalytics.resetToDefaultInMemoryStore()
      }
    }

    it("must aggregate SQLite submitted data, drafts, workflow stages, published inventory, user breakdowns, and ConsoleChartSpec JSON") {
      withTempSQLite { connection =>
        val nowMs     = 1_765_000_000_000L
        val tenMinAgo = nowMs - 10L * 60L * 1000L
        val twoDaysAgo = nowMs - 2L * 24L * 60L * 60L * 1000L

        def insertFormDef(
          app       : String,
          form      : String,
          version   : Int,
          titleEn   : String,
          available : Boolean,
          modBy     : String,
          modAtMs   : Long
        ): Unit = {
          val metadataXml =
            s"""<metadata>
               |  <application-name>$app</application-name>
               |  <form-name>$form</form-name>
               |  <title xml:lang="en">$titleEn</title>
               |  <available>$available</available>
               |</metadata>""".stripMargin
          val sql =
            """INSERT INTO orbeon_form_definition (
              |    created, last_modified_time, last_modified_by,
              |    app, form, form_version, form_metadata, deleted, xml
              |) VALUES (?, ?, ?, ?, ?, ?, ?, 'N', '<xh:html xmlns:xh="http://www.w3.org/1999/xhtml"/>')""".stripMargin
          useAndClose(connection.prepareStatement(sql)) { ps =>
            val ts = new Timestamp(modAtMs)
            ps.setTimestamp(1, ts)
            ps.setTimestamp(2, ts)
            ps.setString   (3, modBy)
            ps.setString   (4, app)
            ps.setString   (5, form)
            ps.setInt      (6, version)
            ps.setString   (7, metadataXml)
            ps.executeUpdate()
          }
        }

        var nextDataId = 100
        def insertCurrentRow(
          conn        : Connection,
          app         : String,
          form        : String,
          version     : Int,
          docId       : String,
          draft       : Boolean,
          stageOpt    : Option[String],
          usernameOpt : Option[String],
          modAtMs     : Long
        ): Unit = {
          nextDataId += 1
          val dataId   = nextDataId
          val draftStr = if (draft) "Y" else "N"
          val ts       = new Timestamp(modAtMs)

          val dataSql =
            """INSERT INTO orbeon_form_data (
              |    id, created, last_modified_time, last_modified_by, username,
              |    app, form, form_version, stage, document_id, draft, deleted, xml
              |) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'N', '<form/>')""".stripMargin
          useAndClose(conn.prepareStatement(dataSql)) { ps =>
            ps.setInt      (1, dataId)
            ps.setTimestamp(2, ts)
            ps.setTimestamp(3, ts)
            usernameOpt.fold(ps.setNull(4, Types.VARCHAR))(ps.setString(4, _))
            usernameOpt.fold(ps.setNull(5, Types.VARCHAR))(ps.setString(5, _))
            ps.setString   (6, app)
            ps.setString   (7, form)
            ps.setInt      (8, version)
            stageOpt.fold(ps.setNull(9, Types.VARCHAR))(ps.setString(9, _))
            ps.setString   (10, docId)
            ps.setString   (11, draftStr)
            ps.executeUpdate()
          }

          val currSql =
            """INSERT INTO orbeon_i_current (
              |    data_id, created, last_modified_time, last_modified_by, username,
              |    app, form, form_version, stage, document_id, draft
              |) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""".stripMargin
          useAndClose(conn.prepareStatement(currSql)) { ps =>
            ps.setInt      (1, dataId)
            ps.setTimestamp(2, ts)
            ps.setTimestamp(3, ts)
            usernameOpt.fold(ps.setNull(4, Types.VARCHAR))(ps.setString(4, _))
            usernameOpt.fold(ps.setNull(5, Types.VARCHAR))(ps.setString(5, _))
            ps.setString   (6, app)
            ps.setString   (7, form)
            ps.setInt      (8, version)
            stageOpt.fold(ps.setNull(9, Types.VARCHAR))(ps.setString(9, _))
            ps.setString   (10, docId)
            ps.setString   (11, draftStr)
            ps.executeUpdate()
          }
        }

        // Published definitions: `acme/order` (v1 + v2 available), `acme/legacy` (v1 unavailable), `hr/onboarding` (v1), `secret/payroll` (v1)
        insertFormDef("acme",   "order",      1, "Customer Order",    available = true,  modBy = "publisher-1", modAtMs = twoDaysAgo)
        insertFormDef("acme",   "order",      2, "Customer Order v2", available = true,  modBy = "publisher-1", modAtMs = tenMinAgo)
        insertFormDef("acme",   "legacy",     1, "Legacy Order",      available = false, modBy = "publisher-2", modAtMs = twoDaysAgo)
        insertFormDef("hr",     "onboarding", 1, "Employee Onboard",  available = true,  modBy = "hr-builder",  modAtMs = tenMinAgo)
        insertFormDef("secret", "payroll",    1, "Executive Payroll", available = true,  modBy = "sec-admin",   modAtMs = tenMinAgo)

        // Persisted submissions & drafts
        insertCurrentRow(connection, "acme",   "order",      2, "doc-1", draft = false, Some("submitted"), Some("alice"), tenMinAgo)
        insertCurrentRow(connection, "acme",   "order",      2, "doc-2", draft = false, Some("approved"),  Some("alice"), tenMinAgo)
        insertCurrentRow(connection, "acme",   "order",      2, "doc-3", draft = false, Some("submitted"), None,          twoDaysAgo)
        insertCurrentRow(connection, "acme",   "order",      2, "doc-4", draft = true,  None,              Some("bob"),   tenMinAgo)
        insertCurrentRow(connection, "hr",     "onboarding", 1, "doc-5", draft = false, Some("review"),    Some("bob"),   tenMinAgo)
        insertCurrentRow(connection, "secret", "payroll",    1, "doc-6", draft = false, Some("locked"),    Some("ceo"),   tenMinAgo)

        val sqliteProps = List(
          PropertyParams(Map.empty, "oxf.fr.persistence.provider.acme.*.*",   XS_STRING_QNAME, "sqlite"),
          PropertyParams(Map.empty, "oxf.fr.persistence.provider.hr.*.*",     XS_STRING_QNAME, "sqlite"),
          PropertyParams(Map.empty, "oxf.fr.persistence.provider.secret.*.*", XS_STRING_QNAME, "sqlite")
        )

        withTestExternalContext { implicit ec =>
          propertiesOverride(sqliteProps*).foreach { case (k, v) =>
            ec.getRequest.getAttributesMap.put(k, v)
          }

          // 1. Scoped snapshot (`acme/*` + `hr/onboarding`) on 1h window: `secret/payroll` must be excluded!
          val scoped = AnalyticsScope(Map("acme" -> Set("*"), "hr" -> Set("onboarding")))
          val snap1hRoot =
            FormRunnerAnalytics.buildAnalyticsSnapshotDom(
              scope            = scoped,
              windowRaw        = "1h",
              preferredLangOpt = Some("en"),
              nowMs            = nowMs
            ).getRootElement

          val kpis1h = snap1hRoot.element("kpis")
          assert(kpis1h.attributeValue("total-submitted")       == "4") // 3 on acme/order + 1 on hr/onboarding (`secret/payroll` excluded)
          assert(kpis1h.attributeValue("window-data-submitted") == "3") // `doc-3` is 2 days old, so 3 in 1h window!
          assert(kpis1h.attributeValue("total-drafts")          == "1")
          assert(kpis1h.attributeValue("published-forms")       == "3") // acme/order, acme/legacy, hr/onboarding
          assert(kpis1h.attributeValue("available-forms")       == "2")
          assert(kpis1h.attributeValue("unavailable-forms")     == "1")
          assert(kpis1h.attributeValue("distinct-apps")         == "2")

          // Verify `<filters>` contains only authorized apps (`acme`, `hr`)
          val filterApps = snap1hRoot.element("filters").elements("app").map(_.attributeValue("name")).toSet
          assert(filterApps == Set("acme", "hr"))

          // Verify `<submitted-data>` stages & form rows
          val stages = snap1hRoot.element("submitted-data").element("stages").elements("stage").toList
          assert(stages.exists(s => s.attributeValue("app") == "acme" && s.attributeValue("stage") == "submitted" && s.attributeValue("submitted") == "2"))
          assert(stages.exists(s => s.attributeValue("app") == "acme" && s.attributeValue("stage") == "approved"  && s.attributeValue("submitted") == "1"))
          assert(! stages.exists(_.attributeValue("app") == "secret"))

          // Verify `<published-forms>` multi-version aggregation (`acme/order` has 2 versions, latest = 2)
          val pubOrder =
            snap1hRoot.element("published-forms").element("forms").elements("form")
              .find(f => f.attributeValue("app") == "acme" && f.attributeValue("form") == "order").get
          assert(pubOrder.attributeValue("latest-version") == "2")
          assert(pubOrder.attributeValue("versions-count") == "2")
          assert(pubOrder.attributeValue("title")          == "Customer Order v2")
          assert(pubOrder.attributeValue("submitted")      == "3")
          assert(pubOrder.attributeValue("drafts")         == "1")

          // Verify `<users>` breakdown (`alice` = 2, `bob` = 1, anonymous = 1; `ceo` excluded)
          val usersElem = snap1hRoot.element("users")
          assert(usersElem.attributeValue("auth-submitted")            == "3")
          assert(usersElem.attributeValue("anon-submitted")            == "1")
          assert(usersElem.attributeValue("distinct-submitting-users") == "2")
          val subUsers = usersElem.element("submitting-users").elements("user").toList
          assert(subUsers.exists(u => u.attributeValue("display-name") == "alice" && u.attributeValue("submitted") == "2"))
          assert(subUsers.exists(u => u.attributeValue("display-name") == "bob"   && u.attributeValue("submitted") == "1"))
          assert(! subUsers.exists(_.attributeValue("display-name") == "ceo"))

          // 2. Verify `7d` window includes `twoDaysAgo` (`doc-3`), and all 10 `ConsoleChartSpec` JSON payloads decode cleanly
          val snap7dRoot =
            FormRunnerAnalytics.buildAnalyticsSnapshotDom(
              scope            = scoped,
              windowRaw        = "7d",
              preferredLangOpt = Some("en"),
              nowMs            = nowMs
            ).getRootElement
          assert(snap7dRoot.element("kpis").attributeValue("window-data-submitted") == "4")

          val chartElems = snap7dRoot.element("charts").elements("chart").toList
          assert(chartElems.map(_.attributeValue("id")).toSet == ExpectedChartIds)
          chartElems.foreach { elem =>
            val id   = elem.attributeValue("id")
            val spec = decode[ConsoleChartSpec](elem.getText).fold(err => fail(s"Failed to decode chart $id: $err"), identity)
            assert(spec.series.nonEmpty, s"Chart $id must declare series")
            if (spec.kind == "timeseries") {
              assert(spec.window == "7d")
              assert(spec.timestampsMs.size == AnalyticsWindow.Window7d.bucketCount)
              assert(spec.series.forall(_.values.size == spec.timestampsMs.size))
            } else {
              assert(spec.kind == "hbar")
              assert(spec.fullCategories.size == spec.categories.size)
              assert(spec.series.forall(_.values.size == spec.categories.size))
            }
          }

          // 3. Verify `oxf.fr.analytics.show-usernames = false` masks usernames across XML and chart JSON
          val maskedProps = sqliteProps :+ PropertyParams(Map.empty, "oxf.fr.analytics.show-usernames", XS_BOOLEAN_QNAME, "false")
          propertiesOverride(maskedProps*).foreach { case (k, v) =>
            ec.getRequest.getAttributesMap.put(k, v)
          }

          val maskedRoot =
            FormRunnerAnalytics.buildAnalyticsSnapshotDom(
              scope            = scoped,
              windowRaw        = "7d",
              preferredLangOpt = Some("en"),
              nowMs            = nowMs
            ).getRootElement

          assert(maskedRoot.attributeValue("show-usernames") == "false")
          val maskedUserNames =
            maskedRoot.element("users").element("submitting-users").elements("user").map(_.attributeValue("display-name")).toSet
          assert(! maskedUserNames.contains("alice"))
          assert(! maskedUserNames.contains("bob"))
          assert(maskedUserNames.contains("user-1"))
          assert(maskedUserNames.contains("user-2"))

          val maskedPubModBy =
            maskedRoot.element("published-forms").element("forms").elements("form").map(_.attributeValue("last-modified-by")).toSet
          assert(maskedPubModBy == Set("***"))

          val usersChartJson =
            maskedRoot.element("charts").elements("chart").find(_.attributeValue("id") == "users-submissions-breakdown").get.getText
          assert(! usersChartJson.contains("alice"))
          assert(! usersChartJson.contains("bob"))
        }
      }
    }
  }
}
