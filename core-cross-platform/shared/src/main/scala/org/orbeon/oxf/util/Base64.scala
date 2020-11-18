/*
 * Copyright 1999,2004 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/*
 * With some modifications made for the Orbeon project.
 */
package org.orbeon.oxf.util


/**
 * This class provides encode/decode for RFC 2045 Base64 as
 * defined by RFC 2045, N. Freed and N. Borenstein.
 * RFC 2045: Multipurpose Internet Mail Extensions (MIME)
 * Part One: Format of Internet Message Bodies. Reference
 * 1996 Available at: http://www.ietf.org/rfc/rfc2045.txt
 * This class is used by XML Schema binary format validation
 *
 * This implementation does not encode/decode streaming
 * data. You need the data that you will encode/decode
 * already on a byte array.
 *
 * @author Jeffrey Rodriguez
 * @author Sandy Gao
 */
object Base64 {

  private val BASELENGTH           = 255
  private val LOOKUPLENGTH         = 64
  private val TWENTYFOURBITGROUP   = 24
  private val EIGHTBIT             = 8
  private val SIXTEENBIT           = 16
  private val FOURBYTE             = 4
  private val SIGN                 = -128
  private val PAD                  = '='
  private val fDebug               = false
  private val base64Alphabet       = new Array[Byte](BASELENGTH)
  private val lookUpBase64Alphabet = new Array[Char](LOOKUPLENGTH)

  locally {

    for (i <- 0 until BASELENGTH)
      base64Alphabet(i) = -1

    for (i <- 'A' to 'Z')
      base64Alphabet(i) = (i - 'A').toByte

    for (i <- 'a' to 'z')
      base64Alphabet(i) = (i - 'a' + 26).toByte

    for (i <- '0' to '9')
      base64Alphabet(i) = (i - '0' + 52).toByte

    base64Alphabet('+') = 62
    base64Alphabet('/') = 63

    for (i <- 0 to 25)
      lookUpBase64Alphabet(i) = ('A' + i).toChar

    locally {
      var i = 26
      var j = 0
      while (i <= 51) {
        lookUpBase64Alphabet(i) = ('a' + j).toChar
        i += 1
        j += 1
      }
    }

    locally {
      var i = 52
      var j = 0
      while (i <= 61) {
        lookUpBase64Alphabet(i) = ('0' + j).toChar
        i += 1
        j += 1
      }
    }

    lookUpBase64Alphabet(62) = '+'
    lookUpBase64Alphabet(63) = '/'
  }

  /**
   * Encodes hex octets into Base64
   *
   * @param binaryData    array containing binaryData
   * @param useLineBreaks whether to use line breaks between blocks (using "false" is non-standard)
   * @return Encoded Base64 array
   */
  def encode(binaryData: Array[Byte], useLineBreaks: Boolean): String = {

    if (binaryData == null)
      return null

    val lengthDataBits = binaryData.length * EIGHTBIT
    if (lengthDataBits == 0)
      return ""

    val fewerThan24bits = lengthDataBits % TWENTYFOURBITGROUP
    val numberTriplets = lengthDataBits / TWENTYFOURBITGROUP
    val numberQuartet =
      if (fewerThan24bits != 0)
        numberTriplets + 1
      else
        numberTriplets

    val numberLines = (numberQuartet - 1) / 19 + 1
    val encodedData = new Array[Char](numberQuartet * 4 + (if (useLineBreaks) numberLines - 1 else 0))

    var k: Byte = 0
    var l: Byte = 0

    var b1: Byte = 0
    var b2: Byte = 0
    var b3: Byte = 0

    var encodedIndex = 0
    var dataIndex = 0
    var i = 0

    if (fDebug)
      System.out.println("number of triplets = " + numberTriplets)

    for (_ <- 0 until numberLines - 1) {
      for (_ <- 0 until 19) {

        b1 = binaryData({dataIndex += 1; dataIndex - 1})
        b2 = binaryData({dataIndex += 1; dataIndex - 1})
        b3 = binaryData({dataIndex += 1; dataIndex - 1})

        if (fDebug)
          System.out.println("b1= " + b1 + ", b2= " + b2 + ", b3= " + b3)

        l = (b2 & 0x0f).toByte
        k = (b1 & 0x03).toByte

        val val1 =
          if ((b1 & SIGN) == 0)
            (b1 >> 2).toByte
          else
            (b1 >> 2 ^ 0xc0).toByte

        val val2 =
          if ((b2 & SIGN) == 0)
            (b2 >> 4).toByte
          else
            (b2 >> 4 ^ 0xf0).toByte

        val val3 =
          if ((b3 & SIGN) == 0)
            (b3 >> 6).toByte
          else
            (b3 >> 6 ^ 0xfc).toByte

        if (fDebug) {
          System.out.println("val2 = " + val2)
          System.out.println("k4   = " + (k << 4))
          System.out.println("vak  = " + (val2 | (k << 4)))
        }

        encodedData({encodedIndex += 1; encodedIndex - 1}) = lookUpBase64Alphabet(val1)
        encodedData({encodedIndex += 1; encodedIndex - 1}) = lookUpBase64Alphabet(val2 | (k << 4))
        encodedData({encodedIndex += 1; encodedIndex - 1}) = lookUpBase64Alphabet((l << 2) | val3)
        encodedData({encodedIndex += 1; encodedIndex - 1}) = lookUpBase64Alphabet(b3 & 0x3f)

        i += 1
      }
      if (useLineBreaks) encodedData({
        encodedIndex += 1; encodedIndex - 1
      }
      ) = 0xa
    }
    while (i < numberTriplets) {

      b1 = binaryData({dataIndex += 1; dataIndex - 1})
      b2 = binaryData({dataIndex += 1; dataIndex - 1})
      b3 = binaryData({dataIndex += 1; dataIndex - 1})

      if (fDebug)
        System.out.println("b1= " + b1 + ", b2= " + b2 + ", b3= " + b3)

      l = (b2 & 0x0f).toByte
      k = (b1 & 0x03).toByte

      val val1 =
        if ((b1 & SIGN) == 0)
          (b1 >> 2).toByte
        else
          (b1 >> 2 ^ 0xc0).toByte

      val val2 =
        if ((b2 & SIGN) == 0)
          (b2 >> 4).toByte
        else
          (b2 >> 4 ^ 0xf0).toByte

      val val3 =
        if ((b3 & SIGN) == 0)
          (b3 >> 6).toByte
        else
          (b3 >> 6 ^ 0xfc).toByte

      if (fDebug) {
        System.out.println("val2 = " + val2)
        System.out.println("k4   = " + (k << 4))
        System.out.println("vak  = " + (val2 | (k << 4)))
      }

      encodedData({encodedIndex += 1; encodedIndex - 1}) = lookUpBase64Alphabet(val1)
      encodedData({encodedIndex += 1; encodedIndex - 1}) = lookUpBase64Alphabet(val2 | (k << 4))
      encodedData({encodedIndex += 1; encodedIndex - 1}) = lookUpBase64Alphabet((l << 2) | val3)
      encodedData({encodedIndex += 1; encodedIndex - 1}) = lookUpBase64Alphabet(b3 & 0x3f)

      i += 1
    }
    // form integral number of 6-bit groups
    if (fewerThan24bits == EIGHTBIT) {
      b1 = binaryData(dataIndex)
      k = (b1 & 0x03).toByte
      if (fDebug) {
        System.out.println("b1=" + b1)
        System.out.println("b1<<2 = " + (b1 >> 2))
      }

      val val1 =
        if ((b1 & SIGN) == 0)
          (b1 >> 2).toByte
        else
          (b1 >> 2 ^ 0xc0).toByte

      encodedData({encodedIndex += 1; encodedIndex - 1}) = lookUpBase64Alphabet(val1)
      encodedData({encodedIndex += 1; encodedIndex - 1}) = lookUpBase64Alphabet(k << 4)
      encodedData({encodedIndex += 1; encodedIndex - 1}) = PAD
      encodedData({encodedIndex += 1; encodedIndex - 1}) = PAD
    } else if (fewerThan24bits == SIXTEENBIT) {

      b1 = binaryData(dataIndex)
      b2 = binaryData(dataIndex + 1)

      l = (b2 & 0x0f).toByte
      k = (b1 & 0x03).toByte

      val val1 =
        if ((b1 & SIGN) == 0)
          (b1 >> 2).toByte
        else
          (b1 >> 2 ^ 0xc0).toByte

      val val2 =
        if ((b2 & SIGN) == 0)
          (b2 >> 4).toByte
        else
          (b2 >> 4 ^ 0xf0).toByte

      encodedData({encodedIndex += 1; encodedIndex - 1}) = lookUpBase64Alphabet(val1)
      encodedData({encodedIndex += 1; encodedIndex - 1}) = lookUpBase64Alphabet(val2 | (k << 4))
      encodedData({encodedIndex += 1; encodedIndex - 1}) = lookUpBase64Alphabet(l << 2)
      encodedData({encodedIndex += 1; encodedIndex - 1}) = PAD
    }

    new String(encodedData)
  }

  /**
   * Decodes Base64 data into octets
   *
   * @param encoded String array containing Base64 data
   * @return Array containing decoded data.
   */
  def decode(encoded: String): Array[Byte] = {

    if (encoded eq null)
      return null

    val base64Data: Array[Char] = encoded.toCharArray

    val len = removeWhiteSpace(base64Data)
    if (len % FOURBYTE != 0)
      return null //should be divisible by four

    val numberQuadruple = len / FOURBYTE
    if (numberQuadruple == 0)
      return new Array[Byte](0)

    var b1: Byte = 0
    var b2: Byte = 0
    var b3: Byte = 0
    var b4: Byte = 0

    var d1: Char = 0
    var d2: Char = 0
    var d3: Char = 0
    var d4: Char = 0

    var i = 0
    var encodedIndex = 0
    var dataIndex = 0
    val decodedData = new Array[Byte](numberQuadruple * 3)

    while (i < numberQuadruple - 1) {
      if (
        ! isData({d1 = base64Data({dataIndex += 1; dataIndex - 1}); d1}) ||
        ! isData({d2 = base64Data({dataIndex += 1; dataIndex - 1}); d2}) ||
        ! isData({d3 = base64Data({dataIndex += 1; dataIndex - 1}); d3}) ||
        ! isData({d4 = base64Data({dataIndex += 1; dataIndex - 1}); d4})
      ) return null //if found "no data" just return null

      b1 = base64Alphabet(d1)
      b2 = base64Alphabet(d2)
      b3 = base64Alphabet(d3)
      b4 = base64Alphabet(d4)

      decodedData({encodedIndex += 1; encodedIndex - 1}) = (b1 << 2 | b2 >> 4).toByte
      decodedData({encodedIndex += 1; encodedIndex - 1}) = (((b2 & 0xf) << 4) | ((b3 >> 2) & 0xf)).toByte
      decodedData({encodedIndex += 1; encodedIndex - 1}) = (b3 << 6 | b4).toByte

      i += 1
    }

    if (
      ! isData({d1 = base64Data({dataIndex += 1; dataIndex - 1}); d1}) ||
      ! isData({d2 = base64Data({dataIndex += 1; dataIndex - 1}); d2})
    ) return null

    b1 = base64Alphabet(d1)
    b2 = base64Alphabet(d2)
    d3 = base64Data({dataIndex += 1; dataIndex - 1})
    d4 = base64Data({dataIndex += 1; dataIndex - 1})
    if (! isData((d3)) || ! isData((d4))) {
      //Check if they are PAD characters
      if (isPad(d3) && isPad(d4)) {
        //Two PAD e.g. 3c[Pad][Pad]
        if ((b2 & 0xf) != 0) //last 4 bits should be zero
          return null

        val tmp = new Array[Byte](i * 3 + 1)
        System.arraycopy(decodedData, 0, tmp, 0, i * 3)
        tmp(encodedIndex) = (b1 << 2 | b2 >> 4).toByte
        return tmp
      } else if (!isPad(d3) && isPad(d4)) {
        //One PAD  e.g. 3cQ[Pad]

        b3 = base64Alphabet(d3)
        if ((b3 & 0x3) != 0) //last 2 bits should be zero
          return null

        val tmp = new Array[Byte](i * 3 + 2)
        System.arraycopy(decodedData, 0, tmp, 0, i * 3)
        tmp({encodedIndex += 1; encodedIndex - 1}) = (b1 << 2 | b2 >> 4).toByte
        tmp(encodedIndex) = (((b2 & 0xf) << 4) | ((b3 >> 2) & 0xf)).toByte
        return tmp
      } else
        return null //an error  like "3c[Pad]r", "3cdX", "3cXd", "3cXX" where X is non data
    } else {
      //No PAD e.g 3cQl
      b3 = base64Alphabet(d3)
      b4 = base64Alphabet(d4)
      decodedData({encodedIndex += 1; encodedIndex - 1}) = (b1 << 2 | b2 >> 4).toByte
      decodedData({encodedIndex += 1; encodedIndex - 1}) = (((b2 & 0xf) << 4) | ((b3 >> 2) & 0xf)).toByte
      decodedData({encodedIndex += 1; encodedIndex - 1}) = (b3 << 6 | b4).toByte
    }
    decodedData
  }

  def isWhiteSpace(octet: Char): Boolean = octet == 0x20 || octet == 0xd || octet == 0xa || octet == 0x9

  private def isPad       (octet: Char): Boolean = octet == PAD
  private def isData      (octet: Char): Boolean = base64Alphabet(octet) != -1

  private def removeWhiteSpace(data: Array[Char]): Int = {

    if (data eq null)
      return 0

    var newSize = 0

    for (i <- data.indices)
      if (! isWhiteSpace(data(i)))
        data({newSize += 1; newSize - 1}) = data(i)

    newSize
  }
}