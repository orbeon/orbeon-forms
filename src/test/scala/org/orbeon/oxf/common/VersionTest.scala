/**
 * Copyright (C) 2010 Orbeon, Inc.
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
package org.orbeon.oxf.common

import java.security.SignatureException

import org.orbeon.dom.Document
import org.orbeon.oxf.common.PEVersion.*
import org.orbeon.oxf.processor.validation.SchemaValidationException
import org.orbeon.oxf.test.{ResourceManagerSupport, ResourceManagerTestBase}
import org.orbeon.oxf.util.CollectionUtils.*
import org.orbeon.oxf.util.DateUtilsUsingSaxon
import org.orbeon.oxf.xml.dom.Converter.*
import org.scalatest.funspec.AnyFunSpecLike

import scala.util.Try

class VersionTest
  extends ResourceManagerTestBase
     with ResourceManagerSupport
     with AnyFunSpecLike {

  describe("Product configuration") {
    it(s"must pass for edition `${Version.Edition}`") {
      if (Version.isPE) {
        assert("PE" === Version.Edition)
        assert(  Version.isPE)
        assert(! Version.instance.isPEFeatureEnabled(featureRequested = false, "foobar"))
        assert(  Version.instance.isPEFeatureEnabled(featureRequested = true,  "foobar"))
      } else {
        assert("CE" === Version.Edition)
        assert(! Version.isPE)
        assert(! Version.instance.isPEFeatureEnabled(featureRequested = false, "foobar"))
        assert(! Version.instance.isPEFeatureEnabled(featureRequested = true,  "foobar"))
      }
    }
  }

  describe("Version expired") {

    val LicenseVersion = "3.8"

    val Suffixes = List(
      "",
      ".1",
      ".0",
      ".2",
      ".10",
      ".foo.bar"
    )

    val Expectations = List(
      ("3.8", false),
      ("3.7", false),
      ("2.0", false),
      ("3.9", true),
      ("4.0", true)
    )

    for {
      (version, expected) <- Expectations
      suffix              <- Suffixes
      versionWithSuffix   = version + suffix
    } locally {
      it(s"must pass for `$versionWithSuffix") {
        assert(expected === PEVersion.isVersionExpired(versionWithSuffix, LicenseVersion))
      }
    }
  }

  private def currentBuildIsSnapshot: Boolean =
    Version.VersionNumber.contains("SNAPSHOT")

  describe("Date from version number") {

    assert(PEVersion.dateFromVersionNumber(Version.VersionNumber).isDefined || currentBuildIsSnapshot)

    val TimeStamp = Some(DateUtilsUsingSaxon.parseISODateOrDateTime("2013-01-28"))

    val BuildTimeStamp = "201301281947"

    val Expectations = List(
      s"$BuildTimeStamp"               -> TimeStamp,
      s"4.1.0.$BuildTimeStamp"         -> TimeStamp,
      s"4.1.0.$BuildTimeStamp.42"      -> TimeStamp,
      s"prefix.$BuildTimeStamp.suffix" -> TimeStamp,
      s"prefix${BuildTimeStamp}suffix" -> TimeStamp,
      s"${BuildTimeStamp}8"            -> None,
      s"20130128"                      -> None
    )

    for ((in, expected) <- Expectations)
      it(s"must pass for `$in`") {
        assert(expected === PEVersion.dateFromVersionNumber(in))
      }
  }

  describe("LicenseInfo") {

    def create(version: Option[String], expiration: Option[Long], subscriptionEnd: Option[Long]) =
      LicenseInfo(
        versionNumber   = "4.1.0.201301281947",
        licensor        = "Orbeon",
        licensee        = "Daffy Duck",
        organization    = "Acme",
        email           = "daffy@orbeon.com",
        issued          = "2013-03-29",
        version         = version,
        expiration      = expiration,
        subscriptionEnd = subscriptionEnd
      )

    def parse(s: String) = Some(DateUtilsUsingSaxon.parseISODateOrDateTime(s))

    case class Booleans(badVersion: Boolean, expired: Boolean, buildAfterSubscriptionEnd: Boolean)

    val Expectations = List(
      (Some("4.0"), None, None)         -> Booleans(badVersion = true,  expired = false, buildAfterSubscriptionEnd = false),
      (None, parse("3000-01-01"), None) -> Booleans(badVersion = false, expired = false, buildAfterSubscriptionEnd = false),
      (None, parse("1000-01-01"), None) -> Booleans(badVersion = false, expired = true,  buildAfterSubscriptionEnd = false),
      (None, None, parse("3000-01-01")) -> Booleans(badVersion = false, expired = false, buildAfterSubscriptionEnd = false),
      (None, None, parse("2013-01-28")) -> Booleans(badVersion = false, expired = false, buildAfterSubscriptionEnd = false),
      (None, None, parse("2013-01-27")) -> Booleans(badVersion = false, expired = false, buildAfterSubscriptionEnd = true)
    )

    for {
      (in, expected) <- Expectations
      license        = (create _).tupled(in)
    } locally {
      it(s"must pass for `$in`") {
        assert(expected.badVersion                === license.isBadVersion)
        assert(expected.expired                   === license.isExpired)
        assert(expected.buildAfterSubscriptionEnd === license.isBuildAfterSubscriptionEnd)
      }
      it(s"must not crash on `toString` for `$in`") {
        license.toString
      }
    }
  }

  private def tryLicenseInfo(license: Document): Try[LicenseInfo] = (
    Try(license)
    flatMap tryGetSignedData
    flatMap LicenseInfo.tryApply
  )

  describe("Invalid signature") {

    val Expectations = List(
      (
        "document contains the signature of some random value hopefully forever lost",
        <signed-data>
          <data>
            <license>
              <licensor>Orbeon, Inc.</licensor>
              <licensee>Wile E. Coyote</licensee>
              <organization>Acme, Corp.</organization>
              <email>info@orbeon.com</email>
              <issued>2013-04-01</issued>
              <expiration/>
            </license>
          </data>
          <signature>MCwCFDp6Ee9MYRjJnBcDA4RS2SjPjJ8PAhQ1zYrSudg9e7ZheQlnPGDSDiFiKQ==</signature>
        </signed-data>.toDocument,
        classOf[SignatureException]
      ),
      (
        "document contains empty signature",
        <signed-data>
          <data>
            <license>
              <licensor>Orbeon, Inc.</licensor>
              <licensee>Wile E. Coyote</licensee>
              <organization>Acme, Corp.</organization>
              <email>info@orbeon.com</email>
              <issued>2013-04-01</issued>
              <expiration/>
            </license>
          </data>
          <signature/>
        </signed-data>.toDocument,
        classOf[SignatureException]
      ),
      (
        "document contains signature not in Base64",
        <signed-data>
          <data>
            <license>
              <licensor>Orbeon, Inc.</licensor>
              <licensee>Wile E. Coyote</licensee>
              <organization>Acme, Corp.</organization>
              <email>info@orbeon.com</email>
              <issued>2013-04-01</issued>
              <expiration/>
            </license>
          </data>
          <signature>FUNKY STUFF</signature>
        </signed-data>.toDocument,
        classOf[SchemaValidationException]
      )
    )

    for {
      (description, license, expectedClass) <- Expectations
    } locally {
      it(description) {

        val thrown = intercept[Exception] {
          tryLicenseInfo(license).get
        }

        val thrownCauses = Iterator.iterateFrom(thrown, (e: Throwable) => Option(e.getCause)).toList

        assert(thrownCauses.map(_.getClass).contains(expectedClass))
      }
    }
  }

  describe("License is for old version") {

    val license =
      <signed-data>
        <data>
          <license>
            <licensor>Orbeon, Inc.</licensor>
            <licensee>Wile E. Coyote</licensee>
            <organization>Acme, Corp.</organization>
            <email>info@orbeon.com</email>
            <issued>2013-04-01</issued>
            <version>3.0</version>
            <expiration/>
            <subscription-start/>
            <subscription-end/>
            <license-id/>
            <license-description/>
          </license>
        </data>
        <signature>MCwCFCHbcWqtslECGXPIUuO6jcEfq0GSAhRU/X9TceCC36jTpkh7oHAHU/7vnw==</signature>
      </signed-data>.toDocument

    it("must succeed") {
      assert(tryLicenseInfo(license).get.isBadVersion)
    }
  }

  describe("Build after subscription end") {

    // License has subscription ending way in the past
    val license =
      <signed-data>
        <data>
          <license>
            <licensor>Orbeon, Inc.</licensor>
            <licensee>Wile E. Coyote</licensee>
            <organization>Acme, Corp.</organization>
            <email>info@orbeon.com</email>
            <issued>2013-04-01</issued>
            <version/>
            <expiration/>
            <subscription-start>2000-01-01</subscription-start>
            <subscription-end>2001-01-01</subscription-end>
            <license-id/>
            <license-description/>
          </license>
        </data>
        <signature>MCwCFANERfNHS+vLMFZGftW9Fa0TDUi3AhRYoUMYU0g/BfIgbmWo8LA4vRgK3Q==</signature>
      </signed-data>.toDocument

    it("must succeed") {
      assert(currentBuildIsSnapshot || tryLicenseInfo(license).get.isBuildAfterSubscriptionEnd)
    }
  }

  describe("License is expired") {

    val license =
      <signed-data>
        <data>
          <license>
            <licensor>Orbeon, Inc.</licensor>
            <licensee>Wile E. Coyote</licensee>
            <organization>Acme, Corp.</organization>
            <email>info@orbeon.com</email>
            <issued>2013-04-01</issued>
            <version/>
            <expiration>2001-01-01</expiration>
            <subscription-start/>
            <subscription-end/>
            <license-id/>
            <license-description/>
          </license>
        </data>
        <signature>MCwCFEp9uGxNm2b+61mxKpgBnjxDUmE+AhRcV3FrqBjPGbDPM1kId2R0AGC/FQ==</signature>
      </signed-data>.toDocument

    it("must succeed") {
      assert(tryLicenseInfo(license).get.isExpired)
    }
  }

  describe("License number parsing") {

    val Expected = List(
      "3.8"                    -> (3, 8),
      "4.1.0.201301281947"     -> (4, 1),
      "2022.1.202212310402-PE" -> (2022, 1),
      "2022.1 PE"              -> (2022, 1), // https://github.com/orbeon/orbeon-forms/issues/5632
    )

    for ((s, majorMinor) <- Expected)
      it(s"must succeed for `$s`") {
        assert(VersionSupport.majorMinor(s).contains(majorMinor))
      }
  }

  describe("KeyPairGenerator, SignatureProcessor, and SignatureVerifierProcessor (DSA and SHA256withRSA)") {

    import org.orbeon.oxf.pipeline.InitUtils.withNewPipelineContext
    import org.orbeon.oxf.processor.ProcessorImpl.{INPUT_CONFIG, INPUT_DATA, OUTPUT_DATA}
    import org.orbeon.oxf.processor.generator.{DOMGenerator, KeyPairGenerator}
    import org.orbeon.oxf.processor.{DOMSerializer, SignatureProcessor, SignatureVerifierProcessor}
    import org.orbeon.oxf.util.PipelineUtils.connect

    def generateKeyPair(algorithmOpt: Option[String]): Document = {
      val keyPairGen = new KeyPairGenerator
      algorithmOpt.foreach { alg =>
        val configDoc = <config><algorithm>{alg}</algorithm></config>.toDocument
        val configGen = new DOMGenerator(configDoc, "key-pair-config", DOMGenerator.ZeroValidity, "oxf:/key-pair-config.xml")
        connect(configGen, OUTPUT_DATA, keyPairGen, INPUT_CONFIG)
      }
      val serializer = new DOMSerializer
      connect(keyPairGen, OUTPUT_DATA, serializer, INPUT_DATA)
      withNewPipelineContext("VersionTest.generateKeyPair") { pipelineContext =>
        serializer.reset(pipelineContext)
        serializer.runGetDocument(pipelineContext)
      }
    }

    def signDocument(dataDoc: Document, privateKeyDoc: Document): Document = {
      val dataGen      = new DOMGenerator(dataDoc, "data", DOMGenerator.ZeroValidity, "oxf:/data.xml")
      val privKeyGen   = new DOMGenerator(privateKeyDoc, "private-key", DOMGenerator.ZeroValidity, "oxf:/private-key.xml")
      val sigProcessor = new SignatureProcessor
      connect(dataGen,    OUTPUT_DATA, sigProcessor, INPUT_DATA)
      connect(privKeyGen, OUTPUT_DATA, sigProcessor, "private-key")
      val serializer = new DOMSerializer
      connect(sigProcessor, OUTPUT_DATA, serializer, INPUT_DATA)
      withNewPipelineContext("VersionTest.signDocument") { pipelineContext =>
        serializer.reset(pipelineContext)
        serializer.runGetDocument(pipelineContext)
      }
    }

    def verifyDocument(signedDoc: Document, publicKeyDoc: Document): Document = {
      val signedGen         = new DOMGenerator(signedDoc, "signed", DOMGenerator.ZeroValidity, "oxf:/signed.xml")
      val pubKeyGen         = new DOMGenerator(publicKeyDoc, "public-key", DOMGenerator.ZeroValidity, "oxf:/public-key.xml")
      val verifierProcessor = new SignatureVerifierProcessor
      connect(signedGen, OUTPUT_DATA, verifierProcessor, INPUT_DATA)
      connect(pubKeyGen, OUTPUT_DATA, verifierProcessor, SignatureVerifierProcessor.INPUT_PUBLIC_KEY)
      val serializer = new DOMSerializer
      connect(verifierProcessor, OUTPUT_DATA, serializer, INPUT_DATA)
      withNewPipelineContext("VersionTest.verifyDocument") { pipelineContext =>
        serializer.reset(pipelineContext)
        serializer.runGetDocument(pipelineContext)
      }
    }

    it("must sign and verify with both default DSA and SHA256withRSA using multi-key <public-keys>") {
      val dsaKeysDoc = generateKeyPair(None)
      val rsaKeysDoc = generateKeyPair(Some("SHA256withRSA"))

      val dsaPrivElem = dsaKeysDoc.getRootElement.element("private-key")
      val dsaPubElem  = dsaKeysDoc.getRootElement.element("public-key")
      val rsaPrivElem = rsaKeysDoc.getRootElement.element("private-key")
      val rsaPubElem  = rsaKeysDoc.getRootElement.element("public-key")

      assert(dsaPrivElem.attributeValueOpt("algorithm").isEmpty)
      assert(rsaPrivElem.attributeValueOpt("algorithm").contains("SHA256withRSA"))
      assert(rsaPubElem.attributeValueOpt("algorithm").contains("SHA256withRSA"))

      val multiPublicKeysDoc =
        <public-keys>
          <public-key>{dsaPubElem.getStringValue}</public-key>
          <public-key algorithm="SHA256withRSA">{rsaPubElem.getStringValue}</public-key>
        </public-keys>.toDocument

      val samplePayload = <license><licensor>Orbeon, Inc.</licensor><licensee>FIPS Customer</licensee></license>.toDocument

      // 1. Verify legacy DSA signature against multiPublicKeysDoc
      val dsaPrivDoc = Document(dsaPrivElem.createCopy)
      val dsaSigned  = signDocument(samplePayload, dsaPrivDoc)
      assert(dsaSigned.getRootElement.element("signature").attributeValueOpt("algorithm").isEmpty)
      val dsaVerified = verifyDocument(dsaSigned, multiPublicKeysDoc)
      assert(dsaVerified.getRootElement.element("licensee").getStringValue == "FIPS Customer")

      // 2. Verify SHA256withRSA signature against multiPublicKeysDoc
      val rsaPrivDoc = Document(rsaPrivElem.createCopy)
      val rsaSigned  = signDocument(samplePayload, rsaPrivDoc)
      assert(rsaSigned.getRootElement.element("signature").attributeValueOpt("algorithm").contains("SHA256withRSA"))
      val rsaVerified = verifyDocument(rsaSigned, multiPublicKeysDoc)
      assert(rsaVerified.getRootElement.element("licensee").getStringValue == "FIPS Customer")
    }
  }
}
