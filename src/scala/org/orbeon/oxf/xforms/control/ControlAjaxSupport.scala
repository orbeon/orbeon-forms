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
import org.orbeon.oxf.xforms.XFormsConstants._
import collection.mutable.LinkedHashSet
import org.apache.commons.lang3.StringUtils
import AjaxSupport._
import org.orbeon.oxf.xml.{XMLUtils, ContentHandlerHelper}

trait ControlAjaxSupport {

    self: XFormsControl ⇒

    // Whether the control support Ajax updates
    def supportAjaxUpdates = true

    // Whether this control got structural changes during the current request
    def hasStructuralChange = containingDocument.getControlsStructuralChanges.contains(prefixedId)

    // Whether the control support full Ajax updates
    def supportFullAjaxUpdates = true

    def outputAjaxDiff(ch: ContentHandlerHelper, other: XFormsControl, attributesImpl: AttributesImpl, isNewlyVisibleSubtree: Boolean) = ()

    def addAjaxAttributes(attributesImpl: AttributesImpl, isNewlyVisibleSubtree: Boolean, other: XFormsControl) = {
        var added = false

        // Control id
        attributesImpl.addAttribute("", "id", "id", ContentHandlerHelper.CDATA, XFormsUtils.namespaceId(containingDocument, getEffectiveId))

        // Class attribute
        added |= addAjaxClasses(attributesImpl, isNewlyVisibleSubtree, other, this)

        // LHHA
        added |= addAjaxLHHA(other, attributesImpl, isNewlyVisibleSubtree)

        // Visited
        if ((Option(other) map (_.visited) getOrElse false) != visited) {
            XMLUtils.addOrAppendToAttribute(attributesImpl, "visited", visited.toString)
            added |= true
        }

        // Output control-specific attributes
        added |= addAjaxCustomAttributes(attributesImpl, isNewlyVisibleSubtree, other)

        added
    }

    // Label, help, hint, alert
    def addAjaxLHHA(other: XFormsControl, attributesImpl: AttributesImpl, isNewlyVisibleSubtree: Boolean): Boolean = {
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

        // By default, diff only attributes in the xxf:* namespace
        val extensionAttributes = getExtensionAttributes
        (extensionAttributes ne null) && addAttributesDiffs(extensionAttributes, XXFORMS_NAMESPACE_URI)
    }

    final def addAjaxStandardAttributes(originalControl: XFormsSingleNodeControl, ch: ContentHandlerHelper, isNewRepeatIteration: Boolean) {
        val extensionAttributes = StandardExtensionAttributes

        if (extensionAttributes ne null) {
            val control2 = this

            for {
                avtAttributeQName ← extensionAttributes
                if avtAttributeQName != CLASS_QNAME
                value1 = if (originalControl eq null) null else originalControl.getExtensionAttributeValue(avtAttributeQName)
                value2 = control2.getExtensionAttributeValue(avtAttributeQName)
                if value1 != value2
                attributeValue = if (value2 ne null) value2 else ""
                attributesImpl = new AttributesImpl
            } yield {
                attributesImpl.addAttribute("", "id", "id", ContentHandlerHelper.CDATA, XFormsUtils.namespaceId(containingDocument, control2.getEffectiveId))
                addAttributeIfNeeded(attributesImpl, "for", control2.getEffectiveId, isNewRepeatIteration, false)
                addAttributeIfNeeded(attributesImpl, "name", avtAttributeQName.getName, isNewRepeatIteration, false)
                ch.startElement("xxf", XXFORMS_NAMESPACE_URI, "attribute", attributesImpl)
                ch.text(attributeValue)
                ch.endElement()
            }
        }
    }

    def writeMIPs(write: (String, String) ⇒ Unit): Unit =
        write("relevant", isRelevant.toString)

    final def writeMIPsAsAttributes(newAttributes: AttributesImpl): Unit = {
        def write(name: String, value: String) =
            newAttributes.addAttribute(XXFORMS_NAMESPACE_URI, name, XXFORMS_PREFIX + ':' + name, ContentHandlerHelper.CDATA, value)

        writeMIPs(write)
    }
}

// NOTE: Use name different from trait so that the Java compiler is happy
object AjaxSupport {

    val StandardExtensionAttributes = Array(STYLE_QNAME, CLASS_QNAME)

    def addAjaxClasses(attributesImpl: AttributesImpl, newlyVisibleSubtree: Boolean, control1: XFormsControl, control2: XFormsControl): Boolean = {
        var added = false
        val class1 = Option(control1) flatMap (control ⇒ Option(control.getExtensionAttributeValue(CLASS_QNAME)))
        val class2 = Option(control2.getExtensionAttributeValue(CLASS_QNAME))

        if (newlyVisibleSubtree || class1 != class2) {

            val attributeOption =
                if (class1.isEmpty)
                    class2
                else {
                    def tokenize(value: Option[String]) = value map (v ⇒ LinkedHashSet(StringUtils.split(v): _*)) getOrElse LinkedHashSet()

                    val classes1 = tokenize(class1)
                    val classes2 = tokenize(class2)

                    // Classes to remove and to add
                    val toRemove = classes1 -- classes2 map ("-" + _)
                    val toAdd    = classes2 -- classes1 map ("+" + _)
                    
                    Some(toRemove ++ toAdd mkString " ")
                }

            attributeOption foreach { attributeValue ⇒
                added |= addOrAppendToAttributeIfNeeded(attributesImpl, "class", attributeValue, newlyVisibleSubtree, attributeValue == "")
            }
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