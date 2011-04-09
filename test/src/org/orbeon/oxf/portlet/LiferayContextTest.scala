/**
 * Copyright (C) 2011 Orbeon, Inc.
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
package org.orbeon.oxf.portlet

import org.scalatest.junit.AssertionsForJUnit
import org.orbeon.oxf.test.ResourceManagerTestBase
import org.junit.Test
import java.lang.String
import scala.collection.JavaConversions._

class LiferayContextTest extends ResourceManagerTestBase with AssertionsForJUnit {

    @Test def testAmendRequest() {

        val request = new MockPortletRequest {

            val attributes = collection.mutable.Map[String, AnyRef](("a1" -> "v1"))
            val properties = collection.mutable.Map(("p1" -> Seq("v1a", "v1b")))

            override def getAttribute(name: String) = attributes.get(name) orNull
            override def getAttributeNames = attributes.keysIterator
            override def setAttribute(name: String, value: AnyRef) { attributes += (name -> value) }

            override def getProperty(name: String) = properties.get(name) map (_.head) orNull
            override def getProperties(name: String) =
                asJavaEnumeration(properties.get(name) map (_.iterator) getOrElse Iterator.empty)
            override def getPropertyNames = properties.keysIterator
        }

        val amendedRequest = (new LiferayContext).amendRequest(request, new MockUser {
            override def getEmailAddress = "test@orbeon.com"
            override def getFullName = "John Smith"
        })

        val expectedAttributes = Map(
            ("a1" -> "v1"),
            ("orbeon.liferay.user.email" -> "test@orbeon.com"),
            ("orbeon.liferay.user.full-name" -> "John Smith")
        )

        val expectedProperties = Map(
            ("p1" -> Seq("v1a", "v1b")),
            ("orbeon-liferay-user-email" -> Seq("test@orbeon.com")),
            ("orbeon-liferay-user-full-name" -> Seq("John Smith"))
        )

        val actualAttributes = amendedRequest.getAttributeNames map (n => (n -> amendedRequest.getAttribute(n))) toMap
        val actualProperties = amendedRequest.getPropertyNames map (n => (n -> amendedRequest.getProperties(n).toSeq)) toMap

        assert(expectedAttributes === actualAttributes)
        assert(expectedProperties === actualProperties)
    }
}