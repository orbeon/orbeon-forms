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
package org.orbeon.oxf.portlet.liferay

import java.{util ⇒ ju}
import javax.portlet.filter.PortletRequestWrapper
import javax.portlet.{PortletRequest, PortletSession}

import org.junit.Test
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer
import org.mockito.{Matchers, Mockito}
import org.orbeon.oxf.http.Headers
import org.orbeon.oxf.portlet.liferay.LiferayAPI.RoleFacade
import org.orbeon.oxf.test.ResourceManagerTestBase
import org.scalatest.junit.AssertionsForJUnit
import org.scalatest.mock.MockitoSugar

import scala.collection.JavaConversions._
import scala.collection.immutable.TreeMap
import scala.collection.mutable

class FormRunnerRequestFilterTest extends ResourceManagerTestBase with AssertionsForJUnit with MockitoSugar {

  @Test def amendPortletRequest(): Unit = {

    // Initial properties
    val initialProperties = Map("p1" → Seq("v1a", "v1b"))

    // Session
    val sessionAttributes = mutable.Map[String, AnyRef]()
    val mockSession = mock[PortletSession]
    Mockito when mockSession.getAttribute(Matchers.anyString) thenAnswer new Answer[AnyRef] {
      def answer(invocation: InvocationOnMock) =
        sessionAttributes.get(invocation.getArguments()(0).asInstanceOf[String]).orNull
    }
    Mockito when mockSession.setAttribute(Matchers.anyString, Matchers.anyObject) thenAnswer new Answer[Unit] {
      def answer(invocation: InvocationOnMock) =
        sessionAttributes += invocation.getArguments()(0).asInstanceOf[String] → invocation.getArguments()(1)
    }

    // Request with initial properties
    val mockRequest = new PortletRequestWrapper(mock[PortletRequest]) {
      override def getProperty(name: String) = initialProperties.get(name) map (_.head) orNull
      override def getProperties(name: String) =
        asJavaEnumeration(initialProperties.get(name) map (_.iterator) getOrElse Iterator.empty)
      override def getPropertyNames = initialProperties.keysIterator
      override def getPortletSession = mockSession
      override def getPortletSession(create: Boolean) = mockSession
    }

    class MyGroup {
      def getGroupId         = 42L
      def getName            = "universe"
      def getDescriptiveName = getName
    }

    case class MyRole(
      getName             : String
    ) {
      def getType           = LiferayAPI.LiferayRegularRoleType.value
      override def toString = s"MyRole($getName)"
    }

    class MyUser {
      def getUserId       = 123L
      def getScreenName   = "jsmith"
      def getFullName     = "John Smith"
      def getEmailAddress = "test@orbeon.com"
      def getGroup        = new MyGroup
      def getRoles        = ju.Arrays.asList(MyRole("manager"): RoleFacade, MyRole("employee"): RoleFacade)
    }

    import org.orbeon.oxf.portlet.liferay.FormRunnerAuthFilter._

    val amendedRequest =
      wrapWithOrbeonAuthHeaders(wrapWithLiferayUserHeaders(mockRequest, new LiferayUser {
        override def userHeaders = LiferaySupport.userHeaders(new MyUser, tests = true)
      }))

    val expectedProperties =
      initialProperties ++ Map(
        "orbeon-liferay-user-id"          → List("123"),
        "orbeon-liferay-user-screen-name" → List("jsmith"),
        "orbeon-liferay-user-full-name"   → List("John Smith"),
        "orbeon-liferay-user-email"       → List("test@orbeon.com"),
        "orbeon-liferay-user-group-id"    → List("42"),
        "orbeon-liferay-user-group-name"  → List("universe"),
        "orbeon-liferay-user-roles"       → List("manager", "employee"),
        Headers.OrbeonUsernameLower       → List("test@orbeon.com"),
        Headers.OrbeonGroupLower          → List("universe"),
        Headers.OrbeonRolesLower          → List("manager", "employee")
      )

    // NOTE: Don't use Array for comparison, because Array's == doesn't work as expected in Scala
    val actualProperties =
      amendedRequest.getPropertyNames map (n ⇒ n → amendedRequest.getProperties(n).toList) toMap

    // Compare using TreeMap to get a reliable order
    def toTreeMap[K, V](map: Map[K, V])(implicit ord: Ordering[K]) = TreeMap[K, V]() ++ map

    assert(toTreeMap(expectedProperties) === toTreeMap(actualProperties))
   }
}