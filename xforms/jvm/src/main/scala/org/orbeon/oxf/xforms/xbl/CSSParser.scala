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

import org.apache.commons.lang3.StringUtils
import org.orbeon.oxf.util.StringUtils._

// Poor man's CSS selector parser. See XBLTransformerTest for the supported subset of CSS.
// TODO: handle [att], [att=val], [att~=val], [att|=val]
object CSSParser {

  // Convert a CSS selector to XPath
  def toXPath(cssSelector: String): String = {
    val sb = new StringBuilder
    val selectors = StringUtils.split(cssSelector, ',')

    var firstSelector = true
    for (selector <- selectors) {
      if (! firstSelector)
        sb append '|'

      val pathsElements = StringUtils.split(selector.trimAllToEmpty, ' ')
      var firstElement = true
      var wasChildAxis = false
      for (pathElement <- pathsElements) {

        def appendPathElement() = {
          if (Set(":root", "*:root")(pathElement))
            sb append "."
          else
            sb append pathElement.replace('|', ':').trimAllToEmpty
          false
        }

        wasChildAxis =
          if (firstElement) {
            // First path element
            if (Set(":root", "*:root")(pathElement)) {
              appendPathElement()
            } else if (pathElement == ">") {
              sb append "./"
              true
            } else {
              sb append "descendant-or-self::"
              appendPathElement()
            }
          } else {
            // Subsequent path element
            if (pathElement == ">") {
              sb append '/'
              true
            } else if (! wasChildAxis) {
              sb append "//"
              appendPathElement()
            } else {
              appendPathElement()
            }
          }

        firstElement = false
      }

      firstSelector = false
    }

    sb.toString
  }
}
