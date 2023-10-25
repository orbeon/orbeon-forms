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

import org.orbeon.oxf.fr.FormDefinitionVersion
import org.orbeon.oxf.fr.persistence.relational.Statement.{Setter, StatementPart}
import org.orbeon.oxf.fr.persistence.relational.search.adt.{DocumentSearchRequest, FilterType, SearchRequest}
import org.orbeon.oxf.util.CoreUtils._


object commonPart  {

  def apply(
    request: SearchRequest,
    version: FormDefinitionVersion
  ): StatementPart = {

    StatementPart(
      sql = {
        val fieldFilterTables =
          request.fields
            .filter(_.filterType != FilterType.None)
            .zipWithIndex
            .map { case (_, i) => s", orbeon_i_control_text tf$i" }
            .mkString(" ")

        val freeTextTable = request match {
          case documentSearchRequest: DocumentSearchRequest => documentSearchRequest.freeTextSearch.nonEmpty.string(", orbeon_form_data d")
          case _                                            => ""
        }

        s"""|SELECT DISTINCT c.data_id
            |           FROM orbeon_i_current c
            |                $fieldFilterTables
            |                $freeTextTable
            |          WHERE     c.app          = ?
            |                AND c.form         = ?
            |                ${ if (version != FormDefinitionVersion.Latest) "AND c.form_version = ?" else ""}
            |""".stripMargin
      },
      setters = {
        val appSetter        :        Setter  = _.setString(_, request.appForm.app)
        val formSetter       :        Setter  = _.setString(_, request.appForm.form)
        val versionSetterOpt : Option[Setter] = version match {
          case FormDefinitionVersion.Specific(v) => Some(_.setInt(_, v))
          case FormDefinitionVersion.Latest      => None
        }
        List(appSetter, formSetter) ++ versionSetterOpt
      }
    )
  }
}
