package org.orbeon.fr


import org.orbeon.oxf.fr.ControlOps
import org.orbeon.xforms
import org.orbeon.xforms.{$, XFormsId}
import org.scalajs.dom
import org.scalajs.dom.html
import org.scalajs.dom.html.Element

import scala.scalajs.js


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
}
