package org.orbeon.oxf.xforms.processor.handlers.xhtml

import org.orbeon.oxf.xforms.analysis.ElementAnalysis
import org.orbeon.oxf.xforms.control.XFormsControl
import org.orbeon.oxf.xforms.control.XFormsSingleNodeControl
import org.orbeon.oxf.xforms.processor.handlers.{HandlerContext, XFormsBaseHandler}
import org.orbeon.oxf.xml.XMLReceiverHelper
import org.xml.sax.Attributes
import org.xml.sax.helpers.AttributesImpl


abstract class XFormsTriggerHandler(
  uri            : String,
  localname      : String,
  qName          : String,
  attributes     : Attributes,
  matched        : ElementAnalysis,
  handlerContext : HandlerContext
) extends
  XFormsControlLifecyleHandler(
    uri,
    localname,
    qName,
    attributes,
    matched,
    handlerContext,
    repeating  = false,
    forwarding = false
  ) {

  protected def getTriggerLabel(xformsControl: XFormsSingleNodeControl): String =
    if (xformsControl.getLabel != null)
      xformsControl.getLabel
    else
      ""

  // Don't output anything in static readonly mode
  override def isMustOutputControl(control: XFormsControl): Boolean =
    ! XFormsBaseHandler.isStaticReadonly(control)

  override def handleLabel(): Unit = () // label is handled differently
  override def handleHint() : Unit = () // hint is handled differently
  override def handleAlert(): Unit = () // triggers don't need an alert (in theory, they could have one)

  override def getEmptyNestedControlAttributesMaybeWithId(effectiveId: String, control: XFormsControl, addId: Boolean): AttributesImpl = {

    // Get standard attributes
    val containerAttributes = super.getEmptyNestedControlAttributesMaybeWithId(effectiveId, control, addId)

    // Add `title` attribute if not yet present and there is a hint
    if (containerAttributes.getValue("title") eq null) {

      val hintValue =
        if (control != null)
          control.getHint
        else
          null

      if (hintValue != null)
        containerAttributes.addAttribute("", "title", "title", XMLReceiverHelper.CDATA, hintValue)
    }

    XFormsBaseHandler.handleAccessibilityAttributes(attributes, containerAttributes)
    containerAttributes
  }
}