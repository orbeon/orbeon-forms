/**
 * Copyright (C) 2024 Orbeon, Inc.
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
package org.orbeon.oxf.fr.persistence.relational.search.part

import org.orbeon.oxf.fr.persistence.relational.Statement.{Setter, StatementPart}
import org.orbeon.oxf.fr.persistence.relational.search.adt.SearchRequest
import org.orbeon.oxf.util.CoreUtils.BooleanOps

import java.sql.Timestamp
import java.time.Instant

object metadataPart {

  def apply(request: SearchRequest): Option[StatementPart] = {
    def timestamp(column: String, operator: String, valueOpt: Option[Instant]) =
      valueOpt.map { value =>
        (s"AND c.$column $operator ?", List[Setter](_.setTimestamp(_, Timestamp.from(value))))
      }

    def stringIn(column: String, valuesOrEmpty: Set[String]) =
      Option(valuesOrEmpty.toList).filter(_.nonEmpty).map { values =>
        (
          s"AND c.$column IN (${values.map(_ => "?").mkString(", ")})",
          values.map(s => (_.setString(_, s)): Setter)
        )
      }

    val sqlStatementsAndSetters = List(
      timestamp("created"           , ">=", request.createdGteOpt     ),
      timestamp("created"           , "<" , request.createdLtOpt      ),
      stringIn ("username"                , request.createdBy         ),
      timestamp("last_modified_time", ">=", request.lastModifiedGteOpt),
      timestamp("last_modified_time", "<" , request.lastModifiedLtOpt ),
      stringIn ("last_modified_by"        , request.lastModifiedBy    ),
      stringIn ("stage"                   , request.workflowStage     )
    ).flatten

    val sql     = sqlStatementsAndSetters.map(" " + _._1).mkString("\n")
    val setters = sqlStatementsAndSetters.flatMap(_._2)

    setters.nonEmpty.option(StatementPart(sql, setters))
  }
}
