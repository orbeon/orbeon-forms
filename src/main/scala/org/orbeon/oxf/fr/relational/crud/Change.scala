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

import java.io.{InputStream, ByteArrayOutputStream}
import java.sql
import java.sql.{Types, Timestamp, Connection}
import javax.xml.transform.OutputKeys
import javax.xml.transform.sax.SAXSource
import javax.xml.transform.stream.StreamResult
import org.orbeon.oxf.fr.relational._
import org.orbeon.oxf.pipeline.api.PipelineContext
import org.orbeon.oxf.processor.generator.RequestGenerator
import org.orbeon.oxf.util.ScalaUtils._
import org.orbeon.oxf.util.{StringBuilderWriter, NetUtils}
import org.orbeon.oxf.webapp.HttpStatusCodeException
import org.orbeon.oxf.xml.{XMLUtils, TransformerUtils}
import org.xml.sax.InputSource

trait Change extends RequestResponse with Common {

    case class Row(created: sql.Timestamp, username: Option[String], groupname: Option[String])
    def existingRow(connection: Connection, req: Request): Option[Row] = {

        val idCols = idColumns(req)
        val table = tableName(req)
        val resultSet = {
            val ps = connection.prepareStatement(
                s"""select created ${req.forData.option(", username , groupname").mkString}
                   |  from $table
                   | where (last_modified_time, $idCols)
                   |       in
                   |       (
                   |             select max(last_modified_time) last_modified_time, $idCols
                   |               from $table
                   |              where app  = ?
                   |                    and form = ?
                   |                    and form_version = ?
                   |                    ${if (req.forData)       "and document_id = ?" else ""}
                   |                    ${if (req.forAttachment) "and file_name   = ?" else ""}
                   |           group by $idCols
                   |       )
                   |       and deleted = 'N'
                 """.stripMargin)
            val position = Iterator.from(1)
            ps.setString(position.next(), req.app)
            ps.setString(position.next(), req.form)
            ps.setInt(position.next(), requestedFormVersion(connection, req))
            if (req.forData)       ps.setString(position.next(), req.dataPart.get.documentId)
            if (req.forAttachment) ps.setString(position.next(), req.filename.get)
            ps.executeQuery()
        }

        // Build case case with first row of result
        if (resultSet.next()) {
            val row = new Row(resultSet.getTimestamp("created"),
                              if (req.forData) Option(resultSet.getString("username" )) else None,
                              if (req.forData) Option(resultSet.getString("groupname")) else None)
            // Query should return at most one row
            assert(! resultSet.next())
            Some(row)
        } else {
            None
        }
    }

    def store(connection: Connection, req: Request, existingRow: Option[Row], delete: Boolean): Unit = {

        val table = tableName(req)
        val ps = connection.prepareStatement(
            s"""insert into $table
                (
                                                 created, last_modified_time, last_modified_by
                                               , app, form, form_version
                    ${if (req.forData)         ", document_id"             else ""}
                                               , deleted
                    ${if (req.forData)         ", draft"                   else ""}
                    ${if (req.forAttachment)   ", file_name, file_content" else ""}
                    ${if (! req.forAttachment) ", xml"                     else ""}
                    ${if (req.forData)         ", username, groupname"     else ""}
                )
                values
                (
                                               ?, ?, ?
                                               , ?, ?, ?
                    ${if (req.forData)         ", ?"    else ""}
                                               , ${if (delete) "'Y'" else "'N'"}
                    ${if (req.forData)         ", ?"    else ""}
                    ${if (req.forAttachment)   ", ?, ?" else ""}
                    ${if (! req.forAttachment) ", ?"    else ""}
                    ${if (req.forData)         ", ?, ?" else ""}
                )""".stripMargin)

        val position = Iterator.from(1)
        val now = new Timestamp(System.currentTimeMillis())

        // For put/update, reads the request either as bytes or XML
        object RequestReader {
            def requestInputStream(): InputStream = {
                val bodyURL = RequestGenerator.getRequestBody(PipelineContext.get)
                NetUtils.uriToInputStream(bodyURL)
            }

            def bytes(): Array[Byte] = {
                val os = new ByteArrayOutputStream
                NetUtils.copyStream(requestInputStream(), os)
                os.toByteArray
            }

            def xml(): String = {
                val transformer = TransformerUtils.getXMLIdentityTransformer
                transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes")
                val writer = new StringBuilderWriter()
                val source = new SAXSource(XMLUtils.newXMLReader(XMLUtils.ParserConfiguration.PLAIN), new InputSource(requestInputStream()))
                transformer.transform(source, new StreamResult(writer))
                writer.toString
            }
        }

                                     ps.setTimestamp(position.next(), existingRow.map(_.created).getOrElse(now))
                                     ps.setTimestamp(position.next(), now)
                                     ps.setString(position.next(), requestUsername.getOrElse(null))
                                     ps.setString(position.next(), req.app)
                                     ps.setString(position.next(), req.form)
                                     ps.setInt   (position.next(), requestedFormVersion(connection, req))
        if (req.forData)             ps.setString(position.next(), req.dataPart.get.documentId)
        if (req.forData)             ps.setString(position.next(), if (req.dataPart.get.isDraft) "Y" else "N")
        if (req.forAttachment)       ps.setString(position.next(), req.filename.get)
        if (delete) {
                                     ps.setNull(position.next(), if (req.forAttachment) Types.BLOB else Types.CLOB)
        } else {
            if (req.forAttachment)   ps.setBytes (position.next(), RequestReader.bytes())
            if (! req.forAttachment) ps.setString(position.next(), RequestReader.xml())
        }
        if (req.forData) {
                                     ps.setString(position.next(), existingRow.map(_.username ).flatten.getOrElse(requestUsername .getOrElse(null)))
                                     ps.setString(position.next(), existingRow.map(_.groupname).flatten.getOrElse(requestGroupname.getOrElse(null)))
        }

        ps.executeUpdate()
    }

    def change(delete: Boolean): Unit = {
        RelationalUtils.withConnection { connection ⇒
            val req = request

            // For a put/delete on data, a form version must be provided
            if (req.forData && ! req.version.isInstanceOf[Specific])
                httpResponse.setStatus(400)
            else {
                val existing = existingRow(connection, req)

                // Check permissions
                val authorized =
                    if (req.forData) {
                        if (existing.isDefined) {
                            // Check we're allowed to update or delete this resource
                            val username      = existing.get.username
                            val groupname     = existing.get.groupname
                            val dataUserGroup = if (username.isEmpty || groupname.isEmpty) None else Some(username.get, groupname.get)
                            val authorizedOps = authorizedOperations(req, dataUserGroup)
                            val requiredOp    = if (delete) "delete" else "update"
                            authorizedOps.contains(requiredOp)
                        } else {
                            // For deletes, if there is no data to delete, it is a 403 if could not read, update,
                            // or delete if it existed (otherwise code later will return a 404)
                            val authorizedOps = authorizedOperations(req, None)
                            val requiredOps   = if (delete) Set("read", "update", "delete") else Set("create")
                            authorizedOps.intersect(requiredOps).nonEmpty
                        }
                    } else {
                        // Operations on deployed forms are always authorized
                        true
                    }

                if (! authorized)
                    httpResponse.setStatus(403)
                else if (delete && existing.isEmpty) {
                    // We can't delete a document that doesn't exist
                    httpResponse.setStatus(404)
                } else {
                    store(connection, req, existing, delete)
                    httpResponse.setStatus(if (delete) 204 else 201)
                }
            }
        }
    }
}
