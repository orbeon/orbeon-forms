/**
 *  Copyright (C) 2013 Orbeon, Inc.
 *
 *  This program is free software; you can redistribute it and/or modify it under the terms of the
 *  GNU Lesser General Public License as published by the Free Software Foundation; either version
 *  2.1 of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU Lesser General Public License for more details.
 *
 *  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.client.builder

import org.scalatest.junit.AssertionsForJUnit
import org.orbeon.oxf.client.FormBuilderOps
import org.junit.Test

trait Permissions extends AssertionsForJUnit with FormBuilderOps {

    private def permissionSelector(selector: String) = cssSelector(".fb-permissions-dialog " + selector)
    private def role(line: Int) = textField(permissionSelector(".fb-role-name[id $= '" + line + "'] input")) // XXX
    private def checkbox(line: Int, crud: String): Checkbox = checkbox(permissionSelector(".fb-" + crud + "-permission[id $= '" + line + "'] input")) // XXX

    private val OpenPermissions  = cssSelector("#fb-permissions-button button")
    private val HasPermissions   = permissionSelector(".fb-has-permissions input")
    private val AddPermission    = permissionSelector(".fb-add-permission a")
    private val Apply            = permissionSelector("[id $= 'save-trigger']")

    @Test def createClerkAdminPermissions(): Unit = {
        Builder.onNewForm {

            // Enable permissions
            click on OpenPermissions
            checkbox(HasPermissions.displayed).select()

            // Clerks can read
            click on AddPermission.clickable
            role(2).value = "clerk"
            checkbox(2, "read").select()

            // Admins can do everything
            click on AddPermission
            role(3).value = "admin"
            checkbox(3, "update").select()
            waitForAjaxResponse()
            checkbox(3, "read") should be ('selected)  // Read auto-selected when selecting update
            checkbox(3, "delete").select()

            // Everyone can create
            checkbox(1, "create").select()
            // Read auto-selected when selecting update
            checkbox(2, "read") should be ('selected)
            checkbox(3, "read") should be ('selected)

            // Save, reopen, and check the permissions are correct
            click on Apply
            waitForAjaxResponse()
            click on OpenPermissions
            waitForAjaxResponse()
            checkbox(HasPermissions) should be ('selected)
            // Roles are re-ordered by alphabetic order, see #917
            checkbox(1, "create") should     be ('selected)
            checkbox(1, "read"  ) should not be ('selected)
            checkbox(1, "update") should not be ('selected)
            checkbox(1, "delete") should not be ('selected)
            role(2).value should be ("admin")
            checkbox(2, "create") should be     ('selected)
            checkbox(2, "read"  ) should be     ('selected)
            checkbox(2, "update") should be     ('selected)
            checkbox(2, "delete") should be     ('selected)
            role(3).value should be ("clerk")
            checkbox(3, "create") should be     ('selected)
            checkbox(3, "read"  ) should be     ('selected)
            checkbox(3, "update") should not be ('selected)
            checkbox(3, "delete") should not be ('selected)

            // Done, close dialog
            click on Apply
            waitForAjaxResponse()
        }
    }
}
