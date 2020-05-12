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
package org.orbeon.oxf.fr.persistence.rest

import scala.xml.Elem

private object Permissions {

  type Permissions = Option[Seq[Permission]]

  sealed trait PermissionFor
  case object Anyone            extends PermissionFor
  case object Owner             extends PermissionFor
  case object Group             extends PermissionFor
  case class Role(role: String) extends PermissionFor
  case class Permission(permissionFor: PermissionFor, operations: Set[String])

  def serialize(permissions: Permissions): Option[Elem] =
    permissions map ( ps =>
      <permissions>{ ps map ( p =>
        <permission operations={p.operations.mkString(" ")}>{
          p.permissionFor match {
            case Anyone  => ""
            case Owner   => <owner/>
            case Group   => <group-member/>
            case Role(r) => <user-role any-of={r}/>
          }
        }</permission>
      )}</permissions>
    )
}
