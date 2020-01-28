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
package org.orbeon.oxf.fr

import org.orbeon.dom.saxon.DocumentWrapper
import org.orbeon.oxf.fr.FormRunner.orbeonRolesFromCurrentRequest
import org.orbeon.oxf.resources.ResourceManagerWrapper
import org.orbeon.oxf.util.XPath
import org.orbeon.saxon.om.{DocumentInfo, NodeInfo}
import org.orbeon.scaxon.NodeConversions._
import org.orbeon.scaxon.SimplePath._
import scala.collection.compat._

trait FormBuilderPermissionsOps {

  // Load form-builder-permissions.xml. For code called from XForms, that instance is loaded in permissions-model.xml.
  //@XPathFunction
  def formBuilderPermissionsConfiguration: Option[DocumentInfo] = {

    val supportedPaths  = List(
      "/config/form-builder-permissions.xml",
      "/config/form-runner-roles.xml"
    )

    val resourceManager = ResourceManagerWrapper.instance

    supportedPaths collectFirst
      {case key if resourceManager.exists(key) => resourceManager.getContentAsDOM4J(key)} map
      (new DocumentWrapper(_, null, XPath.GlobalConfiguration))
  }

  //@XPathFunction
  def formBuilderPermissionsForCurrentUserXPath(configurationOpt: Option[NodeInfo]) =
    formBuilderPermissionsForCurrentUserAsXML(configurationOpt, orbeonRolesFromCurrentRequest)

  // Result document contains a tree structure of apps and forms if roles are configured
  // NOTE: The result is sorted by app name first, then form name.
  def formBuilderPermissionsForCurrentUserAsXML(configurationOpt: Option[NodeInfo], incomingRoleNames: Set[String]): NodeInfo =
    if (findConfiguredRoles(configurationOpt).isEmpty) {
      <apps has-roles="false"/>
    } else {
      <apps has-roles="true">{
        formBuilderPermissions(configurationOpt, incomingRoleNames).to(List).sortBy(_._1) map { case (app, forms) =>
          <app name={app}>{ forms.to(List).sorted map { form => <form name={form}/> } }</app>
        }
      }</apps>
    }

  def formBuilderPermissions(configurationOpt: Option[NodeInfo], incomingRoleNames: Set[String]): Map[String, Set[String]] =
    findConfiguredRoles(configurationOpt) match {
      case Nil =>
        // No role configured
        Map.empty
      case configuredRoles =>
        // Roles configured
        val allConfiguredRoleNames = configuredRoles map (_.attValue("name")) toSet
        val applicableRoleNames    = allConfiguredRoleNames & incomingRoleNames
        val applicableRoles        = configuredRoles filter (e => (applicableRoleNames + "*")(e.attValue("name")))
        val applicableAppNames     = applicableRoles map (_.attValue("app")) toSet

        if (applicableAppNames("*")) {
          // User has access to all apps (and therefore all forms)
          Map("*" -> Set("*"))
        } else {
          // User has access to certain apps only
          (for {
            app <- applicableAppNames
            forms = {
              val applicableFormsForApp = applicableRoles filter (_.attValue("app") == app) map (_.attValue("form")) toSet

              if (applicableFormsForApp("*")) Set("*") else applicableFormsForApp
            }
          } yield
            app -> forms) toMap
        }
    }

  private def findConfiguredRoles(configurationOpt: Option[NodeInfo]) = configurationOpt match {
    case Some(configuration) => configuration.root / * / "role" toList
    case None                => Nil
  }
}

object FormBuilderPermissionsOps extends FormBuilderPermissionsOps