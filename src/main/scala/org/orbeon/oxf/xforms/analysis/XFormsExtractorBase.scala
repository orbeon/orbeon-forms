/**
 * Copyright (C) 2014 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.analysis

import org.orbeon.oxf.xml.{XMLReceiverHelper, XMLReceiver, ForwardingXMLReceiver}
import org.orbeon.oxf.properties.Properties
import org.orbeon.oxf.xforms.{XFormsStaticStateImpl, XFormsConstants, XFormsProperties}
import XFormsProperties._
import XFormsConstants._
import scala.collection.JavaConverters._
import org.xml.sax.helpers.AttributesImpl
import org.xml.sax.Attributes
import collection.mutable
import org.orbeon.oxf.util.ScalaUtils._

abstract class XFormsExtractorBase(xmlReceiver: XMLReceiver)
    extends ForwardingXMLReceiver(xmlReceiver)
    with ExtractorProperties

trait ExtractorProperties extends ForwardingXMLReceiver {

    // This code deals with property values not as parsed values (which are String, Boolean, Int), but as String values
    // only. This is because some XForms values can be AVTs. This means that it is possible that some values will be
    // kept because they differ lexically, even though their value might be the same. For example, `true` and `TRUE`
    // (although this case is degenerate: `TRUE` really shouldn't be allowed, but it is by Java!). In general, this
    // is not a problem, and the worst case scenario is that a few too many properties are kept in the static state.

    private val unparsedLocalProperties = mutable.HashMap[String, String]()

    protected def outputNonDefaultProperties(): Unit = {

        val propertySet = Properties.instance.getPropertySet

        val propertiesToKeep = {
            for {
                (name, prop) ← SUPPORTED_DOCUMENT_PROPERTIES.asScala
                defaultValue = prop.defaultValue
                globalValue  = propertySet.getObject(XFORMS_PROPERTY_PREFIX + name, defaultValue)
            } yield
                unparsedLocalProperties.get(name) match {
                    case Some(localValue) ⇒ localValue  != defaultValue.toString option (name → localValue)
                    case None             ⇒ globalValue != defaultValue          option (name → globalValue.toString)
                }
        } flatten

        val newAttributes = new AttributesImpl

        for ((name, value) ← propertiesToKeep)
            newAttributes.addAttribute(XXFORMS_NAMESPACE_URI, name, "xxf:" + name, XMLReceiverHelper.CDATA, value)

        super.startPrefixMapping("xxf", XXFORMS_NAMESPACE_URI)
        super.startElement("", "properties", "properties", newAttributes)
        super.endElement("", "properties", "properties")
        super.endPrefixMapping("xxf")
    }

    protected def isStoreNoscriptTemplate =
        XFormsStaticStateImpl.isStoreNoscriptTemplate(unparsedLocalProperties)

    protected def addPropertiesIfAny(attributes: Attributes): Unit =
        for {
            i ← 0 until attributes.getLength
            if attributes.getURI(i) == XXFORMS_NAMESPACE_URI
        } locally {
            unparsedLocalProperties.getOrElseUpdate(attributes.getLocalName(i), attributes.getValue(i))
        }
}
