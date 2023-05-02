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
package org.orbeon.oxf.fr.persistence.relational

import org.orbeon.io.IOUtils._

import java.sql.{Connection, PreparedStatement, ResultSet}


object Statement {

  type Setter = (PreparedStatement, Int) => Unit

  case class StatementPart(
    sql    : String,
    setters: List[Setter]
  )

  val NilPart = StatementPart("", Nil)

  def buildQuery(parts: List[StatementPart]): String =
    parts
      .map { case StatementPart(partSQL, _) => partSQL }
      .mkString("\n")

  def executeQuery[T](
    connection : Connection,
    sql        : String,
    parts      : List[StatementPart])(
    block      : ResultSet => T
  ): T =
    useAndClose(connection.prepareStatement(sql)) { ps =>

      val index = Iterator.from(1)

      for {
        StatementPart(_, setters) <- parts
        setter                    <- setters
      } locally {
        setter(ps, index.next())
      }

      useAndClose(ps.executeQuery())(block)
    }
}
