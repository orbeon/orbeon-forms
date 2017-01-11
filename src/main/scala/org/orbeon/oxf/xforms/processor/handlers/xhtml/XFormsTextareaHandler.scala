/**
  * Copyright (C) 2010 Orbeon, Inc.
  *
  * This program is free software; you can redistribute it and/or modify it under the terms of the
  * GNU Lesser General Public License as published by the Free Software Foundation; either version
  * 2.1 of the License, or (at your option) any later version.
  *
  * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  * See the GNU Lesser General Public License for more details.
  *
  * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
  *//**
  * Copyright (C) 2010 Orbeon, Inc.
  *
  * This program is free software; you can redistribute it and/or modify it under the terms of the
  * GNU Lesser General Public License as published by the Free Software Foundation; either version
  * 2.1 of the License, or (at your option) any later version.
  *
  * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  * See the GNU Lesser General Public License for more details.
  *
  * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
  */
package org.orbeon.oxf.xforms.processor.handlers.xhtml

import org.orbeon.oxf.xforms.XFormsConstants
import org.orbeon.oxf.xforms.control.XFormsControl
import org.orbeon.oxf.xforms.control.controls.XFormsTextareaControl
import org.orbeon.oxf.xforms.processor.handlers.XFormsBaseHandler
import org.orbeon.oxf.xml.{XMLConstants, XMLReceiverHelper, XMLUtils}
import org.xml.sax.Attributes

/**
  * Handle xf:textarea.
  */
class XFormsTextareaHandler extends XFormsControlLifecyleHandler(false) {

  protected def handleControlStart(
    uri         : String,
    localname   : String,
    qName       : String,
    attributes  : Attributes,
    effectiveId : String,
    control     : XFormsControl
  ): Unit = {

    val textareaControl        = control.asInstanceOf[XFormsTextareaControl]
    val xmlReceiver            = handlerContext.getController.getOutput
    val isConcreteControl      = textareaControl ne null
    val htmlTextareaAttributes = getEmptyNestedControlAttributesMaybeWithId(uri, localname, attributes, effectiveId, control, true)

    // Create xhtml:textarea
    val xhtmlPrefix = handlerContext.findXHTMLPrefix

    if (! XFormsBaseHandler.isStaticReadonly(textareaControl)) {

      val textareaQName = XMLUtils.buildQName(xhtmlPrefix, "textarea")
      htmlTextareaAttributes.addAttribute("", "name", "name", XMLReceiverHelper.CDATA, effectiveId)

      // Handle accessibility attributes
      XFormsBaseHandler.handleAccessibilityAttributes(attributes, htmlTextareaAttributes)

      // Output all extension attributes
      if (isConcreteControl) {
        // Output xxf:* extension attributes
        textareaControl.addExtensionAttributesExceptClassAndAcceptForHandler(htmlTextareaAttributes, XFormsConstants.XXFORMS_NAMESPACE_URI)
      }

      if (isHTMLDisabled(textareaControl))
        XFormsBaseHandlerXHTML.outputDisabledAttribute(htmlTextareaAttributes)

      if (isConcreteControl)
        XFormsBaseHandler.handleAriaAttributes(textareaControl.isRequired, textareaControl.isValid, htmlTextareaAttributes)

      xmlReceiver.startElement(XMLConstants.XHTML_NAMESPACE_URI, "textarea", textareaQName, htmlTextareaAttributes)
      if (isConcreteControl) {
        val value = textareaControl.getExternalValue
        if (value ne null)
          xmlReceiver.characters(value.toCharArray, 0, value.length)
      }
      xmlReceiver.endElement(XMLConstants.XHTML_NAMESPACE_URI, "textarea", textareaQName)
    } else {
      // Static readonly

      // Use <pre> in text/plain so that spaces are kept by the serializer
      // NOTE: Another option would be to transform the text to output &nbsp; and <br/> instead.

      val containerName  = "pre"
      val containerQName = XMLUtils.buildQName(xhtmlPrefix, containerName)

      xmlReceiver.startElement(XMLConstants.XHTML_NAMESPACE_URI, containerName, containerQName, htmlTextareaAttributes)
      if (isConcreteControl) {
        // NOTE: Don't replace spaces with &nbsp;, as this is not the right algorithm for all cases
        val value = textareaControl.getExternalValue
        if (value ne null)
          xmlReceiver.characters(value.toCharArray, 0, value.length)
      }
      xmlReceiver.endElement(XMLConstants.XHTML_NAMESPACE_URI, containerName, containerQName)
    }
  }
}