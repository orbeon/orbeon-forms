package org.orbeon.fr


import org.orbeon.oxf.fr.ControlOps
import org.orbeon.xforms
import org.orbeon.xforms.{$, DocumentAPI, XFormsId, XFormsXbl}
import org.scalajs.dom
import org.scalajs.dom.html
import org.scalajs.dom.html.Element

import scala.scalajs.js
import scala.scalajs.js.JSConverters._


abstract class FormRunnerEmbeddingAPI extends js.Object

// Form Runner-specific facade as we don't want to expose internal `xforms.Form` members
class FormRunnerForm(private val form: xforms.Form) extends js.Object {

  def addCallback(name: String, fn: js.Function): Unit =
    form.addCallback(name, fn)

  def removeCallback(name: String, fn: js.Function): Unit =
    form.removeCallback(name, fn)

  def isFormDataSafe(): Boolean =
    form.isFormDataSafe

  def activateProcessButton(buttonName: String): Unit = {

    def fromProcessButton =
      Option(dom.document.querySelector(s".fr-buttons .xbl-fr-process-button .fr-$buttonName-button button"))

    def fromDropTrigger =
      Option(dom.document.querySelector(s".fr-buttons .xbl-fr-drop-trigger button.fr-$buttonName-button, .fr-buttons .xbl-fr-drop-trigger li a.fr-$buttonName-button"))

    fromProcessButton
      .orElse(fromDropTrigger)
      .map(_.asInstanceOf[html.Element])
      .foreach(_.click())
  }

  def findControlsByName(controlName: String): js.Array[Element] =
    $(form.elem)
      .find(s".xforms-control[id *= '$controlName-control'], .xbl-component[id *= '$controlName-control']")
      .toArray() collect {
      // The result must be an `html.Element` already
      case e: html.Element => e
    } filter {
      // Check the id matches the requested name
      e => (e.id ne null) && (ControlOps.controlNameFromIdOpt(XFormsId.getStaticIdFromId(e.id)) contains controlName)
    } toJSArray

  def setControlValue(controlName: String, controlValue: String): Unit =
    findControlsByName(controlName).headOption.foreach {
      case e if e.classList.contains("xbl-fr-dropdown-select1") =>
        DocumentAPI.setValue(e.querySelector(".xforms-select1").asInstanceOf[html.Element], controlValue, form.elem)
      case e if XFormsXbl.isJavaScriptLifecycle(e) =>
        DocumentAPI.setValue(e, controlValue, form.elem)
      case e if XFormsXbl.isComponent(e) =>
        // NOP, as the server will reject the value anyway in this case
      case e =>
        DocumentAPI.setValue(e, controlValue, form.elem)
    }

  def activateControl(controlName: String): Unit =
    findControlsByName(controlName).headOption.foreach { e =>

      val elemToClickOpt =
        if (e.classList.contains("xforms-trigger"))
          Option(e.querySelector("button").asInstanceOf[html.Element])
        else if (e.classList.contains("xforms-input"))
          Option(e.querySelector("input").asInstanceOf[html.Element])
        else
          None

      elemToClickOpt.foreach(_.click())
    }
}