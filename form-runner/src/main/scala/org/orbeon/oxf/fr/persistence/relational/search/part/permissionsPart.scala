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

import org.orbeon.oxf.fr.persistence.relational.RelationalUtils
import org.orbeon.oxf.fr.persistence.relational.Statement.{StatementPart, _}
import org.orbeon.oxf.fr.persistence.relational.search.adt.Permissions
import org.orbeon.oxf.util.CoreUtils._

object permissionsPart {

  def apply(permissions: Permissions) =
    if (permissions.authorizedBasedOnRole)
      NilPart
    else {
      val usernameGroupnameTest = {

        val usernameTest     = permissions.authorizedIfUsername.isDefined.option("c.username = ?")
        val groupnameTest    = permissions.authorizedIfGroup   .isDefined.option("c.groupname = ?")
        val organizationTest = permissions.authorizedIfOrganizationMatch.map(organization ⇒ {

          val tables = organization.levels
            .zipWithIndex
            .map{ case (_, index) ⇒  s"(SELECT id FROM orbeon_organization WHERE pos = ? AND name = ?) o${index + 1}" }
            .mkString(",\n")
            .pipe(RelationalUtils.indentSubQuery(_, 2))

          val where = (1 until organization.levels.length)
            .map(index ⇒ s"o$index.id = o${index + 1}.id")
            .mkString(" AND\n")
            .pipe(RelationalUtils.indentSubQuery(_, 2))

          s"""c.organization_id IN (
             |  SELECT id FROM
             |  $tables
             |  WHERE
             |  $where
             |)
           """.stripMargin
        })

        List(usernameTest, groupnameTest, organizationTest).flatten.mkString(" OR\n")
      }

      StatementPart(
        sql = s"AND ($usernameGroupnameTest)",
        setters = {

          val usernameSetter     : Option[Setter] = permissions.authorizedIfUsername.map(username ⇒ _.setString(_, username))
          val groupnameSetter    : Option[Setter] = permissions.authorizedIfGroup   .map(group    ⇒ _.setString(_, group))
          val organizationSetters: List  [Setter] = permissions.authorizedIfOrganizationMatch.toList.flatMap(organization ⇒ {
            organization.levels.zipWithIndex.flatMap { case (levelName, pos) ⇒
              List[Setter](
                (ps, index) ⇒ ps.setInt   (index, pos + 1),
                (ps, index) ⇒ ps.setString(index, levelName)
              )
            }
          })

          List(
            usernameSetter .toList,
            groupnameSetter.toList,
            organizationSetters
          ).flatten
        }
      )
    }

}
