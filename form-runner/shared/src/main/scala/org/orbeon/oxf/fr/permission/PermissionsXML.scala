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
package org.orbeon.oxf.fr.permission

import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.SimplePath._
import org.orbeon.oxf.util.StringUtils._

import scala.xml.Elem


object PermissionsXML {

  def serialize(permissions: Permissions, normalized: Boolean): Option[Elem] =
    permissions match {
      case Permissions.Undefined =>
        None
      case Permissions.Defined(permissionsList) =>
        Some(
          <permissions>
            {
              permissionsList map { permission =>
              <permission operations={Operations.serialize(permission.operations, normalized).mkString(" ")}>
                {
                  permission.conditions map {
                    case Condition.AnyoneWithToken      => <anyone-with-token/>      // new with #5437
                    case Condition.AnyAuthenticatedUser => <any-authenticated-user/> // new with #5437
                    case Condition.Owner                => <owner/>
                    case Condition.Group                => <group-member/>
                    case Condition.RolesAnyOf(roles)    => <user-role any-of={roles.map(_.replace(" ", "%20")).mkString(" ")}/>
                  }
                }
              </permission>
              }
            }
          </permissions>
        )
    }

  def parse(permissionsElemOpt: Option[NodeInfo]): Permissions =
    permissionsElemOpt match {
      case None =>
        Permissions.Undefined
      case Some(permissionsEl) =>
        Permissions.Defined(permissionsEl.child("permission").toList.map(parsePermission))
    }

  /**
   * Given a permission element, e.g.:
   *
   *   <permission operations="read update delete">
   *
   * See backward compatibility handling: https://github.com/orbeon/orbeon-forms/issues/5397
   */
  private def parsePermission(permissionEl: NodeInfo): Permission = {

    val operations =
      Operations.normalizeAndParseSpecificOperations(permissionEl.attValue("operations"))

    val conditions =
      permissionEl.child(*).toList.map(
        conditionEl =>
          conditionEl.localname
            match {
              case "user-role" =>
                val anyOfAttValue = conditionEl.attValue("any-of")
                val rawRoles      = anyOfAttValue.splitTo[List](" ")
                val roles         = rawRoles.map(_.replace("%20", " "))
                Condition.RolesAnyOf(roles)
              case condition =>
                Condition.parseSimpleCondition(condition).getOrElse(throw new IllegalArgumentException(condition))
            }
      )

    Permission(conditions, operations)
  }
}
