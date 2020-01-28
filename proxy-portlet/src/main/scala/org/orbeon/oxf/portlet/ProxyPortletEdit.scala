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

import javax.portlet._

import scala.xml.Elem

// Preference editor for the proxy portlet
trait ProxyPortletEdit extends GenericPortlet {

  import OrbeonProxyPortlet._
  implicit def portletContext = getPortletContext

  case class NameLabel(name: String, label: String, publicName: String)

  object NameLabel {
    def apply(name: String, label: String, publicName: Option[String] = None): NameLabel =
      NameLabel(name, label, publicName getOrElse name)
  }

  sealed trait ControlType { def render(pref: Pref, value: String): Elem }

  case object InputControl extends ControlType {
    def render(pref: Pref, value: String) =
      <label>{pref.nameLabel.label}
        <input type="text" name={pref.nameLabel.name} value={value}/>
      </label>
  }

  case object CheckboxControl extends ControlType {
    def render(pref: Pref, value: String) =
      <label>{pref.nameLabel.label}
        {
          val template = <input type="checkbox" name={pref.nameLabel.name} value="true" checked="checked"/>
          if (value == "true") template else template.copy(attributes = template.attributes.remove("checked"))
        }
      </label>
  }

  case class SelectControl(nameLabels: List[NameLabel]) extends ControlType {
    def render(pref: Pref, value: String) =
      <label>{pref.nameLabel.label}
        <select name={pref.nameLabel.name}>
          {
            for (nameLabel <- nameLabels) yield {
              val template = <option value={nameLabel.name} selected="selected">{nameLabel.label}</option>
              if (value == nameLabel.name) template else template.copy(attributes = template.attributes.remove("selected"))
            }
          }
        </select>
      </label>
  }

  sealed trait Pref { val tpe: ControlType; val nameLabel: NameLabel }

  val PageNameLabels = List(
    NameLabel("home",    "Home Page"),
    NameLabel("summary", "Summary Page"),
    NameLabel("new",     "New Page")
  )

  case object FormRunnerURL                 extends Pref { val tpe = InputControl;                  val nameLabel = NameLabel("form-runner-url",                  "Form Runner URL") }
  case object EnableURLParameters           extends Pref { val tpe = CheckboxControl;               val nameLabel = NameLabel("enable-url-parameters",            "Enable form selection via URL parameters") }
  case object EnablePublicRenderParameters  extends Pref { val tpe = CheckboxControl;               val nameLabel = NameLabel("enable-public-render-parameters",  "Enable form selection via public render parameters") }
  case object EnableSessionParameters       extends Pref { val tpe = CheckboxControl;               val nameLabel = NameLabel("enable-session-parameters",        "Enable form selection via session parameters") }
  case object AppName                       extends Pref { val tpe = InputControl;                  val nameLabel = NameLabel("app-name",                         "Form Runner app name",    Some("orbeon-app")) }
  case object FormName                      extends Pref { val tpe = InputControl;                  val nameLabel = NameLabel("form-name",                        "Form Runner form name",   Some("orbeon-form")) }
  case object DocumentId                    extends Pref { val tpe = InputControl;                  val nameLabel = NameLabel("document-id",                      "Form Runner document id", Some("orbeon-document")) }
  case object ReadOnly                      extends Pref { val tpe = CheckboxControl;               val nameLabel = NameLabel("read-only",                        "Readonly access") }
  case object SendLiferayLanguage           extends Pref { val tpe = CheckboxControl;               val nameLabel = NameLabel("send-liferay-language",            "Send Liferay language") }
  case object SendLiferayUser               extends Pref { val tpe = CheckboxControl;               val nameLabel = NameLabel("send-liferay-user",                "Send Liferay user") }
  case object Page                          extends Pref { val tpe = SelectControl(PageNameLabels); val nameLabel = NameLabel("action",                           "Form Runner page",        Some("orbeon-page")) }

  val AllPreferences = List(
    Page,
    FormRunnerURL,
    AppName,
    FormName,
    ReadOnly,
    SendLiferayLanguage,
    SendLiferayUser
  )

  // Return the value of the preference if set, otherwise the value of the initialization parameter
  // NOTE: We should be able to use portlet.xml portlet-preferences/preference, but somehow this doesn't work properly
  def getPreference(request: PortletRequest, pref: Pref) =
    request.getPreferences.getValue(pref.nameLabel.name, getPortletConfig.getInitParameter(pref.nameLabel.name))

  def getBooleanPreference(request: PortletRequest, pref: Pref) =
    getPreference(request, pref) == "true"

  // Very simple preferences editor
  override def doEdit(request: RenderRequest, response: RenderResponse): Unit =
    withRootException("edit render", new PortletException(_)) {

      response setTitle "Orbeon Forms Preferences"
      response.getWriter write
        <div>
          <style>
            .orbeon-pref-form label {{ display: block; clear:both; font-weight: bold }}
            .orbeon-pref-form input[type=text] {{ display: block; width: 100%; margin-bottom: 10px }}
            .orbeon-pref-form input[type=checkbox] {{ float: left; margin-right: 6px; margin-bottom: 10px }}
            .orbeon-pref-form select {{ display: block; width: auto; margin-bottom: 10px }}
            .orbeon-pref-form hr {{ clear: both }}
            .orbeon-pref-form fieldset {{ padding-bottom: 0 }}
          </style>
          <form action={response.createActionURL.toString} method="post" class="orbeon-pref-form">
            <fieldset>
              <legend>Form Runner Portlet Settings</legend>
              {
                for (pref <- AllPreferences) yield
                  pref.tpe.render(pref, getPreference(request, pref))
              }
              <hr/>
              <div>
                <button name="save"   value="save">Save</button>
                <button name="cancel" value="cancel">Cancel</button>
              </div>
            </fieldset>
          </form>
        </div>.toString
    }

  // Handle preferences editor save/cancel
  def doEditAction(request: ActionRequest, response: ActionResponse): Unit =
    withRootException("view action", new PortletException(_)) {
      request.getParameter("save") match {
        case "save" =>
          def setPreference(pref: Pref, value: String) =
            request.getPreferences.setValue(pref.nameLabel.name, value)

          for (pref <- AllPreferences)
            setPreference(pref, request.getParameter(pref.nameLabel.name))

          request.getPreferences.store()
        case _ =>
      }

      // Go back to view mode
      response.setPortletMode(PortletMode.VIEW)
    }
}
