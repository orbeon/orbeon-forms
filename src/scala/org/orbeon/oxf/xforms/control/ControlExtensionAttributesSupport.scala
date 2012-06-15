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
import org.orbeon.oxf.xml.ContentHandlerHelper
import org.xml.sax.helpers.AttributesImpl
import scala.collection.JavaConverters._
import java.util.{Map ⇒ JMap, HashMap ⇒ JHashMap}

trait ControlExtensionAttributesSupport {

    self: XFormsControl ⇒

    // Optional extension attributes supported by the control
    // TODO: must be evaluated lazily
    private var extensionAttributesValues: JMap[QName, String] = null

    final def compareExtensionAttributes(other: XFormsControl): Boolean = {
        if (extensionAttributesValues ne null)
            for ((currentName, currentValue) ← extensionAttributesValues.asScala)
                if (currentValue != other.getExtensionAttributeValue(currentName))
                    return false

        true
    }

    final def evaluateExtensionAttributes(attributeQNames: Array[QName]) {
        for (avtAttributeQName ← attributeQNames) {
            val attributeValue = element.attributeValue(avtAttributeQName)
            if (attributeValue ne null) {
                // NOTE: This can return null if there is no context
                val resolvedValue = evaluateAvt(attributeValue)
                if (extensionAttributesValues eq null)
                    extensionAttributesValues = new JHashMap[QName, String]
                extensionAttributesValues.put(avtAttributeQName, resolvedValue)
            }
        }
    }

    final def markExtensionAttributesDirty() =
        Option(extensionAttributesValues) foreach
            (_.clear())

    // Return an optional static list of extension attribute QNames provided by the control. If present these
    // attributes are evaluated as AVTs and copied over to the outer control element.
    def getExtensionAttributes: Array[QName] = null

    def getExtensionAttributeValue(attributeName: QName) =
        Option(extensionAttributesValues) map (_.get(attributeName)) orNull

    /**
     * Add all non-null values of extension attributes to the given list of attributes.
     *
     * @param attributesImpl    attributes to add to
     * @param namespaceURI      restriction on namespace URI, or null if all attributes
     */
    final def addExtensionAttributes(attributesImpl: AttributesImpl, namespaceURI: String) =
        if (extensionAttributesValues ne null) {
            for {
                (currentName, currentValue) ← extensionAttributesValues.asScala
                // Skip if namespace URI is excluded
                if (namespaceURI eq null) || namespaceURI == currentName.getNamespaceURI
                if currentName != XFormsConstants.CLASS_QNAME
                if currentValue ne null
                localName = currentName.getName
            } yield
                attributesImpl.addAttribute("", localName, localName, ContentHandlerHelper.CDATA, currentValue)
        }

    final def updateExtensionAttributesCopy(copy: XFormsControl) =
        Option(extensionAttributesValues) foreach
            (e ⇒ copy.extensionAttributesValues = new JHashMap[QName, String](e))
}
