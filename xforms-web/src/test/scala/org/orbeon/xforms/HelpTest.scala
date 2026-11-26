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

import org.scalajs.dom
import org.scalajs.dom.html
import org.scalatest.funspec.AnyFunSpecLike

import scala.scalajs.js
import scala.scalajs.js.Dynamic.global as g


class HelpTest extends AnyFunSpecLike {

  private class MockPopoverInstance(val target: dom.Element, val config: js.Dynamic) extends js.Object {
    var shown   : Boolean = false
    var disposed: Boolean = false
    val title   : String  = Option(config.title).map(_.toString).getOrElse("")
    val content : String  = Option(config.content).map(_.toString).getOrElse("")

    def show(): Unit = {
      shown = true
      val tipId = s"popover-${target.id}"
      target.setAttribute("aria-describedby", tipId)
      val tipElem = dom.document.createElement("div").asInstanceOf[html.Div]
      tipElem.id = tipId
      tipElem.className = "popover show"
      if (title.nonEmpty) {
        val headerElem = dom.document.createElement("h3").asInstanceOf[html.Heading]
        headerElem.className = "popover-header"
        headerElem.innerHTML = title
        tipElem.appendChild(headerElem)
      }
      val bodyElem = dom.document.createElement("div").asInstanceOf[html.Div]
      bodyElem.className = "popover-body"
      bodyElem.innerHTML = content
      tipElem.appendChild(bodyElem)
      dom.document.body.appendChild(tipElem)
    }

    def hide(): Unit = {
      shown = false
      val tipId = target.getAttribute("aria-describedby")
      if (tipId != null) {
        target.removeAttribute("aria-describedby")
        Option(dom.document.getElementById(tipId)).foreach(el => el.parentNode.removeChild(el))
      }
    }

    def dispose(): Unit = {
      disposed = true
      hide()
      val instances = g.__mockPopoverInstances.asInstanceOf[js.Dictionary[MockPopoverInstance]]
      instances -= target.id
    }
  }

  private def setupBootstrapPopoverMock(): Unit = {
    g.__mockPopoverInstances = js.Dictionary.empty[MockPopoverInstance]

    val mockPopoverCtor = js.Dynamic.literal(
      getOrCreateInstance = { (element: dom.Element, configuration: js.Object) =>
        val instances = g.__mockPopoverInstances.asInstanceOf[js.Dictionary[MockPopoverInstance]]
        instances.getOrElseUpdate(element.id, new MockPopoverInstance(element, configuration.asInstanceOf[js.Dynamic]))
      }: js.Function2[dom.Element, js.Object, MockPopoverInstance],
      getInstance = { (element: dom.Element) =>
        val instances = g.__mockPopoverInstances.asInstanceOf[js.Dictionary[MockPopoverInstance]]
        instances.get(element.id).orNull
      }: js.Function1[dom.Element, MockPopoverInstance]
    )

    if (js.isUndefined(g.window.ORBEON))
      g.window.ORBEON = new js.Object
    val orbeon = g.window.ORBEON.asInstanceOf[js.Dynamic]
    orbeon.bootstrap = js.Dynamic.literal(
      Tooltip = js.Dynamic.literal(),
      Popover = mockPopoverCtor
    )
  }

  private def getMockPopover(element: dom.Element): Option[MockPopoverInstance] = {
    val instances = g.__mockPopoverInstances.asInstanceOf[js.Dictionary[MockPopoverInstance]]
    instances.get(element.id)
  }

  describe("External LHHA help popover header") {

    it("must resolve the associated external label via data-orbeon-label-id and render popover-header") {
      setupBootstrapPopoverMock()

      val th = dom.document.createElement("div").asInstanceOf[html.Div]
      th.className = "fr-grid-th"

      val helpBtn = dom.document.createElement("button").asInstanceOf[html.Button]
      helpBtn.id = "custom-properties-grid≡xf-6654"
      helpBtn.className = "xforms-control xforms-help"
      helpBtn.setAttribute("data-orbeon-label-id", "custom-properties-grid≡xf-6655")
      helpBtn.innerHTML = "Optional property profiles."

      val labelSpan = dom.document.createElement("span").asInstanceOf[html.Span]
      labelSpan.id = "custom-properties-grid≡xf-6655"
      labelSpan.className = "xforms-control xforms-label"
      labelSpan.innerHTML = "Profiles"

      th.appendChild(helpBtn)
      th.appendChild(labelSpan)
      dom.document.body.appendChild(th)

      try {
        assert(XFormsUI.getLabelMessage(helpBtn) == "Profiles")

        Help.showHelp(helpBtn)

        val mock = getMockPopover(helpBtn).get
        assert(mock.shown)
        assert(mock.title == "Profiles")
        assert(mock.content == "Optional property profiles.")

        val popoverElem = dom.document.querySelector(".xforms-help-popover")
        assert(popoverElem != null)
        assert(popoverElem.querySelector(".popover-header").innerHTML == "Profiles")

        // Second invocation toggles the popover closed
        Help.showHelp(helpBtn)
        assert(mock.disposed)
        assert(dom.document.querySelector(".xforms-help-popover") == null)

        // Dynamic label update (e.g. language switch) is picked up without updating data-orbeon-label-id
        labelSpan.innerHTML = "Profils"
        Help.showHelp(helpBtn)
        assert(getMockPopover(helpBtn).get.title == "Profils")

        Help.showHelp(helpBtn)
      } finally {
        dom.document.body.removeChild(th)
      }
    }

    it("must not cause setRelevant on external help to hide the associated external label") {
      val th = dom.document.createElement("div").asInstanceOf[html.Div]

      val helpBtn = dom.document.createElement("button").asInstanceOf[html.Button]
      helpBtn.id = "grid≡help-1"
      helpBtn.className = "xforms-control xforms-help"
      helpBtn.setAttribute("data-orbeon-label-id", "grid≡label-1")
      helpBtn.innerHTML = "Help text"

      val labelSpan = dom.document.createElement("span").asInstanceOf[html.Span]
      labelSpan.id = "grid≡label-1"
      labelSpan.className = "xforms-control xforms-label"
      labelSpan.innerHTML = "Column Label"

      th.appendChild(helpBtn)
      th.appendChild(labelSpan)
      dom.document.body.appendChild(th)

      try {
        XFormsUI.setRelevant(helpBtn, isRelevant = false)
        assert(helpBtn.classList.contains("xforms-disabled"))
        assert(! labelSpan.classList.contains("xforms-disabled"))
      } finally {
        dom.document.body.removeChild(th)
      }
    }
  }
}
