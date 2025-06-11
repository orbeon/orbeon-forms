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

import cats.data.NonEmptyList
import org.mockito.invocation.InvocationOnMock
import org.mockito.{ArgumentMatchers, Mockito}
import org.orbeon.oxf.externalcontext.{Credentials, CredentialsSupport, SimpleRole, UserAndGroup}
import org.orbeon.oxf.http.Headers
import org.orbeon.oxf.test.ResourceManagerSupport
import org.scalatest.funspec.AnyFunSpecLike
import org.scalatestplus.mockito.MockitoSugar

import scala.collection.mutable
import scala.jdk.CollectionConverters.*


class FormRunnerRequestFilterTest extends ResourceManagerSupport with AnyFunSpecLike with MockitoSugar {

  describe("The servlet filter's `amendRequest()` function") {

    val mockSession = {
      val sessionAttributes = mutable.Map[String, AnyRef]()
      val mockSession = mock[HttpSession]
      Mockito when mockSession.getAttribute(ArgumentMatchers.anyString) thenAnswer
        ((invocation: InvocationOnMock) => sessionAttributes.get(invocation.getArguments()(0).asInstanceOf[String]).orNull)
      Mockito when mockSession.setAttribute(ArgumentMatchers.anyString, ArgumentMatchers.any) thenAnswer
        ((invocation: InvocationOnMock) => sessionAttributes += invocation.getArguments()(0).asInstanceOf[String] -> invocation.getArguments()(1))
      mockSession
    }

    def headersFromRequest(req: HttpServletRequest): Map[String, List[String]] =
      req.getHeaderNames.asScala map
        (n => n -> req.headerValuesList(n)) toMap

    // Request with initial headers
    def newAmendedRequest(path: String, headers: Map[String, List[String]]) = {

      val mockRequest =
        new HttpServletRequestWrapper(mock[HttpServletRequest]) {
          override def getHeader(name: String) = headers.get(name) map (_.head) orNull
          override def getHeaders(name: String) =
            (headers.get(name) map (_.iterator) getOrElse Iterator.empty).asJavaEnumeration
          override def getHeaderNames = headers.keysIterator.asJavaEnumeration
          override def getSession(create: Boolean) = mockSession
          override def getPathInfo: String = path
        }

      FormRunnerAuthFilterImpl.amendRequest(mockRequest)
    }

    val testCredentials =
      Credentials(
        UserAndGroup("test@orbeon.com", None),
        List(SimpleRole("manager"), SimpleRole("employee")),
        Nil
      )

    val testOrbeonHeaders = Map(
      Headers.OrbeonCredentialsLower -> List("""{"username":"test%40orbeon.com","groups":[],"roles":[{"name":"manager"},{"name":"employee"}],"organizations":[]}"""),
      Headers.OrbeonUsernameLower    -> List("test@orbeon.com"),
      Headers.OrbeonRolesLower       -> List("manager", "employee")
    )

    it("must set authentication headers based on incoming headers") {

      // Initial headers
      val initialHeaders = Map(
        "p1" -> List("v1a", "v1b"),
        // NOTE: Just use these header names because that's what's configured in the properties for the Liferay test
        "Orbeon-Liferay-User-Email".toLowerCase -> List("test@orbeon.com"),
        "Orbeon-Liferay-User-Roles".toLowerCase -> List("manager,employee")
      )

      val amendedRequest = newAmendedRequest("/fr/orbeon/controls/new", initialHeaders)

      assert((initialHeaders ++ testOrbeonHeaders) == headersFromRequest(amendedRequest))
      assert(CredentialsSupport.findCredentialsInSession(new ServletSessionImpl(mockSession)).contains(testCredentials))
    }

    it("must keep authentication headers for services and store credentials") {

      val amendedRequest = newAmendedRequest("/fr/service/acme", testOrbeonHeaders)

      assert(testOrbeonHeaders == headersFromRequest(amendedRequest))
      assert(CredentialsSupport.findCredentialsInSession(new ServletSessionImpl(mockSession)).contains(testCredentials))
    }
  }

  describe("#6473: `HttpServletRequest` header methods and case-insensitivity") {

    // Simulate a non-compliant implementation of `HttpServletRequest` that is case-sensitive
    def newCaseSensitiveRequest(headersMap: Map[String, List[String]]) =
      new HttpServletRequestWrapper(mock[HttpServletRequest]) {
        override def getHeader(name: String) = headersMap.get(name).map(_.head).orNull
        override def getHeaders(name: String) = headersMap.get(name).map(_.iterator).getOrElse(Iterator.empty).asJavaEnumeration
        override def getHeaderNames = headersMap.keysIterator.asJavaEnumeration
      }

    it("must return the correct header values") {

      val h1 = newCaseSensitiveRequest(
        Map(
          "Content-Type" -> List("application/json"),
        )
      )

      // Call to native method
      assert(h1.getHeader("Content-Type") == "application/json")
      assert(h1.getHeader("content-type") == null)

      // Call using helper
      assert(h1.headerValuesList("Content-Type") == List("application/json"))
      assert(h1.headerValuesList("content-type") == List("application/json"))
    }

    it("must not return empty value lists") {

      val h1 = newCaseSensitiveRequest(
        Map(
          "My-Header-With-Values"    -> List("v1", "v2"),
          "My-Header-Without-Values" -> Nil
        )
      )

      // Entry without values is removed
      assert(h1.headerNamesWithValues.toMap == Map("My-Header-With-Values" -> NonEmptyList.fromListUnsafe(List("v1", "v2"))))

      // Call using helpers
      assert(h1.headerFirstValueOpt("My-Header-With-Values")   .contains("v1"))
      assert(h1.headerFirstValueOpt("My-Header-Without-Values").isEmpty)
      assert(h1.headerValuesList   ("My-Header-With-Values")    == List("v1", "v2"))
      assert(h1.headerValuesList   ("My-Header-Without-Values") == Nil)
      assert(h1.headerValuesNel    ("My-Header-With-Values")   .contains(NonEmptyList.fromListUnsafe(List("v1", "v2"))))
      assert(h1.headerValuesNel    ("My-Header-Without-Values").isEmpty)
    }
  }
}