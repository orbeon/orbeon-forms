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
package org.orbeon.oxf.fr.analytics

import org.orbeon.oxf.fr.{AppForm, FormRunner}
import org.orbeon.oxf.properties.PropertySet
import org.orbeon.oxf.util.CoreCrossPlatformSupport
import org.orbeon.oxf.util.StringUtils.*
import org.orbeon.saxon.om.NodeInfo


final case class AnalyticsScope(permissionsByApp: Map[String, Set[String]]) {

  def isAuthorized: Boolean =
    permissionsByApp.nonEmpty

  def hasGlobalAccess: Boolean =
    permissionsByApp.get("*").exists(_.contains("*"))

  def allowsApp(app: String): Boolean =
    hasGlobalAccess ||
      permissionsByApp.contains(app)

  def allowsAppForm(appForm: AppForm): Boolean =
    hasGlobalAccess ||
      permissionsByApp.get("*").exists(forms => forms.contains("*") || forms.contains(appForm.form)) ||
      permissionsByApp.get(appForm.app).exists(forms => forms.contains("*") || forms.contains(appForm.form))

  def union(other: AnalyticsScope): AnalyticsScope = {
    if (this.hasGlobalAccess || other.hasGlobalAccess) {
      AnalyticsScope.GlobalScope
    } else {
      val mergedKeys = this.permissionsByApp.keySet ++ other.permissionsByApp.keySet
      val merged =
        mergedKeys.iterator.map { app =>
          val formsA = this.permissionsByApp.getOrElse(app, Set.empty)
          val formsB = other.permissionsByApp.getOrElse(app, Set.empty)
          val combined = formsA ++ formsB
          app -> (if (combined.contains("*")) Set("*") else combined)
        }.toMap
      AnalyticsScope(merged)
    }
  }
}

object AnalyticsScope {

  val AnalyticsRolesPropertyPrefix = "oxf.fr.analytics.roles"

  val EmptyScope : AnalyticsScope = AnalyticsScope(Map.empty)
  val GlobalScope: AnalyticsScope = AnalyticsScope(Map("*" -> Set("*")))

  def currentRequestRolesSafe: Set[String] =
    if (Option(CoreCrossPlatformSupport.externalContext).flatMap(ec => Option(ec.getRequest)).isDefined)
      FormRunner.orbeonRolesFromCurrentRequest
    else
      Set.empty

  def forCurrentRequest(): AnalyticsScope =
    fromPermissionsAndProperties(
      fbConfigurationOpt = FormRunner.formBuilderPermissionsConfiguration,
      propertySet        = CoreCrossPlatformSupport.properties,
      incomingRoleNames  = currentRequestRolesSafe
    )

  def fromPermissionsAndProperties(
    fbConfigurationOpt: Option[NodeInfo],
    propertySet       : PropertySet,
    incomingRoleNames : Set[String]
  ): AnalyticsScope = {
    val fbScope   = AnalyticsScope(FormRunner.formBuilderPermissions(fbConfigurationOpt, incomingRoleNames))
    val propScope = fromPropertySet(propertySet, incomingRoleNames)
    fbScope.union(propScope)
  }

  def fromPropertySet(
    propertySet      : PropertySet,
    incomingRoleNames: Set[String]
  ): AnalyticsScope =
    if (incomingRoleNames.isEmpty) {
      EmptyScope
    } else {
      var acc = EmptyScope
      val prefixWithDot = AnalyticsRolesPropertyPrefix + "."
      propertySet.allProperties.foreach { prop =>
        val name = prop.name
        if (name == AnalyticsRolesPropertyPrefix || name.startsWith(prefixWithDot)) {
          val configuredRoles = parseRoleTokens(prop.stringValue)
          if (configuredRoles.nonEmpty && (configuredRoles.contains("*") || (configuredRoles & incomingRoleNames).nonEmpty)) {
            val suffixSegments =
              if (name == AnalyticsRolesPropertyPrefix) Nil
              else name.stripPrefix(prefixWithDot).splitTo[List](".")

            val grant = suffixSegments match {
              case Nil | "*" :: Nil | "*" :: "*" :: _ =>
                GlobalScope
              case app :: Nil if app.nonAllBlank =>
                AnalyticsScope(Map(app -> Set("*")))
              case app :: form :: _ if app.nonAllBlank && form.nonAllBlank =>
                AnalyticsScope(Map(app -> Set(form)))
              case _ =>
                EmptyScope
            }
            acc = acc.union(grant)
          }
        }
      }
      acc
    }

  private def parseRoleTokens(raw: String): Set[String] =
    raw
      .replace(',', ' ')
      .splitTo[Set]()
}
