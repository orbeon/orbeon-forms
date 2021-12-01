package org.orbeon.oxf.xforms.event.events

import org.orbeon.oxf.xforms.event.XFormsEvent.{EmptyGetter, PropertyGetter}
import org.orbeon.oxf.xforms.event.XFormsEvents._
import org.orbeon.oxf.xforms.event.{XFormsEvent, XFormsEventTarget}


class XFormsDialogShownEvent(target: XFormsEventTarget, properties: PropertyGetter)
  extends XFormsEvent(XFORMS_DIALOG_SHOWN, target, properties, bubbles = true, cancelable = false) {
  def this(target: XFormsEventTarget) = this(target, EmptyGetter)
}

class XFormsDialogHiddenEvent(target: XFormsEventTarget, properties: PropertyGetter)
  extends XFormsEvent(XFORMS_DIALOG_HIDDEN, target, properties, bubbles = true, cancelable = false) {
  def this(target: XFormsEventTarget) = this(target, EmptyGetter)
}

class XXFormsDialogOpenEvent(target: XFormsEventTarget, properties: PropertyGetter)
    extends XFormsEvent(XXFORMS_DIALOG_OPEN, target, properties, bubbles = true, cancelable = false) {

  def this(properties: PropertyGetter, target: XFormsEventTarget, neighbor: String, constrainToViewport: Boolean) =
    this(target, properties orElse Map("neighbor" -> Option(neighbor), "constrain-to-viewport" -> Option(constrainToViewport)))

  def neighbor            = property[String]("neighbor")
  def constrainToViewport = property[Boolean]("constrain-to-viewport") getOrElse false
}

class XXFormsDialogCloseEvent(target: XFormsEventTarget, properties: PropertyGetter)
  extends XFormsEvent(XXFORMS_DIALOG_CLOSE, target, properties, bubbles = true, cancelable = false) {
  def this(target: XFormsEventTarget) = this(target, EmptyGetter)
}