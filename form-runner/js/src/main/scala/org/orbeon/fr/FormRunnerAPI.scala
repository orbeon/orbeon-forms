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

import org.orbeon.oxf.fr.{ControlOps, Names}
import org.orbeon.xforms._
import org.scalajs.dom.html

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future, Promise}
import scala.scalajs.js
import scala.scalajs.js.JSConverters._

object FormRunnerAPI extends js.Object {

  def findControlsByName(
    controlName : String,
    formElem    : js.UndefOr[html.Form] = js.undefined
  ): js.Array[html.Element] = {

    $(Support.formElemOrDefaultForm(formElem))
      .find(s".xforms-control[id *= '$controlName-control'], .xbl-component[id *= '$controlName-control']")
      .toArray() collect {
      // The result must be an `html.Element` already
      case e: html.Element => e
    } filter {
      // Check the id matches the requested name
      e => (e.id ne null) && (ControlOps.controlNameFromIdOpt(XFormsId.getStaticIdFromId(e.id)) contains controlName)
    } toJSArray
  }

  def isFormDataSafe(
    formElem    : js.UndefOr[html.Form] = js.undefined
  ): Boolean =
    Page.getForm(Support.formElemOrDefaultForm(formElem).id).isFormDataSafe

  val wizard: FormRunnerWizardAPI.type = FormRunnerWizardAPI
}

object FormRunnerWizardAPI extends js.Object {

  def focus(
    controlName   : String,
    repeatIndexes : js.UndefOr[js.Array[Int]] = js.undefined
  ): Unit = {

    // Separate variable due to type inference fail when put inline below
    val indexesString = repeatIndexes map (_.mkString(" ")) getOrElse ""

    AjaxClient.fireEvent(
      AjaxEvent(
        eventName  = "fr-wizard-focus",
        targetId   = Names.ViewComponent,
        properties = Map(
          "fr-control-name"   -> controlName,
          "fr-repeat-indexes" -> indexesString
        )
      )
    )
  }
}
