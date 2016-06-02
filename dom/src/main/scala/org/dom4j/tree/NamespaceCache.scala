package org.dom4j.tree

import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap
import java.{util ⇒ ju}

import org.dom4j.Namespace
import org.dom4j.tree.NamespaceCache._

private object NamespaceCache {

  /**
   * Cache of instances indexed by URI which contain caches for each prefix.
   */
  val cache = new ConcurrentHashMap[String, ju.Map[String, WeakReference[Namespace]]](11, 0.75f, 1)

  /**
   * Cache of instances indexed by URI for defaultnamespaces with no prefixes.
   */
  val noPrefixCache = new ConcurrentHashMap[String, WeakReference[Namespace]](11, 0.75f, 1)
}

/**
 * `NamespaceCache` caches instances of
 * `DefaultNamespace` for reuse both across documents and within
 * documents.
 */
class NamespaceCache {

  /**
   * @return the namespace for the given prefix and uri
   */
  def get(prefix: String, uri: String): Namespace = {
    val uriCache = getURICache(uri)
    var ref = uriCache.get(prefix)
    var answer: Namespace = null
    if (ref ne null) {
      answer = ref.get
    }
    if (answer eq null) {
      uriCache.synchronized {
        ref = uriCache.get(prefix)
        if (ref ne null) {
          answer = ref.get
        }
        if (answer eq null) {
          answer = createNamespace(prefix, uri)
          uriCache.put(prefix, new WeakReference[Namespace](answer))
        }
      }
    }
    answer
  }

  /**
   * @return the name model for the given name and namepsace
   */
  def get(uri: String): Namespace = {
    var ref = noPrefixCache.get(uri)
    var answer: Namespace = null
    if (ref ne null) {
      answer = ref.get
    }
    if (answer eq null) {
      noPrefixCache.synchronized {
        ref = noPrefixCache.get(uri)
        if (ref ne null) {
          answer = ref.get
        }
        if (answer eq null) {
          answer = createNamespace("", uri)
          noPrefixCache.put(uri, new WeakReference[Namespace](answer))
        }
      }
    }
    answer
  }

  /**
   * @return the cache for the given namespace URI. If one does not currently
   *         exist it is created.
   */
  private def getURICache(uri: String): ju.Map[String, WeakReference[Namespace]] = {
    var answer = cache.get(uri)
    if (answer eq null) {
      cache.synchronized {
        answer = cache.get(uri)
        if (answer eq null) {
          answer = new ConcurrentHashMap[String, WeakReference[Namespace]]()
          cache.put(uri, answer)
        }
      }
    }
    answer
  }

  private def createNamespace(prefix: String, uri: String): Namespace = new Namespace(prefix, uri)
}
