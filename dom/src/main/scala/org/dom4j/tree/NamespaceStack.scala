package org.dom4j.tree

import java.{util ⇒ ju}

import org.dom4j.{DocumentFactory, Namespace, QName}

/**
 * NamespaceStack implements a stack of namespaces and optionally maintains a
 * cache of all the fully qualified names (`QName`) which are in
 * scope. This is useful when building or navigating a *dom4j* document.
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
  def getDefaultNamespace = {
    if (_defaultNamespace eq null)
      _defaultNamespace = findDefaultNamespace()

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
    val prefix = namespace.getPrefix
    if ((prefix == null) || (prefix.length == 0)) {
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
    if (prefix == null) {
      prefix = ""
    }
    var i = namespaceStack.size - 1
    while (i >= 0) {
      val namespace = namespaceStack.get(i)
      if (prefix == namespace.getPrefix) {
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
    if (namespace != null) namespace.getURI else null
  }

  /**
   * @return true if the given prefix is in the stack.
   */
  def contains(namespace: Namespace): Boolean = {
    val prefix = namespace.getPrefix
    var current: Namespace = null
    current = if ((prefix == null) || (prefix.length == 0)) getDefaultNamespace else getNamespaceForPrefix(prefix)
    if (current == null) {
      return false
    }
    if (current == namespace) {
      return true
    }
    namespace.getURI == current.getURI
  }

  def getQName(_namespaceURI: String, _localName: String, _qualifiedName: String): QName = {

    var namespaceURI = _namespaceURI
    var localName = _localName
    var qualifiedName = _qualifiedName

    if (localName == null) {
      localName = qualifiedName
    } else if (qualifiedName == null) {
      qualifiedName = localName
    }
    if (namespaceURI == null) {
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
    val namespace = createNamespace(prefix, namespaceURI)
    pushQName(localName, qualifiedName, namespace, prefix)
  }

  def getAttributeQName(_namespaceURI: String, _localName: String, _qualifiedName: String): QName = {

    var namespaceURI = _namespaceURI
    var localName = _localName
    var qualifiedName = _qualifiedName

    if (qualifiedName == null) {
      qualifiedName = localName
    }
    val map = getNamespaceCache
    var answer = map.get(qualifiedName)
    if (answer != null) {
      return answer
    }
    if (localName == null) {
      localName = qualifiedName
    }
    if (namespaceURI == null) {
      namespaceURI = ""
    }
    var namespace: Namespace = null
    var prefix = ""
    val index = qualifiedName.indexOf(":")
    if (index > 0) {
      prefix = qualifiedName.substring(0, index)
      namespace = createNamespace(prefix, namespaceURI)
      if (localName.trim().length == 0) {
        localName = qualifiedName.substring(index + 1)
      }
    } else {
      namespace = Namespace.EmptyNamespace
      if (localName.trim().length == 0) {
        localName = qualifiedName
      }
    }
    answer = pushQName(localName, qualifiedName, namespace, prefix)
    map.put(qualifiedName, answer)
    answer
  }

  def push(prefix: String, _uri: String): Unit = {

    var uri = _uri

    if (uri == null) {
      uri = ""
    }
    val namespace = createNamespace(prefix, uri)
    push(namespace)
  }

  def addNamespace(prefix: String, uri: String): Namespace = {
    val namespace = createNamespace(prefix, uri)
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
      if (prefix == ns.getPrefix) {
        remove(i)
        namespace = ns
        return namespace
      }
      i -= 1
    }
    if (namespace eq null)
      println(s"Warning: missing namespace prefix ignored: $prefix")

    namespace
  }

  override def toString: String =
    super.toString + " Stack: " + namespaceStack.toString

  private def pushQName(localName: String, qualifiedName: String, namespace: Namespace, prefix: String): QName = {
    if ((prefix == null) || (prefix.length == 0)) {
      _defaultNamespace = null
    }
    createQName(localName, qualifiedName, namespace)
  }

  private def createQName(localName: String, qualifiedName: String, namespace: Namespace): QName = {
    DocumentFactory.createQName(localName, namespace)
  }

  private def createNamespace(prefix: String, namespaceURI: String): Namespace = {
    DocumentFactory.createNamespace(prefix, namespaceURI)
  }

  /**
   * Attempts to find the current default namespace on the stack right now or
   * returns null if one could not be found
   */
  private def findDefaultNamespace(): Namespace = {
    var i = namespaceStack.size - 1
    while (i >= 0) {
      val namespace = namespaceStack.get(i)
      if (namespace != null) {
        val prefix = namespace.getPrefix
        if ((prefix == null) || (namespace.getPrefix.length == 0)) {
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
    if (currentNamespaceCache == null) {
      val index = namespaceStack.size - 1
      if (index < 0) {
        currentNamespaceCache = rootNamespaceCache
      } else {
        currentNamespaceCache = namespaceCacheList.get(index)
        if (currentNamespaceCache == null) {
          currentNamespaceCache = new ju.HashMap[String, QName]()
          namespaceCacheList.set(index, currentNamespaceCache)
        }
      }
    }
    currentNamespaceCache
  }
}
