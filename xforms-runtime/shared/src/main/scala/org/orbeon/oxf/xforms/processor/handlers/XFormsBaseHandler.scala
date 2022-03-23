package org.orbeon.oxf.xforms.processor.handlers

import org.orbeon.oxf.xforms.control.XFormsControl
import org.orbeon.oxf.xforms.control.controls.XXFormsAttributeControl
import org.orbeon.oxf.xforms.XFormsContainingDocument
import org.orbeon.oxf.xml._
import org.orbeon.xforms.{Constants, XFormsId}
import org.xml.sax.Attributes
import org.xml.sax.helpers.AttributesImpl


/**
 * Base XForms handler used as base class in both the xml and xhtml handlers.
 */
abstract class XFormsBaseHandler protected (
  uri            : String,
  localname      : String,
  qName          : String,
  attributes     : Attributes,
  handlerContext : HandlerContext,
  val repeating  : Boolean,
  val forwarding : Boolean
) extends
  ElementHandler[HandlerContext](
    uri,
    localname,
    qName,
    attributes,
    handlerContext
  ) {

  protected val containingDocument: XFormsContainingDocument = handlerContext.containingDocument

  // TODO: Get rid of this?
  protected var reusableAttributes = new AttributesImpl

  // TODO: update to use `repeating` and `forwarding`
  override def isRepeating: Boolean = repeating
  override def isForwarding: Boolean = forwarding

  // TODO: Check callers.
  def isNonRelevant(control: XFormsControl): Boolean =
    (control eq null) || ! control.isRelevant

  protected def getIdClassXHTMLAttributes(elementAttributes: Attributes, classes: String, effectiveId: String): AttributesImpl =
    XFormsBaseHandler.getIdClassXHTMLAttributes(containingDocument, reusableAttributes, elementAttributes, classes, effectiveId)
}

object XFormsBaseHandler {

  def handleAccessibilityAttributes(srcAttributes: Attributes, destAttributes: AttributesImpl): Unit = {

    // Handle "tabindex"
    locally {
      // This is the standard XForms attribute
      var value = srcAttributes.getValue("navindex")
      if (value == null) {
        // Try the the XHTML attribute
        value = srcAttributes.getValue("tabindex")
      }
      if (value != null)
        destAttributes.addAttribute("", "tabindex", "tabindex", XMLReceiverHelper.CDATA, value)
    }

    // Handle "accesskey"
    locally {
      val value = srcAttributes.getValue("accesskey")
      if (value ne null)
        destAttributes.addAttribute("", "accesskey", "accesskey", XMLReceiverHelper.CDATA, value)
    }

    // Handle "role"
    locally {
      val value = srcAttributes.getValue("role")
      if (value ne null)
        destAttributes.addAttribute("", "role", "role", XMLReceiverHelper.CDATA, value)
    }
  }

  def handleAriaAttributes(required: Boolean, valid: Boolean, destAttributes: AttributesImpl): Unit = {
    if (required)
      destAttributes.addAttribute("", "aria-required", "aria-required", XMLReceiverHelper.CDATA, "true")
    if (! valid)
      destAttributes.addAttribute("", "aria-invalid", "aria-invalid", XMLReceiverHelper.CDATA, "true")
  }

  def getIdClassXHTMLAttributes(
    containingDocument : XFormsContainingDocument,
    reusableAttributes : AttributesImpl,
    elementAttributes  : Attributes,
    classes            : String,
    effectiveId        : String
  ): AttributesImpl = {

    reusableAttributes.clear()

    // Copy "id"
    if (effectiveId ne null)
      reusableAttributes.addAttribute("", "id", "id", XMLReceiverHelper.CDATA, containingDocument.namespaceId(effectiveId))

    // Create "class" attribute if necessary
    if (classes != null && classes.nonEmpty)
      reusableAttributes.addAttribute("", "class", "class", XMLReceiverHelper.CDATA, classes)

    // Copy attributes in the xhtml namespace to no namespace
    for (i <- 0 until elementAttributes.getLength) {
      if (XMLConstants.XHTML_NAMESPACE_URI == elementAttributes.getURI(i)) {
        val name = elementAttributes.getLocalName(i)
        if (!("class" == name))
          reusableAttributes.addAttribute("", name, name, XMLReceiverHelper.CDATA, elementAttributes.getValue(i))
      }
    }
    reusableAttributes
  }

  def isStaticReadonly(control: XFormsControl): Boolean =
    control != null && control.isStaticReadonly

  // E.g. foo$bar.1-2-3 -> foo$bar$$alert.1-2-3
  def getLHHACIdWithNs(
    containingDocument : XFormsContainingDocument,
    controlEffectiveId : String,
    suffix             : String
  ): String =
    containingDocument.namespaceId(XFormsId.appendToEffectiveId(controlEffectiveId, Constants.LhhacSeparator + suffix))

  def handleAVTsAndIDs(
    _attributes          : Attributes,
    refIdAttributeNames  : Array[String],
    xformsHandlerContext : HandlerContext
  ): Attributes = {

    var attributes = _attributes

    val prefixedId = xformsHandlerContext.getPrefixedId(attributes)
    if (prefixedId ne null) {

      val containingDocument = xformsHandlerContext.containingDocument
      val hasAVT             = xformsHandlerContext.getPartAnalysis.hasAttributeControl(prefixedId)
      val effectiveId        = xformsHandlerContext.getEffectiveId(attributes)

      var found = false
      if (hasAVT) {
        // This element has at least one AVT so process its attributes
        val attributesCount = attributes.getLength
        for (i <- 0 until attributesCount) {
          val attributeValue = attributes.getValue(i)
          if (XMLUtils.maybeAVT(attributeValue)) {
            // This is an AVT most likely
            found = true
            val attributeLocalName = attributes.getLocalName(i)
            val attributeQName = attributes.getQName(i) // use qualified name so we match on "xml:lang"

            // Control analysis
            val controlAnalysis = xformsHandlerContext.getPartAnalysis.getAttributeControl(prefixedId, attributeQName)

            // Get static id of attribute control associated with this particular attribute
            val attributeControlStaticId = controlAnalysis.staticId

            // Find concrete control
            val attributeControlEffectiveId = XFormsId.getRelatedEffectiveId(effectiveId, attributeControlStaticId)
            val attributeControl = containingDocument.getControlByEffectiveId(attributeControlEffectiveId).asInstanceOf[XXFormsAttributeControl]

            // Determine attribute value
            // NOTE: This also handles dummy images for the xhtml:img/@src case
            val effectiveAttributeValue = XXFormsAttributeControl.getExternalValueHandleSrc(attributeControl, controlAnalysis)
            // Set the value of the attribute
            attributes = XMLReceiverSupport.addOrReplaceAttribute(attributes, attributes.getURI(i), XMLUtils.prefixFromQName(attributeQName), attributeLocalName, effectiveAttributeValue)
          }
        }
        if (found) // update the value of the id attribute
          attributes = XMLReceiverSupport.addOrReplaceAttribute(attributes, "", "", "id", containingDocument.namespaceId(effectiveId))
      }
      if (! found) // id was not replaced as part of AVT processing
        attributes = XMLReceiverSupport.addOrReplaceAttribute(attributes, "", "", "id", containingDocument.namespaceId(effectiveId))
    }
    // Check `@for` or other attribute
    for (refIdAttributeName <- refIdAttributeNames) {
      val forAttribute = attributes.getValue(refIdAttributeName)
      if (forAttribute != null)
        attributes = XMLReceiverSupport.addOrReplaceAttribute(attributes, "", "", refIdAttributeName, xformsHandlerContext.getIdPrefix + forAttribute + xformsHandlerContext.getIdPostfix)
    }
    attributes
  }
}
