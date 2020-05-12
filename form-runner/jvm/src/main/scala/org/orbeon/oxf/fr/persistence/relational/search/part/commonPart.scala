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

import java.sql.Connection

import org.orbeon.oxf.fr.persistence.relational.Statement.StatementPart
import org.orbeon.oxf.fr.persistence.relational.search.adt.{FilterType, Request}
import org.orbeon.oxf.util.CoreUtils._

object commonPart  {

  def apply(request: Request, connection: Connection, versionNumber: Int) =
    StatementPart(
      sql = {
        val columnFilterTables =
          request.columns
            .filter(_.filterType != FilterType.None)
            .zipWithIndex
            .map { case (_, i) => s", orbeon_i_control_text tf$i" }
            .mkString(" ")
        val freeTextTable =
          request.freeTextSearch.nonEmpty.string(", orbeon_form_data d")

        s"""|SELECT DISTINCT c.data_id
            |           FROM orbeon_i_current c
            |                $columnFilterTables
            |                $freeTextTable
            |          WHERE c.app          = ?    AND
            |                c.form         = ?    AND
            |                c.form_version = ?
            |""".stripMargin
      },
      setters = List(
        _.setString(_, request.app),
        _.setString(_, request.form),
        _.setInt   (_, versionNumber)
      )
    )
}
