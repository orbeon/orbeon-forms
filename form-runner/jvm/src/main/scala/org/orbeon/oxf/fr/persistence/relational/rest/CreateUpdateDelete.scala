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
package org.orbeon.oxf.fr.persistence.relational.rest

import java.io.{ByteArrayOutputStream, InputStream, Writer}
import java.sql.{Array => _, _}
import javax.xml.transform.OutputKeys
import javax.xml.transform.sax.{SAXResult, SAXSource}
import javax.xml.transform.stream.StreamResult
import org.orbeon.io.IOUtils._
import org.orbeon.io.{IOUtils, StringBuilderWriter}
import org.orbeon.oxf.externalcontext.UserAndGroup
import org.orbeon.oxf.fr.{FormDefinitionVersion, FormRunner, FormRunnerPersistence, Names}
import org.orbeon.oxf.fr.XMLNames.{XF, XH}
import org.orbeon.oxf.fr.permission.Operation.{Create, Delete, Read, Update}
import org.orbeon.oxf.fr.permission.PermissionsAuthorization.{CheckWithDataUser, CheckWithoutDataUser}
import org.orbeon.oxf.fr.permission._
import org.orbeon.oxf.fr.persistence.relational.RelationalCommon._
import org.orbeon.oxf.fr.persistence.relational.{Provider, RelationalUtils}
import org.orbeon.oxf.fr.persistence.relational.Version._
import org.orbeon.oxf.fr.persistence.relational.index.Index
import org.orbeon.oxf.http.{HttpStatusCodeException, StatusCode}
import org.orbeon.oxf.pipeline.api.{PipelineContext, TransformerXMLReceiver}
import org.orbeon.oxf.processor.generator.RequestGenerator
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.util.Logging._
import org.orbeon.oxf.util.{NetUtils, Whitespace, XPath}
import org.orbeon.oxf.xml.{JXQName, _}
import org.orbeon.saxon.event.SaxonOutputKeys
import org.orbeon.saxon.om.DocumentInfo
import org.orbeon.scaxon.SAXEvents.{Atts, StartElement}
import org.xml.sax.InputSource


object RequestReader {

  object IdAtt {
    val IdQName = JXQName("id")
    def unapply(atts: Atts) = atts.atts collectFirst { case (IdQName, value) => value }
  }

  // See https://github.com/orbeon/orbeon-forms/issues/2385
  private val MetadataElementsToKeep = Set(
    "metadata",
    "title",
    "permissions",
    "available"
  )

  // NOTE: Tested that the pattern match works optimally: with form-with-metadata.xhtml, `JQName.unapply` is
  // called 17 times and `IdAtt.unapply` 2 times until the match is found, which is what is expected.
  def isMetadataElement(stack: List[StartElement]): Boolean =
    stack match {
      case
        StartElement(JXQName("", "metadata"), _)                             ::
        StartElement(JXQName(XF, "instance"), IdAtt(Names.MetadataInstance)) ::
        StartElement(JXQName(XF, "model"),    IdAtt(Names.FormModel))        ::
        StartElement(JXQName(XH, "head"), _)                                 ::
        StartElement(JXQName(XH, "html"), _)                                 ::
        Nil => true
      case _  => false
    }

  def requestInputStream(): InputStream =
    RequestGenerator.getRequestBody(PipelineContext.get) match {
      case Some(bodyURL) => NetUtils.uriToInputStream(bodyURL)
      case None          => NetUtils.getExternalContext.getRequest.getInputStream
    }

  def bytes(): Array[Byte] = {
    val os = new ByteArrayOutputStream
    IOUtils.copyStreamAndClose(requestInputStream(), os)
    os.toByteArray
  }

  def dataAndMetadataAsString(provider: Provider, metadata: Boolean): (String, Option[String]) =
    dataAndMetadataAsString(provider, requestInputStream(), metadata)

  def dataAndMetadataAsString(provider: Provider, inputStream: InputStream, metadata: Boolean): (String, Option[String]) = {

    def newTransformer = (
      TransformerUtils.getXMLIdentityTransformer
      |!> (_.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes"))
    )

    def newIdentityReceiver(writer: Writer): TransformerXMLReceiver = {
      val receiver    = TransformerUtils.getIdentityTransformerHandler

      // Configure the receiver's transformer
      locally {
        val transformer = receiver.getTransformer
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION     , "yes")
        transformer.setOutputProperty(OutputKeys.INDENT                   , "no")
        transformer.setOutputProperty(SaxonOutputKeys.INCLUDE_CONTENT_TYPE, "no")
        val escapeNonAsciiCharacters = FormRunner.providerPropertyAsBoolean(
          provider = provider.entryName,
          property = "escape-non-ascii-characters",
          default = false
        )
        if (escapeNonAsciiCharacters)
          transformer.setOutputProperty(OutputKeys.ENCODING, "US-ASCII")
      }

      receiver.setResult(new StreamResult(writer))
      receiver
    }

    val metadataWriterAndReceiver = metadata option {

      val metadataWriter = new StringBuilderWriter()

      // MAYBE: strip enclosing namespaces; truncate long titles
      val metadataFilter =
        new FilterReceiver(
          new WhitespaceXMLReceiver(
            new ElementFilterXMLReceiver(
              newIdentityReceiver(metadataWriter),
              (level, uri, localname, _) => level != 1 || level == 1 && uri == "" && MetadataElementsToKeep(localname)
            ),
            Whitespace.Policy.Normalize,
            (_, _, _, _) => Whitespace.Policy.Normalize
          ),
          isMetadataElement
        )

      (metadataWriter, metadataFilter)
    }

    val source     = new SAXSource(XMLParsing.newXMLReader(ParserConfiguration.Plain), new InputSource(inputStream))
    val dataWriter = new StringBuilderWriter()

    val resultReceiver = metadataWriterAndReceiver match {
      case Some((_, metadataFilter)) =>
        new TeeXMLReceiver(newIdentityReceiver(dataWriter), metadataFilter)
      case None =>
        newIdentityReceiver(dataWriter)
    }

    newTransformer.transform(source, new SAXResult(resultReceiver))

    (dataWriter.result, metadataWriterAndReceiver map (_._1.result))
  }

  // Used by FlatView
  def xmlDocument(): DocumentInfo =
    TransformerUtils.readTinyTree(XPath.GlobalConfiguration, requestInputStream(), "", false, false)
}

trait CreateUpdateDelete
  extends RequestResponse
     with Common
     with CreateCols {

  private def existingRow(connection: Connection, req: Request): Option[Row] = {

    val idCols = idColumns(req).filter(_ != "file_name")
    val table  = tableName(req, master = true)
    val sql =
      s"""|SELECT created
          |       ${if (req.forData) ", username , groupname, organization_id, form_version" else ""}
          |FROM   $table t,
          |       (
          |           SELECT   max(last_modified_time) last_modified_time, ${idCols.mkString(", ")}
          |           FROM     $table
          |           WHERE    app  = ?
          |                    and form = ?
          |                    ${if (! req.forData)     "and form_version = ?" else ""}
          |                    ${if (req.forData)       "and document_id  = ?" else ""}
          |           GROUP BY ${idCols.mkString(", ")}
          |       ) m
          |WHERE  ${joinColumns("last_modified_time" +: idCols, "t", "m")}
          |       AND deleted = 'N'
          |""".stripMargin

    useAndClose(connection.prepareStatement(sql)) { ps =>

      val position = Iterator.from(1)
      ps.setString(position.next(), req.app)
      ps.setString(position.next(), req.form)
      if (! req.forData)     ps.setInt   (position.next(), requestedFormVersion(req))

      req.dataPart foreach (dataPart => ps.setString(position.next(), dataPart.documentId))

      useAndClose(ps.executeQuery()) { resultSet =>

        // Create Row object with first row of result
        if (resultSet.next()) {
          // The query could return multiple rows if we have both a draft and non-draft, but the `created`,
          // `username`, `groupname`, and `form_version` must be the same on all rows, so it doesn't matter from
          // which row we read this from.
          Some(Row(
            created      = resultSet.getTimestamp("created"),
            createdBy    = if (req.forData) UserAndGroup.fromStrings(resultSet.getString("username"), resultSet.getString("groupname")) else None,
            organization = if (req.forData) OrganizationSupport.readFromResultSet(connection, resultSet)                                else None,
            formVersion  = if (req.forData) Option(resultSet.getInt("form_version"))                                                    else None,
            stage        = None // We don't need to know about the stage of a possible existing row
          ))
        } else {
          None
        }
      }
    }
  }

  // NOTE: Gets the first organization if there are multiple organization roots
  private def currentUserOrganization(connection: Connection, req: Request): Option[OrganizationId] =
    httpRequest.credentials.flatMap(_.defaultOrganization).map(OrganizationSupport.createIfNecessary(connection, req.provider, _))

  private def store(connection: Connection, req: Request, existingRow: Option[Row], delete: Boolean): Int = {

    val table = tableName(req)
    val versionToSet = existingRow.flatMap(_.formVersion).getOrElse(requestedFormVersion(req))

    // If for data, start by deleting any draft document and draft attachments
    req.dataPart match {
      case Some(dataPart) if ! req.forAttachment =>

        val fromControlIndexWhere =
          s"""|WHERE data_id IN
              |    (
              |        SELECT data_id
              |          FROM orbeon_i_current
              |         WHERE document_id = ?   AND
              |               draft       = 'Y'
              |    )
              |""".stripMargin

        val orderByClause = req.provider match {
          case Provider.MySQL => " ORDER BY last_modified_time"
          case _              => ""
        }

        val fromControlIndexCount = "SELECT count(*) FROM orbeon_i_control_text " + fromControlIndexWhere

        val count =
          useAndClose(connection.prepareStatement(fromControlIndexCount)) { ps =>
            ps.setString(1, dataPart.documentId)
            useAndClose(ps.executeQuery()) { rs =>
              rs.next()
              rs.getInt(1)
            }
          }

        if (count > 0) {
          val deleteFromControlIndexSql = "DELETE FROM orbeon_i_control_text " + fromControlIndexWhere
          useAndClose(connection.prepareStatement(deleteFromControlIndexSql)) { ps =>
            ps.setString(1, dataPart.documentId)
            ps.executeUpdate()
          }
        }

        // Then delete from all the other tables

        // See https://github.com/orbeon/orbeon-forms/issues/2980: when saving the data for a draft, we don't want to
        // remove draft attachments from `orbeon_form_data_attach`, otherwise the attachments which were just saved are
        // immediately removed! So we remove draft attachments from `from orbeon_form_data_attach` only when we are
        // saving data which is not for a draft. Note that there is no garbage collection for attachments.

        val tablesToDeleteDraftsFrom =
          (! dataPart.isDraft list "orbeon_form_data_attach") :::
          "orbeon_i_current"                                  ::
          "orbeon_form_data"                                  ::
          Nil

        tablesToDeleteDraftsFrom.foreach { table =>

          val fromWhere =
            s"""|
                |       FROM $table
                |      WHERE document_id = ?   AND
                |            draft       = 'Y'
                |""".stripMargin
          val select = "     SELECT count(*) count" + fromWhere
          val delete = "     DELETE" + fromWhere + orderByClause
          val count =
            useAndClose(connection.prepareStatement(select)) { ps =>
              ps.setString(1, dataPart.documentId)
              useAndClose(ps.executeQuery()) { rs =>
                rs.next()
                rs.getInt("count")
              }
            }
          if (count > 0)
            useAndClose(connection.prepareStatement(delete)) { ps =>
              ps.setString(1, dataPart.documentId)
              ps.executeUpdate()
            }
        }

        // 1. In `CreateUpdateDelete.scala`:
        //   1.1. Above, we read/write from/to orbeon_i_current/orbeon_i_control_text (remove drafts)
        //   1.2. Below, we write      to      orbeon_form_data                       (write data)
        // 2. In `Reindex.scala`:
        //   2.1. 1st,   we read       from    orbeon_form_data                       (get data to index)
        //   2.2. 2nd,   we write      to      orbeon_i_current/orbeon_i_control_text (update the index)
        // This can lease to a deadlock, hence here doing a commit after deleting the drafts.
        // The downside is that if writing the data (1.2) fails, with the commit we'll have lost the draft, which seems negligible.
        connection.commit()

      case _ =>

    }

    // Do insert, unless we're deleting draft data
    val deletingDataDraft = delete && req.dataPart.exists(_.isDraft)
    if (! deletingDataDraft) {
      val includedCols = insertCols(req, existingRow, delete, versionToSet, currentUserOrganization(connection, req))
      val colNames     = includedCols.map(_.name).mkString(", ")
      val colValues    =
        includedCols
          .map(_.value match {
            case StaticColValue(value)           => value
            case DynamicColValue(placeholder, _) => placeholder})
          .mkString(", ")

      val insertSql =
        s"""|INSERT INTO $table
            |            ( $colNames  )
            |     VALUES ( $colValues )
            |""".stripMargin
      useAndClose(connection.prepareStatement(insertSql)) { ps =>

        // Set parameters in prepared statement for the dynamic values
        includedCols
          .map(_.value)
          .collect({ case DynamicColValue(_, paramSetter) => paramSetter })
          .zipWithIndex
          .foreach{ case (paramSetter, index) => paramSetter(ps, index + 1)}

        ps.executeUpdate()
      }
    }

    versionToSet
  }

  def change(req: Request, delete: Boolean): Unit = {

    debug("CRUD: handling change request", List("delete" -> delete.toString, "request" -> req.toString))

    val existing =
      RelationalUtils.withConnection { connection =>
        existingRow(connection, req)
      }
    val versionToSet = existing.flatMap(_.formVersion).getOrElse(requestedFormVersion(req))

    // Read outside of a `withConnection` block, so we don't use two simultaneous connections
    val formPermissions = {
      val elOpt = req.forData.option(RelationalUtils.readFormPermissions(req.app, req.form, FormDefinitionVersion.Specific(versionToSet))).flatten
      PermissionsXML.parse(elOpt.orNull)
    }
    debug("CRUD: form permissions", List("permissions" -> formPermissions.toString))

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

      if (badVersion)
        debug("CRUD: bad version", List("status code" -> StatusCode.BadRequest.toString))

      if (badVersion)
        throw HttpStatusCodeException(StatusCode.BadRequest)
    }

    def checkAuthorized(existing: Option[Row]): Unit = {
      val authorized =
        if (req.forData) {
          existing match {
            case Some(existing) =>

              // Check we're allowed to update or delete this resource
              val createdBy     = existing.createdBy
              val organization  = existing.organization.map(_._2)
              val authorizedOps = PermissionsAuthorization.authorizedOperations(
                formPermissions,
                PermissionsAuthorization.currentUserFromSession,
                CheckWithDataUser(createdBy, organization)
              )
              val requiredOp    = if (delete) Delete else Update
              Operations.allows(authorizedOps, requiredOp)
            case None =>
              // For deletes, if there is no data to delete, it is a 403 if could not read, update,
              // or delete if it existed (otherwise code later will return a 404)
              val authorizedOps = PermissionsAuthorization.authorizedOperations(
                formPermissions,
                PermissionsAuthorization.currentUserFromSession,
                CheckWithoutDataUser(optimistic = false)
              )
              val requiredOps   = if (delete) List(Read, Update, Delete) else List(Create)
              Operations.allowsAny(authorizedOps, requiredOps)
          }
        } else {
          // Operations on deployed forms are always authorized
          true
        }

      if (! authorized)
        debug("CRUD: not authorized", List("status code" -> StatusCode.Forbidden.toString))

      if (! authorized)
        throw HttpStatusCodeException(StatusCode.Forbidden)
    }

    def checkVersionWithExisting(existing: Option[Row]): Unit = {

      def isUpdate =
        ! delete && existing.nonEmpty

      def isCreate =
        ! delete && existing.isEmpty

      def existingVersionOpt =
        existing flatMap (_.formVersion)

      def isUnspecifiedOrSpecificVersion =
        req.version match {
          case Unspecified       => true
          case Specific(version) => existingVersionOpt.contains(version)
          case _                 => false
        }

      def isSpecificVersion =
        req.version.isInstanceOf[Specific]

      val badVersion =
        (req.forData && isUpdate && ! isUnspecifiedOrSpecificVersion) ||
        (req.forData && isCreate && ! isSpecificVersion)

      if (badVersion)
        debug("CRUD: bad version", List("status code" -> StatusCode.BadRequest.toString))

      if (badVersion)
        throw HttpStatusCodeException(StatusCode.BadRequest)
    }

    def checkDocExistsForDelete(existing: Option[Row]): Unit = {
      // We can't delete a document that doesn't exist
      val nothingToDelete = delete && existing.isEmpty

      debug("CRUD: nothing to delete", List("status code" -> StatusCode.NotFound.toString))

      if (nothingToDelete)
        throw HttpStatusCodeException(StatusCode.NotFound)
    }

    // Checks
    checkVersionInitial()

    debug("CRUD: retrieved existing row", List("existing" -> existing.isDefined.toString))

    checkAuthorized(existing)
    checkVersionWithExisting(existing)
    checkDocExistsForDelete(existing)

    RelationalUtils.withConnection { connection =>

      // Update database
      val versionSet = store(connection, req, existing, delete)

      debug("CRUD: database updated, before commit", List("version" -> versionSet.toString))

      // Commit before reindexing, as reindexing will read back the form definition, which can
      // cause a deadlock since we're still in the transaction writing the form definition
      connection.commit()

      debug("CRUD: after commit")

      // Update index
      val whatToReindex = req.dataPart match {
        case Some(dataPart) =>
          // Data: update index for this document id
          Index.WhatToReindex.DataForDocumentId(dataPart.documentId)
        case None =>
          // Form definition: update index for this form version
          // Re. the asInstanceOf, when updating a form, we must have a specific version specified
          Index.WhatToReindex.DataForForm(req.app, req.form, versionSet)
      }

      withDebug("CRUD: reindexing", List("what" -> whatToReindex.toString)) {
        Index.reindex(req.provider, connection, whatToReindex)
      }

      // Create flat view if needed
      if (
        requestFlatView                                   &&
        Provider.FlatViewSupportedProviders(req.provider) &&
        req.forForm                                       &&
        ! req.forAttachment                               &&
        ! delete                                          &&
        req.form != Names.LibraryFormName
      ) withDebug("CRUD: creating flat view") {
        FlatView.createFlatView(req, versionSet, connection)
      }

      // Inform caller of the form definition version used
      httpResponse.setHeader(OrbeonFormDefinitionVersion, versionSet.toString)

      httpResponse.setStatus(if (delete) 204 else 201)
    }
  }
}
