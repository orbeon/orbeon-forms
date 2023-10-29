package org.orbeon.dom

import org.orbeon.dom.tree.AbstractNode

import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap


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

    val uriCache =
      cache.computeIfAbsent(
        uri,
        _ => new ConcurrentHashMap[String, WeakReference[Namespace]]()
      )

    val namespaceRef =
      uriCache.computeIfAbsent(
        prefix,
        _ => new WeakReference[Namespace](new Namespace(prefix, uri) {})
      )

    val namespaceOrNull = namespaceRef.get
    if (namespaceOrNull ne null) {
      namespaceOrNull
    } else {
      // Weak reference was cleared so we need to create a new instance and mapping
      val newNamespace = new Namespace(prefix, uri) {}
      uriCache.put(
        prefix,
        new WeakReference[Namespace](newNamespace)
      )
      newNamespace
    }
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
