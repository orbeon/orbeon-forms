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

import org.orbeon.datatypes.LocationData
import org.orbeon.dom.saxon.DocumentWrapper
import org.orbeon.dom.{Document, QName}
import org.orbeon.oxf.common.ValidationException
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.util.XPath
import org.orbeon.oxf.xml.dom.Extensions._
import org.orbeon.scaxon.NodeInfoConversions._
import org.orbeon.scaxon.SimplePath._

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

        if (! PropertySet.isSupportedType(typeQName))
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

        builder += PropertyParams(propertyElement.allInScopeNamespacesAsStrings, name, typeQName, value)
      }
    }

    new PropertyStore(
      PropertySet(globalPropertyDefs),
      namedPropertySets.iterator map { case (name, defs) => name -> PropertySet(defs) } toMap
    )
  }
}
