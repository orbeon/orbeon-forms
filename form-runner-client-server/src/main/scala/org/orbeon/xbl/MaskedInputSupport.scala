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

import scala.collection.mutable.ListBuffer


object MaskedInputSupport {

  sealed trait SlotType {
    def matches(c: Char): Boolean
    def transform(c: Char): Char
    def placeholderChar: Char
  }

  object SlotType {
    case object Digit extends SlotType {
      def matches(c: Char): Boolean = c >= '0' && c <= '9'
      def transform(c: Char): Char  = c
      val placeholderChar: Char     = '9'
    }

    case object UpperLetter extends SlotType {
      def matches(c: Char): Boolean = (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')
      def transform(c: Char): Char  = c.toUpper
      val placeholderChar: Char     = 'A'
    }

    case object LowerLetter extends SlotType {
      def matches(c: Char): Boolean = (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')
      def transform(c: Char): Char  = c.toLower
      val placeholderChar: Char     = 'a'
    }

    case object UpperAlphanumeric extends SlotType {
      def matches(c: Char): Boolean = Digit.matches(c) || UpperLetter.matches(c)
      def transform(c: Char): Char  = c.toUpper
      val placeholderChar: Char     = '*'
    }

    case object UpperHex extends SlotType {
      def matches(c: Char): Boolean = Digit.matches(c) || (c >= 'A' && c <= 'F') || (c >= 'a' && c <= 'f')
      def transform(c: Char): Char  = c.toUpper
      val placeholderChar: Char     = 'H'
    }
  }

  sealed trait MaskToken
  object MaskToken {
    case class Slot(slotType: SlotType) extends MaskToken
    case class Literal(char: Char)      extends MaskToken
  }

  sealed trait MaskSegment {
    def tokens: List[MaskToken]
    def isOptional: Boolean
  }

  object MaskSegment {
    case class Required(tokens: List[MaskToken]) extends MaskSegment { val isOptional = false }
    case class Optional(tokens: List[MaskToken]) extends MaskSegment { val isOptional = true  }
  }

  case class MaskEditResult(
    rawValue      : String,
    formattedValue: String,
    caretPos      : Int
  )

  def parseMask(mask: String): List[MaskSegment] =
    mask.trimAllToOpt match {
      case None =>
        Nil
      case Some(nonEmptyMask) =>
        val segments      = ListBuffer.empty[MaskSegment]
        val currentTokens = ListBuffer.empty[MaskToken]
        var inOptional    = false
        var i             = 0
        val len           = nonEmptyMask.length

        def flushSegment(): Unit =
          if (currentTokens.nonEmpty) {
            val tokens = currentTokens.toList
            segments += (if (inOptional) MaskSegment.Optional(tokens) else MaskSegment.Required(tokens))
            currentTokens.clear()
          }

        while (i < len) {
          nonEmptyMask.charAt(i) match {
            case '\\' =>
              if (i + 1 < len) {
                currentTokens += MaskToken.Literal(nonEmptyMask.charAt(i + 1))
                i += 2
              } else {
                currentTokens += MaskToken.Literal('\\')
                i += 1
              }
            case '[' if ! inOptional =>
              flushSegment()
              inOptional = true
              i += 1
            case ']' if inOptional =>
              flushSegment()
              inOptional = false
              i += 1
            case '9' =>
              currentTokens += MaskToken.Slot(SlotType.Digit)
              i += 1
            case 'A' =>
              currentTokens += MaskToken.Slot(SlotType.UpperLetter)
              i += 1
            case 'a' =>
              currentTokens += MaskToken.Slot(SlotType.LowerLetter)
              i += 1
            case '*' =>
              currentTokens += MaskToken.Slot(SlotType.UpperAlphanumeric)
              i += 1
            case 'H' =>
              currentTokens += MaskToken.Slot(SlotType.UpperHex)
              i += 1
            case other =>
              currentTokens += MaskToken.Literal(other)
              i += 1
          }
        }

        flushSegment()
        segments.toList
    }

  def inputMode(mask: String): String = {
    val slots = parseMask(mask).flatMap(_.tokens).collect { case MaskToken.Slot(st) => st }
    if (slots.nonEmpty && slots.forall(_ == SlotType.Digit)) "numeric" else "text"
  }

  def maxLengthOpt(mask: String): Option[Int] = {
    val tokens = parseMask(mask).flatMap(_.tokens)
    Option.when(tokens.nonEmpty)(tokens.length)
  }

  def placeholder(mask: String): String =
    parseMask(mask)
      .flatMap(_.tokens)
      .map {
        case MaskToken.Slot(slotType) => slotType.placeholderChar
        case MaskToken.Literal(c)     => c
      }
      .mkString

  def isValid(value: String, mask: String): Boolean =
    value.trimAllToOpt match {
      case None =>
        true
      case Some(nonEmptyValue) =>
        val segments = parseMask(mask)
        val allSlots = segments.flatMap(_.tokens).collect { case MaskToken.Slot(st) => st }
        if (allSlots.isEmpty) {
          true
        } else {
          def matchSegments(remaining: List[MaskSegment], pos: Int): Boolean =
            remaining match {
              case Nil =>
                pos == nonEmptyValue.length
              case seg :: tail =>
                val segSlots = seg.tokens.collect { case MaskToken.Slot(st) => st }
                val k        = segSlots.length
                def matchesAllSlotsAtPos: Boolean =
                  pos + k <= nonEmptyValue.length &&
                    segSlots.indices.forall(j => segSlots(j).matches(nonEmptyValue.charAt(pos + j)))

                if (seg.isOptional) {
                  if (k == 0)
                    matchSegments(tail, pos)
                  else
                    (matchesAllSlotsAtPos && matchSegments(tail, pos + k)) || matchSegments(tail, pos)
                } else {
                  matchesAllSlotsAtPos && matchSegments(tail, pos + k)
                }
            }

          matchSegments(segments, 0)
        }
    }

  def toRawValue(input: String, mask: String): String =
    if (input.isEmpty) {
      ""
    } else {
      val segments = parseMask(mask)
      val allSlots = segments.flatMap(_.tokens).collect { case MaskToken.Slot(st) => st }
      if (allSlots.isEmpty) {
        input
      } else {
        extractRawAndCaret(input, input.length, segments, isDeletion = false)._1
      }
    }

  def formatDisplayValue(rawOrFormatted: String, mask: String): String =
    if (rawOrFormatted.isEmpty) {
      ""
    } else {
      val segments = parseMask(mask)
      if (segments.isEmpty) {
        rawOrFormatted
      } else {
        val allSlots = segments.flatMap(_.tokens).collect { case MaskToken.Slot(st) => st }
        val isDirectRawPrefix =
          rawOrFormatted.length <= allSlots.length &&
            rawOrFormatted.indices.forall(idx => allSlots(idx).matches(rawOrFormatted.charAt(idx)))

        val rawSlots =
          if (isDirectRawPrefix)
            rawOrFormatted.zip(allSlots).map { case (c, st) => st.transform(c) }.mkString
          else
            extractRawAndCaret(rawOrFormatted, rawOrFormatted.length, segments, isDeletion = false)._1

        if (rawSlots.isEmpty)
          rawOrFormatted
        else
          formatFromRawSlots(rawSlots, segments)._1
      }
    }

  def applyMask(
    userInput        : String,
    caretPos         : Int,
    previousFormatted: String,
    mask             : String,
    isDeleteForward  : Boolean = false
  ): MaskEditResult = {
    val segments = parseMask(mask)
    if (segments.isEmpty) {
      val clampedCaret = caretPos.max(0).min(userInput.length)
      MaskEditResult(userInput, userInput, clampedCaret)
    } else if (userInput.isEmpty) {
      MaskEditResult("", "", 0)
    } else {
      val isDeletion                     = previousFormatted.nonEmpty && userInput.length < previousFormatted.length
      val (rawCandidate, rawBeforeCaret) = extractRawAndCaret(userInput, caretPos, segments, isDeletion = isDeletion)
      val previousRaw =
        if (previousFormatted.isEmpty) ""
        else extractRawAndCaret(previousFormatted, previousFormatted.length, segments, isDeletion = false)._1

      val deletedLiteralSeparator =
        isDeletion && rawCandidate == previousRaw

      val (finalRaw, finalRawBeforeCaret) =
        if (deletedLiteralSeparator) {
          if (isDeleteForward && rawBeforeCaret < rawCandidate.length) {
            val updated = rawCandidate.substring(0, rawBeforeCaret) + rawCandidate.substring(rawBeforeCaret + 1)
            (updated, rawBeforeCaret)
          } else if (! isDeleteForward && rawBeforeCaret > 0) {
            val idx     = rawBeforeCaret - 1
            val updated = rawCandidate.substring(0, idx) + rawCandidate.substring(idx + 1)
            (updated, idx)
          } else if (rawBeforeCaret == 0 && rawCandidate.isEmpty) {
            ("", 0)
          } else {
            (rawCandidate, rawBeforeCaret)
          }
        } else {
          (rawCandidate, rawBeforeCaret)
        }

      if (finalRaw.isEmpty) {
        MaskEditResult("", "", 0)
      } else {
        val (formatted, caretPositions) = formatFromRawSlots(finalRaw, segments)
        val clampedRawIdx               = finalRawBeforeCaret.max(0).min(caretPositions.length - 1)
        MaskEditResult(finalRaw, formatted, caretPositions(clampedRawIdx))
      }
    }
  }

  private def extractRawAndCaret(
    userInput : String,
    caretPos  : Int,
    segments  : List[MaskSegment],
    isDeletion: Boolean
  ): (String, Int) = {
    val allTokens          = segments.flatMap(_.tokens).toVector
    val tokenCount         = allTokens.length
    val inputLen           = userInput.length
    val clampedCaret       = caretPos.max(0).min(inputLen)
    val rawBuilder         = new StringBuilder
    var rawCharsBeforeCaret = 0
    var i                  = 0
    var t                  = 0

    def literalCharMatches(inputChar: Char, litChar: Char): Boolean =
      inputChar == litChar || (litChar.isLetter && inputChar.toUpper == litChar.toUpper)

    def literalRunAt(startTokenIdx: Int): Vector[Char] = {
      val buf = Vector.newBuilder[Char]
      var k   = startTokenIdx
      while (k < tokenCount && allTokens(k).isInstanceOf[MaskToken.Literal]) {
        buf += allTokens(k).asInstanceOf[MaskToken.Literal].char
        k += 1
      }
      buf.result()
    }

    def nextSlotOpt(fromTokenIdx: Int): Option[SlotType] = {
      var k = fromTokenIdx
      while (k < tokenCount) {
        allTokens(k) match {
          case MaskToken.Slot(st) => return Some(st)
          case _                  => k += 1
        }
      }
      None
    }

    while (i < inputLen && t < tokenCount) {
      allTokens(t) match {
        case MaskToken.Literal(lit) =>
          val run = literalRunAt(t)
          val runMatchesInput =
            i + run.length <= inputLen &&
              run.indices.forall(j => literalCharMatches(userInput.charAt(i + j), run(j)))

          if (run.length > 1) {
            if (runMatchesInput) {
              i += run.length
              t += run.length
            } else {
              t += run.length
            }
          } else {
            val c             = userInput.charAt(i)
            val nextSlot      = nextSlotOpt(t + 1)
            val conflictsSlot = nextSlot.exists(_.matches(c))
            if (literalCharMatches(c, lit) && (! conflictsSlot || isDeletion || i + 1 < inputLen)) {
              i += 1
              t += 1
            } else {
              t += 1
            }
          }

        case MaskToken.Slot(slotType) =>
          val c = userInput.charAt(i)
          if (slotType.matches(c)) {
            rawBuilder += slotType.transform(c)
            if (i < clampedCaret)
              rawCharsBeforeCaret += 1
            i += 1
            t += 1
          } else {
            i += 1
          }
      }
    }

    (rawBuilder.toString, rawCharsBeforeCaret)
  }

  private def formatFromRawSlots(
    rawSlots: String,
    segments: List[MaskSegment]
  ): (String, Vector[Int]) = {
    if (rawSlots.isEmpty) {
      ("", Vector(0))
    } else {
      val totalRaw        = rawSlots.length
      val result          = new StringBuilder
      val pendingLiterals = new StringBuilder
      val caretPositions  = Vector.newBuilder[Int]
      caretPositions += 0

      var rawIdx = 0

      segments.foreach { segment =>
        val segTokens    = segment.tokens
        val segSlotCount = segTokens.count(_.isInstanceOf[MaskToken.Slot])
        val slotsToFill  = (totalRaw - rawIdx).max(0).min(segSlotCount)

        if (slotsToFill > 0) {
          var segSlotsEmitted = 0
          segTokens.foreach {
            case MaskToken.Literal(c) =>
              if (segSlotsEmitted < slotsToFill) {
                pendingLiterals += c
              } else if (slotsToFill == segSlotCount) {
                result += c
              }
            case MaskToken.Slot(slotType) =>
              if (segSlotsEmitted < slotsToFill && rawIdx < totalRaw) {
                if (pendingLiterals.nonEmpty) {
                  result.append(pendingLiterals)
                  pendingLiterals.clear()
                }
                result += slotType.transform(rawSlots.charAt(rawIdx))
                rawIdx += 1
                segSlotsEmitted += 1
                caretPositions += result.length
              }
          }
        }
      }

      val positions = caretPositions.result()
      val updatedPositions =
        if (positions.nonEmpty && result.length > positions.last)
          positions.updated(positions.length - 1, result.length)
        else
          positions

      (result.toString, updatedPositions)
    }
  }
}

object MaskedInputSupportJava {

  //@XPathFunction
  def isValid(value: String, mask: String): Boolean =
    MaskedInputSupport.isValid(Option(value).getOrElse(""), Option(mask).getOrElse(""))

  //@XPathFunction
  def toRawValue(value: String, mask: String): String =
    MaskedInputSupport.toRawValue(Option(value).getOrElse(""), Option(mask).getOrElse(""))

  //@XPathFunction
  def formatDisplayValue(value: String, mask: String): String =
    MaskedInputSupport.formatDisplayValue(Option(value).getOrElse(""), Option(mask).getOrElse(""))

  //@XPathFunction
  def inputMode(mask: String): String =
    MaskedInputSupport.inputMode(Option(mask).getOrElse(""))

  //@XPathFunction
  def maxLength(mask: String): String =
    MaskedInputSupport.maxLengthOpt(Option(mask).getOrElse("")).map(_.toString).getOrElse("")

  //@XPathFunction
  def placeholder(mask: String): String =
    MaskedInputSupport.placeholder(Option(mask).getOrElse(""))
}
