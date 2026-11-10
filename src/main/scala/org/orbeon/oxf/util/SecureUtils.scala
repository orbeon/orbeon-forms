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

import org.apache.commons.pool.BasePoolableObjectFactory
import org.log4s.Logger
import org.orbeon.io.CharsetNames
import org.orbeon.oxf.common.ConfigurationException
import org.orbeon.oxf.properties.Properties

import java.security.{GeneralSecurityException, MessageDigest, Provider, SecureRandom, Security}
import java.util.Base64 as JBase64
import javax.crypto.spec.{GCMParameterSpec, IvParameterSpec, PBEKeySpec, SecretKeySpec}
import javax.crypto.{Cipher, Mac, SecretKey, SecretKeyFactory}


object SecureUtils extends SecureUtilsTrait {

  sealed trait KeyUsage
  object KeyUsage {
    case object GeneralNoCheck          extends KeyUsage
    case object General                 extends KeyUsage
    case object Token                   extends KeyUsage
    case object McpToken                extends KeyUsage
    case object FieldEncryption         extends KeyUsage
    case object FieldEncryptionFallback extends KeyUsage
  }

  private implicit val logger: Logger = LoggerFactory.createLogger("org.orbeon.crypto")

  // Properties
  private val DeprecatedXFormsPasswordProperty        = "oxf.xforms.password"
  private val GeneralPasswordProperty                 = "oxf.crypto.password"
  private val TokenPasswordProperty                   = "oxf.fr.access-token.password"
  private val McpTokenPasswordProperty                = "oxf.fb.mcp.token.password"
  private val FieldEncryptionPasswordProperty         = "oxf.fr.field-encryption.password"
  private val FieldEncryptionFallbackPasswordProperty = "oxf.fr.field-encryption.password.read-fallback"

  private val CheckPasswordStrengthProperty           = "oxf.crypto.check-password-strength"
  private val KeyLengthProperty                       = "oxf.crypto.key-length"
  private val HashAlgorithmProperty                   = "oxf.crypto.hash-algorithm"
  private val PreferredProviderProperty               = "oxf.crypto.preferred-provider"

  private val RandomHexIdBits = 128
  private val RandomHexIdBytes = RandomHexIdBits / 8

  // Length of a value returned by `randomHexId` and other functions
  // 2023-04-11: Used for asserts only.
  val HexShortLength: Int = 40

  private val HexDigits = Array('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f')

  private val KeyCipherAlgorithm = "PBKDF2WithHmacSHA1"
  private val EncryptionCipherTransformation = "AES/CBC/PKCS5Padding"
  private val AesGcmCipherTransformation = "AES/GCM/NoPadding"
  private val DigestAlgorithm = "SHA-256"

  private val AESBlockSize = 128
  private val AESIVSize    = AESBlockSize / 8

  private val AesGcmIvSize        = 12
  private val AesGcmTagByteLength = 16
  private val AesGcmTagBitLength  = AesGcmTagByteLength * 8

  private def getPassword(keyUsage: KeyUsage): String = {

    val propertyNames = keyUsage match {
      case KeyUsage.GeneralNoCheck          => List(DeprecatedXFormsPasswordProperty, GeneralPasswordProperty)
      case KeyUsage.General                 => List(DeprecatedXFormsPasswordProperty, GeneralPasswordProperty)
      case KeyUsage.Token                   => List(TokenPasswordProperty)
      case KeyUsage.McpToken                => List(McpTokenPasswordProperty)
      case KeyUsage.FieldEncryption         => List(FieldEncryptionPasswordProperty)
      case KeyUsage.FieldEncryptionFallback => List(FieldEncryptionFallbackPasswordProperty)
    }

    val propertySet = Properties.instance.getPropertySet
    val propertyOpt =
      propertyNames
        .flatMap(n => propertySet.getPropertyOpt(n))
        .find(_.nonBlankStringValue.isDefined)

    (keyUsage, propertyOpt) match {
      case (KeyUsage.GeneralNoCheck, Some(property)) =>
        property.stringValue
      case (KeyUsage.GeneralNoCheck, None) =>
        randomHexId
      case (_, Some(property)) if ! checkPasswordStrengthProperty =>
        property.stringValue
      case (_, Some(property)) =>
        val isValid = property.associatedValue(_ => PasswordChecker.checkAndLog(property.name, property.stringValue))
        isValid match {
          case true  => property.stringValue
          case false => throw new ConfigurationException(s"Invalid password for property `${property.name}` property, see log for details")
        }
      case (_, None) =>
        throw new ConfigurationException(s"Missing password for property ${propertyNames.mkString("`", "`, `", "`")}")
    }
  }

  private lazy val secureRandom = new SecureRandom

  // Secret keys valid for the life of the classloader
  private val secretKeys: java.util.concurrent.ConcurrentHashMap[KeyUsage, SecretKey] =
    new java.util.concurrent.ConcurrentHashMap

  private def getOrComputeSecretKey(keyUsage: KeyUsage): SecretKey = {

    def computeSecretKey: SecretKey = {

      // Random seeded salt
      val salt = new Array[Byte](16)
      secureRandom.nextBytes(salt)

      val spec = new PBEKeySpec(getPassword(keyUsage).toCharArray, salt, 65536, getKeyLength)

      val factory = getSecretKeyFactory(KeyCipherAlgorithm)
      new SecretKeySpec(factory.generateSecret(spec).getEncoded, "AES")
    }

    secretKeys.computeIfAbsent(keyUsage, _ => computeSecretKey)
  }

  // 2023-04-12: Used by `FieldEncryption` only
  object AesGcm {

    private val aesGcmSecretKeys: java.util.concurrent.ConcurrentHashMap[String, SecretKey] =
      new java.util.concurrent.ConcurrentHashMap

    private def computeSecretKey(password: String): SecretKey = {
      val messageDigest = getMessageDigest(DigestAlgorithm)
      messageDigest.update(password.getBytes(CharsetNames.Utf8))
      val key256Bit = messageDigest.digest()
      // 128-bit key needed for backward compatibility with data encrypted using Tink `AesGcmJce`
      // Shortening is ok https://security.stackexchange.com/a/34797/49208
      val key128Bit = key256Bit.take(16)
      new SecretKeySpec(key128Bit, "AES")
    }

    private def getOrComputeSecretKey(keyUsage: KeyUsage): SecretKey =
      aesGcmSecretKeys.computeIfAbsent(getPassword(keyUsage), computeSecretKey)

    private val aesGcmPool = new SoftReferenceObjectPool(new BasePoolableObjectFactory[Cipher] {
      def makeObject(): Cipher = getCipher(AesGcmCipherTransformation)
    })

    private def withAesGcmCipher[T](body: Cipher => T): T = {
      val cipher = aesGcmPool.borrowObject()
      try body(cipher)
      finally aesGcmPool.returnObject(cipher)
    }

    private def encryptWithKey(plaintext: Array[Byte], secretKey: SecretKey): Array[Byte] = {
      val iv = new Array[Byte](AesGcmIvSize)
      secureRandom.nextBytes(iv)
      withAesGcmCipher { cipher =>
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(AesGcmTagBitLength, iv))
        iv ++ cipher.doFinal(plaintext)
      }
    }

    private def decryptWithKey(ciphertext: Array[Byte], secretKey: SecretKey): Array[Byte] = {
      if (ciphertext.length < AesGcmIvSize + AesGcmTagByteLength)
        throw new GeneralSecurityException("ciphertext too short")
      withAesGcmCipher { cipher =>
        cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(AesGcmTagBitLength, ciphertext, 0, AesGcmIvSize))
        cipher.doFinal(ciphertext, AesGcmIvSize, ciphertext.length - AesGcmIvSize)
      }
    }

    def encrypt(plaintext: Array[Byte], keyUsage: KeyUsage = KeyUsage.FieldEncryption): Array[Byte] =
      keyUsage match {
        case KeyUsage.GeneralNoCheck | KeyUsage.Token | KeyUsage.McpToken | KeyUsage.FieldEncryption =>
          encryptWithKey(plaintext, getOrComputeSecretKey(keyUsage))
        case KeyUsage.General | KeyUsage.FieldEncryptionFallback =>
          throw new IllegalArgumentException(s"Unsupported key usage for AES-GCM encryption: `$keyUsage`")
      }

    def decrypt(ciphertext: Array[Byte], keyUsage: KeyUsage = KeyUsage.FieldEncryption): Array[Byte] =
      keyUsage match {
        case KeyUsage.GeneralNoCheck | KeyUsage.Token | KeyUsage.McpToken =>
          decryptWithKey(ciphertext, getOrComputeSecretKey(keyUsage))
        case KeyUsage.FieldEncryption =>
          try {
            decryptWithKey(ciphertext, getOrComputeSecretKey(KeyUsage.FieldEncryption))
          } catch {
            case _: GeneralSecurityException if checkPasswordForKeyUsage(KeyUsage.FieldEncryptionFallback) =>
              decryptWithKey(ciphertext, getOrComputeSecretKey(KeyUsage.FieldEncryptionFallback))
          }
        case KeyUsage.General | KeyUsage.FieldEncryptionFallback =>
          throw new IllegalArgumentException(s"Unsupported key usage for AES-GCM decryption: `$keyUsage`")
      }

    def encrypt(plaintext : String, keyUsage: KeyUsage = KeyUsage.FieldEncryption): String =
      JBase64.getEncoder.encodeToString(encrypt(plaintext.getBytes(CharsetNames.Utf8), keyUsage))

    def decrypt(ciphertext: String, keyUsage: KeyUsage = KeyUsage.FieldEncryption): String =
      new String(decrypt(JBase64.getMimeDecoder.decode(ciphertext), keyUsage), CharsetNames.Utf8)

    private[util] def encryptWithPassword(password: String, plaintext: Array[Byte]): Array[Byte] =
      encryptWithKey(plaintext, aesGcmSecretKeys.computeIfAbsent(password, computeSecretKey))

    private[util] def decryptWithPassword(password: String, ciphertext: Array[Byte]): Array[Byte] =
      decryptWithKey(ciphertext, aesGcmSecretKeys.computeIfAbsent(password, computeSecretKey))

    private[util] def decryptWithPassword(password: String, ciphertext: String): String =
      new String(decryptWithPassword(password, JBase64.getMimeDecoder.decode(ciphertext)), CharsetNames.Utf8)
  }

  private def checkPasswordStrengthProperty: Boolean =
    Properties.instance.getPropertySet.getBoolean(CheckPasswordStrengthProperty, default = true)

  private def getKeyLength: Int =
    Properties.instance.getPropertySet.getInteger(KeyLengthProperty, 128)

  def getHashAlgorithm: String =
    Properties.instance.getPropertySet.getString(HashAlgorithmProperty, DigestAlgorithm)

  private def getPreferredProvider: Option[String] =
    Properties.instance.getPropertySet.getNonBlankString(PreferredProviderProperty)

  // See: https://github.com/orbeon/orbeon-forms/pull/1745
  private[oxf] def preferredProviderOpt(serviceType: String, algorithm: String): Option[Provider] = {
    val baseAlgorithm = algorithm.takeWhile(_ != '/')
    getPreferredProvider flatMap { preferredProvider =>
      Security.getProviders find { provider =>
        provider.getName == preferredProvider &&
          ((provider.getService(serviceType, algorithm) ne null) ||
           (provider.getService(serviceType, baseAlgorithm) ne null))
      }
    }
  }

  private def getCipher(transformation: String): Cipher =
    preferredProviderOpt("Cipher", transformation).fold(Cipher.getInstance(transformation))(Cipher.getInstance(transformation, _))

  private def getSecretKeyFactory(algorithm: String): SecretKeyFactory =
    preferredProviderOpt("SecretKeyFactory", algorithm).fold(SecretKeyFactory.getInstance(algorithm))(SecretKeyFactory.getInstance(algorithm, _))

  private def getMessageDigest(algorithm: String): MessageDigest =
    preferredProviderOpt("MessageDigest", algorithm).fold(MessageDigest.getInstance(algorithm))(MessageDigest.getInstance(algorithm, _))

  private def getMac(algorithm: String): Mac =
    preferredProviderOpt("Mac", algorithm).fold(Mac.getInstance(algorithm))(Mac.getInstance(algorithm, _))

  // Cipher is not thread-safe, see:
  // https://stackoverflow.com/questions/6957406/is-cipher-thread-safe
  private val pool = new SoftReferenceObjectPool(new BasePoolableObjectFactory[Cipher] {
    def makeObject(): Cipher = getCipher(EncryptionCipherTransformation)
  })

  private def withCipher[T](body: Cipher => T) = {
    val cipher = pool.borrowObject()
    try body(cipher)
    finally pool.returnObject(cipher)
  }

  def checkPasswordForKeyUsage(keyUsage: KeyUsage): Boolean =
    try {
      getPassword(keyUsage)
      true
    } catch {
      case _: ConfigurationException => false
    }

  // Encrypt a byte array
  // The result is converted to Base64 encoding without line breaks or spaces
  def encrypt(keyUsage: KeyUsage, bytes: Array[Byte]): String = encryptIV(keyUsage, bytes, None)

  // Public for tests
  def encryptIV(keyUsage: KeyUsage, bytes: Array[Byte], ivOption: Option[Array[Byte]]): String =
    withCipher { cipher =>
      ivOption match {
        case Some(iv) =>
          cipher.init(Cipher.ENCRYPT_MODE, getOrComputeSecretKey(keyUsage), new IvParameterSpec(iv))
          // Don't prepend IV
          Base64.encode(cipher.doFinal(bytes), useLineBreaks = false)
        case None =>
          cipher.init(Cipher.ENCRYPT_MODE, getOrComputeSecretKey(keyUsage))
          val params = cipher.getParameters
          val iv = params.getParameterSpec(classOf[IvParameterSpec]).getIV
          // Prepend the IV to the ciphertext
          Base64.encode(iv ++ cipher.doFinal(bytes), useLineBreaks = false)
      }
    }

  // Decrypt a Base64-encoded string into a byte array
  def decrypt(keyUsage: KeyUsage, text: String): Array[Byte] = decryptIV(keyUsage, text, None)

  private def decryptIV(keyUsage: KeyUsage, text: String, ivOption: Option[Array[Byte]]): Array[Byte] =
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

      cipher.init(Cipher.DECRYPT_MODE, getOrComputeSecretKey(keyUsage), new IvParameterSpec(iv))
      cipher.doFinal(message)
    }

  def digestStringJava(text: String, algorithm: String, encoding: String): String =
    digestString(text, algorithm, ByteEncoding.fromString(encoding))

  // Compute a digest
  def digestString(text: String, algorithm: String, encoding: ByteEncoding): String =
    digestBytes(text.getBytes(CharsetNames.Utf8), algorithm, encoding)

  // Compute a digest with the default algorithm
  def digestStringToHexShort(text: String): String =
    digestString(text, getHashAlgorithm, ByteEncoding.Hex).substring(0, HexShortLength)

  def digestBytes(bytes: Array[Byte], encoding: ByteEncoding): String =
    digestBytes(bytes, getHashAlgorithm, encoding)

  def digestBytes(bytes: Array[Byte], algorithm: String, encoding: ByteEncoding): String = {
    val messageDigest = getMessageDigest(algorithm)
    messageDigest.update(bytes)
    withEncoding(messageDigest.digest, encoding)
  }

  def digestStream(
    inputStream : java.io.InputStream,
    algorithm   : String = getHashAlgorithm
  ): String = {
    val messageDigest = getMessageDigest(algorithm)
    val buffer        = new Array[Byte](8192) // 8KB buffer

    Iterator
      .continually(inputStream.read(buffer))
      .takeWhile(_ != -1)
      .foreach(bytesRead => messageDigest.update(buffer, 0, bytesRead))

    withEncoding(messageDigest.digest, ByteEncoding.Hex)
  }

  def hmacStringWeakJava(text: String): String =
    hmacString(SecureUtils.KeyUsage.GeneralNoCheck, text, ByteEncoding.Hex)

  def hmacStringToHexShort(keyUsage: KeyUsage, text: String): String =
    hmacString(keyUsage, text, ByteEncoding.Hex).substring(0, HexShortLength)

  // Compute an HMAC with the default password and algorithm
  def hmacString(keyUsage: KeyUsage, text: String, encoding: ByteEncoding): String =
    hmacBytes(getPassword(keyUsage).getBytes(CharsetNames.Utf8), text.getBytes(CharsetNames.Utf8), getHashAlgorithm, encoding)

  // Compute an HMAC
  def hmacString(key: String, text: String, algorithm: String, encoding: ByteEncoding): String =
    hmacBytes(key.getBytes(CharsetNames.Utf8), text.getBytes(CharsetNames.Utf8), algorithm, encoding)

  private def hmacBytes(key: Array[Byte], bytes: Array[Byte], algorithm: String, encoding: ByteEncoding): String = {

    // See standard names:
    // https://docs.oracle.com/javase/6/docs/technotes/guides/security/StandardNames.html
    val fullAlgorithmName = "Hmac" + algorithm.toUpperCase.replace("-", "")

    val mac = getMac(fullAlgorithmName)
    mac.init(new SecretKeySpec(key, fullAlgorithmName))

    val digestBytes = mac.doFinal(bytes)
    val result = withEncoding(digestBytes, encoding)

    result.replace("\n", "")
  }

  private def withEncoding(bytes: Array[Byte], encoding: ByteEncoding) = encoding match {
    case ByteEncoding.Base64 => Base64.encode(bytes, useLineBreaks = false)
    case ByteEncoding.Hex    => byteArrayToHex(bytes)
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

  // Generate a random 128-bit value hashed to hex
  //@XPathFunction
  def randomHexId: String = {
    // It's unclear whether there is a real benefit to re-seed once in a while:
    // https://stackoverflow.com/questions/295628/securerandom-init-once-or-every-time-it-is-needed
    val bytes = new Array[Byte](RandomHexIdBytes)
    secureRandom.nextBytes(bytes)
    // We hash on top so that the actual random sequence won't be known if the id is made public
    digestBytes(bytes, ByteEncoding.Hex).substring(0, HexShortLength) // keep the same final length no matter what the hash algorithm is
  }

  // Get a new message digest with the default algorithm
  def defaultMessageDigest: MessageDigest =
    getMessageDigest(getHashAlgorithm)
}