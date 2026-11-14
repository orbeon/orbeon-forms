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

import java.util.Base64
import scala.collection.mutable.ArrayBuffer


object BarcodeSupport {

  val DefaultFormat        : String = "CODE128"
  val DefaultHeightPx      : Int    = 60
  val MinHeightPx          : Int    = 24
  val MaxHeightPx          : Int    = 240
  val DefaultModuleWidthPx : Int    = 2
  val MinModuleWidthPx     : Int    = 1
  val MaxModuleWidthPx     : Int    = 4
  val QuietZoneModules     : Int    = 10

  sealed trait BarcodeFormat
  object BarcodeFormat {
    case object Code128 extends BarcodeFormat
    case object Code39  extends BarcodeFormat
    case object Ean13   extends BarcodeFormat
    case object Ean8    extends BarcodeFormat
    case object UpcA    extends BarcodeFormat
    case object Itf     extends BarcodeFormat
    case object Codabar extends BarcodeFormat

    def fromString(s: String): BarcodeFormat =
      Option(s).flatMap(_.trimAllToOpt).map(_.toUpperCase.replace("-", "").replace("_", "")) match {
        case Some("CODE39")              => Code39
        case Some("EAN13" | "EAN")       => Ean13
        case Some("EAN8")                => Ean8
        case Some("UPCA" | "UPC")        => UpcA
        case Some("ITF" | "ITF14")       => Itf
        case Some("CODABAR")             => Codabar
        case _                           => Code128
      }
  }

  def normalizeHeightPx(heightPxStr: String): Int =
    Option(heightPxStr)
      .flatMap(_.trimAllToOpt)
      .flatMap(_.toIntOption)
      .map(n => math.max(MinHeightPx, math.min(MaxHeightPx, n)))
      .getOrElse(DefaultHeightPx)

  def normalizeModuleWidthPx(moduleWidthPxStr: String): Int =
    Option(moduleWidthPxStr)
      .flatMap(_.trimAllToOpt)
      .flatMap(_.toIntOption)
      .map(n => math.max(MinModuleWidthPx, math.min(MaxModuleWidthPx, n)))
      .getOrElse(DefaultModuleWidthPx)

  def encodeWithDisplayValue(
    text  : String,
    format: String = DefaultFormat
  ): Option[(Array[Boolean], String)] =
    Option(text).map(_.trim).filter(_.nonEmpty).flatMap { trimmed =>
      BarcodeFormat.fromString(format) match {
        case BarcodeFormat.Code128 => encodeCode128(trimmed).map(_ -> trimmed)
        case BarcodeFormat.Code39  => encodeCode39(trimmed.toUpperCase).map(_ -> trimmed.toUpperCase)
        case BarcodeFormat.Ean13   => encodeEan13(trimmed)
        case BarcodeFormat.UpcA    => encodeUpcA(trimmed)
        case BarcodeFormat.Ean8    => encodeEan8(trimmed)
        case BarcodeFormat.Itf     => encodeItf(trimmed)
        case BarcodeFormat.Codabar => encodeCodabar(trimmed.toUpperCase)
      }
    }

  def encodeModules(
    text  : String,
    format: String = DefaultFormat
  ): Option[Array[Boolean]] =
    encodeWithDisplayValue(text, format).map(_._1)

  def displayValue(
    text  : String,
    format: String = DefaultFormat
  ): String =
    encodeWithDisplayValue(text, format).map(_._2).getOrElse(Option(text).getOrElse(""))

  def imageWidthPx(
    text            : String,
    format          : String = DefaultFormat,
    moduleWidthPxStr: String = DefaultModuleWidthPx.toString
  ): Int =
    encodeModules(text, format).fold(0) { modules =>
      val modWidth = normalizeModuleWidthPx(moduleWidthPxStr)
      (modules.length + QuietZoneModules * 2) * modWidth
    }

  def toPngDataUri(
    text            : String,
    format          : String = DefaultFormat,
    heightPxStr     : String = DefaultHeightPx.toString,
    moduleWidthPxStr: String = DefaultModuleWidthPx.toString
  ): String =
    encodeModules(text, format).fold("") { modules =>
      val heightPx  = normalizeHeightPx(heightPxStr)
      val modWidth  = normalizeModuleWidthPx(moduleWidthPxStr)
      val totalMods = modules.length + QuietZoneModules * 2
      val widthPx   = totalMods * modWidth
      val pngBytes  = QrCodeSupport.encodeMonochromePngRect(widthPx, heightPx) { (px, _) =>
        val mx = px / modWidth - QuietZoneModules
        mx >= 0 && mx < modules.length && modules(mx)
      }
      val base64 = Base64.getEncoder.encodeToString(pngBytes)
      s"data:image/png;base64,$base64"
    }

  def toSvg(
    text            : String,
    format          : String = DefaultFormat,
    heightPxStr     : String = DefaultHeightPx.toString,
    moduleWidthPxStr: String = DefaultModuleWidthPx.toString
  ): String =
    encodeModules(text, format).fold("") { modules =>
      val heightPx  = normalizeHeightPx(heightPxStr)
      val modWidth  = normalizeModuleWidthPx(moduleWidthPxStr)
      val totalMods = modules.length + QuietZoneModules * 2
      val widthPx   = totalMods * modWidth
      val sb        = new java.lang.StringBuilder(512)
      sb.append(s"""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 $totalMods $heightPx" width="$widthPx" height="$heightPx" shape-rendering="crispEdges">""")
      sb.append(s"""<rect width="$totalMods" height="$heightPx" fill="#ffffff"/>""")
      sb.append("""<path fill="#000000" d="""")
      var x = 0
      while (x < modules.length) {
        if (modules(x)) {
          val startX = x
          while (x < modules.length && modules(x))
            x += 1
          val runLen = x - startX
          val px     = startX + QuietZoneModules
          sb.append(s"M${px},0h${runLen}v${heightPx}h-${runLen}z")
        } else {
          x += 1
        }
      }
      sb.append(""""/></svg>""")
      sb.toString
    }

  // ---- Code 128 (ISO/IEC 15417) ------------------------------------------------------------------------------------

  private val Code128StartB = 104
  private val Code128StartC = 105
  private val Code128CodeC  = 99
  private val Code128CodeB  = 100
  private val Code128Stop   = 106

  private val Code128Widths: Array[String] = Array(
    "212222", "222122", "222221", "121223", "121322", "131222", "122213", "122312", "132212", "221213",
    "221312", "231212", "112232", "122132", "122231", "113222", "123122", "123221", "223211", "221132",
    "221231", "213212", "223112", "312131", "311222", "321122", "321221", "312212", "322112", "322211",
    "212123", "212321", "232121", "111323", "131123", "131321", "112313", "132113", "132311", "211313",
    "231113", "231311", "112133", "112331", "132131", "113123", "113321", "133121", "313121", "211331",
    "231131", "213113", "213311", "213131", "311123", "311321", "331121", "312113", "312311", "332111",
    "314111", "221411", "431111", "111224", "111422", "121124", "121421", "141122", "141221", "112214",
    "112412", "122114", "122411", "142112", "142211", "241211", "221114", "413111", "241112", "134111",
    "111242", "121142", "121241", "114212", "124112", "124211", "411212", "421112", "421211", "212141",
    "214121", "412121", "111143", "111341", "131141", "114113", "114311", "411113", "411311", "113141",
    "114131", "311141", "411131", "211412", "211214", "211232", "2331112"
  )

  private def countConsecutiveDigits(s: String, from: Int): Int = {
    var i = from
    while (i < s.length && s.charAt(i) >= '0' && s.charAt(i) <= '9')
      i += 1
    i - from
  }

  private def encodeCode128(text: String): Option[Array[Boolean]] =
    if (text.forall(ch => ch >= 32 && ch <= 126)) {
      val symbols  = new ArrayBuffer[Int](text.length + 4)
      val initDigits = countConsecutiveDigits(text, 0)
      var inSetC   = initDigits >= 4 || (initDigits == 2 && text.length == 2)
      symbols += (if (inSetC) Code128StartC else Code128StartB)

      var i = 0
      while (i < text.length) {
        if (inSetC) {
          if (countConsecutiveDigits(text, i) >= 2) {
            val pairVal = (text.charAt(i) - '0') * 10 + (text.charAt(i + 1) - '0')
            symbols += pairVal
            i += 2
          } else {
            symbols += Code128CodeB
            inSetC = false
          }
        } else {
          val digitsAhead = countConsecutiveDigits(text, i)
          if (digitsAhead >= 4) {
            if ((digitsAhead & 1) == 1) {
              symbols += (text.charAt(i) - 32)
              i += 1
            }
            symbols += Code128CodeC
            inSetC = true
          } else {
            symbols += (text.charAt(i) - 32)
            i += 1
          }
        }
      }

      var checksum = symbols(0)
      var pos      = 1
      while (pos < symbols.length) {
        checksum += symbols(pos) * pos
        pos += 1
      }
      symbols += (checksum % 103)
      symbols += Code128Stop

      val modules = new ArrayBuffer[Boolean](symbols.length * 11 + 2)
      symbols.foreach { sym =>
        val widths = Code128Widths(sym)
        var elemIdx = 0
        while (elemIdx < widths.length) {
          val isBar = (elemIdx & 1) == 0
          val w     = widths.charAt(elemIdx) - '0'
          var k     = 0
          while (k < w) {
            modules += isBar
            k += 1
          }
          elemIdx += 1
        }
      }
      Some(modules.toArray)
    } else {
      None
    }

  // ---- Code 39 (ISO/IEC 16388) -------------------------------------------------------------------------------------

  private val Code39Patterns: Map[Char, String] = Map(
    '0' -> "000110100", '1' -> "100100001", '2' -> "001100001", '3' -> "101100000",
    '4' -> "000110001", '5' -> "100110000", '6' -> "001110000", '7' -> "000100101",
    '8' -> "100100100", '9' -> "001100100", 'A' -> "100001001", 'B' -> "001001001",
    'C' -> "101001000", 'D' -> "000011001", 'E' -> "100011000", 'F' -> "001011000",
    'G' -> "000001101", 'H' -> "100001100", 'I' -> "001001100", 'J' -> "000011100",
    'K' -> "100000011", 'L' -> "001000011", 'M' -> "101000010", 'N' -> "000010011",
    'O' -> "100010010", 'P' -> "001010010", 'Q' -> "000000111", 'R' -> "100000110",
    'S' -> "001000110", 'T' -> "000010110", 'U' -> "110000001", 'V' -> "011000001",
    'W' -> "111000000", 'X' -> "010010001", 'Y' -> "110010000", 'Z' -> "011010000",
    '-' -> "010000101", '.' -> "110000100", ' ' -> "011000100", '$' -> "010101000",
    '/' -> "010100010", '+' -> "010001010", '%' -> "000101010", '*' -> "010010100"
  )

  private def encodeCode39(text: String): Option[Array[Boolean]] = {
    val clean = text.stripPrefix("*").stripSuffix("*")
    if (clean.nonEmpty && clean.forall(ch => ch != '*' && Code39Patterns.contains(ch))) {
      val fullText = s"*$clean*"
      val modules  = new ArrayBuffer[Boolean](fullText.length * 16)
      var idx      = 0
      while (idx < fullText.length) {
        if (idx > 0)
          modules += false // Inter-character narrow space
        val pat = Code39Patterns(fullText.charAt(idx))
        var j   = 0
        while (j < 9) {
          val isBar = (j & 1) == 0
          val width = if (pat.charAt(j) == '1') 3 else 1
          var k     = 0
          while (k < width) {
            modules += isBar
            k += 1
          }
          j += 1
        }
        idx += 1
      }
      Some(modules.toArray)
    } else {
      None
    }
  }

  // ---- EAN-13, EAN-8, and UPC-A (ISO/IEC 15420) -------------------------------------------------------------------

  private val EanLPatterns: Array[String] = Array(
    "0001101", "0011001", "0010011", "0111101", "0100011",
    "0110001", "0101111", "0111011", "0110111", "0001011"
  )

  private val EanGPatterns: Array[String] = Array(
    "0100111", "0110011", "0011011", "0100001", "0011101",
    "0111001", "0000101", "0010001", "0001001", "0010111"
  )

  private val EanRPatterns: Array[String] = Array(
    "1110010", "1100110", "1101100", "1000010", "1011100",
    "1001110", "1010000", "1000100", "1001000", "1110100"
  )

  private val Ean13Parity: Array[String] = Array(
    "LLLLLL", "LLGLGG", "LLGGLG", "LLGGGL", "LGLLGG",
    "LGGLLG", "LGGGLL", "LGLGLG", "LGLGGL", "LGGLGL"
  )

  private def computeEanCheckDigit(digits: String): Char = {
    var sum = 0
    var i   = 0
    val n   = digits.length
    while (i < n) {
      val d      = digits.charAt(n - 1 - i) - '0'
      val weight = if ((i & 1) == 0) 3 else 1
      sum += d * weight
      i += 1
    }
    ((10 - (sum % 10)) % 10 + '0').toChar
  }

  private def appendBitString(buf: ArrayBuffer[Boolean], bits: String): Unit = {
    var i = 0
    while (i < bits.length) {
      buf += (bits.charAt(i) == '1')
      i += 1
    }
  }

  private def encodeEan13Modules(digits13: String): Array[Boolean] = {
    val modules    = new ArrayBuffer[Boolean](95)
    val firstDigit = digits13.charAt(0) - '0'
    val parity     = Ean13Parity(firstDigit)

    // Left guard: 101
    appendBitString(modules, "101")

    var i = 1
    while (i <= 6) {
      val d   = digits13.charAt(i) - '0'
      val pat = if (parity.charAt(i - 1) == 'L') EanLPatterns(d) else EanGPatterns(d)
      appendBitString(modules, pat)
      i += 1
    }

    // Center guard: 01010
    appendBitString(modules, "01010")

    i = 7
    while (i <= 12) {
      val d = digits13.charAt(i) - '0'
      appendBitString(modules, EanRPatterns(d))
      i += 1
    }

    // Right guard: 101
    appendBitString(modules, "101")
    modules.toArray
  }

  private def encodeEan13(text: String): Option[(Array[Boolean], String)] =
    if (text.forall(ch => ch >= '0' && ch <= '9')) {
      val full13Opt =
        if (text.length == 12)
          Some(text + computeEanCheckDigit(text))
        else if (text.length == 13 && computeEanCheckDigit(text.substring(0, 12)) == text.charAt(12))
          Some(text)
        else
          None
      full13Opt.map(d13 => encodeEan13Modules(d13) -> d13)
    } else {
      None
    }

  private def encodeUpcA(text: String): Option[(Array[Boolean], String)] =
    if (text.forall(ch => ch >= '0' && ch <= '9')) {
      val full12Opt =
        if (text.length == 11)
          Some(text + computeEanCheckDigit(text))
        else if (text.length == 12 && computeEanCheckDigit(text.substring(0, 11)) == text.charAt(11))
          Some(text)
        else
          None
      full12Opt.map(d12 => encodeEan13Modules("0" + d12) -> d12)
    } else {
      None
    }

  private def encodeEan8(text: String): Option[(Array[Boolean], String)] =
    if (text.forall(ch => ch >= '0' && ch <= '9')) {
      val full8Opt =
        if (text.length == 7)
          Some(text + computeEanCheckDigit(text))
        else if (text.length == 8 && computeEanCheckDigit(text.substring(0, 7)) == text.charAt(7))
          Some(text)
        else
          None
      full8Opt.map { d8 =>
        val modules = new ArrayBuffer[Boolean](67)
        appendBitString(modules, "101")
        var i = 0
        while (i < 4) {
          appendBitString(modules, EanLPatterns(d8.charAt(i) - '0'))
          i += 1
        }
        appendBitString(modules, "01010")
        while (i < 8) {
          appendBitString(modules, EanRPatterns(d8.charAt(i) - '0'))
          i += 1
        }
        appendBitString(modules, "101")
        modules.toArray -> d8
      }
    } else {
      None
    }

  // ---- ITF (Interleaved 2 of 5, ISO/IEC 16390) ---------------------------------------------------------------------

  private val ItfPatterns: Array[String] = Array(
    "00110", "10001", "01001", "11000", "00101",
    "10100", "01100", "00011", "10010", "01010"
  )

  private def encodeItf(text: String): Option[(Array[Boolean], String)] =
    if (text.nonEmpty && text.forall(ch => ch >= '0' && ch <= '9')) {
      val evenText = if ((text.length & 1) == 1) "0" + text else text
      val modules  = new ArrayBuffer[Boolean](evenText.length * 9 + 9)
      // Start pattern: narrow bar, narrow space, narrow bar, narrow space (1010)
      appendBitString(modules, "1010")
      var i = 0
      while (i < evenText.length) {
        val patBars   = ItfPatterns(evenText.charAt(i) - '0')
        val patSpaces = ItfPatterns(evenText.charAt(i + 1) - '0')
        var j = 0
        while (j < 5) {
          val barWidth = if (patBars.charAt(j) == '1') 3 else 1
          var k = 0
          while (k < barWidth) {
            modules += true
            k += 1
          }
          val spaceWidth = if (patSpaces.charAt(j) == '1') 3 else 1
          k = 0
          while (k < spaceWidth) {
            modules += false
            k += 1
          }
          j += 1
        }
        i += 2
      }
      // Stop pattern: wide bar (3), narrow space (1), narrow bar (1) -> 11101
      appendBitString(modules, "11101")
      Some(modules.toArray -> evenText)
    } else {
      None
    }

  // ---- Codabar (ANSI/AIM BC3-1995) ---------------------------------------------------------------------------------

  private val CodabarPatterns: Map[Char, String] = Map(
    '0' -> "0000011", '1' -> "0000110", '2' -> "0001001", '3' -> "1100000",
    '4' -> "0010010", '5' -> "1000010", '6' -> "0100001", '7' -> "0100100",
    '8' -> "0110000", '9' -> "1001000", '-' -> "0001100", '$' -> "0011000",
    ':' -> "1000101", '/' -> "1010001", '.' -> "1010100", '+' -> "0010101",
    'A' -> "0011010", 'B' -> "0101001", 'C' -> "0001011", 'D' -> "0001110"
  )

  private val CodabarStartStop: Set[Char] = Set('A', 'B', 'C', 'D')

  private def encodeCodabar(text: String): Option[(Array[Boolean], String)] = {
    val hasStartStop =
      text.length >= 3 &&
      CodabarStartStop.contains(text.head) &&
      CodabarStartStop.contains(text.last)
    val fullText = if (hasStartStop) text else s"A${text}A"
    val body     = fullText.substring(1, fullText.length - 1)
    if (body.nonEmpty && body.forall(ch => ! CodabarStartStop.contains(ch) && CodabarPatterns.contains(ch))) {
      val modules = new ArrayBuffer[Boolean](fullText.length * 12)
      var idx     = 0
      while (idx < fullText.length) {
        if (idx > 0)
          modules += false
        val pat = CodabarPatterns(fullText.charAt(idx))
        var j   = 0
        while (j < 7) {
          val isBar = (j & 1) == 0
          val width = if (pat.charAt(j) == '1') 3 else 1
          var k     = 0
          while (k < width) {
            modules += isBar
            k += 1
          }
          j += 1
        }
        idx += 1
      }
      val display = if (hasStartStop) fullText else text
      Some(modules.toArray -> display)
    } else {
      None
    }
  }
}

object BarcodeSupportJava {

  //@XPathFunction
  def toPngDataUri(
    text            : String,
    format          : String,
    heightPxStr     : String,
    moduleWidthPxStr: String
  ): String =
    BarcodeSupport.toPngDataUri(text, format, heightPxStr, moduleWidthPxStr)

  //@XPathFunction
  def imageWidthPx(
    text            : String,
    format          : String,
    moduleWidthPxStr: String
  ): Int =
    BarcodeSupport.imageWidthPx(text, format, moduleWidthPxStr)

  //@XPathFunction
  def displayValue(text: String, format: String): String =
    BarcodeSupport.displayValue(text, format)

  //@XPathFunction
  def normalizeHeightPx(heightPxStr: String): Int =
    BarcodeSupport.normalizeHeightPx(heightPxStr)

  //@XPathFunction
  def normalizeModuleWidthPx(moduleWidthPxStr: String): Int =
    BarcodeSupport.normalizeModuleWidthPx(moduleWidthPxStr)
}
