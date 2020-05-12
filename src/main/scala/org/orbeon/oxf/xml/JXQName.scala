/**
 * Copyright (C) 2014 Orbeon, Inc.
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

import javax.xml.namespace.{QName => JQName}

// Helpers for javax.xml.namespace.QName
object JXQName {
  def apply(local: String)              = new JQName(local)
  def apply(uri: String, local: String) = new JQName(uri, local)
  def apply(uriLocal: (String, String)) = new JQName(uriLocal._1, uriLocal._2)

  def unapply(c: javax.xml.namespace.QName) = Some(c.getNamespaceURI, c.getLocalPart)

  implicit def tupleToJQName(tuple: (String, String)): JQName = JXQName(tuple._1, tuple._2)
  implicit def stringToJQname(s: String)             : JQName = JXQName(s)
}
