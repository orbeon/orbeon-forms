package org.dom4j.tree

import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap
import java.{util ⇒ ju}

import org.dom4j.Namespace

object NamespaceCache {

  // Cache of instances indexed by URI which contain caches for each prefix.
  private val cache = new ConcurrentHashMap[String, ConcurrentHashMap[String, WeakReference[Namespace]]](11, 0.75f, 1)

  // Cache of instances indexed by URI for default namespaces with no prefixes.
  private val noPrefixCache = new ConcurrentHashMap[String, WeakReference[Namespace]](11, 0.75f, 1)

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
          answer = new ConcreteNamespace(prefix, uri)
          uriCache.put(prefix, new WeakReference[Namespace](answer))
        }
      }
    }
    answer
  }

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
          answer = new ConcreteNamespace("", uri)
          noPrefixCache.put(uri, new WeakReference[Namespace](answer))
        }
      }
    }
    answer
  }

  // Return the cache for the given namespace URI. If one does not currently exist it is created.
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
}
