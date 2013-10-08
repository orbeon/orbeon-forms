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

import org.orbeon.oxf.fr.relational.RelationalUtils._
import org.orbeon.oxf.util.ScalaUtils._
import java.util
import java.sql.Connection

object Rest {

    case class Request(app: String, form: String, filename: Option[String], version: Version, dataPart: Option[Request#DataPart]) {
        case class DataPart(isDraft: Boolean, documentId: String)
        def forForm = ! dataPart.isDefined
        def forData =   dataPart.isDefined
        def forAttachment = filename.isDefined
    }

    def latestNonDeletedFormVersion(app: String, form: String)(implicit connection: Connection): Option[Int] = {
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

    def tableName(implicit request: Request): String =
        Seq(
            Some("orbeon_form"),
            request.forForm       option "_definition",
            request.forData       option "_data",
            request.forAttachment option "_attach"
        ).flatten.mkString

    case class FormRow(created: util.Date)
    case class DataRow(created: util.Date, username: String, groupname: String)

    def existingRow(implicit connection: Connection, request: Request): Option[Either[FormRow, DataRow]] = {

        // List of columns that identify a row
        val idColumns =
            Seq(
                Some("app"), Some("form"),
                request.forForm       option "form_version",
                request.forData       option "document_id",
                request.forAttachment option "file_name"
            ).flatten.mkString(", ")

        def latest = latestNonDeletedFormVersion(request.app, request.form)
        val version = request.version match {
            case Latest         ⇒ latest.getOrElse(1)
            case Next           ⇒ latest.map(_ + 1).getOrElse(1)
            case Specific(v)    ⇒ v
            case ForDocument(_) ⇒ throw new IllegalStateException // Only supported when retrieving a form
        }

        val resultSet = {
            val ps = connection.prepareStatement(
                s"""select created ${request.forData.option(", username , groupname").mkString}
                   |  from $tableName
                   | where (last_modified_time, $idColumns)
                   |       in
                   |       (
                   |             select max(last_modified_time) last_modified_time, $idColumns
                   |               from $tableName
                   |              where app  = ?
                   |                    and form = ?
                   |                    and form_version = ?
                   |                    ${if (request.forData)       "and document_id = ?" else ""}
                   |                    ${if (request.forAttachment) "and file_name = ?"   else ""}
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
            val rowInfo = if (request.forData) Some(Right(DataRow(resultSet.getDate("created"),
                                                                  resultSet.getString("username"),
                                                                  resultSet.getString("groupname"))))
                          else                 Some(Left(FormRow(resultSet.getDate("created"))))
            // Query should return at most one row
            rowInfo ensuring (! resultSet.next())
        } else {
            None
        }
    }
}
