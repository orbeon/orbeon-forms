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

import org.orbeon.io.IOUtils._
import org.orbeon.io.{IOUtils, StringBuilderWriter}
import org.orbeon.oxf.externalcontext.{ExternalContext, UserAndGroup}
import org.orbeon.oxf.fr.XMLNames.{XF, XH}
import org.orbeon.oxf.fr.persistence.PersistenceMetadataSupport
import org.orbeon.oxf.fr.persistence.relational.Version._
import org.orbeon.oxf.fr.persistence.relational.index.Index
import org.orbeon.oxf.fr.persistence.relational.rest.SqlSupport._
import org.orbeon.oxf.fr.persistence.relational.{Provider, RelationalUtils, WhatToReindex}
import org.orbeon.oxf.fr.{FormRunner, Names}
import org.orbeon.oxf.http.{EmptyInputStream, Headers, HttpStatusCodeException, StatusCode}
import org.orbeon.oxf.pipeline.api.{PipelineContext, TransformerXMLReceiver}
import org.orbeon.oxf.processor.generator.RequestGenerator
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.util.Logging._
import org.orbeon.oxf.util.{DateUtils, NetUtils, Whitespace, XPath}
import org.orbeon.oxf.xml._
import org.orbeon.saxon.event.SaxonOutputKeys
import org.orbeon.saxon.om.DocumentInfo
import org.orbeon.scaxon.SAXEvents.{Atts, StartElement}
import org.xml.sax.InputSource

import java.io.{ByteArrayOutputStream, InputStream, Writer}
import java.sql.{Array => _, _}
import java.time.Instant
import javax.xml.transform.OutputKeys
import javax.xml.transform.sax.{SAXResult, SAXSource}
import javax.xml.transform.stream.StreamResult


object RequestReader {

  private object IdAtt {
    val IdQName = JXQName("id")
    def unapply(atts: Atts) = atts.atts collectFirst { case (IdQName, value) => value }
  }

  // See https://github.com/orbeon/orbeon-forms/issues/2385
  private val MetadataElementsToKeep = Set(
    "metadata",
    "title",
    Names.Permissions,
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

  def requestInputStream(): Option[InputStream] =
    RequestGenerator.getRequestBody(PipelineContext.get) match {
      case Some(bodyURL) =>
        Some(NetUtils.uriToInputStream(bodyURL))

      case None =>
        // TODO: should getInputStream return an Option instead or does it impact too much code elsewhere?
        val inputStream = NetUtils.getExternalContext.getRequest.getInputStream
        val nonEmptyInputStream = inputStream != EmptyInputStream
        nonEmptyInputStream option inputStream
    }

  def bytes(): Option[Array[Byte]] =
    requestInputStream().map { is =>
      val os = new ByteArrayOutputStream
      IOUtils.copyStreamAndClose(is, os)
      os.toByteArray
    }

  def dataAndMetadataAsString(provider: Provider, metadata: Boolean): (Option[String], Option[String]) =
    requestInputStream().map { is =>
      val (data, metadataOpt) = dataAndMetadataAsString(provider, is, metadata)
      (Some(data), metadataOpt)
    }.getOrElse {
      (None, None)
    }

  def dataAndMetadataAsString(
    provider   : Provider,
    inputStream: InputStream,
    metadata   : Boolean
  ): (String, Option[String]) = {

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
  def xmlDocument(): Option[DocumentInfo] =
    requestInputStream().map { is =>
      TransformerUtils.readTinyTree(XPath.GlobalConfiguration, is, "", false, false)
    }
}

trait CreateUpdateDelete {

  private def findExistingRow(connection: Connection, req: CrudRequest, versionToSet: Int): Option[Row] = {

    val idCols = SqlSupport.idColumns(req).filter(_ != "file_name")
    val table  = SqlSupport.tableName(req, master = true)
    val sql =
      s"""|SELECT created
          |       ${if (req.forData) ", username , groupname, organization_id, form_version" else ""}
          |FROM   $table t,
          |       (
          |           SELECT   max(last_modified_time) last_modified_time, ${idCols.mkString(", ")}
          |           FROM     $table
          |           WHERE    app  = ?
          |                    and form = ?
          |                    ${if (req.forForm) "and form_version = ?" else ""}
          |                    ${if (req.forData) "and document_id  = ?" else ""}
          |                    ${if (req.forData) "and draft        = ?" else ""}
          |           GROUP BY ${idCols.mkString(", ")}
          |       ) m
          |WHERE  ${SqlSupport.joinColumns("last_modified_time" +: idCols, "t", "m")}
          |       AND deleted = 'N'
          |""".stripMargin

    useAndClose(connection.prepareStatement(sql)) { ps =>

      val position = Iterator.from(1)
      ps.setString(position.next(), req.appForm.app)
      ps.setString(position.next(), req.appForm.form)
      if (req.forForm)
        ps.setInt(position.next(), versionToSet)

      req.dataPart foreach { dataPart =>
        ps.setString(position.next(), dataPart.documentId)
        ps.setString(position.next(), if (dataPart.isDraft) "Y" else "N")
      }

      useAndClose(ps.executeQuery()) { resultSet =>

        // Create Row object with first row of result
        if (resultSet.next()) {
          // The query could return multiple rows if we have both a draft and non-draft, but the `created`,
          // `username`, `groupname`, and `form_version` must be the same on all rows, so it doesn't matter from
          // which row we read this from.
          Some(Row(
            createdTime  = resultSet.getTimestamp("created"),
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
  private def currentUserOrganization(connection: Connection, req: CrudRequest): Option[OrganizationId] =
    req.credentials
      .flatMap(_.defaultOrganization)
      .map(OrganizationSupport.createIfNecessary(connection, req.provider, _))

  // TODO: consider splitting into `deleteDrafts()` and `store()`
  private def store(
    connection    : Connection,
    req           : CrudRequest,
    existingRowOpt: Option[Row],
    delete        : Boolean,
    versionToSet  : Int
  ):Option[Timestamp] = {

    val table = SqlSupport.tableName(req)

    def doDelete(
      table          : String,
      documentId     : String,
      isDraft        : Boolean,
      lastModifiedOpt: Option[Instant],
      filenameOpt    : Option[String]
    ): Unit = {

      val fromPart =
        s"FROM $table"

      val iControlTextWhere =
          s"""|WHERE data_id IN
              |    (
              |        SELECT data_id
              |          FROM orbeon_i_current
              |         WHERE document_id = ?   AND
              |               draft       = ?
              |    )
              |""".stripMargin

      val otherTableWhere =
        s"""|WHERE document_id = ?   AND
            |      draft       = ?
            |""".stripMargin

      val wherePart =
        if (table == "orbeon_i_control_text")
          iControlTextWhere
        else
          otherTableWhere

      val lastModifiedPart = lastModifiedOpt.map(_ => "AND last_modified_time = ?").getOrElse("")
      val filenamePart     = filenameOpt    .map(_ => "AND file_name          = ?").getOrElse("")

      val orderByPart =
        if (table == "orbeon_i_control_text")
          Nil
        else
          req.provider match {
          case Provider.MySQL => List("ORDER BY last_modified_time")
          case _              => Nil
        }

      val selectParts = List("SELECT count(*) count", fromPart, wherePart, lastModifiedPart, filenamePart)
      val deleteParts = List("DELETE",                fromPart, wherePart, lastModifiedPart, filenamePart) ::: orderByPart

      def setParams(ps: PreparedStatement): Unit = {
        val position = Iterator.from(1)
        ps.setString(position.next(), documentId)
        ps.setString(position.next(), if (isDraft) "Y" else "N")
        lastModifiedOpt.foreach(lastModified => ps.setTimestamp(position.next(), Timestamp.from(lastModified)))
        filenameOpt    .foreach(filename     => ps.setString(position.next(), filename))
      }

      val count =
        useAndClose(connection.prepareStatement(selectParts.mkString(" "))) { ps =>
          setParams(ps)
          useAndClose(ps.executeQuery()) { rs =>
            rs.next()
            rs.getInt("count")
          }
        }
      if (count > 0)
        useAndClose(connection.prepareStatement(deleteParts.mkString(" "))) { ps =>
          setParams(ps)
          ps.executeUpdate()
        }
    }

    // If for data, start by deleting any draft document and draft attachments
    req.dataPart match {
      case Some(dataPart) if ! req.forAttachment =>

        // See https://github.com/orbeon/orbeon-forms/issues/2980: when saving the data for a draft, we don't want to
        // remove draft attachments from `orbeon_form_data_attach`, otherwise the attachments which were just saved are
        // immediately removed! So we remove draft attachments from `from orbeon_form_data_attach` only when we are
        // saving data which is not for a draft. Note that there is no garbage collection for attachments.

        val tablesToDeleteDraftsFrom =
          "orbeon_i_control_text"                             ::
          (! dataPart.isDraft list "orbeon_form_data_attach") :::
          "orbeon_i_current"                                  ::
          "orbeon_form_data"                                  ::
          Nil

        tablesToDeleteDraftsFrom.foreach { table =>
          doDelete(table, dataPart.documentId, isDraft = true, lastModifiedOpt = None, filenameOpt = None)
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
    } // end delete draft data and attachments

    req.dataPart match {
      case Some(dataPart) if dataPart.forceDelete =>

        // Force delete, AKA purge, historical data and/or attachments

        req.filename match {
          case None if req.lastModifiedOpt.isEmpty =>
            // For data but missing last modified time
            throw HttpStatusCodeException(StatusCode.BadRequest)
          case None =>
            // For data
            doDelete("orbeon_form_data", dataPart.documentId, isDraft = false, req.lastModifiedOpt, filenameOpt = None)
          case someFilename =>
            // For attachment
            // Here we delete all data matching the document id and filename, regardless of the last modified time
            doDelete("orbeon_form_data_attach", dataPart.documentId, isDraft = false, None, someFilename)
        }
      case _ =>
    }

    val deletingDataDraft = delete && req.dataPart.exists(_.isDraft)
    val forceDelete       = delete && req.dataPart.exists(_.forceDelete)

    val lastModifiedOpt = (! deletingDataDraft && ! forceDelete) option {

      // Do insert, unless we're deleting draft data or force deleting

      val currentTimestamp = new Timestamp(System.currentTimeMillis())

      val includedCols = insertCols(req, existingRowOpt, delete, versionToSet, currentTimestamp, currentUserOrganization(connection, req))
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

      currentTimestamp
    }

    lastModifiedOpt
  }

  def change(req: CrudRequest, delete: Boolean)(implicit httpResponse: ExternalContext.Response): Unit = {

    debug("CRUD: handling change request", List("delete" -> delete.toString, "request" -> req.toString))

    val versionToSet =
      req.version.getOrElse(throw HttpStatusCodeException(StatusCode.BadRequest))

    // TODO: Since the persistence proxy does a HEAD already, we must not repeat it here. Instead, the  persistence
    //  proxy must pass the information needed: existing or not, `formVersion`, `organization._1`, `createdTime`,
    //  `createdBy.username`
    val existingRowOpt =
      RelationalUtils.withConnection { connection =>
        findExistingRow(connection, req, versionToSet)
      }

    debug("CRUD: retrieved existing row", List("existing" -> existingRowOpt.isDefined.toString))

    // Just a consistency test
    existingRowOpt.flatMap(_.formVersion).foreach { versionFromExisting =>
      if (versionToSet != versionFromExisting)
        throw HttpStatusCodeException(StatusCode.Conflict) // or 400?
    }

    if (req.forForm)
      PersistenceMetadataSupport.maybeInvalidateCachesFor(req.appForm, versionToSet)

    RelationalUtils.withConnection { connection =>

      // Update database
      val lastModifiedOpt = store(connection, req, existingRowOpt, delete, versionToSet)

      debug("CRUD: database updated, before commit", List("version" -> versionToSet.toString))

      // Commit before reindexing, as reindexing will read back the form definition, which can
      // cause a deadlock since we're still in the transaction writing the form definition
      connection.commit()

      debug("CRUD: after commit")

      // Update index if needed
      if (! req.dataPart.exists(_.forceDelete)) { // no need as we only delete historical data, not current data, so data must already be deindexed

        val whatToReindex = req.dataPart match {
          case Some(dataPart) =>
            // Data: update index for this document id
            WhatToReindex.DataForDocumentId(dataPart.documentId)
          case None =>
            // Form definition: update index for this form version
            // Re. the asInstanceOf, when updating a form, we must have a specific version specified
            WhatToReindex.DataForForm(req.appForm, versionToSet)
        }

        withDebug("CRUD: reindexing", List("what" -> whatToReindex.toString)) {
          Index.reindex(req.provider, connection, whatToReindex)
        }

        // Create flat view if needed
        if (
          req.flatView                                      &&
          Provider.FlatViewSupportedProviders(req.provider) &&
          req.forForm                                       &&
          ! req.forAttachment                               &&
          ! delete                                          &&
          req.appForm.form != Names.LibraryFormName
        ) withDebug("CRUD: creating flat view") {
          FlatView.createFlatView(req, versionToSet, connection)
        }
      }

      // Inform caller of the form definition version used
      httpResponse.setHeader(OrbeonFormDefinitionVersion, versionToSet.toString)
      // Inform caller of the last modified time of the resource
      //
      // This is compatible with RFC7231, see sections 6.3.2, 6.3.5, and 7.2. But note that here we return the last
      // known modified time associated with the resource, which for a `PUT` is definitely correct, however in the case
      // of a `DELETE` this means the date the resource was *marked* as deleted. We could argue that this should return
      // the last modified time of the resource when it was extant. However, for our callers (purge API), it is
      // necessary to know the last modified time of the resource when it was marked as deleted, so we return that.
      //
      // This is currently `None` for a force `DELETE`, but we could also try to return the information in that case
      // although we don't have a use for it at the moment.
      lastModifiedOpt.foreach { lastModified =>
        httpResponse.setHeader(Headers.LastModified,       DateUtils.formatRfc1123DateTimeGmt(lastModified.toInstant))
        httpResponse.setHeader(Headers.OrbeonLastModified, DateUtils.formatIsoDateTimeUtc(lastModified.getTime))
      }

      // "If the target resource does not have a current representation and the PUT successfully creates one, then the
      // origin server MUST inform the user agent by sending a 201 (Created) response. If the target resource does have
      // a current representation and that representation is successfully modified in accordance with the state of the
      // enclosed representation, then the origin server MUST send either a 200 (OK) or a 204 (No Content) response to
      // indicate successful completion of the request." (https://www.rfc-editor.org/rfc/rfc9110#name-put)
      httpResponse.setStatus(
        if (delete)
          StatusCode.NoContent
        else if (existingRowOpt.isDefined)
          StatusCode.NoContent
        else
          StatusCode.Created
      )
    }
  }
}
