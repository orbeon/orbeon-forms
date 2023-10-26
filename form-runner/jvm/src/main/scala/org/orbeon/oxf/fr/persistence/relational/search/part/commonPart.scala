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

import org.orbeon.oxf.fr.persistence.relational.Statement.{Setter, StatementPart}
import org.orbeon.oxf.fr.persistence.relational.search.adt.{Control, FilterType}
import org.orbeon.oxf.fr.{AppForm, FormDefinitionVersion}
import org.orbeon.oxf.util.CoreUtils._


object commonPart  {

  def apply(
    appForm       : AppForm,
    version       : FormDefinitionVersion,
    controls      : List[Control],
    freeTextSearch: Option[String]
  ): StatementPart = {

    StatementPart(
      sql = {
        val controlFilterTables =
          controls
            .filter(_.filterType != FilterType.None)
            .zipWithIndex
            .map { case (_, i) => s", orbeon_i_control_text tf$i" }
            .mkString(" ")
        val freeTextTable =
          freeTextSearch.nonEmpty.string(", orbeon_form_data d")

        s"""|SELECT DISTINCT c.data_id
            |           FROM orbeon_i_current c
            |                $controlFilterTables
            |                $freeTextTable
            |          WHERE     c.app          = ?
            |                AND c.form         = ?
            |                ${ if (version != FormDefinitionVersion.Latest) "AND c.form_version = ?" else ""}
            |""".stripMargin
      },
      setters = {
        val appSetter        :        Setter  = _.setString(_, appForm.app)
        val formSetter       :        Setter  = _.setString(_, appForm.form)
        val versionSetterOpt : Option[Setter] = version match {
          case FormDefinitionVersion.Specific(v) => Some(_.setInt(_, v))
          case FormDefinitionVersion.Latest      => None
        }
        List(appSetter, formSetter) ++ versionSetterOpt
      }
    )
  }
}
