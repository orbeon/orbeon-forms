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
import org.orbeon.oxf.fr.relational.{ForDocument, Specific, Next, Unspecified}
import org.orbeon.oxf.util.{LoggerFactory, IndentedLogger}
import org.orbeon.oxf.fr.{FormRunnerPersistence, FormRunner}
import org.orbeon.oxf.webapp.HttpStatusCodeException
import org.orbeon.scaxon.XML._
import org.orbeon.saxon.om.DocumentInfo

trait Common extends RequestResponse with FormRunnerPersistence {

    implicit val Logger = new IndentedLogger(LoggerFactory.createLogger(classOf[CRUD]), "")

    /**
     * Finds in the database what form version is used for an app/form and optional document id:
     *
     * 1. If only the app/form are provided, it returns the latest version of a published, non-deleted, form.
     *    We wouldn't want to return a version of a deleted published form, as running a GET for the latest
     *    form definition for an app/form, we would here return the version of a deleted form (and then 404 as we
     *    can't find that form in the database).
     * 2. If the document id is provided, it returns the form version used for that document. We could return the
     *    form version for a deleted document, but decided against it for consistency with what we do when returning
     *    the form version for app/form. (A benefit of returning the version of a deleted data is that this could
     *    allow Form Runner to return a 510 to the browser. Without it, since Form Runner starts by reading the form
     *    definition, it will fail if that version isn't found. But this isn't a real benefit since right now the
     *    Page Flow Controller doesn't know how to return a 510.)
     */
    def formVersion(connection: Connection, app: String, form: String, docId: Option[String]): Option[Int] = {
        val versionResult = {
            val table = s"orbeon_form_${if (docId.isEmpty) "definition" else "data"}"
            val ps = connection.prepareStatement(
                s"""|SELECT max(t.form_version)
                    |FROM   $table t,
                    |       (
                    |           SELECT   max(last_modified_time) last_modified_time, app, form, form_version
                    |           FROM     $table
                    |           WHERE    app = ?
                    |                    AND form = ?
                    |                    ${docId.map(_ ⇒ "and document_id = ?").getOrElse("")}
                    |           GROUP BY app, form, form_version
                    |       ) m
                    |WHERE  ${joinColumns(Seq("last_modified_time", "app", "form", "form_version"), "t", "m")}
                    |       AND t.deleted = 'N'
                    |""".stripMargin)
            ps.setString(1, app)
            ps.setString(2, form)
            docId.foreach(ps.setString(3, _))
            val rs = ps.executeQuery()
            rs.next(); rs
        }
        val version = versionResult.getInt(1)
        if (versionResult.wasNull()) None else Some(version)
    }

    /**
     * For every request, there is a corresponding specific form version number. In the request, that specific version
     * can be specified, but the caller can also say that it wants the next version, the latest version, or the version
     * of the form used to create a specific document. This function finds the specific form version corresponding to
     * the request.
     */
    def requestedFormVersion(connection: Connection, req: Request): Int = {
        def latest = formVersion(connection, req.app, req.form, None)
        req.version match {
            case Unspecified        ⇒ latest.getOrElse(1)
            case Next               ⇒ latest.map(_ + 1).getOrElse(1)
            case Specific(v)        ⇒ v
            case ForDocument(docId) ⇒ formVersion(connection, req.app, req.form, Some(docId))
                                           .getOrElse(throw new HttpStatusCodeException(404))
        }
    }

    // List of columns that identify a row
    def idColumns(req: Request): List[String] =
        List(
            Some("app"), Some("form"),
            req.forForm       option "form_version",
            req.forData       option "document_id",
            req.forData       option "draft",
            req.forAttachment option "file_name"
        ).flatten

    def idColumnsList(req: Request): String = idColumns(req).mkString(", ")
    def joinColumns(cols: Seq[String], t1: String, t2: String) = cols.map(c ⇒ s"$t1.$c = $t2.$c").mkString(" AND ")

    def readFormMetadata(req: Request): DocumentInfo =
        readFormMetadata(req.app, req.form).ensuring(_.isDefined, "can't find form metadata for data").get

    // Given a user/group name coming from the data, tells us what operations we can do in this data, assuming that
    // it is for the current request app/form
    def authorizedOperations(formMetadata: DocumentInfo, dataUserGroup: (Option[String], Option[String])): Set[String] = {
        val permissions = (formMetadata / "forms" / "form" / "permissions").headOption
        permissions match {
            case None                ⇒ Set("create", "read", "update", "delete")
            case Some(permissionsEl) ⇒
                val (username, groupname) = dataUserGroup
                FormRunner.allAuthorizedOperations(permissionsEl, username, groupname).toSet
        }
    }
}
