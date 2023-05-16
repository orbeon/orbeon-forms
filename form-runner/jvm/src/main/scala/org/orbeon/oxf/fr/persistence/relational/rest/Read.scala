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
import org.orbeon.oxf.fr.FormRunnerPersistence
import org.orbeon.oxf.fr.permission.PermissionsAuthorization.CheckWithDataUser
import org.orbeon.oxf.fr.persistence.relational.Provider.PostgreSQL
import org.orbeon.oxf.fr.persistence.relational.Version._
import org.orbeon.oxf.fr.persistence.relational._
import org.orbeon.oxf.http.{Headers, HttpMethod, HttpStatusCodeException, StatusCode}
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.util.{ContentTypes, DateUtils, NetUtils}

import java.io.{ByteArrayInputStream, StringReader}
import java.sql.Timestamp


trait Read extends FormRunnerPersistence {

  def getOrHead(req: CrudRequest, method: HttpMethod)(implicit httpResponse: ExternalContext.Response): Unit = {

    RelationalUtils.withConnection { connection =>

      val hasStage = req.forData && ! req.forAttachment
      val readBody = method == HttpMethod.GET

      val sql = {
        val table  = SqlSupport.tableName(req)
        val idCols = SqlSupport.idColumns(req)
        val xmlCol = Provider.xmlColSelect(req.provider, "t")
        val body   =
          if (readBody)
            if (req.forAttachment) ", t.file_content"
            else                   s", $xmlCol"
          else                     ""

        req.lastModifiedOpt match {
          case Some(_) =>
            s"""|SELECT  t.last_modified_time, t.created
                |        $body
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

        // Holds the data we will read from the database
        case class FromDatabase(
          dataUserOpt          : Option[CheckWithDataUser], // set to `Some(_)` if `forData == true`
          formVersion          : Int,
          stageOpt             : Option[String],
          createdDateTime      : Timestamp,
          lastModifiedDateTime : Timestamp,
          bodyOpt              : Option[Array[Byte]]
        )

        val fromDatabase = useAndClose(ps.executeQuery()) { resultSet =>
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

            // Maybe read body
            val bodyOpt = readBody.flatOption {
              val bodyInputStream = {
                if (req.forAttachment) {
                  req.provider match {
                    case PostgreSQL => Option(resultSet.getBytes("file_content")).map(new ByteArrayInputStream(_))
                    case _          => Option(resultSet.getBlob("file_content")).map(_.getBinaryStream)
                  }
                } else {
                  val reader = req.provider match {
                    case PostgreSQL => new StringReader(resultSet.getString("xml"))
                    case _          => resultSet.getClob("xml").getCharacterStream
                  }
                  Some(new ReaderInputStream(reader, CharsetNames.Utf8))
                }
              }

              bodyInputStream.map(useAndClose(_)(NetUtils.inputStreamToByteArray))
            }

            FromDatabase(
              dataUserOpt          = dataUser,
              formVersion          = dbFormVersion,
              stageOpt             = if (hasStage) Option(resultSet.getString("stage")) else None,
              createdDateTime      = resultSet.getTimestamp("created"),
              lastModifiedDateTime = resultSet.getTimestamp("last_modified_time"),
              bodyOpt              = bodyOpt
            )

          } else {
            throw HttpStatusCodeException(StatusCode.NotFound)
          }
        }

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
        httpResponse.setHeader(Headers.Created,      DateUtils.formatRfc1123DateTimeGmt(fromDatabase.createdDateTime.toInstant))
        httpResponse.setHeader(Headers.LastModified, DateUtils.formatRfc1123DateTimeGmt(fromDatabase.lastModifiedDateTime.toInstant))
        if (! req.forAttachment)
          httpResponse.setHeader(Headers.ContentType, ContentTypes.XmlContentType)

        // Maybe send body
        fromDatabase.bodyOpt.foreach(httpResponse.getOutputStream.write)
      }
    }
  }
}
