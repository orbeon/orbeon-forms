package org.orbeon.oxf.xforms.processor.handlers.xhtml

import org.orbeon.oxf.xforms.analysis.ElementAnalysis
import org.orbeon.oxf.xforms.control.controls.XFormsSecretControl
import org.orbeon.oxf.xforms.processor.handlers.{HandlerContext, XFormsBaseHandler}
import org.orbeon.oxf.xml.{XMLConstants, XMLReceiverHelper, XMLUtils}
import org.orbeon.xforms.XFormsNames
import org.xml.sax.Attributes


class XFormsSecretHandler(
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

  def handleControlStart(): Unit = {

    val secretControl = currentControl.asInstanceOf[XFormsSecretControl]
    val contentHandler = handlerContext.controller.output
    val containerAttributes = getEmptyNestedControlAttributesMaybeWithId(getEffectiveId, secretControl, addId = true)

    // Create `xh:input`
    val xhtmlPrefix = handlerContext.findXHTMLPrefix
    if (! XFormsBaseHandler.isStaticReadonly(secretControl)) {
      val inputQName = XMLUtils.buildQName(xhtmlPrefix, "input")
      containerAttributes.addAttribute("", "type", "type", XMLReceiverHelper.CDATA, "password")
      containerAttributes.addAttribute("", "name", "name", XMLReceiverHelper.CDATA, getEffectiveId)
      containerAttributes.addAttribute("", "value", "value", XMLReceiverHelper.CDATA, if (secretControl == null || secretControl.getExternalValue == null) "" else secretControl.getExternalValue()
      )
      // Handle accessibility attributes
      XFormsBaseHandler.forwardAccessibilityAttributes(attributes, containerAttributes)
      handleAriaByAtts(containerAttributes, XFormsLHHAHandler.coreControlLhhaByCondition)

      // Output all extension attributes
      // Output `xxf:*` extension attributes
      secretControl.addExtensionAttributesExceptClassAndAcceptForHandler(reusableAttributes, XFormsNames.XXFORMS_NAMESPACE_URI)
      if (isXFormsReadonlyButNotStaticReadonly(secretControl))
        XFormsBaseHandlerXHTML.outputReadonlyAttribute(reusableAttributes)
      XFormsBaseHandler.handleAriaAttributes(secretControl.isRequired, secretControl.isValid, secretControl.visited, containerAttributes)

      // Output element
      contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "input", inputQName, containerAttributes)
      contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "input", inputQName)
    } else {
      // Output static read-only value
      val spanQName = XMLUtils.buildQName(xhtmlPrefix, "span")
      containerAttributes.addAttribute("", "class", "class", "CDATA", "xforms-field")
      contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "span", spanQName, containerAttributes)
      val value = secretControl.getFormattedValue
      if (value.isDefined)
        contentHandler.characters(value.get.toCharArray, 0, value.get.length)
      contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "span", spanQName)
    }
  }
}