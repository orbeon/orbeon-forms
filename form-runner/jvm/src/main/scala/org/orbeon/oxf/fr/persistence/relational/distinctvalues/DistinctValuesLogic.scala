/**
 * Copyright (C) 2023 Orbeon, Inc.
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
package org.orbeon.oxf.fr.persistence.relational.distinctvalues

import org.orbeon.oxf.externalcontext.ExternalContext
import org.orbeon.oxf.fr.persistence.relational.Provider
import org.orbeon.oxf.fr.persistence.relational.Statement.*
import org.orbeon.oxf.fr.persistence.relational.distinctvalues.adt.*
import org.orbeon.oxf.fr.persistence.relational.search.SearchLogic
import org.orbeon.oxf.fr.persistence.relational.search.adt.SearchPermissions
import org.orbeon.oxf.util.CollectionUtils.*
import org.orbeon.oxf.util.IndentedLogger

import java.sql.Connection


trait DistinctValuesLogic {

  private case class RequestContext(
    provider   : Provider,
    connection : Connection,
    commonParts: List[StatementPart],
    innerSQL   : String
  )

  def queryDistinctValues(
    request        : DistinctValuesRequest
  )(implicit
    externalContext: ExternalContext,
    indentedLogger : IndentedLogger
  ): DistinctValues = {
    // Re-use part of the search API logic (permissions, SQL generation)
    SearchLogic.runBodyIfHasSomePermissions(
      request           = request,
      queries           = Nil,
      freeTextSearch    = None,
      noPermissionValue = DistinctValues()
    ) {
      case (connection: Connection, commonAndPermissionsParts: List[StatementPart], _: SearchPermissions) =>

        val innerSQL = buildQuery(commonAndPermissionsParts)

        implicit val requestContext = RequestContext(request.provider, connection, commonAndPermissionsParts, innerSQL)

        val controlValues  = request.controlPaths.map(distinctControlValues)
        val metadataValues = request.metadata.map(distinctMetadataValues)

        DistinctValues(controlValues ::: metadataValues)
    }
  }

  private def distinctControlValues(
    controlPath: String)(implicit
    ctx        : RequestContext
  ): ControlValues = {
    // We can't use DISTINCT on CLOB columns, so we cast to VARCHAR. A more correct approach would be to
    // retrieve all values and then make them distinct in Scala. At the moment, the Distinct Values API is used
    // to display dropdown values in the UI, so the CAST approach should be good enough.

    val sql =
      s"""SELECT
         |    ${Provider.distinctVal(ctx.provider, "t.val", "val")}
         |FROM
         |    (${ctx.innerSQL}) c
         |LEFT JOIN
         |    orbeon_i_control_text t
         |    ON t.data_id = c.data_id
         |WHERE
         |    t.control = ?
         |""".stripMargin

    val controlPathPart = StatementPart("", List[Setter]((ps, i) => ps.setString(i, controlPath)))

    val distinctValues = executeQuery(ctx.connection, sql, ctx.commonParts :+ controlPathPart) { valuesResultSet =>
      Iterator.iterateWhile(
        cond = valuesResultSet.next(),
        elem = valuesResultSet.getString("val")
      ).toList
    }

    ControlValues(controlPath, distinctValues)
  }

  private def distinctMetadataValues(
    metadata: Metadata)(implicit
    ctx     : RequestContext
  ): MetadataValues = {
    val sql =
      s"""SELECT ${Provider.distinctVal(ctx.provider, metadata.sqlColumn, metadata.sqlColumn)}
         |FROM   orbeon_i_current
         |WHERE  data_id IN (${ctx.innerSQL})
         |""".stripMargin

    val distinctValues = executeQuery(ctx.connection, sql, ctx.commonParts) { valuesResultSet =>
      Iterator.iterateWhile(
        cond = valuesResultSet.next(),
        elem = valuesResultSet.getString(metadata.sqlColumn)
      ).toList
    }

    MetadataValues(metadata, distinctValues)
  }
}
