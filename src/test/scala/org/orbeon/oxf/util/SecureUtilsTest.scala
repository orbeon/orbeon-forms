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

import org.junit.Test
import java.security.SecureRandom
import org.orbeon.oxf.test.ResourceManagerTestBase
import org.scalatestplus.junit.AssertionsForJUnit

// NOTE: hmac is tested via XFormsUploadControlTest
class SecureUtilsTest extends ResourceManagerTestBase with AssertionsForJUnit {

  private val sizes = Seq(0, 1, 10, 16, 100, 128, 256, 1000, 1024, 10000, 16384, 100000)

  @Test def encryptDecrypt(): Unit = {

    def asserts(size: Int) = {

      val bytes = randomBytes(size)

      // Decrypted value is the same as the original
      assert(bytes.toList === SecureUtils.decrypt(SecureUtils.encrypt(bytes)).toList)

      // Encrypting the same value twice doesn't yield the same result
      assert(SecureUtils.encrypt(bytes) != SecureUtils.encrypt(bytes))

      // Unless we provide an IV
      val iv = randomBytes(16)
      assert(SecureUtils.encryptIV(bytes, Some(iv)) === SecureUtils.encryptIV(bytes, Some(iv)))
    }

    // Try multiple data sizes
    for (size ← sizes)
      asserts(size)

    // Try in parallel
    for (size ← sizes.par)
      asserts(size)
  }

  @Test def toHex(): Unit = {
    val bytes = randomBytes(100)
    assert(bytes.map("%02X" format _).mkString.toLowerCase === SecureUtils.byteArrayToHex(bytes))
  }

  @Test def digest(): Unit = {

    val algorithms = Seq("SHA1", "MD5")
    val encodings  = Seq("hex", "base64")

    def asserts(algorithm: String, encoding: String, size: Int) = {
      val bytes = randomBytes(size)

      // Same digest
      assert(SecureUtils.digestBytes(bytes, algorithm, encoding) === SecureUtils.digestBytes(bytes, algorithm, encoding))

      // Digest different if data changes
      assert(SecureUtils.digestBytes(bytes :+ 42.asInstanceOf[Byte], algorithm, encoding) != SecureUtils.digestBytes(bytes, algorithm, encoding))
    }

    // Try multiple algorithms, encodings, and data sizes
    for (algorithm ← algorithms; encoding ← encodings; size ← sizes)
      asserts(algorithm, encoding, size)

    // Try in parallel
    for (algorithm ← algorithms.par; encoding ← encodings; size ← sizes)
      asserts(algorithm, encoding, size)

    // Check that for a given algorithm/encoding, the digest has the same size for any size of input data
    def assertSameSize(algorithm: String, encoding: String) =
      sizes map randomBytes map (SecureUtils.digestBytes(_, algorithm, encoding)) map (_.size) sliding 2 foreach
        { case Seq(s1, s2) ⇒ assert(s1 === s2) }

    for (algorithm ← algorithms; encoding ← encodings)
      assertSameSize(algorithm, encoding)
  }

  private def randomBytes(n: Int) = {
    val bytes = new Array[Byte](n)
    (new SecureRandom).nextBytes(bytes)
    bytes
  }
}
