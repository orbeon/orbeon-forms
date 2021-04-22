/**
 * Copyright (C) 2012 Orbeon, Inc.
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

import java.security.{MessageDigest, SecureRandom, Security}

import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.{IvParameterSpec, PBEKeySpec, SecretKeySpec}
import org.apache.commons.pool.BasePoolableObjectFactory
import org.orbeon.io.CharsetNames
import org.orbeon.oxf.properties.Properties
import org.orbeon.oxf.common.OXFException

object SecureUtils extends SecureUtilsTrait {

  // Properties
  private val XFormsPasswordProperty    = "oxf.xforms.password" // for backward compatibility
  private val PasswordProperty          = "oxf.crypto.password"
  private val KeyLengthProperty         = "oxf.crypto.key-length"
  private val HashAlgorithmProperty     = "oxf.crypto.hash-algorithm"
  private val PreferredProviderProperty = "oxf.crypto.preferred-provider"

  private def getPassword: String = {
    val propertySet = Properties.instance.getPropertySet
    propertySet.getNonBlankString(XFormsPasswordProperty) orElse
      propertySet.getNonBlankString(PasswordProperty) getOrElse
      (throw new OXFException(PasswordProperty + "property is not set"))
  }

  private def getKeyLength: Int =
    Properties.instance.getPropertySet.getInteger(KeyLengthProperty, 128)

  private def getHashAlgorithm: String =
    Properties.instance.getPropertySet.getString(HashAlgorithmProperty, "SHA1")

  private def getPreferredProvider: Option[String] =
    Properties.instance.getPropertySet.getNonBlankString(PreferredProviderProperty)

  private val HexDigits = Array('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f')

  // Modern algorithms as of 2012
  private val KeyCipherAlgorithm = "PBKDF2WithHmacSHA1"
  private val EncryptionCipherTransformation = "AES/CBC/PKCS5Padding"

  val AESBlockSize = 128
  val AESIVSize    = AESBlockSize / 8

  private lazy val secureRandom = new SecureRandom

  // Secret key valid for the life of the classloader
  private lazy val secretKey: SecretKey = {

    // Random seeded salt
    val salt = new Array[Byte](8)
    secureRandom.nextBytes(salt)

    val spec = new PBEKeySpec(getPassword.toCharArray, salt, 65536, getKeyLength)

    val factory = SecretKeyFactory.getInstance(KeyCipherAlgorithm)
    new SecretKeySpec(factory.generateSecret(spec).getEncoded, "AES")
  }

  // See: https://github.com/orbeon/orbeon-forms/pull/1745
  private lazy val preferredProviderOpt =
    getPreferredProvider flatMap { preferredProvider =>
      Security.getProviders find (_.getName == preferredProvider)
    }

  // Cipher is not thread-safe, see:
  // http://stackoverflow.com/questions/6957406/is-cipher-thread-safe
  private val pool = new SoftReferenceObjectPool(new BasePoolableObjectFactory[Cipher] {
    def makeObject() = preferredProviderOpt match {
      case Some(preferred) => Cipher.getInstance(EncryptionCipherTransformation, preferred)
      case None            => Cipher.getInstance(EncryptionCipherTransformation)
    }
  })

  private def withCipher[T](body: Cipher => T) = {
    val cipher = pool.borrowObject()
    try body(cipher)
    finally pool.returnObject(cipher)
  }

  // Encrypt a byte array
  // The result is converted to Base64 encoding without line breaks or spaces
  def encrypt(bytes: Array[Byte]): String = encryptIV(bytes, None)

  def encryptIV(bytes: Array[Byte], ivOption: Option[Array[Byte]]): String =
    withCipher { cipher =>
      ivOption match {
        case Some(iv) =>
          cipher.init(Cipher.ENCRYPT_MODE, secretKey, new IvParameterSpec(iv))
          // Don't prepend IV
          Base64.encode(cipher.doFinal(bytes), false)
        case None =>
          cipher.init(Cipher.ENCRYPT_MODE, secretKey)
          val params = cipher.getParameters
          val iv = params.getParameterSpec(classOf[IvParameterSpec]).getIV
          // Prepend the IV to the ciphertext
          Base64.encode(iv ++ cipher.doFinal(bytes), false)
      }
    }

  // Decrypt a Base64-encoded string into a byte array
  def decrypt(text: String): Array[Byte] = decryptIV(text, None)

  def decryptIV(text: String, ivOption: Option[Array[Byte]]): Array[Byte] =
    withCipher { cipher =>
      val (iv, message) =
        ivOption match {
          case Some(iv) =>
            // The IV was passed
            (iv, Base64.decode(text))
          case None =>
            // The IV was prepended to the message
            Base64.decode(text).splitAt(AESIVSize)
        }

      cipher.init(Cipher.DECRYPT_MODE, secretKey, new IvParameterSpec(iv))
      cipher.doFinal(message)
    }

  // Compute a digest
  def digestString(text: String, algorithm: String, encoding: String): String =
    digestBytes(text.getBytes(CharsetNames.Utf8), algorithm, encoding)

  // Compute a digest with the default algorithm
  def digestString(text: String, encoding: String): String =
    digestString(text, getHashAlgorithm, encoding)

  def digestBytes(bytes: Array[Byte], encoding: String): String =
    digestBytes(bytes, getHashAlgorithm, encoding)

  def digestBytes(bytes: Array[Byte], algorithm: String, encoding: String): String = {
    val messageDigest = MessageDigest.getInstance(algorithm)
    messageDigest.update(bytes)
    withEncoding(messageDigest.digest, encoding)
  }

  // Compute an HMAC with the default password and algorithm
  def hmacString(text: String, encoding: String): String =
    hmacBytes(getPassword.getBytes(CharsetNames.Utf8), text.getBytes(CharsetNames.Utf8), getHashAlgorithm, encoding)

  // Compute an HMAC
  def hmacString(key: String, text: String, algorithm: String, encoding: String): String =
    hmacBytes(key.getBytes(CharsetNames.Utf8), text.getBytes(CharsetNames.Utf8), algorithm, encoding)

  def hmacBytes(key: Array[Byte], bytes: Array[Byte], algorithm: String, encoding: String): String = {

    // See standard names:
    // http://docs.oracle.com/javase/6/docs/technotes/guides/security/StandardNames.html
    val fullAlgorithmName = "Hmac" + algorithm.toUpperCase.replace("-", "")

    val mac = Mac.getInstance(fullAlgorithmName)
    mac.init(new SecretKeySpec(key, fullAlgorithmName))

    val digestBytes = mac.doFinal(bytes)
    val result = withEncoding(digestBytes, encoding)

    result.replace("\n", "")
  }

  private def withEncoding(bytes: Array[Byte], encoding: String) = encoding match {
    case "base64" => Base64.encode(bytes, false)
    case "hex"    => byteArrayToHex(bytes)
    case _        => throw new IllegalArgumentException(s"Invalid digest encoding (must be one of `base64` or `hex`): `$encoding`")
  }

  // Convert to a lowercase hexadecimal value
  def byteArrayToHex(bytes: Array[Byte]): String = {
    val sb = new StringBuilder(bytes.length * 2)

    var i: Int = 0
    while (i < bytes.length) {
      sb.append(HexDigits((bytes(i) >> 4) & 0xf))
      sb.append(HexDigits(bytes(i) & 0xf))
      i += 1
    }

    sb.toString
  }

  // Length of a value returned by randomHexId
  lazy val HexIdLength = randomHexId.size

  // Generate a random 128-bit value hashed to hex
  //@XPathFunction
  def randomHexId: String = {
    // It's unclear whether there is a real benefit to re-seed once in a while:
    // http://stackoverflow.com/questions/295628/securerandom-init-once-or-every-time-it-is-needed
    val bytes = new Array[Byte](16)
    secureRandom.nextBytes(bytes)
    // We hash on top so that the actual random sequence won't be known if the id is made public
    digestBytes(bytes, "hex")
  }

  // Get a new message digest with the default algorithm
  def defaultMessageDigest: MessageDigest =
    MessageDigest.getInstance(getHashAlgorithm)
}