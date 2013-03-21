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
package org.orbeon.oxf.xforms

import org.junit.Assert.assertEquals
import org.junit.Test
import org.orbeon.oxf.test.FormRunnerOps
import org.orbeon.oxf.test.OrbeonClientBase
import org.scalatest.junit.AssertionsForJUnit
import org.scalatest.selenium.WebBrowser

trait ClientPropertiesTest extends AssertionsForJUnit with FormRunnerOps {

    @Test def testEventProperties(): Unit = {
        loadOrbeonPage("/xforms-sandbox/sample/test-event-properties")
        checkOutputs(Seq("triggered" → "false", "p1" → "", "p2" → ""))
        $("#send-event button").click()
        waitForAjaxResponse()
        checkOutputs(Seq("triggered" → "true", "p1" → "v1", "p2" → "v2"))
    }

    private def checkOutputs(outputs: Seq[(String, String)]) =
        outputs.foreach { case (cssClass, expected) ⇒
            val actual = $("." + cssClass + " span").getText
            assertEquals(expected, actual)
        }
}
