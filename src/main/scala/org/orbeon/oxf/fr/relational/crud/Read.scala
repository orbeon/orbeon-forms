/**
 * Copyright (C) 2013 Orbeon, Inc.
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
package org.orbeon.oxf.fr.relational.crud

import java.io.OutputStreamWriter
import org.orbeon.oxf.fr.FormRunnerPersistence
import org.orbeon.oxf.fr.relational.{Next, Unspecified, RelationalUtils}
import org.orbeon.oxf.util.NetUtils
import org.orbeon.oxf.webapp.HttpStatusCodeException

trait Read extends RequestResponse with Common with FormRunnerPersistence {

    def get(): Unit = {
        RelationalUtils.withConnection { connection ⇒
            val req = request

            val badVersion =
                // For data, version must be left unspecified
                (req.forData && req.version != Unspecified) ||
                // For form definition, everything is valid except Next
                (req.forForm && req.version == Next)
            if (badVersion) throw HttpStatusCodeException(400)

            val resultSet = {
                val table = tableName(request)
                val idCols = idColumns(request)
                val xmlCol = if (req.provider == "oracle") "t.xml.getClobVal()" else "t.xml"
                val ps = connection.prepareStatement(
                    s"""select
                       |    last_modified_time
                       |    ${if (req.forAttachment) ", file_content"          else s", $xmlCol xml"}
                       |    ${if (req.forData)       ", username, groupname"   else ""}
                       |    ${if (req.forForm)       ", form_version"          else ""}
                       |from $table t
                       |    where (last_modified_time, $idCols)
                       |          in
                       |          (
                       |              select max(last_modified_time) last_modified_time, $idCols
                       |                from $table
                       |               where app  = ?
                       |                     and form = ?
                       |                     ${if (req.forForm)       "and form_version = ?"              else ""}
                       |                     ${if (req.forData)       "and document_id = ? and draft = ?" else ""}
                       |                     ${if (req.forAttachment) "and file_name = ?"                 else ""}
                       |            group by $idCols
                       |          )
                       |    and deleted = 'N'
                       |""".stripMargin)

                val position = Iterator.from(1)
                ps.setString(position.next(), req.app)
                ps.setString(position.next(), req.form)
                if (req.forForm) ps.setInt(position.next(), requestedFormVersion(connection, req))
                if (req.forData) {
                    ps.setString(position.next(), req.dataPart.get.documentId)
                    ps.setString(position.next(), if (req.dataPart.get.isDraft) "Y" else "N")
                }
                if (req.forAttachment) ps.setString(position.next(), req.filename.get)
                ps.executeQuery()
            }

            if (resultSet.next()) {

                // Check user can read and set Orbeon-Operations header
                if (req.forData) {
                    val dataUserGroup = {
                        val username      = resultSet.getString("username")
                        val groupname     = resultSet.getString("groupname")
                        if (username == null || groupname == null) None else Some(username, groupname)
                    }
                    val operations = authorizedOperations(req, dataUserGroup)
                    if (! operations.contains("read"))
                        throw new HttpStatusCodeException(403)
                    httpResponse.setHeader("Orbeon-Operations", operations.mkString(" "))
                }

                // Set form version header
                if (req.forForm) {
                    val formVersion = resultSet.getInt("form_version")
                    httpResponse.setHeader("Orbeon-Form-Definition-Version", formVersion.toString)
                }

                // Write content (XML / file)
                if (req.forAttachment) {
                    val blob = resultSet.getBlob("file_content")
                    NetUtils.copyStream(blob.getBinaryStream, httpResponse.getOutputStream)
                } else {
                    val clob = resultSet.getClob("xml")
                    httpResponse.setHeader("Content-Type", "application/xml")
                    val writer = new OutputStreamWriter(httpResponse.getOutputStream, "UTF-8")
                    NetUtils.copyStream(clob.getCharacterStream, writer)
                    writer.close()
                }

            } else {
                throw new HttpStatusCodeException(404)
            }
        }
    }
}
