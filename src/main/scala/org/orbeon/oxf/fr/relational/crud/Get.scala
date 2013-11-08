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

import org.orbeon.oxf.fr.relational.RelationalUtils
import org.orbeon.oxf.util.{Connection, NetUtils}
import org.orbeon.oxf.webapp.HttpStatusCodeException
import org.orbeon.oxf.fr.{FormRunner, FormRunnerPersistence}

trait Get extends RequestResponse with Common with FormRunnerPersistence {

    def get(): Unit = {
        RelationalUtils.withConnection { connection ⇒
            val req = request
            val table = tableName(request)
            val idCols = idColumns(request)

            val resultSet = {
                val ps = connection.prepareStatement(
                    s"""select
                       |    last_modified_time,
                       |    ${if (req.forAttachment) "file_content" else "t.xml xml"}
                       |    ${if (req.forData)       ", username, groupname" else ""}
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

                val xml = resultSet.getClob("xml")
                val response = NetUtils.getExternalContext.getResponse
                NetUtils.copyStream(xml.getCharacterStream, response.getWriter)
            } else {
                throw new HttpStatusCodeException(404)
            }
        }
    }
}
