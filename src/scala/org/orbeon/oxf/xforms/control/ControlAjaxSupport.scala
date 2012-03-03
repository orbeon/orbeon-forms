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
package org.orbeon.oxf.xforms.control

import org.dom4j.QName
import org.orbeon.oxf.xforms._
import org.xml.sax.helpers.AttributesImpl
import scala.Option
import org.orbeon.oxf.xforms.XFormsConstants.LHHA
import collection.mutable.LinkedHashSet
import org.apache.commons.lang.StringUtils
import AjaxSupport._
import org.orbeon.oxf.xml.{XMLUtils, ContentHandlerHelper}

trait ControlAjaxSupport {

    self: XFormsControl ⇒

    // Whether the control support Ajax updates
    def supportAjaxUpdates = true

    // Whether the control support full Ajax updates
    def supportFullAjaxUpdates = true

    def outputAjaxDiff(ch: ContentHandlerHelper, other: XFormsControl, attributesImpl: AttributesImpl, isNewlyVisibleSubtree: Boolean) = ()

    def addAjaxAttributes(attributesImpl: AttributesImpl, isNewlyVisibleSubtree: Boolean, other: XFormsControl) = {
        var added = false

        // Control id
        attributesImpl.addAttribute("", "id", "id", ContentHandlerHelper.CDATA, XFormsUtils.namespaceId(containingDocument, getEffectiveId))

        // Class attribute
        added |= addAjaxClass(attributesImpl, isNewlyVisibleSubtree, other, this)

        // Label, help, hint, alert, etc.

        def addAjaxLHHA() = {
            var added = false

            for {
                lhhaType ← LHHA.values
                value1 = if (isNewlyVisibleSubtree) null else other.getLHHA(lhhaType).value()
                lhha2 = self.getLHHA(lhhaType)
                value2 = lhha2.value()
                if value1 != value2
                attributeValue = Option(lhha2.escapedValue()) getOrElse ""
            } yield
                added |= addOrAppendToAttributeIfNeeded(attributesImpl, lhhaType.name(), attributeValue, isNewlyVisibleSubtree, attributeValue == "")

            added
        }

        added |= addAjaxLHHA()
        // Output control-specific attributes
        added |= addAjaxCustomAttributes(attributesImpl, isNewlyVisibleSubtree, other)

        added
    }

    /**
     * Add attributes differences for custom attributes.
     *
     * @param attributesImpl        attributes to add to
     * @param isNewRepeatIteration  whether the current controls is within a new repeat iteration
     * @param other                 original control, possibly null
     * @return                      true if any attribute was added, false otherwise
     */
    def addAjaxCustomAttributes(attributesImpl: AttributesImpl, isNewRepeatIteration: Boolean, other: XFormsControl) = {

        def addAttributesDiffs(attributeQNames: Array[QName], namespaceURI: String) = {
            var added = false
            for {
                avtAttributeQName ← attributeQNames
                // Skip if namespace URI is excluded
                if (namespaceURI eq null) || namespaceURI == avtAttributeQName.getNamespaceURI
                value1 = if (other eq null) null else other.getExtensionAttributeValue(avtAttributeQName)
                value2 = self.getExtensionAttributeValue(avtAttributeQName)
                if value1 != value2
                attributeValue = if (value2 ne null) value2 else ""
            } yield
                // NOTE: For now we use the local name; may want to use a full name?
                added |= addAttributeIfNeeded(attributesImpl, avtAttributeQName.getName, attributeValue, isNewRepeatIteration, attributeValue == "")

            added
        }

        // By default, diff only attributes in the xxforms:* namespace
        val extensionAttributes = getExtensionAttributes
        (extensionAttributes ne null) && addAttributesDiffs(extensionAttributes, XFormsConstants.XXFORMS_NAMESPACE_URI)
    }

    final def addAjaxStandardAttributes(originalControl: XFormsSingleNodeControl, ch: ContentHandlerHelper, isNewRepeatIteration: Boolean) {
        val extensionAttributes = STANDARD_EXTENSION_ATTRIBUTES

        if (extensionAttributes ne null) {
            val control2 = this

            for {
                avtAttributeQName ← extensionAttributes
                if avtAttributeQName != XFormsConstants.CLASS_QNAME
                value1 = if (originalControl eq null) null else originalControl.getExtensionAttributeValue(avtAttributeQName)
                value2 = control2.getExtensionAttributeValue(avtAttributeQName)
                if value1 != value2
                attributeValue = if (value2 ne null) value2 else ""
                attributesImpl = new AttributesImpl
            } yield {
                attributesImpl.addAttribute("", "id", "id", ContentHandlerHelper.CDATA, XFormsUtils.namespaceId(containingDocument, control2.getEffectiveId))
                addAttributeIfNeeded(attributesImpl, "for", control2.getEffectiveId, isNewRepeatIteration, false)
                addAttributeIfNeeded(attributesImpl, "name", avtAttributeQName.getName, isNewRepeatIteration, false)
                ch.startElement("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "attribute", attributesImpl)
                ch.text(attributeValue)
                ch.endElement()
            }
        }
    }
}

// NOTE: Use name different from trait so that the Java compiler is happy
object AjaxSupport {

    val STANDARD_EXTENSION_ATTRIBUTES = Array(XFormsConstants.STYLE_QNAME, XFormsConstants.CLASS_QNAME)

    def addAjaxClass(attributesImpl: AttributesImpl, newlyVisibleSubtree: Boolean, control1: XFormsControl, control2: XFormsControl): Boolean = {
        var added = false
        val class1 = Option(control1) map (_.getExtensionAttributeValue(XFormsConstants.CLASS_QNAME)) orNull
        val class2 = control2.getExtensionAttributeValue(XFormsConstants.CLASS_QNAME)

        if (newlyVisibleSubtree || !XFormsUtils.compareStrings(class1, class2)) {
            // Custom MIPs changed
            val attributeValue =
                if (class1 eq null)
                    class2
                else {
                    val sb = new StringBuilder(100)

                    def tokenize(value: String) = LinkedHashSet(StringUtils.split(value): _*)

                    val classes1 = tokenize(class1)
                    val classes2 = tokenize(class2)

                    // Classes to remove
                    for (currentClass ← classes1) {
                        if (! classes2(currentClass)) {
                            if (sb.length > 0)
                                sb.append(' ')
                            sb.append('-')
                            sb.append(currentClass)
                        }
                    }

                    // Classes to add
                    for (currentClass ← classes2) {
                        if (! classes1(currentClass)) {
                            if (sb.length > 0)
                                sb.append(' ')
                            sb.append('+')
                            sb.append(currentClass)
                        }
                    }
                    sb.toString
                }
            if (attributeValue ne null)
                added |= addOrAppendToAttributeIfNeeded(attributesImpl, "class", attributeValue, newlyVisibleSubtree, attributeValue == "")
        }
        added
    }

    def addOrAppendToAttributeIfNeeded(attributesImpl: AttributesImpl, name: String, value: String, isNewRepeatIteration: Boolean, isDefaultValue: Boolean) =
        if (isNewRepeatIteration && isDefaultValue)
            false
        else {
            XMLUtils.addOrAppendToAttribute(attributesImpl, name, value)
            true
        }

    def addAttributeIfNeeded(attributesImpl: AttributesImpl, name: String, value: String, isNewRepeatIteration: Boolean, isDefaultValue: Boolean) =
        if (isNewRepeatIteration && isDefaultValue)
            false
        else {
            attributesImpl.addAttribute("", name, name, ContentHandlerHelper.CDATA, value)
            true
        }
}