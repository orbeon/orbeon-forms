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
package org.orbeon.oxf.fr.relational

import java.sql.Connection
import java.sql
import org.orbeon.oxf.util.ScalaUtils._
import org.orbeon.oxf.util.{StringBuilderWriter, ScalaUtils, NetUtils}
import org.orbeon.oxf.externalcontext.ExternalContextOps._
import scala.util.matching.Regex
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
import javax.sql.rowset.serial.SerialClob
import org.orbeon.oxf.processor.generator.RequestGenerator
import org.orbeon.oxf.pipeline.api.PipelineContext
import org.orbeon.oxf.xml.{XMLUtils, TransformerUtils}
import javax.xml.transform.stream.StreamResult
import javax.xml.transform.sax.SAXSource
import org.xml.sax.InputSource

object CRUD {

    case class Request(app: String, form: String, filename: Option[String], version: Version, dataPart: Option[Request#DataPart]) {
        case class DataPart(isDraft: Boolean, documentId: String)
        def forForm = ! dataPart.isDefined
        def forData =   dataPart.isDefined
        def forAttachment = filename.isDefined
    }

    def tableName(request: Request): String =
        Seq(
            Some("orbeon_form"),
            request.forForm       option "_definition",
            request.forData       option "_data",
            request.forAttachment option "_attach"
        ).flatten.mkString

    private def latestNonDeletedFormVersion(connection: Connection, app: String, form: String): Option[Int] = {
        val maxVersion = {
            val ps = connection.prepareStatement(
                """select max(form_version)
                  |  from orbeon_form_definition
                  | where (last_modified_by, app, form, form_version) in
                  |       (
                  |             select last_modified_time, app, form, form_version
                  |               from orbeon_form_definition
                  |              where app = ?
                  |                    and form = ?
                  |           group by app, form, form_version
                  |       )
                  |   and deleted = 'N'
                """.stripMargin)
            ps.setString(1, app)
            ps.setString(2, form)
            val rs = ps.executeQuery()
            rs.next(); rs
        }
        val version = maxVersion.getInt(1)
        if (maxVersion.wasNull()) None else Some(version)
    }


    private def httpRequest = NetUtils.getExternalContext.getRequest
    private def headerValue(name: String): Option[String] = httpRequest.getFirstHeader(name)
    private def requestUsername : Option[String] = headerValue("orbeon-username")
    private def requestGroupname: Option[String] = headerValue("orbeon-groupname")

    private case class Row(created: sql.Date, username: Option[String], groupname: Option[String])

    private def existingRow(connection: Connection, request: Request): Option[Row] = {

        // List of columns that identify a row
        val idColumns =
            Seq(
                Some("app"), Some("form"),
                request.forForm       option "form_version",
                request.forData       option "document_id",
                request.forAttachment option "file_name"
            ).flatten.mkString(", ")

        def latest = latestNonDeletedFormVersion(connection, request.app, request.form)
        val table = tableName(request)
        val version = request.version match {
            case Latest         ⇒ latest.getOrElse(1)
            case Next           ⇒ latest.map(_ + 1).getOrElse(1)
            case Specific(v)    ⇒ v
            case ForDocument(_) ⇒ throw new IllegalStateException // Only supported when retrieving a form
        }

        val resultSet = {
            val ps = connection.prepareStatement(
                s"""select created ${request.forData.option(", username , groupname").mkString}
                   |  from $table
                   | where (last_modified_time, $idColumns)
                   |       in
                   |       (
                   |             select max(last_modified_time) last_modified_time, $idColumns
                   |               from $table
                   |              where app  = ?
                   |                    and form = ?
                   |                    and form_version = ?
                   |                    ${if (request.forData)       "and document_id = ?" else ""}
                   |                    ${if (request.forAttachment) "and file_name   = ?" else ""}
                   |           group by $idColumns
                   |       )
                   |       and deleted = 'N'
                 """.stripMargin)
            val position = Iterator.from(1)
            ps.setString(position.next(), request.app)
            ps.setString(position.next(), request.form)
            ps.setInt(position.next(), version)
            if (request.forData)       ps.setString(position.next(), request.dataPart.get.documentId)
            if (request.forAttachment) ps.setString(position.next(), request.filename.get)
            ps.executeQuery()
        }

        // Build case case with first row of result
        if (resultSet.next()) {
            val row = new Row(resultSet.getDate("created"),
                                  if (request.forData) Some(resultSet.getString("username")) else None,
                                  if (request.forData) Some(resultSet.getString("group"   )) else None)
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

    private val CrudFormPath = "/fr/service/([^/]+)/crud/([^/]+)/([^/]+)/form/([^/]+)".r

    def put(): Unit = {
        val CrudFormPath(_, app, form, _) = NetUtils.getExternalContext.getRequest.getRequestPath
        RelationalUtils.withConnection { connection ⇒
            val request = new Request(app, form, None, Latest, None)
            val existing = existingRow(connection, request)
            store(connection, request, existing)
        }
    }
}
