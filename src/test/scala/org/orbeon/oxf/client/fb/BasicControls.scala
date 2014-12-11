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

        val ControlsCount = 29

        def countAllToolboxControlButtons =
            cssSelector(".fb-tool > .fb-add-control").findAllElements.size

        def clickOnToolboxControlButtons(from: Int, to: Int) =
            executeScript(
                "$('.fb-tool > .fb-add-control > button').slice(arguments[0], arguments[1] + 1).click();",
                new java.lang.Integer(from),
                new java.lang.Integer(to)
            )

        def countCurrentGridRows =
            executeScript("return $('.fb-selected').closest('.fr-grid').find('.fb-grid-tr:not(.xforms-repeat-template)').size()").asInstanceOf[Long]

        def countRepeatedGrids =
            executeScript("return $('.xbl-fr-section .xbl-fr-grid .fr-repeat').size()").asInstanceOf[Long]

        def clickOnToolboxButtonsAndCheck2(from: Int, to: Int) =
            for {
                _ ← clickOnToolboxControlButtons(from, to)
                - ← assert(countCurrentGridRows == (to + 1))
            }()

        // Insert controls step by step as inserting all of them at once can take more than the allowed timeout
        def clickOnAllToolboxButtonsAndCheck(step: Int) =
            0 until ControlsCount sliding (step, step) map (r ⇒ r.head → r.last) foreach (clickOnToolboxButtonsAndCheck2 _).tupled

        Builder.onNewForm {
            for {
                _ ← Builder.insertNewGrid()
                _ ← assert(countAllToolboxControlButtons == ControlsCount)
                _ ← clickOnAllToolboxButtonsAndCheck(5)
                _ ← Builder.insertNewRepeatedGrid()
                - ← assert(countRepeatedGrids == 1)
                _ ← clickOnAllToolboxButtonsAndCheck(5)
            }()
        }
    }
}
