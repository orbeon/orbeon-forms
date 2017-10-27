package org.orbeon.dom

import java.{util ⇒ ju}

object QName {

  private var noNamespaceCache : ju.Map[String, QName]                    = ju.Collections.synchronizedMap(new ju.WeakHashMap[String, QName]())
  private var namespaceCache   : ju.Map[Namespace, ju.Map[String, QName]] = ju.Collections.synchronizedMap(new ju.WeakHashMap[Namespace, ju.Map[String, QName]]())

  // 2017-10-27: 84 usages
  def apply(nameOrNull: String): QName = apply(nameOrNull, Namespace.EmptyNamespace, nameOrNull)

  // 2017-10-27: 247 usages
  def apply(nameOrNull: String, namespaceOrNull: Namespace): QName = apply(nameOrNull, namespaceOrNull, null)

  // 2017-10-27: 2 external usage
  def apply(nameOrNull: String, namespaceOrNull: Namespace, qualifiedNameOrNull: String): QName = {

    // TODO: The name should not be null or ""!

    var normalizedName = if (nameOrNull eq null) "" else nameOrNull

    val cache = getOrCreateNamespaceCache(namespaceOrNull)

    var answer = cache.get(normalizedName)

    if (answer eq null) {
      answer = applyNormalize(normalizedName, namespaceOrNull, qualifiedNameOrNull)
      cache.put(normalizedName, answer)
    }

    answer
  }

  // 2017-10-27: 17 usages
  def apply(name: String, prefix: String, uri: String): QName = {
    require((name ne null) && (prefix ne null) && (uri ne null))
    applyNormalize(name, Namespace(prefix, uri), null)
  }

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

  //
  private def applyNormalize(
    nameOrNull          : String,    // TODO: Disallow null and ""!
    namespaceOrNull     : Namespace, // defaults to `EmptyNamespace`
    qualifiedNameOrNull : String     // if not provided, try to use prefix from namespace to build
  ): QName = {

    val name      = if (nameOrNull eq null) "" else nameOrNull
    val namespace = if (namespaceOrNull eq null) Namespace.EmptyNamespace else namespaceOrNull

    new QName(
      name,
      namespace,
      if (qualifiedNameOrNull ne null)
        qualifiedNameOrNull
      else
        if (! namespace.prefix.isEmpty) namespace.prefix + ":" + name else name
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
class QName private (val name: String, val namespace: Namespace, val qualifiedName: String) {

  private var _hashCode: Int = _

  override def hashCode(): Int = {
    if (_hashCode == 0) {
      _hashCode = name.hashCode ^ namespace.uri.hashCode
      if (_hashCode == 0) {
        _hashCode = 0xbabe
      }
    }
    _hashCode
  }

  override def equals(other: Any): Boolean =
    other match {
      case anyRef: AnyRef if this eq anyRef ⇒ true
      case that: QName if hashCode == that.hashCode && name == that.name && namespace.uri == that.namespace.uri ⇒ true
      case _ ⇒ false
    }

  override def toString =
    if (namespace.uri == "") name else s"Q{$namespace.uri}$qualifiedName"
}
