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

import junit.framework.Assert._
import org.orbeon.dom.Document
import org.junit.Test
import org.orbeon.oxf.common.PEVersion._
import org.orbeon.oxf.processor.validation.SchemaValidationException
import org.orbeon.oxf.test.ResourceManagerTestBase
import org.orbeon.oxf.util.DateUtils
import org.orbeon.oxf.util.ScalaUtils._
import org.orbeon.oxf.xml.Dom4j.elemToDocument
import org.scalatest.junit.AssertionsForJUnit

import scala.util.Try

class VersionTest extends ResourceManagerTestBase with AssertionsForJUnit {

  @Test def productConfiguration(): Unit = {
    if (Version.isPE) {
      assertEquals("PE", Version.Edition)
      assertTrue(Version.isPE)
      assertFalse(Version.instance.isPEFeatureEnabled(featureRequested = false, "foobar"))
      assertTrue(Version.instance.isPEFeatureEnabled(featureRequested = true, "foobar"))
    } else {
      assertEquals("CE", Version.Edition)
      assertFalse(Version.isPE)
      assertFalse(Version.instance.isPEFeatureEnabled(featureRequested = false, "foobar"))
      assertFalse(Version.instance.isPEFeatureEnabled(featureRequested = true, "foobar"))
    }
  }

  @Test def versionExpired(): Unit =  {
    assertFalse(PEVersion.isVersionExpired("3.8", "3.8"))
    assertFalse(PEVersion.isVersionExpired("3.8.1", "3.8"))
    assertFalse(PEVersion.isVersionExpired("3.8.0", "3.8"))
    assertFalse(PEVersion.isVersionExpired("3.8.2", "3.8"))
    assertFalse(PEVersion.isVersionExpired("3.8.10", "3.8"))
    assertFalse(PEVersion.isVersionExpired("3.8.foo.bar", "3.8"))

    assertFalse(PEVersion.isVersionExpired("3.7", "3.8"))
    assertFalse(PEVersion.isVersionExpired("3.7.1", "3.8"))
    assertFalse(PEVersion.isVersionExpired("3.7.0", "3.8"))
    assertFalse(PEVersion.isVersionExpired("3.7.2", "3.8"))
    assertFalse(PEVersion.isVersionExpired("3.7.10", "3.8"))
    assertFalse(PEVersion.isVersionExpired("3.7.foo.bar", "3.8"))

    assertFalse(PEVersion.isVersionExpired("2.0", "3.8"))
    assertFalse(PEVersion.isVersionExpired("2.0.1", "3.8"))
    assertFalse(PEVersion.isVersionExpired("2.0.0", "3.8"))
    assertFalse(PEVersion.isVersionExpired("2.0.2", "3.8"))
    assertFalse(PEVersion.isVersionExpired("2.0.10", "3.8"))
    assertFalse(PEVersion.isVersionExpired("2.0.foo.bar", "3.8"))

    assertTrue(PEVersion.isVersionExpired("3.9", "3.8"))
    assertTrue(PEVersion.isVersionExpired("3.9.1", "3.8"))
    assertTrue(PEVersion.isVersionExpired("3.9.0", "3.8"))
    assertTrue(PEVersion.isVersionExpired("3.9.2", "3.8"))
    assertTrue(PEVersion.isVersionExpired("3.9.10", "3.8"))
    assertTrue(PEVersion.isVersionExpired("3.9.foo.bar", "3.8"))

    assertTrue(PEVersion.isVersionExpired("4.0", "3.8"))
    assertTrue(PEVersion.isVersionExpired("4.0.1", "3.8"))
    assertTrue(PEVersion.isVersionExpired("4.0.0", "3.8"))
    assertTrue(PEVersion.isVersionExpired("4.0.2", "3.8"))
    assertTrue(PEVersion.isVersionExpired("4.0.10", "3.8"))
    assertTrue(PEVersion.isVersionExpired("4.0.foo.bar", "3.8"))
  }

  @Test def dateFromVersionNumber(): Unit = {

    // Make sure the current build has a timestamp
    assert(PEVersion.dateFromVersionNumber(Version.VersionNumber).isDefined)

    val TimeStamp = Some(DateUtils.parseISODateOrDateTime("2013-01-28"))

    val expected = Seq(
      "201301281947"                → TimeStamp,
      "4.1.0.201301281947"          → TimeStamp,
      "4.1.0.201301281947.42"       → TimeStamp,
      "prefix.201301281947.suffix"  → TimeStamp,
      "prefix201301281947suffix"    → TimeStamp,
      "2013012819478"               → None,
      "20130128"                    → None
    )

    expected foreach { case (in, out) ⇒
      assert(out === PEVersion.dateFromVersionNumber(in))
    }
  }

  @Test def licenseInfo(): Unit = {

    def create(version: Option[String], expiration: Option[Long], subscriptionEnd: Option[Long]) =
      LicenseInfo("4.1.0.201301281947", "Orbeon", "Daffy Duck", "Acme", "daffy@orbeon.com", "2013-03-29", version, expiration, subscriptionEnd)

    def parse(s: String) = Some(DateUtils.parseISODateOrDateTime(s))

    case class Booleans(badVersion: Boolean, expired: Boolean, buildAfterSubscriptionEnd: Boolean)

    val expected = Seq(
      (Some("4.0"), None, None)         → Booleans(badVersion = true,  expired = false, buildAfterSubscriptionEnd = false),
      (None, parse("3000-01-01"), None) → Booleans(badVersion = false, expired = false, buildAfterSubscriptionEnd = false),
      (None, parse("1000-01-01"), None) → Booleans(badVersion = false, expired = true,  buildAfterSubscriptionEnd = false),
      (None, None, parse("3000-01-01")) → Booleans(badVersion = false, expired = false, buildAfterSubscriptionEnd = false),
      (None, None, parse("2013-01-28")) → Booleans(badVersion = false, expired = false, buildAfterSubscriptionEnd = false),
      (None, None, parse("2013-01-27")) → Booleans(badVersion = false, expired = false, buildAfterSubscriptionEnd = true)
    )

    expected foreach { case (in, out) ⇒
      val license = (create _).tupled(in)

      assert(out.badVersion                === license.isBadVersion)
      assert(out.expired                   === license.isExpired)
      assert(out.buildAfterSubscriptionEnd === license.isBuildAfterSubscriptionEnd)
    }
  }

  private def tryLicenseInfo(license: Document) = (
    Try(license)
    flatMap tryGetSignedData
    flatMap LicenseInfo.tryApply
  )

  @Test def invalidSignature(): Unit = {

    // A few licenses with invalid signatures
    val licenses: Seq[(Document, Class[_])] = Seq(
      // Document contains the signature of some random value hopefully forever lost
      elemToDocument(
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
        </signed-data>) → classOf[SignatureException],
      // Document contains empty signature
      elemToDocument(
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
        </signed-data>) → classOf[SignatureException],
      // Document contains signature not in Base64
      elemToDocument(
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
        </signed-data>) → classOf[SchemaValidationException]
    )

    licenses foreach { case (license, expectedClass) ⇒

      val thrown = intercept[Exception] {
        tryLicenseInfo(license).rootFailure.get
      }

      assert(expectedClass === thrown.getClass)
    }
  }

  @Test def badVersion(): Unit = {

    // License is for old version
    val license: Document =
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
      </signed-data>

    assert(tryLicenseInfo(license).get.isBadVersion)
  }

  @Test def buildAfterSubscriptionEnd(): Unit = {

    // License has subscription ending way in the past
    val license: Document =
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
      </signed-data>

    assert(tryLicenseInfo(license).get.isBuildAfterSubscriptionEnd)
  }

  @Test def expired(): Unit = {

    // License is expired
    val license: Document =
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
      </signed-data>

    assert(tryLicenseInfo(license).get.isExpired)
  }
}
