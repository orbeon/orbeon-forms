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

import org.mockito.invocation.InvocationOnMock
import org.mockito.{ArgumentMatchers, Mockito}
import org.orbeon.oxf.http.Headers
import org.orbeon.oxf.portlet.liferay.LiferayAPI.RoleFacade
import org.orbeon.oxf.test.ResourceManagerSupport
import org.scalatest.funspec.AnyFunSpecLike
import org.scalatestplus.mockito.MockitoSugar

import java.{util => ju}
import javax.portlet.filter.PortletRequestWrapper
import javax.portlet.{PortletRequest, PortletSession}
import scala.collection.immutable.TreeMap
import scala.collection.mutable
import scala.jdk.CollectionConverters._


class FormRunnerRequestFilterTest extends ResourceManagerSupport with AnyFunSpecLike with MockitoSugar {

  describe("The portlet filter's `amendRequest()` function") {

    // Initial properties
    val initialProperties = Map("p1" -> List("v1a", "v1b"))

    // Session
    val sessionAttributes = mutable.Map[String, AnyRef]()
    val mockSession = mock[PortletSession]
    Mockito when mockSession.getAttribute(ArgumentMatchers.anyString) thenAnswer
      ((invocation: InvocationOnMock) => sessionAttributes.get(invocation.getArguments()(0).asInstanceOf[String]).orNull)
    Mockito when mockSession.setAttribute(ArgumentMatchers.anyString, ArgumentMatchers.any) thenAnswer
      ((invocation: InvocationOnMock) => sessionAttributes += invocation.getArguments()(0).asInstanceOf[String] -> invocation.getArguments()(1))

    // Request with initial properties
    val mockRequest = new PortletRequestWrapper(mock[PortletRequest]) {
      override def getProperty(name: String) = initialProperties.get(name) map (_.head) orNull
      override def getProperties(name: String) =
        (initialProperties.get(name) map (_.iterator) getOrElse Iterator.empty).asJavaEnumeration
      override def getPropertyNames = initialProperties.keysIterator.asJavaEnumeration
      override def getPortletSession = mockSession
      override def getPortletSession(create: Boolean) = mockSession
    }

    class MyGroup {
      def getGroupId         = 42L
      def getName            = "universe"
      def getDescriptiveName = getName
    }

    case class MyRole(getName: String) {
      def getType             = LiferayAPI.LiferayRegularRoleType.value
      override def toString() = s"MyRole($getName)"
    }

    class MyUser {
      def getUserId       = 123L
      def getScreenName   = "jsmith"
      def getFullName     = "John Paul Smith"
      def getFirstName    = "John"
      def getMiddleName   = "Paul"
      def getLastName     = "Smith"
      def getEmailAddress = "test@orbeon.com"
      def getGroup        = new MyGroup
      def getRoles        = ju.Arrays.asList(MyRole("manager"): RoleFacade, MyRole("employee"): RoleFacade)
    }

    class MyCompany {
      def getAuthType     = LiferayAPI.LiferayEmailAddressAuthType.name
    }

    import org.orbeon.oxf.portlet.liferay.FormRunnerAuthFilter._

    val amendedRequest =
      wrapWithOrbeonAuthHeaders(AddLiferayUserHeadersFilter.wrapWithLiferayUserHeaders(mockRequest, new LiferayUser {
        override def userHeaders = LiferaySupport.userHeaders(new MyUser, new MyCompany, tests = true)
      }))

    val expectedProperties =
      initialProperties ++ Map(
        "orbeon-liferay-user-id"          -> List("123"),
        "orbeon-liferay-user-screen-name" -> List("jsmith"),
        "orbeon-liferay-user-full-name"   -> List("John Paul Smith"),
        "orbeon-liferay-user-first-name"  -> List("John"),
        "orbeon-liferay-user-middle-name" -> List("Paul"),
        "orbeon-liferay-user-last-name"   -> List("Smith"),
        "orbeon-liferay-user-email"       -> List("test@orbeon.com"),
        "orbeon-liferay-user-group-id"    -> List("42"),
        "orbeon-liferay-user-group-name"  -> List("universe"),
        "orbeon-liferay-user-roles"       -> List("manager", "employee"),
        Headers.OrbeonUsernameLower       -> List("test@orbeon.com"),
        Headers.OrbeonGroupLower          -> List("universe"),
        Headers.OrbeonRolesLower          -> List("manager", "employee"),
        Headers.OrbeonCredentialsLower    -> List("""{"username":"test%40orbeon.com","groups":["universe"],"roles":[{"name":"manager"},{"name":"employee"}],"organizations":[]}""")
      )

    // NOTE: Don't use Array for comparison, because Array's == doesn't work as expected in Scala
    val actualProperties =
      amendedRequest.getPropertyNames.asScala map (n => n -> amendedRequest.getProperties(n).asScala.toList) toMap

    // Compare using TreeMap to get a reliable order
    def toTreeMap[K, V](map: Map[K, V])(implicit ord: Ordering[K]) = TreeMap[K, V]() ++ map

    it ("must set authentication headers based on incoming headers") {
      assert(toTreeMap(expectedProperties) === toTreeMap(actualProperties))
    }
   }
}