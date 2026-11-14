/**
  * Copyright (C) 2026 Orbeon, Inc.
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
package org.orbeon.xbl

import org.orbeon.oxf.util.StringUtils.*


object CodeInputSupport {

  val DefaultLength        = 6
  val MinLength            = 1
  val MaxLength            = 16
  val DefaultCharacterType = "digits"

  def normalizeLength(lengthStr: String): Int =
    Option(lengthStr)
      .flatMap(_.trimAllToOpt)
      .flatMap(_.toIntOption)
      .map(n => math.max(MinLength, math.min(MaxLength, n)))
      .getOrElse(DefaultLength)

  def normalizeCharacterType(charTypeStr: String): String =
    Option(charTypeStr).flatMap(_.trimAllToOpt) match {
      case Some("alphanumeric") => "alphanumeric"
      case Some("letters")      => "letters"
      case _                    => DefaultCharacterType
    }

  def normalizeChar(c: Char, charTypeStr: String): Option[Char] =
    normalizeCharacterType(charTypeStr) match {
      case "digits"       => Option.when(c >= '0' && c <= '9')(c)
      case "letters"      => Option.when(c.isLetter)(c.toUpper)
      case "alphanumeric" =>
        if (c >= '0' && c <= '9') Some(c)
        else if (c.isLetter)      Some(c.toUpper)
        else                      None
    }

  def filterAndNormalize(raw: String, charTypeStr: String, maxLen: Int): String =
    Option(raw).getOrElse("").iterator.flatMap(normalizeChar(_, charTypeStr)).take(maxLen).mkString

  // Stored value uses space ' ' for internal empty slots when later slots are non-empty,
  // with trailing spaces stripped (so completely empty is "").
  def slotsToStoredValue(slots: Seq[Option[Char]]): String =
    slots
      .map(_.getOrElse(' '))
      .mkString
      .reverse
      .dropWhile(_ == ' ')
      .reverse

  def storedValueToSlots(value: String, length: Int, charTypeStr: String): Vector[Option[Char]] = {
    val raw = Option(value).getOrElse("")
    Vector.tabulate(length) { idx =>
      if (idx < raw.length && raw.charAt(idx) != ' ')
        normalizeChar(raw.charAt(idx), charTypeStr)
      else
        None
    }
  }

  def cellValueAt(value: String, lengthStr: String, charTypeStr: String, indexOneBased: Int): String = {
    val len   = normalizeLength(lengthStr)
    val slots = storedValueToSlots(value, len, charTypeStr)
    if (indexOneBased >= 1 && indexOneBased <= slots.length)
      slots(indexOneBased - 1).map(_.toString).getOrElse("")
    else
      ""
  }

  def isValidCode(value: String, lengthStr: String, charTypeStr: String): Boolean = {
    val raw = Option(value).getOrElse("")
    if (raw.isAllBlank) {
      true
    } else {
      val len = normalizeLength(lengthStr)
      raw.length == len && raw.forall(c => normalizeChar(c, charTypeStr).contains(c))
    }
  }
}

object CodeInputSupportJava {

  //@XPathFunction
  def normalizeLength(lengthStr: String): Int =
    CodeInputSupport.normalizeLength(lengthStr)

  //@XPathFunction
  def isValidCode(value: String, lengthStr: String, charTypeStr: String): Boolean =
    CodeInputSupport.isValidCode(value, lengthStr, charTypeStr)

  //@XPathFunction
  def cellValueAt(value: String, lengthStr: String, charTypeStr: String, indexOneBased: Int): String =
    CodeInputSupport.cellValueAt(value, lengthStr, charTypeStr, indexOneBased)
}
