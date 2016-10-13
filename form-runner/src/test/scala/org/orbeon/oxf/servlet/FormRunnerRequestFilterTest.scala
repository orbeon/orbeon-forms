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

import java.{util ⇒ ju}
import javax.servlet.http.{HttpServletRequest, HttpServletRequestWrapper, HttpSession}

import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer
import org.mockito.{Matchers, Mockito}
import org.orbeon.oxf.http.Headers
import org.scalatest.FunSpec
import org.scalatest.mock.MockitoSugar

import scala.collection.JavaConversions._
import scala.collection.immutable.TreeMap
import scala.collection.mutable

class FormRunnerRequestFilterTest extends FunSpec with MockitoSugar {

  describe("The `amendRequest()` function") {

    // Initial headers
    val initialHeaders = Map(
      "p1" → Seq("v1a", "v1b"),
      // NOTE: Just use these header names because that's what's configured in the properties for the Liferay test
      "Orbeon-Liferay-User-Email".toLowerCase → Seq("test@orbeon.com"),
      "Orbeon-Liferay-User-Roles".toLowerCase → Seq("manager,employee")
    )

    val sessionAttributes = mutable.Map[String, AnyRef]()
    val mockSession = mock[HttpSession]
    Mockito when mockSession.getAttribute(Matchers.anyString) thenAnswer new Answer[AnyRef] {
      def answer(invocation: InvocationOnMock) =
        sessionAttributes.get(invocation.getArguments()(0).asInstanceOf[String]).orNull
    }
    Mockito when mockSession.setAttribute(Matchers.anyString, Matchers.anyObject) thenAnswer new Answer[Unit] {
      def answer(invocation: InvocationOnMock) =
        sessionAttributes += invocation.getArguments()(0).asInstanceOf[String] → invocation.getArguments()(1)
    }

    // Request with initial headers
    val mockRequest = new HttpServletRequestWrapper(mock[HttpServletRequest]) {
      override def getHeader(name: String) = initialHeaders.get(name) map (_.head) orNull
      override def getHeaders(name: String) =
        asJavaEnumeration(initialHeaders.get(name) map (_.iterator) getOrElse Iterator.empty)
      override def getHeaderNames= initialHeaders.keysIterator
      override def getSession(create: Boolean) = mockSession
    }

    val amendedRequest = FormRunnerAuthFilter.amendRequest(mockRequest)

    // NOTE: Use Seq or List but not Array for comparison, because Array's == doesn't work as expected in Scala
    val expectedHeaders = initialHeaders ++ Map(
      Headers.OrbeonUsernameLower → Seq("test@orbeon.com"),
      Headers.OrbeonRolesLower    → Seq("manager", "employee")
    )

    // NOTE: Use asInstanceOf because servlet API doesn't have generics
    val actualHeaders = amendedRequest.getHeaderNames.asInstanceOf[ju.Enumeration[String]] map
      (n ⇒ n → amendedRequest.getHeaders(n).asInstanceOf[ju.Enumeration[String]].toList) toMap

    // Compare using TreeMap to get a reliable order
    def toTreeMap[K, V](map: Map[K, V])(implicit ord: Ordering[K]) = TreeMap[K, V]() ++ map

    it ("must set authentication headers based on incoming headers") {
      assert(toTreeMap(expectedHeaders) === toTreeMap(actualHeaders))
    }
  }
}