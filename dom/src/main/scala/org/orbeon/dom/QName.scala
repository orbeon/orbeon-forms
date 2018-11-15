package org.orbeon.dom

import java.{util ⇒ ju}

object QName {

  private val noNamespaceCache: ju.Map[String, QName]                    = ju.Collections.synchronizedMap(new ju.WeakHashMap[String, QName]())
  private val namespaceCache  : ju.Map[Namespace, ju.Map[String, QName]] = ju.Collections.synchronizedMap(new ju.WeakHashMap[Namespace, ju.Map[String, QName]]())

  // 2017-10-27: 84 usages
  def apply(localName: String): QName = apply(localName, Namespace.EmptyNamespace, localName)

  // 2017-10-27: 247 usages
  def apply(localName: String, namespaceOrNull: Namespace): QName = apply(localName, namespaceOrNull, null)

  // 2017-10-27: 2 external usage
  def apply(localName: String, namespaceOrNull: Namespace, qualifiedNameOrNull: String): QName = {

    require((localName ne null) && localName.nonEmpty)

    val cache = getOrCreateNamespaceCache(namespaceOrNull)

    var answer = cache.get(localName)

    if (answer eq null) {
      answer = applyNormalize(localName, namespaceOrNull, qualifiedNameOrNull)
      cache.put(localName, answer)
    }

    answer
  }

  // 2017-10-27: 17 usages
  def apply(localName: String, prefix: String, uri: String): QName =
    applyNormalize(localName, Namespace(prefix, uri), null)

  private def getOrCreateNamespaceCache(namespaceOrNull: Namespace): ju.Map[String, QName] = {
    if (namespaceOrNull eq Namespace.EmptyNamespace) { // only one instance of the empty namespace due to cache
      noNamespaceCache
    } else {
      var answer: ju.Map[String, QName] = null
      if (namespaceOrNull ne null) {
        answer = namespaceCache.get(namespaceOrNull)
      }
      if (answer eq null) {
        answer = ju.Collections.synchronizedMap(new ju.HashMap[String, QName]())
        namespaceCache.put(namespaceOrNull, answer)
      }
      answer
    }
  }

  private def applyNormalize(
    localName           : String,
    namespaceOrNull     : Namespace, // defaults to `EmptyNamespace`
    qualifiedNameOrNull : String     // if not provided, try to use prefix from namespace to build
  ): QName = {

    require((localName ne null) && localName.nonEmpty)

    val namespace = if (namespaceOrNull eq null) Namespace.EmptyNamespace else namespaceOrNull

    new QName(
      localName,
      namespace,
      if (qualifiedNameOrNull ne null)
        qualifiedNameOrNull
      else
        if (! namespace.prefix.isEmpty) namespace.prefix + ":" + localName else localName
    )
  }
}

//
// `QName` represents a qualified name value of an XML element or
// attribute. It consists of a local name and a instance.
//
// NOTE: This is a bit weird because the qualified name is not use for equality, and only the
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
      case anyRef: AnyRef if this eq anyRef ⇒ true
      case that: QName if hashCode == that.hashCode && localName == that.localName && namespace.uri == that.namespace.uri ⇒ true
      case _ ⇒ false
    }

  override def toString =
    if (namespace.uri == "") localName else s"Q{$namespace.uri}$qualifiedName"
}
