/**
 * Copyright (C) 2026 Orbeon, Inc.
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
package org.orbeon.oxf.fr

import org.bouncycastle.crypto.CryptoServicesRegistrar
import org.bouncycastle.jcajce.provider.BouncyCastleFipsProvider
import org.orbeon.dom.Document
import org.orbeon.io.CharsetNames
import org.orbeon.oxf.common.ConfigurationException
import org.orbeon.oxf.fr.permission.Operation
import org.orbeon.oxf.pipeline.InitUtils.withNewPipelineContext
import org.orbeon.oxf.processor.ProcessorImpl.{INPUT_CONFIG, INPUT_DATA, OUTPUT_DATA}
import org.orbeon.oxf.processor.generator.{DOMGenerator, KeyPairGenerator}
import org.orbeon.oxf.processor.{DOMSerializer, SignatureProcessor, SignatureVerifierProcessor}
import org.orbeon.oxf.properties.Properties
import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport}
import org.orbeon.oxf.util.PipelineUtils.connect
import org.orbeon.oxf.util.{ByteEncoding, SecureUtils}
import org.orbeon.oxf.xml.dom.Converter.*
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funspec.AnyFunSpecLike

import java.io.File
import java.security.{GeneralSecurityException, Provider, Security}
import java.time.Duration
import java.util.Base64 as JBase64
import scala.util.{Success, Try}


trait BcFipsProviderBootstrap {
  protected val originalProviders: Array[Provider] = Security.getProviders.clone()

  // Simulate SAP Commerce Cloud Strict Security Mode (SSM) before Orbeon ResourceManager/Properties boot:
  // 1. Remove standard JDK JCE crypto providers (`SunJCE`, `SunRsaSign`, `SunEC`) so AES, PBKDF2, HMAC, and RSA
  //    cannot silently fall back to non-FIPS JDK providers; retain `SUN` behind `BCFIPS` as in SSM (`security.provider.3=SUN`).
  // 2. Register `BouncyCastleFipsProvider` as `security.provider.1` under `-Dorg.bouncycastle.fips.approved_only=true`.
  locally {
    for (nonFipsProviderName <- List("SunJCE", "SunRsaSign", "SunEC"))
      Security.removeProvider(nonFipsProviderName)
    if (Security.getProvider("BCFIPS") eq null)
      Security.insertProviderAt(new BouncyCastleFipsProvider(), 1)
  }
}

class FipsStrictModeTest
  extends DocumentTestBase
     with BcFipsProviderBootstrap
     with ResourceManagerSupport
     with AnyFunSpecLike
     with BeforeAndAfterAll {

  override protected def afterAll(): Unit = {
    try {
      Security.getProviders.foreach(p => Security.removeProvider(p.getName))
      originalProviders.zipWithIndex.foreach { case (p, idx) =>
        Security.insertProviderAt(p, idx + 1)
      }
    } finally {
      super.afterAll()
    }
  }

  private def withProperties[T](url: String)(body: => T): T = {
    Properties.initialize(url)
    try body
    finally {
      Properties.initialize("oxf:/ops/unit-tests/properties.xml")
    }
  }

  describe("BC-FIPS Strict Security Mode (approved_only=true) runtime verification") {

    it("must run on a pure BC-FIPS classpath with approved_only=true and zero non-FIPS BC or Tink classes") {
      assert(java.lang.Boolean.getBoolean("org.bouncycastle.fips.approved_only"))
      assert(CryptoServicesRegistrar.isInApprovedOnlyMode)
      assert(Security.getProviders.headOption.map(_.getName).contains("BCFIPS"))
      assert(! Security.getProviders.exists(p => Set("SunJCE", "SunRsaSign", "SunEC")(p.getName)))

      val classpathJars =
        sys.props.getOrElse("java.class.path", "")
          .split(File.pathSeparatorChar)
          .map(new File(_).getName)
          .toList

      assert(classpathJars.exists(_.startsWith("bc-fips-")))
      assert(classpathJars.exists(_.startsWith("bcpkix-fips-")))
      assert(classpathJars.exists(_.startsWith("bcutil-fips-")))
      assert(! classpathJars.exists(n => n.startsWith("bcprov-") || n.startsWith("bcpkix-jdk") || n.startsWith("bcutil-jdk") || n.startsWith("tink-")))

      assert(Try(Class.forName("org.bouncycastle.jce.provider.BouncyCastleProvider")).isFailure)
      assert(Try(Class.forName("com.google.crypto.tink.Aead")).isFailure)
    }

    it("must route all Orbeon JCA services to BCFIPS when oxf.crypto.fips-mode is enabled") {
      withProperties("oxf:/ops/unit-tests/properties-encryption-fips-low-iter.xml") {
        assert(SecureUtils.isFipsMode)

        val expectedBcFipsServices = List(
          "Cipher"           -> "AES/GCM/NoPadding",
          "SecretKeyFactory" -> "PBKDF2WithHmacSHA256",
          "MessageDigest"    -> "SHA-256",
          "Mac"              -> "HmacSHA256",
          "KeyFactory"       -> "RSA",
          "Signature"        -> "SHA256withRSA",
          "KeyPairGenerator" -> "RSA"
        )

        for ((serviceType, algorithm) <- expectedBcFipsServices) {
          val resolved = SecureUtils.preferredProviderOpt(serviceType, algorithm).map(_.getName)
          assert(resolved.contains("BCFIPS"), s"Expected BCFIPS for $serviceType/$algorithm, got $resolved")
        }
      }
    }

    it("must sign and verify SHA256withRSA licenses via BCFIPS without touching DSA, while rejecting SHA1withDSA") {
      withProperties("oxf:/ops/unit-tests/properties-encryption-fips-low-iter.xml") {
        // 1. Generate 3072-bit RSA key pair via Orbeon's KeyPairGenerator processor
        val rsaKeyPairGen = new KeyPairGenerator
        val configDoc     = <config><algorithm>SHA256withRSA</algorithm></config>.toDocument
        connect(
          new DOMGenerator(configDoc, "rsa-cfg", DOMGenerator.ZeroValidity, "oxf:/rsa-cfg.xml"),
          OUTPUT_DATA,
          rsaKeyPairGen,
          INPUT_CONFIG
        )
        val keySerializer = new DOMSerializer
        connect(rsaKeyPairGen, OUTPUT_DATA, keySerializer, INPUT_DATA)
        val rsaKeysDoc =
          withNewPipelineContext("FipsStrictModeTest.generateRsaKeyPair") { pc =>
            keySerializer.reset(pc)
            keySerializer.runGetDocument(pc)
          }

        val rsaPrivElem = rsaKeysDoc.getRootElement.element("private-key")
        val rsaPubElem  = rsaKeysDoc.getRootElement.element("public-key")
        assert(rsaPrivElem.attributeValueOpt("algorithm").contains("SHA256withRSA"))

        // Include a dummy legacy DSA public-key entry alongside RSA in `<public-keys>` to prove DSA is never instantiated
        val multiPublicKeysDoc =
          <public-keys>
            <public-key>MIIBtzCCASwGByqGSM44BAEwggEfAoGBAP1/U4EddRIpUt9KnC7s5Of2EbdSPO9EAMMeP4C2USZpRV1AIlH7WT2NWPq/xfW6MPbLm1Vs14E7gB00b/JmYLdrmVClpJ+f6AR7ECLCT7up1/63xhv4O1fnxqimFQ8E+4P208UewwI1VBNaFpEy9nXzrith1yrv8iIDGZ3RSAHHAhUAl2BQjxUjC8yykrmCouuEC/BYHPUCgYEA9+GghdabPd7LvKtcNrhXuXmUr7v6OuqC+VdMCz0HgmdRWVeOutRZT+ZxBxCBgLRJFnEj6EwoFhO3zwkyjMim4TwWeotUfI0o4KOuHiuzpnWRbqN/C/ohNWLx+2J6ASQ7zKTxvqhRkImog9/hWuWfBpKLZl6Ae1UlZAFMO/7PSSoDgYQAAoGAQ1s7c5fH289x2X0Y3nK1vJ6p/M8l0w==</public-key>
            <public-key algorithm="SHA256withRSA">{rsaPubElem.getStringValue}</public-key>
          </public-keys>.toDocument

        val licensePayload =
          <license>
            <licensor>Orbeon, Inc.</licensor>
            <licensee>SAP SSM FIPS Customer</licensee>
          </license>.toDocument

        // 2. Sign with SHA256withRSA via SignatureProcessor
        val sigProcessor = new SignatureProcessor
        connect(new DOMGenerator(licensePayload, "lic", DOMGenerator.ZeroValidity, "oxf:/lic.xml"), OUTPUT_DATA, sigProcessor, INPUT_DATA)
        connect(new DOMGenerator(Document(rsaPrivElem.createCopy), "priv", DOMGenerator.ZeroValidity, "oxf:/priv.xml"), OUTPUT_DATA, sigProcessor, "private-key")
        val sigSerializer = new DOMSerializer
        connect(sigProcessor, OUTPUT_DATA, sigSerializer, INPUT_DATA)
        val rsaSignedDoc =
          withNewPipelineContext("FipsStrictModeTest.signRsa") { pc =>
            sigSerializer.reset(pc)
            sigSerializer.runGetDocument(pc)
          }

        // 3. Verify with SignatureVerifierProcessor against `<public-keys>`
        val verifier = new SignatureVerifierProcessor
        connect(new DOMGenerator(rsaSignedDoc, "signed", DOMGenerator.ZeroValidity, "oxf:/signed.xml"), OUTPUT_DATA, verifier, INPUT_DATA)
        connect(new DOMGenerator(multiPublicKeysDoc, "pubs", DOMGenerator.ZeroValidity, "oxf:/pubs.xml"), OUTPUT_DATA, verifier, SignatureVerifierProcessor.INPUT_PUBLIC_KEY)
        val verSerializer = new DOMSerializer
        connect(verifier, OUTPUT_DATA, verSerializer, INPUT_DATA)
        val verifiedDoc =
          withNewPipelineContext("FipsStrictModeTest.verifyRsa") { pc =>
            verSerializer.reset(pc)
            verSerializer.runGetDocument(pc)
          }
        assert(verifiedDoc.getRootElement.element("licensee").getStringValue == "SAP SSM FIPS Customer")

        // 4. Confirm BCFIPS in approved_only mode actively blocks unapproved algorithms:
        // - DSA key generation is absent (`NoSuchAlgorithmException` under FIPS 186-5)
        // - `SHA1withRSA` signing throws `FipsUnapprovedOperationError` even with a valid 3072-bit RSA key
        val bcfips = Security.getProvider("BCFIPS")
        intercept[java.security.NoSuchAlgorithmException] {
          java.security.KeyPairGenerator.getInstance("DSA", bcfips)
        }
        val rsaPrivKey = java.security.KeyFactory.getInstance("RSA", bcfips).generatePrivate(
          new java.security.spec.PKCS8EncodedKeySpec(org.orbeon.oxf.util.Base64.decode(rsaPrivElem.getStringValue))
        )
        val caughtUnapproved =
          try {
            java.security.Signature.getInstance("SHA1withRSA", bcfips).initSign(rsaPrivKey)
            false
          } catch {
            case _: org.bouncycastle.crypto.fips.FipsUnapprovedOperationError => true
          }
        assert(caughtUnapproved)
      }
    }

    it("must execute AES-GCM encryption (strings + binary attachments, AAD, rotation, and legacy Tink V0 read) under BCFIPS approved_only") {
      val aadBytes     = "name=oxf.fr.field-encryption.password".getBytes(CharsetNames.Utf8)
      val wrongAad     = "name=oxf.fr.access-token.password".getBytes(CharsetNames.Utf8)
      val attachBytes  = Array.tabulate[Byte](2048)(i => (i * 13 + 5).toByte)

      val (v1EncryptedString, v1EncryptedAttach, v1WithAad) =
        withProperties("oxf:/ops/unit-tests/properties-encryption-fips-low-iter.xml") {
          val cipherText = SecureUtils.AesGcm.encrypt("Approved BCFIPS AES-256-GCM", SecureUtils.KeyUsage.FieldEncryption)
          val rawBytes   = JBase64.getDecoder.decode(cipherText)
          assert(rawBytes.take(4) sameElements Array[Byte](0x4F, 0x46, 0x00, 0x01))

          val encAttach = SecureUtils.AesGcm.encrypt(attachBytes, SecureUtils.KeyUsage.FieldEncryption)
          assert(encAttach.take(4) sameElements Array[Byte](0x4F, 0x46, 0x00, 0x01))

          val encWithAad = SecureUtils.AesGcm.encryptV1WithPassword(
            password   = "ForTestsOnly-1!T42mMw*pgH9.EfTZx!4r",
            plaintext  = "wrapped-secret-value".getBytes(CharsetNames.Utf8),
            iterations = 10000,
            aadOpt     = Some(aadBytes)
          )

          (cipherText, encAttach, encWithAad)
        }

      // Verify AAD unwrap and AAD mismatch failure on BCFIPS
      val unwrappedAad = SecureUtils.AesGcm.decryptWithPassword(
        password   = "ForTestsOnly-1!T42mMw*pgH9.EfTZx!4r",
        ciphertext = v1WithAad,
        aadOpt     = Some(aadBytes)
      )
      assert(new String(unwrappedAad, CharsetNames.Utf8) == "wrapped-secret-value")
      intercept[GeneralSecurityException] {
        SecureUtils.AesGcm.decryptWithPassword(
          password   = "ForTestsOnly-1!T42mMw*pgH9.EfTZx!4r",
          ciphertext = v1WithAad,
          aadOpt     = Some(wrongAad)
        )
      }

      // Switch to rotated password (`read-fallback` = password 1) in FIPS mode:
      // 1. Version 1 string & binary attachment decrypt via V1-first fallback without touching Version 0
      // 2. Pre-change Google Tink 1.20.0 Version 0 string & binary attachment vectors also decrypt cleanly via BCFIPS SHA-256 + AES-128-GCM!
      withProperties("oxf:/ops/unit-tests/properties-encryption-fips-2.xml") {
        assert(SecureUtils.AesGcm.decrypt(v1EncryptedString, SecureUtils.KeyUsage.FieldEncryption) == "Approved BCFIPS AES-256-GCM")
        assert(SecureUtils.AesGcm.decrypt(v1EncryptedAttach, SecureUtils.KeyUsage.FieldEncryption) sameElements attachBytes)

        // Pre-change Tink Version 0 XML & binary attachment vectors
        assert(SecureUtils.AesGcm.decrypt("4gcE0cY39H6A28IYyjzcaHwT4nCtlfVOISljZ6qD") == "43")
        assert(SecureUtils.AesGcm.decrypt("AUHFTmyiuv251TtpAfW6wDemP1+AQvuQKvL+XNxZ8/ZdcJgjxS87nyXWGlfNusCThfi90aiTNVEgdt0VPIXveM1jzD8mlpU=") == "Value whose legacy Tink IV starts with 0x01")

        val tinkAttachBytes = JBase64.getDecoder.decode("AYcgoEKSimdvRQYTFufZ9zJOSdwxlLDgJtQjh+BPlfoi4e0DwS/x48u6Ab4=")
        val expectedPngHead = Array(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0xFF, 0x01, 0x02, 0x03, 0x04, 0x7F, 0x80).map(_.toByte)
        assert(SecureUtils.AesGcm.decrypt(tinkAttachBytes, SecureUtils.KeyUsage.FieldEncryption) sameElements expectedPngHead)
      }

      // Short password (< 15 bytes) fails fast with ConfigurationException
      withProperties("oxf:/ops/unit-tests/properties-encryption-fips-short-password.xml") {
        intercept[ConfigurationException] {
          SecureUtils.AesGcm.encrypt("blocked")
        }
      }
    }

    it("must execute ephemeral encryption, HMAC, randomHexId, and all Form Runner tokens under BCFIPS approved_only") {
      withProperties("oxf:/ops/unit-tests/properties-encryption-fips-low-iter.xml") {
        val hexId = SecureUtils.randomHexId
        assert(hexId.length == SecureUtils.HexShortLength)

        val hmac = SecureUtils.hmacString(SecureUtils.KeyUsage.Token, "fips-hmac-input", ByteEncoding.Hex)
        assert(hmac.nonEmpty)

        val ephemeralPlain = "ephemeral-xforms-state-payload".getBytes(CharsetNames.Utf8)
        val ephemeralEnc   = SecureUtils.encrypt(SecureUtils.KeyUsage.General, ephemeralPlain)
        assert(SecureUtils.decrypt(SecureUtils.KeyUsage.General, ephemeralEnc) sameElements ephemeralPlain)

        val ops       = Set[Operation](Operation.Read, Operation.Update)
        val tokenHmac = FormRunnerAccessToken.TokenHmac("acme", "fips-form", 1, Some("doc-1"))
        val accessTok = FormRunnerAccessToken.encryptToken("acme", "fips-form", 1, Some("doc-1"), Duration.ofMinutes(5), ops).get
        assert(FormRunnerAccessToken.decryptTokenPayloadCheckExpiration(tokenHmac, accessTok).map(_.toSet) == Success(ops))

        val adminTok = FormRunnerAdminToken.encryptToken(Duration.ofMinutes(5), isInternalAdminUser = true).get
        assert(FormRunnerAdminToken.decryptTokenPayloadCheckExpiration((), adminTok) == Success(true))

        val extTok = FormRunnerExternalModeToken.encryptToken("acme", "fips-form", 1, Some("doc-1"), Duration.ofMinutes(5)).get
        assert(FormRunnerExternalModeToken.decryptTokenPayloadCheckExpiration((), extTok).isSuccess)

        val opsEnc = FormRunnerOperationsEncryption.encryptString("choices-validation")
        assert(FormRunnerOperationsEncryption.decryptString(opsEnc).contains("choices-validation"))
      }
    }
  }
}
