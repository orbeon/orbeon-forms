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

import org.orbeon.xforms.facade.BootstrapTip
import org.scalajs.dom
import org.scalajs.dom.html
import org.scalatest.funspec.AnyFunSpecLike

import scala.scalajs.js
import scala.scalajs.js.Dynamic.global as g


class TooltipTest extends AnyFunSpecLike {

  private class MockTooltipInstance(val target: dom.Element, val config: js.Dynamic) extends js.Object {
    var shown    : Boolean = false
    var enabled  : Boolean = true
    var disposed : Boolean = false
    var title    : String  = Option(config.title).map(_.toString).getOrElse("")

    def show(): Unit = {
      shown = true
      val tipId = s"tooltip-${target.id}"
      target.setAttribute("aria-describedby", tipId)
      val tipElem = dom.document.createElement("div").asInstanceOf[html.Div]
      tipElem.id = tipId
      tipElem.className = "tooltip show"
      val innerElem = dom.document.createElement("div").asInstanceOf[html.Div]
      innerElem.className = "tooltip-inner"
      innerElem.innerHTML = title
      tipElem.appendChild(innerElem)
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

    def enable(): Unit =
      enabled = true

    def disable(): Unit =
      enabled = false

    def dispose(): Unit = {
      disposed = true
      hide()
      val instances = g.__mockTooltipInstances.asInstanceOf[js.Dictionary[MockTooltipInstance]]
      instances -= target.id
    }

    def setContent(content: js.Dictionary[js.Any]): Unit =
      content.get(".tooltip-inner").foreach { newTitle =>
        title = if (newTitle != null) newTitle.toString else ""
        val tipId = target.getAttribute("aria-describedby")
        if (tipId != null) {
          Option(dom.document.getElementById(tipId)).foreach { tipElem =>
            Option(tipElem.querySelector(".tooltip-inner")).foreach(_.innerHTML = title)
          }
        }
      }
  }

  private def setupBootstrapMock(): Unit = {
    g.__mockTooltipInstances = js.Dictionary.empty[MockTooltipInstance]

    val mockTooltipCtor = js.Dynamic.literal(
      getOrCreateInstance = { (element: dom.Element, configuration: js.Object) =>
        val instances = g.__mockTooltipInstances.asInstanceOf[js.Dictionary[MockTooltipInstance]]
        instances.getOrElseUpdate(element.id, new MockTooltipInstance(element, configuration.asInstanceOf[js.Dynamic]))
      }: js.Function2[dom.Element, js.Object, MockTooltipInstance],
      getInstance = { (element: dom.Element) =>
        val instances = g.__mockTooltipInstances.asInstanceOf[js.Dictionary[MockTooltipInstance]]
        instances.get(element.id).orNull
      }: js.Function1[dom.Element, MockTooltipInstance]
    )

    if (js.isUndefined(g.window.ORBEON))
      g.window.ORBEON = new js.Object
    val orbeon = g.window.ORBEON.asInstanceOf[js.Dynamic]
    orbeon.bootstrap = js.Dynamic.literal(
      Tooltip = mockTooltipCtor,
      Popover = js.Dynamic.literal()
    )
  }

  private def getMockInstance(element: dom.Element): Option[MockTooltipInstance] = {
    val instances = g.__mockTooltipInstances.asInstanceOf[js.Dictionary[MockTooltipInstance]]
    instances.get(element.id)
  }

  describe("XFormsUiEvents.showToolTip") {

    it("must set dictionary entry to null when message is empty") {
      setupBootstrapMock()
      val dict = js.Dictionary.empty[BootstrapTip]
      val control = dom.document.createElement("div").asInstanceOf[html.Div]
      control.id = "my-control"
      val target = dom.document.createElement("input").asInstanceOf[html.Input]
      target.id = "my-target"
      control.appendChild(target)
      dom.document.body.appendChild(control)

      XFormsUiEvents.showToolTip(dict, control, target, "")

      assert(dict.contains("my-control"))
      assert(dict("my-control") == null)
      assert(getMockInstance(target).isEmpty)
    }

    it("must create, show, and register tooltip when message is non-empty") {
      setupBootstrapMock()
      val dict = js.Dictionary.empty[BootstrapTip]
      val control = dom.document.createElement("div").asInstanceOf[html.Div]
      control.id = "ctrl-1"
      val target = dom.document.createElement("input").asInstanceOf[html.Input]
      target.id = "target-1"
      control.appendChild(target)
      dom.document.body.appendChild(control)

      XFormsUiEvents.showToolTip(dict, control, target, "Hello Tooltip")

      val tip = dict("ctrl-1")
      assert(tip != null)
      assert(tip.target eq target)

      val mock = getMockInstance(target).get
      assert(mock.shown)
      assert(mock.title == "Hello Tooltip")
      assert(tip.element != null)
      assert(tip.element.classList.contains("tooltip"))
    }

    it("must update message and re-show when hovered again on same target") {
      setupBootstrapMock()
      val dict = js.Dictionary.empty[BootstrapTip]
      val control = dom.document.createElement("div").asInstanceOf[html.Div]
      control.id = "ctrl-2"
      val target = dom.document.createElement("input").asInstanceOf[html.Input]
      target.id = "target-2"
      control.appendChild(target)
      dom.document.body.appendChild(control)

      XFormsUiEvents.showToolTip(dict, control, target, "Initial message")
      val mock = getMockInstance(target).get
      assert(mock.title == "Initial message")

      // Mouse leaves (simulating hide)
      mock.hide()
      assert(! mock.shown)

      // Hover again with updated message
      XFormsUiEvents.showToolTip(dict, control, target, "Updated message")
      assert(mock.shown)
      assert(mock.title == "Updated message")
    }

    it("must destroy old tooltip and create new one when target changes") {
      setupBootstrapMock()
      val dict = js.Dictionary.empty[BootstrapTip]
      val control = dom.document.createElement("div").asInstanceOf[html.Div]
      control.id = "ctrl-3"
      val target1 = dom.document.createElement("input").asInstanceOf[html.Input]
      target1.id = "target-3a"
      val target2 = dom.document.createElement("span").asInstanceOf[html.Span]
      target2.id = "target-3b"
      control.appendChild(target1)
      control.appendChild(target2)
      dom.document.body.appendChild(control)

      XFormsUiEvents.showToolTip(dict, control, target1, "Tip on 1")
      val mock1 = getMockInstance(target1).get
      assert(mock1.shown)

      // Now hover on target2
      XFormsUiEvents.showToolTip(dict, control, target2, "Tip on 2")
      assert(mock1.disposed)

      val tip2 = dict("ctrl-3")
      assert(tip2.target eq target2)
      val mock2 = getMockInstance(target2).get
      assert(mock2.shown)
      assert(mock2.title == "Tip on 2")
    }

    it("must destroy existing tooltip and set dictionary entry to null if message becomes empty") {
      setupBootstrapMock()
      val dict = js.Dictionary.empty[BootstrapTip]
      val control = dom.document.createElement("div").asInstanceOf[html.Div]
      control.id = "ctrl-4"
      val target = dom.document.createElement("input").asInstanceOf[html.Input]
      target.id = "target-4"
      control.appendChild(target)
      dom.document.body.appendChild(control)

      XFormsUiEvents.showToolTip(dict, control, target, "Some tip")
      val mock = getMockInstance(target).get
      assert(mock.shown)

      // Message cleared
      XFormsUiEvents.showToolTip(dict, control, target, "")
      assert(mock.disposed)
      assert(dict("ctrl-4") == null)
    }
  }

  describe("XFormsUI tooltip updates") {

    it("must disable and hide tooltip when message is set to empty") {
      setupBootstrapMock()
      Globals.reset()
      val control = dom.document.createElement("div").asInstanceOf[html.Div]
      control.id = "ctrl-ui-1"
      val target = dom.document.createElement("input").asInstanceOf[html.Input]
      target.id = "target-ui-1"
      control.appendChild(target)
      dom.document.body.appendChild(control)

      XFormsUiEvents.showToolTip(Globals.hintTooltipForControl, control, target, "Hint text")
      val mock = getMockInstance(target).get
      assert(mock.enabled)
      assert(mock.shown)

      // Set hint message to empty
      XFormsUI.setHintMessage(control, "")
      assert(! mock.enabled)
      assert(! mock.shown)

      // Set hint message to new text
      XFormsUI.setHintMessage(control, "New hint text")
      assert(mock.enabled)
      assert(mock.title == "New hint text")
    }

    it("must disable and hide alert tooltip when constraint level is valid (empty)") {
      setupBootstrapMock()
      Globals.reset()
      val control = dom.document.createElement("div").asInstanceOf[html.Div]
      control.id = "ctrl-ui-2"
      val alertElem = dom.document.createElement("div").asInstanceOf[html.Div]
      alertElem.id = "ctrl-ui-2-alert"
      alertElem.className = "xforms-alert xforms-active"
      control.appendChild(alertElem)
      dom.document.body.appendChild(control)

      XFormsUiEvents.showToolTip(Globals.alertTooltipForControl, control, alertElem, "Error message")
      val mock = getMockInstance(alertElem).get
      assert(mock.enabled)
      assert(mock.shown)

      // Control becomes valid (level = "")
      XFormsUI.setConstraintLevel(control, "")
      assert(! mock.enabled)
      assert(! mock.shown)

      // Control becomes invalid again
      XFormsUI.setConstraintLevel(control, "error")
      assert(mock.enabled)
    }
  }
}
