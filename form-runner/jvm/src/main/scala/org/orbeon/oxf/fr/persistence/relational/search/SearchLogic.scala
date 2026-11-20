/**
 * Copyright (C) 2016 Orbeon, Inc.
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
package org.orbeon.oxf.fr.persistence.relational.search

import org.orbeon.oxf.externalcontext.{ExternalContext, Organization, UserAndGroup}
import org.orbeon.oxf.fr.SearchVersion
import org.orbeon.oxf.fr.Version.OrbeonFormDefinitionVersion
import org.orbeon.oxf.fr.permission.PermissionsAuthorization.CheckWithDataUser
import org.orbeon.oxf.fr.permission.*
import org.orbeon.oxf.fr.persistence.PersistenceMetadataSupport
import org.orbeon.oxf.fr.persistence.relational.Statement.*
import org.orbeon.oxf.fr.persistence.relational.rest.{OrganizationId, OrganizationSupport}
import org.orbeon.oxf.fr.persistence.relational.search.adt.Metadata.LastModified
import org.orbeon.oxf.fr.persistence.relational.search.adt.*
import org.orbeon.oxf.fr.persistence.relational.search.part.*
import org.orbeon.oxf.fr.persistence.relational.{Provider, RelationalUtils}
import org.orbeon.oxf.util.CollectionUtils.*
import org.orbeon.oxf.util.CoreUtils.*
import org.orbeon.oxf.util.IndentedLogger
import org.orbeon.oxf.util.Logging.*
import org.orbeon.oxf.util.StringUtils.*
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.SimplePath.{Document as _, *}

import java.sql.Connection
import scala.collection.mutable


object SearchLogic {

  case class PositionedDocument(
    document: Document,
    position: Int
  )

  case class AroundSearchResult(
    total          : Int,
    currentPosition: Option[Int],
    previous       : Option[PositionedDocument],
    current        : Option[PositionedDocument],
    next           : Option[PositionedDocument]
  )

  private case class PreparedSearchQuery(
    innerSQL          : String,
    statementParts    : List[StatementPart],
    orderedSubquerySQL: String,
    allStatementParts : List[StatementPart]
  )

  private def prepareSearchQuery(
    request                  : SearchRequest,
    connection               : Connection,
    commonAndPermissionsParts: List[StatementPart]
  ): PreparedSearchQuery = {
    val statementParts = commonAndPermissionsParts ++ metadataPart(request).toList ++ List(
      columnFilterPart  (request),
      draftsPart        (request),
      freeTextFilterPart(request)
    )
    val innerSQL       = buildQuery(statementParts)
    val orderDirection = request.orderQuery.orderDirection.get

    // We'll refer to the order by column as "sort_column"
    val (orderByAliasing, orderByStatementPartOpt) = request.orderQuery match {
      case controlQuery @ ControlQuery(controlPath, _, _) =>
        val aggregateFunction = orderDirection match {
          case Ascending  => "MIN"
          case Descending => "MAX"
        }
        val controlColumn = s"t.${controlQuery.sqlColumn}"
        // DB2, Oracle, and SQL Server use CLOB/NTEXT data types for control values, which cannot be used in ORDER
        // BY clauses, so we cast them to VARCHAR.
        val controlColumnWithCast = request.provider match {
          case _         => controlColumn
        }
        (
          s"(SELECT $aggregateFunction($controlColumnWithCast) FROM orbeon_i_control_text t WHERE t.data_id = s.data_id AND t.control = ?) AS sort_column",
          Some(StatementPart("", List[Setter]((ps, i) => ps.setString(i, controlPath))))
        )
      case _ =>
        (
          s"c.${request.orderQuery.sqlColumn} AS sort_column",
          None
        )
    }

    // First order by clause (specified by request), with CAST if necessary
    val firstOrderByClause = s"d.sort_column ${orderDirection.sql}"

    // Second order by clause (we order by last_modified_time DESC as well, if it makes sense)
    val secondOrderByClause = request.orderQuery match {
      case MetadataQuery(LastModified, _, _) => ""
      case _                                 => ", d.last_modified_time DESC"
    }

    // Deterministic tie-breaker so Summary pagination and Detail prev/next always see identical ordering
    val orderByClauses = firstOrderByClause + secondOrderByClause + ", d.data_id DESC"

    val rowNumSQL     = Provider.rowNumSQL(
      provider   = request.provider,
      connection = connection,
      orderBy    = orderByClauses
    )
    val rowNumCol     = rowNumSQL.col
    val rowNumOrderBy = rowNumSQL.orderBy
    val rowNumTable   = rowNumSQL.table match {
      case Some(table) => table + ","
      case None        => ""
    }

    val orderedSubquerySQL =
      s"""SELECT
         |    d.*,
         |    $rowNumCol
         |FROM
         |    (
         |        SELECT
         |            c.*, $orderByAliasing
         |        FROM
         |            $rowNumTable
         |            (
         |                $innerSQL
         |            ) s
         |        INNER JOIN
         |            orbeon_i_current c
         |            ON c.data_id = s.data_id
         |    ) d
         |$rowNumOrderBy""".stripMargin

    val allStatementParts = orderByStatementPartOpt.toList ::: statementParts

    PreparedSearchQuery(
      innerSQL           = innerSQL,
      statementParts     = statementParts,
      orderedSubquerySQL = orderedSubquerySQL,
      allStatementParts  = allStatementParts
    )
  }

  private def executeSearchCount(
    connection    : Connection,
    innerSQL      : String,
    statementParts: List[StatementPart]
  )(implicit
    indentedLogger: IndentedLogger
  ): Int = {
    val sql =
      s"""SELECT count(*)
         |  FROM (
         |       $innerSQL
         |       ) a
         |""".stripMargin

    debug(s"search total query\n$sql")
    executeQuery(connection, sql, statementParts) { rs =>
      rs.next()
      rs.getInt(1)
    }
  }

  private def readDocumentMetadata(rs: java.sql.ResultSet): DocumentMetadata =
    DocumentMetadata(
      documentId       = rs.getString("document_id"),
      draft            = rs.getString("draft") == "Y",
      createdTime      = rs.getTimestamp("created"),
      lastModifiedTime = rs.getTimestamp("last_modified_time"),
      createdBy        = UserAndGroup.fromStrings(rs.getString("username"),         rs.getString("groupname")),
      lastModifiedBy   = UserAndGroup.fromStrings(rs.getString("last_modified_by"), ""),
      workflowStage    = Option(rs.getString("stage")),
      organizationId   = RelationalUtils.getIntOpt(rs, "organization_id")
    )

  private def computeOperations(
    connection        : Connection,
    request           : SearchRequest,
    permissions       : SearchPermissions,
    metadata          : DocumentMetadata,
    organizationsCache: mutable.Map[Int, Organization]
  )(implicit
    externalContext   : ExternalContext,
    indentedLogger    : IndentedLogger
  ): String = {
    def readFromDatabase(id: Int) = OrganizationSupport.read(connection, OrganizationId(id)).get
    val organization              = metadata.organizationId.map(id => organizationsCache.getOrElseUpdate(id, readFromDatabase(id)))
    val check                     = CheckWithDataUser(metadata.createdBy, organization)
    val operations                = PermissionsAuthorization.authorizedOperations(permissions.formPermissions, request.credentials, check)
    Operations.serialize(operations, normalized = true).mkString(" ")
  }

  // TODO: The methods below are used by the search API, but also by the distinct values API. Should we leave them
  //  here, as they're more closely associated with the search API, or move them elsewhere (e.g. in the parent package),
  //  in which case some classes/methods should be renamed (SearchVersion, SearchPermissions, doSearch, etc.)?

  def searchVersion(httpRequest: ExternalContext.Request): SearchVersion =
    SearchVersion(httpRequest.getFirstHeaderIgnoreCase(OrbeonFormDefinitionVersion))

  def anyOfOperations(rootElement: NodeInfo): Option[Set[Operation]] =
    rootElement.child("operations").headOption.map(_.attValue("any-of").splitTo[Set]().map(Operation.withName))

  private def computePermissions(
    request       : SearchRequestCommon
  )(implicit
    indentedLogger: IndentedLogger
  ): SearchPermissions = {

    val searchOperations = request.anyOfOperations.getOrElse(SearchOps.SearchOperations)

    // The persistence proxy should pre-process and post-process the information, see:
    // https://github.com/orbeon/orbeon-forms/issues/5741
    val formPermissions =
      PersistenceMetadataSupport.readFormPermissionsMaybeWithAdminSupport(
        request.isInternalAdminUser,
        request.appForm,
        request.version
      )

    SearchPermissions(
      formPermissions,
      authorizedBasedOnRoleOptimistic  = PermissionsAuthorization.authorizedBasedOnRole(formPermissions, request.credentials, searchOperations, optimistic = true),
      authorizedBasedOnRolePessimistic = PermissionsAuthorization.authorizedBasedOnRole(formPermissions, request.credentials, searchOperations, optimistic = false),
      authorizedIfUsername             = PermissionsAuthorization.hasPermissionCond(formPermissions, Condition.Owner, searchOperations).flatOption(request.credentials.map(_.userAndGroup.username)),
      authorizedIfGroup                = PermissionsAuthorization.hasPermissionCond(formPermissions, Condition.Group, searchOperations).flatOption(request.credentials.map(_.userAndGroup.groupname)).flatten,
      authorizedIfOrganizationMatch    = SearchOps.authorizedIfOrganizationMatch(formPermissions, request.credentials)
    )
  }

  def runBodyIfHasSomePermissions[T, R <: SearchRequestCommon](
    request           : R,
    queries           : List[Query],
    freeTextSearch    : Option[String],
    noPermissionValue : T,
    connectionOpt     : Option[Connection]
  )(
    body              : (Connection, List[StatementPart], SearchPermissions) => T
  )(implicit
    externalContext   : ExternalContext,
    indentedLogger    : IndentedLogger
  ): T = {
    val permissions      = computePermissions(request)
    val hasNoPermissions =
      ! permissions.authorizedBasedOnRoleOptimistic     &&
      permissions.authorizedIfUsername         .isEmpty &&
      permissions.authorizedIfGroup            .isEmpty &&
      permissions.authorizedIfOrganizationMatch.isEmpty

    if (hasNoPermissions)
      // There is no chance we can access any data, no need to run any SQL
      noPermissionValue
    else {
      val commonStatementPart = commonPart(request.appForm, request.version, queries, freeTextSearch)
      val statementParts      = List(commonStatementPart, permissionsPart(permissions))
      RelationalUtils.withConnection(request.provider, connectionOpt)(body(_, statementParts, permissions))
    }
  }

  def doSearch(
    request        : SearchRequest,
    connectionOpt  : Option[Connection]
  )(implicit
    externalContext: ExternalContext,
    indentedLogger : IndentedLogger
  ): (List[Document], Int) =
    SearchLogic.runBodyIfHasSomePermissions(
      request           = request,
      queries           = request.queries,
      freeTextSearch    = request.freeTextSearch,
      noPermissionValue = (List[Document](), 0),
      connectionOpt     = connectionOpt
    ) {
      (connection: Connection, commonAndPermissionsParts: List[StatementPart], permissions: SearchPermissions) =>

        val prepared    = prepareSearchQuery(request, connection, commonAndPermissionsParts)
        val searchCount = executeSearchCount(connection, prepared.innerSQL, prepared.statementParts)

        val startOffsetZeroBased = (request.pageNumber - 1) * request.pageSize

        // Use `LEFT JOIN` instead of regular join, in case the form doesn't have any control marked
        // to be indexed, in which case there won't be anything for it in `orbeon_i_control_text`.
        val sql =
          s"""SELECT
             |    c.*,
             |    t.control,
             |    t.pos,
             |    t.val
             |FROM
             |    (
             |        ${prepared.orderedSubquerySQL}
             |    ) c
             | LEFT JOIN
             |    orbeon_i_control_text t
             |    ON t.data_id = c.data_id
             | WHERE
             |    row_num
             |        BETWEEN ${startOffsetZeroBased + 1}
             |        AND     ${startOffsetZeroBased + request.pageSize}
             | ORDER BY row_num
             |""".stripMargin
        debug(s"search items query\n$sql")

        val rawDocumentMetadataAndValues = executeQuery(connection, sql, prepared.allStatementParts) { documentsResultSet =>

          Iterator.iterateWhile(
            cond = documentsResultSet.next(),
            elem = (
              readDocumentMetadata(documentsResultSet),
              DocumentValue(
                control = documentsResultSet.getString("control"),
                pos     = documentsResultSet.getInt("pos"),
                value   = documentsResultSet.getString("val")
              )
            )
          ).toList
        }

        // The order of the document metadata in the SQL results is already correct, take it as a reference for the final order
        val orderedDocumentMetadata          = rawDocumentMetadataAndValues.map(_._1).distinct
        val documentValuesByDocumentMetadata = rawDocumentMetadataAndValues.groupBy(_._1).view.mapValues(_.map(_._2))
        val documentMetadataAndValues        = orderedDocumentMetadata.map { documentMetadata =>
          // Keep document metadata order and group all values together
          documentMetadata -> documentValuesByDocumentMetadata(documentMetadata)
        }

        // Compute possible operations for each document
        val organizationsCache = mutable.Map[Int, Organization]()
        val documents = documentMetadataAndValues.map { case (metadata, values) =>
          val operations = computeOperations(connection, request, permissions, metadata, organizationsCache)
          Document(metadata, operations, values)
        }
        (documents, searchCount)
    }

  def doSearchAround(
    request        : SearchRequest,
    around         : AroundDocument,
    connectionOpt  : Option[Connection]
  )(implicit
    externalContext: ExternalContext,
    indentedLogger : IndentedLogger
  ): AroundSearchResult =
    SearchLogic.runBodyIfHasSomePermissions(
      request           = request,
      queries           = request.queries,
      freeTextSearch    = request.freeTextSearch,
      noPermissionValue = AroundSearchResult(0, None, None, None, None),
      connectionOpt     = connectionOpt
    ) {
      (connection: Connection, commonAndPermissionsParts: List[StatementPart], permissions: SearchPermissions) =>

        val prepared    = prepareSearchQuery(request, connection, commonAndPermissionsParts)
        val searchCount = executeSearchCount(connection, prepared.innerSQL, prepared.statementParts)

        if (searchCount == 0) {
          AroundSearchResult(0, None, None, None, None)
        } else {
          // 1. Find the 1-based row_num of the target document
          val findPositionSql =
            s"""SELECT c.row_num
               |  FROM (
               |       ${prepared.orderedSubquerySQL}
               |       ) c
               | WHERE c.document_id = ?
               |   AND c.draft = ?
               |""".stripMargin

          debug(s"search around position query\n$findPositionSql")
          val positionParts = prepared.allStatementParts :+ StatementPart(
            "",
            List[Setter](
              (ps, i) => ps.setString(i, around.documentId),
              (ps, i) => ps.setString(i, if (around.isDraft) "Y" else "N")
            )
          )
          val currentPosOpt: Option[Int] =
            executeQuery(connection, findPositionSql, positionParts) { rs =>
              if (rs.next()) Some(rs.getInt(1)) else None
            }

          val (prevRowNumOpt, currentRowNumOpt, nextRowNumOpt) =
            currentPosOpt match {
              case Some(pos) =>
                (
                  Option(pos - 1).filter(_ >= 1),
                  Some(pos),
                  Option(pos + 1).filter(_ <= searchCount)
                )
              case None =>
                around.fallbackPosition match {
                  case Some(fallbackPos) =>
                    (
                      Option((fallbackPos - 1).min(searchCount)).filter(_ >= 1),
                      None,
                      Option(fallbackPos).filter(p => p >= 1 && p <= searchCount)
                    )
                  case None =>
                    (None, None, None)
                }
            }

          val targetRowNums = List(prevRowNumOpt, currentRowNumOpt, nextRowNumOpt).flatten.distinct

          if (targetRowNums.isEmpty) {
            AroundSearchResult(searchCount, currentPosOpt, None, None, None)
          } else {
            // 2. Fetch metadata for previous/current/next rows without joining control_text
            val placeholders = targetRowNums.map(_ => "?").mkString(", ")
            val neighborsSql =
              s"""SELECT c.*
                 |  FROM (
                 |       ${prepared.orderedSubquerySQL}
                 |       ) c
                 | WHERE c.row_num IN ($placeholders)
                 | ORDER BY c.row_num
                 |""".stripMargin

            debug(s"search around neighbors query\n$neighborsSql")
            val neighborParts = prepared.allStatementParts :+ StatementPart(
              "",
              targetRowNums.map(rn => ((ps, i) => ps.setInt(i, rn)): Setter)
            )
            val rowsByPos: Map[Int, DocumentMetadata] =
              executeQuery(connection, neighborsSql, neighborParts) { rs =>
                Iterator.iterateWhile(
                  cond = rs.next(),
                  elem = rs.getInt("row_num") -> readDocumentMetadata(rs)
                ).toMap
              }

            val organizationsCache = mutable.Map[Int, Organization]()

            def toPositionedDoc(rowNumOpt: Option[Int]): Option[PositionedDocument] =
              for {
                rowNum   <- rowNumOpt
                metadata <- rowsByPos.get(rowNum)
              } yield {
                val operations = computeOperations(connection, request, permissions, metadata, organizationsCache)
                PositionedDocument(Document(metadata, operations, Nil), rowNum)
              }

            AroundSearchResult(
              total           = searchCount,
              currentPosition = currentPosOpt,
              previous        = toPositionedDoc(prevRowNumOpt),
              current         = toPositionedDoc(currentRowNumOpt),
              next            = toPositionedDoc(nextRowNumOpt)
            )
          }
        }
    }
}
