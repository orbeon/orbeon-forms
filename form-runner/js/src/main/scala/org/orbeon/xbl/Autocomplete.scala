/**
 * Copyright (C) 2019 Orbeon, Inc.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version
 * 2.1 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.xbl

import org.orbeon.xbl.Select2Facade._
import org.orbeon.xforms.$
import org.orbeon.xforms.facade.{XBL, XBLCompanion}
import org.scalajs.dom.{MutationObserver, MutationObserverInit}
import org.scalajs.jquery.JQuery

import scala.scalajs.js

object Autocomplete {
  XBL.declareCompanion(name = "fr|dropdown-select1-search" , new AutocompleteCompanion())
  XBL.declareCompanion(name = "fr|databound-select1-search", new AutocompleteCompanion())
}

private class AutocompleteCompanion extends XBLCompanion {

  val DataPlaceholder = "data-placeholder"

  override def init(): Unit = {

    val container              = $(containerElem)
    val select                 = container.find("select")
    val elementWithPlaceholder = container.children(s"[$DataPlaceholder]")

    // Select2 wants the first option not to have a value when using a placeholder
    select.find("option").first().removeAttr("value")

    def initOrUpdatePlaceholder() {
        select.select2(new Select2Options {
          val placeholder: String = elementWithPlaceholder.attr(DataPlaceholder).get
        })
    }
    initOrUpdatePlaceholder()
    onAttributeChange(elementWithPlaceholder, DataPlaceholder, initOrUpdatePlaceholder)
  }

  def onAttributeChange(element: JQuery, attributeName: String, listener: () ⇒ Unit) {
      val observer = new MutationObserver((_, _) ⇒ listener())
      observer.observe(element.get(0), MutationObserverInit(
        attributes      = true,
        attributeFilter = js.Array(attributeName)
      ))
  }
}

private object Select2Facade {

  implicit def jQuery2Select2(jQuery: JQuery): JQuerySelect2 =
      jQuery.asInstanceOf[JQuerySelect2]

  @js.native
  trait JQuerySelect2 extends JQuery {
    def select2(options: Select2Options): Unit = js.native
  }

  trait Select2Options extends js.Object {
    val placeholder: String
  }
}
