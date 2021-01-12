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
package org.orbeon.dom.saxon

import org.orbeon.dom
import org.orbeon.dom.Node
import org.orbeon.oxf.xforms.analysis.model.ModelDefs
import org.orbeon.oxf.xforms.model.InstanceData
import org.orbeon.oxf.xml.XMLConstants
import org.orbeon.saxon.model.{BuiltInAtomicType, SchemaType, Type, Untyped}
import org.orbeon.saxon.om._
import org.orbeon.saxon.value.UntypedAtomicValue
import org.orbeon.xforms.XFormsNames

import scala.util.control.NonFatal


/**
 * This wrapper is an extension of the Saxon node wrapper which is aware of XForms type annotations.
 */
object TypedNodeWrapper {

  private[saxon] def makeTypedWrapper(node: Node, docWrapper: DocumentWrapper, parent: NodeWrapper): NodeWrapper =
    if (node.isInstanceOf[dom.Document])
      docWrapper
    else
      new TypedNodeWrapper(node, docWrapper, parent)

  class TypedValueException(val nodeName: String, val typeName: String, val nodeValue: String) extends RuntimeException
}

class TypedNodeWrapper private (node: dom.Node, docWrapper: DocumentWrapper, parent: NodeWrapper)
  extends ConcreteNodeWrapper(node, docWrapper, parent) {

  override protected def makeWrapper(node: dom.Node, docWrapper: DocumentWrapper, parent: NodeWrapper): NodeWrapper =
    TypedNodeWrapper.makeTypedWrapper(node, docWrapper, parent)

  override def atomize(): AtomicSequence =
    checkTypeAnnotation(new UntypedAtomicValue(_), _.atomize(this))

  override def getSchemaType: SchemaType = {
    val nodeType = InstanceData.getType(node)
    if (nodeType == null) {
      getUntypedType
    } else {
      // Extract QName
      var uri       = nodeType.namespace.uri
      val localname = nodeType.localName

      // For type annotation purposes, `xf:integer` is translated into `xs:integer`. This is because XPath has no
      // knowledge of the XForms union types.
      if (uri == XFormsNames.XFORMS_NAMESPACE_URI && ModelDefs.XFormsVariationTypeNames(localname))
        uri = XMLConstants.XSD_URI

      val schemaType = getConfiguration.getSchemaType(new StructuredQName(nodeType.namespace.prefix, uri, localname))

      if (schemaType eq null)
        getUntypedType // back to default case
      else
        schemaType // return identified type
    }
  }

  private def checkTypeAnnotation[A](untyped: CharSequence => A, typed: SchemaType => A): A = {

    val schemaType = getSchemaType

    if (schemaType == null || schemaType == BuiltInAtomicType.UNTYPED_ATOMIC || schemaType == Untyped.getInstance) {
      untyped(getStringValueCS)
    } else {
//      val stype = getConfiguration.getSchemaType(schemaType)
//      if (stype eq null)
//        throw new XPathException(s"Unknown type annotation `${Err.wrap(getAnnotationTypeName(schemaType))}` in document")
//      else
      try
        typed(schemaType)
      catch {
        case NonFatal(_) =>
          // TODO: Would be good to pass err.getMessage()
          throw new TypedNodeWrapper.TypedValueException(getDisplayName, schemaType.getDisplayName, getStringValue)
      }
    }
  }

  private def getUntypedType: SchemaType =
    getNodeKind match {
      case Type.ATTRIBUTE               => BuiltInAtomicType.UNTYPED_ATOMIC
      case Type.DOCUMENT | Type.ELEMENT => Untyped.getInstance
      case _                            => null
    }
}