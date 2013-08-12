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

import java.{lang ⇒ jl}
import org.orbeon.oxf.xforms.XFormsConstants._
import org.orbeon.oxf.xforms.analysis.ElementAnalysis._
import org.orbeon.oxf.xforms.analysis.SimpleElementAnalysis

// Trait for all elements that have an appearance
trait AppearanceTrait extends SimpleElementAnalysis {

    val appearances = attQNameSet(element, APPEARANCE_QNAME, namespaceMapping)
    val mediatype   = Option(element.attributeValue(MEDIATYPE_QNAME))
    
    def encodeAndAppendAppearances(sb: jl.StringBuilder) =
        appearances foreach { a ⇒
            if (sb.length > 0)
                sb.append(' ')
            sb.append("xforms-")
            sb.append(localName)
            sb.append("-appearance-")

            // Names in a namespace may get a prefix
            val uri = a.getNamespaceURI
            if (uri != "") {
                // Try standard prefixes or else use the QName prefix
                val prefix = AppearanceTrait.StandardPrefixes.getOrElse(uri, a.getNamespacePrefix)
                if (prefix != "") {
                    sb.append(prefix)
                    sb.append("-")
                }
            }

            sb.append(a.getName)
        }
}

private object AppearanceTrait {
    // The client expects long prefixes
    val StandardPrefixes = Map(XXFORMS_NAMESPACE_URI → "xxforms", XFORMS_NAMESPACE_URI → "xforms")
}