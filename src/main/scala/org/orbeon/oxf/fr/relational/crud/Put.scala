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

import java.io.ByteArrayOutputStream
import java.sql
import java.sql.Connection
import javax.xml.transform.OutputKeys
import javax.xml.transform.sax.SAXSource
import javax.xml.transform.stream.StreamResult
import org.orbeon.oxf.fr.relational._
import org.orbeon.oxf.pipeline.api.PipelineContext
import org.orbeon.oxf.processor.generator.RequestGenerator
import org.orbeon.oxf.util.ScalaUtils._
import org.orbeon.oxf.util.{StringBuilderWriter, NetUtils}
import org.orbeon.oxf.xml.{XMLUtils, TransformerUtils}
import org.xml.sax.InputSource
import scala.Some

trait Put extends Request with Common {

    private case class Row(created: sql.Date, username: Option[String], groupname: Option[String])
    private def existingRow(connection: Connection, req: Request): Option[Row] = {

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
            val row = new Row(resultSet.getDate("created"),
                                  if (req.forData) Some(resultSet.getString("username")) else None,
                                  if (req.forData) Some(resultSet.getString("group"   )) else None)
            // Query should return at most one row
            assert(! resultSet.next())
            Some(row)
        } else {
            None
        }
    }

    def store(connection: Connection, request: Request, existingRow: Option[Row]): Unit = {

        val table = tableName(request)
        val ps = connection.prepareStatement(
            s"""insert into $table
                (
                                                    created, last_modified_time, last_modified_by
                                                    , app, form, form_version
                    ${if (request.forData)         ", document_id"             else ""}
                                                    , deleted
                    ${if (request.forData)         ", draft"                   else ""}
                    ${if (request.forAttachment)   ", file_name, file_content" else ""}
                    ${if (! request.forAttachment) ", xml"                     else ""}
                    ${if (request.forData)         ", username, groupname"     else ""}
                )
                values
                (
                                                    ?, ?, ?
                                                    , ?, ?, 1
                    ${if (request.forData)         ", ?"    else ""}
                                                    , 'N'
                    ${if (request.forData)         ", ?"    else ""}
                    ${if (request.forAttachment)   ", ?, ?" else ""}
                    ${if (! request.forAttachment) ", ?"    else ""}
                    ${if (request.forData)         ", ?, ?" else ""}
                )""".stripMargin)

        val position = Iterator.from(1)
        val now = new sql.Date(System.currentTimeMillis())
        val requestInputStream = {
            val bodyURL = RequestGenerator.getRequestBody(PipelineContext.get)
            NetUtils.uriToInputStream(bodyURL)
        }

        def requestBytes(): Array[Byte] = {
            val os = new ByteArrayOutputStream
            NetUtils.copyStream(requestInputStream, os)
            os.toByteArray
        }

        def requestXML(): String = {
            val transformer = TransformerUtils.getXMLIdentityTransformer
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes")
            val writer = new StringBuilderWriter()
            val source = new SAXSource(XMLUtils.newXMLReader(XMLUtils.ParserConfiguration.PLAIN), new InputSource(requestInputStream))
            transformer.transform(source, new StreamResult(writer))
            writer.toString
        }

                                     ps.setDate  (position.next(), existingRow.map(_.created).getOrElse(now))
                                     ps.setDate  (position.next(), now)
                                     ps.setString(position.next(), requestUsername.getOrElse(null))
                                     ps.setString(position.next(), request.app)
                                     ps.setString(position.next(), request.form)
        if (request.forData)         ps.setString(position.next(), request.dataPart.get.documentId)
        if (request.forData)         ps.setString(position.next(), if (request.dataPart.get.isDraft) "Y" else "N")
        if (request.forAttachment)   ps.setString(position.next(), request.filename.get)
        if (request.forAttachment)   ps.setBytes (position.next(), requestBytes())
        if (! request.forAttachment) ps.setString(position.next(), requestXML())
        if (request.forData)         ps.setString(position.next(), existingRow.map(_.username .get).getOrElse(requestUsername .getOrElse(null)))
        if (request.forData)         ps.setString(position.next(), existingRow.map(_.groupname.get).getOrElse(requestGroupname.getOrElse(null)))

        ps.executeUpdate()
    }

    def put(): Unit = {
        RelationalUtils.withConnection { connection ⇒
            val req = request
            val existing = existingRow(connection, req)
            store(connection, req, existing)
        }
    }
}
