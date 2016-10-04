/**
 * Copyright (C) 2016 Orbeon, Inc.
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
package org.orbeon.oxf.fr

import org.junit.Test
import org.orbeon.oxf.test.{ResourceManagerTestBase, XMLSupport}
import org.orbeon.oxf.util.Logging
import org.scalatest.junit.AssertionsForJUnit
import org.orbeon.oxf.fr.FormRunner.Permissions._
import org.orbeon.scaxon.XML._

class PermissionsTest extends ResourceManagerTestBase with AssertionsForJUnit with XMLSupport with Logging {

  @Test def basedOnRoles(): Unit = {

    // If the form has no permissions, then we can perform all operations
    assert(FormRunner.authorizedOperationsBasedOnRoles(null) === List("*"))

    // With the "clerk can read" permission, check the a clerk actually can read, and another user can't
    val clerkCanRead = serialize(Some(Seq(Permission(Role("clerk"), Set("read"))))).get
    assert(FormRunner.authorizedOperationsBasedOnRoles(clerkCanRead, List("clerk" )) === List("read"))
    assert(FormRunner.authorizedOperationsBasedOnRoles(clerkCanRead, List("intake")) === List())

  }

  @Test def basedOnData(): Unit = {

  }
}
