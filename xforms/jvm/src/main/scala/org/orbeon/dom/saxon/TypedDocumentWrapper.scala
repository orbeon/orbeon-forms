package org.orbeon.dom.saxon

import org.orbeon.dom.{Document, Node}
import org.orbeon.saxon.Configuration

/**
 * This wrapper is an extension of the Saxon node wrapper which is aware of XForms type annotations.
 */
class TypedDocumentWrapper(
  val document      : Document,
  val s             : String,
  val configuration : Configuration
) extends DocumentWrapper(document, s, configuration) {

  override protected def makeWrapper(
    node       : Node,
    docWrapper : DocumentWrapper,
    parent     : NodeWrapper
  ): NodeWrapper =
    TypedNodeWrapper.makeTypedWrapper(node, docWrapper, parent)
}