/**
 * Copyright (C) 2013 Orbeon, Inc.
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
package org.orbeon.oxf.util

import enumeratum.EnumEntry.Lowercase
import enumeratum._
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.saxon
import scala.collection.compat._

object Whitespace  {

  sealed trait Policy extends EnumEntry with Lowercase
  object Policy extends Enum[Policy] {

    val values     = findValues
    val valuesList = values.to(List)

    case object Preserve  extends Policy
    case object Normalize extends Policy // like XML Schema's collapse and XPath's normalize-space()
    case object Collapse  extends Policy // collapse sequences of multiple whitespace characters to a single space
    case object Trim      extends Policy // trim leading and trailing whitespace
  }

  def applyPolicy(s: CharSequence, policy: Policy): String = {

    val resultCS =
      policy match {
        case Policy.Preserve  => s
        case Policy.Normalize => saxon.value.Whitespace.collapseWhitespace(s)
        case Policy.Collapse  => collapseWhitespaceNoTrim(s)
        case Policy.Trim      => s.toString.trimAllToEmpty
      }

    if (resultCS.length > 0) resultCS.toString else ""
  }

  // Collapse whitespace but don't remove leading/trailing space if any. This is more conservative.
  // Inspired by Saxon collapseWhitespace
  private def collapseWhitespaceNoTrim(cs: CharSequence): CharSequence = {
    val length = cs.length
    if (length == 0 || ! saxon.value.Whitespace.containsWhitespace(cs))
      cs
    else {
      val sb = new java.lang.StringBuilder(length)
      var inWhitespace = false
      var i = 0
      while (i < length) {
        cs.charAt(i) match {
          case c @ ('\n' | '\r' | '\t' | ' ') =>
            if (! inWhitespace) {
              sb.append(' ')
              inWhitespace = true
            }
          case c =>
            sb.append(c)
            inWhitespace = false
        }
        i += 1
      }
      sb
    }
  }
}