package org.orbeon.oxf.xforms.processor.handlers.xhtml

import org.orbeon.oxf.xforms.analysis.ElementAnalysis
import org.orbeon.oxf.xforms.control.controls.XFormsRangeControl
import org.orbeon.oxf.xforms.processor.handlers.HandlerContext
import org.orbeon.oxf.xml.{XMLConstants, XMLReceiverHelper, XMLUtils}
import org.xml.sax.Attributes
import org.xml.sax.helpers.AttributesImpl


private object XFormsRangeHandler {
  val RangeBackgroundClass = "xforms-range-background"
  val RangeThumbClass      = "xforms-range-thumb"
}

class XFormsRangeHandler(
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

  override def getContainingElementName = "div"

  override def handleControlStart(): Unit = {

    val rangeControl = currentControl.asInstanceOf[XFormsRangeControl]
    val contentHandler = handlerContext.controller.output

    // Create nested `xh:div` elements
    val xhtmlPrefix = handlerContext.findXHTMLPrefix
    val divName = "div"
    val divQName = XMLUtils.buildQName(xhtmlPrefix, divName)
    val backgroundAttributes = getBackgroundAttributes(getEffectiveId, rangeControl)
    contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, divName, divQName, backgroundAttributes)
    contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, divName, divQName, getThumbAttributes)
    contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, divName, divQName)
    contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, divName, divQName)
  }

  private def getThumbAttributes: AttributesImpl = {
    // Just set class
    reusableAttributes.clear()
    reusableAttributes.addAttribute("", "class", "class", XMLReceiverHelper.CDATA, XFormsRangeHandler.RangeThumbClass)
    reusableAttributes
  }

  private def getBackgroundAttributes(effectiveId: String, xformsControl: XFormsRangeControl): AttributesImpl = {
    // Add custom class
    val containerAttributes = getEmptyNestedControlAttributesMaybeWithId(effectiveId, xformsControl, addId = true)
    containerAttributes.addAttribute("", "class", "class", XMLReceiverHelper.CDATA, XFormsRangeHandler.RangeBackgroundClass)
    containerAttributes
  }
}