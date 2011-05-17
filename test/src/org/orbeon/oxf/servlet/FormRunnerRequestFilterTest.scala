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
package org.orbeon.oxf.servlet

import org.orbeon.oxf.test.ResourceManagerTestBase
import org.scalatest.junit.AssertionsForJUnit
import org.scalatest.mock.MockitoSugar
import org.junit.Test
import javax.servlet.http.{HttpServletRequest, HttpServletRequestWrapper}
import collection.JavaConversions._
import collection.immutable.TreeMap
import java.util.{Enumeration => JEnumeration}

class FormRunnerRequestFilterTest extends ResourceManagerTestBase with AssertionsForJUnit with MockitoSugar {

    @Test def testAmendServletRequest() {

        // Initial headers
        val initialHeaders = Map(
            "p1" -> Seq("v1a", "v1b"),
            // NOTE: Just use these header names because that's what's configured in the properties for the Liferay test
            "Orbeon-Liferay-User-Email".toLowerCase -> Seq("test@orbeon.com"),
            "Orbeon-Liferay-User-Roles".toLowerCase -> Seq("manager,employee")
        )

        // Request with initial headers
        val mockRequest = new HttpServletRequestWrapper(mock[HttpServletRequest]) {
            override def getHeader(name: String) = initialHeaders.get(name) map (_.head) orNull
            override def getHeaders(name: String) =
                asJavaEnumeration(initialHeaders.get(name) map (_.iterator) getOrElse Iterator.empty)
            override def getHeaderNames= initialHeaders.keysIterator
        }

        val amendedRequest = (new FormRunnerRequestFilter).amendRequest(mockRequest)

        // NOTE: Use Seq or List but not Array for comparison, because Array's == doesn't work as expected in Scala
        val expectedHeaders = initialHeaders ++ Map(
            "orbeon-username" -> Seq("test@orbeon.com"),
            "orbeon-roles" -> Seq("manager", "employee")
        )

        // NOTE: Use asInstanceOf because servlet API doesn't have generics
        val actualHeaders = amendedRequest.getHeaderNames.asInstanceOf[JEnumeration[String]] map
            (n => n -> amendedRequest.getHeaders(n).asInstanceOf[JEnumeration[String]].toList) toMap

        // Compare using TreeMap to get a reliable order
        def toTreeMap[K, V](map: Map[K, V])(implicit ord: Ordering[K]) = TreeMap[K, V]() ++ map

        assert(toTreeMap(expectedHeaders) === toTreeMap(actualHeaders))
    }
}