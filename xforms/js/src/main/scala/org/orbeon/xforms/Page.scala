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

@JSExportTopLevel("OrbeonPage")
object Page {

  import Private._

  @JSExport
  // Make the `lazy val` accessible to JavaScript through a function
  // We can remove the `()` once we got rid of the call in `AjaxServer.js`
  def loadingIndicator(): LoadingIndicator = Private.loadingIndicator

  def registerForm(namespacedFormId: String, form: Form): Unit = {
    formsByNamespacedFormId += namespacedFormId -> form
    formsByUuid             += form.uuid        -> form
  }

  def unregisterForm(form: Form): Unit = {
    formsByNamespacedFormId -= form.elem.id
    formsByUuid             -= form.uuid
  }

  def countInitializedForms: Int = formsByNamespacedFormId.size

  def findFormByUuid(uuid: String): Option[Form] =
    formsByUuid.get(uuid)

  @JSExport
  def getForm(namespacedFormId: String): Form =
    formsByNamespacedFormId.getOrElse(namespacedFormId, throw new IllegalArgumentException(s"form `$namespacedFormId` not found"))

  // Handle the case where the id is already prefixed. As of 2019-01-09 this can be a problem only
  // for one caller of this API. Short namespaces are removed as of Orbeon Forms 2019.2 so the potential
  // for conflict is lowered.
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

    def getControlConstructor(container: html.Element): () => Upload =
      controlConstructors find (_.predicate(container)) match {
        case Some(result) => result.controlConstructor
        case None         => throw new IllegalArgumentException(s"Can't find a relevant control for container: `${container.id}`")
      }

    def createAndRegisterNewControl() = {
      val newControl = getControlConstructor(container)()
      idToControl += container.id -> newControl
      newControl.init(container)
      newControl
    }

    idToControl.get(container.id) match {
      case None                                            => createAndRegisterNewControl()
      case Some(control) if control.container ne container => createAndRegisterNewControl()
      case Some(control)                                   => control
    }
  }

  // NOTE: This mechanism is deprecated by XBL and only used for the native `Upload` as of 2019-05-13.
  def registerControlConstructor(controlConstructor: () => Upload, predicate: html.Element => Boolean): Unit =
    controlConstructors ::= ConstructorPredicate(controlConstructor, predicate)

  private object Private {

    case class ConstructorPredicate(controlConstructor: () => Upload, predicate: html.Element => Boolean)

    lazy val loadingIndicator        = new LoadingIndicator
    var      formsByNamespacedFormId = Map[String, Form]()
    var      formsByUuid             = Map[String, Form]()
    var      controlConstructors     = List[ConstructorPredicate]()
    var      idToControl             = Map[String, Upload]()
  }
}