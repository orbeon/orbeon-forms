package org.orbeon.dom

import java.{util => ju}



object QName {

  implicit class QNameOps(private val q: QName) extends AnyVal {

    // http://www.w3.org/TR/xpath-30/#doc-xpath30-URIQualifiedName
    def uriQualifiedName: String =
      if (q.namespace.uri.isEmpty)
        q.localName
      else
        "Q{" + q.namespace.uri + '}' + q.localName

    def clarkName: String =
      if (q.namespace.uri.isEmpty)
        q.localName
      else
        "{" + q.namespace.uri + '}' + q.localName
  }

  // 2017-10-27: 84 usages
  def apply(localName: String): QName = apply(localName, Namespace.EmptyNamespace, localName)

  // 2017-10-27: 247 usages
  def apply(localName: String, namespaceOrNull: Namespace): QName = apply(localName, namespaceOrNull, null)

  // 2017-10-27: 2 external usage
  def apply(localName: String, namespaceOrNull: Namespace, qualifiedNameOrNull: String): QName = {

    require((localName ne null) && localName.nonEmpty)

    val namespace = if (namespaceOrNull eq null) Namespace.EmptyNamespace else namespaceOrNull

    val cache = getOrCreateNamespaceCache(namespace)

    var answer = cache.get(localName)

    if (answer eq null) {
      answer = applyNormalize(localName, namespace, qualifiedNameOrNull)
      cache.put(localName, answer)
    }

    answer
  }

  // 2017-10-27: 17 usages
  def apply(localName: String, prefix: String, uri: String): QName =
    applyNormalize(localName, Namespace(prefix, uri), null)

  private val namespaceCache = new ju.concurrent.ConcurrentHashMap[Namespace, ju.concurrent.ConcurrentHashMap[String, QName]]()

  private def getOrCreateNamespaceCache(namespace: Namespace): ju.Map[String, QName] = {
    var answer = namespaceCache.get(namespace)
    if (answer eq null) {
      answer = new ju.concurrent.ConcurrentHashMap[String, QName]()
      namespaceCache.put(namespace, answer)
    }
    answer
  }

  private def applyNormalize(
    localName           : String,
    namespace           : Namespace,
    qualifiedNameOrNull : String     // if not provided, try to use prefix from namespace to build
  ): QName = {

    require((localName ne null) && localName.nonEmpty && (namespace ne null))

    new QName(
      localName,
      namespace,
      if (qualifiedNameOrNull ne null)
        qualifiedNameOrNull
      else
        if (! namespace.prefix.isEmpty) namespace.prefix + ":" + localName else localName
    )
  }

  // Compare first by namespace URI then by local name
  implicit object QNameOrdering extends Ordering[QName] {
    def compare(x: QName, y: QName): Int = {
      val nsOrder = x.namespace.uri compareTo y.namespace.uri
      if (nsOrder != 0)
        nsOrder
      else
        x.localName compareTo y.localName
    }
  }
}

//
// `QName` represents a qualified name value of an XML element or
// attribute. It consists of a local name and a instance.
//
// NOTE: This is a bit weird because the qualified name is not used for equality: only the
// namespace URI and local name are used.
//
// Ideally we would like this to be a case class, and equality to be distinct.
//
// Also, we don't like that this stores the qualified name. Storing `prefix: Option[String]`
// would be better.
//
class QName private (val localName: String, val namespace: Namespace, val qualifiedName: String) {

  private var _hashCode: Int = _

  override def hashCode(): Int = {
    if (_hashCode == 0) {
      _hashCode = localName.hashCode ^ namespace.uri.hashCode
      if (_hashCode == 0) {
        _hashCode = 0xbabe
      }
    }
    _hashCode
  }

  override def equals(other: Any): Boolean =
    other match {
      case anyRef: AnyRef if this eq anyRef => true
      case that: QName if hashCode == that.hashCode && localName == that.localName && namespace.uri == that.namespace.uri => true
      case _ => false
    }

  override def toString: String =
    if (namespace.uri == "") localName else s"Q{${namespace.uri}}$localName"
}
