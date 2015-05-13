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
import org.orbeon.oxf.xml.XMLReceiverHelper
import org.xml.sax.helpers.AttributesImpl
import org.orbeon.oxf.xforms.XFormsConstants._

trait ControlExtensionAttributesSupport {

    self: XFormsControl ⇒

    import ControlExtensionAttributesSupport._

    // Optional extension attributes supported by the control
    private[ControlExtensionAttributesSupport] var _extensionAttributes: Option[Map[QName, String]] = None

    final def evaluatedExtensionAttributes =
        _extensionAttributes getOrElse {

            val result =
                if (staticControl eq null)
                    Map.empty[QName, String]
                else if (isRelevant)
                    // NOTE: evaluateAvt can return null if there is no context
                    // WARNING: don't use `mapValues`, which return a view which can't be stored in the back control tree
                    staticControl.extensionAttributes map { case (k, v) ⇒ k → (Option(evaluateAvt(v)) getOrElse "") }
                else
                    // Don't attempt to evaluate expression when the control is non-relevant
                    staticControl.nonRelevantExtensionAttributes

            _extensionAttributes = Some(result)
            result
        }

    final def evaluateNonRelevantExtensionAttribute(): Unit =
        _extensionAttributes = None

    final def markExtensionAttributesDirty() =
        _extensionAttributes = None

    final def compareExtensionAttributes(other: XFormsControl) =
        evaluatedExtensionAttributes == other.evaluatedExtensionAttributes

    // NOTE: Overridden by some tests
    def extensionAttributeValue(attributeName: QName) =
        evaluatedExtensionAttributes.get(attributeName)

    final def jExtensionAttributeValue(attributeName: QName) =
        evaluatedExtensionAttributes.get(attributeName).orNull

    // Add all non-null values to the given list of attributes, filtering by namespace URI
    // NOTE: The `class` attribute is excluded because handled separately.
    // NOTE: The `accept` attribute is also handled separately by the handler.
    final def addExtensionAttributesExceptClassAndAcceptForHandler(attributesImpl: AttributesImpl, namespaceURI: String): Unit =
        for {
            (name, value) ← evaluatedExtensionAttributes
            if name.getNamespaceURI == namespaceURI && ! StandardAttributesToFilterOnHandler(name) && name != NAVINDEX_QNAME
            if value ne null
            localName = name.getName
        } attributesImpl.addAttribute("", localName, localName, XMLReceiverHelper.CDATA, value)

    final def addExtensionAttributesExceptClassAndAcceptForAjax(originalControl: XFormsControl, namespaceURI: String, isNewlyVisibleSubtree: Boolean)(ch: XMLReceiverHelper): Unit =
        for {
            name ← staticControl.extensionAttributes.keys
            if name.getNamespaceURI == namespaceURI && ! StandardAttributesToFilterOnHandler(name)
        } outputAttributeElement(originalControl, name.getName, _.extensionAttributeValue(name).orNull, isNewlyVisibleSubtree)(ch)
}

private object ControlExtensionAttributesSupport {
    val StandardAttributesToFilterOnHandler = Set(CLASS_QNAME, ACCEPT_QNAME)
}