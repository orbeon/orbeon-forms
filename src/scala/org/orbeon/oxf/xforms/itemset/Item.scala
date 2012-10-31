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
package org.orbeon.oxf.xforms.itemset

import Item._
import collection.JavaConverters._
import java.util.{Map ⇒ JMap}
import org.apache.commons.lang3.StringUtils
import org.dom4j.QName
import org.orbeon.oxf.xforms.XFormsUtils._
import org.orbeon.oxf.xforms.control.XFormsControl.getEscapedHTMLValue
import org.orbeon.oxf.xml.ContentHandlerHelper
import org.orbeon.oxf.xml.XMLUtils.escapeXMLMinimal
import org.orbeon.oxf.xml.dom4j.LocationData

/**
 * Represents an item (xforms:item, xforms:choice, or item in itemset).
 */
case class Item(label: Label, value: String, attributes: Map[QName, String])(val position: Int, encode: Boolean)
        extends ItemContainer {

    assert(attributes ne null)

    // NOTE: As of 2010-08-18, label can be null in these cases:
    //
    // - xforms:choice with (see XFormsUtils.getElementValue())
    //   - single-node binding that doesn't point to an acceptable item
    //   - value attribute but the evaluation context is empty
    //   - exception when dereferencing an @src attribute
    // - xforms|input:xxforms-type(xs:boolean)

    def jAttributes = attributes.asJava

    def externalValue   = Option(value) map (v ⇒ if (encode) position.toString else v) getOrElse ""
    def javaScriptValue = escapeJavaScript(externalValue)

    def javaScriptLabel(locationData: LocationData) =
        Option(label) map (_.javaScriptValue(locationData)) getOrElse ""

    // Implement equals by hand because children are not part of the case class
    // NOTE: The compiler does not generate equals for case classes that come with an equals! So can't use super to
    // reach compiler-generated case class equals.
    override def equals(other: Any) = other match {
        case other: Item ⇒
            label         == other.label         &&
            externalValue == other.externalValue &&
            attributes    == other.attributes    &&
            super.equals(other)
        case _ ⇒ false
    }
}

object Item {

    // Value is encrypted if requested, except with single selection if the value is empty [WHY?]
    def apply(position: Int, isMultiple: Boolean, encode: Boolean, attributes: JMap[QName, String], label: Label, value: String): Item =
        Item(label, value, if (attributes eq null) Map() else attributes.asScala.toMap)(position, encode)

    // Represent a label
    case class Label(label: String, isHTML: Boolean) {
        def streamAsHTML(ch: ContentHandlerHelper, locationData: LocationData): Unit =
            if (isHTML)
                streamHTMLFragment(ch.getXmlReceiver, label, locationData, "")
            else
                ch.text(StringUtils.defaultString(label))

        def javaScriptValue(locationData: LocationData) =
            escapeJavaScript(
                if (isHTML)
                    getEscapedHTMLValue(locationData, label)
                else
                    escapeXMLMinimal(label))
    }
}