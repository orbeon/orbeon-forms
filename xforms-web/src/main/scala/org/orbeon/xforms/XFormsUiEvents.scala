package org.orbeon.xforms

import org.orbeon.web.DomSupport.DomElemOps
import org.scalajs.dom
import org.scalajs.dom.html

import scala.scalajs.js
import scala.scalajs.js.Dynamic.{newInstance, global as g}


object XFormsUiEvents {

  // Public API
  lazy val orbeonLoadedEvent: YUICustomEvent =
    newInstance(g.YAHOO.util.CustomEvent)(
      "orbeonLoaded", dom.window, false, g.YAHOO.util.CustomEvent.LIST, true
    ).asInstanceOf[YUICustomEvent]

  // Public API
  lazy val errorEvent: YUICustomEvent =
    newInstance(g.YAHOO.util.CustomEvent)("errorEvent").asInstanceOf[YUICustomEvent]

  // 2026-05-13: API only used by `TinyMCE`
  val componentChangedLayoutCB = new CallbackList[Unit]()

  class ValueChangeInternalEvent(
    val control        : html.Element,
    val newControlValue: String
  ) extends js.Object

  // 2026-05-13: API only used by `Select1SearchCompanion`
  val afterValueChangeCB = new CallbackList[ValueChangeInternalEvent]()

  // Walk up the DOM from `element` to find the first ancestor (or self) that is an XForms control,
  // an XBL component, or an XForms dialog. Returns null if none is found.
  def findParentXFormsControl(element: dom.EventTarget): Option[html.Element] =
    element match {
      case elem: html.Element =>
        elem.ancestorOrSelfElem(".xforms-control, .xbl-component, .xforms-dialog", includeSelf = true).nextOption()
      case _ =>
        None
    }

  def showToolTip(
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
        val yuiTooltip =
          newInstance(g.YAHOO.widget.Tooltip)(
            control.id + toolTipSuffix,
            js.Dynamic.literal(
              context   = target,
              text      = message,
              showDelay = 0,
              hideDelay = 0,
              // High zIndex so tooltip is always on top, e.g. above dialogs
              zIndex    = 10000
            )
          ).asInstanceOf[js.Dynamic]
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
