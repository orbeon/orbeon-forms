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
import org.scalatest.matchers.ShouldMatchers

trait LabelHintEditor extends AssertionsForJUnit with FormBuilderOps with ShouldMatchers {

    // CSS selectors
    val FirstControl = cssSelector("*[id $= 'control-1-control']")
    val FirstControlLabel = cssSelector(FirstControl.queryString + " .xforms-label")
    val LabelEditor = cssSelector(".fb-label-editor")
    val LabelEditorInput = cssSelector(LabelEditor.queryString + " input[type = 'text']")

    @Test def editLabel(): Unit = {
        Builder.onNewForm {

            // Click on label and check it is displayed
            {
                click on FirstControlLabel
                LabelEditor.element should be ('displayed)
            }

            // Enter label
            {
                val textfield = textField(LabelEditorInput)
                textfield.value = "First name"
                textfield.enter()
                def checkLabelValueSet() = FirstControlLabel.element.text should be ("First name")
                checkLabelValueSet()  // New label should be set right away (even before Ajax response)
                waitForAjaxResponse()
                checkLabelValueSet()  // New label should still be set after Ajax response
            }
        }
    }
}
