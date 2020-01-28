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

import org.orbeon.oxf.util.Whitespace._
import org.orbeon.saxon.value.{Whitespace => SWhitespace}

class CharacterAccumulator {

  private var appendCount = 0
  private val acc = new java.lang.StringBuilder

  // Stats
  private var _savedCharacters = 0
  def savedBytes = _savedCharacters * 2

  private var _multipleAppends = 0
  def multipleAppends = _multipleAppends

  def append(policy: Policy, ch: Array[Char], start: Int, length: Int): Unit =
    if (length > 0) {
      acc.append(ch, start, length)
      appendCount += 1
    }

  def collapseAndReset(policy: Policy): String = {

    val originalLength = acc.length
    val resultCS = Whitespace.applyPolicy(acc, policy)

    _savedCharacters += originalLength - resultCS.length
    _multipleAppends += 0 max appendCount - 1

    val result = if (resultCS.length > 0) resultCS.toString else ""
    reset()
    result
  }

  // Collapse whitespace but don't remove leading/trailing space if any. This is more conservative.
  // Inspired by Saxon collapseWhitespace
  private def collapseWhitespaceNoTrim(cs: CharSequence): CharSequence = {
    val length = cs.length
    if (length == 0 || ! SWhitespace.containsWhitespace(cs))
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

  private def reset(): Unit = {
    appendCount = 0
    acc.setLength(0)
  }
}
