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
import org.orbeon.oxf.xforms.XFormsConstants._

trait ControlExtensionAttributesSupport {

    self: XFormsControl ⇒

    import ControlExtensionAttributesSupport._

    // Optional extension attributes supported by the control
    private[ControlExtensionAttributesSupport] var extensionAttributeValues = Map[QName, String]()

    final def compareExtensionAttributes(other: XFormsControl) =
        extensionAttributeValues == other.extensionAttributeValues

    final def evaluateExtensionAttributes(): Unit =
        extensionAttributeValues = (
            for {
                avtAttributeQName ← StandardExtensionAttributes ++ staticControl.extensionAttributeNames
                attributeValue = element.attributeValue(avtAttributeQName)
                if attributeValue ne null
                resolvedValue = evaluateAvt(attributeValue) // NOTE: This can return null if there is no context
            } yield
                avtAttributeQName → resolvedValue
        ).toMap

    final def markExtensionAttributesDirty() = extensionAttributeValues = Map()

    def getExtensionAttributeValue(attributeName: QName) =
        extensionAttributeValues.get(attributeName) orNull

    /**
     * Add all non-null values of extension attributes to the given list of attributes.
     *
     * @param attributesImpl    attributes to add to
     * @param namespaceURI      restriction on namespace URI, or null if all attributes
     */
    final def addExtensionAttributes(attributesImpl: AttributesImpl, namespaceURI: String) =
        if (extensionAttributeValues ne null) {
            for {
                (currentName, currentValue) ← extensionAttributeValues
                // Skip if namespace URI is excluded
                if (namespaceURI eq null) || namespaceURI == currentName.getNamespaceURI
                if currentName != XFormsConstants.CLASS_QNAME
                if currentValue ne null
                localName = currentName.getName
            } yield
                attributesImpl.addAttribute("", localName, localName, ContentHandlerHelper.CDATA, currentValue)
        }
}

object ControlExtensionAttributesSupport {
    val StandardExtensionAttributes = Seq(STYLE_QNAME, CLASS_QNAME)
}