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
import org.orbeon.oxf.fr.FormRunner.Permissions._
import org.orbeon.oxf.test.{ResourceManagerTestBase, XMLSupport}
import org.orbeon.oxf.util.Logging
import org.orbeon.scaxon.XML._
import org.scalatest.junit.AssertionsForJUnit
import org.orbeon.oxf.util.CoreUtils._

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

    // Pretty typical permissions defined in the form
    val formPermissions = serialize(Some(Seq(
      Permission(Anyone         , Set("create")),
      Permission(Owner          , Set("read", "update")),
      Permission(Role("clerk")  , Set("read")),
      Permission(Role("manager"), Set("read", "update"))
    ))).get

    // Anonymous users can only create
    FormRunner
      .allAuthorizedOperations(
        permissionsElement  = formPermissions,
        dataUsername        = None,
        dataGroupname       = None,
        dataOrganization    = None,
        currentUsername     = Some("jsmith"),
        currentGroupname    = None,
        currentOrganization = None)
      .kestrel{ ops ⇒ assert(ops === List("create"))}

    // The owner can also read and update
    FormRunner
      .allAuthorizedOperations(
        permissionsElement  = formPermissions,
        dataUsername        = Some("jsmith"),
        dataGroupname       = None,
        dataOrganization    = None,
        currentUsername     = Some("jsmith"),
        currentGroupname    = None,
        currentOrganization = None)
      .kestrel{ ops ⇒ assert(ops === List("create", "read", "update"))}

  }
}
