/**
 * Copyright (C) 2026 Orbeon, Inc.
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

import org.orbeon.oxf.util.CoreCrossPlatformSupport
import org.orbeon.oxf.util.StringUtils.*


object SimpleConstraint {

  sealed trait SimpleConstraint {
    def satisfiedFor(currentRoles: List[String]): Boolean
  }

  case class All(roles: List[String]) extends SimpleConstraint {
    override def satisfiedFor(currentRoles: List[String]): Boolean = roles.forall(currentRoles.contains)
  }

  // Not re-using Condition.RolesAnyOf
  case class Any(roles: List[String]) extends SimpleConstraint {
    override def satisfiedFor(currentRoles: List[String]): Boolean = roles.exists(currentRoles.contains)
  }

  case object AlwaysSatisfied extends SimpleConstraint {
    def satisfiedFor(currentRoles: List[String]): Boolean = true
  }

  def apply(rolesOpt: Option[String], constraintOpt: Option[String]): SimpleConstraint =
    rolesOpt match {
      case None =>
        AlwaysSatisfied

      case Some(roles) =>
        val rolesAsList = roles.splitTo[List]().map(_.trimAllToEmpty)
        val constraint  = constraintOpt.map(_.toLowerCase.trimAllToEmpty).getOrElse("all")

        constraint match {
          case "all" | "and" => All(rolesAsList)
          case "any" | "or"  => Any(rolesAsList)
          case _             => throw new IllegalArgumentException(s"Illegal roles constraint: $constraint")
        }
    }

  def satisfiedForUserRolesFromCurrentSession(rolesOpt: Option[String], constraintOpt: Option[String]): Boolean = {

    val currentUserRolesOpt =
      Option(CoreCrossPlatformSupport.externalContext)
        .map(_.getRequest.credentials)
        .map(_.toList.flatMap(_.roles.map(_.roleName)))

    currentUserRolesOpt match {
      case Some(currentUserRoles) =>
        // Use user roles from current session
        val simpleConstraint = SimpleConstraint(rolesOpt = rolesOpt, constraintOpt = constraintOpt)
        simpleConstraint.satisfiedFor(currentUserRoles)

      case None =>
        // Current user roles not specified (see readPublishedFormStorageDetails case and tests)
        true
    }
  }
}
