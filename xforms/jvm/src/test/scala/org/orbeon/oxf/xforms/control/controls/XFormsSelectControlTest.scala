/**
 * Copyright (C) 2012 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.control.controls

import org.junit.Test
import org.orbeon.oxf.test.DocumentTestBase
import org.orbeon.oxf.xforms.control.controls.XFormsSelectControl.updateSelection
import org.orbeon.oxf.xml.Dom4j.elemToDocument
import org.scalatestplus.junit.AssertionsForJUnit

class XFormsSelectControlTest extends DocumentTestBase with AssertionsForJUnit{

  @Test def updateSelect(): Unit = {

    locally {
      val (newlySelectedValues, newlyDeselectedValues, newInstanceValue) =
        updateSelection(Set("a", "b", "c"), Set("a", "b"), Set("a"))

      assert(Set() == newlySelectedValues)
      assert(Set("b") == newlyDeselectedValues)
      assert(Set("a", "c") == newInstanceValue)
    }

    locally {
      val (newlySelectedValues, newlyDeselectedValues, newInstanceValue) =
        updateSelection(Set("a", "b", "c"), Set("a", "b"), Set("a", "b"))

      assert(Set() == newlySelectedValues)
      assert(Set() == newlyDeselectedValues)
      assert(Set("a", "b", "c") == newInstanceValue)
    }

    locally {
      val (newlySelectedValues, newlyDeselectedValues, newInstanceValue) =
        updateSelection(Set("a", "b", "c"), Set("a", "b"), Set())

      assert(Set() == newlySelectedValues)
      assert(Set("a", "b") == newlyDeselectedValues)
      assert(Set("c") == newInstanceValue)
    }

    locally {
      val (newlySelectedValues, newlyDeselectedValues, newInstanceValue) =
        updateSelection(Set("a", "b", "c"), Set("a", "b", "d"), Set("a", "d"))

      assert(Set("d") == newlySelectedValues)
      assert(Set("b") == newlyDeselectedValues)
      assert(Set("a", "c", "d") == newInstanceValue)
    }

    locally {
      val (newlySelectedValues, newlyDeselectedValues, newInstanceValue) =
        updateSelection(Set("a", "b", "c"), Set("d", "e"), Set("d", "e"))

      assert(Set("d", "e") == newlySelectedValues)
      assert(Set() == newlyDeselectedValues)
      assert(Set("a", "b", "c", "d", "e") == newInstanceValue)
    }
  }

  // See https://github.com/orbeon/orbeon-forms/issues/2517
  @Test def testSelectDeselectEventsIssue2517(): Unit = {

    this setupDocument
      <xh:html
        xmlns:xh="http://www.w3.org/1999/xhtml"
        xmlns:xf="http://www.w3.org/2002/xforms"
        xmlns:xxf="http://orbeon.org/oxf/xml/xforms">

        <xh:head>
          <xf:model id="model" xxf:encrypt-item-values="false">
            <xf:instance id="instance" xxf:exclude-result-prefixes="#all">
              <instance/>
            </xf:instance>
          </xf:model>
        </xh:head>
        <xh:body>
          <xf:select ref="." appearance="full" id="select">
            <xf:item>
                <xf:label>Do you have children?</xf:label>
                <xf:value>true</xf:value>
            </xf:item>
            <xf:setvalue
                event="xforms-select"
                ref="."
                value="true()"/>
            <xf:setvalue
                event="xforms-deselect"
                ref="."
                value="false()"/>
          </xf:select>
        </xh:body>
      </xh:html>

    assert("" === getControlValue("select"))

    setControlValue("select", "true")
    assert("true" === getControlValue("select"))

    setControlValue("select", "")
    assert("false" === getControlValue("select"))

    setControlValue("select", "true")
    assert("true" === getControlValue("select"))
  }
}
