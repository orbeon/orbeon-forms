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
import org.scalajs.dom
import org.scalajs.dom.html

import scala.scalajs.js
import scala.scalajs.js.JSConverters._


object FormRunnerAPI extends FormRunnerEmbeddingAPI {

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

  val wizard      : FormRunnerWizardAPI.type       = FormRunnerWizardAPI
  val errorSummary: FormRunnerErrorSummaryAPI.type = FormRunnerErrorSummaryAPI
}

object FormRunnerErrorSummaryAPI extends js.Object {

  private var listeners: List[js.Function1[ErrorSummaryNavigateToErrorEvent, Any]] = Nil

  trait ErrorSummaryNavigateToErrorEvent extends js.Object {
    val validationPosition : Int
    val elementId          : String
    val repetitions        : js.Array[Int]
    val controlName        : String
    val controlLabel       : String
    val validationMessage  : String
    val validationLevel    : String
    val sectionNames       : js.Array[String]

    // Later
//    val sectionForTemplate: Option[String]
//    val repetitions: List[Int]
//    val ancestorSections: List[String]
//    val wizardPageName: String
  }

  // Private
  def _dispatch(
    _validationPosition: Int,
    _elementId         : String,
    _controlName       : String,
    _controlLabel      : String,
    _validationMessage : String,
    _validationLevel   : String,
    _sectionNames      : js.Array[String]
  ): Unit =
    listeners foreach (_(new ErrorSummaryNavigateToErrorEvent {
      val validationPosition: Int              = _validationPosition
      val elementId         : String           = _elementId
      val repetitions       : js.Array[Int]    = XFormsId.getEffectiveIdSuffixParts(_elementId).toJSArray
      val controlName       : String           = _controlName
      val controlLabel      : String           = _controlLabel
      val validationMessage : String           = _validationMessage
      val validationLevel   : String           = _validationLevel
      val sectionNames      : js.Array[String] = _sectionNames
    }))

  def addNavigateToErrorListener(fn: js.Function1[ErrorSummaryNavigateToErrorEvent, Any]): Unit =
    listeners ::= fn

  def removeNavigateToErrorListener(fn: js.Function1[ErrorSummaryNavigateToErrorEvent, Any]): Unit =
    listeners = listeners filterNot (_ eq fn)
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
