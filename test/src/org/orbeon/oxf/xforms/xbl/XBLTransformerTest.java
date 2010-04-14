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
package org.orbeon.oxf.xforms.xbl;

import junit.framework.TestCase;

public class XBLTransformerTest extends TestCase {

    public void testCSSToXPath() {

        assertEquals("descendant-or-self::foo:a", XBLTransformer.cssToXPath("foo|a"));
        assertEquals("descendant-or-self::foo:a//foo:b", XBLTransformer.cssToXPath("foo|a foo|b"));
        assertEquals("descendant-or-self::foo:a//foo:b|descendant-or-self::bar:a//bar:b",
                XBLTransformer.cssToXPath("foo|a foo|b, bar|a bar|b"));

        assertEquals("descendant-or-self::foo:a/foo:b", XBLTransformer.cssToXPath("foo|a > foo|b"));
        assertEquals("descendant-or-self::foo:a/foo:b|descendant-or-self::bar:a/bar:b",
                XBLTransformer.cssToXPath("foo|a > foo|b, bar|a > bar|b"));
    }
}
