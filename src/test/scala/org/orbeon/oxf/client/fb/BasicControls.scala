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
package org.orbeon.oxf.client.fb

import org.junit.Test
import org.orbeon.oxf.client.{FormBuilderOps, XFormsOps}
import org.orbeon.oxf.util.ScalaUtils._
import org.scalatest.junit.AssertionsForJUnit

trait BasicControls extends AssertionsForJUnit with FormBuilderOps with XFormsOps {

    @Test def addGridsSectionsControls(): Unit = {

        import Builder._

        val ControlsCount = 29 // change when we add/remove controls to toolbox (ideally would be known from source)

        def clickOnToolboxControlButtons(from: Int, to: Int) =
            executeScript(
                "$('.fb-tool > .fb-add-control > button').slice(arguments[0], arguments[1] + 1).click();",
                new java.lang.Integer(from),
                new java.lang.Integer(to)
            )

        def clickOnToolboxButtonsAndCheck(from: Int, to: Int) =
            for {
                _ ← clickOnToolboxControlButtons(from, to)
                - ← assert(countCurrentGridRows == (to + 1))
            }()

        // Insert controls step by step as inserting all of them at once can take more than the allowed timeout
        def clickOnAllToolboxButtonsAndCheck(step: Int) = (
            0 until ControlsCount
            sliding (step, step)
            map     (r ⇒ r.head → r.last)
            foreach (clickOnToolboxButtonsAndCheck _).tupled
        )

        def setAndCheckNameOfControlInCell(from: Int, to: Int, controlName: String) =
            for {
                _ ← moveOverCellInCurrentGrid(from, to)
                _ ← openControlSettings()
                _ ← ControlSettings.setControlName(controlName)
                _ ← ControlSettings.applySettings()
                _ ← moveOverCellInCurrentGrid(from, to)
                _ ← openControlSettings()
                _ ← assert(controlName == ControlSettings.getControlName)
                _ ← ControlSettings.cancelSettings()
            }()

        onNewForm {
            for {
                - ← assert(countSections == 1) // the form has an initial section
                - ← assert(countGrids == 1)    // the form has an initial grid
                _ ← assert(countAllToolboxControlButtons == ControlsCount)

                _ ← insertNewGrid()
                - ← assert(countGrids == 2)
                _ ← clickOnAllToolboxButtonsAndCheck(5)

                _ ← setAndCheckNameOfControlInCell(1, 1, "my-input")

                _ ← insertNewRepeatedGrid()
                - ← assert(countRepeatedGrids == 1)
                - ← assert(countGrids == 3)
                _ ← clickOnAllToolboxButtonsAndCheck(5)

                _ ← setAndCheckNameOfControlInCell(1 + 2 * 1, 1 + 1, "my-input-in-repeated-grid") // adjust row/col in repeated grid

                _ ← insertNewSection()
                - ← assert(countSections == 2)
            }()
        }
    }
}
