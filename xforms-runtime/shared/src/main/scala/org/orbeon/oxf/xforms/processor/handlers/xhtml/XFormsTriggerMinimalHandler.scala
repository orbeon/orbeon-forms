package org.orbeon.oxf.xforms.processor.handlers.xhtml

import org.orbeon.oxf.xforms.analysis.ElementAnalysis
import org.orbeon.oxf.xforms.control.controls.XFormsTriggerControl
import org.orbeon.oxf.xforms.processor.handlers.HandlerContext
import org.orbeon.oxf.xforms.processor.handlers.xhtml.XFormsBaseHandlerXHTML.outputDisabledAttribute
import org.orbeon.oxf.xml.SaxSupport.*
import org.orbeon.oxf.xml.{XMLConstants, XMLUtils}
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

    val buttonAttributes = getEmptyNestedControlAttributesMaybeWithId(getEffectiveId, triggerControl, addId = true)
    buttonAttributes.addOrReplace(XFormsNames.CLASS_QNAME, "btn-link")
    if (isXFormsReadonlyButNotStaticReadonly(triggerControl))
      outputDisabledAttribute(buttonAttributes)

    // Output `xxf:*` extension attributes
    triggerControl.addExtensionAttributesExceptClassAndAcceptForHandler(buttonAttributes, XFormsNames.XXFORMS_NAMESPACE_URI)

    // `xh:button`
    val xhtmlPrefix = handlerContext.findXHTMLPrefix
    val spanQName = XMLUtils.buildQName(xhtmlPrefix, XFormsTriggerMinimalHandler.EnclosingElementName)
    xmlReceiver.startElement(XMLConstants.XHTML_NAMESPACE_URI, XFormsTriggerMinimalHandler.EnclosingElementName, spanQName, buttonAttributes)
    val labelValueOpt = getTriggerLabel(triggerControl)
    val mustOutputHTMLFragment = triggerControl != null && triggerControl.isHTMLLabel(handlerContext.collector)
    XFormsBaseHandlerXHTML.outputLabelTextIfNotEmpty(labelValueOpt, xhtmlPrefix, mustOutputHTMLFragment, Option(triggerControl.getLocationData))(xmlReceiver)
    xmlReceiver.endElement(XMLConstants.XHTML_NAMESPACE_URI, XFormsTriggerMinimalHandler.EnclosingElementName, spanQName)
  }
}