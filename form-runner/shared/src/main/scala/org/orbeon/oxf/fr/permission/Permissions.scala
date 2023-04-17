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

sealed trait                                                              Permissions
case object UndefinedPermissions                                  extends Permissions
case class  DefinedPermissions(permissionsList: List[Permission]) extends Permissions

case class Permission(
  conditions: List[Condition],
  operations: SpecificOperations
)

sealed trait                                                              Condition
case object AnyoneWithToken                                       extends Condition // new with #5437
case object AnyAuthenticatedUser                                  extends Condition // new with #5437
case object Owner                                                 extends Condition
case object Group                                                 extends Condition
case class  RolesAnyOf(roles: List[String])                       extends Condition
