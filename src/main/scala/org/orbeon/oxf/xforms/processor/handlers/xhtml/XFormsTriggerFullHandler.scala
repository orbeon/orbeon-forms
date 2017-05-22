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
 */
package org.orbeon.oxf.xforms.processor.handlers.xhtml

import org.orbeon.dom.QName
import org.orbeon.oxf.xforms.XFormsConstants._
import org.orbeon.oxf.xforms.control.XFormsControl
import org.orbeon.oxf.xforms.control.controls.XFormsTriggerControl
import org.orbeon.oxf.xforms.processor.handlers.xhtml.XFormsBaseHandlerXHTML._
import org.orbeon.oxf.xforms.processor.handlers.xhtml.XFormsTriggerFullHandler._
import org.orbeon.oxf.xml.XMLConstants.XHTML_NAMESPACE_URI
import org.orbeon.oxf.xml.XMLReceiverHelper.CDATA
import org.orbeon.oxf.xml.{SAXUtils, XMLUtils}
import org.xml.sax.Attributes
import org.xml.sax.helpers.AttributesImpl

/**
 * Default full appearance (button).
 *
 * This can also be the "pseudo-minimal" appearance for noscript mode.
 */
class XFormsTriggerFullHandler(
  uri            : String,
  localname      : String,
  qName          : String,
  attributes     : Attributes,
  matched        : AnyRef,
  handlerContext : AnyRef
) extends XFormsTriggerHandler(uri, localname, qName, attributes, matched, handlerContext) {

  override protected def handleControlStart(): Unit = {

    val triggerControl = currentControlOrNull.asInstanceOf[XFormsTriggerControl]
    val xmlReceiver = xformsHandlerContext.getController.getOutput

    val containerAttributes = getEmptyNestedControlAttributesMaybeWithId(getEffectiveId, triggerControl, true)

    val isHTMLLabel = (triggerControl ne null) && triggerControl.isHTMLLabel
    val xhtmlPrefix = xformsHandlerContext.findXHTMLPrefix

    if (xformsHandlerContext.isNoScript) {
      // Noscript mode: we need a name to detect activation
      addAttribute(containerAttributes, "name", getEffectiveId)

      // We need a value so we can detect an activated button with IE 7
      // NOTE: IE 6/7 sends the <button> content as value instead of the value attribute!
      addAttribute(containerAttributes, "value", "activate")

      // In JS-free mode, all buttons are submit inputs or image inputs
      addAttribute(containerAttributes, "type", "submit")
    } else {
      // Script mode: a button without value
      addAttribute(containerAttributes, "type", "button")
    }

    // Disabled attribute when needed
    val disabled = isHTMLDisabled(triggerControl)
    if (disabled)
      outputDisabledAttribute(containerAttributes)

    // Determine bootstrap classes, which go on the <button> element
    // NOTE: It seems we don't need the .disabled class (if (disabled) List("disabled") else Nil).
    def isNoscriptMinimal =
      xformsHandlerContext.isNoScript && XFormsControl.appearances(elementAnalysis)(XFORMS_MINIMAL_APPEARANCE_QNAME)

    val bootstrapClasses = "btn" :: (
      if (isNoscriptMinimal)
        "btn-link" :: Nil
      else
        XFormsControl.appearances(elementAnalysis) flatMap BootstrapAppearances.get toList
    )

    // xh:button or xh:input
    val elementName = "button"
    val spanQName = XMLUtils.buildQName(xhtmlPrefix, elementName)
    xmlReceiver.startElement(XHTML_NAMESPACE_URI, elementName, spanQName, appendClasses(containerAttributes, bootstrapClasses))

    // Output content of <button> element
    if ("button" == elementName)
      outputLabelText(xmlReceiver, triggerControl, getTriggerLabel(triggerControl), xhtmlPrefix, isHTMLLabel)

    xmlReceiver.endElement(XHTML_NAMESPACE_URI, elementName, spanQName)
  }
}

private object XFormsTriggerFullHandler {

  def appendClasses(atts: AttributesImpl, classes: List[String]): AttributesImpl = {
    val existingClasses = Option(atts.getValue("class")).toList
    val newClasses = existingClasses ::: classes ::: Nil mkString " "

    SAXUtils.addOrReplaceAttribute(atts, "", "", "class", newClasses)
  }

  def addAttribute(atts: AttributesImpl, name: String, value: String): Unit =
    atts.addAttribute("", name, name, CDATA, value)

  // Map appearances to Bootstrap classes, e.g. xxf:primary → btn-primary
  val BootstrapAppearances =
    Seq("primary", "info", "success", "warning", "danger", "inverse", "mini", "small", "large", "block") map
      (name ⇒ QName.get(name, XXFORMS_NAMESPACE_URI) → ("btn-" + name)) toMap
}