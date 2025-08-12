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

import cats.implicits.catsSyntaxOptionId
import org.orbeon.io.IOUtils.*
import org.orbeon.io.{IOUtils, StringBuilderWriter}
import org.orbeon.oxf.externalcontext.ExternalContext
import org.orbeon.oxf.fr.Version.*
import org.orbeon.oxf.fr.XMLNames.{XF, XH}
import org.orbeon.oxf.fr.persistence.PersistenceMetadataSupport
import org.orbeon.oxf.fr.persistence.relational.index.Index
import org.orbeon.oxf.fr.persistence.relational.rest.SqlSupport.*
import org.orbeon.oxf.fr.persistence.relational.search.SearchLogic
import org.orbeon.oxf.fr.persistence.relational.search.adt.{Drafts, SearchRequest}
import org.orbeon.oxf.fr.persistence.relational.{Provider, RelationalUtils, WhatToReindex}
import org.orbeon.oxf.fr.{FormDefinitionVersion, FormRunner, Names}
import org.orbeon.oxf.http.{EmptyInputStream, Headers, HttpStatusCodeException, StatusCode}
import org.orbeon.oxf.pipeline.api.TransformerXMLReceiver
import org.orbeon.oxf.util.CoreUtils.*
import org.orbeon.oxf.util.Logging.*
import org.orbeon.oxf.util.{DateUtils, IndentedLogger, Whitespace, XPath}
import org.orbeon.oxf.xml.*
import org.orbeon.saxon.event.SaxonOutputKeys
import org.orbeon.saxon.om.DocumentInfo
import org.orbeon.scaxon.SAXEvents.{Atts, StartElement}
import org.xml.sax.InputSource

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, InputStream, Writer}
import java.sql.{Array as _, *}
import java.time.Instant
import javax.xml.transform.OutputKeys
import javax.xml.transform.sax.{SAXResult, SAXSource}
import javax.xml.transform.stream.StreamResult


object RequestReader {

  private object IdAtt {
    private val IdQName = JXQName("id")
    def unapply(atts: Atts): Option[String] = atts.atts collectFirst { case (IdQName, value) => value }
  }

  sealed trait                                         Body
  object Body {
    case class Cached   (bytes  : Array[Byte]) extends Body
    case class Streamed (stream : InputStream) extends Body
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

  private def requestInputStream(bodyOpt: Option[Body]): Option[InputStream] =
    bodyOpt.map {
      case Body.Cached(bytes) => new ByteArrayInputStream(bytes)
      case Body.Streamed(is)  => is
    }

  def bytes(bodyOpt: Option[Body]): Option[Array[Byte]] =
    bodyOpt.map {
      case Body.Cached(bytes) => bytes
      case Body.Streamed(is)  =>
        val os = new ByteArrayOutputStream
        IOUtils.copyStreamAndClose(is, os)
        os.toByteArray
    }

  // Used by FlatView
  def xmlDocument(bodyOpt: Option[Body]): Option[DocumentInfo] =
    requestInputStream(bodyOpt).map { is =>
      TransformerUtils.readTinyTree(XPath.GlobalConfiguration, is, "", false, false)
    }

  def dataAndMetadataAsString(bodyOpt: Option[Body], provider: Provider, metadata: Boolean): (Option[String], Option[String]) =
    requestInputStream(bodyOpt).map { is =>
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

    def newTransformer =
      TransformerUtils.getXMLIdentityTransformer |!>
        (_.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes"))

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
}

trait CreateUpdateDelete {

  // NOTE: Gets the first organization if there are multiple organization roots
  private def currentUserOrganization(connection: java.sql.Connection, req: CrudRequest): Option[OrganizationId] =
    req.credentials
      .flatMap(_.defaultOrganization)
      .map(OrganizationSupport.createIfNecessary(connection, req.provider, _))

  case class StoreResult(idOpt: Option[Int], lastModifiedOpt: Option[Instant])

  private def store(
    connection     : java.sql.Connection,
    req            : CrudRequest,
    reqBodyOpt     : Option[RequestReader.Body],
    delete         : Boolean,
    versionToSet   : Int,
    forSingleton   : Boolean
  )(implicit
    externalContext: ExternalContext
  ): StoreResult = {

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

    def doDeleteDraftDataAndAttachments(dataPart: DataPart): Unit = {
      // See https://github.com/orbeon/orbeon-forms/issues/2980: when saving the data for a draft, we don't want to
      // remove draft attachments from `orbeon_form_data_attach`, otherwise the attachments which were just saved are
      // immediately removed! So we remove draft attachments from `from orbeon_form_data_attach` only when we are
      // saving data which is not for a draft. Note that there is no garbage collection for attachments.

      // See https://github.com/orbeon/orbeon-forms/issues/7049: we also want to delete draft attachments in the case
      // where we're explicitly deleting a draft. Before the fix for #7049, we had:
      //
      //  val deleteDraftAttachments = ! dataPart.isDraft
      //
      // Now, with both conditions, we have:
      //
      //  val deleteDraftAttachments = ! dataPart.isDraft || (dataPart.isDraft && delete)
      //                             = ! dataPart.isDraft || delete

      val deleteDraftAttachments = ! dataPart.isDraft || delete

      val tablesToDeleteDraftsFrom =
        "orbeon_i_control_text"                                 ::
        (deleteDraftAttachments list "orbeon_form_data_attach") :::
        "orbeon_i_current"                                      ::
        "orbeon_form_data"                                      ::
        Nil

      tablesToDeleteDraftsFrom.foreach { table =>
        doDelete(table, dataPart.documentId, isDraft = true, lastModifiedOpt = None, filenameOpt = None)
      }

      // TODO: delete draft attachments also in filesystem/S3

      // We used to do a `commit()` here for #4515
      // But we must not commit for singletons at least, as that breaks what should be a single transaction
      if (! forSingleton)
        connection.commit()
    }

    def doForceDelete(dataPart: DataPart): Unit = {

      assert(delete)

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
    }

    def doInsert(): StoreResult = {

      val currentTimestamp = new Timestamp(System.currentTimeMillis())

      val includedCols = insertCols(req, reqBodyOpt, delete, versionToSet, currentTimestamp, currentUserOrganization(connection, req))
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

      val idOpt = Provider.executeUpdateAndReturnGeneratedId(
        connection = connection,
        provider   = req.provider,
        table      = table,
        sql        = insertSql
      ) { ps =>

        // Set parameters in prepared statement for the dynamic values
        includedCols
          .map(_.value)
          .collect({ case DynamicColValue(_, paramSetter) => paramSetter })
          .zipWithIndex
          .foreach{ case (paramSetter, index) => paramSetter(ps, index + 1)}
      }

      StoreResult(idOpt, currentTimestamp.toInstant.some)
    }

    req.dataPart.collect {
      case dataPart if ! req.forAttachment =>
        doDeleteDraftDataAndAttachments(dataPart)
    }

    req.dataPart match {
      case Some(dataPart) if dataPart.forceDelete =>
        doForceDelete(dataPart)
      case _ =>
    }

    val deletingDataDraft = delete && req.dataPart.exists(_.isDraft)
    val forceDelete       = delete && req.dataPart.exists(_.forceDelete)

    if (! deletingDataDraft && ! forceDelete)
      doInsert()
    else
      StoreResult(idOpt = None, lastModifiedOpt = None)
  }

  def change(
    req            : CrudRequest,
    delete         : Boolean
  )(implicit
    externalContext: ExternalContext,
    indentedLogger : IndentedLogger
  ): Unit = {

    debug("CRUD: handling change request", List("delete" -> delete.toString, "request" -> req.toString))

    val versionToSet =
      req.version.getOrElse(throw HttpStatusCodeException(StatusCode.BadRequest))

    if (req.forForm)
      PersistenceMetadataSupport.maybeInvalidateCachesFor(req.appForm, versionToSet)

    val createFlatView: Boolean =
      req.flatView                                      &&
      Provider.FlatViewSupportedProviders(req.provider) &&
      req.forForm                                       &&
      ! req.forAttachment                               &&
      ! delete                                          &&
      req.appForm.form != Names.LibraryFormName

    // Cache the request content if we need it for multiple reads
    val reqBodyOpt: Option[RequestReader.Body] = {
      val inputStreamOpt = Some(externalContext.getRequest.getInputStream).filter(_ != EmptyInputStream)
      inputStreamOpt.map { is =>
        if (createFlatView) {
          val os = new ByteArrayOutputStream
          IOUtils.copyStreamAndClose(is, os)
          RequestReader.Body.Cached(os.toByteArray)
        } else {
          RequestReader.Body.Streamed(is)
        }
      }
    }

    def reindex(connectionOpt: Option[java.sql.Connection]): Unit =
      // Update index if needed
      if (
        ! req.forAttachment &&               // https://github.com/orbeon/orbeon-forms/issues/6913
        ! req.dataPart.exists(_.forceDelete) // no need to reindex as we only `DELETE` historical data, which is not indexed
      ) {
        val whatToReindex = req.dataPart match {
          case Some(dataPart) =>
            // Data: update index for this document id
            WhatToReindex.DataForDocumentId(dataPart.documentId, appFormVersion = (req.appForm, versionToSet))
          case None =>
            // Form definition: update index for this form version
            WhatToReindex.DataForForm((req.appForm, versionToSet))
        }

        // If we are deleting a form definition, we should clear the index, but we should not reindex the data after that.
        // https://github.com/orbeon/orbeon-forms/issues/6915
        val clearOnly =
          delete && req.forForm // we know it's not for an attachment as that's tested above

        withDebug("CRUD: reindexing", List("what" -> whatToReindex.toString)) {
          Index.reindex(req.provider, whatToReindex, clearOnly = clearOnly, connectionOpt)
        }
      }

    // Is this for a singleton `PUT`?
    val allowCreateOnlyIfSearchEmpty =
      req.forData             &&
      ! delete                &&
      req.existingRow.isEmpty &&
      req.singleton.getOrElse(false)

    // Update database
    val storeResult =
      if (allowCreateOnlyIfSearchEmpty) {
        // https://github.com/orbeon/orbeon-forms/issues/7164
        try {
          RelationalUtils.withConnection { connection =>

            connection.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE)

            val (_, count) = SearchLogic.doSearch(
              request = SearchRequest(
                provider            = req.provider,
                appForm             = req.appForm,
                version             = req.version.map(FormDefinitionVersion.Specific.apply).getOrElse(FormDefinitionVersion.Latest),
                credentials         = req.credentials,
                isInternalAdminUser = false,
                pageSize            = 1,
                pageNumber          = 1,
                queries             = Nil,
                drafts              = Drafts.IncludeDrafts,
                freeTextSearch      = None,
                anyOfOperations     = None
              ),
              connectionOpt = Some(connection)
            )
            if (count > 0)
              throw HttpStatusCodeException(StatusCode.Conflict)

            val storeResult = store(connection, req, reqBodyOpt, delete, versionToSet, forSingleton = true)
            reindex(Some(connection))
            storeResult
          }
        } catch {
          case _: java.sql.SQLException if allowCreateOnlyIfSearchEmpty =>
            // See https://github.com/orbeon/orbeon-forms/issues/7164
            // If we get an error with `allowCreateOnlyIfSearchEmpty == true` we assume that it is a conflict.
            // Can we do better?
            throw HttpStatusCodeException(StatusCode.Conflict)
        }
      } else {
        // This is the normal case, which doesn't require any special locking
        val storeResult =
          RelationalUtils.withConnection { connection =>
            store(connection, req, reqBodyOpt, delete, versionToSet, forSingleton = false)
          }
        reindex(None)
        storeResult
    }

    if (createFlatView)
      doCreateFlatView(req, reqBodyOpt, versionToSet)

    val httpResponse = externalContext.getResponse

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
    // This is currently `None` for a force `DELETE`, but we could also try to return the information in that case,
    // although we don't have a use for it at the moment.
    storeResult.lastModifiedOpt.foreach { lastModified =>
      httpResponse.setHeader(Headers.LastModified,       DateUtils.formatRfc1123DateTimeGmt(lastModified))
      httpResponse.setHeader(Headers.OrbeonLastModified, DateUtils.formatIsoDateTimeUtc(lastModified))
    }

    if (req.forDataNotAttachment) {
      for {
        id           <- storeResult.idOpt
        lastModified <- storeResult.lastModifiedOpt
      } {
        httpResponse.setHeader(
          Headers.ETag,
          ETag.eTag(tableName = SqlSupport.tableName(req), id = id, lastModified = lastModified)
        )
      }
    }

    // "If the target resource does not have a current representation and the PUT successfully creates one, then the
    // origin server MUST inform the user agent by sending a 201 (Created) response. If the target resource does have
    // a current representation and that representation is successfully modified in accordance with the state of the
    // enclosed representation, then the origin server MUST send either a 200 (OK) or a 204 (No Content) response to
    // indicate successful completion of the request." (https://www.rfc-editor.org/rfc/rfc9110#name-put)
    httpResponse.setStatus(
      if (delete)
        StatusCode.NoContent
      else if (req.existingRow.isDefined)
        StatusCode.NoContent
      else
        StatusCode.Created
    )
  }

  private def doCreateFlatView(
    req            : CrudRequest,
    reqBodyOpt     : Option[RequestReader.Body],
    versionToSet   : Int
  )(implicit
    externalContext: ExternalContext,
    indentedLogger : IndentedLogger
  ): Unit =
    withDebug("CRUD: creating flat view") {
      RelationalUtils.withConnection { connection =>

        val prefixesInMainViewColumnNames = FormRunner.providerPropertyAsBoolean(
          req.provider.entryName,
          "flat-view.prefixes-in-main-view-column-names",
          default = true
        )

        val maxIdentifierLength = FormRunner.providerPropertyAsInteger(
          req.provider.entryName,
          "flat-view.max-identifier-length",
          default = FlatView.CompatibilityMaxIdentifierLength
        )

        FlatView.createFlatViews(req, reqBodyOpt, versionToSet, connection, prefixesInMainViewColumnNames, maxIdentifierLength)
      }
    }
}
