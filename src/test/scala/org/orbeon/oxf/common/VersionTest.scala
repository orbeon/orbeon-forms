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

import org.junit.Test
import org.orbeon.oxf.test.ResourceManagerTestBase
import junit.framework.Assert._
import org.scalatest.junit.AssertionsForJUnit
import org.orbeon.oxf.common.PEVersion.LicenseInfo
import org.orbeon.oxf.util.DateUtils

class VersionTest extends ResourceManagerTestBase with AssertionsForJUnit {

    @Test def productConfiguration(): Unit = {
        if (Version.isPE) {
            assertEquals("PE", Version.Edition)
            assertTrue(Version.isPE)
            assertFalse(Version.instance.isPEFeatureEnabled(false, "foobar"))
            assertTrue(Version.instance.isPEFeatureEnabled(true, "foobar"))
        } else {
            assertEquals("CE", Version.Edition)
            assertFalse(Version.isPE)
            assertFalse(Version.instance.isPEFeatureEnabled(false, "foobar"))
            assertFalse(Version.instance.isPEFeatureEnabled(true, "foobar"))
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

        val TimeStamp = Some(1359356400000L)

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
            (assert(out === PEVersion.dateFromVersionNumber(in)))
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
}