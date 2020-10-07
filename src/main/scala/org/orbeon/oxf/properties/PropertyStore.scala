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
import org.orbeon.oxf.util.{DateUtilsUsingSaxon, XPath}
import org.orbeon.oxf.xml.XMLConstants._
import org.orbeon.oxf.xml.dom.Extensions._
import org.orbeon.saxon.om.Name10Checker
import org.orbeon.scaxon.NodeConversions._
import org.orbeon.scaxon.SimplePath._

import scala.jdk.CollectionConverters._
import scala.collection.mutable


class PropertyStore(globalPropertySet: PropertySet, processorPropertySets: Map[QName, PropertySet]) {

  def getGlobalPropertySet: PropertySet =
    globalPropertySet

  def getProcessorPropertySet(processorQName: QName): PropertySet =
    processorPropertySets.getOrElse(processorQName, PropertySet.empty)
}

// Construction of a `PropertyStore` currently depends on Saxon classes.
object PropertyStore {

  def parse(doc: Document): PropertyStore = {

    import PropertySet.PropertyParams

    val globalPropertyDefs = mutable.ListBuffer[PropertyParams]()
    val namedPropertySets = mutable.HashMap[QName, mutable.ListBuffer[PropertyParams]]()

    val dw = new DocumentWrapper(doc, null, XPath.GlobalConfiguration)

    // NOTE: The use of `attribute` is for special use of the property store by certain processors.
    // NOTE: Don't use XPathCache, because that depends on properties, which we are initializing here!
    for (node <- (dw descendant "property") ++ (dw descendant "attribute")) {

      val propertyElement = unsafeUnwrapElement(node)

      if (propertyElement.attributeValueOpt("as").isDefined) {

        val typeQName =
          propertyElement.resolveAttValueQName("as", unprefixedIsNoNamespace = true) getOrElse (
            throw new ValidationException(
              s"Missing `as` attribute value",
              propertyElement.getData.asInstanceOf[LocationData]
            )
          )

        if (! Private.SupportedTypes.contains(typeQName))
          throw new ValidationException(
            s"Invalid `as` attribute: ${typeQName.qualifiedName}",
            propertyElement.getData.asInstanceOf[LocationData]
          )

        val name = propertyElement.attributeValue("name")

        val value =
          propertyElement.attributeValueOpt("value") match {
            case Some(valueFromAttribute) => valueFromAttribute
            case None                     => trimAllToEmpty(propertyElement.getText)
          }

        val builder =
          propertyElement.attributeValueOpt("processor-name") match {
            case Some(_) =>
              val processorQName =
                propertyElement.resolveAttValueQName("processor-name", unprefixedIsNoNamespace = true) getOrElse (
                  throw new ValidationException(
                    s"Missing `processor-name` attribute value",
                    propertyElement.getData.asInstanceOf[LocationData]
                  )
                )

              namedPropertySets.getOrElseUpdate(processorQName, mutable.ListBuffer[PropertyParams]())
            case None =>
              globalPropertyDefs
          }

        builder += ((propertyElement, name, typeQName, Private.getObjectFromStringValue(value, typeQName, propertyElement)))
      }
    }

    new PropertyStore(
      PropertySet(globalPropertyDefs),
      namedPropertySets.iterator map { case (name, defs) => name -> PropertySet(defs) } toMap
    )
  }

  private object Private {

    val SupportedTypes =
      Map[QName, (String, Element) => AnyRef](
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

    def getObjectFromStringValue(stringValue: String, typ: QName, element: Element): AnyRef =
      SupportedTypes.get(typ) map (_(stringValue, element)) orNull

    def convertString (value: String, element: Element) = value
    def convertInteger(value: String, element: Element) = new jl.Integer(value)
    def convertBoolean(value: String, element: Element) = jl.Boolean.valueOf(value)
    def convertDate   (value: String, element: Element) = new ju.Date(DateUtilsUsingSaxon.parseISODateOrDateTime(value))

    def convertQName(value: String, element: Element): QName =
      element.resolveAttValueQName("value", unprefixedIsNoNamespace = true) getOrElse
        (throw new ValidationException("QName value not found ", null))

    def convertURI(value: String, element: Element): URI =
      try {
        new URI(value)
      } catch {
        case e: URISyntaxException =>
          throw new ValidationException(e, null)
      }

    def convertNCName(value: String, element: Element): String = {
      if (! Name10Checker.getInstance.isValidNCName(value))
        throw new ValidationException("Not an NCName: " + value, null)
      value
    }

    def convertNMTOKEN(value: String, element: Element): String = {
      if (! Name10Checker.getInstance.isValidNmtoken(value))
        throw new ValidationException("Not an NMTOKEN: " + value, null)
      value
    }

    def convertNMTOKENS(value: String, element: Element): ju.Set[String] = {
      val tokens = value.splitTo[Set]()
      for (token <- tokens) {
        if (! Name10Checker.getInstance.isValidNmtoken(token))
          throw new ValidationException(s"Not an NMTOKENS: $value" , null)
      }
      tokens.asJava
    }

    def convertNonNegativeInteger(value: String, element: Element): jl.Integer = {
      val ret = convertInteger(value, element)
      if (ret < 0)
        throw new ValidationException(s"Not a non-negative integer: $value", null)
      ret
    }
  }
}
