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

import org.orbeon.xforms.facade.Control
import org.scalajs.dom
import org.scalajs.dom.html
import org.scalajs.dom.raw.{Attr, MutationObserver, MutationRecord}

import scala.scalajs.js
import scala.scalajs.js.annotation.{JSExportAll, JSExportTopLevel}

@JSExportTopLevel("ORBEON.xforms.Page") // used by `xforms.js`, `AjaxServer.js`, `Calendar.coffee`
@JSExportAll
object Page {

  case class ConstructorPredicate(controlConstructor: () ⇒ Control, predicate: html.Element ⇒ Boolean)

  private var forms = Map[String, Form]()

  private var controlConstructors: List[ConstructorPredicate] = Nil
  private var idToControl = Map[String, Control]()

  def getForm(id: String): Form =
    forms.get(id) match {
      case Some(form) ⇒
        form
      case None ⇒
        val newForm = new Form(dom.document.getElementById(id).asInstanceOf[html.Element])
        forms += id → newForm
        newForm
    }

  private val LangAttr = "lang"

  private def langElement: Option[dom.Element] = {

    val langElements = Iterator(
      () ⇒ dom.document.documentElement,
      () ⇒ dom.document.querySelector(".orbeon-portlet-div[lang]")
    )
    langElements
      .map(_.apply())
      .find(_.hasAttribute(LangAttr))
  }

  // noinspection AccessorLikeMethodIsEmptyParen
  // Return the language for the page, defaulting to English if none is found
  // See also https://github.com/orbeon/orbeon-forms/issues/3787
  def getLang(): String = {
    langElement
      .map(_.getAttribute(LangAttr).substring(0, 2))
      .getOrElse("en")
  }

  def onLangChange(listener: String ⇒ Unit): Unit = {
    langElement.foreach { element ⇒
      val callback = (a: js.Array[MutationRecord], b: MutationObserver) ⇒
        listener(getLang())
      new MutationObserver(callback).observe(
        target   = element,
        options = dom.MutationObserverInit(
          attributes      = true,
          attributeFilter = js.Array("lang")
        )
      )
    }
  }

  // Create or return a control object corresponding to the provided container. Each control is inside a given
  // form, so getControl() could be a method of a form, but since we can given a container or control id determine
  // the control object without knowing the form, this method is defined at the Page level which makes it easier
  // to use for a caller who doesn't necessarily have a reference to the form object.
  def getControl(container: html.Element): Control = {

    def getControlConstructor(container: html.Element): () ⇒ Control =
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

  // Register a control constructor (such as tree, input...). This is expected to be called by the control itself when loaded.
  def registerControlConstructor(controlConstructor: () ⇒ Control, predicate: html.Element ⇒ Boolean): Unit =
    controlConstructors ::= ConstructorPredicate(controlConstructor, predicate)
}
