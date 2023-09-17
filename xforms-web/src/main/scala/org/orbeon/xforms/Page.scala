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

import org.orbeon.xforms
import org.orbeon.xforms.Constants.FormClass
import org.scalajs.dom.html

import scala.scalajs.js
import scala.scalajs.js.annotation.{JSExport, JSExportTopLevel}
import scala.scalajs.js.|


@JSExportTopLevel("OrbeonPage")
object Page {

  import Private._

  @JSExport
  // Make the `lazy val` accessible to JavaScript through a function
  // We can remove the `()` once we got rid of the call in `AjaxServer.js`
  def loadingIndicator(): LoadingIndicator = Private.loadingIndicator

  def registerForm(namespacedFormId: String, form: xforms.Form): Unit = {
    formsByNamespacedFormId += namespacedFormId -> form
    formsByUuid             += form.uuid        -> form
  }

  def unregisterForm(form: xforms.Form): Unit = {
    formsByNamespacedFormId -= form.elem.id
    formsByUuid             -= form.uuid
  }

  def countInitializedForms: Int = formsByNamespacedFormId.size

  def findFormByUuid(uuid: String): Option[xforms.Form] =
    formsByUuid.get(uuid)

  // 2023-08-15: 4 callers in JavaScript, rest in Scala
  @JSExport
  def getXFormsFormFromNamespacedIdOrThrow(namespacedFormId: String): xforms.Form =
    findXFormsFormFromNamespacedId(namespacedFormId).getOrElse(throw new IllegalArgumentException(s"form `$namespacedFormId` not found"))

  def findXFormsForm(elemOrNamespacedId: js.UndefOr[html.Element | String]): Option[xforms.Form] =
    (elemOrNamespacedId: Any) match {
      case namespacedId: String => Page.findXFormsFormFromNamespacedId(namespacedId)
      case elem: html.Element   => Page.findXFormsFormFromHtmlElemOrDefault(elem)
      case _                    => Page.findXFormsFormFromHtmlElemOrDefault(js.undefined)
    }

  def findXFormsFormFromNamespacedId(namespacedFormId: String): Option[xforms.Form] =
    formsByNamespacedFormId.get(namespacedFormId)

  def findXFormsFormFromHtmlElem(elem: html.Element): Option[xforms.Form] =
    findAncestorOrSelfHtmlFormFromHtmlElem(elem).flatMap(form => Option(form.id)).flatMap(findXFormsFormFromNamespacedId)

  def findXFormsFormFromHtmlElemOrDefault(elem: js.UndefOr[html.Element]): Option[xforms.Form] =
    findAncestorOrSelfHtmlFormFromHtmlElemOrDefault(elem).flatMap(f => findXFormsFormFromNamespacedId(f.id))

  // 2023-08-15: 4 callers in `xforms.js`, rest in Scala
  @JSExport
  def getXFormsFormFromHtmlElemOrThrow(elem: html.Element): xforms.Form =
    findXFormsFormFromHtmlElem(elem).getOrElse(throw new IllegalStateException("form not found"))

  // 2023-08-15: 3 callers in `xforms.js` only
  // Caller doesn't pass `undefined` so we don't need to handle it. It is also expected that the form will be found.
  @JSExport
  def getAncestorOrSelfHtmlFormFromHtmlElemOrThrow(elem: html.Element): html.Form =
    findAncestorOrSelfHtmlFormFromHtmlElem(elem).getOrElse(throw new IllegalStateException("form not found"))

  def findAncestorOrSelfHtmlFormFromHtmlElemOrDefault(elem: js.UndefOr[html.Element]): Option[html.Form] =
    elem.toOption match {
      case Some(e) => findAncestorOrSelfHtmlFormFromHtmlElem(e)
      case None    => Support.allFormElems.headOption
    }

  // Try to optimize the search for the `html.Form`, and also try to ensure that this is an Orbeon Forms form.
  // `None` means no matching element was not found.
  private def findAncestorOrSelfHtmlFormFromHtmlElem(elem: html.Element): Option[html.Form] = {

    val rawHtmlFormOpt =
      elem match {
        case formElem: html.Form =>
          Some(formElem)
        case elem: html.Element =>
          elem.asInstanceOf[js.Dynamic].form match {
            case formElem: html.Form =>
              Some(formElem)
            case _ =>
              Option(elem.asInstanceOf[js.Dynamic].closest(s"form.$FormClass[id]").asInstanceOf[html.Form])
          }
      }

    rawHtmlFormOpt.filter(Support.isXFormsFormElem)
  }

  // Handle the case where the id is already prefixed. As of 2019-01-09 this can be a problem only
  // for one caller of this API. Short namespaces are removed as of Orbeon Forms 2019.2 so the potential
  // for conflict is lowered.
  @JSExport
  def namespaceIdIfNeeded(namespacedFormId: String, id: String): String =
    getXFormsFormFromNamespacedIdOrThrow(namespacedFormId).namespaceIdIfNeeded(id)

  @JSExport
  def deNamespaceIdIfNeeded(namespacedFormId: String, id: String): String =
    getXFormsFormFromNamespacedIdOrThrow(namespacedFormId).deNamespaceIdIfNeeded(id)

  @JSExport
  def getUploadControl(container: html.Element): Upload = {

    def getControlConstructor(container: html.Element): () => Upload =
      controlConstructors find (_.predicate(container)) match {
        case Some(result) => result.controlConstructor
        case None         => throw new IllegalArgumentException(s"Can't find a relevant control for container: `${container.id}`")
      }

    def createAndRegisterNewControl(): Upload = {
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
