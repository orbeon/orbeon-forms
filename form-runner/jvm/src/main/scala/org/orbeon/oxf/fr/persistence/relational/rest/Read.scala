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
import org.orbeon.oxf.externalcontext.UserAndGroup
import org.orbeon.oxf.fr.permission.Operation.Read
import org.orbeon.oxf.fr.permission.PermissionsAuthorization.CheckWithDataUser
import org.orbeon.oxf.fr.permission.{Operations, PermissionsAuthorization, PermissionsXML}
import org.orbeon.oxf.fr.persistence.relational.Provider.PostgreSQL
import org.orbeon.oxf.fr.persistence.relational.RelationalCommon._
import org.orbeon.oxf.fr.persistence.relational.Version._
import org.orbeon.oxf.fr.persistence.relational._
import org.orbeon.oxf.fr.{FormDefinitionVersion, FormRunner, FormRunnerPersistence}
import org.orbeon.oxf.http.{Headers, HttpMethod, HttpStatusCodeException, StatusCode}
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.util.{ContentTypes, DateUtils, NetUtils}

import java.io.{ByteArrayInputStream, StringReader}
import java.sql.Timestamp


trait Read extends RequestResponse with Common with FormRunnerPersistence {

  def getOrHead(req: Request, method: HttpMethod): Unit = {

    RelationalUtils.withConnection { connection =>

      val badVersion =
        // For data, we don't need a version number, so accept we either accept no version being specified,
        // or if a form version if specified we'll later check that it matches the version number in the database.
        (req.forData && (req.version match {
          case Unspecified => false
          case Specific(_) => false
          case           _ => true
        }))  ||
        // For form definition, everything is valid except Next
        (req.forForm && req.version == Next)
      if (badVersion) throw HttpStatusCodeException(StatusCode.BadRequest)
      val hasStage = req.forData && ! req.forAttachment
      val readBody = method == HttpMethod.GET

      val sql = {
        val table  = tableName(req)
        val idCols = idColumns(req)
        val xmlCol = Provider.xmlColSelect(req.provider, "t")
        val body   =
          if (readBody)
            if (req.forAttachment) ", t.file_content"
            else                   s", $xmlCol"
          else                     ""
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
            |WHERE   ${joinColumns("last_modified_time" +: idCols, "t", "m")}
            |""".stripMargin
      }
      useAndClose(connection.prepareStatement(sql)) { ps =>
        val position = Iterator.from(1)
        ps.setString(position.next(), req.appForm.app)
        ps.setString(position.next(), req.appForm.form)
        if (req.forForm) ps.setInt(position.next(), requestedFormVersion(req))
        if (req.forData) {
          ps.setString(position.next(), req.dataPart.get.documentId)
          ps.setString(position.next(), if (req.dataPart.get.isDraft) "Y" else "N")
        }
        if (req.forAttachment) ps.setString(position.next(), req.filename.get)

        // Holds the data we will read from the database
        case class FromDatabase(
          dataUserOpt          : Option[CheckWithDataUser],
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
            req.version match {
              case Specific(reqFormVersion) =>
                if (dbFormVersion != reqFormVersion)
                  throw HttpStatusCodeException(StatusCode.BadRequest)
              case _ => // NOP; we're all good
            }

            // Info about the owner, which will be used to check the user can access the data
            val dataUser = req.forData.option(CheckWithDataUser(
              userAndGroup = UserAndGroup.fromStrings(resultSet.getString("username"), resultSet.getString("groupname")),
              organization = OrganizationSupport.readFromResultSet(connection, resultSet).map(_._2)
            ))

            // Maybe read body
            val bodyOpt = readBody.option {
              val bodyInputStream = {
                if (req.forAttachment) {
                  req.provider match {
                    case PostgreSQL => new ByteArrayInputStream(resultSet.getBytes("file_content"))
                    case _          => resultSet.getBlob("file_content").getBinaryStream
                  }
                } else {
                  val reader = req.provider match {
                    case PostgreSQL => new StringReader(resultSet.getString("xml"))
                    case _          => resultSet.getClob("xml").getCharacterStream
                  }
                  new ReaderInputStream(reader, CharsetNames.Utf8)
                }
              }

              useAndClose(bodyInputStream)(NetUtils.inputStreamToByteArray)
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

        // Check user can read and set Orbeon-Operations header
        fromDatabase.dataUserOpt.foreach { dataUser =>

          // Read form permissions after we're done with the connection to read the data,
          // so we don't use two simultaneous connections
          val formPermissionsForDataRequestOptOpt = req.forData.option(RelationalUtils.readFormPermissions(
            req.appForm, FormDefinitionVersion.Specific(fromDatabase.formVersion))
          )

          formPermissionsForDataRequestOptOpt foreach { formPermissionsElemOpt =>
            val authorizedOperations = PermissionsAuthorization.authorizedOperations(
              FormRunner.permissionsFromElemOrProperties(
                formPermissionsElemOpt,
                req.appForm
              ),
              PermissionsAuthorization.findCurrentCredentialsFromSession,
              dataUser
            )
            if (!Operations.allows(authorizedOperations, Read))
              throw HttpStatusCodeException(StatusCode.Forbidden)
            httpResponse.setHeader(
              FormRunnerPersistence.OrbeonOperations,
              Operations.serialize(authorizedOperations, normalized = true).mkString(" ")
            )
          }
        }

        // Send headers
        httpResponse.setHeader(OrbeonFormDefinitionVersion, fromDatabase.formVersion.toString)
        fromDatabase.stageOpt.foreach(httpResponse.setHeader(StageHeader.HeaderName, _))
        httpResponse.setHeader(Headers.Created,      DateUtils.formatRfc1123DateTimeGmt(fromDatabase.createdDateTime.toInstant))
        httpResponse.setHeader(Headers.LastModified, DateUtils.formatRfc1123DateTimeGmt(fromDatabase.lastModifiedDateTime.toInstant))
        if (!req.forAttachment)
          httpResponse.setHeader(Headers.ContentType, ContentTypes.XmlContentType)

        // Maybe send body
        fromDatabase.bodyOpt.foreach(httpResponse.getOutputStream.write)
      }
    }
  }
}
