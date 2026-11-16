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
import org.orbeon.xbl.MaskedInputSupport.{MaskToken, SlotType}

import scala.collection.mutable.ListBuffer


object CodeInputSupport {

  val DefaultMask          = "999999"
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

  def effectiveMask(maskStr: String, lengthStr: String = "", charTypeStr: String = ""): String =
    Option(maskStr).flatMap(_.trimAllToOpt) match {
      case Some(nonEmptyMask) =>
        nonEmptyMask
      case None =>
        val hasLegacyLength   = Option(lengthStr).exists(_.nonAllBlank)
        val hasLegacyCharType = Option(charTypeStr).exists(_.nonAllBlank)
        if (hasLegacyLength || hasLegacyCharType) {
          val len      = normalizeLength(lengthStr)
          val slotChar = normalizeCharacterType(charTypeStr) match {
            case "letters"      => 'A'
            case "alphanumeric" => '*'
            case _              => '9'
          }
          slotChar.toString * len
        } else {
          DefaultMask
        }
    }

  // Parses mask tokens without optional `[...]` segments (unescaped `[` and `]` are ignored).
  // Guarantees at least one Slot token (falling back to DefaultMask if none) and at most MaxLength Slot tokens.
  def parseMaskTokens(mask: String): Vector[MaskToken] = {
    val rawMask = Option(mask).flatMap(_.trimAllToOpt).getOrElse(DefaultMask)
    val tokens  = ListBuffer.empty[MaskToken]
    var slotCnt = 0
    var i       = 0
    val len     = rawMask.length

    def addSlot(st: SlotType): Unit =
      if (slotCnt < MaxLength) {
        tokens += MaskToken.Slot(st)
        slotCnt += 1
      }

    while (i < len) {
      rawMask.charAt(i) match {
        case '\\' =>
          if (i + 1 < len) {
            tokens += MaskToken.Literal(rawMask.charAt(i + 1))
            i += 2
          } else {
            tokens += MaskToken.Literal('\\')
            i += 1
          }
        case '[' | ']' =>
          i += 1
        case '9' =>
          addSlot(SlotType.Digit)
          i += 1
        case 'A' =>
          addSlot(SlotType.UpperLetter)
          i += 1
        case 'a' =>
          addSlot(SlotType.LowerLetter)
          i += 1
        case '*' =>
          addSlot(SlotType.UpperAlphanumeric)
          i += 1
        case 'H' =>
          addSlot(SlotType.UpperHex)
          i += 1
        case other =>
          tokens += MaskToken.Literal(other)
          i += 1
      }
    }

    if (slotCnt == 0)
      Vector.fill(DefaultLength)(MaskToken.Slot(SlotType.Digit))
    else
      tokens.toVector
  }

  def slotTypesForMask(mask: String): Vector[SlotType] =
    parseMaskTokens(mask).collect { case MaskToken.Slot(st) => st }

  def normalizeCharForSlot(c: Char, slotType: SlotType): Option[Char] =
    Option.when(slotType.matches(c))(slotType.transform(c))

  def normalizeChar(c: Char, charTypeStr: String): Option[Char] =
    normalizeCharacterType(charTypeStr) match {
      case "digits"       => normalizeCharForSlot(c, SlotType.Digit)
      case "letters"      => normalizeCharForSlot(c, SlotType.UpperLetter)
      case "alphanumeric" => normalizeCharForSlot(c, SlotType.UpperAlphanumeric)
    }

  def filterAndNormalize(raw: String, charTypeStr: String, maxLen: Int): String =
    Option(raw).getOrElse("").iterator.flatMap(normalizeChar(_, charTypeStr)).take(maxLen).mkString

  // Distributes pasted/autofilled text across slots starting at `startSlotIdx`, skipping matching literal separators.
  def distributeTextToSlots(
    rawText      : String,
    currentSlots : IndexedSeq[Option[Char]],
    mask         : String,
    startSlotIdx : Int
  ): (Vector[Option[Char]], Int) = {
    val tokens         = parseMaskTokens(mask)
    val slotTypes      = tokens.collect { case MaskToken.Slot(st) => st }
    val slotCount      = slotTypes.length
    val updated        = Array.tabulate(slotCount)(i => currentSlots.lift(i).flatten)
    val effectiveStart = if (updated.forall(_.isEmpty)) 0 else math.max(0, math.min(startSlotIdx, slotCount - 1))

    // Find the token index corresponding to `effectiveStart`
    var startTokenIdx = 0
    var seenSlots     = 0
    while (startTokenIdx < tokens.length && seenSlots < effectiveStart) {
      if (tokens(startTokenIdx).isInstanceOf[MaskToken.Slot])
        seenSlots += 1
      startTokenIdx += 1
    }

    val input    = Option(rawText).getOrElse("")
    var i        = 0
    var t        = startTokenIdx
    var sIdx     = effectiveStart
    var filled   = 0

    def literalMatches(inputChar: Char, litChar: Char): Boolean =
      inputChar == litChar || (litChar.isLetter && inputChar.toUpper == litChar.toUpper)

    while (i < input.length && t < tokens.length && sIdx < slotCount) {
      tokens(t) match {
        case MaskToken.Literal(lit) =>
          if (literalMatches(input.charAt(i), lit))
            i += 1
          t += 1
        case MaskToken.Slot(slotType) =>
          val c = input.charAt(i)
          if (slotType.matches(c)) {
            updated(sIdx) = Some(slotType.transform(c))
            sIdx   += 1
            filled += 1
            i      += 1
            t      += 1
          } else {
            i += 1
          }
      }
    }

    val nextFocusIdx =
      if (filled > 0) math.min(effectiveStart + filled, slotCount - 1)
      else effectiveStart

    (updated.toVector, nextFocusIdx)
  }

  // Stored value uses space ' ' for internal empty slots when later slots are non-empty,
  // with trailing spaces stripped (so completely empty is "").
  def slotsToStoredValue(slots: Seq[Option[Char]]): String =
    slots
      .map(_.getOrElse(' '))
      .mkString
      .reverse
      .dropWhile(_ == ' ')
      .reverse

  def storedValueToSlots(value: String, mask: String): Vector[Option[Char]] = {
    val raw       = Option(value).getOrElse("")
    val slotTypes = slotTypesForMask(mask)
    val slotCount = slotTypes.length
    if (raw.isEmpty) {
      Vector.fill(slotCount)(None)
    } else {
      val matchesPositionally =
        raw.length <= slotCount &&
          raw.indices.forall(idx => raw.charAt(idx) == ' ' || slotTypes(idx).matches(raw.charAt(idx)))

      if (matchesPositionally) {
        Vector.tabulate(slotCount) { idx =>
          if (idx < raw.length && raw.charAt(idx) != ' ')
            normalizeCharForSlot(raw.charAt(idx), slotTypes(idx))
          else
            None
        }
      } else {
        distributeTextToSlots(raw, Vector.fill(slotCount)(None), mask, startSlotIdx = 0)._1
      }
    }
  }

  def storedValueToSlots(value: String, length: Int, charTypeStr: String): Vector[Option[Char]] =
    storedValueToSlots(value, effectiveMask("", length.toString, charTypeStr))

  def tokenCount(maskStr: String, lengthStr: String, charTypeStr: String): Int =
    parseMaskTokens(effectiveMask(maskStr, lengthStr, charTypeStr)).length

  def isSlotToken(maskStr: String, lengthStr: String, charTypeStr: String, tokenIndexOneBased: Int): Boolean = {
    val tokens = parseMaskTokens(effectiveMask(maskStr, lengthStr, charTypeStr))
    tokenIndexOneBased >= 1 &&
      tokenIndexOneBased <= tokens.length &&
      tokens(tokenIndexOneBased - 1).isInstanceOf[MaskToken.Slot]
  }

  def tokenValueAt(
    value             : String,
    maskStr           : String,
    lengthStr         : String,
    charTypeStr       : String,
    tokenIndexOneBased: Int
  ): String = {
    val mask   = effectiveMask(maskStr, lengthStr, charTypeStr)
    val tokens = parseMaskTokens(mask)
    if (tokenIndexOneBased < 1 || tokenIndexOneBased > tokens.length) {
      ""
    } else {
      tokens(tokenIndexOneBased - 1) match {
        case MaskToken.Literal(ch) =>
          if (ch == ' ') "\u00a0" else ch.toString
        case MaskToken.Slot(_) =>
          val slotIdx = tokens.take(tokenIndexOneBased - 1).count(_.isInstanceOf[MaskToken.Slot])
          val slots   = storedValueToSlots(value, mask)
          slots.lift(slotIdx).flatten.map(_.toString).getOrElse("")
      }
    }
  }

  def cellValueAt(value: String, lengthStr: String, charTypeStr: String, indexOneBased: Int): String =
    tokenValueAt(value, "", lengthStr, charTypeStr, indexOneBased)

  def isValidCode(value: String, maskStr: String, lengthStr: String, charTypeStr: String): Boolean = {
    val raw = Option(value).getOrElse("")
    if (raw.isAllBlank) {
      true
    } else {
      val slotTypes = slotTypesForMask(effectiveMask(maskStr, lengthStr, charTypeStr))
      raw.length == slotTypes.length &&
        slotTypes.indices.forall { i =>
          val c = raw.charAt(i)
          c != ' ' && slotTypes(i).matches(c) && slotTypes(i).transform(c) == c
        }
    }
  }

  def isValidCode(value: String, lengthStr: String, charTypeStr: String): Boolean =
    isValidCode(value, "", lengthStr, charTypeStr)
}

object CodeInputSupportJava {

  //@XPathFunction
  def effectiveMask(maskStr: String, lengthStr: String, charTypeStr: String): String =
    CodeInputSupport.effectiveMask(maskStr, lengthStr, charTypeStr)

  //@XPathFunction
  def normalizeLength(lengthStr: String): Int =
    CodeInputSupport.normalizeLength(lengthStr)

  //@XPathFunction
  def tokenCount(maskStr: String, lengthStr: String, charTypeStr: String): Int =
    CodeInputSupport.tokenCount(maskStr, lengthStr, charTypeStr)

  //@XPathFunction
  def isSlotToken(maskStr: String, lengthStr: String, charTypeStr: String, tokenIndexOneBased: Int): Boolean =
    CodeInputSupport.isSlotToken(maskStr, lengthStr, charTypeStr, tokenIndexOneBased)

  //@XPathFunction
  def tokenValueAt(
    value             : String,
    maskStr           : String,
    lengthStr         : String,
    charTypeStr       : String,
    tokenIndexOneBased: Int
  ): String =
    CodeInputSupport.tokenValueAt(value, maskStr, lengthStr, charTypeStr, tokenIndexOneBased)

  //@XPathFunction
  def isValidCode(value: String, maskStr: String, lengthStr: String, charTypeStr: String): Boolean =
    CodeInputSupport.isValidCode(value, maskStr, lengthStr, charTypeStr)

  //@XPathFunction
  def isValidCode(value: String, lengthStr: String, charTypeStr: String): Boolean =
    CodeInputSupport.isValidCode(value, "", lengthStr, charTypeStr)

  //@XPathFunction
  def cellValueAt(value: String, lengthStr: String, charTypeStr: String, indexOneBased: Int): String =
    CodeInputSupport.cellValueAt(value, lengthStr, charTypeStr, indexOneBased)
}
