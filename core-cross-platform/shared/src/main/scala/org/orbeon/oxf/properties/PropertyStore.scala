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
import org.orbeon.oxf.properties.PropertySet.PropertyParams
import org.orbeon.oxf.util.StaticXPath
import org.orbeon.oxf.util.StringUtils.*
import org.orbeon.oxf.xml.XMLConstants
import org.orbeon.oxf.xml.dom.Extensions.*
import org.orbeon.properties.api
import org.orbeon.properties.api.{PropertyDefinition, PropertyDefinitions}
import org.orbeon.scaxon.NodeInfoConversions.*
import org.orbeon.scaxon.SimplePath.*

import java.util
import java.util.Optional
import scala.collection.mutable
import scala.jdk.CollectionConverters.*
import scala.jdk.OptionConverters.{RichOption, RichOptional}


trait PropertyStore {
  def globalPropertySet: PropertySet
  def processorPropertySet(processorQName: QName): PropertySet
}

case class SimplePropertyStore(globalPropertySet: PropertySet, private val processorPropertySets: Map[QName, PropertySet])
  extends PropertyStore {
  def processorPropertySet(processorQName: QName): PropertySet =
    processorPropertySets.getOrElse(processorQName, PropertySet.empty)
}

// Construction of a `PropertyStore` currently depends on Saxon classes.
object PropertyStore {

  val empty: PropertyStore =
    SimplePropertyStore(PropertySet.empty, Map.empty)

  def fromPropertyDefinitions(propertyDefinitions: Iterable[PropertyDefinition], eTag: api.ETag): PropertyStore = {

    def makePropertyParams(pp: PropertyDefinition) =
      PropertyParams(
        namespaces   = pp.getNamespaces.asScala.toMap,
        name         = pp.getName,
        typeQName    = QName(pp.getType, XMLConstants.XSD_NAMESPACE),
        stringValue  = pp.getValue,
      )

    val globalPropertySet =
      PropertySet(
        propertyDefinitions.view.filter(_.getCategory.isEmpty).map(makePropertyParams),
        eTag
      )

    val uniqueCategoriesAsList =
      propertyDefinitions
        .iterator
        .map(_.getCategory)
        .distinct
        .flatMap(_.toScala)
        .toList

    val processorPropertySetsAsList =
      uniqueCategoriesAsList.map { category =>
        QName.fromUriQualifiedName(category).getOrElse(throw new IllegalArgumentException(category)) ->
          PropertySet(
            propertyDefinitions
              .view
              .filter(d => ! d.getCategory.isEmpty && d.getCategory.get() == category)
              .map(makePropertyParams),
            eTag
          )
      }

    SimplePropertyStore(
      globalPropertySet,
      processorPropertySetsAsList.toMap
    )
  }

  // Used by `XIncludeProcessor`, `SaxonXQueryProcessor`, `XSLTTransformer`
  def parse(doc: Document, eTag: api.ETag): PropertyStore = {

    import PropertySet.PropertyParams

    val globalPropertyDefs = mutable.ListBuffer[PropertyParams]()
    val namedPropertySets = mutable.HashMap[QName, mutable.ListBuffer[PropertyParams]]()

    val dw = new DocumentWrapper(doc, null, StaticXPath.GlobalConfiguration)

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

    SimplePropertyStore(
      PropertySet(globalPropertyDefs, eTag),
      namedPropertySets
        .iterator
        .map { case (name, defs) => name -> PropertySet(defs, eTag) }
        .toMap
    )
  }

  def parseToPropertyDefinitions(doc: Document): PropertyDefinitions = {

    val allPropertyDefinitions = mutable.ListBuffer[PropertyDefinition]()

    val dw = new DocumentWrapper(doc, null, StaticXPath.GlobalConfiguration)

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

        val valueFromAttOrText =
          propertyElement.attributeValueOpt("value") match {
            case Some(valueFromAttribute) => valueFromAttribute
            case None                     => trimAllToEmpty(propertyElement.getText)
          }

        val categoryOpt =
        propertyElement.attributeValueOpt("processor-name").map(_ =>
          propertyElement
            .resolveAttValueQName("processor-name", unprefixedIsNoNamespace = true)
            .getOrElse(
              throw new ValidationException(
                s"Missing `processor-name` attribute value",
                propertyElement.getData.asInstanceOf[LocationData]
              )
            )
            .uriQualifiedName
        )

        allPropertyDefinitions +=
          new PropertyDefinition {
            def getName      : String                   = propertyElement.attributeValue("name")
            def getValue     : String                   = valueFromAttOrText
            def getType      : String                   = typeQName.localName
            def getNamespaces: util.Map[String, String] = propertyElement.allInScopeNamespacesAsStrings.asJava
            def getCategory  : Optional[String]         = categoryOpt.toJava
          }
      }
    }

    allPropertyDefinitions.asJava
  }
}
