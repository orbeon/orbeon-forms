package org.dom4j

/**
 * `IllegalAddException` is thrown when a node is added incorrectly
 * to an `Element`.
 */
class IllegalAddException(reason: String) extends IllegalArgumentException(reason) {

  def this(parent: Element, node: Node, reason: String) =
    this("The node \"" + node.toString + "\" could not be added to the element \"" +
      parent.getQualifiedName +
      "\" because: " +
      reason)

  def this(parent: Branch, node: Node, reason: String) =
    this("The node \"" + node.toString + "\" could not be added to the branch \"" +
      parent.getName +
      "\" because: " +
      reason)
}
