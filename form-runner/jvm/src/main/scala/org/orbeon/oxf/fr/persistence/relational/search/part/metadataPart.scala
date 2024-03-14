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
import org.orbeon.oxf.fr.persistence.relational.search.adt.MetadataFilterType.{InstantFilterType, StringFilterType}
import org.orbeon.oxf.fr.persistence.relational.search.adt.{MetadataQuery, SearchRequest}
import org.orbeon.oxf.util.CoreUtils.BooleanOps

import java.sql.Timestamp
import java.time.Instant

object metadataPart {

  def apply(request: SearchRequest): Option[StatementPart] = {
    def timestamp(column: String, operator: String, value: Instant) =
      (
        s"AND c.$column $operator ?",
        List[Setter](_.setTimestamp(_, Timestamp.from(value)))
      )

    def string(column: String, operator: String, value: String) =
      (
        s"AND c.$column $operator ?",
        List[Setter](_.setString(_, value))
      )

    val sqlStatementsAndSetters = request.queries.collect {
      case MetadataQuery(metadata, Some(metadataFilterType), _) =>
        metadataFilterType match {
          case instantFilterType: InstantFilterType => timestamp(metadata.sqlColumn, metadataFilterType.sql, instantFilterType.filter)
          case stringFilterType : StringFilterType  => string   (metadata.sqlColumn, metadataFilterType.sql, stringFilterType .filter)
        }
    }

    val sql     = sqlStatementsAndSetters.map(" " + _._1).mkString("\n")
    val setters = sqlStatementsAndSetters.flatMap(_._2)

    setters.nonEmpty.option(StatementPart(sql, setters))
  }
}
