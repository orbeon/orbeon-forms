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

import java.io.{ByteArrayInputStream, OutputStreamWriter, StringReader}
import org.orbeon.oxf.fr.FormRunnerPersistence
import org.orbeon.oxf.fr.relational.{Next, Unspecified, RelationalUtils}
import org.orbeon.oxf.http.Headers
import org.orbeon.oxf.util.ScalaUtils._
import org.orbeon.oxf.util.NetUtils
import org.orbeon.oxf.webapp.HttpStatusCodeException

trait Read extends RequestResponse with Common with FormRunnerPersistence {

    def get(req: Request): Unit = {

        // Read before establishing a connection, so we don't use two simultaneous connections
        val formMetadataForDataRequestOpt = req.forData option readFormMetadata(req)

        RelationalUtils.withConnection { connection ⇒

            val badVersion =
                // For data, version must be left unspecified
                (req.forData && req.version != Unspecified) ||
                // For form definition, everything is valid except Next
                (req.forForm && req.version == Next)
            if (badVersion) throw HttpStatusCodeException(400)

            val resultSet = {
                val table  = tableName(req)
                val idCols = idColumns(req)
                val xmlCol = req.provider match {
                    case "oracle"     ⇒ "t.xml.getClobVal()"
                    case "db2"        ⇒ "xml2clob(t.xml)"
                    case "postgresql" ⇒ "t.xml as"
                    case _            ⇒ "t.xml"
                }
                val ps = connection.prepareStatement(
                    s"""|SELECT  t.last_modified_time
                        |        ${if (req.forAttachment) ", t.file_content"            else s", $xmlCol xml"}
                        |        ${if (req.forData)       ", t.username, t.groupname"   else ""}
                        |        , t.form_version, t.deleted
                        |FROM    $table t,
                        |        (
                        |            SELECT   max(last_modified_time) last_modified_time, ${idCols.mkString(", ")}
                        |              FROM   $table
                        |             WHERE   app  = ?
                        |                     and form = ?
                        |                     ${if (req.forForm)       "and form_version = ?"              else ""}
                        |                     ${if (req.forData)       "and document_id = ? and draft = ?" else ""}
                        |                     ${if (req.forAttachment) "and file_name = ?"                 else ""}
                        |            GROUP BY ${idCols.mkString(", ")}
                        |        ) m
                        |WHERE   ${joinColumns("last_modified_time" +: idCols, "t", "m")}
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

                // We can't always return a 403 instead of a 410/404, so we decided it's OK to divulge to unauthorized
                // users that the data exists or existed
                val deleted = resultSet.getString("deleted") == "Y"
                if (deleted)
                    throw new HttpStatusCodeException(410)

                // Check user can read and set Orbeon-Operations header
                formMetadataForDataRequestOpt foreach { formMetadata ⇒
                    val dataUserGroup = {
                        val username  = resultSet.getString("username")
                        val groupname = resultSet.getString("groupname")
                        Option(username) → Option(groupname)
                    }
                    val operations = authorizedOperations(formMetadata, dataUserGroup)
                    System.out.println(operations)
                    if (! operations.contains("read"))
                        throw new HttpStatusCodeException(403)
                    httpResponse.setHeader("Orbeon-Operations", operations.mkString(" "))
                }

                // Set form version header
                locally {
                    val formVersion = resultSet.getInt("form_version")
                    httpResponse.setHeader("Orbeon-Form-Definition-Version", formVersion.toString)
                }

                // Write content (XML / file)
                if (req.forAttachment) {
                    val stream = req.provider match {
                        case "postgresql" ⇒ new ByteArrayInputStream(resultSet.getBytes("file_content"))
                        case _            ⇒ resultSet.getBlob("file_content").getBinaryStream
                    }
                    NetUtils.copyStream(stream, httpResponse.getOutputStream)
                } else {
                    val stream = req.provider match {
                        case "postgresql" ⇒ new StringReader(resultSet.getString("xml"))
                        case _            ⇒ resultSet.getClob("xml").getCharacterStream
                    }
                    httpResponse.setHeader(Headers.ContentType, "application/xml")
                    val writer = new OutputStreamWriter(httpResponse.getOutputStream, "UTF-8")
                    NetUtils.copyStream(stream, writer)
                    writer.close()
                }

            } else {
                throw new HttpStatusCodeException(404)
            }
        }
    }
}
