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
import org.orbeon.oxf.fr.persistence.relational.search.adt.SearchPermissions
import org.orbeon.oxf.util.CoreUtils._

object permissionsPart {

  def apply(permissions: SearchPermissions) =
    if (permissions.authorizedBasedOnRole)
      NilPart
    else {
      val usernameGroupnameTest = {

        val usernameTest = permissions.authorizedIfUsername.isDefined.option("c.username = ?")
        val groupnameTest = permissions.authorizedIfGroup.isDefined.option("c.groupname = ?")
        val organizationTest = permissions.authorizedIfOrganizationMatch.nonEmpty.option {
          val userOrganizations =
            permissions.authorizedIfOrganizationMatch
              .map(RelationalUtils.sqlString)
              .mkString(", ")
          s"c.organization_id IN (SELECT id FROM orbeon_organization WHERE name IN ($userOrganizations))"
        }

        List(usernameTest, groupnameTest, organizationTest).flatten.mkString(" OR\n")
      }

      StatementPart(
        sql = s"AND ($usernameGroupnameTest)",
        setters = {
          List[Option[Setter]](
            permissions.authorizedIfUsername.map(username => _.setString(_, username)),
            permissions.authorizedIfGroup.map(group => _.setString(_, group))
          ).flatten
        }
      )
    }

}
