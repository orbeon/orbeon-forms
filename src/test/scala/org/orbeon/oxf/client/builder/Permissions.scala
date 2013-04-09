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
import org.scalatest.concurrent.Eventually._
import org.orbeon.oxf.common.Version

trait Permissions extends AssertionsForJUnit with FormBuilderOps {

    private def permissionSelector(selector: String) = cssSelector(s".fb-permissions-dialog $selector")
    private def role(line: Int) = permissionSelector(s".fb-role-name[id $$= '$line'] input")
    private def checkbox(line: Int, crud: String): CssSelectorQuery = permissionSelector(s".fb-$crud-permission[id $$= '$line'] input")

    private val OpenPermissions  = cssSelector("#fb-permissions-button button")
    private val HasPermissions   = permissionSelector(".fb-has-permissions input")
    private val AddPermission    = permissionSelector(".fb-add-permission a")
    private val Apply            = permissionSelector("[id $= 'save-trigger']")

    @Test def createClerkAdminPermissions(): Unit = {
        if (Version.isPE) {
            Builder.onNewForm {

                // Enable permissions
                patientlyClick(OpenPermissions)
                patientlyClick(HasPermissions)

                // Clerks can read
                patientlyClick(AddPermission)
                patientlySendKeys(role(2), "clerk")
                patientlyClick(checkbox(2, "read"))

                // Admins can do everything
                patientlyClick(AddPermission)
                patientlySendKeys(role(3), "admin")
                patientlyClick(checkbox(3, "update"))
                eventually { assert(checkbox(checkbox(3, "read")).isSelected) }
                // Read auto-selected when selecting update
                patientlyClick(checkbox(3, "delete"))

                // Everyone can create
                patientlyClick(checkbox(1, "create"))
                // Read auto-selected when selecting update
                eventually {
                    assert(checkbox(checkbox(2, "read")).isSelected)
                    assert(checkbox(checkbox(3, "read")).isSelected)
                }

                // Save, reopen, and check the permissions are correct
                patientlyClick(Apply)
                patientlyClick(OpenPermissions)
                eventually {
                    assert(  checkbox(HasPermissions).isSelected)
                    // Roles are re-ordered by alphabetic order, see #917
                    assert(  checkbox(checkbox(1, "create")).isSelected)
                    assert(! checkbox(checkbox(1, "read"  )).isSelected)
                    assert(! checkbox(checkbox(1, "update")).isSelected)
                    assert(! checkbox(checkbox(1, "delete")).isSelected)
                    assert(textField(role(2)).value === "admin")
                    assert(  checkbox(checkbox(2, "create")).isSelected)
                    assert(  checkbox(checkbox(2, "read"  )).isSelected)
                    assert(  checkbox(checkbox(2, "update")).isSelected)
                    assert(  checkbox(checkbox(2, "delete")).isSelected)
                    assert(textField(role(3)).value === "clerk")
                    assert(  checkbox(checkbox(3, "create")).isSelected)
                    assert(  checkbox(checkbox(3, "read"  )).isSelected)
                    assert(! checkbox(checkbox(3, "update")).isSelected)
                    assert(! checkbox(checkbox(3, "delete")).isSelected)
                }

                // Done, close dialog
                patientlyClick(Apply)
            }
        }
    }
}
