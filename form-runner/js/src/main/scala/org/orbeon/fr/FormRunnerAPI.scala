/**
  * Copyright (C) 2017 Orbeon, Inc.
  *
  * This program is free software; you can redistribute it and/or modify it under the terms of the
  * GNU Lesser General Public License as published by the Free Software Foundation; either version
  *  2.1 of the License, or (at your option) any later version.
  *
  * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  * See the GNU Lesser General Public License for more details.
  *
  * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
  */
package org.orbeon.fr

import org.orbeon.oxf.fr.ControlOps
import org.orbeon.xforms.{$, DocumentAPI, Support, XFormsId}
import org.scalajs.dom.html

import scala.scalajs.js
import scala.scalajs.js.JSConverters._
import scala.scalajs.js.annotation.{JSExportAll, JSExportTopLevel}

@JSExportTopLevel("ORBEON.fr.API")
@JSExportAll
object FormRunnerAPI {

  def findControlsByName(
    controlName : String,
    formElem    : js.UndefOr[html.Form] = js.undefined
  ): js.Array[html.Element] = {

    $(Support.formElemOrDefaultForm(formElem))
      .find(s".xforms-control[id *= '$controlName-control'], .xbl-component[id *= '$controlName-control']")
      .toArray() collect {
      // The result must be an `html.Element` already
      case e: html.Element ⇒ e
    } filter {
      // Exclude template content
      e ⇒ $(e).parents(".xforms-repeat-template").length == 0
    } filter {
      // Check the id matches the requested name
      e ⇒ (e.id ne null) && (ControlOps.controlNameFromIdOpt(XFormsId.getStaticIdFromId(e.id)) contains controlName)
    } toJSArray
  }
}

@JSExportTopLevel("ORBEON.fr.API.wizard")
@JSExportAll
object FormRunnerWizardAPI {

  def focus(
    controlName   : String,
    repeatIndexes : js.UndefOr[js.Array[Int]] = js.undefined
  ): Unit =
    DocumentAPI.dispatchEvent(new js.Object {

      val targetId  = "fr-view-component"
      val eventName = "fr-wizard-focus"

      val properties = new js.Object {
        val `fr-control-name`  : String = controlName
        val `fr-repeat-indexes`: String = repeatIndexes map (_.mkString(" ")) getOrElse ""
      }
    })

}
