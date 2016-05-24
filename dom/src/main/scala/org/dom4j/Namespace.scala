package org.dom4j

import org.dom4j.tree.{AbstractNode, NamespaceCache}

object Namespace {

  private val NamespaceCache = new NamespaceCache

  val XMLNamespace   = NamespaceCache.get("xml", "http://www.w3.org/XML/1998/namespace")
  val EmptyNamespace = NamespaceCache.get("", "")

  /**
   * A helper method to return the Namespace instance for the given prefix and
   * URI
   *
   * @return an interned Namespace object
   */
  def get(prefix: String, uri: String): Namespace = NamespaceCache.get(prefix, uri)

  /**
   * A helper method to return the Namespace instance for no prefix and the
   * URI
   *
   * @return an interned Namespace object
   */
  def get(uri: String): Namespace = NamespaceCache.get(uri)
}

class Namespace(_prefix: String, _uri: String) extends AbstractNode {

  override def getNodeType: Short = Node.NAMESPACE_NODE

  private val prefix = if (_prefix ne null) _prefix else ""
  def getPrefix = prefix

  private val uri = if (_uri ne null) _uri else ""
  def getURI = uri

  private var _hashCode: Int = _

  override def hashCode(): Int = {
    if (_hashCode == 0)
      _hashCode = createHashCode()
    _hashCode
  }

  private def createHashCode(): Int = {
    var result = uri.hashCode ^ prefix.hashCode
    if (result == 0)
      result = 0xbabe
    result
  }

  /**
   * Two Namespaces are equals if their URI and prefix are equal.
   */
  override def equals(other: Any): Boolean =
    other match {
      case anyRef: AnyRef if this eq anyRef ⇒ true
      case that: Namespace if hashCode == that.hashCode && uri == that.getURI && prefix == that.getPrefix ⇒ true
      case _ ⇒ false
    }

  override def getText: String = uri
  override def getStringValue: String = uri

  override def toString: String = {
    super.toString + " [Namespace: prefix " + getPrefix +
      " mapped to URI \"" +
      getURI +
      "\"]"
  }

  def accept(visitor: Visitor) = visitor.visit(this)
}
