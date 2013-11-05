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

import java.sql.Connection
import org.orbeon.oxf.util.ScalaUtils._
import org.orbeon.oxf.fr.relational.{ForDocument, Specific, Next, Latest}
import org.orbeon.oxf.util.{LoggerFactory, IndentedLogger}

trait Common extends RequestResponse {

    implicit val Logger = new IndentedLogger(LoggerFactory.createLogger(classOf[CRUD]), "")

    def latestNonDeletedFormVersion(connection: Connection, app: String, form: String): Option[Int] = {
        val maxVersion = {
            val ps = connection.prepareStatement(
                """select max(form_version)
                  |  from orbeon_form_definition
                  | where (last_modified_time, app, form, form_version) in
                  |       (
                  |             select max(last_modified_time) last_modified_time, app, form, form_version
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

    /**
     * For every request, there is a corresponding specific form version number. In the request, that specific version
     * can be specified, but the caller can also say that it wants the next version, the latest version, or the version
     * of the form used to create a specific document. This function finds the specific form version corresponding to
     * the request.
     */
    def requestedFormVersion(connection: Connection, req: Request): Int = {
        def latest = latestNonDeletedFormVersion(connection, req.app, req.form)
        request.version match {
            case Latest         ⇒ latest.getOrElse(1)
            case Next           ⇒ latest.map(_ + 1).getOrElse(1)
            case Specific(v)    ⇒ v
            case ForDocument(_) ⇒ ??? // NYI
        }
    }

    // List of columns that identify a row
    def idColumns(req: Request): String =
        Seq(
            Some("app"), Some("form"),
            req.forForm       option "form_version",
            req.forData       option "document_id",
            req.forData       option "draft",
            req.forAttachment option "file_name"
        ).flatten.mkString(", ")

}
