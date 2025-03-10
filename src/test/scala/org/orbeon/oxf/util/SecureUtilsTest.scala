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

    it("must error upon decrypting with incorrect password") {
      assertThrows[java.security.GeneralSecurityException] {
        SecureUtils.decrypt(SecureUtils.KeyUsage.General, encrypted2)
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

  private def randomBytes(n: Int) = {
    val bytes = new Array[Byte](n)
    (new SecureRandom).nextBytes(bytes)
    bytes
  }
}
