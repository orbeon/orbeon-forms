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
package org.orbeon.oxf.client

import org.junit.Assert.assertEquals
import org.junit.Test
import org.scalatest.junit.{MustMatchersForJUnit, AssertionsForJUnit}

trait XForms extends AssertionsForJUnit with MustMatchersForJUnit with FormRunnerOps {

    // https://github.com/orbeon/orbeon-forms/issues/889
    @Test def issue889(): Unit = {

        def clickCheckbox() = clickElementByCSS("#hide-checkbox input")

        loadOrbeonPage("/unit-tests/issue-889")

        def liGroupElements = cssSelector("#group-begin-ul-group ~ li:not(.xforms-group-begin-end)").findAllElements.to[List]

        liGroupElements foreach (_.classes must contain ("xforms-disabled"))
        cssSelector("#div-group").element.classes must contain ("xforms-disabled")
        liGroupElements(0).classes must not contain ("class42")
        liGroupElements(1).classes must contain ("myClass")

        clickCheckbox()

        liGroupElements foreach (_.classes must not contain ("xforms-disabled"))
        cssSelector("#div-group").element.classes must not contain ("xforms-disabled")
        liGroupElements(0).classes must contain ("class42")
        liGroupElements(1).classes must contain ("myClass")

        clickCheckbox()
        liGroupElements foreach (_.classes must contain ("xforms-disabled-subsequent"))
        cssSelector("#div-group").element.classes must contain ("xforms-disabled-subsequent")
        liGroupElements(0).classes must not contain ("class42")
        liGroupElements(1).classes must contain ("myClass")

    }

    @Test def testEventProperties(): Unit = {

        def checkOutputs(outputs: Seq[(String, String)]) =
            outputs.foreach { case (cssClass, expected) ⇒
                val actual = $("." + cssClass + " span").getText
                assertEquals(expected, actual)
            }

        loadOrbeonPage("/xforms-sandbox/sample/test-event-properties")
        checkOutputs(Seq("triggered" → "false", "p1" → "", "p2" → ""))
        $("#send-event button").click()
        waitForAjaxResponse()
        checkOutputs(Seq("triggered" → "true", "p1" → "v1", "p2" → "v2"))
    }
}
