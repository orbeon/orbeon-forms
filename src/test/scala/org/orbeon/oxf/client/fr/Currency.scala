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
package org.orbeon.oxf.client.fr

import org.junit.Test
import org.scalatest.junit.MustMatchersForJUnit
import org.orbeon.oxf.client.FormRunnerOps

trait Currency extends MustMatchersForJUnit with FormRunnerOps {

    // https://github.com/orbeon/orbeon-forms/issues/1026
    @Test def displayUpdateWhenNoXFormsUpdate(): Unit = {

        val currencyInput = cssSelector(".xbl-fr-currency .xbl-fr-number-visible-input")
        val emailInput = cssSelector(".xforms-type-email input")

        for {
            _ ← loadOrbeonPage("/fr/orbeon/controls/new")
            _ ← clickOn(LinkTextQuery("Typed Controls"))
        }()

        def enterValueCheckRounded() = for {
            _ ← clickOn(currencyInput)
            _ ← textField(currencyInput).value = "0.9998"
            _ ← clickOn(emailInput)
            _ ← assert(textField(currencyInput).value === "1.00")
        }()

        // First time, checking the server rounds the value
        enterValueCheckRounded()
        // Second time, checking that even if the XForms value isn't changed, the client puts the previous XForms value
        enterValueCheckRounded()
    }
}
