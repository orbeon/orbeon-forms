package org.orbeon.dom.tree

import java.{util => ju}

import org.orbeon.dom.{Namespace, QName}

/**
 * NamespaceStack implements a stack of namespaces and optionally maintains a
 * cache of all the fully qualified names (`QName`) which are in
 * scope. This is useful when building or navigating a tree.
 */
class NamespaceStack {

  /**
    * The Stack of namespaces
    */
  private val namespaceStack = new ju.ArrayList[Namespace]()

  /**
    * The cache of qualifiedNames to QNames per namespace context
    */
  private val namespaceCacheList = new ju.ArrayList[ju.Map[String, QName]]()

  /**
   * A cache of current namespace context cache of mapping from qualifiedName
   * to QName
   */
  private var currentNamespaceCache: ju.Map[String, QName] = _

  /**
    * A cache of mapping from qualifiedName to QName before any namespaces are
    * declared
    */
  private val rootNamespaceCache = new ju.HashMap[String, QName]()

  /**
    * Caches the default namespace defined via xmlns=""
   */
  private var _defaultNamespace: Namespace = _
  def getDefaultNamespace: Namespace = {
    if (_defaultNamespace eq null)
      _defaultNamespace = findDefaultNamespace

    _defaultNamespace
  }

  /**
   * Pushes the given namespace onto the stack so that its prefix becomes
   * available.
   */
  def push(namespace: Namespace): Unit = {
    namespaceStack.add(namespace)
    namespaceCacheList.add(null)
    currentNamespaceCache = null
    val prefix = namespace.prefix
    if ((prefix eq null) || (prefix.length == 0)) {
      _defaultNamespace = namespace
    }
  }

  // Pops the most recently used `Namespace` from the stack
  def pop(): Namespace = remove(namespaceStack.size - 1)

  def size: Int = namespaceStack.size

  def clear(): Unit = {
    namespaceStack.clear()
    namespaceCacheList.clear()
    rootNamespaceCache.clear()
    currentNamespaceCache = null
  }

  def getNamespace(index: Int): Namespace = namespaceStack.get(index)

  /**
   * @return the namespace for the given prefix or null if it could not be found.
   */
  def getNamespaceForPrefix(_prefix: String): Namespace = {
    var prefix = _prefix
    if (prefix eq null) {
      prefix = ""
    }
    var i = namespaceStack.size - 1
    while (i >= 0) {
      val namespace = namespaceStack.get(i)
      if (prefix == namespace.prefix) {
        return namespace
      }
      i -= 1
    }
    null
  }

  /**
   * @return the URI for the given prefix or null if it could not be found.
   */
  def getURI(prefix: String): String = {
    val namespace = getNamespaceForPrefix(prefix)
    if (namespace ne null) namespace.uri else null
  }

  /**
   * @return true if the given prefix is in the stack.
   */
  def contains(namespace: Namespace): Boolean = {
    val prefix = namespace.prefix
    var current: Namespace = null
    current = if ((prefix eq null) || (prefix.length == 0)) getDefaultNamespace else getNamespaceForPrefix(prefix)
    if (current eq null) {
      return false
    }
    if (current == namespace) {
      return true
    }
    namespace.uri == current.uri
  }

  def getQName(_namespaceURI: String, _localName: String, _qualifiedName: String): QName = {

    var namespaceURI = _namespaceURI
    var localName = _localName
    var qualifiedName = _qualifiedName

    if (localName eq null) {
      localName = qualifiedName
    } else if (qualifiedName eq null) {
      qualifiedName = localName
    }
    if (namespaceURI eq null) {
      namespaceURI = ""
    }
    var prefix = ""
    val index = qualifiedName.indexOf(":")
    if (index > 0) {
      prefix = qualifiedName.substring(0, index)
      if (localName.trim().length == 0) {
        localName = qualifiedName.substring(index + 1)
      }
    } else if (localName.trim().length == 0) {
      localName = qualifiedName
    }
    pushQName(localName, Namespace(prefix, namespaceURI), prefix)
  }

  def getAttributeQName(_namespaceURI: String, _localName: String, _qualifiedName: String): QName = {

    var namespaceURI = _namespaceURI
    var localName = _localName
    var qualifiedName = _qualifiedName

    if (qualifiedName eq null) {
      qualifiedName = localName
    }
    val map = getNamespaceCache
    var answer = map.get(qualifiedName)
    if (answer ne null) {
      return answer
    }
    if (localName eq null) {
      localName = qualifiedName
    }
    if (namespaceURI eq null) {
      namespaceURI = ""
    }
    var namespace: Namespace = null
    var prefix = ""
    val index = qualifiedName.indexOf(":")
    if (index > 0) {
      prefix = qualifiedName.substring(0, index)
      namespace = Namespace(prefix, namespaceURI)
      if (localName.trim().length == 0) {
        localName = qualifiedName.substring(index + 1)
      }
    } else {
      namespace = Namespace.EmptyNamespace
      if (localName.trim().length == 0) {
        localName = qualifiedName
      }
    }
    answer = pushQName(localName, namespace, prefix)
    map.put(qualifiedName, answer)
    answer
  }

  def push(prefix: String, uri: String): Unit =
    push(Namespace(prefix, if (uri eq null) "" else uri))

  def addNamespace(prefix: String, uri: String): Namespace = {
    val namespace = Namespace(prefix, uri)
    push(namespace)
    namespace
  }

  // Pops a namespace from the stack with the given prefix and URI
  def pop(_prefix: String): Namespace = {

    var prefix = _prefix

    if (prefix eq null)
      prefix = ""

    var namespace: Namespace = null
    var i = namespaceStack.size - 1
    while (i >= 0) {
      val ns = namespaceStack.get(i)
      if (prefix == ns.prefix) {
        remove(i)
        namespace = ns
        return namespace
      }
      i -= 1
    }

    // ORBEON: We don't want a `println` here. We should either remove if it doesn't happen, or throw if it's an error.
//    if (namespace eq null)
//      println(s"Warning: missing namespace prefix ignored: $prefix")

    namespace
  }

  private def pushQName(localName: String, namespace: Namespace, prefix: String): QName = {
    if ((prefix eq null) || (prefix.length == 0)) {
      _defaultNamespace = null
    }
    QName(localName, namespace)
  }

  /**
   * Attempts to find the current default namespace on the stack right now or
   * returns null if one could not be found
   */
  private def findDefaultNamespace: Namespace = {
    var i = namespaceStack.size - 1
    while (i >= 0) {
      val namespace = namespaceStack.get(i)
      if (namespace ne null) {
        val prefix = namespace.prefix
        if ((prefix eq null) || (namespace.prefix.length == 0)) {
          return namespace
        }
      }
      i -= 1
    }
    null
  }

  private def remove(index: Int): Namespace = {
    val namespace = namespaceStack.remove(index)
    namespaceCacheList.remove(index)
    _defaultNamespace = null
    currentNamespaceCache = null
    namespace
  }

  private def getNamespaceCache: ju.Map[String, QName] = {
    if (currentNamespaceCache eq null) {
      val index = namespaceStack.size - 1
      if (index < 0) {
        currentNamespaceCache = rootNamespaceCache
      } else {
        currentNamespaceCache = namespaceCacheList.get(index)
        if (currentNamespaceCache eq null) {
          currentNamespaceCache = new ju.HashMap[String, QName]()
          namespaceCacheList.set(index, currentNamespaceCache)
        }
      }
    }
    currentNamespaceCache
  }
}
