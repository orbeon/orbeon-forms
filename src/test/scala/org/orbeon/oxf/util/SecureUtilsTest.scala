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

import org.orbeon.oxf.test.ResourceManagerSupport
import org.scalatest.funspec.AnyFunSpecLike

import java.security.SecureRandom
import scala.collection.parallel.CollectionConverters.*
import scala.util.{Failure, Success, Try}


// NOTE: hmac is tested via XFormsUploadControlTest
class SecureUtilsTest
  extends ResourceManagerSupport
     with AnyFunSpecLike {

  private val Sizes = List(0, 1, 10, 16, 100, 128, 256, 1000, 1024, 10000, 16384, 100000)

  describe("Encrypt and decrypt") {

    def asserts(size: Int) = {

      val bytes = randomBytes(size)

      // Decrypted value is the same as the original
      assert(bytes sameElements SecureUtils.decrypt(SecureUtils.KeyUsage.General, SecureUtils.encrypt(SecureUtils.KeyUsage.General, bytes)))

      // Encrypting the same value twice doesn't yield the same result
      assert(SecureUtils.encrypt(SecureUtils.KeyUsage.General, bytes) != SecureUtils.encrypt(SecureUtils.KeyUsage.General, bytes))

      // Unless we provide an IV
      val iv = randomBytes(16)
      assert(SecureUtils.encryptIV(SecureUtils.KeyUsage.General, bytes, Some(iv)) == SecureUtils.encryptIV(SecureUtils.KeyUsage.General, bytes, Some(iv)))
    }

    // Try multiple data sizes
    it("must pass serially") {
      for (size <- Sizes)
        asserts(size)
    }

    it("must pass in parallel") {
      for (size <- Sizes.par)
        asserts(size)
    }
  }

  describe("Encryption with different passwords") {

    val bytes = randomBytes(100)

    val encrypted1 = SecureUtils.encrypt(SecureUtils.KeyUsage.General, bytes)
    val encrypted2 = SecureUtils.encrypt(SecureUtils.KeyUsage.Token, bytes)

    val decrypted1 = SecureUtils.decrypt(SecureUtils.KeyUsage.General, encrypted1)
    val decrypted2 = SecureUtils.decrypt(SecureUtils.KeyUsage.Token, encrypted2)

    it("must encrypt/decrypt with different passwords") {
      assert(encrypted1 != encrypted2)
      assert(decrypted1 sameElements decrypted2)
    }

    it("must fail or produce different bytes upon decrypting with incorrect password") {
      Try(SecureUtils.decrypt(SecureUtils.KeyUsage.General, encrypted2)) match {
        case Success(decryptedWithWrongKey) => assert(! decryptedWithWrongKey.sameElements(bytes))
        case Failure(_)                     => // Failure is also acceptable
      }
    }
  }

  describe("Convert to hexadecimal") {
    val bytes = randomBytes(100)
    it("must convert") {
      assert(bytes.map("%02X" format _).mkString.toLowerCase == SecureUtils.byteArrayToHex(bytes))
    }
  }

  describe("Digest") {

    val Algorithms = List("SHA1", "MD5")
    val Encodings  = List(ByteEncoding.Hex, ByteEncoding.Base64)

    def asserts(algorithm: String, encoding: ByteEncoding, size: Int) = {

      val bytes = randomBytes(size)

      // Same digest
      assert(SecureUtils.digestBytes(bytes, algorithm, encoding) == SecureUtils.digestBytes(bytes, algorithm, encoding))

      // Digest different if data changes
      assert(SecureUtils.digestBytes(bytes :+ 42.asInstanceOf[Byte], algorithm, encoding) != SecureUtils.digestBytes(bytes, algorithm, encoding))
    }

    // Try multiple algorithms, encodings, and data sizes
    for {
      algorithm <- Algorithms
      encoding  <- Encodings
    } locally {
      it(s"must pass for $algorithm/$encoding") {
        for (size <- Sizes)
          asserts(algorithm, encoding, size)
      }
    }

    it(s"must pass in parallel for all") {
      for {
        algorithm <- Algorithms.par
        encoding  <- Encodings
        size      <- Sizes
      } locally {
        asserts(algorithm, encoding, size)
      }
    }

    // Check that for a given algorithm/encoding, the digest has the same size for any size of input data
    def assertSameSize(algorithm: String, encoding: ByteEncoding): Unit =
      Sizes
        .map(randomBytes)
        .map(SecureUtils.digestBytes(_, algorithm, encoding))
        .map(_.size)
        .sliding(2)
        .foreach {
          case Seq(s1, s2) => assert(s1 == s2)
          case _           => throw new IllegalStateException
        }

    it(s"must have the same size in parallel for all") {
      for {
        algorithm <- Algorithms.par
        encoding  <- Encodings
      } locally {
        assertSameSize(algorithm, encoding)
      }
    }
  }

  describe("AES-GCM (Tink-compatible and FIPS PBKDF2-SHA256 field encryption)") {

    val TestPassword = "ForTestsOnly-1!T42mMw*pgH9.EfTZx!4r"

    def withProperties[T](url: String)(body: => T): T = {
      org.orbeon.oxf.properties.Properties.initialize(url)
      try body
      finally {
        org.orbeon.oxf.properties.Properties.initialize("oxf:/ops/unit-tests/properties.xml")
      }
    }

    it("must round-trip encrypt and decrypt Version 0 across sizes") {
      for (size <- Sizes) {
        val bytes     = randomBytes(size)
        val encrypted = SecureUtils.AesGcm.encryptWithPassword(TestPassword, bytes, useVersion1 = false)
        val decrypted = SecureUtils.AesGcm.decryptWithPassword(TestPassword, encrypted)
        assert(bytes sameElements decrypted)
        assert(encrypted.length == 12 + size + 16)
      }
    }

    it("must round-trip encrypt and decrypt Version 1 (PBKDF2WithHmacSHA256) across sizes, preserve embedded iterations, and verify AAD") {
      val aad1 = "name=oxf.fr.field-encryption.password".getBytes(org.orbeon.io.CharsetNames.Utf8)
      val aad2 = "name=oxf.fr.access-token.password".getBytes(org.orbeon.io.CharsetNames.Utf8)

      for (size <- Sizes) {
        val bytes      = randomBytes(size)
        val encrypted1 = SecureUtils.AesGcm.encryptWithPassword(TestPassword, bytes, useVersion1 = true, iterations = 10000)
        val encrypted2 = SecureUtils.AesGcm.encryptWithPassword(TestPassword, bytes, useVersion1 = true, iterations = 25000)
        assert(encrypted1.take(4) sameElements Array[Byte](0x4F, 0x46, 0x00, 0x01))
        assert(encrypted1.slice(4, 8) sameElements Array[Byte](0x00, 0x00, 0x27, 0x10)) // 10000 big-endian
        assert(encrypted2.slice(4, 8) sameElements Array[Byte](0x00, 0x00, 0x61, 0xA8.toByte)) // 25000 big-endian
        assert(encrypted1.length == 8 + 16 + 12 + size + 16)
        assert(! (encrypted1 sameElements encrypted2))
        // Both ciphertexts decrypt without passing iterations, even though they used different iteration counts
        assert(bytes sameElements SecureUtils.AesGcm.decryptWithPassword(TestPassword, encrypted1))
        assert(bytes sameElements SecureUtils.AesGcm.decryptWithPassword(TestPassword, encrypted2))

        // Associated data (AAD) must authenticate and reject mismatched or missing AAD
        val encryptedWithAad = SecureUtils.AesGcm.encryptWithPassword(TestPassword, bytes, useVersion1 = true, iterations = 10000, aadOpt = Some(aad1))
        assert(bytes sameElements SecureUtils.AesGcm.decryptWithPassword(TestPassword, encryptedWithAad, aadOpt = Some(aad1)))
        assert(Try(SecureUtils.AesGcm.decryptWithPassword(TestPassword, encryptedWithAad, aadOpt = Some(aad2))).isFailure)
        assert(Try(SecureUtils.AesGcm.decryptWithPassword(TestPassword, encryptedWithAad, aadOpt = None)).isFailure)
      }
    }

    it("must decrypt ciphertexts produced by Google Tink 1.20.0 AesGcmJce without polluting PBKDF2 key cache") {
      val stringVectors = List(
        "Hello, Orbeon Forms! Testing Tink backward compatibility." -> "7eFI2d4BL5WqHPs7yf2iEfM0hnnKgVfNe8cUqIZCA+B9nAOlT7JIRosHdZ4wyWOvQoE+FWUEz/hiOIRyubF6t13gH/LPt171Cr+g/ZnSpcWio/1l+w==",
        "43"                                                        -> "4gcE0cY39H6A28IYyjzcaHwT4nCtlfVOISljZ6qD",
        ""                                                          -> "LZWoG8U5rfXUY2Yqe/bI1HHCiF2r5KKO8mR2Ow==",
        """<tag attr="a&b">Café 日本語</tag>"""                      -> "BRh7wFxXvHbI99rOI+Orafi4MLQ/aOG/zwudmpOQ953pugSTcwjOdF/magRW+GtWeMp1P1EwlNzyxiRWEG+FLMA=",
        "Value whose legacy Tink IV starts with 0x01"               -> "AUHFTmyiuv251TtpAfW6wDemP1+AQvuQKvL+XNxZ8/ZdcJgjxS87nyXWGlfNusCThfi90aiTNVEgdt0VPIXveM1jzD8mlpU="
      )

      val cacheCountBefore = SecureUtils.AesGcm.cachedPbkdf2KeyCount
      for ((expectedText, tinkCiphertext) <- stringVectors)
        assert(SecureUtils.AesGcm.decryptWithPassword(TestPassword, tinkCiphertext) == expectedText)
      assert(SecureUtils.AesGcm.cachedPbkdf2KeyCount == cacheCountBefore)
    }

    it("must fail when tampering with Version 0 or Version 1 AES-GCM ciphertext") {
      for (useVersion1 <- List(false, true)) {
        val encrypted = SecureUtils.AesGcm.encryptWithPassword(TestPassword, randomBytes(64), useVersion1 = useVersion1, iterations = 10000)
        encrypted(encrypted.length - 1) = (encrypted(encrypted.length - 1) ^ 0x01).toByte
        assert(Try(SecureUtils.AesGcm.decryptWithPassword(TestPassword, encrypted)).isFailure)
      }
    }

    it("must write Version 1 by default in non-FIPS mode and honor oxf.crypto.fips-mode for ephemeral crypto, provider lookup, and password length") {
      withProperties("oxf:/ops/unit-tests/properties-encryption-1.xml") {
        assert(! SecureUtils.isFipsMode)
        val nonFipsEncrypted = SecureUtils.AesGcm.encrypt("Default Version 1 secret")
        val nonFipsBytes     = java.util.Base64.getDecoder.decode(nonFipsEncrypted)
        assert(nonFipsBytes.take(4) sameElements Array[Byte](0x4F, 0x46, 0x00, 0x01))
        assert(SecureUtils.AesGcm.decrypt(nonFipsEncrypted) == "Default Version 1 secret")
      }

      val v1FromFirstPassword =
        withProperties("oxf:/ops/unit-tests/properties-encryption-fips-1.xml") {
          assert(SecureUtils.isFipsMode)

          // In FIPS mode, legacy `SunJCE` default must not be forced
          assert(! SecureUtils.preferredProviderOpt("Cipher", "AES/GCM/NoPadding").exists(_.getName == "SunJCE"))

          // AesGcm writes Version 1 (`"OF\0\1"` + iterations prefix) and reads both Version 1 and legacy Tink Version 0
          val v1Encrypted = SecureUtils.AesGcm.encrypt("FIPS secret value")
          val v1Bytes     = java.util.Base64.getDecoder.decode(v1Encrypted)
          assert(v1Bytes.take(4) sameElements Array[Byte](0x4F, 0x46, 0x00, 0x01))
          assert(SecureUtils.AesGcm.decrypt(v1Encrypted) == "FIPS secret value")
          assert(SecureUtils.AesGcm.decrypt("AUHFTmyiuv251TtpAfW6wDemP1+AQvuQKvL+XNxZ8/ZdcJgjxS87nyXWGlfNusCThfi90aiTNVEgdt0VPIXveM1jzD8mlpU=") == "Value whose legacy Tink IV starts with 0x01")

          // Ephemeral `SecureUtils.encrypt` / `decrypt` round-trips in FIPS mode (PBKDF2WithHmacSHA256 + AES/GCM/NoPadding)
          val payload    = randomBytes(128)
          val encrypted1 = SecureUtils.encrypt(SecureUtils.KeyUsage.General, payload)
          val encrypted2 = SecureUtils.encrypt(SecureUtils.KeyUsage.General, payload)
          assert(encrypted1 != encrypted2)
          assert(SecureUtils.decrypt(SecureUtils.KeyUsage.General, encrypted1) sameElements payload)
          assert(SecureUtils.decrypt(SecureUtils.KeyUsage.General, encrypted2) sameElements payload)

          v1Encrypted
        }

      // Rotated password in FIPS mode decrypts both Version 1 data (via V1-first fallback) and pre-change Tink data
      withProperties("oxf:/ops/unit-tests/properties-encryption-fips-2.xml") {
        assert(SecureUtils.isFipsMode)
        assert(SecureUtils.AesGcm.decrypt(v1FromFirstPassword) == "FIPS secret value")
        assert(SecureUtils.AesGcm.decrypt("4gcE0cY39H6A28IYyjzcaHwT4nCtlfVOISljZ6qD") == "43")
        assert(SecureUtils.AesGcm.decrypt("AUHFTmyiuv251TtpAfW6wDemP1+AQvuQKvL+XNxZ8/ZdcJgjxS87nyXWGlfNusCThfi90aiTNVEgdt0VPIXveM1jzD8mlpU=") == "Value whose legacy Tink IV starts with 0x01")
      }

      // Short password (< 15 bytes) must fail fast in FIPS mode even when password strength check is disabled
      withProperties("oxf:/ops/unit-tests/properties-encryption-fips-short-password.xml") {
        assert(SecureUtils.isFipsMode)
        intercept[org.orbeon.oxf.common.ConfigurationException] {
          SecureUtils.AesGcm.encrypt("hello")
        }
      }
    }

    it("must reject out-of-range KDF iterations and bound LRU PBKDF2 key cache under concurrent load (> 256 salts)") {
      intercept[org.orbeon.oxf.common.ConfigurationException] {
        SecureUtils.AesGcm.encryptWithPassword(TestPassword, Array[Byte](1, 2, 3), useVersion1 = true, iterations = 999)
      }
      intercept[org.orbeon.oxf.common.ConfigurationException] {
        SecureUtils.AesGcm.encryptWithPassword(TestPassword, Array[Byte](1, 2, 3), useVersion1 = true, iterations = 10000001)
      }

      // 320 distinct 16-byte salts (> Pbkdf2KeyCacheMaxSize = 256) encrypted and decrypted concurrently
      val distinctSalts = (1 to 320).map(_ => randomBytes(16)).toVector
      distinctSalts.par.foreach { salt =>
        val payload   = randomBytes(96)
        val encrypted = SecureUtils.AesGcm.encryptWithPassword(
          password    = TestPassword,
          plaintext   = payload,
          useVersion1 = true,
          iterations  = 1000,
          saltOpt     = Some(salt)
        )
        val decrypted = SecureUtils.AesGcm.decryptWithPassword(TestPassword, encrypted)
        assert(decrypted sameElements payload)
      }

      assert(SecureUtils.AesGcm.cachedPbkdf2KeyCount <= 256)
    }
  }

  private def randomBytes(n: Int) = {
    val bytes = new Array[Byte](n)
    (new SecureRandom).nextBytes(bytes)
    bytes
  }
}
