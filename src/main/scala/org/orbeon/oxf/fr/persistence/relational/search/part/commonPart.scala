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

import org.orbeon.oxf.fr.persistence.relational.Provider.MySQL
import org.orbeon.oxf.fr.persistence.relational.Statement.StatementPart
import org.orbeon.oxf.fr.persistence.relational.search.adt.Request
import org.orbeon.oxf.util.CoreUtils._

object commonPart  {

  def apply(request: Request) =

    StatementPart(
      sql = {

        val rowNumCol =
          if (request.provider == MySQL)
            // MySQL lacks row_number, see http://stackoverflow.com/a/1895127/5295
            "@rownum := @rownum + 1 row_number"
          else
            "row_number() over (order by c.last_modified_time desc) row_number"

        val mySqlRowNumTable =
          (request.provider == MySQL).string(", (select @rownum := 0) r")
        val columnFilterTables =
          request.columns
            .filter(_.filterWith.nonEmpty)
            .zipWithIndex
            .map { case (_, i) ⇒ s", orbeon_i_control_text tf$i" }
            .mkString(" ")
        val freeTextTable =
          request.freeTextSearch.nonEmpty.string(", orbeon_form_data d")

        s"""|    SELECT c.data_id,
            |           c.document_id,
            |           c.draft,
            |           c.created,
            |           c.last_modified_time,
            |           c.last_modified_by,
            |           c.username,
            |           c.groupname,
            |           $rowNumCol
            |      FROM orbeon_i_current c
            |           $mySqlRowNumTable
            |           $columnFilterTables
            |           $freeTextTable
            |     WHERE c.app     = ?         AND
            |           c.form    = ?
            |""".stripMargin
      },
      setters = List(
        _.setString(_, request.app),
        _.setString(_, request.form)
      )
    )}
