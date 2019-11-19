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
package org.orbeon.xforms

import org.scalajs.dom
import org.scalajs.dom.html

import scala.scalajs.js


object Support {

  def formElemOrDefaultForm(formElem: js.UndefOr[html.Form]): html.Form =
    formElem getOrElse getFirstForm

  def getFirstForm: html.Form =
    $(dom.document.forms).filter(".xforms-form")(0).asInstanceOf[html.Form]

  def adjustIdNamespace(
    formElem : js.UndefOr[html.Form],
    targetId : String
  ): (html.Element, String) = {

    val form   = Support.formElemOrDefaultForm(formElem)
    val formId = form.id

    // See comment on `namespaceIdIfNeeded`
    form → Page.namespaceIdIfNeeded(formId, targetId)
  }
}
