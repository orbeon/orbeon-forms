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
package org.orbeon.oxf.fb

import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.XML._
import org.orbeon.oxf.fr.FormRunner.orbeonRoles

trait PermissionsOps {

    // Whether, given permissions specified in XML, the user has Form Builder access to the given app/form
    def hasAdminPermissionsFor(fbPermissions: NodeInfo, app: String, form: String): Boolean = {

        def findAppElement(a: String) = fbPermissions \ "app" find (_ \@ "name" === a)
        def findFormElement(e: NodeInfo, f: String) = e \ "form" find (_ \@ "name" === f)

        def matchesApp(a: String) = findAppElement(a).isDefined
        def matchesForm(e: NodeInfo, f: String) = findFormElement(e, f).isDefined

        matchesApp("*") || (matchesApp(app) && (matchesForm(findAppElement(app).get, "*") || matchesForm(findAppElement(app).get, form)))
    }

    // For XForms callers
    def formBuilderPermissionsAsXML(formBuilderRoles: NodeInfo): NodeInfo = {
        // NOTE: Whether in container or header mode, roles are parsed into the Orbeon-Roles header at this point
        val appForms = formBuilderPermissions(formBuilderRoles, orbeonRoles)

        if (appForms.isEmpty)
            <apps has-roles="false"/>
        else
            // Result document contains a tree structure of apps and forms
            <apps has-roles="true">{
                appForms.to[List].sortBy(_._1) map { case (app, forms) ⇒
                    <app name={app}>{ forms map { form ⇒ <form name={form}/> } }</app>
                }
            }</apps>
    }

    def formBuilderPermissions(formBuilderRoles: NodeInfo, incomingRoleNames: Set[String]): Map[String, Set[String]] = {

        val configuredRoles = formBuilderRoles.root \ * \ "role"
        if (configuredRoles.isEmpty) {
            // No role configured
            Map()
        } else {
            // Roles configured
            val allConfiguredRoleNames = configuredRoles map (_.attValue("name")) toSet
            val applicableRoleNames    = allConfiguredRoleNames & incomingRoleNames
            val applicableRoles        = configuredRoles filter (e ⇒ (applicableRoleNames + "*")(e.attValue("name")))
            val applicableAppNames     = applicableRoles map (_.attValue("app")) toSet

            if (applicableAppNames("*")) {
                // User has access to all apps (and therefore all forms)
                Map("*" → Set("*"))
            } else {
                // User has access to certain apps only
                (for {
                    app ← applicableAppNames
                    forms = {
                        val applicableFormsForApp = applicableRoles filter (_.attValue("app") == app) map (_.attValue("form")) toSet

                        if (applicableFormsForApp("*")) Set("*") else applicableFormsForApp
                    }
                } yield
                    app → forms) toMap
            }
        }
    }
}
