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
import org.orbeon.oxf.common.PEVersion._
import org.orbeon.oxf.processor.validation.SchemaValidationException
import org.orbeon.oxf.test.{ResourceManagerSupport, ResourceManagerTestBase}
import org.orbeon.oxf.util.CollectionUtils._
import org.orbeon.oxf.util.DateUtilsUsingSaxon
import org.orbeon.oxf.xml.dom.Converter._
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
}
