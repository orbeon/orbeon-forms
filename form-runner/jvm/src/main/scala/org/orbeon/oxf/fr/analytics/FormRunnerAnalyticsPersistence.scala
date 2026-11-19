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

import org.orbeon.oxf.externalcontext.Credentials
import org.orbeon.oxf.fr.*
import org.orbeon.oxf.fr.FormRunnerPersistence.{getProvidersWithProperties, isInternalProvider, providerPropertyOpt}
import org.orbeon.oxf.fr.permission.{Operations, PermissionsAuthorization}
import org.orbeon.oxf.fr.persistence.relational.Statement.{Setter, StatementPart, executeQuery}
import org.orbeon.oxf.fr.persistence.relational.{Provider, RelationalUtils}
import org.orbeon.oxf.properties.PropertySet
import org.orbeon.oxf.util.CollectionUtils.fromIteratorExt
import org.orbeon.oxf.util.StringUtils.*
import org.orbeon.oxf.util.{CoreCrossPlatformSupport, IndentedLogger, XPath}
import org.orbeon.scaxon.SimplePath.NodeInfoOps
import org.orbeon.xforms.XFormsCrossPlatformSupport

import java.sql.{Connection, Timestamp}
import java.time.Instant
import scala.collection.immutable.ListMap
import scala.collection.mutable
import scala.util.control.NonFatal


object FormRunnerAnalyticsPersistence {

  private val BuiltInServiceUriRe = """^/fr/service/(oracle|mysql|postgresql|db2|sqlserver|sqlite)(?:/.*)?$""".r

  final case class SubmittedFormDataRow(
    appForm              : AppForm,
    version              : Int,
    titleOpt             : Option[String],
    submittedCount       : Long,
    windowSubmittedCount : Long,
    draftCount           : Long,
    latestModifiedMsOpt  : Option[Long],
    isSummaryAllowed     : Boolean
  )

  final case class WorkflowStageRow(
    appForm        : AppForm,
    stage          : String,
    submittedCount : Long,
    sharePercent   : Double
  )

  final case class UserSubmissionRow(
    userDisplay     : String,
    isAuthenticated : Boolean,
    distinctForms   : Int,
    submittedCount  : Long,
    sharePercent    : Double
  )

  final case class AppInventoryRow(
    app              : String,
    publishedForms   : Int,
    totalVersions    : Int,
    availableForms   : Int,
    unavailableForms : Int,
    totalSubmitted   : Long,
    totalDrafts      : Long
  )

  final case class PublishedFormInventoryRow(
    appForm           : AppForm,
    titleOpt          : Option[String],
    latestVersion     : Int,
    versionsCount     : Int,
    available         : Boolean,
    lastModifiedMs    : Long,
    lastModifiedByOpt : Option[String],
    submittedCount    : Long,
    draftCount        : Long,
    isSummaryAllowed  : Boolean
  )

  final case class DataTimeBucketPoint(
    timestampMs    : Long,
    submittedCount : Long,
    draftCount     : Long
  )

  final case class PersistenceAnalyticsSnapshot(
    totalSubmitted          : Long,
    windowSubmitted         : Long,
    totalDrafts             : Long,
    windowDrafts            : Long,
    authenticatedSubmitted  : Long,
    anonymousSubmitted      : Long,
    distinctSubmittingUsers : Int,
    distinctAppsCount       : Int,
    publishedFormsCount     : Int,
    availableFormsCount     : Int,
    unavailableFormsCount   : Int,
    submittedByForm         : List[SubmittedFormDataRow],
    workflowStages          : List[WorkflowStageRow],
    userSubmissions         : List[UserSubmissionRow],
    appInventory            : List[AppInventoryRow],
    publishedForms          : List[PublishedFormInventoryRow],
    timeSeries              : List[DataTimeBucketPoint],
    discoveredApps          : Set[String],
    discoveredFormsByApp    : Map[String, Set[String]]
  )

  private final case class ResolvedRelationalProvider(
    providerName   : String,
    provider       : Provider,
    datasourceName : String
  )

  private final case class RawPublishedVersion(
    appForm           : AppForm,
    version           : Int,
    titlesByLang      : ListMap[String, String],
    available         : Boolean,
    lastModifiedMs    : Long,
    lastModifiedByOpt : Option[String],
    isSummaryAllowed  : Boolean
  )

  def queryPersistenceSnapshot(
    query            : AnalyticsQuery,
    preferredLangOpt : Option[String],
    nowMs            : Long
  ): PersistenceAnalyticsSnapshot = {
    implicit val propertySet   : PropertySet    = CoreCrossPlatformSupport.properties
    implicit val indentedLogger: IndentedLogger = RelationalUtils.newIndentedLogger

    val fbPermissions  = FormRunner.formBuilderPermissions(FormRunner.formBuilderPermissionsConfiguration, AnalyticsScope.currentRequestRolesSafe)
    val credentialsOpt = Option(CoreCrossPlatformSupport.externalContext).flatMap(ec => Option(ec.getRequest)).flatMap(_.credentials)

    val dataProviders = discoverRelationalProviders(query.appFilterOpt, query.formFilterOpt, Some(FormOrData.Data))
    val formProviders = discoverRelationalProviders(query.appFilterOpt, query.formFilterOpt, Some(FormOrData.Form))

    val allDiscoveredApps        = mutable.HashSet.empty[String]
    val allDiscoveredFormsByApp  = mutable.HashMap.empty[String, mutable.HashSet[String]]

    def recordDiscovered(af: AppForm): Unit =
      if (af.form != Names.LibraryFormName && af != AppForm("orbeon", "builder") && af != AppForm("orbeon", "configuration") && query.scope.allowsAppForm(af)) {
        allDiscoveredApps += af.app
        allDiscoveredFormsByApp.getOrElseUpdate(af.app, mutable.HashSet.empty) += af.form
      }

    // 1. Collect published form definitions across form providers
    val rawVersionsBuffer = mutable.ListBuffer.empty[RawPublishedVersion]
    val seenDatasourceForm = mutable.HashSet.empty[String]

    formProviders.foreach { rp =>
      if (seenDatasourceForm.add(s"${rp.provider.entryName}:${rp.datasourceName}")) {
        try {
          RelationalUtils.withConnectionForDatasource(rp.provider, rp.datasourceName) { connection =>
            queryPublishedFormVersions(connection, query, fbPermissions, credentialsOpt, recordDiscovered).foreach { v =>
              if (FormRunnerPersistence.findProvider(v.appForm, FormOrData.Form).contains(rp.providerName) || formProviders.size == 1)
                rawVersionsBuffer += v
            }
          }
        } catch {
          case NonFatal(_) =>
            // Datasource not configured in current environment (e.g. unit test without JNDI DB)
            ()
        }
      }
    }

    // Deduplicate by (appForm, version)
    val distinctPublishedVersions: List[RawPublishedVersion] =
      rawVersionsBuffer
        .groupBy(v => v.appForm -> v.version)
        .valuesIterator
        .map(_.maxBy(_.lastModifiedMs))
        .toList

    def resolveTitle(titles: ListMap[String, String]): Option[String] =
      preferredLangOpt.flatMap(titles.get).filter(_.nonAllBlank)
        .orElse(titles.get("en").filter(_.nonAllBlank))
        .orElse(titles.valuesIterator.find(_.nonAllBlank))

    val summaryAllowedByAppForm: Map[AppForm, Boolean] =
      distinctPublishedVersions
        .groupBy(_.appForm)
        .view
        .mapValues(_.exists(_.isSummaryAllowed))
        .toMap

    val titleByAppFormVersion: Map[(AppForm, Int), Option[String]] =
      distinctPublishedVersions
        .map(v => (v.appForm -> v.version) -> resolveTitle(v.titlesByLang))
        .toMap

    val titleByAppForm: Map[AppForm, Option[String]] =
      distinctPublishedVersions
        .groupBy(_.appForm)
        .view
        .mapValues(vs => resolveTitle(vs.maxBy(_.version).titlesByLang))
        .toMap

    // 2. Collect submitted data & draft aggregates across data providers
    // Keyed by (AppForm, version)
    final class FormVersionAgg {
      var submittedCount       : Long = 0L
      var windowSubmittedCount : Long = 0L
      var draftCount           : Long = 0L
      var latestModifiedMs     : Long = -1L
    }

    val aggByFormVersion = mutable.HashMap.empty[(AppForm, Int), FormVersionAgg]
    val stageCounts      = mutable.HashMap.empty[(AppForm, String), Long]
    val userCounts       = mutable.HashMap.empty[String, (Long, mutable.HashSet[AppForm])]
    var anonSubmitted    : Long = 0L
    var authSubmitted    : Long = 0L

    val window         = query.window
    val currentAligned = (nowMs / window.bucketWidthMs) * window.bucketWidthMs
    val firstAligned   = currentAligned - (window.bucketCount - 1).toLong * window.bucketWidthMs
    val submittedSeries = new Array[Long](window.bucketCount)
    val draftSeries     = new Array[Long](window.bucketCount)
    var windowSubmittedTotal : Long = 0L
    var windowDraftTotal     : Long = 0L

    val seenDatasourceData = mutable.HashSet.empty[String]

    dataProviders.foreach { rp =>
      if (seenDatasourceData.add(s"${rp.provider.entryName}:${rp.datasourceName}")) {
        try {
          RelationalUtils.withConnectionForDatasource(rp.provider, rp.datasourceName) { connection =>
            def ownsDataFor(af: AppForm): Boolean =
              af.form != Names.LibraryFormName &&
                af != AppForm("orbeon", "builder") &&
                af != AppForm("orbeon", "configuration") &&
                (FormRunnerPersistence.findProvider(af, FormOrData.Data).contains(rp.providerName) || dataProviders.size == 1)

            // Unfiltered discovery of apps/forms in `orbeon_i_current` so dropdowns stay populated even when filtered
            queryDistinctDataAppForms(connection).foreach { af =>
              if (ownsDataFor(af))
                recordDiscovered(af)
            }

            val (whereSql, whereSetters) = buildOptionalAppFormWhere(query, tableAlias = "c", includeWhereKeyword = true)

            // 2A. Totals by (app, form, form_version, draft)
            val totalsSql =
              s"""SELECT c.app, c.form, c.form_version, c.draft, COUNT(*) AS cnt, MAX(c.last_modified_time) AS latest_mod
                 |FROM orbeon_i_current c
                 |$whereSql
                 |GROUP BY c.app, c.form, c.form_version, c.draft
                 |""".stripMargin

            executeQuery(connection, totalsSql, List(StatementPart(totalsSql, whereSetters))) { rs =>
              while (rs.next()) {
                val af = AppForm(rs.getString("app"), rs.getString("form"))
                if (ownsDataFor(af) && query.matchesAppForm(af)) {
                  val ver   = rs.getInt("form_version")
                  val draft = Option(rs.getString("draft")).exists(_.equalsIgnoreCase("Y"))
                  val cnt   = rs.getLong("cnt")
                  val modTs = Option(rs.getTimestamp("latest_mod")).map(_.getTime).getOrElse(-1L)

                  val agg = aggByFormVersion.getOrElseUpdate(af -> ver, new FormVersionAgg)
                  if (draft) agg.draftCount += cnt
                  else agg.submittedCount += cnt
                  if (modTs > agg.latestModifiedMs)
                    agg.latestModifiedMs = modTs
                }
              }
            }

            // 2B. Windowed time-series (`created >= firstAligned`)
            val (andAppFormSql, andSetters) = buildOptionalAppFormWhere(query, tableAlias = "c", includeWhereKeyword = false)
            val extraClause = if (andAppFormSql.nonEmpty) s" AND $andAppFormSql" else ""
            val windowSql =
              s"""SELECT c.app, c.form, c.form_version, c.draft, c.created
                 |FROM orbeon_i_current c
                 |WHERE c.created >= ?$extraClause
                 |""".stripMargin
            val firstAlignedSetter: Setter =
              (ps, idx) => ps.setTimestamp(idx, Timestamp.from(Instant.ofEpochMilli(firstAligned)))
            val windowSetters: List[Setter] =
              firstAlignedSetter :: andSetters

            executeQuery(connection, windowSql, List(StatementPart(windowSql, windowSetters))) { rs =>
              while (rs.next()) {
                val af = AppForm(rs.getString("app"), rs.getString("form"))
                if (ownsDataFor(af) && query.matchesAppForm(af)) {
                  val ver       = rs.getInt("form_version")
                  val draft     = Option(rs.getString("draft")).exists(_.equalsIgnoreCase("Y"))
                  val createdMs = Option(rs.getTimestamp("created")).map(_.getTime).getOrElse(-1L)
                  if (createdMs >= firstAligned) {
                    val rawIdx = ((createdMs - firstAligned) / window.bucketWidthMs).toInt
                    val idx    = math.max(0, math.min(window.bucketCount - 1, rawIdx))
                    if (draft) {
                      draftSeries(idx) += 1L
                      windowDraftTotal += 1L
                    } else {
                      submittedSeries(idx) += 1L
                      windowSubmittedTotal += 1L
                      aggByFormVersion.getOrElseUpdate(af -> ver, new FormVersionAgg).windowSubmittedCount += 1L
                    }
                  }
                }
              }
            }

            // 2C. Workflow stage breakdown (`draft = 'N'`)
            val stageSql =
              s"""SELECT c.app, c.form, c.stage, COUNT(*) AS cnt
                 |FROM orbeon_i_current c
                 |WHERE c.draft = 'N' AND c.stage IS NOT NULL AND c.stage <> ''$extraClause
                 |GROUP BY c.app, c.form, c.stage
                 |""".stripMargin

            executeQuery(connection, stageSql, List(StatementPart(stageSql, andSetters))) { rs =>
              while (rs.next()) {
                val af    = AppForm(rs.getString("app"), rs.getString("form"))
                val stage = Option(rs.getString("stage")).flatMap(_.trimAllToOpt)
                if (ownsDataFor(af) && query.matchesAppForm(af) && stage.isDefined) {
                  val key = af -> stage.get
                  stageCounts.put(key, stageCounts.getOrElse(key, 0L) + rs.getLong("cnt"))
                }
              }
            }

            // 2D. User submission breakdown (`draft = 'N'`)
            val userSql =
              s"""SELECT c.app, c.form, c.username, COUNT(*) AS cnt
                 |FROM orbeon_i_current c
                 |WHERE c.draft = 'N'$extraClause
                 |GROUP BY c.app, c.form, c.username
                 |""".stripMargin

            executeQuery(connection, userSql, List(StatementPart(userSql, andSetters))) { rs =>
              while (rs.next()) {
                val af      = AppForm(rs.getString("app"), rs.getString("form"))
                val userOpt = Option(rs.getString("username")).flatMap(_.trimAllToOpt)
                val cnt     = rs.getLong("cnt")
                if (ownsDataFor(af) && query.matchesAppForm(af) && cnt > 0L) {
                  userOpt match {
                    case Some(u) =>
                      authSubmitted += cnt
                      val (prevCnt, formsSet) = userCounts.getOrElseUpdate(u, (0L, mutable.HashSet.empty[AppForm]))
                      formsSet += af
                      userCounts.put(u, (prevCnt + cnt, formsSet))
                    case None =>
                      anonSubmitted += cnt
                  }
                }
              }
            }
          }
        } catch {
          case NonFatal(_) =>
            ()
        }
      }
    }

    val totalSubmitted = aggByFormVersion.valuesIterator.map(_.submittedCount).sum
    val totalDrafts    = aggByFormVersion.valuesIterator.map(_.draftCount).sum

    val submittedRows: List[SubmittedFormDataRow] =
      aggByFormVersion.toList
        .map { case ((af, ver), agg) =>
          val canSummary =
            summaryAllowedByAppForm.getOrElse(
              af,
              FormRunner.hasFormBuilderPermissionForAppForm(fbPermissions, af)
            )
          SubmittedFormDataRow(
            appForm              = af,
            version              = ver,
            titleOpt             = titleByAppFormVersion.get(af -> ver).flatten.orElse(titleByAppForm.get(af).flatten),
            submittedCount       = agg.submittedCount,
            windowSubmittedCount = agg.windowSubmittedCount,
            draftCount           = agg.draftCount,
            latestModifiedMsOpt  = Option.when(agg.latestModifiedMs > 0L)(agg.latestModifiedMs),
            isSummaryAllowed     = canSummary
          )
        }
        .filter(r => r.submittedCount > 0L || r.draftCount > 0L)
        .sortBy(r => (-r.submittedCount, -r.draftCount, r.appForm.app, r.appForm.form, -r.version))

    val totalStagedCount = stageCounts.valuesIterator.sum
    val workflowStageRows: List[WorkflowStageRow] =
      stageCounts.toList
        .map { case ((af, stg), cnt) =>
          val pct = if (totalStagedCount > 0L) (cnt.toDouble / totalStagedCount.toDouble) * 100.0 else 0.0
          WorkflowStageRow(af, stg, cnt, pct)
        }
        .sortBy(r => (-r.submittedCount, r.appForm.app, r.appForm.form, r.stage))

    val userRowsBuffer = mutable.ListBuffer.empty[UserSubmissionRow]
    val grandUserTotal = authSubmitted + anonSubmitted
    if (anonSubmitted > 0L) {
      val anonFormsCnt =
        aggByFormVersion.keysIterator.map(_._1).toSet.size
      val pct = if (grandUserTotal > 0L) (anonSubmitted.toDouble / grandUserTotal.toDouble) * 100.0 else 0.0
      userRowsBuffer += UserSubmissionRow(
        userDisplay     = "",
        isAuthenticated = false,
        distinctForms   = anonFormsCnt,
        submittedCount  = anonSubmitted,
        sharePercent    = pct
      )
    }

    userCounts.toList
      .sortBy { case (u, (cnt, _)) => (-cnt, u) }
      .zipWithIndex
      .foreach { case ((u, (cnt, formsSet)), idx) =>
        val pct  = if (grandUserTotal > 0L) (cnt.toDouble / grandUserTotal.toDouble) * 100.0 else 0.0
        val disp = if (query.showUsernames) u else s"user-${idx + 1}"
        userRowsBuffer += UserSubmissionRow(
          userDisplay     = disp,
          isAuthenticated = true,
          distinctForms   = formsSet.size,
          submittedCount  = cnt,
          sharePercent    = pct
        )
      }

    val userSubmissionRows =
      userRowsBuffer.toList.sortBy(r => (-r.submittedCount, r.userDisplay)).take(100)

    // 3. Build Published Forms Inventory (grouped by AppForm across versions) & App Inventory
    val matchingPublishedVersions =
      distinctPublishedVersions.filter(v => query.matchesAppForm(v.appForm))

    val submittedByAppForm: Map[AppForm, (Long, Long)] =
      aggByFormVersion.toList
        .groupBy(_._1._1)
        .view
        .mapValues { entries =>
          (entries.iterator.map(_._2.submittedCount).sum, entries.iterator.map(_._2.draftCount).sum)
        }
        .toMap

    val publishedFormRows: List[PublishedFormInventoryRow] =
      matchingPublishedVersions
        .groupBy(_.appForm)
        .toList
        .map { case (af, versions) =>
          val latestV = versions.maxBy(_.version)
          val (subCnt, drfCnt) = submittedByAppForm.getOrElse(af, (0L, 0L))
          PublishedFormInventoryRow(
            appForm           = af,
            titleOpt          = resolveTitle(latestV.titlesByLang),
            latestVersion     = latestV.version,
            versionsCount     = versions.size,
            available         = latestV.available,
            lastModifiedMs    = latestV.lastModifiedMs,
            lastModifiedByOpt = latestV.lastModifiedByOpt.map(u => if (query.showUsernames) u else "***"),
            submittedCount    = subCnt,
            draftCount        = drfCnt,
            isSummaryAllowed  = versions.exists(_.isSummaryAllowed)
          )
        }
        .sortBy(r => (r.appForm.app, r.appForm.form))

    val allScopedAppsForInventory =
      (publishedFormRows.iterator.map(_.appForm.app) ++ submittedByAppForm.keysIterator.filter(query.matchesAppForm).map(_.app)).toSet

    val appInventoryRows: List[AppInventoryRow] =
      allScopedAppsForInventory.toList
        .map { app =>
          val formsInApp    = publishedFormRows.filter(_.appForm.app == app)
          val versionsInApp = matchingPublishedVersions.count(_.appForm.app == app)
          val availCnt      = formsInApp.count(_.available)
          val unavailCnt    = formsInApp.size - availCnt
          val subSum        = submittedByAppForm.iterator.collect { case (af, (s, _)) if af.app == app && query.matchesAppForm(af) => s }.sum
          val drfSum        = submittedByAppForm.iterator.collect { case (af, (_, d)) if af.app == app && query.matchesAppForm(af) => d }.sum
          AppInventoryRow(
            app              = app,
            publishedForms   = formsInApp.size,
            totalVersions    = versionsInApp,
            availableForms   = availCnt,
            unavailableForms = unavailCnt,
            totalSubmitted   = subSum,
            totalDrafts      = drfSum
          )
        }
        .sortBy(r => (-r.totalSubmitted, -r.publishedForms, r.app))

    val dataSeries: List[DataTimeBucketPoint] =
      (0 until window.bucketCount).map { i =>
        DataTimeBucketPoint(
          timestampMs    = firstAligned + i.toLong * window.bucketWidthMs,
          submittedCount = submittedSeries(i),
          draftCount     = draftSeries(i)
        )
      }.toList

    PersistenceAnalyticsSnapshot(
      totalSubmitted          = totalSubmitted,
      windowSubmitted         = windowSubmittedTotal,
      totalDrafts             = totalDrafts,
      windowDrafts            = windowDraftTotal,
      authenticatedSubmitted  = authSubmitted,
      anonymousSubmitted      = anonSubmitted,
      distinctSubmittingUsers = userCounts.size,
      distinctAppsCount       = appInventoryRows.size,
      publishedFormsCount     = publishedFormRows.size,
      availableFormsCount     = publishedFormRows.count(_.available),
      unavailableFormsCount   = publishedFormRows.count(! _.available),
      submittedByForm         = submittedRows,
      workflowStages          = workflowStageRows,
      userSubmissions         = userSubmissionRows,
      appInventory            = appInventoryRows,
      publishedForms          = publishedFormRows,
      timeSeries              = dataSeries,
      discoveredApps          = allDiscoveredApps.toSet,
      discoveredFormsByApp    = allDiscoveredFormsByApp.view.mapValues(_.toSet).toMap
    )
  }

  private def discoverRelationalProviders(
    appOpt     : Option[String],
    formOpt    : Option[String],
    formOrData : Option[FormOrData]
  )(implicit
    propertySet: PropertySet
  ): List[ResolvedRelationalProvider] = {
    val providerNames =
      getProvidersWithProperties(None, None, formOrData).keys.toList.sorted

    providerNames.flatMap { providerName =>
      if (providerName != RelationalUtils.ResourceProvider && isInternalProvider(providerName)) {
        val providerEnumOpt =
          Provider.withNameInsensitiveOption(providerName).orElse {
            providerPropertyOpt(providerName, "uri")
              .flatMap(_.nonBlankStringValue)
              .collect { case BuiltInServiceUriRe(dbType) => Provider.withNameInsensitiveOption(dbType) }
              .flatten
          }
        val dsOpt = providerPropertyOpt(providerName, "datasource").flatMap(_.nonBlankStringValue)
        for {
          p  <- providerEnumOpt
          ds <- dsOpt
        } yield ResolvedRelationalProvider(providerName, p, ds)
      } else {
        None
      }
    }
  }

  private def buildOptionalAppFormWhere(
    query               : AnalyticsQuery,
    tableAlias          : String,
    includeWhereKeyword : Boolean
  ): (String, List[Setter]) = {
    val clauses = List(
      query.appFilterOpt.map(app => (s"$tableAlias.app = ?", ((ps, idx) => ps.setString(idx, app)): Setter)),
      query.formFilterOpt.map(frm => (s"$tableAlias.form = ?", ((ps, idx) => ps.setString(idx, frm)): Setter))
    ).flatten

    if (clauses.isEmpty) {
      ("" -> Nil)
    } else {
      val joined = clauses.map(_._1).mkString(" AND ")
      val sql    = if (includeWhereKeyword) s"WHERE $joined" else joined
      (sql -> clauses.map(_._2))
    }
  }

  private def queryDistinctDataAppForms(connection: Connection)(implicit indentedLogger: IndentedLogger): List[AppForm] = {
    val sql = "SELECT DISTINCT app, form FROM orbeon_i_current"
    executeQuery(connection, sql, List(StatementPart(sql, Nil))) { rs =>
      Iterator.iterateWhile(
        cond = rs.next(),
        elem = AppForm(rs.getString("app"), rs.getString("form"))
      ).toList
    }
  }

  private def queryPublishedFormVersions(
    connection       : Connection,
    query            : AnalyticsQuery,
    fbPermissions    : Map[String, Set[String]],
    credentialsOpt   : Option[Credentials],
    recordDiscovered : AppForm => Unit
  )(implicit
    indentedLogger   : IndentedLogger
  ): List[RawPublishedVersion] = {
    val sql =
      """SELECT
        |    d.app                application_name,
        |    d.form               form_name,
        |    d.form_version       form_version,
        |    d.form_metadata      form_metadata,
        |    d.last_modified_time last_modified_time,
        |    d.last_modified_by   last_modified_by
        |FROM
        |    orbeon_form_definition d,
        |    (
        |        SELECT
        |            app,
        |            form,
        |            form_version,
        |            MAX(last_modified_time) last_modified_time
        |        FROM
        |            orbeon_form_definition
        |        GROUP BY
        |            app, form, form_version
        |    ) latest
        |WHERE
        |    d.app                = latest.app                AND
        |    d.form               = latest.form               AND
        |    d.form_version       = latest.form_version       AND
        |    d.last_modified_time = latest.last_modified_time AND
        |    d.deleted            = 'N'
        |""".stripMargin

    executeQuery(connection, sql, List(StatementPart(sql, Nil))) { rs =>
      val b = List.newBuilder[RawPublishedVersion]
      while (rs.next()) {
        val af = AppForm(rs.getString("application_name"), rs.getString("form_name"))
        if (af.form != Names.LibraryFormName && af != AppForm("orbeon", "builder") && af != AppForm("orbeon", "configuration")) {
          recordDiscovered(af)
          if (query.scope.allowsAppForm(af)) {
            val (titles, available, permissionsOpt) =
              Option(rs.getString("form_metadata")).map(_.trim).filter(_.nonEmpty).map { metadataXmlStr =>
                val xml = XFormsCrossPlatformSupport.stringToTinyTree(
                  configuration  = XPath.GlobalConfiguration,
                  string         = metadataXmlStr,
                  handleXInclude = false,
                  handleLexical  = false
                ).rootElement

                val t = ListMap.from((xml / "title").map(n => n.attValue("*:lang") -> n.stringValue))
                val avail = xml.elemValueOpt("available").forall(_ != "false")
                val perms = xml.child("permissions").headOption
                (t, avail, perms)
              }.getOrElse((ListMap.empty[String, String], true, None))

            val hasAdmin = FormRunner.hasFormBuilderPermissionForAppForm(fbPermissions, af)
            val ops =
              Operations.serialize(
                PermissionsAuthorization.authorizedOperationsForNoData(
                  permissions    = FormRunner.permissionsFromElemOrProperties(permissionsOpt, af, propertyProfileOpt = None),
                  credentialsOpt = credentialsOpt
                ),
                normalized = true
              )
            val summaryOps = org.orbeon.oxf.fr.persistence.relational.form.adt.Form.SummaryOps
            val isSummaryAllowed = hasAdmin || ops.exists(summaryOps.contains)

            b += RawPublishedVersion(
              appForm           = af,
              version           = rs.getInt("form_version"),
              titlesByLang      = titles,
              available         = available,
              lastModifiedMs    = Option(rs.getTimestamp("last_modified_time")).map(_.getTime).getOrElse(0L),
              lastModifiedByOpt = Option(rs.getString("last_modified_by")).flatMap(_.trimAllToOpt),
              isSummaryAllowed  = isSummaryAllowed
            )
          }
        }
      }
      b.result()
    }
  }
}
