/**
  * Copyright (C) 20017 Orbeon, Inc.
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
package org.orbeon.xforms

import org.orbeon.oxf.util.StringUtils._
import org.scalajs.dom.html

import scala.scalajs.js.annotation.{JSExport, JSExportTopLevel}

@JSExportTopLevel("ORBEON.xforms.Page")
object Page {

  import Private._

  def setForm(id: String, form: Form): Unit =
    forms += id → form

  def countInitializedForms: Int = forms.size

  @JSExport
  def getForm(id: String): Form =
    forms.getOrElse(id, throw new IllegalArgumentException(s"form `$id` not found"))

  @JSExport
  def updateServerEventsInput(formId: String, serverEventsValue: String): Unit =
    getForm(formId).serverEventInput.value = if (serverEventsValue ne null) serverEventsValue else ""

  // Handle the case where the id is already prefixed. This is not great as we don't know for sure
  // whether the control starts with a short namespace, e.g. `o0`, `o1`, etc. Short namespaces are
  // disabled by default which mitigates this problem. As of 2019-01-09 this can be a problem only
  // for one caller of this API.
  @JSExport
  def namespaceIdIfNeeded(formId: String, id: String): String = {
    val ns = getForm(formId).ns
    if (id.startsWith(ns))
      id
    else
      ns + id
  }

  // See comment for `namespaceIdIfNeeded`
  @JSExport
  def deNamespaceIdIfNeeded(formId: String, id: String): String = {
    val ns = getForm(formId).ns
    if (id.startsWith(ns))
      id.substringAfter(ns)
    else
      id
  }

  @JSExport
  def getUploadControl(container: html.Element): Upload = {

    def getControlConstructor(container: html.Element): () ⇒ Upload =
      controlConstructors find (_.predicate(container)) match {
        case Some(result) ⇒ result.controlConstructor
        case None         ⇒ throw new IllegalArgumentException(s"Can't find a relevant control for container: `${container.id}`")
      }

    def createAndRegisterNewControl() = {
      val newControl = getControlConstructor(container)()
      idToControl += container.id → newControl
      newControl.init(container)
      newControl
    }

    idToControl.get(container.id) match {
      case None                                            ⇒ createAndRegisterNewControl()
      case Some(control) if control.container ne container ⇒ createAndRegisterNewControl()
      case Some(control)                                   ⇒ control
    }
  }

  // NOTE: This mechanism is deprecated by XBL and only used for the native `Upload` as of 2019-05-13.
  def registerControlConstructor(controlConstructor: () ⇒ Upload, predicate: html.Element ⇒ Boolean): Unit =
    controlConstructors ::= ConstructorPredicate(controlConstructor, predicate)

  private object Private {

    case class ConstructorPredicate(controlConstructor: () ⇒ Upload, predicate: html.Element ⇒ Boolean)

    var forms = Map[String, Form]()

    var controlConstructors: List[ConstructorPredicate] = Nil
    var idToControl = Map[String, Upload]()
  }
}