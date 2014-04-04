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

trait CreateUpdateDelete extends RequestResponse with Common {

    private case class Row(created: sql.Timestamp, username: Option[String], group: Option[String], formVersion: Option[Int])

    private def existingRow(connection: Connection, req: Request): Option[Row] = {

        val idCols = idColumns(req)
        val table  = tableName(req)
        val resultSet = {
            val ps = connection.prepareStatement(
                s"""|select created ${req.forData.option(", username , groupname, form_version").mkString}
                    |  from $table
                    | where (last_modified_time, $idCols)
                    |       in
                    |       (
                    |             select max(last_modified_time) last_modified_time, $idCols
                    |               from $table
                    |              where app  = ?
                    |                    and form = ?
                    |                    ${if (! req.forData)     "and form_version = ?" else ""}
                    |                    ${if (req.forData)       "and document_id  = ?" else ""}
                    |                    ${if (req.forAttachment) "and file_name    = ?" else ""}
                    |           group by $idCols
                    |       )
                    |       and deleted = 'N'
                    |""".stripMargin)
            val position = Iterator.from(1)
            ps.setString(position.next(), req.app)
            ps.setString(position.next(), req.form)
            if (! req.forData)     ps.setInt   (position.next(), requestedFormVersion(connection, req))
            if (req.forData)       ps.setString(position.next(), req.dataPart.get.documentId)
            if (req.forAttachment) ps.setString(position.next(), req.filename.get)
            ps.executeQuery()
        }

        // Create Row object with first row of result
        if (resultSet.next()) {
            val row = new Row(resultSet.getTimestamp("created"),
                              if (req.forData) Option(resultSet.getString("username" )) else None,
                              if (req.forData) Option(resultSet.getString("groupname")) else None,
                              if (req.forData) Option(resultSet.getInt("form_version")) else None)
            // The query could return multiple rows if we have both a draft and non-draft, but the `created`,
            // `username`, `groupname`, and `form_version` must be the same on all rows, so it doesn't matter from
            // which row we read this from.
            Some(row)
        } else {
            None
        }
    }

    private def store(connection: Connection, req: Request, existingRow: Option[Row], delete: Boolean): Unit = {

        val table = tableName(req)

        // Do insert
        locally {
            val xmlCol = if (req.provider == "oracle") "xml_clob" else "xml"
            val ps = connection.prepareStatement(
                s"""|insert into $table
                    |(
                    |                                   created, last_modified_time, last_modified_by
                    |                                 , app, form, form_version
                    |    ${if (req.forData)          ", document_id"             else ""}
                    |                                 , deleted
                    |    ${if (req.forData)          ", draft"                   else ""}
                    |    ${if (req.forAttachment)    ", file_name, file_content" else ""}
                    |    ${if (! req.forAttachment) s", $xmlCol"                 else ""}
                    |    ${if (req.forData)          ", username, groupname"     else ""}
                    |)
                    |values
                    |(
                    |                               ?, ?, ?
                    |                               , ?, ?, ?
                    |    ${if (req.forData)         ", ?"    else ""}
                    |                               , ${if (delete) "'Y'" else "'N'"}
                    |    ${if (req.forData)         ", ?"    else ""}
                    |    ${if (req.forAttachment)   ", ?, ?" else ""}
                    |    ${if (! req.forAttachment) ", ?" else ""}
                    |    ${if (req.forData)         ", ?, ?" else ""}
                    |)
                    |""".stripMargin)

            // For put/update, reads the request either as bytes or XML
            object RequestReader {
                def requestInputStream(): InputStream = {
                    RequestGenerator.getRequestBody(PipelineContext.get) match {
                        case bodyURL: String ⇒ NetUtils.uriToInputStream(bodyURL)
                        case _ ⇒ httpRequest.getInputStream
                    }
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

            val position = Iterator.from(1)
            val now = new Timestamp(System.currentTimeMillis())

                                         ps.setTimestamp(position.next(), existingRow.map(_.created).getOrElse(now))
                                         ps.setTimestamp(position.next(), now)
                                         ps.setString(position.next(), requestUsername.getOrElse(null))
                                         ps.setString(position.next(), req.app)
                                         ps.setString(position.next(), req.form)
                                         ps.setInt   (position.next(), existingRow.map(_.formVersion).flatten.getOrElse(requestedFormVersion(connection, req)))
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
                                         ps.setString(position.next(), existingRow.map(_.group).flatten.getOrElse(requestGroup.getOrElse(null)))
            }

            ps.executeUpdate()
        }

        // If we saved a "normal" document (not a draft), delete any draft document and draft attachments
        if (req.forData && ! req.dataPart.get.isDraft && ! req.forAttachment) {
            for (table ←  Set("orbeon_form_data", "orbeon_form_data_attach")) {
                val ps = connection.prepareStatement(
                    s"""|delete from $table
                        |where
                        |    app             = ?
                        |    and form        = ?
                        |    and document_id = ?
                        |    and draft       = 'Y'
                        |""".stripMargin)
                val position = Iterator.from(1)
                ps.setString(position.next(), req.app)
                ps.setString(position.next(), req.form)
                ps.setString(position.next(), req.dataPart.get.documentId)
                ps.executeUpdate()
            }
        }
    }

    /**
     * If we just saved a draft, older drafts (if any) for the same app/form/document-id/file-name.
     *
     * - In the query, in the last_modified_time != ... part of the query, the additional level of nesting required
     *   on MySQL; see: http://stackoverflow.com/a/45498/5295.
     */
    private def deleteDraftOnSaveData(connection: Connection, req: Request): Unit = {
        val table = tableName(req)
        val ps = connection.prepareStatement(
            s"""|delete from $table
                |where
                |    app = ?
                |    and form = ?
                |    and document_id = ?
                |    ${if (req.forAttachment) "and file_name = ?" else ""}
                |    and draft = 'Y'
                |    and last_modified_time !=
                |        (
                |            select last_modified_time from
                |            (
                |                select max(last_modified_time) last_modified_time
                |                from $table
                |                where
                |                    app = ?
                |                    and form = ?
                |                    and document_id = ?
                |                    ${if (req.forAttachment) "and file_name = ?" else ""}
                |                    and draft = 'Y'
                |            ) t
                |        )
                |""".stripMargin)

        val position = Iterator.from(1)

        // Run twice as the the same values are used in the inner
        for (i ← 1 to 2) {
            ps.setString(position.next(), req.app)
            ps.setString(position.next(), req.form)
            ps.setString(position.next(), req.dataPart.get.documentId)
            if (req.forAttachment) ps.setString(position.next(), req.filename.get)
        }

        ps.executeUpdate()
    }

    /**
     * We want to delete drafts in the following two cases:
     * - Data is deleted, in which case we don't want to keep corresponding drafts
     * - A draft is explicitly deleted, which can be done from the summary page -->
     */
    private def deleteDraft(connection: Connection, req: Request): Unit = {
        for (table ← Seq("orbeon_form_data", "orbeon_form_data_attach")) {
            val ps = connection.prepareStatement(
                s"""|delete from $table
                    |where      app         = ?
                    |       and form        = ?
                    |       and document_id = ?
                    |       and draft = 'Y'
                    |""".stripMargin)
            val position = Iterator.from(1)
            ps.setString(position.next(), req.app)
            ps.setString(position.next(), req.form)
            ps.setString(position.next(), req.dataPart.get.documentId)
            ps.executeUpdate()
        }
    }

    def change(req: Request, delete: Boolean): Unit = {
        RelationalUtils.withConnection { connection ⇒

            // Initial test on version that doesn't rely on accessing the database to read a document; we do this first:
            // - For correctness: e.g., a PUT for a document id is an invalid request, but if we start by checking
            //   permissions, we might not find the document and return a 400 instead.
            // - For efficiency: when we can, it's better to 400 right away without accessing the database.
            def checkVersionInitial(): Unit = {
                val badVersion =
                    // Only GET for form definitions can request a version for a given document
                    req.version.isInstanceOf[ForDocument] ||
                    // Delete: no version can be specified
                    req.forData && delete && ! (req.version == Unspecified)
                if (badVersion) throw HttpStatusCodeException(400)
            }

            def checkAuthorized(existing: Option[Row]): Unit = {
                val authorized =
                    if (req.forData) {
                        if (existing.isDefined) {
                            // Check we're allowed to update or delete this resource
                            val username      = existing.get.username
                            val groupname     = existing.get.group
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
                if (! authorized) throw HttpStatusCodeException(403)
            }

            def checkVersionWithExisting(existing: Option[Row]): Unit = {

                def isUpdate =
                    ! delete && existing.nonEmpty

                def isCreate =
                    ! delete && existing.isEmpty

                def existingVersion =
                    existing flatMap (_.formVersion)

                def isUnspecifiedOrSpecificVersion =
                    req.version match {
                        case Unspecified       ⇒ true
                        case Specific(version) ⇒ Some(version) == existingVersion
                        case _                 ⇒ false
                    }

                def isSpecificVersion =
                    req.version.isInstanceOf[Specific]

                def badVersion =
                    (req.forData && isUpdate && ! isUnspecifiedOrSpecificVersion) ||
                    (req.forData && isCreate && ! isSpecificVersion)

                if (badVersion)
                    throw HttpStatusCodeException(400)
            }

            def checkDocExistsForDelete(existing: Option[Row]): Unit = {
                // We can't delete a document that doesn't exist
                val nothingToDelete = delete && existing.isEmpty
                if (nothingToDelete) throw HttpStatusCodeException(404)
            }

            // Checks
            checkVersionInitial()
            val existing = existingRow(connection, req)
            checkAuthorized(existing)
            checkVersionWithExisting(existing)
            checkDocExistsForDelete(existing)

            // Update database
            store(connection, req, existing, delete)
            if (! delete && req.forData && req.dataPart.get.isDraft)
                deleteDraftOnSaveData(connection, req)
            if (delete && req.forData)
                deleteDraft(connection, req)

            httpResponse.setStatus(if (delete) 204 else 201)
        }
    }
}
