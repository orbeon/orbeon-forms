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
import org.orbeon.oxf.client.{XFormsOps, FormBuilderOps}
import org.scalatest.junit.AssertionsForJUnit


trait RepeatSettings extends AssertionsForJUnit with FormBuilderOps with XFormsOps {

    private object Private {

        val GridDetailsDialogPrefix = "dialog-grid-details≡"

        val MinRepeatSelect1Id      = s"${GridDetailsDialogPrefix}min-repeat"
        val MaxRepeatSelect1Id      = s"${GridDetailsDialogPrefix}max-repeat"

        val SaveButtonSelector      = cssSelector(s"#${GridDetailsDialogPrefix}save-button button")
        val MinInputSelector        = cssSelector(s"#${GridDetailsDialogPrefix}min-input")
        val MaxInputSelector        = cssSelector(s"#${GridDetailsDialogPrefix}max-input")
    }

    import Private._

    @Test def minMaxSettings(): Unit = {

        def saveAndCheck(min: Int, max: Int) =
            for {
                _ ← clickOn(XForms.fullItemSelector(MinRepeatSelect1Id, min))
                _ ← clickOn(XForms.fullItemSelector(MaxRepeatSelect1Id, max))
                minValue      = min.toString
                maxValue      = ((min + 1) max 1).toString
                checkMinField = min >= 2
                checkMaxField = max >= 1
                _ ← if (checkMinField) MinInputSelector.element.replaceFieldText(minValue)
                _ ← if (checkMaxField) MaxInputSelector.element.replaceFieldText(maxValue)
                _ ← clickOn(SaveButtonSelector)
                _ ← Builder.openGridDetails("fb≡section-1-control≡grid-3-grid")
                _ ← assert(radioButton(XForms.fullItemSelector(MinRepeatSelect1Id, min)).isSelected)
                _ ← assert(radioButton(XForms.fullItemSelector(MaxRepeatSelect1Id, max)).isSelected)
                _ ← if (checkMinField) assert(minValue === MinInputSelector.element.fieldText)
                _ ← if (checkMaxField) assert(maxValue === MaxInputSelector.element.fieldText)
            }()

        Builder.onNewForm {
            for {
                _ ← Builder.insertNewRepeatedGrid()
                _ ← Builder.openGridDetails("fb≡section-1-control≡grid-3-grid")
            }()

            for {
                min ← 0 to 2
                max ← 0 to 1
            } locally {
                saveAndCheck(min, max)
            }
        }
    }
}