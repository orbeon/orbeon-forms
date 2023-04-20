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

import org.orbeon.oxf.util.CollectionUtils


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

object Condition {

  def parseSimpleCondition(conditionString: String): Option[Condition] =
    conditionString match {
      case "anyone"                 => None
      case "anyone-with-token"      => Some(AnyoneWithToken)
      case "any-authenticated-user" => Some(AnyAuthenticatedUser)
      case "owner"                  => Some(Owner)
      case "group-member"           => Some(Group)
      case other                    => throw new IllegalArgumentException(other)
    }

  def simpleConditionToStringName(conditionOpt: Option[Condition]): String =
    conditionOpt match {
      case None                       => "anyone"
      case Some(AnyoneWithToken)      => "anyone-with-token"
      case Some(AnyAuthenticatedUser) => "any-authenticated-user"
      case Some(Owner)                => "owner"
      case Some(Group)                => "group-member"
      case Some(other)                => throw new IllegalArgumentException(other.toString)
    }
}

object Permissions {

  private def addImpliedRead(permissions: DefinedPermissions): DefinedPermissions =
    permissions.copy(
      permissionsList =
        permissions.permissionsList.map {
          case Permission(conditions, SpecificOperations(ops)) if ops.contains(Operation.Update) =>
            Permission(conditions, SpecificOperations(ops + Operation.Read))
          case permission =>
            permission
        }
    )

  private def addImpliedOps(permissions: DefinedPermissions): DefinedPermissions = {

    def findOpsForCondition(condition: Option[Condition]): Set[Operation] = {
      val ConditionList = condition.toList
      permissions.permissionsList.collect {
        case Permission(ConditionList, SpecificOperations(ops)) => ops
      }.flatten.toSet
    }

    val anyoneOps  = findOpsForCondition(None)
    val tokenOps   = findOpsForCondition(Some(AnyoneWithToken))
    val anyAuthOps = findOpsForCondition(Some(AnyAuthenticatedUser))

    def isAnyAuthImplied(op: Operation) =
      anyoneOps.contains(op) && ! tokenOps.contains(op)

    permissions.copy(
      permissionsList =
        permissions.permissionsList.map {
          case Permission(Nil, SpecificOperations(ops)) =>
            Permission(Nil, SpecificOperations(ops -- tokenOps)) // remove ops that require a token
          case permission @ Permission(List(AnyoneWithToken), _) =>
            permission
          case Permission(conditions @ List(AnyAuthenticatedUser), SpecificOperations(ops)) =>
            Permission(conditions, SpecificOperations(ops.filterNot(isAnyAuthImplied)))
          case Permission(conditions, SpecificOperations(ops)) =>
            Permission(conditions, SpecificOperations(ops.filterNot(op => isAnyAuthImplied(op) || anyAuthOps.contains(op))))
          case permission =>
            permission
        }
    )
  }

  private def removeEmptyPermissions(permissions: DefinedPermissions): DefinedPermissions =
    permissions.copy(
      permissionsList =
        permissions.permissionsList.collect {
          case permission @ Permission(_, SpecificOperations(ops)) if ops.nonEmpty =>
            permission
        }
    )

  private def normalizedRolePermissions(permissions: DefinedPermissions): DefinedPermissions = {

    val rawRolePermissions = permissions.permissionsList.collect {
      case rolePermission @ Permission(List(RolesAnyOf(_)), SpecificOperations(_)) => rolePermission
    }

    val orderedDistinctRoleOperations =
      Operations.inDefinitionOrder(rawRolePermissions.flatMap(_.operations.operations).distinct)

    val rolesWithOperations =
      for {
        roleOperation <- orderedDistinctRoleOperations
        rolesWithOp   = rawRolePermissions.collect {
          // Pattern matches the structure above (single condition, single role name)
          case Permission(List(RolesAnyOf(List(role))), SpecificOperations(ops)) if ops.contains(roleOperation) => role
        }
      } yield
        rolesWithOp.sorted -> roleOperation

    val normalizedRolePermissions: List[Permission] =
      for ((roles, operations) <- CollectionUtils.combineValues(rolesWithOperations))
      yield
        Permission(List(RolesAnyOf(roles)), SpecificOperations(operations.toSet))

    permissions.copy(
      permissionsList =
        permissions.permissionsList
          .filterNot(rawRolePermissions.contains) :::
          normalizedRolePermissions
    )
  }

  def normalizePermissions(rawPermissions: DefinedPermissions): DefinedPermissions =
    removeEmptyPermissions(
      normalizedRolePermissions(
        addImpliedOps(
          addImpliedRead(rawPermissions)
        )
      )
    )
}