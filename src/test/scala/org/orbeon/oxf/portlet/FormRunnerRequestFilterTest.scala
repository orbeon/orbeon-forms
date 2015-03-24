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

import java.util.Arrays
import javax.portlet.PortletRequest
import javax.portlet.filter.PortletRequestWrapper

import com.liferay.portal.model.{Group, Role, User}
import org.junit.Test
import org.mockito.Mockito
import org.orbeon.oxf.fr.FormRunnerAuth._
import org.orbeon.oxf.portlet.liferay.FormRunnerAuthFilter
import org.orbeon.oxf.test.ResourceManagerTestBase
import org.scalatest.junit.AssertionsForJUnit
import org.scalatest.mock.MockitoSugar

import scala.collection.JavaConversions._
import scala.collection.immutable.TreeMap

class FormRunnerRequestFilterTest extends ResourceManagerTestBase with AssertionsForJUnit with MockitoSugar {

    @Test def amendPortletRequest() {

        // Initial properties
        val initialProperties = Map("p1" → Seq("v1a", "v1b"))

        // Request with initial properties
        val mockRequest = new PortletRequestWrapper(mock[PortletRequest]) {
            override def getProperty(name: String) = initialProperties.get(name) map (_.head) orNull
            override def getProperties(name: String) =
                asJavaEnumeration(initialProperties.get(name) map (_.iterator) getOrElse Iterator.empty)
            override def getPropertyNames = initialProperties.keysIterator
        }

        val mockRoleManager = mock[Role]
        Mockito when mockRoleManager.getName  thenReturn "manager"

        val mockRoleEmployee = mock[Role]
        Mockito when mockRoleEmployee.getName thenReturn "employee"

        val mockGroup = mock[Group]
        Mockito when mockGroup.getGroupId     thenReturn 42
        Mockito when mockGroup.getName        thenReturn "universe"

        val mockUser = mock[User]
        Mockito when mockUser.getUserId       thenReturn 123
        Mockito when mockUser.getScreenName   thenReturn "jsmith"
        Mockito when mockUser.getFullName     thenReturn "John Smith"
        Mockito when mockUser.getEmailAddress thenReturn "test@orbeon.com"
        Mockito when mockUser.getRoles        thenReturn Arrays.asList(mockRoleManager, mockRoleEmployee)
        Mockito when mockUser.getGroup        thenReturn mockGroup

        val amendedRequest = FormRunnerAuthFilter.amendRequest(mockRequest, mockUser)

        // NOTE: Use Seq or List but not Array for comparison, because Array's == doesn't work as expected in Scala
        val expectedProperties = initialProperties ++ Map(
            "orbeon-liferay-user-id"          → Seq("123"),
            "orbeon-liferay-user-screen-name" → Seq("jsmith"),
            "orbeon-liferay-user-full-name"   → Seq("John Smith"),
            "orbeon-liferay-user-email"       → Seq("test@orbeon.com"),
            "orbeon-liferay-user-group-id"    → Seq("42"),
            "orbeon-liferay-user-group-name"  → Seq("universe"),
            "orbeon-liferay-user-roles"       → Seq("manager", "employee"),
            OrbeonUsernameHeaderName          → Seq("test@orbeon.com"),
            OrbeonGroupHeaderName             → Seq("universe"),
            OrbeonRolesHeaderName             → Seq("manager", "employee")
        )

        val actualProperties = amendedRequest.getPropertyNames map (n ⇒ n → amendedRequest.getProperties(n).toList) toMap

        // Compare using TreeMap to get a reliable order
        def toTreeMap[K, V](map: Map[K, V])(implicit ord: Ordering[K]) = TreeMap[K, V]() ++ map

        assert(toTreeMap(expectedProperties) === toTreeMap(actualProperties))
   }
}