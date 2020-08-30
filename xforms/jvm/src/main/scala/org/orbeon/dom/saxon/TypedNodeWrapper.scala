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

import org.orbeon.dom.{Document, Node}
import org.orbeon.oxf.xforms.analysis.model.Model
import org.orbeon.oxf.xforms.model.InstanceData
import org.orbeon.oxf.xml.XMLConstants
import org.orbeon.saxon.`type`.{SchemaType, Type}
import org.orbeon.saxon.om._
import org.orbeon.saxon.trans.{Err, XPathException}
import org.orbeon.saxon.value.{UntypedAtomicValue, Value}
import org.orbeon.xforms.XFormsNames

import scala.util.control.NonFatal

/**
 * This wrapper is an extension of the Saxon node wrapper which is aware of XForms type annotations.
 */
object TypedNodeWrapper {
  private[saxon] def makeTypedWrapper(node: Node, docWrapper: DocumentWrapper, parent: NodeWrapper): NodeWrapper =
    if (node.isInstanceOf[Document])
      docWrapper
    else {
      val wrapper = new TypedNodeWrapper(node, parent)
      wrapper.docWrapper = docWrapper
      wrapper
    }

  class TypedValueException(val nodeName: String, val typeName: String, val nodeValue: String) extends RuntimeException
}

class TypedNodeWrapper private (node: Node, parent: NodeWrapper)
  extends NodeWrapper(node, parent) {

  override protected def makeWrapper(node: Node, docWrapper: DocumentWrapper, parent: NodeWrapper): NodeWrapper =
    TypedNodeWrapper.makeTypedWrapper(node, docWrapper, parent)

  override def getTypedValue: SequenceIterator =
    checkTypeAnnotation(s => SingletonIterator.makeIterator(new UntypedAtomicValue(s)), _.getTypedValue(this))

  override def atomize: Value =
    checkTypeAnnotation(new UntypedAtomicValue(_), _.atomize(this))

  override def getTypeAnnotation: Int = {
    val nodeType = InstanceData.getType(node.asInstanceOf[Node])
    if (nodeType == null) {
      getUntypedType
    } else {
      // Extract QName
      var uri       = nodeType.namespace.uri
      val localname = nodeType.localName

      // For type annotation purposes, `xf:integer` is translated into `xs:integer`. This is because XPath has no
      // knowledge of the XForms union types.
      if (uri == XFormsNames.XFORMS_NAMESPACE_URI && Model.XFormsVariationTypeNames(localname))
        uri = XMLConstants.XSD_URI

      val requestedTypeFingerprint = StandardNames.getFingerprint(uri, localname)
      if (requestedTypeFingerprint == -1)
        getUntypedType // back to default case
      else
        requestedTypeFingerprint // return identified type
    }
  }

  private def checkTypeAnnotation[A](untyped: String => A, typed: SchemaType => A): A = {
    var annotation = getTypeAnnotation
    if ((annotation & NodeInfo.IS_DTD_TYPE) != 0)
      annotation = StandardNames.XS_UNTYPED_ATOMIC
    annotation &= NamePool.FP_MASK
    if (annotation == -1 || annotation == StandardNames.XS_UNTYPED_ATOMIC || annotation == StandardNames.XS_UNTYPED) {
      untyped(getStringValueCS)
    } else {
      val stype = getConfiguration.getSchemaType(annotation)
      if (stype eq null)
        throw new XPathException(s"Unknown type annotation `${Err.wrap(getAnnotationTypeName(annotation))}` in document")
      else
        try
          typed(stype)
        catch {
          case NonFatal(_) =>
            // TODO: Would be good to pass err.getMessage()
            throw new TypedNodeWrapper.TypedValueException(getDisplayName, getAnnotationTypeName(annotation), getStringValue)
        }
    }
  }

  private def getAnnotationTypeName(annotation: Int): String =
    try
      getNamePool.getDisplayName(annotation)
    catch {
      case NonFatal(_) =>
        annotation.toString
    }

  private def getUntypedType: Int =
    if (getNodeKind == Type.ATTRIBUTE)
      StandardNames.XS_UNTYPED_ATOMIC
    else
      StandardNames.XS_UNTYPED
}