package org.orbeon.dom

import org.orbeon.dom.tree.NamespaceCache

object Namespace {

  val XMLNamespace   = NamespaceCache.get("xml", "http://www.w3.org/XML/1998/namespace")
  val EmptyNamespace = NamespaceCache.get("", "")

  // Create or get from cache an immutable `Namespace`
  def apply(prefix: String, uri: String): Namespace = {
    require(prefix ne null)
    require(uri ne null)
    NamespaceCache.get(prefix, uri)
  }

  // Create or get from cache an immutable `Namespace` with empty prefix
  def apply(uri: String): Namespace = {
    require(uri ne null)
    NamespaceCache.get(uri)
  }
}

trait Namespace extends Node {
  def prefix: String
  def uri: String
}
