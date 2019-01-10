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

import org.orbeon.oxf.fr.persistence.relational.Provider.MySQL
import org.orbeon.oxf.fr.persistence.relational.Statement.StatementPart
import org.orbeon.oxf.fr.persistence.relational.search.adt.Request
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.util.IOUtils.useAndClose
import org.orbeon.oxf.util.StringUtils._

object commonPart  {

  def apply(request: Request, connection: Connection, versionNumber: Int) =
    StatementPart(
      sql = {

        val mySQLMajorVersion =
          (request.provider == MySQL).option {
            // MySQL < 8 lacks row_number, see http://stackoverflow.com/a/1895127/5295
            val mySQLVersion = {
              val sql = "SHOW VARIABLES LIKE \"version\""
              useAndClose(connection.prepareStatement(sql)) { ps ⇒
                useAndClose(ps.executeQuery()) { rs ⇒
                  rs.next()
                  rs.getString("value")
                }
              }
            }
            mySQLVersion.splitTo(".").head.toInt
          }
        val isMySQLBefore8 = mySQLMajorVersion.exists(_ < 8)

        val rowNumCol =
          if (isMySQLBefore8) "@rownum := @rownum + 1 row_num"
          else                "row_number() over (order by c.last_modified_time desc) row_num"
        val mySqlRowNumTable = isMySQLBefore8.string(", (select @rownum := 0) r")
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
            |           c.organization_id,
            |           $rowNumCol
            |      FROM orbeon_i_current c
            |           $mySqlRowNumTable
            |           $columnFilterTables
            |           $freeTextTable
            |     WHERE c.app          = ?    AND
            |           c.form         = ?    AND
            |           c.form_version = ?
            |""".stripMargin
      },
      setters = List(
        _.setString(_, request.app),
        _.setString(_, request.form),
        _.setInt   (_, versionNumber)
      )
    )
}
