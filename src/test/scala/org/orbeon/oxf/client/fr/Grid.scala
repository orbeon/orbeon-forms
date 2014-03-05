/**
 * Copyright (C) 2014 Orbeon, Inc.
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
package org.orbeon.oxf.client.fr

import org.junit.Test
import org.scalatest.junit.MustMatchersForJUnit
import org.orbeon.oxf.client.FormRunnerOps


trait Grid extends MustMatchersForJUnit with FormRunnerOps {
    // See also: https://github.com/orbeon/orbeon-forms/issues/1431
    @Test def rowMenu(): Unit = {

        def openMenu(iteration: Int): Unit = {
            val buttons = cssSelector(".fr-grid-dropdown-button").findAllElements.toList

            if (buttons.size < iteration)
                throw new IndexOutOfBoundsException
            else
                clickOn(buttons(iteration - 1))
        }

        def isMenuOpen =
            cssSelector(".fr-grid-dropdown-menu").element.classes("open")

        def clickOutside(): Unit =
            clickOn(cssSelector("body"))

        def clickOnMenuAction(action: String): Unit =
            clickOn(cssSelector(s".fr-$action"))

        def firstInputValue =
            textField(cssSelector(".my-input input")).value

        for {
            _ ← loadOrbeonPage("/unit-tests/issue-1431")
            _ ← openMenu(1)
            _ ← assert(isMenuOpen)
            _ ← clickOutside()
            _ ← assert(! isMenuOpen)
            _ ← openMenu(1)
            _ ← assert(isMenuOpen)
            _ ← clickOnMenuAction("insert-below")
            _ ← assert(! isMenuOpen)
            _ ← assert(cssSelector("#my-grid≡fr-tr⊙2").findElement.isDefined)
            _ ← assert(firstInputValue === "R2-D2")
            _ ← openMenu(2)
            _ ← assert(isMenuOpen)
            _ ← clickOnMenuAction("move-up")
            _ ← assert(! isMenuOpen)
            _ ← assert(firstInputValue === "")
        }()
    }
}
