/**
 * Copyright (C) 2016 Orbeon, Inc.
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
package org.orbeon.oxf.fr.persistence.relational.search.part

import org.orbeon.oxf.fr.persistence.relational.Statement.StatementPart
import org.orbeon.oxf.fr.persistence.relational.search.adt.Drafts._
import org.orbeon.oxf.fr.persistence.relational.search.adt.WhichDrafts._
import org.orbeon.oxf.fr.persistence.relational.search.adt._

object draftsPart {

  def apply(request: Request) =
    request.drafts match {
      case IncludeDrafts => StatementPart("", Nil)
      case ExcludeDrafts => StatementPart(" AND c.draft = 'N'", Nil)
      case OnlyDrafts(whichDrafts) =>
        val justDraft = " AND c.draft = 'Y'"
        whichDrafts match {
          case AllDrafts =>
            StatementPart(justDraft, Nil)
          case DraftsForNeverSavedDocs =>
            StatementPart(
              sql = justDraft +
                """| AND
                   |   (
                   |     SELECT count(*)
                   |     FROM orbeon_i_current c2
                   |     WHERE
                   |         c2.app         = ?             AND
                   |         c2.form        = ?             AND
                   |         c2.draft       = 'N'           AND
                   |         c2.document_id = c.document_id
                   |   ) = 0
                   |""".stripMargin,
              setters = List(
                _.setString(_, request.app),
                _.setString(_, request.form)
              )
            )
          case DraftsForDocumentId(documentId) =>
            StatementPart(
              sql = justDraft + " AND c.document_id = ?",
              setters = List(_.setString(_, documentId))
            )
      }
    }

}
