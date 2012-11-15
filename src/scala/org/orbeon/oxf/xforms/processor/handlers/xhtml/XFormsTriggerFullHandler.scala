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

import org.orbeon.oxf.xforms.XFormsUtils
import org.orbeon.oxf.xforms.control.XFormsControl
import org.orbeon.oxf.xforms.control.controls.XFormsTriggerControl
import org.orbeon.oxf.xml.ContentHandlerHelper.CDATA
import org.orbeon.oxf.xml.XMLConstants.XHTML_NAMESPACE_URI
import org.orbeon.oxf.xml.XMLReceiverAdapter
import org.orbeon.oxf.xml.XMLUtils
import org.xml.sax.Attributes
import org.xml.sax.helpers.AttributesImpl
import XFormsBaseHandlerXHTML._
import XFormsTriggerFullHandler._
import org.dom4j.QName
import org.orbeon.oxf.xforms.XFormsConstants._
import scala.collection.JavaConverters._
import java.lang.{StringBuilder ⇒ JStringBuilder}

/**
 * Default full appearance (button).
 *
 * This can also be the "pseudo-minimal" appearance for noscript mode.
 */
class XFormsTriggerFullHandler extends XFormsTriggerHandler {

    protected def handleControlStart(uri: String, localname: String, qName: String, attributes: Attributes, effectiveId: String, control: XFormsControl) {

        val triggerControl = control.asInstanceOf[XFormsTriggerControl]
        val xmlReceiver = handlerContext.getController.getOutput

        val containerAttributes = getContainerAttributes(uri, localname, attributes, effectiveId, triggerControl, true)

        val isHTMLLabel = (triggerControl ne null) && triggerControl.isHTMLLabel
        val xhtmlPrefix = handlerContext.findXHTMLPrefix

        val elementName =
            if (handlerContext.isNoScript) {
                // Noscript mode: we need a name to detect activation
                addAttribute(containerAttributes, "name", effectiveId)

                // Deal with legacy IE stuff
                val (inputType, elementName) =
                    if (handlerContext.isRenderingEngineIE6OrEarlier) {
                        val (inputLabelValue, inputType) =
                            getIE6LabelValue(triggerControl, xhtmlPrefix, containerAttributes, effectiveId, isHTMLLabel)

                        addAttribute(containerAttributes, "value", inputLabelValue)
                        (inputType, "input")
                    } else {
                        // We need a value so we can detect an activated button with IE 7
                        // NOTE: IE 6/7 sends the <button> content as value instead of the value attribute!
                        addAttribute(containerAttributes, "value", "activate")
                        ("submit", "button")
                    }

                // In JS-free mode, all buttons are submit inputs or image inputs
                addAttribute(containerAttributes, "type", inputType)

                elementName
            } else {
                // Script mode: a button without value
                addAttribute(containerAttributes, "type", "button")
                "button"
            }

        // Disabled attribute when needed
        val disabled = isHTMLDisabled(triggerControl)
        if (disabled)
            outputDisabledAttribute(containerAttributes)

        // Determine bootstrap classes, which go on the <button> element
        // NOTE: It seems we don't need the .disabled class (if (disabled) List("disabled") else Nil).
        def isNoscriptMinimal =
            handlerContext.isNoScript && XFormsControl.appearances(elementAnalysis)(XXFORMS_MINIMAL_APPEARANCE_QNAME)

        val bootstrapClasses = "btn" :: (
            if (isNoscriptMinimal)
                "btn-link" :: Nil
            else
                (getAppearances.asScala flatMap (appearance ⇒ BootstrapAppearances.get(appearance)) toList))


        // xh:button or xh:input
        val spanQName = XMLUtils.buildQName(xhtmlPrefix, elementName)
        xmlReceiver.startElement(XHTML_NAMESPACE_URI, elementName, spanQName, appendClasses(containerAttributes, bootstrapClasses))

        // Output content of <button> element
        if ("button" == elementName)
            outputLabelText(xmlReceiver, triggerControl, getTriggerLabel(triggerControl), xhtmlPrefix, isHTMLLabel)

        xmlReceiver.endElement(XHTML_NAMESPACE_URI, elementName, spanQName)
    }

    // IE 6 does not support discriminating between multiple buttons: it sends them all, so we use "input" instead. The
    // code below tries to output <input type="submit"> or <input type="image"> depending on the content of the label.
    // This has limitations: we can only handle text or a single image.
    private def getIE6LabelValue(
            triggerControl: XFormsTriggerControl,
            xhtmlPrefix: String,
            containerAttributes: AttributesImpl,
            effectiveId: String,
            isHTMLLabel: Boolean) = {

        val labelValue = getTriggerLabel(triggerControl)

        if (isHTMLLabel) {
            // Only output character content within input
            containingDocument.getControls.getIndentedLogger.logWarning(
                "xf:trigger",
                "IE 6 does not support <button> elements properly. Only text within HTML content will appear garbled.",
                "control id", effectiveId)

            // Analyze label value to find a nested image or text
            val (image, text) = {
                val sb = new JStringBuilder(labelValue.length)
                var imageInfo: Option[(Option[String], Option[String], Option[String])] = None

                XFormsUtils.streamHTMLFragment(new XMLReceiverAdapter {
                    override def startElement(namespaceURI: String, localName: String, qName: String, atts: Attributes): Unit =
                        // Remember information of first image found
                        if (imageInfo.isEmpty && localName == "img")
                            imageInfo = Some((Option(atts.getValue("src")), Option(atts.getValue("alt")), Option(atts.getValue("title"))))

                    override def characters(ch: Array[Char], start: Int, length: Int): Unit =
                        sb.append(ch, start, length)

                }, labelValue, triggerControl.getLocationData, xhtmlPrefix)

                (imageInfo, sb.toString)
            }

            image match {
                case Some((Some(src), altOpt, titleOpt)) if text.trim == "" ⇒
                    // There is an image and no text, output image
                    addAttribute(containerAttributes, "src", src)
                    altOpt   foreach (addAttribute(containerAttributes, "alt",  _))
                    titleOpt foreach (addAttribute(containerAttributes, "title", _))
                    ("", "image")
                case _ ⇒
                    // Output text
                    ("submit", text)
            }
        } else
            ("submit", labelValue)
    }
}

private object XFormsTriggerFullHandler {

    def appendClasses(atts: AttributesImpl, classes: List[String]): AttributesImpl = {
        val existingClasses = Option(atts.getValue("class")).toList
        val newClasses = existingClasses ::: classes ::: Nil mkString " "

        XMLUtils.addOrReplaceAttribute(atts, "", "", "class", newClasses)
    }

    def addAttribute(atts: AttributesImpl, name: String, value: String): Unit =
        atts.addAttribute("", name, name, CDATA, value)

    // Map appearances to Bootstrap classes, e.g. xxf:primary → btn-primary
    val BootstrapAppearances =
        Seq("primary", "info", "success", "warning", "danger", "inverse") map
            (name ⇒ QName.get(name, XXFORMS_NAMESPACE_URI) → ("btn-" + name)) toMap
}