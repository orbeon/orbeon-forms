/**
  * Copyright (C) 2013 Orbeon, Inc.
  *
  * This program is free software; you can redistribute it and/or modify it under the terms of the
  * GNU Lesser General Public License as published by the Free Software Foundation; either version
  *  2.1 of the License, or (at your option) any later version.
  *
  * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  * See the GNU Lesser General Public License for more details.
  *
  * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
  */
package org.orbeon.oxf.fr.persistence.relational

import java.sql.Connection

import org.orbeon.oxf.fr.persistence.relational.Version._
import org.orbeon.oxf.http.{HttpStatusCodeException, StatusCode}
import org.orbeon.io.IOUtils.useAndClose

object RelationalCommon {

  def joinColumns(cols: Seq[String], t1: String, t2: String): String =
    cols.map(c => s"$t1.$c = $t2.$c").mkString(" AND ")

  /**
    * Finds in the database what form version is used for an app/form and optional document id:
    *
    * 1. If only the app/form are provided, it returns the latest version of a published, non-deleted, form.
    * We wouldn't want to return a version of a deleted published form, as running a GET for the latest
    * form definition for an app/form, we would here return the version of a deleted form (and then 404 as we
    * can't find that form in the database).
    *
    * 2. If the document id is provided, it returns the form version used for that document. We could return the
    * form version for a deleted document, but decided against it for consistency with what we do when returning
    * the form version for app/form. (A benefit of returning the version of a deleted data is that this could
    * allow Form Runner to return a 510 to the browser. Without it, since Form Runner starts by reading the form
    * definition, it will fail if that version isn't found. But this isn't a real benefit since right now the
    * Page Flow Controller doesn't know how to return a 510.)
    */
  def formVersion(connection: Connection, app: String, form: String, docId: Option[String]): Option[Int] = {
    val table = s"orbeon_form_${if (docId.isEmpty) "definition" else "data"}"
    val versionSql =
      s"""|SELECT max(t.form_version)
          |FROM   $table t,
          |       (
          |           SELECT   max(last_modified_time) last_modified_time, app, form, form_version
          |           FROM     $table
          |           WHERE    app = ?
          |                    AND form = ?
          |                    ${docId.map(_ => "and document_id = ?").getOrElse("")}
          |           GROUP BY app, form, form_version
          |       ) m
          |WHERE  ${joinColumns(Seq("last_modified_time", "app", "form", "form_version"), "t", "m")}
          |       AND t.deleted = 'N'
          |""".stripMargin
    useAndClose(connection.prepareStatement(versionSql)) { ps =>
      ps.setString(1, app)
      ps.setString(2, form)
      docId.foreach(ps.setString(3, _))
      useAndClose(ps.executeQuery()) { rs =>
        rs.next()
        val version = rs.getInt(1)
        if (rs.wasNull()) None else Some(version)
      }
    }
  }

  /**
    * For every request, there is a corresponding specific form version number. In the request, that specific version
    * can be specified, but the caller can also say that it wants the next version, the latest version, or the version
    * of the form used to create a specific document. This function finds the specific form version corresponding to
    * the request.
    *
    * Throws `HttpStatusCodeException` if `ForDocument` and the document is not found.
    */
  def requestedFormVersion(connection: Connection, req: RequestCommon): Int = {

    def latest = formVersion(connection, req.app, req.form, None)

    req.version match {
      case Unspecified        => latest.getOrElse(1)
      case Next               => latest.map(_ + 1).getOrElse(1)
      case Specific(v)        => v
      case ForDocument(docId) => formVersion(connection, req.app, req.form, Some(docId))
        .getOrElse(throw HttpStatusCodeException(StatusCode.NotFound))
    }
  }

}
