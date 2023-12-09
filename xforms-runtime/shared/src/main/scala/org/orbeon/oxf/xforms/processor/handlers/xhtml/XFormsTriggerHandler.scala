package org.orbeon.oxf.xforms.processor.handlers.xhtml

import org.orbeon.oxf.xforms.analysis.ElementAnalysis
import org.orbeon.oxf.xforms.analysis.controls.LHHAAnalysis
import org.orbeon.oxf.xforms.control.XFormsControl
import org.orbeon.oxf.xforms.control.XFormsSingleNodeControl
import org.orbeon.oxf.xforms.processor.handlers.{HandlerContext, XFormsBaseHandler}
import org.orbeon.oxf.xml.SaxSupport._
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
  XFormsControlLifecycleHandler(
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
    Option(xformsControl.getLabel(handlerContext.collector)).getOrElse("")

  // Don't output anything in static readonly mode
  override def isMustOutputControl(control: XFormsControl): Boolean =
    ! XFormsBaseHandler.isStaticReadonly(control)

  override def handleLabel(lhhaAnalysis: LHHAAnalysis): Unit = () // label is handled differently
  override def handleHint(lhhaAnalysis: LHHAAnalysis) : Unit = () // hint is handled differently
  override def handleAlert(lhhaAnalysis: LHHAAnalysis): Unit = () // triggers don't need an alert (in theory, they could have one)

  override def getEmptyNestedControlAttributesMaybeWithId(effectiveId: String, control: XFormsControl, addId: Boolean): AttributesImpl = {

    // Get standard attributes
    val containerAttributes = super.getEmptyNestedControlAttributesMaybeWithId(effectiveId, control, addId)

    // Add `title` attribute if not yet present and there is a hint
    if (containerAttributes.getValue("title") eq null) {

      val hintValue =
        if (control != null)
          control.getHint(handlerContext.collector)
        else
          null

      if (hintValue != null)
        containerAttributes.addOrReplace("title", hintValue)
    }

    XFormsBaseHandler.forwardAccessibilityAttributes(attributes, containerAttributes)
    containerAttributes
  }
}