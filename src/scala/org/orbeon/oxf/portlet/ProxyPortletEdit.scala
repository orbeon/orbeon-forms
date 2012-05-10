/**
 * Copyright (C) 2012 Orbeon, Inc.
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
package org.orbeon.oxf.portlet

import org.orbeon.oxf.util.ScalaUtils._
import javax.portlet._
import org.apache.log4j.Logger

// Preference editor for the proxy portlet
trait ProxyPortletEdit extends GenericPortlet {

    implicit def logger: Logger

    sealed trait Preference { val name: String }
    case object FormRunnerURL extends Preference { val name = "form-runner-url" }
    case object AppName       extends Preference { val name = "app-name" }
    case object FormName      extends Preference { val name = "form-name" }
    case object Action        extends Preference { val name = "action" }
    case object ReadOnly      extends Preference { val name = "read-only" }

    private val PreferenceLabels = Map(
        FormRunnerURL → "Form Runner URL",
        AppName       → "Form Runner app name",
        FormName      → "Form Runner form name",
        Action        → "Form Runner action",
        ReadOnly      → "Read-Only access"
    )

    // Return the value of the preference if set, otherwise the value of the initialization parameter
    // NOTE: We should be able to use portlet.xml portlet-preferences/preference, but somehow this doesn't work properly
    def getPreference(request: PortletRequest, pref: Preference) =
        request.getPreferences.getValue(pref.name, getPortletConfig.getInitParameter(pref.name))

    // Very simple preferences editor
    override def doEdit(request: RenderRequest, response: RenderResponse): Unit =
        withRootException("edit render", new PortletException(_)) {

            response setTitle "Orbeon Forms Preferences"
            response.getWriter write
                <div>
                    <style>
                        .orbeon-pref-form label {{display: block; font-weight: bold}}
                        .orbeon-pref-form input {{display: block; width: 20em }}
                    </style>
                    <form action={response.createActionURL.toString} method="post" class="orbeon-pref-form">
                        {
                            for ((pref, label) ← PreferenceLabels) yield
                                <label>{label}: <input name={pref.name} value={getPreference(request, pref)}/></label>
                        }
                        <hr/>
                        <p>
                            <button name="save" value="save">Save</button>
                            <button name="cancel" value="cancel">Cancel</button>
                        </p>
                    </form>
                </div>.toString
        }

    // Handle preferences editor save/cancel
    def doEditAction(request: ActionRequest, response: ActionResponse): Unit =
        withRootException("view action", new PortletException(_)) {
            request.getParameter("save") match {
                case "save" ⇒
                    def setPreference(pref: Preference, value: String) = request.getPreferences.setValue(pref.name, value)

                    for ((pref, label) ← PreferenceLabels)
                        setPreference(pref, request.getParameter(pref.name))

                    request.getPreferences.store()
                case _ ⇒
            }

            // Go back to view mode
            response.setPortletMode(PortletMode.VIEW)
        }
}
