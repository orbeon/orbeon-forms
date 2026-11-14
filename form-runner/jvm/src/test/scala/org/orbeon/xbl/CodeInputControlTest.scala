/**
  * Copyright (C) 2026 Orbeon, Inc.
  *
  * This program is free software; you can redistribute it and/or modify it under the terms of the
  * GNU Lesser General Public License as published by the Free Software Foundation; either version
  *  2.1 of the License, or (at your option) any later version.
  *
  * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  * See the GNU Lesser General Public License for more details.
  *
  * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
  */
package org.orbeon.xbl

import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport, XFormsSupport}
import org.orbeon.oxf.xforms.control.XFormsValueComponentControl
import org.orbeon.oxf.xforms.event.EventCollector
import org.orbeon.oxf.xml.dom.Converter.*
import org.scalatest.funspec.AnyFunSpecLike


class CodeInputControlTest
  extends DocumentTestBase
     with ResourceManagerSupport
     with AnyFunSpecLike
     with XFormsSupport {

  describe("CodeInputSupport helper") {

    it("must normalize code length and character type") {
      assert(CodeInputSupport.normalizeLength("")     == 6)
      assert(CodeInputSupport.normalizeLength("abc")  == 6)
      assert(CodeInputSupport.normalizeLength("0")    == 1)
      assert(CodeInputSupport.normalizeLength("4")    == 4)
      assert(CodeInputSupport.normalizeLength("16")   == 16)
      assert(CodeInputSupport.normalizeLength("25")   == 16)

      assert(CodeInputSupport.normalizeCharacterType("")             == "digits")
      assert(CodeInputSupport.normalizeCharacterType("digits")       == "digits")
      assert(CodeInputSupport.normalizeCharacterType("alphanumeric") == "alphanumeric")
      assert(CodeInputSupport.normalizeCharacterType("letters")      == "letters")
      assert(CodeInputSupport.normalizeCharacterType("unknown")      == "digits")
    }

    it("must filter and normalize characters according to character-type") {
      assert(CodeInputSupport.filterAndNormalize("12-34 56", "digits", 6)       == "123456")
      assert(CodeInputSupport.filterAndNormalize("12a34b56", "digits", 6)       == "123456")
      assert(CodeInputSupport.filterAndNormalize("ab-12-cd", "alphanumeric", 6) == "AB12CD")
      assert(CodeInputSupport.filterAndNormalize("ab-12-cd", "letters", 4)      == "ABCD")
    }

    it("must convert between slot arrays and stored value strings including internal blank cells") {
      val emptySlots = Vector.fill[Option[Char]](6)(None)
      assert(CodeInputSupport.slotsToStoredValue(emptySlots) == "")

      val fullSlots = Vector(Some('1'), Some('2'), Some('3'), Some('4'), Some('5'), Some('6'))
      assert(CodeInputSupport.slotsToStoredValue(fullSlots) == "123456")

      val holeSlots = Vector(Some('1'), None, Some('3'), None, None, None)
      assert(CodeInputSupport.slotsToStoredValue(holeSlots) == "1 3")
      assert(CodeInputSupport.storedValueToSlots("1 3", 6, "digits") == holeSlots)

      assert(CodeInputSupport.cellValueAt("1 3", "6", "digits", 1) == "1")
      assert(CodeInputSupport.cellValueAt("1 3", "6", "digits", 2) == "")
      assert(CodeInputSupport.cellValueAt("1 3", "6", "digits", 3) == "3")
      assert(CodeInputSupport.cellValueAt("1 3", "6", "digits", 4) == "")
    }

    it("must validate codes against length and character-type") {
      // Blank value is valid (required MIP is checked separately)
      assert(CodeInputSupport.isValidCode("", "6", "digits"))
      assert(CodeInputSupport.isValidCode("   ", "6", "digits"))

      // Digits (default length 6)
      assert(CodeInputSupport.isValidCode("012345", "", ""))
      assert(! CodeInputSupport.isValidCode("01234", "", ""))
      assert(! CodeInputSupport.isValidCode("01 345", "", ""))
      assert(! CodeInputSupport.isValidCode("01234A", "", ""))

      // Alphanumeric (length 8)
      assert(CodeInputSupport.isValidCode("AB12CD34", "8", "alphanumeric"))
      assert(! CodeInputSupport.isValidCode("ab12cd34", "8", "alphanumeric"))
      assert(! CodeInputSupport.isValidCode("AB12CD3", "8", "alphanumeric"))

      // Letters (length 4)
      assert(CodeInputSupport.isValidCode("WXYZ", "4", "letters"))
      assert(! CodeInputSupport.isValidCode("WX1Z", "4", "letters"))
    }
  }

  describe("fr:code-input XBL control") {

    it("must validate complete, incomplete, and character-restricted codes via xxbl:constraint") {

      val xhtml =
        <xh:html
          xmlns:xf="http://www.w3.org/2002/xforms"
          xmlns:xh="http://www.w3.org/1999/xhtml"
          xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
          xmlns:xs="http://www.w3.org/2001/XMLSchema"
          xmlns:fr="http://orbeon.org/oxf/xml/form-runner">
          <xh:head>
            <xf:model
              xxf:expose-xpath-types="true"
              xxf:function-library="org.orbeon.oxf.fr.library.FormRunnerFunctionLibrary">
              <xf:instance id="fr-form-instance">
                <form>
                  <otp-code/>
                  <alpha-code/>
                </form>
              </xf:instance>
              <xf:bind ref="instance('fr-form-instance')">
                <xf:bind id="otp-code-bind"   ref="otp-code"   type="fr:code-input"/>
                <xf:bind id="alpha-code-bind" ref="alpha-code" type="fr:code-input"/>
              </xf:bind>
            </xf:model>
          </xh:head>
          <xh:body>
            <fr:code-input id="otp-code-control" bind="otp-code-bind">
              <xf:label>Verification Code</xf:label>
            </fr:code-input>
            <fr:code-input id="alpha-code-control" bind="alpha-code-bind" length="4" character-type="alphanumeric">
              <xf:label>Reference Code</xf:label>
            </fr:code-input>
          </xh:body>
        </xh:html>.toDocument

      withTestExternalContext { _ =>
        withXFormsDocument(xhtml) { doc =>
          withActionAndDoc(doc) {

            val otpControl   = doc.getControlByEffectiveId("otp-code-control").asInstanceOf[XFormsValueComponentControl]
            val alphaControl = doc.getControlByEffectiveId("alpha-code-control").asInstanceOf[XFormsValueComponentControl]

            // Initially empty -> valid
            assert(otpControl.isValid)
            assert(alphaControl.isValid)

            // Incomplete 6-digit OTP -> invalid
            setControlValueWithEventSearchNested("otp-code-control", "123")
            assert(otpControl.getValue(EventCollector.Throw) == "123")
            assert(! otpControl.isValid)

            // Complete 6-digit OTP -> valid
            setControlValueWithEventSearchNested("otp-code-control", "048291")
            assert(otpControl.getValue(EventCollector.Throw) == "048291")
            assert(otpControl.isValid)

            // Alphanumeric 4-char code
            setControlValueWithEventSearchNested("alpha-code-control", "A1B")
            assert(! alphaControl.isValid)

            setControlValueWithEventSearchNested("alpha-code-control", "A1B9")
            assert(alphaControl.isValid)
          }
        }
      }
    }
  }
}
