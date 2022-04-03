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
import org.orbeon.oxf.fr.persistence.relational.Statement._
import org.orbeon.oxf.fr.persistence.relational.search.adt.{Column, FilterType, SearchRequest}


object columnFilterPart {

  def apply(request: SearchRequest): StatementPart =
    if (! request.columns.exists(_.filterType != FilterType.None))
        NilPart
    else
        StatementPart(
          sql =
            request.columns
              // Only consider column with a filter
              .filter(_.filterType != FilterType.None)
              // Add index, used to refer the appropriate tf table
              .zipWithIndex
              .flatMap { case (column, i) =>
                val dataControlWhere =
                  s"""AND tf$i.data_id = c.data_id
                     |AND tf$i.control = ?
                     |""".stripMargin
                val valueWhere =
                  column.filterType match {
                    case FilterType.None              => List.empty
                    case FilterType.Exact    (_)      => List("AND " + Provider.textEquals  (request.provider, s"tf$i.val"))
                    case FilterType.Substring(_)      => List("AND " + Provider.textContains(request.provider, s"tf$i.val"))
                    case FilterType.Token    (tokens) =>
                      tokens.map { _ =>
                        "AND " + Provider.textContains(request.provider, s"concat(' ', tf$i.val, ' ')")
                      }
                  }
                dataControlWhere :: valueWhere
              }
              .mkString(" "),
          setters = {
            val values =
              request.columns.flatMap { case Column(path, matchType) =>
                matchType match {
                  case FilterType.None              => List.empty
                  case FilterType.Exact(filter)     => path :: List(filter)
                  case FilterType.Substring(filter) => path :: List(s"%${filter.toLowerCase}%")
                  case FilterType.Token(tokens)     => path :: tokens.map(token => s"% $token %")
                }
              }
            values.map(value => (_.setString(_, value)): Setter)
          }
        )
}
