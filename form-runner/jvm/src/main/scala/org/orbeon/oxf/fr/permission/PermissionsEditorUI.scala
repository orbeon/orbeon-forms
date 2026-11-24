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

import io.circe.parser
import org.orbeon.oxf.util.CoreUtils.*
import org.orbeon.oxf.util.StringUtils.*
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.NodeConversions
import org.orbeon.scaxon.SimplePath.*

import scala.xml.Elem


object PermissionsEditorUI {

  import Private.*

  //@XPathFunction
  def isValidPermissionsJson(jsonString: String): Boolean =
    PermissionsJSON.parseString(Option(jsonString).getOrElse("")).isSuccess

  //@XPathFunction
  def formatPermissionsJson(jsonString: String): String = {
    val raw = Option(jsonString).getOrElse("")
    PermissionsJSON.parseString(raw) match {
      case scala.util.Success(Permissions.Undefined)        => ""
      case scala.util.Success(defined: Permissions.Defined) => PermissionsJSON.formatToString(defined)
      case scala.util.Failure(_)                            => raw
    }
  }

  //@XPathFunction
  def jsonToUiXml(jsonString: String, alwaysShowRolesJson: String): NodeInfo = {
    val permissions =
      PermissionsJSON.parseString(Option(jsonString).getOrElse("")).getOrElse(Permissions.Undefined)
    val alwaysShowRoles =
      parseAlwaysShowRoles(Option(alwaysShowRolesJson).getOrElse(""))

    NodeConversions.elemToNodeInfo(permissionsToUiElem(permissions, alwaysShowRoles))
  }

  //@XPathFunction
  def uiXmlToJson(hasPermissions: Boolean, permissionsElOrNull: NodeInfo): String =
    if (! hasPermissions) {
      ""
    } else {
      Option(permissionsElOrNull) match {
        case None =>
          "{}"
        case Some(permissionsEl) =>
          val roleElems    = (permissionsEl / PermissionElemName).filter(el => (el / RoleElemName).nonEmpty).toList
          val rawRoleNames = roleElems.map(_.elemValue(RoleElemName).trimAllToEmpty)
          val hasBlankOrDuplicateRoles =
            rawRoleNames.exists(_.isEmpty) || rawRoleNames.distinct.size != rawRoleNames.size

          if (hasBlankOrDuplicateRoles) {
            val baseJson = PermissionsJSON.serializeToJson(uiXmlToDefinedPermissions(permissionsEl)).getOrElse(io.circe.Json.obj())
            val rawRoleFields =
              roleElems.map { roleEl =>
                val roleName = roleEl.elemValue(RoleElemName).trimAllToEmpty
                val ops      = Operations.inDefinitionOrder(Operations.parseFromStringTokensNoWildcard(roleEl.elemValue(OperationsElemName)).operations)
                roleName -> io.circe.Json.fromValues(ops.map(op => io.circe.Json.fromString(op.entryName)))
              }
            val sentinelRoleFields =
              if (rawRoleNames.exists(_.isEmpty)) rawRoleFields
              else rawRoleFields :+ ("" -> io.circe.Json.arr())
            val updatedObj =
              baseJson.asObject.getOrElse(io.circe.JsonObject.empty)
                .add("roles", io.circe.Json.fromJsonObject(io.circe.JsonObject.fromIterable(sentinelRoleFields)))
            io.circe.Json.fromJsonObject(updatedObj).spaces2
          } else {
            PermissionsJSON.formatToString(uiXmlToDefinedPermissions(permissionsEl))
          }
      }
    }

  //@XPathFunction
  def xmlToJson(permissionsElOrNull: NodeInfo): String = {
    val parsed = PermissionsXML.parse(Option(permissionsElOrNull))
    PermissionsJSON.formatToString(parsed)
  }

  //@XPathFunction
  def jsonToXml(jsonString: String): NodeInfo = {
    val parsed = PermissionsJSON.parseString(Option(jsonString).getOrElse("")).getOrElse(Permissions.Undefined)
    val normalized =
      parsed match {
        case Permissions.Undefined        => Permissions.Undefined
        case defined: Permissions.Defined => Permissions.normalizePermissions(defined)
      }
    PermissionsXML.serialize(normalized, normalized = false)
      .map(NodeConversions.elemToNodeInfo)
      .orNull
  }

  def uiXmlToDefinedPermissions(permissionsEl: NodeInfo): Permissions.Defined = {

    val findAnyonePermissionElem: String => Option[NodeInfo] =
      (_: String) => (permissionsEl / PermissionElemName).find(el => (el / RoleElemName).isEmpty && el.attValueOpt("for").isEmpty)

    val findForPermissionElem: String => Option[NodeInfo] =
      (condition: String) => (permissionsEl / PermissionElemName).find(_.attValue("for") == condition)

    def findPermissionsFor(conditionOpt: Option[Condition], findElem: String => Option[NodeInfo]): Option[Permission] =
      findElem(Condition.simpleConditionToStringName(conditionOpt)) flatMap { permissionElem =>

        val specificOperations =
          Operations.parseFromStringTokensNoWildcard(permissionElem.elemValue(OperationsElemName))

        specificOperations != Operations.None option
          Permission(
            conditionOpt.toList,
            specificOperations
          )
      }

    val rawRolePermissions =
      for {
        rolePermissionElem <- (permissionsEl / PermissionElemName).toList
        nonEmptyRoleName   <- rolePermissionElem.elemValueOpt(RoleElemName).flatMap(_.trimAllToOpt)
        operations         = Operations.parseFromStringTokensNoWildcard(rolePermissionElem.elemValue(OperationsElemName))
      } yield
        Permission(List(Condition.RolesAnyOf(List(nonEmptyRoleName))), operations)

    Permissions.normalizePermissions(
      Permissions.Defined(
        findPermissionsFor(None,                                 findAnyonePermissionElem).toList :::
        findPermissionsFor(Some(Condition.AnyoneWithToken),      findForPermissionElem).toList    :::
        findPermissionsFor(Some(Condition.AnyAuthenticatedUser), findForPermissionElem).toList    :::
        findPermissionsFor(Some(Condition.Owner),                findForPermissionElem).toList    :::
        findPermissionsFor(Some(Condition.Group),                findForPermissionElem).toList    :::
        rawRolePermissions
      )
    )
  }

  def uiXmlToFormDefinitionXml(permissionsEl: NodeInfo): NodeInfo =
    NodeConversions.elemToNodeInfo(
      PermissionsXML.serialize(
        uiXmlToDefinedPermissions(permissionsEl),
        normalized = false
      ).getOrElse(throw new IllegalStateException)
    )

  def permissionsToUiElem(permissions: Permissions, alwaysShowRoles: List[String]): Elem = {

    val distinctAlwaysShowRoles = alwaysShowRoles.flatMap(_.trimAllToOpt).distinct

    val (hasPermissionsStr, definedList) =
      permissions match {
        case Permissions.Undefined     => (""    , Nil)
        case Permissions.Defined(list) => ("true", list)
      }

    def opsForSimpleCondition(condOpt: Option[Condition]): Set[Operation] = {
      val condList = condOpt.toList
      definedList.collect {
        case Permission(`condList`, SpecificOperations(ops)) => ops
      }.flatten.toSet
    }

    def formatOps(ops: Set[Operation]): String =
      Operations.inDefinitionOrder(ops).map(_.entryName).mkString(" ")

    // In the UI grid, Row 1 (`Anyone`) shows `anyone` ∪ `anyone-with-token` so that token-gated
    // operations remain checked on `Anyone` alongside `Require token`.
    val anyoneUiOpsStr =
      formatOps(opsForSimpleCondition(None) ++ opsForSimpleCondition(Some(Condition.AnyoneWithToken)))

    val rolePairs: List[(String, Set[Operation])] =
      definedList.collect {
        case Permission(List(Condition.RolesAnyOf(roles)), SpecificOperations(ops)) =>
          roles.map(_ -> ops)
      }.flatten

    val allRoles = (distinctAlwaysShowRoles ++ rolePairs.map(_._1)).distinct

    <editor>
      <has-permissions>{hasPermissionsStr}</has-permissions>
      <always-show-roles>{
        distinctAlwaysShowRoles.map(r => <role>{r}</role>)
      }</always-show-roles>
      <permissions>
        <permission><operations>{anyoneUiOpsStr}</operations></permission>
        <permission for="anyone-with-token"><operations>{formatOps(opsForSimpleCondition(Some(Condition.AnyoneWithToken)))}</operations></permission>
        <permission for="any-authenticated-user"><operations>{formatOps(opsForSimpleCondition(Some(Condition.AnyAuthenticatedUser)))}</operations></permission>
        <permission for="owner"><operations>{formatOps(opsForSimpleCondition(Some(Condition.Owner)))}</operations></permission>
        <permission for="group-member"><operations>{formatOps(opsForSimpleCondition(Some(Condition.Group)))}</operations></permission>{
          allRoles.map { role =>
            val roleOpsStr = formatOps(rolePairs.collect { case (`role`, ops) => ops }.flatten.toSet)
            <permission><operations>{roleOpsStr}</operations><role>{role}</role></permission>
          }
        }
      </permissions>
    </editor>
  }

  private object Private {

    val PermissionElemName = "permission"
    val OperationsElemName = "operations"
    val RoleElemName       = "role"

    def parseAlwaysShowRoles(alwaysShowRolesJson: String): List[String] =
      alwaysShowRolesJson.trimAllToOpt match {
        case Some(nonBlank) =>
          parser.parse(nonBlank).flatMap(_.as[List[String]]).getOrElse(Nil)
        case None =>
          Nil
      }
  }
}
