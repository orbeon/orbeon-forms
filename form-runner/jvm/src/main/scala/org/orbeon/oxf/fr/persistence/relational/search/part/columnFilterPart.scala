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
package org.orbeon.oxf.fr.persistence.relational.search.part

import org.orbeon.oxf.fr.persistence.relational.Provider
import org.orbeon.oxf.fr.persistence.relational.Statement.*
import org.orbeon.oxf.fr.persistence.relational.search.adt.{ControlFilterType, ControlQuery, SearchRequest}


object columnFilterPart {

  def apply(request: SearchRequest): StatementPart = {
    val controlQueries = request.queries.collect { case controlQuery: ControlQuery => controlQuery }

    if (! controlQueries.exists(_.filterType.isDefined))
        NilPart
    else
        StatementPart(
          sql =
            controlQueries
              // Only consider column with a filter
              .filter(_.filterType.isDefined)
              // Add index, used to refer the appropriate tf table
              .zipWithIndex
              .flatMap { case (column, i) =>
                val dataControlWhere =
                  s"""AND tf$i.data_id = c.data_id
                     |AND tf$i.control = ?
                     |""".stripMargin
                val valueWhere =
                  column.filterType match {
                    case None                                      => List.empty
                    case Some(ControlFilterType.Exact    (_))      => List("AND " + Provider.textEquals  (request.provider, s"tf$i.val"))
                    case Some(ControlFilterType.Substring(_))      => List("AND " + Provider.textContains(request.provider, s"tf$i.val"))
                    case Some(ControlFilterType.Token    (tokens)) =>
                      tokens.map { _ =>
                        "AND " + Provider.textContains(request.provider, Provider.concat(request.provider, "' '", s"tf$i.val", "' '"))
                      }
                  }
                dataControlWhere :: valueWhere
              }
              .mkString(" "),
          setters = {
            val values =
              controlQueries.flatMap { case ControlQuery(path, filterType, _) =>
                filterType match {
                  case None                                      => List.empty
                  case Some(ControlFilterType.Exact    (filter)) => path :: List(Provider.textEqualsParam  (request.provider, filter))
                  case Some(ControlFilterType.Substring(filter)) => path :: List(Provider.textContainsParam(request.provider, filter))
                  case Some(ControlFilterType.Token    (tokens)) => path :: tokens.map(token => Provider.textContainsParam(request.provider, s" $token "))
                }
              }
            values.map(value => (_.setString(_, value)): Setter)
          }
        )
  }
}
