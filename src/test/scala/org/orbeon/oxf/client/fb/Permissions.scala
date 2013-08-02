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
package org.orbeon.oxf.client.fb

import org.scalatest.junit.AssertionsForJUnit
import org.orbeon.oxf.client.FormBuilderOps
import org.junit.Test
import org.orbeon.oxf.common.Version
import org.orbeon.oxf.xforms.XFormsConstants._

trait Permissions extends AssertionsForJUnit with FormBuilderOps {

    private val RepeatSeparator = "" + REPEAT_SEPARATOR

    private def permissionSelector(selector: String): CssSelectorQuery = cssSelector(s".fb-permissions-dialog $selector")
    private def role(line: Int): CssSelectorQuery = permissionSelector(s".fb-role-name[id $$= '${RepeatSeparator + line}'] input")
    private def checkbox(line: Int, crud: String): CssSelectorQuery = permissionSelector(s".fb-$crud-permission[id $$= '${RepeatSeparator + line}'] input")

    private val OpenPermissions  = cssSelector("#fb-permissions-button button")
    private val HasPermissions   = permissionSelector(".fb-has-permissions input")
    private val AddPermission    = permissionSelector(".fb-add-permission a")
    private val Apply            = permissionSelector("[id $= 'save-trigger']")

    @Test def createClerkAdminPermissions(): Unit = {
        if (Version.isPE) {
            Builder.onNewForm {

                for {
                    // Enable permissions
                    _ ← clickOn(OpenPermissions)
                    _ ← clickOn(HasPermissions)

                    // Clerks can read
                    _ ← clickOn(AddPermission)
                    _ ← textField(role(2)).value = "clerk"
                    _ ← clickOn(checkbox(2, "read"))

                    // Admins can do everything
                    _ ← clickOn(AddPermission)
                    _ ← patientlySendKeys(role(3), "admin")
                    _ ← clickOn(checkbox(3, "update"))
                    _ ← assert(checkbox(checkbox(3, "read")).isSelected)
                    // Read auto-selected when selecting update
                    _ ← clickOn(checkbox(3, "delete"))

                    // Everyone can create
                    _ ← clickOn(checkbox(1, "create"))
                    // Read auto-selected when selecting update
                    _ ← assert(checkbox(checkbox(2, "read")).isSelected)
                    _ ← assert(checkbox(checkbox(3, "read")).isSelected)

                    // Save, reopen, and check the permissions are correct
                    _ ← clickOn(Apply)
                    _ ← clickOn(OpenPermissions)
                    _ ← assert(  checkbox(HasPermissions).isSelected)
                    // Roles are re-ordered by alphabetic order, see #917
                    _ ← assert(  checkbox(checkbox(1, "create")).isSelected)
                    _ ← assert(! checkbox(checkbox(1, "read"  )).isSelected)
                    _ ← assert(! checkbox(checkbox(1, "update")).isSelected)
                    _ ← assert(! checkbox(checkbox(1, "delete")).isSelected)
                    _ ← assert(textField(role(2)).value === "admin")
                    _ ← assert(  checkbox(checkbox(2, "create")).isSelected)
                    _ ← assert(  checkbox(checkbox(2, "read"  )).isSelected)
                    _ ← assert(  checkbox(checkbox(2, "update")).isSelected)
                    _ ← assert(  checkbox(checkbox(2, "delete")).isSelected)
                    _ ← assert(textField(role(3)).value === "clerk")
                    _ ← assert(  checkbox(checkbox(3, "create")).isSelected)
                    _ ← assert(  checkbox(checkbox(3, "read"  )).isSelected)
                    _ ← assert(! checkbox(checkbox(3, "update")).isSelected)
                    _ ← assert(! checkbox(checkbox(3, "delete")).isSelected)

                    // Done, close dialog
                    _ ← clickOn(Apply)
                }()
            }
        }
    }
}
