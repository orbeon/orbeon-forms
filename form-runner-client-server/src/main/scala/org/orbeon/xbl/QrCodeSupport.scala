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

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.Base64


object QrCodeSupport {

  val DefaultSizePx          : Int    = 160
  val MinSizePx              : Int    = 64
  val MaxSizePx              : Int    = 512
  val DefaultErrorCorrection : String = "M"
  val QuietZoneModules       : Int    = 4
  val MinVersion             : Int    = 1
  val MaxVersion             : Int    = 40

  sealed abstract class EcLevel(val formatBits: Int, val ordinal: Int)
  object EcLevel {
    case object L extends EcLevel(1, 0)
    case object M extends EcLevel(0, 1)
    case object Q extends EcLevel(3, 2)
    case object H extends EcLevel(2, 3)

    def fromString(s: String): EcLevel =
      Option(s).flatMap(_.trimAllToOpt).map(_.toUpperCase) match {
        case Some("L") => L
        case Some("Q") => Q
        case Some("H") => H
        case _         => M
      }
  }

  def normalizeSizePx(sizePxStr: String): Int =
    Option(sizePxStr)
      .flatMap(_.trimAllToOpt)
      .flatMap(_.toIntOption)
      .map(n => math.max(MinSizePx, math.min(MaxSizePx, n)))
      .getOrElse(DefaultSizePx)

  // ISO/IEC 18004 tables for Versions 1..40 (index 0 unused)
  private val TotalCodewords: Array[Int] = Array(
    0,
    26,   44,   70,  100,  134,  172,  196,  242,  292,  346,
    404,  466,  532,  581,  655,  733,  815,  901,  991, 1085,
    1156, 1258, 1364, 1474, 1588, 1706, 1828, 1921, 2051, 2185,
    2323, 2465, 2611, 2761, 2876, 3034, 3196, 3362, 3532, 3706
  )

  private val EcCodewordsPerBlock: Array[Array[Int]] = Array(
    // L
    Array(
      0,
       7, 10, 15, 20, 26, 18, 20, 24, 30, 18,
      20, 24, 26, 30, 22, 24, 28, 30, 28, 28,
      28, 28, 30, 30, 26, 28, 30, 30, 30, 30,
      30, 30, 30, 30, 30, 30, 30, 30, 30, 30
    ),
    // M
    Array(
      0,
      10, 16, 26, 18, 24, 16, 18, 22, 22, 26,
      30, 22, 22, 24, 24, 28, 28, 26, 26, 26,
      26, 28, 28, 28, 28, 28, 28, 28, 28, 28,
      28, 28, 28, 28, 28, 28, 28, 28, 28, 28
    ),
    // Q
    Array(
      0,
      13, 22, 18, 26, 18, 24, 18, 22, 20, 24,
      28, 26, 24, 20, 30, 24, 28, 28, 26, 30,
      28, 30, 30, 30, 30, 28, 30, 30, 30, 30,
      30, 30, 30, 30, 30, 30, 30, 30, 30, 30
    ),
    // H
    Array(
      0,
      17, 28, 22, 16, 22, 28, 26, 26, 24, 28,
      24, 28, 22, 24, 24, 30, 28, 28, 26, 28,
      30, 24, 30, 30, 30, 30, 30, 30, 30, 30,
      30, 30, 30, 30, 30, 30, 30, 30, 30, 30
    )
  )

  private val NumErrorCorrectionBlocks: Array[Array[Int]] = Array(
    // L
    Array(
      0,
       1,  1,  1,  1,  1,  2,  2,  2,  2,  4,
       4,  4,  4,  4,  6,  6,  6,  6,  7,  8,
       8,  9,  9, 10, 12, 12, 12, 13, 14, 15,
      16, 17, 18, 19, 19, 20, 21, 22, 24, 25
    ),
    // M
    Array(
      0,
       1,  1,  1,  2,  2,  4,  4,  4,  5,  5,
       5,  8,  9,  9, 10, 10, 11, 13, 14, 16,
      17, 17, 18, 20, 21, 23, 25, 26, 28, 29,
      31, 33, 35, 37, 38, 40, 43, 45, 47, 49
    ),
    // Q
    Array(
      0,
       1,  1,  2,  2,  4,  4,  6,  6,  8,  8,
       8, 10, 12, 16, 12, 17, 16, 18, 21, 20,
      23, 23, 25, 27, 29, 34, 34, 35, 38, 40,
      43, 45, 48, 51, 53, 56, 59, 62, 65, 68
    ),
    // H
    Array(
      0,
       1,  1,  2,  4,  4,  4,  5,  6,  8,  8,
      11, 11, 16, 16, 18, 16, 19, 21, 25, 25,
      25, 34, 30, 32, 35, 37, 40, 42, 45, 48,
      51, 54, 57, 60, 63, 66, 70, 74, 77, 81
    )
  )

  private def alignmentPatternCenters(version: Int): Array[Int] =
    if (version <= 1) {
      Array.emptyIntArray
    } else {
      val numAlign = version / 7 + 2
      val step     =
        if (version == 32) 26
        else ((version * 4 + numAlign * 2 + 1) / (numAlign * 2 - 2)) * 2
      val result = new Array[Int](numAlign)
      result(0) = 6
      var pos = version * 4 + 10
      var i   = numAlign - 1
      while (i >= 1) {
        result(i) = pos
        pos -= step
        i -= 1
      }
      result
    }

  def encodeMatrix(text: String, errorCorrectionLevel: String = DefaultErrorCorrection): Option[Array[Array[Boolean]]] =
    Option(text).filter(_.nonEmpty).flatMap { nonEmptyText =>
      val utf8Bytes = nonEmptyText.getBytes(StandardCharsets.UTF_8)
      val ecLevel   = EcLevel.fromString(errorCorrectionLevel)

      findVersion(utf8Bytes.length, ecLevel).map { version =>
        val dataCodewords = encodeDataCodewords(utf8Bytes, version, ecLevel)
        val allCodewords  = addErrorCorrectionAndInterleave(dataCodewords, version, ecLevel)
        buildBestMaskedMatrix(allCodewords, version, ecLevel)
      }
    }

  def toPngDataUri(
    text                : String,
    errorCorrectionLevel: String = DefaultErrorCorrection,
    sizePxStr           : String = DefaultSizePx.toString
  ): String =
    encodeMatrix(text, errorCorrectionLevel).fold("") { matrix =>
      val targetPx   = normalizeSizePx(sizePxStr)
      val qrSize     = matrix.length
      val totalMods  = qrSize + QuietZoneModules * 2
      val scale      = math.max(2, math.round(targetPx.toFloat / totalMods.toFloat))
      val imgSize    = totalMods * scale
      val pngBytes   = encodeMonochromePng(matrix, QuietZoneModules, scale, imgSize)
      val base64     = Base64.getEncoder.encodeToString(pngBytes)
      s"data:image/png;base64,$base64"
    }

  def toSvg(
    text                : String,
    errorCorrectionLevel: String = DefaultErrorCorrection,
    sizePxStr           : String = DefaultSizePx.toString
  ): String =
    encodeMatrix(text, errorCorrectionLevel).fold("") { matrix =>
      val targetPx  = normalizeSizePx(sizePxStr)
      val qrSize    = matrix.length
      val totalMods = qrSize + QuietZoneModules * 2
      val sb        = new java.lang.StringBuilder(1024)
      sb.append(s"""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 $totalMods $totalMods" width="$targetPx" height="$targetPx" shape-rendering="crispEdges">""")
      sb.append(s"""<rect width="$totalMods" height="$totalMods" fill="#ffffff"/>""")
      sb.append("""<path fill="#000000" d="""")
      var y = 0
      while (y < qrSize) {
        var x = 0
        while (x < qrSize) {
          if (matrix(y)(x)) {
            val px = x + QuietZoneModules
            val py = y + QuietZoneModules
            sb.append(s"M${px},${py}h1v1h-1z")
          }
          x += 1
        }
        y += 1
      }
      sb.append(""""/></svg>""")
      sb.toString
    }

  private def charCountBits(version: Int): Int =
    if (version <= 9) 8 else 16

  private def getNumDataCodewords(version: Int, ecLevel: EcLevel): Int =
    TotalCodewords(version) -
      EcCodewordsPerBlock(ecLevel.ordinal)(version) * NumErrorCorrectionBlocks(ecLevel.ordinal)(version)

  private def findVersion(byteLen: Int, ecLevel: EcLevel): Option[Int] =
    (MinVersion to MaxVersion).find { v =>
      val capacityBits = getNumDataCodewords(v, ecLevel) * 8
      val neededBits   = 4 + charCountBits(v) + byteLen * 8
      neededBits <= capacityBits
    }

  private def encodeDataCodewords(utf8Bytes: Array[Byte], version: Int, ecLevel: EcLevel): Array[Byte] = {
    val capacityCodewords = getNumDataCodewords(version, ecLevel)
    val capacityBits      = capacityCodewords * 8

    val bits = new scala.collection.mutable.ArrayBuffer[Int](capacityBits)
    def appendBits(value: Int, len: Int): Unit = {
      var i = len - 1
      while (i >= 0) {
        bits += ((value >>> i) & 1)
        i -= 1
      }
    }

    // Byte mode indicator (0100)
    appendBits(0x4, 4)
    appendBits(utf8Bytes.length, charCountBits(version))
    utf8Bytes.foreach(b => appendBits(b & 0xFF, 8))

    // Terminator (up to 4 zero bits)
    val terminatorLen = math.min(4, capacityBits - bits.length)
    appendBits(0, terminatorLen)

    // Pad to byte boundary
    val padBits = (8 - (bits.length % 8)) % 8
    appendBits(0, padBits)

    // Pad bytes 0xEC, 0x11 alternately
    var padIndex = 0
    while (bits.length < capacityBits) {
      appendBits(if ((padIndex & 1) == 0) 0xEC else 0x11, 8)
      padIndex += 1
    }

    val result = new Array[Byte](capacityCodewords)
    var i = 0
    while (i < capacityCodewords) {
      var b = 0
      var j = 0
      while (j < 8) {
        b = (b << 1) | bits(i * 8 + j)
        j += 1
      }
      result(i) = b.toByte
      i += 1
    }
    result
  }

  private def addErrorCorrectionAndInterleave(
    data   : Array[Byte],
    version: Int,
    ecLevel: EcLevel
  ): Array[Byte] = {
    val numBlocks     = NumErrorCorrectionBlocks(ecLevel.ordinal)(version)
    val blockEcLen    = EcCodewordsPerBlock(ecLevel.ordinal)(version)
    val rawCodewords  = TotalCodewords(version)
    val numShortBlks  = numBlocks - (rawCodewords % numBlocks)
    val shortBlkLen   = rawCodewords / numBlocks
    val shortDataLen  = shortBlkLen - blockEcLen

    val rsDiv = reedSolomonDivisor(blockEcLen)

    val dataBlocks = new Array[Array[Byte]](numBlocks)
    val ecBlocks   = new Array[Array[Byte]](numBlocks)

    var k = 0
    var i = 0
    while (i < numBlocks) {
      val dataLen = shortDataLen + (if (i < numShortBlks) 0 else 1)
      val blkData = new Array[Byte](dataLen)
      System.arraycopy(data, k, blkData, 0, dataLen)
      k += dataLen
      dataBlocks(i) = blkData
      ecBlocks(i)   = reedSolomonRemainder(blkData, rsDiv)
      i += 1
    }

    val result   = new Array[Byte](rawCodewords)
    var outIndex = 0
    val maxDataLen = shortDataLen + 1
    var col = 0
    while (col < maxDataLen) {
      var row = 0
      while (row < numBlocks) {
        if (col < dataBlocks(row).length) {
          result(outIndex) = dataBlocks(row)(col)
          outIndex += 1
        }
        row += 1
      }
      col += 1
    }

    col = 0
    while (col < blockEcLen) {
      var row = 0
      while (row < numBlocks) {
        result(outIndex) = ecBlocks(row)(col)
        outIndex += 1
        row += 1
      }
      col += 1
    }

    result
  }

  private def gfMultiply(x: Int, y: Int): Int = {
    var z = 0
    var i = 7
    while (i >= 0) {
      z = (z << 1) ^ ((z >>> 7) * 0x11D)
      z ^= ((y >>> i) & 1) * x
      i -= 1
    }
    z
  }

  private def reedSolomonDivisor(degree: Int): Array[Byte] = {
    val result = new Array[Byte](degree)
    result(degree - 1) = 1
    var root = 1
    var i    = 0
    while (i < degree) {
      var j = 0
      while (j < degree) {
        result(j) = gfMultiply(result(j) & 0xFF, root).toByte
        if (j + 1 < degree)
          result(j) = (result(j) ^ result(j + 1)).toByte
        j += 1
      }
      root = gfMultiply(root, 0x02)
      i += 1
    }
    result
  }

  private def reedSolomonRemainder(data: Array[Byte], divisor: Array[Byte]): Array[Byte] = {
    val result = new Array[Byte](divisor.length)
    data.foreach { b =>
      val factor = (b ^ result(0)) & 0xFF
      System.arraycopy(result, 1, result, 0, result.length - 1)
      result(result.length - 1) = 0
      var i = 0
      while (i < result.length) {
        result(i) = (result(i) ^ gfMultiply(divisor(i) & 0xFF, factor)).toByte
        i += 1
      }
    }
    result
  }

  private def buildBestMaskedMatrix(
    allCodewords: Array[Byte],
    version     : Int,
    ecLevel     : EcLevel
  ): Array[Array[Boolean]] = {
    val size       = version * 4 + 17
    val modules    = Array.ofDim[Boolean](size, size)
    val isFunction = Array.ofDim[Boolean](size, size)

    def setFunctionModule(x: Int, y: Int, isDark: Boolean): Unit = {
      modules(y)(x)    = isDark
      isFunction(y)(x) = true
    }

    def drawFinderPattern(cx: Int, cy: Int): Unit = {
      var dy = -4
      while (dy <= 4) {
        var dx = -4
        while (dx <= 4) {
          val dist = math.max(math.abs(dx), math.abs(dy))
          val xx   = cx + dx
          val yy   = cy + dy
          if (xx >= 0 && xx < size && yy >= 0 && yy < size)
            setFunctionModule(xx, yy, dist != 2 && dist != 4)
          dx += 1
        }
        dy += 1
      }
    }

    def drawAlignmentPattern(cx: Int, cy: Int): Unit = {
      var dy = -2
      while (dy <= 2) {
        var dx = -2
        while (dx <= 2) {
          setFunctionModule(cx + dx, cy + dy, math.max(math.abs(dx), math.abs(dy)) != 1)
          dx += 1
        }
        dy += 1
      }
    }

    // Timing patterns
    var i = 0
    while (i < size) {
      setFunctionModule(6, i, i % 2 == 0)
      setFunctionModule(i, 6, i % 2 == 0)
      i += 1
    }

    // 3 Finder patterns
    drawFinderPattern(3, 3)
    drawFinderPattern(size - 4, 3)
    drawFinderPattern(3, size - 4)

    // Alignment patterns
    val alignCoords = alignmentPatternCenters(version)
    val numAlign    = alignCoords.length
    var ay = 0
    while (ay < numAlign) {
      var ax = 0
      while (ax < numAlign) {
        val isFinderCorner =
          (ax == 0 && ay == 0) ||
          (ax == 0 && ay == numAlign - 1) ||
          (ax == numAlign - 1 && ay == 0)
        if (! isFinderCorner)
          drawAlignmentPattern(alignCoords(ax), alignCoords(ay))
        ax += 1
      }
      ay += 1
    }

    // Reserve format bits & dark module
    drawFormatBits(modules, isFunction, ecLevel, 0, size)

    // Version bits (versions 7..40)
    if (version >= 7) {
      var rem = version
      var j   = 0
      while (j < 12) {
        rem = (rem << 1) ^ ((rem >>> 11) * 0x1F25)
        j += 1
      }
      val bits = (version << 12) | rem
      var bitIdx = 0
      while (bitIdx < 18) {
        val bit = ((bits >>> bitIdx) & 1) != 0
        val a   = size - 11 + (bitIdx % 3)
        val b   = bitIdx / 3
        setFunctionModule(a, b, bit)
        setFunctionModule(b, a, bit)
        bitIdx += 1
      }
    }

    // Place data codewords in zigzag scan
    var bitIndex = 0
    val totalBits = allCodewords.length * 8
    var right = size - 1
    while (right >= 1) {
      if (right == 6)
        right = 5
      var vert = 0
      while (vert < size) {
        var j = 0
        while (j < 2) {
          val x      = right - j
          val upward = ((right + 1) & 2) == 0
          val y      = if (upward) size - 1 - vert else vert
          if (! isFunction(y)(x) && bitIndex < totalBits) {
            val bit = ((allCodewords(bitIndex >>> 3) >>> (7 - (bitIndex & 7))) & 1) != 0
            modules(y)(x) = bit
            bitIndex += 1
          }
          j += 1
        }
        vert += 1
      }
      right -= 2
    }

    // Choose mask with lowest penalty
    var bestMask    = 0
    var bestPenalty = Int.MaxValue
    var bestMatrix  = modules

    var mask = 0
    while (mask < 8) {
      val candidate = cloneMatrix(modules)
      val dummyFn   = cloneMatrix(isFunction)
      applyMask(candidate, dummyFn, mask)
      drawFormatBits(candidate, dummyFn, ecLevel, mask, size)
      val penalty = computePenaltyScore(candidate)
      if (penalty < bestPenalty) {
        bestPenalty = penalty
        bestMask    = mask
        bestMatrix  = candidate
      }
      mask += 1
    }

    bestMatrix
  }

  private def cloneMatrix(src: Array[Array[Boolean]]): Array[Array[Boolean]] = {
    val n   = src.length
    val dst = Array.ofDim[Boolean](n, n)
    var y   = 0
    while (y < n) {
      System.arraycopy(src(y), 0, dst(y), 0, n)
      y += 1
    }
    dst
  }

  private def applyMask(
    modules   : Array[Array[Boolean]],
    isFunction: Array[Array[Boolean]],
    mask      : Int
  ): Unit = {
    val size = modules.length
    var y    = 0
    while (y < size) {
      var x = 0
      while (x < size) {
        if (! isFunction(y)(x)) {
          val invert = mask match {
            case 0 => (x + y) % 2 == 0
            case 1 => y % 2 == 0
            case 2 => x % 3 == 0
            case 3 => (x + y) % 3 == 0
            case 4 => (x / 3 + y / 2) % 2 == 0
            case 5 => (x * y) % 2 + (x * y) % 3 == 0
            case 6 => ((x * y) % 2 + (x * y) % 3) % 2 == 0
            case _ => ((x + y) % 2 + (x * y) % 3) % 2 == 0
          }
          if (invert)
            modules(y)(x) = ! modules(y)(x)
        }
        x += 1
      }
      y += 1
    }
  }

  private def drawFormatBits(
    modules   : Array[Array[Boolean]],
    isFunction: Array[Array[Boolean]],
    ecLevel   : EcLevel,
    mask      : Int,
    size      : Int
  ): Unit = {
    def setFn(x: Int, y: Int, isDark: Boolean): Unit = {
      modules(y)(x)    = isDark
      isFunction(y)(x) = true
    }

    val data = (ecLevel.formatBits << 3) | mask
    var rem  = data
    var i    = 0
    while (i < 10) {
      rem = (rem << 1) ^ ((rem >>> 9) * 0x537)
      i += 1
    }
    val bits = ((data << 10) | rem) ^ 0x5412

    i = 0
    while (i <= 5) {
      setFn(8, i, ((bits >>> i) & 1) != 0)
      i += 1
    }
    setFn(8, 7, ((bits >>> 6) & 1) != 0)
    setFn(8, 8, ((bits >>> 7) & 1) != 0)
    setFn(7, 8, ((bits >>> 8) & 1) != 0)
    i = 9
    while (i < 15) {
      setFn(14 - i, 8, ((bits >>> i) & 1) != 0)
      i += 1
    }

    i = 0
    while (i < 8) {
      setFn(size - 1 - i, 8, ((bits >>> i) & 1) != 0)
      i += 1
    }
    i = 8
    while (i < 15) {
      setFn(8, size - 15 + i, ((bits >>> i) & 1) != 0)
      i += 1
    }
    setFn(8, size - 8, isDark = true)
  }

  private def computePenaltyScore(modules: Array[Array[Boolean]]): Int = {
    val size    = modules.length
    var penalty = 0

    // Rule 1: runs of same color in rows and columns
    var y = 0
    while (y < size) {
      var runColor = modules(y)(0)
      var runLen   = 1
      var x        = 1
      while (x < size) {
        if (modules(y)(x) == runColor) {
          runLen += 1
          if (runLen == 5)
            penalty += 3
          else if (runLen > 5)
            penalty += 1
        } else {
          runColor = modules(y)(x)
          runLen   = 1
        }
        x += 1
      }
      y += 1
    }

    var x = 0
    while (x < size) {
      var runColor = modules(0)(x)
      var runLen   = 1
      y = 1
      while (y < size) {
        if (modules(y)(x) == runColor) {
          runLen += 1
          if (runLen == 5)
            penalty += 3
          else if (runLen > 5)
            penalty += 1
        } else {
          runColor = modules(y)(x)
          runLen   = 1
        }
        y += 1
      }
      x += 1
    }

    // Rule 2: 2x2 blocks of same color
    y = 0
    while (y < size - 1) {
      x = 0
      while (x < size - 1) {
        val c = modules(y)(x)
        if (c == modules(y)(x + 1) && c == modules(y + 1)(x) && c == modules(y + 1)(x + 1))
          penalty += 3
        x += 1
      }
      y += 1
    }

    // Rule 3: finder-like patterns (1:1:3:1:1 with 4 light modules on either side) in rows and columns
    y = 0
    while (y < size) {
      var bitsRow = 0
      var bitsCol = 0
      x = 0
      while (x < size) {
        bitsRow = ((bitsRow << 1) & 0x7FF) | (if (modules(y)(x)) 1 else 0)
        bitsCol = ((bitsCol << 1) & 0x7FF) | (if (modules(x)(y)) 1 else 0)
        if (x >= 10) {
          if (bitsRow == 0x05D || bitsRow == 0x5D0)
            penalty += 40
          if (bitsCol == 0x05D || bitsCol == 0x5D0)
            penalty += 40
        }
        x += 1
      }
      y += 1
    }

    // Rule 4: balance of dark and light modules
    var dark = 0
    y = 0
    while (y < size) {
      x = 0
      while (x < size) {
        if (modules(y)(x))
          dark += 1
        x += 1
      }
      y += 1
    }
    val total = size * size
    val k     = math.abs(dark * 20 - total * 10) / total
    penalty += k * 10

    penalty
  }

  // Pure-Scala 1-bit grayscale PNG encoder (zero external or AWT dependencies; runs on JVM and Scala.js)
  private def encodeMonochromePng(
    matrix   : Array[Array[Boolean]],
    quietZone: Int,
    scale    : Int,
    imgSize  : Int
  ): Array[Byte] = {
    val qrSize = matrix.length
    encodeMonochromePngRect(imgSize, imgSize) { (px, py) =>
      val mx = px / scale - quietZone
      val my = py / scale - quietZone
      mx >= 0 && mx < qrSize && my >= 0 && my < qrSize && matrix(my)(mx)
    }
  }

  private[xbl] def encodeMonochromePngRect(
    width      : Int,
    height     : Int
  )(
    isDarkPixel: (Int, Int) => Boolean
  ): Array[Byte] = {
    val rowByteLen  = (width + 7) / 8
    val scanlineLen = 1 + rowByteLen
    val rawData     = new Array[Byte](height * scanlineLen)

    var py = 0
    while (py < height) {
      val rowOffset = py * scanlineLen
      rawData(rowOffset) = 0 // Filter type 0 (None)
      var px = 0
      while (px < width) {
        // In 1-bit grayscale PNG: 0 = black (dark module), 1 = white (light module)
        if (! isDarkPixel(px, py)) {
          val byteIdx = rowOffset + 1 + (px >>> 3)
          val bitMask = 1 << (7 - (px & 7))
          rawData(byteIdx) = (rawData(byteIdx) | bitMask).toByte
        }
        px += 1
      }
      py += 1
    }

    val out = new ByteArrayOutputStream(rawData.length + 128)
    // PNG Signature
    out.write(Array[Byte](0x89.toByte, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))

    // IHDR chunk (width, height, bitDepth=1, colorType=0 grayscale, compression=0, filter=0, interlace=0)
    val ihdr = new ByteArrayOutputStream(13)
    writeInt32BE(ihdr, width)
    writeInt32BE(ihdr, height)
    ihdr.write(1) // bit depth = 1
    ihdr.write(0) // color type = 0 (grayscale)
    ihdr.write(0) // compression = 0
    ihdr.write(0) // filter = 0
    ihdr.write(0) // interlace = 0
    writePngChunk(out, "IHDR", ihdr.toByteArray)

    // IDAT chunk with zlib wrapper + stored deflate blocks
    val zlibOut = new ByteArrayOutputStream(rawData.length + 32)
    zlibOut.write(0x78)
    zlibOut.write(0x01)

    var offset = 0
    while (offset < rawData.length) {
      val blockLen = math.min(65535, rawData.length - offset)
      val isFinal  = if (offset + blockLen >= rawData.length) 1 else 0
      zlibOut.write(isFinal)
      zlibOut.write(blockLen & 0xFF)
      zlibOut.write((blockLen >>> 8) & 0xFF)
      val nlen = (~blockLen) & 0xFFFF
      zlibOut.write(nlen & 0xFF)
      zlibOut.write((nlen >>> 8) & 0xFF)
      zlibOut.write(rawData, offset, blockLen)
      offset += blockLen
    }
    writeInt32BE(zlibOut, adler32(rawData))
    writePngChunk(out, "IDAT", zlibOut.toByteArray)

    // IEND chunk
    writePngChunk(out, "IEND", Array.emptyByteArray)
    out.toByteArray
  }

  private def writeInt32BE(out: ByteArrayOutputStream, v: Int): Unit = {
    out.write((v >>> 24) & 0xFF)
    out.write((v >>> 16) & 0xFF)
    out.write((v >>> 8)  & 0xFF)
    out.write(v & 0xFF)
  }

  private def writePngChunk(out: ByteArrayOutputStream, chunkType: String, data: Array[Byte]): Unit = {
    val typeBytes = chunkType.getBytes(StandardCharsets.US_ASCII)
    writeInt32BE(out, data.length)
    out.write(typeBytes)
    out.write(data)
    writeInt32BE(out, crc32(typeBytes, data))
  }

  private def crc32(typeBytes: Array[Byte], data: Array[Byte]): Int = {
    var c = 0xFFFFFFFF
    def update(bytes: Array[Byte]): Unit = {
      var i = 0
      while (i < bytes.length) {
        c ^= (bytes(i) & 0xFF)
        var k = 0
        while (k < 8) {
          c = if ((c & 1) != 0) (c >>> 1) ^ 0xEDB88320 else c >>> 1
          k += 1
        }
        i += 1
      }
    }
    update(typeBytes)
    update(data)
    ~c
  }

  private def adler32(data: Array[Byte]): Int = {
    var a = 1
    var b = 0
    var i = 0
    while (i < data.length) {
      a = (a + (data(i) & 0xFF)) % 65521
      b = (b + a) % 65521
      i += 1
    }
    (b << 16) | a
  }
}

object QrCodeSupportJava {

  //@XPathFunction
  def toPngDataUri(text: String, errorCorrectionLevel: String, sizePxStr: String): String =
    QrCodeSupport.toPngDataUri(text, errorCorrectionLevel, sizePxStr)

  //@XPathFunction
  def normalizeSizePx(sizePxStr: String): Int =
    QrCodeSupport.normalizeSizePx(sizePxStr)
}
