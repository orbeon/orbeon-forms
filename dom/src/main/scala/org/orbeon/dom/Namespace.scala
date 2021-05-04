package org.orbeon.dom

import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap
import java.{util => ju}

import org.orbeon.dom.tree.AbstractNode

object Namespace {

  // Cache of instances indexed by URI which contain caches for each prefix.
  private val cache = new ConcurrentHashMap[String, ConcurrentHashMap[String, WeakReference[Namespace]]](11, 0.75f, 1)

  val XMLNamespace  : Namespace = apply("xml", "http://www.w3.org/XML/1998/namespace")
  val EmptyNamespace: Namespace = apply("", "")

  // Smart constructor to get from cache or create an immutable `Namespace` instance
  def apply(prefix: String, uri: String): Namespace = {

    // Only check for non-null here, but check name validity later when actually instantiating
    require(prefix ne null)
    require(uri ne null)

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
          answer = new Namespace(prefix, uri) {}
          uriCache.put(prefix, new WeakReference[Namespace](answer))
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

// See https://stackoverflow.com/questions/5827510/how-to-override-apply-in-a-case-class-companion/25538287#25538287
abstract case class Namespace private[Namespace] (prefix: String, uri: String) extends AbstractNode {

  // TODO
  // 2018-03-21: We checked that there are "few" namespaces created with Form Builder and Form Runner.
  // For example, editing and running a large form, I get a total of 122 namespace created. So checking
  // for validity is not a problem at all. We should use tested logic, like that of Saxon's `Name10Checker`.
  // 2020-11-23: For Saxon 10, it's just `NameChecker`

  private def readResolve()            : Object    = Namespace.apply(prefix, uri)
  def copy(prefix: String, uri: String): Namespace = Namespace.apply(prefix, uri)

  def getType: Int = 13
  override def getText: String = uri
  override def getStringValue: String = uri

  def accept(visitor: Visitor): Unit = visitor.visit(this)
}
