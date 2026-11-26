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
  private val FipsModeProperty                        = "oxf.crypto.fips-mode"
  private val KdfIterationsProperty                   = "oxf.crypto.kdf.iterations"

  private val DefaultKdfIterations                    = 310000
  private val LegacyKdfIterations                     = 65536
  private val FipsMinPasswordBytes                    = 15
  private val DefaultNonFipsProviderName              = "SunJCE"
  private val BcFipsProviderName                      = "BCFIPS"

  private val RandomHexIdBits  = 128
  private val RandomHexIdBytes = RandomHexIdBits / 8

  // Length of a value returned by `randomHexId` and other functions
  // 2023-04-11: Used for asserts only.
  val HexShortLength: Int = 40

  private val HexDigits = Array('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f')

  private val LegacyKeyCipherAlgorithm       = "PBKDF2WithHmacSHA1"
  private val FipsKeyCipherAlgorithm         = "PBKDF2WithHmacSHA256"
  private val EncryptionCipherTransformation = "AES/CBC/PKCS5Padding"
  private val AesGcmCipherTransformation     = "AES/GCM/NoPadding"
  private val DigestAlgorithm                = "SHA-256"

  private val AESBlockSize = 128
  private val AESIVSize    = AESBlockSize / 8

  private val AesGcmV1Magic0: Byte     = 0x4F // 'O'
  private val AesGcmV1Magic1: Byte     = 0x46 // 'F'
  private val AesGcmV1Magic2: Byte     = 0x00
  private val AesGcmV1Magic3: Byte     = 0x01
  private val AesGcmV1HeaderSize       = 8    // 4-byte magic/version + 4-byte big-endian iterations
  private val MinKdfIterations         = 1000
  private val MaxKdfIterations         = 10000000
  private val AesGcmSaltSize           = 16
  private val AesGcmIvSize             = 12
  private val AesGcmTagByteLength      = 16
  private val AesGcmTagBitLength       = AesGcmTagByteLength * 8
  private val AesGcmV1KeyBitLength     = 256
  private val AesGcmV1MinLength        = AesGcmV1HeaderSize + AesGcmSaltSize + AesGcmIvSize + AesGcmTagByteLength

  private def validatePasswordForFips(propertyName: String, password: String): String = {
    if (isFipsMode && password.getBytes(CharsetNames.Utf8).length < FipsMinPasswordBytes)
      throw new ConfigurationException(
        s"Password for property `$propertyName` must be at least $FipsMinPasswordBytes bytes in FIPS mode"
      )
    password
  }

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
        validatePasswordForFips(property.name, property.stringValue)
      case (KeyUsage.GeneralNoCheck, None) =>
        defaultGeneralNoCheckPassword
      case (_, Some(property)) if ! checkPasswordStrengthProperty =>
        validatePasswordForFips(property.name, property.stringValue)
      case (_, Some(property)) =>
        val isValid = property.associatedValue(_ => PasswordChecker.checkAndLog(property.name, property.stringValue))
        isValid match {
          case true  => validatePasswordForFips(property.name, property.stringValue)
          case false => throw new ConfigurationException(s"Invalid password for property `${property.name}` property, see log for details")
        }
      case (_, None) =>
        throw new ConfigurationException(s"Missing password for property ${propertyNames.mkString("`", "`, `", "`")}")
    }
  }

  private lazy val secureRandom = new SecureRandom
  private lazy val defaultGeneralNoCheckPassword: String = randomHexId

  // Ephemeral secret keys cached by `(KeyUsage, fipsMode)` and `(password, iterations)` so dynamic property updates take effect
  private val secretKeys: java.util.concurrent.ConcurrentHashMap[(KeyUsage, Boolean), (String, Int, SecretKey)] =
    new java.util.concurrent.ConcurrentHashMap

  private def getOrComputeSecretKey(keyUsage: KeyUsage, fipsMode: Boolean): SecretKey = {
    val password   = getPassword(keyUsage)
    val iterations = if (fipsMode) getKdfIterations else LegacyKdfIterations

    def computeSecretKey(): SecretKey = {
      // Random seeded salt (ephemeral per-JVM key)
      val salt = new Array[Byte](AesGcmSaltSize)
      secureRandom.nextBytes(salt)

      val keyAlgorithm = if (fipsMode) FipsKeyCipherAlgorithm else LegacyKeyCipherAlgorithm
      val keyBitLength = if (fipsMode) AesGcmV1KeyBitLength else getKeyLength
      val spec         = new PBEKeySpec(password.toCharArray, salt, iterations, keyBitLength)

      val factory = getSecretKeyFactory(keyAlgorithm)
      try new SecretKeySpec(factory.generateSecret(spec).getEncoded, "AES")
      finally spec.clearPassword()
    }

    secretKeys.compute(
      (keyUsage, fipsMode),
      (_, existing) =>
        if ((existing ne null) && existing._1 == password && existing._2 == iterations)
          existing
        else
          (password, iterations, computeSecretKey())
    )._3
  }

  private val aesGcmPool = new SoftReferenceObjectPool(new BasePoolableObjectFactory[Cipher] {
    def makeObject(): Cipher = getCipher(AesGcmCipherTransformation)
  })

  private def withAesGcmCipher[T](body: Cipher => T): T = {
    val cipher = aesGcmPool.borrowObject()
    try body(cipher)
    finally aesGcmPool.returnObject(cipher)
  }

  // 2023-04-12: Used by `FieldEncryption` only
  object AesGcm {

    private val v0SecretKeys: java.util.concurrent.ConcurrentHashMap[String, SecretKey] =
      new java.util.concurrent.ConcurrentHashMap

    private final case class Pbkdf2CacheKey(password: String, salt: Vector[Byte], iterations: Int)

    private val Pbkdf2KeyCacheMaxSize = 256
    private val pbkdf2KeyCache: java.util.LinkedHashMap[Pbkdf2CacheKey, SecretKey] =
      new java.util.LinkedHashMap[Pbkdf2CacheKey, SecretKey](Pbkdf2KeyCacheMaxSize + 1, 0.75f, true) {
        override def removeEldestEntry(eldest: java.util.Map.Entry[Pbkdf2CacheKey, SecretKey]): Boolean =
          size() > Pbkdf2KeyCacheMaxSize
      }

    private val SaltRotationIntervalMs = 3600L * 1000L // 1 hour
    @volatile private var currentSaltAndTimestamp: (Array[Byte], Long) = null

    private def currentEncryptionSalt(): Array[Byte] = {
      val now      = System.currentTimeMillis()
      val existing = currentSaltAndTimestamp
      if ((existing ne null) && (now - existing._2) < SaltRotationIntervalMs) {
        existing._1
      } else {
        val newSalt = new Array[Byte](AesGcmSaltSize)
        secureRandom.nextBytes(newSalt)
        currentSaltAndTimestamp = (newSalt, now)
        newSalt
      }
    }

    private def computeV0SecretKey(password: String): SecretKey = {
      val messageDigest = getMessageDigest(DigestAlgorithm)
      messageDigest.update(password.getBytes(CharsetNames.Utf8))
      val key256Bit = messageDigest.digest()
      // 128-bit key needed for backward compatibility with data encrypted using Tink `AesGcmJce`
      // Shortening is ok https://security.stackexchange.com/a/34797/49208
      val key128Bit = key256Bit.take(16)
      new SecretKeySpec(key128Bit, "AES")
    }

    private def getOrComputeV0SecretKey(password: String): SecretKey =
      v0SecretKeys.computeIfAbsent(password, computeV0SecretKey)

    private def getOrComputeV1SecretKey(password: String, salt: Array[Byte], iterations: Int): SecretKey = {
      val cacheKey  = Pbkdf2CacheKey(password, salt.toVector, iterations)
      val cachedOpt = pbkdf2KeyCache.synchronized {
        Option(pbkdf2KeyCache.get(cacheKey))
      }
      cachedOpt.getOrElse {
        val spec    = new PBEKeySpec(password.toCharArray, salt, iterations, AesGcmV1KeyBitLength)
        val factory = getSecretKeyFactory(FipsKeyCipherAlgorithm)
        val secretKey =
          try new SecretKeySpec(factory.generateSecret(spec).getEncoded, "AES")
          finally spec.clearPassword()
        pbkdf2KeyCache.synchronized {
          pbkdf2KeyCache.put(cacheKey, secretKey)
        }
        secretKey
      }
    }

    private[oxf] def extractV1IterationsOpt(ciphertext: Array[Byte]): Option[Int] =
      if (
        ciphertext.length >= AesGcmV1MinLength &&
        ciphertext(0) == AesGcmV1Magic0        &&
        ciphertext(1) == AesGcmV1Magic1        &&
        ciphertext(2) == AesGcmV1Magic2        &&
        ciphertext(3) == AesGcmV1Magic3
      ) {
        val iter =
          ((ciphertext(4) & 0xff) << 24) |
          ((ciphertext(5) & 0xff) << 16) |
          ((ciphertext(6) & 0xff) << 8)  |
          (ciphertext(7)  & 0xff)
        if (iter >= MinKdfIterations && iter <= MaxKdfIterations)
          Some(iter)
        else
          None
      } else {
        None
      }

    private def encryptV0WithPassword(password: String, plaintext: Array[Byte]): Array[Byte] = {
      val iv = new Array[Byte](AesGcmIvSize)
      secureRandom.nextBytes(iv)
      val secretKey = getOrComputeV0SecretKey(password)
      withAesGcmCipher { cipher =>
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(AesGcmTagBitLength, iv))
        iv ++ cipher.doFinal(plaintext)
      }
    }

    private[oxf] def encryptV1WithPassword(
      password  : String,
      plaintext : Array[Byte],
      iterations: Int,
      saltOpt   : Option[Array[Byte]] = None,
      aadOpt    : Option[Array[Byte]] = None
    ): Array[Byte] = {
      if (iterations < MinKdfIterations || iterations > MaxKdfIterations)
        throw new ConfigurationException(
          s"Property `$KdfIterationsProperty` must be between $MinKdfIterations and $MaxKdfIterations (got $iterations)"
        )
      val salt      = saltOpt.getOrElse(currentEncryptionSalt())
      val iv        = new Array[Byte](AesGcmIvSize)
      secureRandom.nextBytes(iv)
      val secretKey = getOrComputeV1SecretKey(password, salt, iterations)
      withAesGcmCipher { cipher =>
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(AesGcmTagBitLength, iv))
        aadOpt.foreach(cipher.updateAAD)
        val encrypted = cipher.doFinal(plaintext)
        val out       = new Array[Byte](AesGcmV1HeaderSize + AesGcmSaltSize + AesGcmIvSize + encrypted.length)
        out(0) = AesGcmV1Magic0
        out(1) = AesGcmV1Magic1
        out(2) = AesGcmV1Magic2
        out(3) = AesGcmV1Magic3
        out(4) = ((iterations >>> 24) & 0xff).toByte
        out(5) = ((iterations >>> 16) & 0xff).toByte
        out(6) = ((iterations >>> 8)  & 0xff).toByte
        out(7) = (iterations          & 0xff).toByte
        System.arraycopy(salt,      0, out, AesGcmV1HeaderSize,                                AesGcmSaltSize)
        System.arraycopy(iv,        0, out, AesGcmV1HeaderSize + AesGcmSaltSize,               AesGcmIvSize)
        System.arraycopy(encrypted, 0, out, AesGcmV1HeaderSize + AesGcmSaltSize + AesGcmIvSize, encrypted.length)
        out
      }
    }

    private def decryptV0WithPassword(password: String, ciphertext: Array[Byte]): Array[Byte] = {
      if (ciphertext.length < AesGcmIvSize + AesGcmTagByteLength)
        throw new GeneralSecurityException("ciphertext too short")
      val secretKey = getOrComputeV0SecretKey(password)
      withAesGcmCipher { cipher =>
        cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(AesGcmTagBitLength, ciphertext, 0, AesGcmIvSize))
        cipher.doFinal(ciphertext, AesGcmIvSize, ciphertext.length - AesGcmIvSize)
      }
    }

    private[oxf] def decryptV1WithPassword(
      password  : String,
      ciphertext: Array[Byte],
      iterations: Int,
      aadOpt    : Option[Array[Byte]] = None
    ): Array[Byte] = {
      val salt       = java.util.Arrays.copyOfRange(ciphertext, AesGcmV1HeaderSize, AesGcmV1HeaderSize + AesGcmSaltSize)
      val ivOffset   = AesGcmV1HeaderSize + AesGcmSaltSize
      val dataOffset = ivOffset + AesGcmIvSize
      val secretKey  = getOrComputeV1SecretKey(password, salt, iterations)
      withAesGcmCipher { cipher =>
        cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(AesGcmTagBitLength, ciphertext, ivOffset, AesGcmIvSize))
        aadOpt.foreach(cipher.updateAAD)
        cipher.doFinal(ciphertext, dataOffset, ciphertext.length - dataOffset)
      }
    }

    private def decodeBase64(ciphertext: String): Array[Byte] =
      JBase64.getDecoder.decode(ciphertext.filterNot(_.isWhitespace))

    def encrypt(plaintext: Array[Byte]): Array[Byte] =
      encrypt(plaintext, KeyUsage.FieldEncryption)

    def encrypt(plaintext: Array[Byte], keyUsage: KeyUsage): Array[Byte] =
      keyUsage match {
        case KeyUsage.GeneralNoCheck | KeyUsage.Token | KeyUsage.McpToken | KeyUsage.FieldEncryption =>
          // Starting with 2026.1, always write the self-describing Version 1 envelope by default
          encryptV1WithPassword(getPassword(keyUsage), plaintext, iterations = getKdfIterations)
        case KeyUsage.General | KeyUsage.FieldEncryptionFallback =>
          throw new IllegalArgumentException(s"Unsupported key usage for AES-GCM encryption: `$keyUsage`")
      }

    def decrypt(ciphertext: Array[Byte]): Array[Byte] =
      decrypt(ciphertext, KeyUsage.FieldEncryption)

    def decrypt(ciphertext: Array[Byte], keyUsage: KeyUsage): Array[Byte] =
      keyUsage match {
        case KeyUsage.GeneralNoCheck | KeyUsage.Token | KeyUsage.McpToken =>
          decryptWithPassword(getPassword(keyUsage), ciphertext)
        case KeyUsage.FieldEncryption =>
          val primaryPassword     = getPassword(KeyUsage.FieldEncryption)
          val fallbackPasswordOpt =
            if (checkPasswordForKeyUsage(KeyUsage.FieldEncryptionFallback))
              Some(getPassword(KeyUsage.FieldEncryptionFallback))
            else
              None

          def tryPasswords(decryptFn: String => Array[Byte]): Array[Byte] =
            try {
              decryptFn(primaryPassword)
            } catch {
              case e: GeneralSecurityException =>
                fallbackPasswordOpt match {
                  case Some(fallbackPassword) => decryptFn(fallbackPassword)
                  case None                   => throw e
                }
            }

          extractV1IterationsOpt(ciphertext) match {
            case Some(headerIterations) =>
              try {
                tryPasswords(pwd => decryptV1WithPassword(pwd, ciphertext, headerIterations))
              } catch {
                case _: GeneralSecurityException =>
                  // Safeguard for the < 2^-40 case where a legacy Version 0 random IV starts with `"OF\0\1"` + valid int
                  tryPasswords(pwd => decryptV0WithPassword(pwd, ciphertext))
              }
            case None =>
              tryPasswords(pwd => decryptV0WithPassword(pwd, ciphertext))
          }
        case KeyUsage.General | KeyUsage.FieldEncryptionFallback =>
          throw new IllegalArgumentException(s"Unsupported key usage for AES-GCM decryption: `$keyUsage`")
      }

    def encrypt(plaintext: String): String =
      encrypt(plaintext, KeyUsage.FieldEncryption)

    def encrypt(plaintext: String, keyUsage: KeyUsage): String =
      JBase64.getEncoder.encodeToString(encrypt(plaintext.getBytes(CharsetNames.Utf8), keyUsage))

    def decrypt(ciphertext: String): String =
      decrypt(ciphertext, KeyUsage.FieldEncryption)

    def decrypt(ciphertext: String, keyUsage: KeyUsage): String =
      new String(decrypt(decodeBase64(ciphertext), keyUsage), CharsetNames.Utf8)

    private[oxf] def encryptWithPassword(
      password   : String,
      plaintext  : Array[Byte],
      useVersion1: Boolean             = true,
      iterations : Int                 = getKdfIterations,
      saltOpt    : Option[Array[Byte]] = None,
      aadOpt     : Option[Array[Byte]] = None
    ): Array[Byte] =
      if (useVersion1)
        encryptV1WithPassword(password, plaintext, iterations, saltOpt, aadOpt)
      else {
        require(aadOpt.isEmpty, "Version 0 AES-GCM does not support AAD")
        encryptV0WithPassword(password, plaintext)
      }

    private[oxf] def decryptWithPassword(
      password  : String,
      ciphertext: Array[Byte],
      aadOpt    : Option[Array[Byte]] = None
    ): Array[Byte] =
      extractV1IterationsOpt(ciphertext) match {
        case Some(headerIterations) =>
          try {
            decryptV1WithPassword(password, ciphertext, headerIterations, aadOpt)
          } catch {
            case _: GeneralSecurityException if aadOpt.isEmpty =>
              decryptV0WithPassword(password, ciphertext)
            case e: GeneralSecurityException =>
              throw e
          }
        case None if aadOpt.isEmpty =>
          decryptV0WithPassword(password, ciphertext)
        case None =>
          throw new GeneralSecurityException("Expected Version 1 AES-GCM header when AAD is provided")
      }

    private[oxf] def decryptWithPassword(password: String, ciphertext: String): String =
      new String(decryptWithPassword(password, decodeBase64(ciphertext)), CharsetNames.Utf8)
    private[util] def cachedPbkdf2KeyCount: Int =
      pbkdf2KeyCache.synchronized(pbkdf2KeyCache.size())
  }

  def isFipsMode: Boolean =
    Properties.instance.getPropertySet.getBoolean(FipsModeProperty, default = false)

  def getKdfIterations: Int =
    Properties.instance.getPropertySet.getInteger(KdfIterationsProperty, DefaultKdfIterations)

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

    def supportsService(provider: Provider): Boolean =
      (provider.getService(serviceType, algorithm) ne null) ||
        (provider.getService(serviceType, baseAlgorithm) ne null)

    val effectiveProviderNameOpt =
      getPreferredProvider match {
        case Some(DefaultNonFipsProviderName) if isFipsMode =>
          // In FIPS mode, do not force the legacy `SunJCE` default over a registered `BCFIPS` / provider #1
          Some(BcFipsProviderName)
        case other =>
          other
      }

    effectiveProviderNameOpt flatMap { preferredProvider =>
      Security.getProviders find { provider =>
        provider.getName == preferredProvider && supportsService(provider)
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
    if (isFipsMode) {
      val secretKey = getOrComputeSecretKey(keyUsage, fipsMode = true)
      withAesGcmCipher { cipher =>
        ivOption match {
          case Some(iv) =>
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(AesGcmTagBitLength, iv))
            // Don't prepend IV
            Base64.encode(cipher.doFinal(bytes), useLineBreaks = false)
          case None =>
            val iv = new Array[Byte](AesGcmIvSize)
            secureRandom.nextBytes(iv)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(AesGcmTagBitLength, iv))
            // Prepend the IV to the ciphertext
            Base64.encode(iv ++ cipher.doFinal(bytes), useLineBreaks = false)
        }
      }
    } else {
      withCipher { cipher =>
        ivOption match {
          case Some(iv) =>
            cipher.init(Cipher.ENCRYPT_MODE, getOrComputeSecretKey(keyUsage, fipsMode = false), new IvParameterSpec(iv))
            // Don't prepend IV
            Base64.encode(cipher.doFinal(bytes), useLineBreaks = false)
          case None =>
            cipher.init(Cipher.ENCRYPT_MODE, getOrComputeSecretKey(keyUsage, fipsMode = false))
            val params = cipher.getParameters
            val iv = params.getParameterSpec(classOf[IvParameterSpec]).getIV
            // Prepend the IV to the ciphertext
            Base64.encode(iv ++ cipher.doFinal(bytes), useLineBreaks = false)
        }
      }
    }

  // Decrypt a Base64-encoded string into a byte array
  def decrypt(keyUsage: KeyUsage, text: String): Array[Byte] = decryptIV(keyUsage, text, None)

  private def decryptIV(keyUsage: KeyUsage, text: String, ivOption: Option[Array[Byte]]): Array[Byte] =
    if (isFipsMode) {
      val secretKey = getOrComputeSecretKey(keyUsage, fipsMode = true)
      withAesGcmCipher { cipher =>
        val (iv, message) =
          ivOption match {
            case Some(iv) =>
              (iv, Base64.decode(text))
            case None =>
              Base64.decode(text).splitAt(AesGcmIvSize)
          }
        cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(AesGcmTagBitLength, iv))
        cipher.doFinal(message)
      }
    } else {
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

        cipher.init(Cipher.DECRYPT_MODE, getOrComputeSecretKey(keyUsage, fipsMode = false), new IvParameterSpec(iv))
        cipher.doFinal(message)
      }
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
