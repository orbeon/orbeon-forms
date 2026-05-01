/**
 * Copyright (C) 2020 Orbeon, Inc.
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
import scala.scalajs.js.Dynamic.{newInstance, global as g}
import scala.scalajs.js.annotation.{JSExport, JSExportTopLevel}


@JSExportTopLevel("OrbeonXFormsUiEvents")
object XFormsUiEvents {

  val orbeonLoadedEvent: YUICustomEvent =
    newInstance(g.YAHOO.util.CustomEvent)(
      "orbeonLoaded", dom.window, false, g.YAHOO.util.CustomEvent.LIST, true
    ).asInstanceOf[YUICustomEvent]

  val errorEvent: YUICustomEvent =
    newInstance(g.YAHOO.util.CustomEvent)("errorEvent").asInstanceOf[YUICustomEvent]

  val componentChangedLayoutEvent: YUICustomEvent =
    newInstance(g.YAHOO.util.CustomEvent)("componentChangedLayout").asInstanceOf[YUICustomEvent]

  val beforeValueChange: YUICustomEvent =
    newInstance(g.YAHOO.util.CustomEvent)(
      null, null, false, g.YAHOO.util.CustomEvent.FLAT
    ).asInstanceOf[YUICustomEvent]

  val valueChange: YUICustomEvent =
    newInstance(g.YAHOO.util.CustomEvent)(
      null, null, false, g.YAHOO.util.CustomEvent.FLAT
    ).asInstanceOf[YUICustomEvent]

  val afterValueChange: YUICustomEvent =
    newInstance(g.YAHOO.util.CustomEvent)(
      null, null, false, g.YAHOO.util.CustomEvent.FLAT
    ).asInstanceOf[YUICustomEvent]

  // TODO: 2026-04-29: Placeholder.js uses this
  val lhhaChangeEvent: YUICustomEvent =
    newInstance(g.YAHOO.util.CustomEvent)(
      null, null, false, g.YAHOO.util.CustomEvent.FLAT
    ).asInstanceOf[YUICustomEvent]

  // Walk up the DOM from `element` to find the first ancestor (or self) that is an XForms control,
  // an XBL component, or an XForms dialog. Returns null if none is found.
  @JSExport
  def _findParentXFormsControl(element: dom.EventTarget): html.Element = {
    var current: dom.Node = element.asInstanceOf[dom.Node]
    while (current ne null) {
      current match {
        case elem: html.Element if elem.tagName.toLowerCase == "iframe" =>
          // This might be an iframe corresponding to a legacy YUI dialog
          val dialogs = g.ORBEON.xforms.Globals.dialogs
          if (!js.isUndefined(dialogs) && (dialogs != null)) {
            val dialogsDict = dialogs.asInstanceOf[js.Dictionary[js.Dynamic]]
            for ((_, dialog) <- dialogsDict) {
              if (dialog.iframe.asInstanceOf[dom.Node] eq elem)
                return dialog.element.asInstanceOf[html.Element]
            }
          }
        case elem: html.Element =>
          if ($(elem).is(".xforms-control, .xbl-component, .xforms-dialog"))
            return elem
        case _ =>
      }
      current = current.parentNode
    }
    null
  }

  @JSExport
  def _showToolTip(
    tooltipForControl: js.Dictionary[js.Any],
    control          : html.Element,
    target           : html.Element,
    toolTipSuffix    : String,
    message          : String,
    event            : dom.MouseEvent
  ): Unit = {

    // Cases where we don't want to reuse an existing tooltip for this control
    if (g.YAHOO.lang.isObject(tooltipForControl.getOrElse(control.id, null)).asInstanceOf[Boolean]) {
      val existingTooltip = tooltipForControl(control.id).asInstanceOf[js.Dynamic]
      if (existingTooltip.orbeonTarget.asInstanceOf[js.Any] ne (target: js.Any)) {
        existingTooltip.cfg.setProperty("disabled", true)
        existingTooltip.hide()
        tooltipForControl(control.id) = null
      }
    }

    // Create tooltip if we have never "seen" this control
    if (tooltipForControl.getOrElse(control.id, null) == null) {
      if (message == "") {
        // Makes it easier for tests to check that the mouseover did run
        tooltipForControl(control.id) = null
      } else {
        val yuiTooltip = newInstance(g.YAHOO.widget.Tooltip)(control.id + toolTipSuffix, js.Dynamic.literal(
          context   = target,
          text      = message,
          showDelay = 0,
          hideDelay = 0,
          // High zIndex so tooltip is always on top, e.g. above dialogs
          zIndex    = 10000
        )).asInstanceOf[js.Dynamic]
        yuiTooltip.orbeonControl = control
        yuiTooltip.orbeonTarget  = target
        // Position the tooltip by sending the mouse move event
        yuiTooltip.onContextMouseMove.call(target, event, yuiTooltip)
        // Show the tooltip since it missed the initial mouse over
        yuiTooltip.onContextMouseOver.call(target, event, yuiTooltip)
        tooltipForControl(control.id) = yuiTooltip
      }
    }
  }
}
