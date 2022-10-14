/**
 * Copyright (C) 2007 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.xbl

import org.orbeon.oxf.util.StringUtils._

import scala.collection.mutable


// Poor man's CSS selector parser. See XBLTransformerTest for the supported subset of CSS.
// TODO: handle [att], [att=val], [att~=val], [att|=val]
object CSSParser {

  // Convert a CSS selector to XPath
  def toXPath(cssSelector: String): String = {
    val sb = new mutable.StringBuilder
    val selectors = cssSelector.splitTo[Array](",")

    var firstSelector = true
    for (selector <- selectors) {
      if (! firstSelector)
        sb append '|'

      val pathsElements = selector.trimAllToEmpty.splitTo[Array]()
      var firstElement = true
      var wasChildAxis = false
      for (pathElement <- pathsElements) {

        def appendPathElement(): Unit =
          if (Set(":root", "*:root")(pathElement))
            sb append "."
          else
            sb append pathElement.replace('|', ':').trimAllToEmpty

        wasChildAxis =
          if (firstElement) {
            // First path element
            if (Set(":root", "*:root")(pathElement)) {
              appendPathElement()
              false
            } else if (pathElement == ">") {
              sb append "./"
              true
            } else {
              sb append "descendant-or-self::"
              appendPathElement()
              false
            }
          } else {
            // Subsequent path element
            if (pathElement == ">") {
              sb append '/'
              true
            } else if (! wasChildAxis) {
              sb append "//"
              appendPathElement()
              false
            } else {
              appendPathElement()
              false
            }
          }

        firstElement = false
      }

      firstSelector = false
    }

    sb.toString
  }
}
