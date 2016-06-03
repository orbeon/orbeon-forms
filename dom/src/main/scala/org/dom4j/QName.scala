package org.dom4j

import org.dom4j.tree.QNameCache

object QName {

  private val QNameCache = new QNameCache

  def get(name: String): QName = QNameCache.get(name)

  def get(name: String, namespace: Namespace): QName = QNameCache.get(name, namespace)

  def get(name: String, prefix: String, uri: String): QName =
    if (((prefix eq null) || prefix.isEmpty) && (uri eq null)) {
      QName.get(name)
    } else if ((prefix eq null) || prefix.isEmpty) {
      QNameCache.get(name, Namespace(uri))
    } else if (uri eq null) {
      QName.get(name)
    } else {
      QNameCache.get(name, Namespace(prefix, uri))
    }

  def get(qualifiedName: String, uri: String): QName =
    if (uri eq null) {
      QNameCache.get(qualifiedName)
    } else {
      QNameCache.get(qualifiedName, uri)
    }

  def get(localName: String, namespace: Namespace, qualifiedName: String): QName =
    QNameCache.get(localName, namespace, qualifiedName)
}

/**
 * `QName` represents a qualified name value of an XML element or
 * attribute. It consists of a local name and a instance.
 */
class QName(_name: String, _namespace: Namespace, _qualifiedName: String) {

  def this(name: String) = this(name, Namespace.EmptyNamespace, null)
  def this(name: String, namespace: Namespace) = this(name, namespace, null)

  private val name = if (_name eq null) "" else _name
  def getName = name

  private val namespace: Namespace = if (_namespace eq null) Namespace.EmptyNamespace else _namespace
  def getNamespace = namespace

  private val qualifiedName =
    if (_qualifiedName ne null) {
      _qualifiedName
    } else {
      val prefix = getNamespacePrefix
      if ((prefix ne null) && ! prefix.isEmpty) prefix + ":" + name else name
    }

  def getQualifiedName = qualifiedName

  def getNamespacePrefix = namespace.prefix
  def getNamespaceURI    = namespace.uri

  private var _hashCode: Int = _

  override def hashCode(): Int = {
    if (_hashCode == 0) {
      _hashCode = getName.hashCode ^ getNamespaceURI.hashCode
      if (_hashCode == 0) {
        _hashCode = 0xbabe
      }
    }
    _hashCode
  }

  override def equals(other: Any): Boolean =
    other match {
      case anyRef: AnyRef if this eq anyRef ⇒ true
      case that: QName if hashCode == that.hashCode && getName == that.getName && getNamespaceURI == that.getNamespaceURI ⇒ true
      case _ ⇒ false
    }

  override def toString =
    if (getNamespaceURI == "") name else s"Q{{$getNamespaceURI}}$getQualifiedName"
}
