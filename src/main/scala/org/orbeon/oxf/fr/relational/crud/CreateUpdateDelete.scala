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

import java.io.{Writer, InputStream, ByteArrayOutputStream}
import java.sql.{Array ⇒ _, _}
import javax.xml.transform.OutputKeys
import javax.xml.transform.sax.{SAXResult, SAXSource}
import javax.xml.transform.stream.StreamResult
import org.orbeon.oxf.fr.relational._
import org.orbeon.oxf.pipeline.api.PipelineContext
import org.orbeon.oxf.processor.generator.RequestGenerator
import org.orbeon.oxf.util.ScalaUtils._
import org.orbeon.oxf.util.{Whitespace, XPath, StringBuilderWriter, NetUtils}
import org.orbeon.oxf.xml._
import org.xml.sax.InputSource
import org.orbeon.saxon.om.DocumentInfo
import org.orbeon.scaxon.SAXEvents.{Atts, StartElement}
import org.orbeon.oxf.fr.FormRunner.{XH, XF}
import org.orbeon.oxf.xml.JXQName
import org.orbeon.oxf.fr.relational.Specific
import org.orbeon.oxf.fr.relational.ForDocument
import org.orbeon.oxf.webapp.HttpStatusCodeException

object RequestReader {

    object IdAtt {
        val IdQName = JXQName("id")
        def unapply(atts: Atts) = atts.atts collectFirst { case (IdQName, value) ⇒ value }
    }

    // NOTE: Tested that the pattern match works optimally: with form-with-metadata.xhtml, JQName.unapply is
    // called 17 times and IdAtt.unapply 2 times until the match is found, which is what is expected.
    def isMetadataElement(stack: List[StartElement]): Boolean =
        stack match {
            case
                StartElement(JXQName("", "metadata"), _)                         ::
                StartElement(JXQName(XF, "instance"), IdAtt("fr-form-metadata")) ::
                StartElement(JXQName(XF, "model"),    IdAtt("fr-form-model"))    ::
                StartElement(JXQName(XH, "head"), _)                             ::
                StartElement(JXQName(XH, "html"), _)                             ::
                Nil ⇒ true
            case _  ⇒ false
        }

    def requestInputStream(): InputStream = {
        RequestGenerator.getRequestBody(PipelineContext.get) match {
            case bodyURL: String ⇒ NetUtils.uriToInputStream(bodyURL)
            case _               ⇒ NetUtils.getExternalContext.getRequest.getInputStream
        }
    }

    def bytes(): Array[Byte] = {
        val os = new ByteArrayOutputStream
        NetUtils.copyStream(requestInputStream(), os)
        os.toByteArray
    }

    def dataAndMetadataAsString(metadata: Boolean): (String, Option[String]) =
        dataAndMetadataAsString(requestInputStream(), metadata)

    def dataAndMetadataAsString(inputStream: InputStream, metadata: Boolean): (String, Option[String]) = {

        def newTransformer = (
            TransformerUtils.getXMLIdentityTransformer
            |!> (_.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes"))
        )

        def newIdentityReceiver(writer: Writer) = (
            TransformerUtils.getIdentityTransformerHandler
            |!> (_.getTransformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes"))
            |!> (_.setResult(new StreamResult(writer)))
        )

        val metadataWriterAndReceiver = metadata option {

            val metadataWriter = new StringBuilderWriter()

            // MAYBE: strip enclosing namespaces; remove description; truncate long titles
            val metadataFilter =
                new FilterReceiver(
                    new WhitespaceXMLReceiver(
                        newIdentityReceiver(metadataWriter),
                        Whitespace.Normalize,
                        (_, _, _, _) ⇒ Whitespace.Normalize
                    ),
                    isMetadataElement
                )

            (metadataWriter, metadataFilter)
        }

        val source     = new SAXSource(XMLParsing.newXMLReader(XMLParsing.ParserConfiguration.PLAIN), new InputSource(inputStream))
        val dataWriter = new StringBuilderWriter()

        val resultReceiver = metadataWriterAndReceiver match {
            case Some((_, metadataFilter)) ⇒
                new TeeXMLReceiver(newIdentityReceiver(dataWriter), metadataFilter)
            case None ⇒
                newIdentityReceiver(dataWriter)
        }

        newTransformer.transform(source, new SAXResult(resultReceiver))

        (dataWriter.toString, metadataWriterAndReceiver map (_._1.toString))
    }

    def xmlDocument(): DocumentInfo =
        TransformerUtils.readTinyTree(XPath.GlobalConfiguration, requestInputStream(), "", false, false)
}

trait CreateUpdateDelete extends RequestResponse with Common {

    private case class Row(created: Timestamp, username: Option[String], group: Option[String], formVersion: Option[Int])

    private def existingRow(connection: Connection, req: Request): Option[Row] = {

        val idCols = idColumns(req)
        val table  = tableName(req)
        val resultSet = {
            val ps = connection.prepareStatement(
                s"""|SELECT created
                    |       ${if (req.forData) ", username , groupname, form_version" else ""}
                    |FROM   $table t,
                    |       (
                    |           SELECT   max(last_modified_time) last_modified_time, ${idCols.mkString(", ")}
                    |           FROM     $table
                    |           WHERE    app  = ?
                    |                    and form = ?
                    |                    ${if (! req.forData)     "and form_version = ?" else ""}
                    |                    ${if (req.forData)       "and document_id  = ?" else ""}
                    |                    ${if (req.forAttachment) "and file_name    = ?" else ""}
                    |           GROUP BY ${idCols.mkString(", ")}
                    |       ) m
                    |WHERE  ${joinColumns("last_modified_time" +: idCols, "t", "m")}
                    |       AND deleted = 'N'
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


    private def store(connection: Connection, req: Request, existingRow: Option[Row], delete: Boolean): Int = {

        val table = tableName(req)
        val versionToSet = existingRow.map(_.formVersion).flatten.getOrElse(requestedFormVersion(connection, req))

        def param[T](setter: (PreparedStatement) ⇒ ((Int, T) ⇒ Unit), value: ⇒ T): (PreparedStatement, Int) ⇒ Unit = {
            (ps: PreparedStatement, i: Int) ⇒ setter(ps)(i, value)
        }

        // Do insert
        locally {
            val xmlCol = if (req.provider == "oracle") "xml_clob" else "xml"
            val isFormDefinition = req.forForm && ! req.forAttachment
            val now = new Timestamp(System.currentTimeMillis())

            val (xmlOpt, metadataOpt) =
                if (! delete && ! req.forAttachment) {
                    val (xml, metadataOpt) = RequestReader.dataAndMetadataAsString(metadata = !req.forData)
                    (Some(xml), metadataOpt)
                } else {
                    (None, None)
                }

            val possibleCols = List(
                true                  → "created"            → param(_.setTimestamp, existingRow.map(_.created).getOrElse(now)),
                true                  → "last_modified_time" → param(_.setTimestamp, now),
                true                  → "last_modified_by"   → param(_.setString   , requestUsername.orNull),
                true                  → "app"                → param(_.setString   , req.app),
                true                  → "form"               → param(_.setString   , req.form),
                true                  → "form_version"       → param(_.setInt      , versionToSet),
                req.forData           → "document_id"        → param(_.setString   , req.dataPart.get.documentId),
                true                  → "deleted"            → param(_.setString   , if (delete) "Y" else "N"),
                req.forData           → "draft"              → param(_.setString   , if (req.dataPart.get.isDraft) "Y" else "N"),
                req.forAttachment     → "file_name"          → param(_.setString   , req.filename.get),
                req.forAttachment     → "file_content"       → param(_.setBytes    , RequestReader.bytes()),
                isFormDefinition      → "form_metadata"      → param(_.setString   , metadataOpt.orNull),
                req.forData           → "username"           → param(_.setString   , existingRow.map(_.username).flatten.getOrElse(requestUsername.orNull)),
                req.forData           → "groupname"          → param(_.setString   , existingRow.map(_.group   ).flatten.getOrElse(requestGroup.orNull)),
                ! req.forAttachment   → xmlCol               → param(_.setString   , xmlOpt.orNull)
            )

            val includedCols =
                for {
                    ((included, col), param) ← possibleCols
                    if included
                } yield col → param

            val ps = connection.prepareStatement(
                s"""|INSERT INTO $table
                    |            ( ${includedCols.map(_._1   ).mkString(", ")} )
                    |     VALUES ( ${includedCols.map(_ ⇒ "?").mkString(", ")} )
                    |""".stripMargin)

            for (((_, param), i) ← includedCols.zipWithIndex)
                param(ps, i + 1)
            ps.executeUpdate()
        }

        // If we saved a "normal" document (not a draft), delete any draft document and draft attachments
        if (req.forData && ! req.dataPart.get.isDraft && ! req.forAttachment) {
            for (table ←  Set("orbeon_form_data", "orbeon_form_data_attach")) {
                val ps = connection.prepareStatement(
                    s"""|DELETE FROM $table
                        |WHERE  app             = ?
                        |       AND form        = ?
                        |       AND document_id = ?
                        |       AND draft       = 'Y'
                        |""".stripMargin)
                val position = Iterator.from(1)
                ps.setString(position.next(), req.app)
                ps.setString(position.next(), req.form)
                ps.setString(position.next(), req.dataPart.get.documentId)
                ps.executeUpdate()
            }
        }

        versionToSet
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

        // Read before establishing a connection, so we don't use two simultaneous connections
        val formMetadata = req.forData option readFormMetadata(req)

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
                            val authorizedOps = authorizedOperations(formMetadata.get, dataUserGroup)
                            val requiredOp    = if (delete) "delete" else "update"
                            authorizedOps.contains(requiredOp)
                        } else {
                            // For deletes, if there is no data to delete, it is a 403 if could not read, update,
                            // or delete if it existed (otherwise code later will return a 404)
                            val authorizedOps = authorizedOperations(formMetadata.get, None)
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
            val versionSet = store(connection, req, existing, delete)
            if (! delete && req.forData && req.dataPart.get.isDraft)
                deleteDraftOnSaveData(connection, req)
            if (delete && req.forData)
                deleteDraft(connection, req)

            // Create flat view if needed
            if (requestFlatView && Set("oracle", "db2")(req.provider) && req.forForm && ! delete && req.form != "library")
                FlatView.createFlatView(req, connection)

            // Inform caller of the form definition version used
            httpResponse.setHeader("Orbeon-Form-Definition-Version", versionSet.toString)

            httpResponse.setStatus(if (delete) 204 else 201)
        }
    }
}
