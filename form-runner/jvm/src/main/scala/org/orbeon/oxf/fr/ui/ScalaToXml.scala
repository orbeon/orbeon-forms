/**
  * Copyright (C) 2007 Orbeon, Inc.
  *
  * This program is free software; you can redistribute it and/or modify it under the terms of the
  * GNU Lesser General Public License as published by the Free Software Foundation; either version
  *  2.1 of the License, or (at your option) any later version.
  *
  * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  * See the GNU Lesser General Public License for more details.
  *
  * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
  */
package org.orbeon.oxf.fr.ui

import io.circe.syntax._
import io.circe.{Decoder, Encoder, Json, parser}
import org.orbeon.dom
import org.orbeon.dom.QName
import org.orbeon.dom.saxon.DocumentWrapper
import org.orbeon.oxf.json.{Converter, Symbols}
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.util.XPath
import org.orbeon.oxf.xforms.NodeInfoFactory
import org.orbeon.oxf.xforms.NodeInfoFactory.elementInfo
import org.orbeon.oxf.xforms.action.XFormsAPI
import org.orbeon.oxf.xml.TransformerUtils
import org.orbeon.saxon.om.{DocumentInfo, NodeInfo}
import org.orbeon.scaxon.Implicits._
import org.orbeon.scaxon.SimplePath._
import org.orbeon.xml.NamespaceMapping

import scala.util.{Failure, Success, Try}
import scala.collection.compat._


trait ScalaToXml {

  val mustConvertAdtType: String => Boolean = (name: String) => name.headOption exists (_.isUpper)

  type MyState

  implicit val encoder: Encoder[MyState]
  implicit val decoder: Decoder[MyState]

  def encode(state: MyState)    : String = state.asJson.noSpaces
  def decode(jsonString: String): Try[MyState] = parser.decode[MyState](jsonString).fold(Failure.apply, Success.apply)

  private val TypeQName: QName = QName("type")

  private val StandardJsonTypes = Set(
    Symbols.String,
    Symbols.Number,
    Symbols.Boolean,
    Symbols.Null,
    Symbols.Object,
    Symbols.Array
  )

  def simplifiedXmlToState(rootElem: NodeInfo): Try[MyState] = {

    require(rootElem.isElement)

    // Create XForms JSON document
    val simplifiedXmlDoc = new DocumentWrapper(
      dom.Document(Symbols.JSON) |!>
        (_.getRootElement.addAttribute(Symbols.Type, Symbols.Object)),
      null,
      XPath.GlobalConfiguration
    )

    XFormsAPI.insert(
      into   = simplifiedXmlDoc.rootElement,
      origin = rootElem child *
    )

    val fullXmlDoc = simplifiedXmlToFullXml(simplifiedXmlDoc)

    val jsonString = Converter.xmlToJsonString(fullXmlDoc, strict = true)

    decode(jsonString)
  }

  def stateToFullXml(state: MyState): DocumentInfo =
    Converter.jsonStringToXmlDoc(encode(state))

  def fullXmlToSimplifiedXml(
    fullXmlDoc       : DocumentInfo,
    typeName         : String = Symbols.Object,
    namespaceMapping : NamespaceMapping = NamespaceMapping.EmptyMapping
  ): DocumentInfo = {

    val simplifiedXmlDoc = TransformerUtils.extractAsMutableDocument(fullXmlDoc)

    // Update type on root element
    if (mustConvertAdtType(typeName))
      XFormsAPI.setvalue(ref = simplifiedXmlDoc.rootElement att TypeQName, value = typeName)

    // Add namespaces if needed
    for ((prefix, uri) <- namespaceMapping.mapping)
      XFormsAPI.insert(into = simplifiedXmlDoc.rootElement, origin = NodeInfoFactory.namespaceInfo(prefix, uri), doDispatch = false)

    val elemsForAdtTypes =
      simplifiedXmlDoc descendant * filter (e => mustConvertAdtType(e.localname))

    // `reverse` so that nested elements are moved first
    elemsForAdtTypes.to(List).reverse foreach { elem =>

      val parent   = elem.parentUnsafe
      val typeName = elem.localname
      val children = elem child *

      XFormsAPI.setvalue(parent att TypeQName, typeName)
      XFormsAPI.insert(into = parent, origin = children, doDispatch = false)
      XFormsAPI.delete(ref = elem, doDispatch = false)
    }

    // Mutable -> immutable
    TransformerUtils.stringToTinyTree(XPath.GlobalConfiguration, TransformerUtils.tinyTreeToString(simplifiedXmlDoc), false, false)
  }

  def simplifiedXmlToFullXml(simplifiedXmlDoc: DocumentInfo): DocumentInfo = {

    val fullXmlDoc = TransformerUtils.extractAsMutableDocument(simplifiedXmlDoc)

    val elemsWithNonStandardType = fullXmlDoc descendant * filter { elem =>
      elem attValueOpt TypeQName match {
        case Some(typ) if ! StandardJsonTypes(typ) => true
        case _ => false
      }
    }

    // `reverse` so that nested elements are moved first
    elemsWithNonStandardType.to(List).reverse foreach { elem =>

      val typeAtt  = elem /@ TypeQName
      val typeName = typeAtt.stringValue
      val children = elem child *

      XFormsAPI.setvalue(ref = typeAtt, value = Symbols.Object)
      XFormsAPI.delete(ref = children)

      XFormsAPI.insert(
        into   = elem,
        origin = elementInfo(
          qName   = typeName,
          content = NodeInfoFactory.attributeInfo(TypeQName, Symbols.Object) :: children.to(List)
        )
      )
    }

    fullXmlDoc
  }

  def configToJson(config: MyState): Json =
    config.asJson

  def configToJsonStringCompact(config: MyState): String =
    config.asJson.noSpaces

  def configToJsonStringIndented(config: MyState): String =
    config.asJson.spaces2

  def configToXml(config: MyState): DocumentInfo =
    fullXmlToSimplifiedXml(stateToFullXml(config))
}
