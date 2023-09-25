package org.orbeon.oxf.xforms.processor.handlers.xhtml

import org.orbeon.oxf.xforms.analysis.ElementAnalysis
import org.orbeon.oxf.xforms.control.controls.XFormsSecretControl
import org.orbeon.oxf.xforms.processor.handlers.XFormsBaseHandler.forwardAutocompleteAttribute
import org.orbeon.oxf.xforms.processor.handlers.{HandlerContext, XFormsBaseHandler}
import org.orbeon.oxf.xml.SaxSupport._
import org.orbeon.oxf.xml.{XMLConstants, XMLUtils}
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
    implicit val xmlReceiver = handlerContext.controller.output
    val containerAttributes = getEmptyNestedControlAttributesMaybeWithId(getEffectiveId, secretControl, addId = true)

    // Create `xh:input`
    val xhtmlPrefix = handlerContext.findXHTMLPrefix
    if (! XFormsBaseHandler.isStaticReadonly(secretControl)) {
      val inputQName = XMLUtils.buildQName(xhtmlPrefix, "input")
      containerAttributes.addOrReplace("type", "password")
      containerAttributes.addOrReplace("name", getEffectiveId)
      containerAttributes.addOrReplace("value", if (secretControl == null || secretControl.getExternalValue == null) "" else secretControl.getExternalValue())
      // Handle accessibility attributes
      XFormsBaseHandler.forwardAccessibilityAttributes(attributes, containerAttributes)
      handleAriaByAtts(containerAttributes, XFormsLHHAHandler.coreControlLhhaByCondition)

      // Output all extension attributes
      // Output `xxf:*` extension attributes
      secretControl.addExtensionAttributesExceptClassAndAcceptForHandler(containerAttributes, XFormsNames.XXFORMS_NAMESPACE_URI)
      if (isXFormsReadonlyButNotStaticReadonly(secretControl))
        XFormsBaseHandlerXHTML.outputReadonlyAttribute(containerAttributes)
      XFormsBaseHandler.handleAriaAttributes(secretControl.isRequired, secretControl.isValid, secretControl.visited, containerAttributes)

      forwardAutocompleteAttribute(attributes, "input", containerAttributes)

      // Output element
      xmlReceiver.startElement(XMLConstants.XHTML_NAMESPACE_URI, "input", inputQName, containerAttributes)
      xmlReceiver.endElement(XMLConstants.XHTML_NAMESPACE_URI, "input", inputQName)
    } else {
      outputStaticReadonlyField(xhtmlPrefix) {
        secretControl.getFormattedValue foreach { value =>
          xmlReceiver.characters(value.toCharArray, 0, value.length)
        }
      }
    }
  }
}