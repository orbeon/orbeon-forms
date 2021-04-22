package org.orbeon.oxf.xforms.processor.handlers.xhtml

import org.orbeon.oxf.xforms.analysis.ElementAnalysis
import org.orbeon.oxf.xforms.control.controls.XFormsTriggerControl
import org.orbeon.oxf.xforms.processor.handlers.HandlerContext
import org.orbeon.oxf.xml.{XMLConstants, XMLReceiverHelper, XMLUtils}
import org.orbeon.xforms.XFormsNames
import org.xml.sax.Attributes


private object XFormsTriggerMinimalHandler {
  val EnclosingElementName = "button"
}

class XFormsTriggerMinimalHandler(
  uri            : String,
  localname      : String,
  qName          : String,
  attributes     : Attributes,
  matched        : ElementAnalysis,
  handlerContext : HandlerContext
) extends
  XFormsTriggerHandler(
    uri,
    localname,
    qName,
    attributes,
    matched,
    handlerContext
  ) {

  def handleControlStart(): Unit = {

    val triggerControl = currentControl.asInstanceOf[XFormsTriggerControl]
    val xmlReceiver = handlerContext.controller.output

    val htmlAnchorAttributes = getEmptyNestedControlAttributesMaybeWithId(getEffectiveId, triggerControl, addId = true)
    htmlAnchorAttributes.addAttribute("", "class", "class", XMLReceiverHelper.CDATA, "btn-link")

    // Output `xxf:*` extension attributes
    triggerControl.addExtensionAttributesExceptClassAndAcceptForHandler(htmlAnchorAttributes, XFormsNames.XXFORMS_NAMESPACE_URI)

    // `xh:button`
    val xhtmlPrefix = handlerContext.findXHTMLPrefix
    val spanQName = XMLUtils.buildQName(xhtmlPrefix, XFormsTriggerMinimalHandler.EnclosingElementName)
    xmlReceiver.startElement(XMLConstants.XHTML_NAMESPACE_URI, XFormsTriggerMinimalHandler.EnclosingElementName, spanQName, htmlAnchorAttributes)
    val labelValue = getTriggerLabel(triggerControl)
    val mustOutputHTMLFragment = triggerControl != null && triggerControl.isHTMLLabel
    XFormsBaseHandlerXHTML.outputLabelTextIfNotEmpty(labelValue, xhtmlPrefix, mustOutputHTMLFragment, Option(triggerControl.getLocationData))(xmlReceiver)
    xmlReceiver.endElement(XMLConstants.XHTML_NAMESPACE_URI, XFormsTriggerMinimalHandler.EnclosingElementName, spanQName)
  }
}