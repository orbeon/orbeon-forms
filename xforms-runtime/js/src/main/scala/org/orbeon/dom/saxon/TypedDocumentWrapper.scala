package org.orbeon.dom.saxon

import org.orbeon.dom
import org.orbeon.oxf.util.StaticXPath.SaxonConfiguration

/**
 * This wrapper is an extension of the Saxon node wrapper which is aware of XForms type annotations.
 */
class TypedDocumentWrapper(
  val document      : dom.Document,
  val s             : String,
  val configuration : SaxonConfiguration
) extends DocumentWrapper(document, s, configuration) {

  override protected def makeWrapper(
    node       : dom.Node,
    docWrapper : DocumentWrapper,
    parent     : NodeWrapper
  ): NodeWrapper =
    TypedNodeWrapper.makeTypedWrapper(node, docWrapper, parent)
}