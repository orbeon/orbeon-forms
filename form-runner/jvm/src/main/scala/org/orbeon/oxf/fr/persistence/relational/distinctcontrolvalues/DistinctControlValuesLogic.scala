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
package org.orbeon.oxf.fr.persistence.relational.distinctcontrolvalues

import org.orbeon.oxf.fr.persistence.relational.Statement._
import org.orbeon.oxf.fr.persistence.relational.distinctcontrolvalues.adt.{ControlValues, DistinctControlValuesRequest}
import org.orbeon.oxf.fr.persistence.relational.search.SearchLogic
import org.orbeon.oxf.fr.persistence.relational.search.adt._
import org.orbeon.oxf.util.CollectionUtils._

import java.sql.Connection


trait DistinctControlValuesLogic {

  def queryControlValues(request: DistinctControlValuesRequest): List[ControlValues] = {
    // Re-use part of the search API logic (permissions, SQL generation)
    SearchLogic.doSearch(
      request           = request,
      controls          = Nil,
      freeTextSearch    = None,
      anyOfOperations   = None,
      noPermissionValue = List[ControlValues]()
    ) {
      case (connection: Connection, commonParts: List[StatementPart], _: SearchPermissions) =>

        val innerSQL = buildQuery(commonParts)

        // Retrieve distinct values for all queried controls
        request.controlPaths.map { controlPath =>

          val sql =
            s"""SELECT
               |    DISTINCT t.val
               |FROM
               |    ($innerSQL) c
               |LEFT JOIN
               |    orbeon_i_control_text t
               |    ON t.data_id = c.data_id
               |WHERE
               |    t.control = ?
               |""".stripMargin

          val controlPathPart = StatementPart("", List[Setter]((ps, i) => ps.setString(i, controlPath)))

          val distinctValues = executeQuery(connection, sql, commonParts :+ controlPathPart) { valuesResultSet =>
            Iterator.iterateWhile(
              cond = valuesResultSet.next(),
              elem = valuesResultSet.getString("val")
            ).toList
          }

          ControlValues(controlPath, distinctValues)
        }
    }
  }
}
