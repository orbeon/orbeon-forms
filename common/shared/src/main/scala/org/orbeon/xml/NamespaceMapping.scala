/**
  * Copyright (C) 2010 Orbeon, Inc.
  *
  * This program is free software; you can redistribute it and/or modify it under the terms of the
  * GNU Lesser General Public License as published by the Free Software Foundation; either version
  *  2.1 of the License, or (at your option) any later version.
  *
  * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  * See the GNU Lesser General Public License for more details.
  *
  * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
  */
package org.orbeon.xml

import java.{util => ju}

import scala.collection.JavaConverters._

case class NamespaceMapping(hash: String, mapping: Map[String, String]) {
  require((hash ne null) && (mapping ne null))
}

// We used to compute a has with `SecureUtils.defaultMessageDigest`. The hash was used for 2
// purposes:
//
// 1. XPathCache uses the hash as part of the cache key
// 2. reducing the number of namespace objects in `XFormsAnnotator` and `Metadata`.
//
// Now we address #2 by using Scala immutable maps. This doesn't reduce the number of objects as much
// has using a strong hash and reusing the `NamespaceMapping` but it's good enough, and we don't need
// the hash anymore for this.
//
// Remains #1, the hash from `hashCode` is computed with MurmurHash 3, which is not as strong but
// should hopefully be enough to discriminate expressions in the cache which are identical except
// for the namespaces in scope.
//
object NamespaceMapping {

  val EmptyMapping: NamespaceMapping = apply(Map.empty[String, String])

  def apply(mapping: Map[String, String]): NamespaceMapping =
    NamespaceMapping(mapping.hashCode.toString, mapping)

  def apply(mapping: ju.Map[String, String]): NamespaceMapping =
    apply(mapping.asScala.toMap)
}