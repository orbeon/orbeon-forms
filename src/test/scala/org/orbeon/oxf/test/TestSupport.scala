/**
 * Copyright (C) 2013 Orbeon, Inc.
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
package org.orbeon.oxf.test

import org.dom4j.{Document ⇒ JDocument, Element ⇒ JElement}
import org.orbeon.oxf.xml.Dom4j
import org.orbeon.oxf.xml.dom4j.Dom4jUtils
import org.scalatest.junit.AssertionsForJUnit

trait TestSupport extends AssertionsForJUnit {
    def assertXMLDocumentsIgnoreNamespacesInScope(left: JDocument, right: JDocument) = {
        val result = Dom4j.compareDocumentsIgnoreNamespacesInScope(left, right)

        // So that we get a nicer message
        if (! result) {
            assert(Dom4jUtils.domToPrettyString(left) === Dom4jUtils.domToPrettyString(right))
            assert(condition = false)
        }
    }

    def assertXMLElementsIgnoreNamespacesInScopeCollapse(left: JElement, right: JElement) = {
        val result = Dom4j.compareElementsIgnoreNamespacesInScopeCollapse(left, right)

        // So that we get a nicer message
        if (! result) {
            assert(Dom4jUtils.domToPrettyString(left) === Dom4jUtils.domToPrettyString(right))
            assert(condition = false)
        }
    }
}
