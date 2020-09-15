/**
 * Copyright (C) 2016 Orbeon, Inc.
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
package org.orbeon.oxf.properties

import java.net.{URI, URISyntaxException}
import java.{lang => jl, util => ju}

import org.orbeon.datatypes.LocationData
import org.orbeon.dom.saxon.DocumentWrapper
import org.orbeon.dom.{Document, Element, QName}
import org.orbeon.oxf.common.ValidationException
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.util.{DateUtils, XPath}
import org.orbeon.oxf.xml.XMLConstants._
import org.orbeon.oxf.xml.dom.Extensions._
import org.orbeon.saxon.om.Name10Checker
import org.orbeon.scaxon.NodeConversions._
import org.orbeon.scaxon.SimplePath._

import scala.collection.JavaConverters._
import scala.collection.mutable

// TODO: Find way to get rid of `mutable.Map`. Check callers' expectations.
class PropertyStore(globalPropertySet: PropertySet, processorPropertySets: mutable.Map[QName, PropertySet]) {

  def getGlobalPropertySet: PropertySet =
    globalPropertySet

  def getProcessorPropertySet(processorQName: QName): PropertySet =
    processorPropertySets.getOrElseUpdate(processorQName, new PropertySet)
}

object PropertyStore {

  def parse(doc: Document): PropertyStore = {

    val globalPropertySet = new PropertySet
    val processorPropertySets = mutable.HashMap[QName, PropertySet]()

    def getProcessorPropertySet(processorQName: QName): PropertySet =
      processorPropertySets.getOrElseUpdate(processorQName, new PropertySet)

    val dw = new DocumentWrapper(doc, null, XPath.GlobalConfiguration)

    // NOTE: The use of `attribute` is for special use of the property store by certain processors.
    // NOTE: Don't use XPathCache, because that depends on properties, which we are initializing here!
    for (node <- (dw descendant "property") ++ (dw descendant "attribute")) {

      val propertyElement = unsafeUnwrapElement(node)

      if (propertyElement.attributeValueOpt("as").isDefined) {

        val typeQName = propertyElement.resolveAttValueQName("as", unprefixedIsNoNamespace = true)

        if (! PropertyStore.SupportedTypes.contains(typeQName))
          throw new ValidationException(s"Invalid `as` attribute: ${typeQName.qualifiedName}", propertyElement.getData.asInstanceOf[LocationData])

        val name = propertyElement.attributeValue("name")

        val value =
          propertyElement.attributeValueOpt("value") match {
            case Some(valueFromAttribute) => valueFromAttribute
            case None                     => trimAllToEmpty(propertyElement.getText)
          }

        propertyElement.attributeValueOpt("processor-name") match {
          case Some(_) =>
            val processorQName = propertyElement.resolveAttValueQName("processor-name", unprefixedIsNoNamespace = true)
            getProcessorPropertySet(processorQName).setProperty(propertyElement, name, typeQName, value)
          case None =>
            globalPropertySet.setProperty(propertyElement, name, typeQName, value)
        }
      }
    }

    new PropertyStore(globalPropertySet, processorPropertySets)
  }

  private val SupportedTypes = Map[QName, (String, Element) => AnyRef](
    XS_STRING_QNAME             -> convertString,
    XS_INTEGER_QNAME            -> convertInteger,
    XS_BOOLEAN_QNAME            -> convertBoolean,
    XS_DATE_QNAME               -> convertDate,
    XS_DATETIME_QNAME           -> convertDate,
    XS_QNAME_QNAME              -> convertQName,
    XS_ANYURI_QNAME             -> convertURI,
    XS_NCNAME_QNAME             -> convertNCName,
    XS_NMTOKEN_QNAME            -> convertNMTOKEN,
    XS_NMTOKENS_QNAME           -> convertNMTOKENS,
    XS_NONNEGATIVEINTEGER_QNAME -> convertNonNegativeInteger
  )

  /**
    * Convert a property's string value to an object.
    *
    * @param element Element on which the property is defined. Used for QName resolution if needed.
    * @return        object or null
    */
  def getObjectFromStringValue(stringValue: String, typ: QName, element: Element): AnyRef =
    SupportedTypes.get(typ) map (_(stringValue, element)) orNull

  private def convertString (value: String, element: Element) = value
  private def convertInteger(value: String, element: Element) = new jl.Integer(value)
  private def convertBoolean(value: String, element: Element) = jl.Boolean.valueOf(value)
  private def convertDate   (value: String, element: Element) = new ju.Date(DateUtils.parseISODateOrDateTime(value))

  private def convertQName(value: String, element: Element): QName =
    element.resolveAttValueQName("value", unprefixedIsNoNamespace = true)

  private def convertURI(value: String, element: Element): URI =
    try {
      new URI(value)
    } catch {
      case e: URISyntaxException =>
        throw new ValidationException(e, null)
    }

  private def convertNCName(value: String, element: Element): String = {
    if (! Name10Checker.getInstance.isValidNCName(value))
      throw new ValidationException("Not an NCName: " + value, null)
    value
  }

  private def convertNMTOKEN(value: String, element: Element): String = {
    if (! Name10Checker.getInstance.isValidNmtoken(value))
      throw new ValidationException("Not an NMTOKEN: " + value, null)
    value
  }

  private def convertNMTOKENS(value: String, element: Element): ju.Set[String] = {
    val tokens = value.splitTo[Set]()
    for (token <- tokens) {
      if (! Name10Checker.getInstance.isValidNmtoken(token))
        throw new ValidationException(s"Not an NMTOKENS: $value" , null)
    }
    tokens.asJava
  }

  private def convertNonNegativeInteger(value: String, element: Element): jl.Integer = {
    val ret = convertInteger(value, element)
    if (ret < 0)
      throw new ValidationException(s"Not a non-negative integer: $value", null)
    ret
  }
}
