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

import java.net.URI

import org.orbeon.oxf.properties.Properties
import org.orbeon.oxf.util.ScalaUtils._
import org.orbeon.oxf.xforms.XFormsConstants._
import org.orbeon.oxf.xforms.XFormsProperties._
import org.orbeon.oxf.xforms.XFormsStaticStateImpl
import org.orbeon.oxf.xml.{ForwardingXMLReceiver, NamespaceContext, XMLReceiver, XMLReceiverHelper}
import org.xml.sax.Attributes
import org.xml.sax.helpers.AttributesImpl

import scala.collection.JavaConverters._
import scala.collection.mutable

case class XMLElementDetails(
  id                  : String,
  xmlBase             : URI,
  xmlLang             : String,
  xmlLangAvtId        : String,
  scope               : XXBLScope,
  isModel             : Boolean,
  isXForms            : Boolean,
  isXXForms           : Boolean,
  isEXForms           : Boolean,
  isXBL               : Boolean,
  isXXBL              : Boolean,
  isExtension         : Boolean,
  isXFormsOrExtension : Boolean
)

abstract class XFormsExtractorBase(xmlReceiver: XMLReceiver)
  extends ForwardingXMLReceiver(xmlReceiver)
  with ExtractorProperties
  with ExtractorOutput

trait ExtractorOutput extends XMLReceiver {

  // Output receiver, which can be null when our subclass is `ScopeExtractor`. Urgh.
  // NOTE: Ugly until we get trait parameters!
  def getXMLReceiver: XMLReceiver

  protected val inputNamespaceContext       = new NamespaceContext
  private   var outputNamespaceContextStack = inputNamespaceContext.current :: Nil

  override def startPrefixMapping(prefix: String, uri: String): Unit =
    if (getXMLReceiver ne null)
      inputNamespaceContext.startPrefixMapping(prefix, uri)

  override def endPrefixMapping(s: String): Unit = ()

  def startStaticStateElement(uri: String, localname: String, qName: String, attributes: Attributes): Unit =
    if (getXMLReceiver ne null) {
      outputNamespaceContextStack ::= inputNamespaceContext.current
      iterateChangedMappings foreach (getXMLReceiver.startPrefixMapping _).tupled
      getXMLReceiver.startElement(uri, localname, qName, attributes)
    }

  def endStaticStateElement(uri: String, localname: String, qName: String): Unit =
    if (getXMLReceiver ne null) {
      getXMLReceiver.endElement(uri, localname, qName)
      iterateChangedMappings foreach { case (prefix, _) ⇒ getXMLReceiver.endPrefixMapping(prefix) }
      outputNamespaceContextStack = outputNamespaceContextStack.tail
    }

  // Compare the mappings for the last two elements output and return the mappings that have changed
  private def iterateChangedMappings = {

    val oldMappings = outputNamespaceContextStack.tail.head.mappings
    val newMappings = outputNamespaceContextStack.head.mappings

    if (oldMappings eq newMappings) {
      // Optimized case where mappings haven't changed at all
      Iterator.empty
    } else {
      for {
        newMapping @ (newPrefix, newURI) ← newMappings.iterator
        if (
          oldMappings.get(newPrefix) match {
            case None                             ⇒ true  // new mapping
            case Some(oldURI) if oldURI != newURI ⇒ true  // changed mapping, including to/from undeclaration with ""
            case _                                ⇒ false // unchanged mapping
          }
        )
      } yield
        newMapping
    }
  }
}

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
