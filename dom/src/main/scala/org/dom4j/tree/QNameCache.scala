package org.dom4j.tree

import java.{util ⇒ ju}

import org.dom4j.{Namespace, QName}

/**
 * `QNameCache` caches instances of `QName` for reuse
 * both across documents and within documents.
 */
class QNameCache {

  protected var noNamespaceCache: ju.Map[String, QName] = ju.Collections.synchronizedMap(new ju.WeakHashMap[String, QName]())
  protected var namespaceCache: ju.Map[Namespace, ju.Map[String, QName]] = ju.Collections.synchronizedMap(new ju.WeakHashMap[Namespace, ju.Map[String, QName]]())

  def get(_name: String): QName = {
    var name = _name
    var answer: QName = null
    if (name ne null) {
      answer = noNamespaceCache.get(name)
    } else {
      name = ""
    }
    if (answer eq null) {
      answer = createQName(name)
      noNamespaceCache.put(name, answer)
    }
    answer
  }

  def get(_name: String, namespace: Namespace): QName = {
    var name = _name
    val cache = getOrCreateNamespaceCache(namespace)
    var answer: QName = null
    if (name ne null) {
      answer = cache.get(name)
    } else {
      name = ""
    }
    if (answer eq null) {
      answer = createQName(name, namespace)
      cache.put(name, answer)
    }
    answer
  }

  def get(_localName: String, namespace: Namespace, qName: String): QName = {
    var localName = _localName
    val cache = getOrCreateNamespaceCache(namespace)
    var answer: QName = null
    if (localName ne null) {
      answer = cache.get(localName)
    } else {
      localName = ""
    }
    if (answer eq null) {
      answer = createQName(localName, namespace, qName)
      cache.put(localName, answer)
    }
    answer
  }

  def get(qualifiedName: String, uri: String): QName = {
    val index = qualifiedName.indexOf(':')
    if (index < 0) {
      get(qualifiedName, Namespace(uri))
    } else {
      val name = qualifiedName.substring(index + 1)
      val prefix = qualifiedName.substring(0, index)
      get(name, Namespace(prefix, uri))
    }
  }

  protected def getOrCreateNamespaceCache(namespace: Namespace): ju.Map[String, QName] = {
    if (namespace == Namespace.EmptyNamespace) {
      return noNamespaceCache
    }
    var answer: ju.Map[String, QName] = null
    if (namespace ne null) {
      answer = namespaceCache.get(namespace)
    }
    if (answer eq null) {
      answer = ju.Collections.synchronizedMap(new ju.HashMap[String, QName]())
      namespaceCache.put(namespace, answer)
    }
    answer
  }

  private def createQName(name: String) = new QName(name)
  private def createQName(name: String, namespace: Namespace) = new QName(name, namespace)
  private def createQName(name: String, namespace: Namespace, qualifiedName: String) = new QName(name, namespace, qualifiedName)
}
