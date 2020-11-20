package org.orbeon.dom.saxon

import org.orbeon.dom


object ConcreteNodeWrapper {
  def makeWrapper(node: dom.Node, docWrapper: DocumentWrapper, parent: NodeWrapper): NodeWrapper = {

    require(node ne null)
    require(docWrapper ne null)

    node match {
      case _: dom.Document => docWrapper
      case _: dom.Node     => new ConcreteNodeWrapper(node, docWrapper, parent)
    }
  }
}

class ConcreteNodeWrapper protected (
  val node       : dom.Node,
  val docWrapper : DocumentWrapper,
  var parent     : NodeWrapper // null means unknown
) extends NodeWrapper {
  treeInfo = docWrapper
}