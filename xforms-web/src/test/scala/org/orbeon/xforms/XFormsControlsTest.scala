/**
 * Copyright (C) 2026 Orbeon, Inc.
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
package org.orbeon.xforms

import org.orbeon.web.DomSupport.*
import org.scalajs.dom
import org.scalajs.dom.html
import org.scalatest.funspec.AnyFunSpecLike


class XFormsControlsTest extends AnyFunSpecLike {

  describe("XFormsControls.setCurrentValue and XFormsUI.getCurrentValue for static-readonly selection controls") {

    it("must update `.xforms-field` for `xforms-select1-appearance-full` when rendered in minimal static-readonly mode") {
      val control = dom.document.createElementT("span")
      control.className = "xforms-control xforms-select1 xforms-select1-appearance-full xforms-static"
      val field = dom.document.createElementT("span")
      field.className = "xforms-field"
      field.textContent = "Intermediate"
      control.appendChild(field)
      dom.document.body.appendChild(control)

      try {
        assert(XFormsUI.getCurrentValue(control).contains("Intermediate"))

        XFormsControls.setCurrentValue(control, "Elite Striker &amp; Keeper Academy", force = false)
        assert(field.textContent == "Elite Striker & Keeper Academy")
        assert(XFormsUI.getCurrentValue(control).contains("Elite Striker & Keeper Academy"))
      } finally {
        dom.document.body.removeChild(control)
      }
    }

    it("must toggle `.xforms-selected` / `.xforms-deselected` for `xforms-select1-appearance-full` when rendered in full static-readonly mode") {
      val control = dom.document.createElementT("span")
      control.className = "xforms-control xforms-select1 xforms-select1-appearance-full xforms-static"
      val items = dom.document.createElementT("span")
      items.className = "xforms-items"
      items.innerHTML =
        """<span class="xforms-selected" role="radio" aria-checked="true"><span class="radio"><span>Beginner</span></span></span>""" +
        """<span class="xforms-deselected" role="radio" aria-checked="false"><span class="radio"><span>Advanced</span></span></span>"""
      control.appendChild(items)
      dom.document.body.appendChild(control)

      try {
        XFormsControls.setCurrentValue(control, "Advanced", force = false)
        val itemElems = control.querySelectorAllT("[role='radio']")
        assert(itemElems.head.hasClass("xforms-deselected"))
        assert(itemElems.head.getAttribute("aria-checked") == "false")
        assert(itemElems(1).hasClass("xforms-selected"))
        assert(itemElems(1).getAttribute("aria-checked") == "true")
      } finally {
        dom.document.body.removeChild(control)
      }
    }

    it("must update `.xforms-field` for `xforms-select-appearance-full` when rendered in minimal static-readonly mode") {
      val control = dom.document.createElementT("span")
      control.className = "xforms-control xforms-select xforms-select-appearance-full xforms-static"
      val field = dom.document.createElementT("span")
      field.className = "xforms-field"
      field.textContent = "Early Drop-off"
      control.appendChild(field)
      dom.document.body.appendChild(control)

      try {
        assert(XFormsUI.getCurrentValue(control).contains("Early Drop-off"))

        XFormsControls.setCurrentValue(control, "Early Drop-off - Lunch Plan", force = false)
        assert(field.textContent == "Early Drop-off - Lunch Plan")
        assert(XFormsUI.getCurrentValue(control).contains("Early Drop-off - Lunch Plan"))
      } finally {
        dom.document.body.removeChild(control)
      }
    }
  }
}
