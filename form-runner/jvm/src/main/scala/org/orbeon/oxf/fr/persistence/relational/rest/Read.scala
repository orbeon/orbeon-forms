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

import java.io.{ByteArrayInputStream, OutputStreamWriter, StringReader}

import org.joda.time.DateTime
import org.orbeon.io.CharsetNames
import org.orbeon.io.IOUtils._
import org.orbeon.oxf.fr.FormRunnerPersistence
import org.orbeon.oxf.fr.permission.Operation.Read
import org.orbeon.oxf.fr.permission.PermissionsAuthorization.CheckWithDataUser
import org.orbeon.oxf.fr.permission.{Operations, PermissionsAuthorization, PermissionsXML}
import org.orbeon.oxf.fr.persistence.relational.Provider.PostgreSQL
import org.orbeon.oxf.fr.persistence.relational.RelationalCommon._
import org.orbeon.oxf.fr.persistence.relational.Version._
import org.orbeon.oxf.fr.persistence.relational._
import org.orbeon.oxf.http.{Headers, HttpStatusCodeException, StatusCode}
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.util.{ContentTypes, DateUtils, NetUtils}

trait Read extends RequestResponse with Common with FormRunnerPersistence {

  def get(req: Request): Unit = {

    // Read before establishing a connection, so we don't use two simultaneous connections
    val formMetadataForDataRequestOpt = req.forData.option(RelationalUtils.readFormPermissions(req.app, req.form))

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

      val sql = {
        val table  = tableName(req)
        val idCols = idColumns(req)
        val xmlCol = Provider.xmlCol(req.provider, "t")
        s"""|SELECT  t.last_modified_time, t.created
            |        ${if (req.forAttachment) ", t.file_content"                             else s", $xmlCol"}
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
        ps.setString(position.next(), req.app)
        ps.setString(position.next(), req.form)
        if (req.forForm) ps.setInt(position.next(), requestedFormVersion(connection, req))
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
            req.version match {
              case Specific(reqFormVersion) =>
                if (dbFormVersion != reqFormVersion)
                  throw HttpStatusCodeException(StatusCode.BadRequest)
              case _ => // NOP; we're all good
            }

            // Check user can read and set Orbeon-Operations header
            formMetadataForDataRequestOpt foreach { formMetadata =>
              val dataUser = CheckWithDataUser(
                username     = Option(resultSet.getString("username")),
                groupname    = Option(resultSet.getString("groupname")),
                organization = OrganizationSupport.readFromResultSet(connection, resultSet).map(_._2)
              )
              val authorizedOperations = PermissionsAuthorization.authorizedOperations(
                PermissionsXML.parse(formMetadata.orNull),
                PermissionsAuthorization.currentUserFromSession,
                dataUser
              )
              if (! Operations.allows(authorizedOperations, Read))
                throw HttpStatusCodeException(StatusCode.Forbidden)
              httpResponse.setHeader("Orbeon-Operations", Operations.serialize(authorizedOperations).mkString(" "))
            }

            val createdDateTime      = resultSet.getTimestamp("created")
            val lastModifiedDateTime = resultSet.getTimestamp("last_modified_time")

            // Set form version and stage headers
            httpResponse.setHeader(OrbeonFormDefinitionVersion, dbFormVersion.toString)
            if (hasStage) {
              val stage = resultSet.getString("stage")
              Option(stage).foreach(httpResponse.setHeader(StageHeader.HeaderName, _))
            }

            // Write content (XML / file)
            if (req.forAttachment) {
              val stream = req.provider match {
                case PostgreSQL => new ByteArrayInputStream(resultSet.getBytes("file_content"))
                case _          => resultSet.getBlob("file_content").getBinaryStream
              }
              NetUtils.copyStream(stream, httpResponse.getOutputStream)
            } else {
              val stream = req.provider match {
                case PostgreSQL => new StringReader(resultSet.getString("xml"))
                case _          => resultSet.getClob("xml").getCharacterStream
              }
              httpResponse.setHeader(Headers.ContentType, ContentTypes.XmlContentType)

              // Date headers
              httpResponse.setHeader(Headers.Created,      DateUtils.RFC1123Date.print(new DateTime(createdDateTime)))
              httpResponse.setHeader(Headers.LastModified, DateUtils.RFC1123Date.print(new DateTime(lastModifiedDateTime)))

              val writer = new OutputStreamWriter(httpResponse.getOutputStream, CharsetNames.Utf8)
              NetUtils.copyStream(stream, writer)
              writer.close()
            }

          } else {
            throw HttpStatusCodeException(StatusCode.NotFound)
          }
        }
      }
    }
  }
}
