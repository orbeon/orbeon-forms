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

    private def permissionSelector(selector: String) = cssSelector(".fb-permissions-dialog " + selector)
    private def role(line: Int) = permissionSelector(".fb-role-name[id $= '" + line + "'] input")
    private def checkbox(line: Int, crud: String): CssSelectorQuery = permissionSelector(".fb-" + crud + "-permission[id $= '" + line + "'] input")

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
                eventually { checkbox(3, "read") should be ('selected) } // Read auto-selected when selecting update
                patientlyClick(checkbox(3, "delete"))

                // Everyone can create
                patientlyClick(checkbox(1, "create"))
                // Read auto-selected when selecting update
                eventually {
                    checkbox(2, "read") should be ('selected)
                    checkbox(3, "read") should be ('selected)
                }

                // Save, reopen, and check the permissions are correct
                patientlyClick(Apply)
                patientlyClick(OpenPermissions)
                eventually {
                    checkbox(HasPermissions) should be ('selected)
                    // Roles are re-ordered by alphabetic order, see #917
                    checkbox(1, "create") should     be ('selected)
                    checkbox(1, "read"  ) should not be ('selected)
                    checkbox(1, "update") should not be ('selected)
                    checkbox(1, "delete") should not be ('selected)
                    textField(role(2)).value should be ("admin")
                    checkbox(2, "create") should be     ('selected)
                    checkbox(2, "read"  ) should be     ('selected)
                    checkbox(2, "update") should be     ('selected)
                    checkbox(2, "delete") should be     ('selected)
                    textField(role(3)).value should be ("clerk")
                    checkbox(3, "create") should be     ('selected)
                    checkbox(3, "read"  ) should be     ('selected)
                    checkbox(3, "update") should not be ('selected)
                    checkbox(3, "delete") should not be ('selected)
                }

                // Done, close dialog
                patientlyClick(Apply)
            }
        }
    }
}
