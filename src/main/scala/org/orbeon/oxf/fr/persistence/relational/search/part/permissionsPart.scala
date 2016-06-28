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

import org.orbeon.oxf.fr.persistence.relational.Statement.{StatementPart, _}
import org.orbeon.oxf.fr.persistence.relational.search.adt.Permissions
import org.orbeon.oxf.util.ScalaUtils._

object permissionsPart {

  def apply(permissions: Permissions) =
    if (permissions.authorizedIfUsername.isEmpty && permissions.authorizedIfGroup.isEmpty)
      NilPart
    else {
      val usernameGroupnameTest =
        List(
          permissions.authorizedIfUsername.map(_ ⇒ "c.username = ?"),
          (permissions.authorizedIfUsername.isDefined
            && permissions.authorizedIfGroup.isDefined).option("OR"),
          permissions.authorizedIfGroup.map(_ ⇒ "c.groupname = ?")
        )
        .flatten
        .mkString(" ")

      StatementPart(
        sql = s"AND ($usernameGroupnameTest)",
        setters = List[Option[Setter]](
          permissions.authorizedIfUsername.map(username ⇒ _.setString(_, username)),
          permissions.authorizedIfGroup   .map(group    ⇒ _.setString(_, group))
        ).flatten
      )
    }

}
