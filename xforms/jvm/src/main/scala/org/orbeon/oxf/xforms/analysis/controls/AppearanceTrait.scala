/**
 *  Copyright (C) 2007 Orbeon, Inc.
 *
 *  This program is free software you can redistribute it and/or modify it under the terms of the
 *  GNU Lesser General Public License as published by the Free Software Foundation either version
 *  2.1 of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU Lesser General Public License for more details.
 *
 *  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.xforms.analysis.controls

import java.{lang => jl}

import org.orbeon.dom.QName
import org.orbeon.oxf.xforms.analysis.ElementAnalysis
import org.orbeon.oxf.xforms.analysis.ElementAnalysis._
import org.orbeon.xforms.XFormsNames._


trait AppearanceTrait extends ElementAnalysis {

  import AppearanceTrait._

  val appearances: Set[QName]     = attQNameSet(element, APPEARANCE_QNAME, namespaceMapping)
  val mediatype  : Option[String] = Option(element.attributeValue(MEDIATYPE_QNAME))

  def encodeAndAppendAppearances(sb: jl.StringBuilder): Unit =
    appearances foreach (encodeAndAppendAppearance(sb, localName, _))
}

object AppearanceTrait {
  // The client expects long prefixes
  private val StandardPrefixes = Map(XXFORMS_NAMESPACE_URI -> "xxforms", XFORMS_NAMESPACE_URI -> "xforms")

  def encodeAndAppendAppearances(sb: jl.StringBuilder, lhha: String, appearances: Set[String]): Unit =
    appearances map QName.apply foreach (encodeAndAppendAppearance(sb, lhha, _))

  def encodeAndAppendAppearance(sb: jl.StringBuilder, lhha: String, appearance: QName): Unit = {
    if (sb.length > 0)
      sb.append(' ')
    sb.append("xforms-")
    sb.append(lhha)
    sb.append("-appearance-")
    encodeAppearanceValue(sb, appearance)
  }

  def encodeAppearanceValue(sb: jl.StringBuilder, appearance: QName): jl.StringBuilder = {
    // Names in a namespace may get a prefix
    val uri = appearance.namespace.uri
    if (uri.nonEmpty) {
      // Try standard prefixes or else use the QName prefix
      val prefix = AppearanceTrait.StandardPrefixes.getOrElse(uri, appearance.namespace.prefix)
      if (prefix.nonEmpty) {
        sb.append(prefix)
        sb.append("-")
      }
    }
    sb.append(appearance.localName)
    sb
  }
}