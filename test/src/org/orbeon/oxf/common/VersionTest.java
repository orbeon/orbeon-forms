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
package org.orbeon.oxf.common;

import org.junit.Test;
import org.orbeon.oxf.test.ResourceManagerTestBase;

import static junit.framework.Assert.*;

public class VersionTest extends ResourceManagerTestBase {

    @Test
    public void productConfiguration() {
        if (Version.isPE()) {
            assertEquals("PE", Version.getEdition());
            assertTrue(Version.isPE());
            assertFalse(Version.instance().isPEFeatureEnabled(false, "foobar"));
            assertTrue(Version.instance().isPEFeatureEnabled(true, "foobar"));
        } else {
            assertEquals("CE", Version.getEdition());
            assertFalse(Version.isPE());
            assertFalse(Version.instance().isPEFeatureEnabled(false, "foobar"));
            assertFalse(Version.instance().isPEFeatureEnabled(true, "foobar"));
        }
    }

    @Test
    public void versionExpired() {
        assertFalse(PEVersion.isVersionExpired("3.8", "3.8"));
        assertFalse(PEVersion.isVersionExpired("3.8.1", "3.8"));
        assertFalse(PEVersion.isVersionExpired("3.8.0", "3.8"));
        assertFalse(PEVersion.isVersionExpired("3.8.2", "3.8"));
        assertFalse(PEVersion.isVersionExpired("3.8.10", "3.8"));
        assertFalse(PEVersion.isVersionExpired("3.8.foo.bar", "3.8"));

        assertFalse(PEVersion.isVersionExpired("3.7", "3.8"));
        assertFalse(PEVersion.isVersionExpired("3.7.1", "3.8"));
        assertFalse(PEVersion.isVersionExpired("3.7.0", "3.8"));
        assertFalse(PEVersion.isVersionExpired("3.7.2", "3.8"));
        assertFalse(PEVersion.isVersionExpired("3.7.10", "3.8"));
        assertFalse(PEVersion.isVersionExpired("3.7.foo.bar", "3.8"));

        assertFalse(PEVersion.isVersionExpired("2.0", "3.8"));
        assertFalse(PEVersion.isVersionExpired("2.0.1", "3.8"));
        assertFalse(PEVersion.isVersionExpired("2.0.0", "3.8"));
        assertFalse(PEVersion.isVersionExpired("2.0.2", "3.8"));
        assertFalse(PEVersion.isVersionExpired("2.0.10", "3.8"));
        assertFalse(PEVersion.isVersionExpired("2.0.foo.bar", "3.8"));

        assertTrue(PEVersion.isVersionExpired("3.9", "3.8"));
        assertTrue(PEVersion.isVersionExpired("3.9.1", "3.8"));
        assertTrue(PEVersion.isVersionExpired("3.9.0", "3.8"));
        assertTrue(PEVersion.isVersionExpired("3.9.2", "3.8"));
        assertTrue(PEVersion.isVersionExpired("3.9.10", "3.8"));
        assertTrue(PEVersion.isVersionExpired("3.9.foo.bar", "3.8"));

        assertTrue(PEVersion.isVersionExpired("4.0", "3.8"));
        assertTrue(PEVersion.isVersionExpired("4.0.1", "3.8"));
        assertTrue(PEVersion.isVersionExpired("4.0.0", "3.8"));
        assertTrue(PEVersion.isVersionExpired("4.0.2", "3.8"));
        assertTrue(PEVersion.isVersionExpired("4.0.10", "3.8"));
        assertTrue(PEVersion.isVersionExpired("4.0.foo.bar", "3.8"));
    }
}
