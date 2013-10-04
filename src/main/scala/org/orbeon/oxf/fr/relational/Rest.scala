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
import org.orbeon.oxf.fr.relational.version._
import java.util
import java.sql.Connection

object Rest {

    case class Request(app: String, form: String, filename: Option[String], version: Version, formOrData: Either[Request#Form, Request#Data]) {
        case class Form()
        case class Data(isDraft: Boolean, documentId: String)
        def isDraft = Option(formOrData).collect { case Right(data) ⇒ data.isDraft }.get
        def documentId = Option(formOrData).collect { case Right(data) ⇒ data.documentId }.get
        def forForm = formOrData.isLeft
        def forData = formOrData.isRight
        def forAttachment = filename.isDefined
    }

    def latestNonDeletedFormVersion(app: String, form: String)(implicit connection: Connection): Option[Int] = {
        val maxVersion = (
            connection.prepareStatement(
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
            |!> (_.setString(1, app))
            |!> (_.setString(2, form))
            |>  (_.executeQuery())
            |!> (_.next())
        )
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

    case class FormRowInfo(created: util.Date)
    case class DataRowInfo(created: util.Date, username: String, groupname: String)

    def infoFromExistingRow(implicit connection: Connection, request: Request): Option[Either[FormRowInfo, DataRowInfo]] = {

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
            case Latest ⇒ latest.getOrElse(1)
            case Next ⇒ latest.map(_ + 1).getOrElse(1)
            case Specific(v) ⇒ v
            case ForDocument(_) ⇒ throw new IllegalStateException // Only supported when retrieving a form
        }

        val position = Iterator.from(1)
        val resultSet = (
            connection.prepareStatement(
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
                   |                    ${request.forData.option("and document_id = ?").mkString}
                   |                    ${request.forAttachment.option("and file_name = ?").mkString}
                   |           group by $idColumns
                   |       )
                   |       and deleted = 'N'
                 """.stripMargin)
            |!> (_.setString(position.next(), request.app))
            |!> (_.setString(position.next(), request.form))
            |!> (_.setInt(position.next(), version))
            |!> (_ |> (request.forData.option(_)) |> (_.map(_.setString(position.next(), request.documentId))))
            |!> (_ |> (request.forAttachment.option(_)) |> (_.map(_.setString(position.next(), request.filename.get))))
            |>  (_.executeQuery())
        )

        // Build case case with first row of result
        resultSet.next() match {
            case true ⇒
                val rowInfo = request.formOrData match {
                    case Left(_)  ⇒ Some(Left(FormRowInfo(resultSet.getDate("created"))))
                    case Right(_) ⇒ Some(Right(DataRowInfo(resultSet.getDate("created"),
                        resultSet.getString("username"), resultSet.getString("groupname"))))
                }
                // Query should return at most one row
                rowInfo ensuring (! resultSet.next())
            case false ⇒ None
        }
    }
}
