package org.orbeon.oxf.xforms.processor.handlers.xhtml

import org.orbeon.oxf.xforms.analysis.controls.{LHHAAnalysis, SecretControl}
import org.orbeon.oxf.xforms.control.controls.XFormsSecretControl
import org.orbeon.oxf.xforms.processor.handlers.XFormsBaseHandler.forwardAutocompleteAttribute
import org.orbeon.oxf.xforms.processor.handlers.{HandlerContext, XFormsBaseHandler}
import org.orbeon.oxf.xml.SaxSupport.*
import org.orbeon.oxf.xml.{XMLConstants, XMLUtils}
import org.orbeon.xforms.XFormsNames
import org.xml.sax.Attributes


class XFormsSecretHandler(
  uri            : String,
  localname      : String,
  qName          : String,
  attributes     : Attributes,
  elementAnalysis: SecretControl,
  handlerContext : HandlerContext
) extends
  XFormsControlLifecycleHandler(
    uri,
    localname,
    qName,
    attributes,
    elementAnalysis,
    handlerContext,
    repeating  = false,
    forwarding = false
  ) {

  private lazy val placeHolderInfo: Option[PlaceHolderInfo] =
    PlaceHolderInfo.placeHolderValueOpt(elementAnalysis, currentControl)

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
      containerAttributes.addOrReplace("value", if (secretControl == null || secretControl.getExternalValue(handlerContext.collector) == null) "" else secretControl.getExternalValue(handlerContext.collector))
      // Handle accessibility attributes
      XFormsBaseHandler.forwardAccessibilityAttributes(attributes, containerAttributes, handlerContext)
      handleAriaByAtts(containerAttributes, XFormsLHHAHandler.coreControlLhhaByCondition)

      // Output all extension attributes
      // Output `xxf:*` extension attributes
      secretControl.addExtensionAttributesExceptClassAndAcceptForHandler(containerAttributes, XFormsNames.XXFORMS_NAMESPACE_URI)

      // Add attribute even if the control is not concrete
      placeHolderInfo foreach { placeHolderInfo =>
        if (placeHolderInfo.value ne null) // unclear whether this can ever be null
          containerAttributes.addOrReplace("placeholder", placeHolderInfo.value)
      }

      if (isXFormsReadonlyButNotStaticReadonly(secretControl))
        XFormsBaseHandlerXHTML.outputReadonlyAttribute(containerAttributes)
      XFormsBaseHandler.handleAriaAttributes(secretControl.isRequired, secretControl.isValid, secretControl.visited, containerAttributes)

      forwardAutocompleteAttribute(attributes, "input", containerAttributes)

      // Output element
      xmlReceiver.startElement(XMLConstants.XHTML_NAMESPACE_URI, "input", inputQName, containerAttributes)
      xmlReceiver.endElement(XMLConstants.XHTML_NAMESPACE_URI, "input", inputQName)
    } else {
      outputStaticReadonlyField(xhtmlPrefix) {
        secretControl.getFormattedValue(handlerContext.collector) foreach { value =>
          xmlReceiver.characters(value.toCharArray, 0, value.length)
        }
      }
    }
  }

  protected override def handleLabel(lhhaAnalysis: LHHAAnalysis): Unit =
    if (! (placeHolderInfo exists (_.isLabelPlaceholder)))
      super.handleLabel(lhhaAnalysis)

  protected override def handleHint(lhhaAnalysis: LHHAAnalysis): Unit =
    if (placeHolderInfo forall (_.isLabelPlaceholder))
      super.handleHint(lhhaAnalysis)
}