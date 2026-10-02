package org.orbeon.xforms

import org.orbeon.web.DomSupport.DomElemOps
import org.orbeon.xforms.facade.{Bootstrap, BootstrapTip}
import org.scalajs.dom
import org.scalajs.dom.html

import scala.scalajs.js


object XFormsUiEvents {

  // Public API
  val orbeonLoadedEvent: LegacyCustomEvent =
    new LegacyCustomEvent(
      typeName     = "orbeonLoaded",
      defaultScope = dom.window,
      signature    = LegacyCustomEvent.List,
      fireOnce     = true
    )

  // Public API
  val errorEvent: LegacyCustomEvent =
    new LegacyCustomEvent(
      typeName     = "errorEvent",
      defaultScope = dom.window,
      signature    = LegacyCustomEvent.List,
      fireOnce     = false
    )

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
    tooltipForControl: js.Dictionary[BootstrapTip],
    control          : html.Element,
    target           : html.Element,
    message          : String
  ): Unit = {

    if (message == "") {
      tooltipForControl.get(control.id).filter(_ != null).foreach(_.destroy())
      tooltipForControl(control.id) = null
    } else {
      val currentTooltip = tooltipForControl.getOrElse(control.id, null)
      if (currentTooltip != null) {
        if (currentTooltip.target ne target) {
          currentTooltip.destroy()
          tooltipForControl(control.id) = null
        } else {
          currentTooltip.updateTitle(message)
          currentTooltip.enable()
          currentTooltip.show()
        }
      }

      if (tooltipForControl.getOrElse(control.id, null) == null) {
        val placement: js.Function = () => {
          val p = Placement.getPlacement(Placement.getPositionDetails(target))
          if (p == Placement.Over) "bottom" else p.entryName
        }
        val tip = Bootstrap.newTooltip(target, js.Dynamic.literal(
          title     = message,
          html      = true,
          animation = false,
          placement = placement
        ))
        tooltipForControl(control.id) = tip
        tip.show()
      }
    }
  }
}
