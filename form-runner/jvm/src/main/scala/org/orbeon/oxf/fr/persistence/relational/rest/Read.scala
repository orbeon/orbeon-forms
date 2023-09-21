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

import org.apache.commons.io.input.ReaderInputStream
import org.orbeon.io.CharsetNames
import org.orbeon.io.IOUtils._
import org.orbeon.oxf.externalcontext.{ExternalContext, UserAndGroup}
import org.orbeon.oxf.fr.permission.PermissionsAuthorization.CheckWithDataUser
import org.orbeon.oxf.fr.persistence.relational.Provider.{PostgreSQL, binarySize, partialBinary}
import org.orbeon.oxf.fr.persistence.relational.Version._
import org.orbeon.oxf.fr.persistence.relational._
import org.orbeon.oxf.http._
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.util.{ContentTypes, DateUtils, NetUtils}

import java.io.{ByteArrayInputStream, StringReader}
import java.sql.Timestamp
import scala.annotation.tailrec


trait Read {

  def getOrHead(req: CrudRequest, method: HttpMethod)(implicit httpResponse: ExternalContext.Response): Unit = {

    val hasStage = req.forData && ! req.forAttachment
    val readBody = method == HttpMethod.GET

    // Determine what kind of body we need to read, if any
    val bodyContentOpt = if (readBody) {
      if (req.forAttachment) {
        req.ranges.singleRange match {
          case None =>
            Some(FullAttachment)

          case Some(range) =>
            // Ask for the total attachment size first
            val totalSize = this.fromDatabase(
              req                     = req,
              hasStage                = hasStage,
              readTotalAttachmentSize = true,
              bodyContentOpt          = None
            ).totalAttachmentSize.getOrElse {
              throw HttpStatusCodeException(StatusCode.InternalServerError)
            }

            Some(PartialAttachment(offset = range.start, length = range.length(totalSize)))
        }
      } else {
        Some(Xml)
      }
    } else {
      None
    }

    getOrHeadOnceOrRepeatedly(
      req            = req,
      httpResponse   = httpResponse,
      headersSet     = false,
      hasStage       = hasStage,
      bodyContentOpt = bodyContentOpt
    )
  }

  @tailrec
  private def getOrHeadOnceOrRepeatedly(
    req           : CrudRequest,
    httpResponse  : ExternalContext.Response,
    headersSet    : Boolean,
    hasStage      : Boolean,
    bodyContentOpt: Option[BodyContent]
  ): Unit = {
    val partialBinaryMaxLength = Provider.partialBinaryMaxLength(req.provider)

    // Some databases (Oracle) don't support large partial binary reads, so we need to read the attachment by calling
    // the database repeatedly
    val (actualBodyContentOpt, nextBodyContentOpt) = bodyContentOpt match {
      case Some(PartialAttachment(offset, length)) if length > partialBinaryMaxLength =>
        // Compute current and next partial attachment read
        val actualBodyContent  = PartialAttachment(offset,                          partialBinaryMaxLength)
        val nextBodyContentOpt = PartialAttachment(offset + partialBinaryMaxLength, length - partialBinaryMaxLength)

        (Some(actualBodyContent), Some(nextBodyContentOpt))

      case _ =>
        (bodyContentOpt, None)
    }

    val fromDatabase = this.fromDatabase(
      req                     = req,
      hasStage                = hasStage,
      readTotalAttachmentSize = actualBodyContentOpt match {
        case Some(PartialAttachment(_, _)) => true
        case _                             => false
      },
      bodyContentOpt          = actualBodyContentOpt
    )

    if (! headersSet) {
      // Send headers
      httpResponse.setHeader(OrbeonFormDefinitionVersion, fromDatabase.formVersion.toString)

      fromDatabase.dataUserOpt.foreach { dataUser =>
        dataUser.userAndGroup.foreach { userAndGroup =>
          httpResponse.setHeader(Headers.OrbeonUsername, userAndGroup.username)
          userAndGroup.groupname.foreach(httpResponse.setHeader(Headers.OrbeonGroup, _))
        }
        // TODO: set `Headers.OrbeonOrganization`, but in what format?
      }

      fromDatabase.stageOpt.foreach(httpResponse.setHeader(StageHeader.HeaderName, _))
      httpResponse.setHeader(Headers.Created,            DateUtils.formatRfc1123DateTimeGmt(fromDatabase.createdDateTime.toInstant))
      httpResponse.setHeader(Headers.LastModified,       DateUtils.formatRfc1123DateTimeGmt(fromDatabase.lastModifiedDateTime.toInstant))
      // Also provide this with in ISO format with millisecond precision for compatibility with Search and History APIs
      httpResponse.setHeader(Headers.OrbeonCreated,      DateUtils.formatIsoDateTimeUtc(fromDatabase.createdDateTime.getTime))
      httpResponse.setHeader(Headers.OrbeonLastModified, DateUtils.formatIsoDateTimeUtc(fromDatabase.lastModifiedDateTime.getTime))
      if (!req.forAttachment)
        httpResponse.setHeader(Headers.ContentType,      ContentTypes.XmlContentType)

      for {
        range                <- req.ranges.singleRange
        totalFileContentSize <- fromDatabase.totalAttachmentSize
        // Check that the file is stored into the database (and not e.g. in the filesystem)
        if totalFileContentSize > 0
      } locally {
        // Set HTTP range headers and status
        httpResponse.addHeaders(range.responseHeaders(totalFileContentSize))
        httpResponse.setStatus(StatusCode.PartialContent)
      }
    }

    // Maybe send body
    fromDatabase.bodyOpt.foreach(httpResponse.getOutputStream.write)

    nextBodyContentOpt match {
      case None =>
        ()

      case Some(nextBodyContent) =>
        getOrHeadOnceOrRepeatedly(
          req            = req,
          httpResponse   = httpResponse,
          hasStage       = hasStage,
          // Only set headers once
          headersSet     = true,
          bodyContentOpt = Some(nextBodyContent)
        )
    }
  }

  // Holds the data we will read from the database
  private case class FromDatabase(
   dataUserOpt         : Option[CheckWithDataUser], // set to `Some(_)` if `forData == true`
   formVersion         : Int,
   stageOpt            : Option[String],
   createdDateTime     : Timestamp,
   lastModifiedDateTime: Timestamp,
   bodyOpt             : Option[Array[Byte]],
   totalAttachmentSize : Option[Int]
 )

  private sealed trait BodyContent
  private case object FullAttachment                                extends BodyContent
  private case class  PartialAttachment(offset: Long, length: Long) extends BodyContent
  private case object Xml                                           extends BodyContent

  private def fromDatabase(
    req                    : CrudRequest,
    hasStage               : Boolean,
    readTotalAttachmentSize: Boolean,
    bodyContentOpt         : Option[BodyContent]
  ): FromDatabase = RelationalUtils.withConnection { connection =>
    val sql = {
      val table  = SqlSupport.tableName(req)
      val idCols = SqlSupport.idColumns(req)

      val body = bodyContentOpt match {
        case None =>
          ""

        case Some(FullAttachment) =>
          ", t.file_content"

        case Some(PartialAttachment(offset, length)) =>
          val partialFileContent = partialBinary(
            req.provider,
            columnName = "t.file_content",
            alias      = "file_content",
            offset     = offset,
            length     = Some(length)
          )

          s", $partialFileContent"

        case Some(Xml) =>
          s", ${Provider.xmlColSelect(req.provider, "t")}"
      }

      val totalAttachmentSize =
        if (readTotalAttachmentSize) {
          val totalFileContentSize = binarySize(
            req.provider,
            columnName = "t.file_content",
            alias      = "total_file_content_size"
          )

          s", $totalFileContentSize"
        } else {
          ""
        }

      req.lastModifiedOpt match {
        case Some(_) =>
          s"""|SELECT  t.last_modified_time, t.created
              |        $body
              |        $totalAttachmentSize
              |        ${if (req.forData)       ", t.username, t.groupname, t.organization_id" else ""}
              |        ${if (hasStage)          ", t.stage"                                    else ""}
              |        , t.form_version, t.deleted
              |FROM    $table t
              |WHERE   t.app  = ?
              |        and t.form = ?
              |        and t.last_modified_time = ?
              |        ${if (req.forForm)       "and t.form_version = ?"                else ""}
              |        ${if (req.forData)       "and t.document_id = ? and t.draft = ?" else ""}
              |        ${if (req.forAttachment) "and t.file_name = ?"                   else ""}
              |""".stripMargin
        case None =>
          s"""|SELECT  t.last_modified_time, t.created
              |        $body
              |        $totalAttachmentSize
              |        ${if (req.forData)       ", t.username, t.groupname, t.organization_id" else ""}
              |        ${if (hasStage)          ", t.stage"                                    else ""}
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
              |WHERE   ${SqlSupport.joinColumns("last_modified_time" +: idCols, "t", "m")}
              |""".stripMargin
      }
    }

    useAndClose(connection.prepareStatement(sql)) { ps =>
      val position = Iterator.from(1)
      ps.setString(position.next(), req.appForm.app)
      ps.setString(position.next(), req.appForm.form)
      req.lastModifiedOpt foreach { lastModified =>
        ps.setTimestamp(position.next(), Timestamp.from(lastModified))
      }
      if (req.forForm)
        ps.setInt(position.next(), req.version.getOrElse(throw HttpStatusCodeException(StatusCode.BadRequest)))
      if (req.forData) {
        ps.setString(position.next(), req.dataPart.get.documentId)
        ps.setString(position.next(), if (req.dataPart.get.isDraft) "Y" else "N")
      }
      if (req.forAttachment) ps.setString(position.next(), req.filename.get)

      useAndClose(ps.executeQuery()) { resultSet =>
        if (resultSet.next()) {

          // We can't always return a 403 instead of a 410/404, so we decided it's OK to divulge to unauthorized
          // users that the data exists or existed
          val deleted = resultSet.getString("deleted") == "Y"
          if (deleted)
            throw HttpStatusCodeException(StatusCode.Gone)

          // Check version if specified
          val dbFormVersion = resultSet.getInt("form_version")

          // Info about the owner, which will be used to check the user can access the data
          val dataUser = req.forData.option(CheckWithDataUser(
            userAndGroup = UserAndGroup.fromStrings(resultSet.getString("username"), resultSet.getString("groupname")),
            organization = OrganizationSupport.readFromResultSet(connection, resultSet).map(_._2)
          ))

          val bodyOpt = bodyContentOpt.flatMap { bodyContent =>
            val bodyInputStream = bodyContent match {
              case FullAttachment | PartialAttachment(_, _) =>
                req.provider match {
                  case PostgreSQL => Option(resultSet.getBytes("file_content")).map(new ByteArrayInputStream(_))
                  case _          => Option(resultSet.getBlob("file_content")).map(_.getBinaryStream)
                }

              case Xml =>
                val reader = req.provider match {
                  case PostgreSQL => new StringReader(resultSet.getString("xml"))
                  case _          => resultSet.getClob("xml").getCharacterStream
                }
                Some(new ReaderInputStream(reader, CharsetNames.Utf8))
            }

            bodyInputStream.map(useAndClose(_)(NetUtils.inputStreamToByteArray))
          }

          FromDatabase(
            dataUserOpt          = dataUser,
            formVersion          = dbFormVersion,
            stageOpt             = if (hasStage) Option(resultSet.getString("stage")) else None,
            createdDateTime      = resultSet.getTimestamp("created"),
            lastModifiedDateTime = resultSet.getTimestamp("last_modified_time"),
            bodyOpt              = bodyOpt,
            totalAttachmentSize  = readTotalAttachmentSize.option(resultSet.getInt("total_file_content_size"))
          )

        } else {
          throw HttpStatusCodeException(StatusCode.NotFound)
        }
      }
    }
  }
}
