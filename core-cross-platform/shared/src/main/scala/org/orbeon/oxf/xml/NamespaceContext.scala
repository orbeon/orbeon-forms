/**
 * Copyright (C) 2012 Orbeon, Inc.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version
 * 2.1 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.xml

import java.{util => ju}

import scala.jdk.CollectionConverters._

// Better implementation of SAX NamespaceSupport/NamespaceSupport2
//
// This implementation strictly uses immutable maps. This lets the Scala library deal with the difficulty of updating
// mappings incrementally at each level, and makes sure we don't modify anything by mistake. In the best case scenario,
// when there are no new mappings on an element, we have full reuse. When there are new mappings, there is still
// possible reuse due to the persistent collections.
//
// There are some assumptions done with regard to SAX events:
//
// - endPrefixMapping is never actually useful
// - namespace undeclarations are supported via `startPrefixMapping("prefix", "")`
//
// In order to easily support pending updates reachable from the outside, undeclarations are simply stored as a mapping
// to "" instead of removing the mapping. However, when asking for a URI by prefix, or for declared prefixes, a mapping
// to "" is equivalent to no mapping.
//
// The resulting implementation is simple and short: compare with 824 lines for NamespaceSupport and 749 lines for
// NamespaceSupport2! It is likely to be more memory efficient too, as the old implementations copy maps constantly (no
// persistent collections!).
//
class NamespaceContext {

  // A context has a parent and an immutable map
  case class Context(parent: Option[Context], mappings: Map[String, String]) {

    // Get the URI for the given prefix if any (can pass "" for the default prefix)
    def uriForPrefix(prefix: String): Option[String] =
      mappings.get(prefix) filterNot (_ == "")

    // Get all prefixes in scope ("" is not considered a prefix)
    def prefixes: List[String] =
      mappings.collect { case (prefix, uri) if prefix != "" && uri != "" => prefix } toList

    // Get all prefixes in scope, include returning "" for the default namespace if present
    def prefixesWithDefault: List[String] =
      mappingsWithDefault map { case (prefix, _) => prefix } toList

    // Get all mappings, including the default namespace if present
    def mappingsWithDefault: Iterable[(String, String)] =
      mappings filterNot { case (_, uri) => uri == "" }

    // Get all prefixes for the given URI ("" is not considered a prefix)
    // NOTE: This is not efficient if the map is large as we iterate through all map values.
    def prefixesForURI(uri: String): List[String] =
      mappings.collect { case (prefix, `uri`) if prefix != "" && uri != "" => prefix } toList

    // Get one prefix for the given URI ("" is not considered a prefix)
    // NOTE: This is not efficient if the map is large as we iterate through all map values.
    def firstPrefixForURI(uri: String): Option[String] =
      prefixesForURI(uri).headOption
  }

  // Start with the standard "xml" prefix
  private var _current = Context(None, Map("xml" -> "http://www.w3.org/XML/1998/namespace"))
  def current: Context = _current

  // TODO: We create a copy of the map because we don't want the mapping from "" -> default ns.
  // This is used only by `XFormsAnnotator` but needs to be fixed. Also, what about undeclarations?
  def currentMapping: Map[String, String] = _current.mappings - ""

  // Pending mappings, which will be in force for the next element
  private var _pending = Map.empty[String, String]
  def pending: Map[String, String] = _pending

  // When an element starts, we freeze its mappings
  def startElement(): Unit = {
    _current = Context(Some(_current), _current.mappings ++ _pending)
    _pending = Map.empty
  }

  // When an element ends, the mappings become again those of the parent element
  def endElement(): Unit = {
    _current = _current.parent.get
    _pending = Map.empty
  }

  // Add the mapping
  def startPrefixMapping(prefix: String, uri: String): Unit =
    if (isLegalPrefix(prefix))
      _pending += prefix -> uri

  // For compatibility with NamespaceSupport/NamespaceSupport2
  def getURI(prefix: String): String = current.uriForPrefix(prefix).orNull
  def getPrefixes: ju.Enumeration[String] = current.prefixes.iterator.asJavaEnumeration
  def getPrefix(uri: String): String = current.firstPrefixForURI(uri).orNull

  // See http://www.w3.org/TR/REC-xml-names/#xmlReserved
  // We ignore these 2 prefixes but do not reject other prefixes starting with "xml"
  private def isLegalPrefix(prefix: String) = prefix != "xml" && prefix != "xmlns"
}