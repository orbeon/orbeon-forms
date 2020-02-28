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

import org.junit.Test
import org.orbeon.oxf.client.FormBuilderOps
import org.scalatest.concurrent.Eventually._
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.junit.AssertionsForJUnit

trait ClientControlResourcesEditorTest extends AssertionsForJUnit with FormBuilderOps with Matchers {

  // CSS selectors
  private val FirstControl = cssSelector("*[id $= 'control-1-control']")
  private val FirstControlLabel = cssSelector(FirstControl.queryString + " .xforms-label")
  private val FirstControlHint = cssSelector(FirstControl.queryString + " .xforms-hint")
  private val LabelEditor = cssSelector(".fb-label-editor")
  private val LabelEditorInput = cssSelector(LabelEditor.queryString + " input[type = 'text']")
  private val Body = cssSelector(".fb-navbar img")

  // On click on the label, because the label has a `for` pointing to the input, the focus would switch to the input,
  // which we don't want to happen. So in control-resources-editor/*.coffee, we remove the `for` after the click. While this
  // works on the browser, it doesn't work when the click is done through WebDriver, just with Firefox. So here
  // we remove the `for` before doing the click.
  private def clickLabel(selector: CssSelectorQuery): Unit = {
    executeScript(s"""ORBEON.jQuery("${selector.queryString}").removeAttr('for')""")
    click on selector
  }

  @Test def editLabel(): Unit = {
    Builder.onNewForm {

      // Enter label and check it is set
      locally {
        // Click on label and check it is displayed
        clickLabel(FirstControlLabel)
        val textfield = eventually {
          val textfield = textField(LabelEditorInput)
          textfield should be ('displayed)
          textfield
        }
        // Enter label
        textfield.value = "First name"
        textfield.enter()
        eventually { FirstControlLabel.element.text should be ("First name") }
      }

      // Bug #915: Label editor: label disappears
      locally {
        click on FirstControlLabel
        click on FirstControlHint
        click on Body
        FirstControlLabel.element.text should be ("First name")
      }
    }
  }
}
