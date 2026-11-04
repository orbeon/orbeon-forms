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

import org.orbeon.dom.QName
import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport}
import org.orbeon.oxf.xforms.event.EventCollector
import org.orbeon.oxf.xml.dom.Converter.*
import org.orbeon.xbl.MaskedInputSupport.MaskEditResult
import org.scalatest.funspec.AnyFunSpecLike


class MaskedInputSupportTest
  extends DocumentTestBase
     with ResourceManagerSupport
     with AnyFunSpecLike {

  describe("MaskedInputSupport") {

    describe("formatDisplayValue and toRawValue") {

      val cases = List(
        // (mask, rawInput, expectedFormatted, expectedRaw)
        ("(999) 999-9999",        "5551234567",        "(555) 123-4567",    "5551234567"),
        ("(999) 999-9999",        "(555) 123-4567",    "(555) 123-4567",    "5551234567"),
        ("(999) 999-9999",        "555",               "(555",              "555"),
        ("(999) 999-9999",        "",                  "",                  ""),
        ("A9A 9A9",               "k1a0b1",            "K1A 0B1",           "K1A0B1"),
        ("A9A 9A9",               "K1A 0B1",           "K1A 0B1",           "K1A0B1"),
        ("a9a-9a9",               "K1A0B1",            "k1a-0b1",           "k1a0b1"),
        ("99999[-9999]",          "90210",             "90210",             "90210"),
        ("99999[-9999]",          "902101234",         "90210-1234",        "902101234"),
        ("99999[-9999]",          "9021012",           "90210-12",          "9021012"),
        ("\\7\\5\\6.9999.9999.99","7123456789",        "756.7123.4567.89",  "7123456789"),
        ("\\7\\5\\6.9999.9999.99","756.7123.4567.89",  "756.7123.4567.89",  "7123456789"),
        ("POL-9999-AA",           "1234xy",            "POL-1234-XY",       "1234XY"),
        ("POL-9999-AA",           "POL-1234-XY",       "POL-1234-XY",       "1234XY"),
        ("HH:HH:HH",              "0a1b2c",            "0A:1B:2C",          "0A1B2C"),
        ("**-**",                 "a1b2",              "A1-B2",             "A1B2")
      )

      for ((mask, input, expectedFormatted, expectedRaw) <- cases) {
        it(s"must format and extract raw value for mask `$mask` and input `$input`") {
          assert(MaskedInputSupport.formatDisplayValue(input, mask) == expectedFormatted)
          assert(MaskedInputSupport.toRawValue(input, mask)         == expectedRaw)
        }
      }
    }

    describe("isValid") {

      val cases = List(
        ("(999) 999-9999",        "",           true),
        ("(999) 999-9999",        "5551234567", true),
        ("(999) 999-9999",        "555123",     false),
        ("(999) 999-9999",        "555123456A", false),
        ("A9A 9A9",               "K1A0B1",     true),
        ("A9A 9A9",               "11A0B1",     false),
        ("99999[-9999]",          "90210",      true),
        ("99999[-9999]",          "902101234",  true),
        ("99999[-9999]",          "9021012",    false),
        ("99999[-9999]",          "902",        false),
        ("HH:HH:HH",              "0A1F9C",     true),
        ("HH:HH:HH",              "0G1F9C",     false)
      )

      for ((mask, rawValue, expectedValid) <- cases) {
        it(s"must validate `$rawValue` against `$mask` as $expectedValid") {
          assert(MaskedInputSupport.isValid(rawValue, mask) == expectedValid)
        }
      }
    }

    describe("applyMask live typing and deletion") {

      it("must auto-insert leading and intervening literals while typing") {
        val mask = "(999) 999-9999"

        val step1 = MaskedInputSupport.applyMask("5", 1, "", mask)
        assert(step1 == MaskEditResult("5", "(5", 2))

        val step2 = MaskedInputSupport.applyMask("(55", 3, step1.formattedValue, mask)
        assert(step2 == MaskEditResult("55", "(55", 3))

        val step3 = MaskedInputSupport.applyMask("(555", 4, step2.formattedValue, mask)
        assert(step3 == MaskEditResult("555", "(555", 4))

        val step4 = MaskedInputSupport.applyMask("(5551", 5, step3.formattedValue, mask)
        assert(step4 == MaskEditResult("5551", "(555) 1", 7))
      }

      it("must allow backspacing across literal separators without getting stuck") {
        val mask = "(999) 999-9999"

        // User is at `(555) |123-4567` (caret at 6) and presses Backspace, deleting the space -> `(555)123-4567` (caret at 5)
        val afterBackspaceOnSeparator =
          MaskedInputSupport.applyMask(
            userInput         = "(555)123-4567",
            caretPos          = 5,
            previousFormatted = "(555) 123-4567",
            mask              = mask
          )
        assert(afterBackspaceOnSeparator == MaskEditResult("551234567", "(551) 234-567", 3))

        // User has `(5` and presses Backspace to delete `5` -> `(`
        val afterDeletingLastDigit =
          MaskedInputSupport.applyMask(
            userInput         = "(",
            caretPos          = 1,
            previousFormatted = "(5",
            mask              = mask
          )
        assert(afterDeletingLastDigit == MaskEditResult("", "", 0))
      }

      it("must handle escaped digit prefixes like Swiss AHV numbers") {
        val mask = "\\7\\5\\6.9999.9999.99"

        // Typing `7` as first slot into empty field
        val step1 = MaskedInputSupport.applyMask("7", 1, "", mask)
        assert(step1 == MaskEditResult("7", "756.7", 5))

        // Backspacing `7` from `756.7` -> browser leaves `756.`
        val stepBack = MaskedInputSupport.applyMask("756.", 4, "756.7", mask)
        assert(stepBack == MaskEditResult("", "", 0))
      }
    }

    describe("inputMode, maxLengthOpt, and placeholder") {
      it("must return numeric inputMode only when all slots are digits") {
        assert(MaskedInputSupport.inputMode("(999) 999-9999") == "numeric")
        assert(MaskedInputSupport.inputMode("99999[-9999]")   == "numeric")
        assert(MaskedInputSupport.inputMode("A9A 9A9")        == "text")
        assert(MaskedInputSupport.inputMode("HH:HH")          == "text")
        assert(MaskedInputSupport.inputMode("")               == "text")
      }

      it("must compute maxLengthOpt from total tokens") {
        assert(MaskedInputSupport.maxLengthOpt("(999) 999-9999").contains(14))
        assert(MaskedInputSupport.maxLengthOpt("99999[-9999]").contains(10))
        assert(MaskedInputSupport.maxLengthOpt("A9A 9A9").contains(7))
        assert(MaskedInputSupport.maxLengthOpt("").isEmpty)
      }

      it("must compute placeholder from mask tokens") {
        assert(MaskedInputSupport.placeholder("(999) 999-9999") == "(999) 999-9999")
        assert(MaskedInputSupport.placeholder("A9A 9A9")        == "A9A 9A9")
        assert(MaskedInputSupport.placeholder("99999[-9999]")   == "99999-9999")
        assert(MaskedInputSupport.placeholder("HH:HH:HH")       == "HH:HH:HH")
        assert(MaskedInputSupport.placeholder("\\99-99")        == "99-99")
        assert(MaskedInputSupport.placeholder("")               == "")
      }
    }

    describe("`<fr:masked-input>` XBL component") {

      it("must compile `xxbl:constraint`, format, deserialize, and validate against the configured mask") {
        withTestExternalContext { _ =>
          this setupDocument
            <xh:html
              xmlns:xh="http://www.w3.org/1999/xhtml"
              xmlns:xf="http://www.w3.org/2002/xforms"
              xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
              xmlns:fr="http://orbeon.org/oxf/xml/form-runner">
              <xh:head>
                <xf:model
                  id="fr-form-model"
                  xxf:lang="en"
                  xxf:xpath-analysis="true"
                  xxf:function-library="org.orbeon.oxf.fr.library.FormRunnerFunctionLibrary">
                  <xf:instance id="fr-form-instance">
                    <form>
                      <phone/>
                      <preloaded>5551234567</preloaded>
                    </form>
                  </xf:instance>
                  <xf:bind id="phone-bind" name="phone" ref="phone" type="fr:masked-input" xxf:functions="restricted"/>
                  <xf:bind id="preloaded-bind" name="preloaded" ref="preloaded" type="fr:masked-input" xxf:functions="restricted"/>
                </xf:model>
              </xh:head>
              <xh:body>
                <fr:masked-input id="phone-control" bind="phone-bind" mask="(999) 999-9999">
                  <xf:label>Phone</xf:label>
                </fr:masked-input>
                <fr:masked-input id="preloaded-control" bind="preloaded-bind" mask="(999) 999-9999">
                  <xf:label>Preloaded</xf:label>
                </fr:masked-input>
              </xh:body>
            </xh:html>.toDocument

          val phoneControl     = getValueControl("phone-control")
          val preloadedControl = getValueControl("preloaded-control")
          val phoneInput       = getControl("phone-control≡input")

          // 1. Placeholder attribute on the inner `<xh:input>` reflects the mask pattern
          assert(phoneInput.extensionAttributeValue(QName("placeholder")).contains("(999) 999-9999"))

          // 2. Initially empty is valid; preloaded value is valid and formatted
          assert(phoneControl.getValue(EventCollector.Throw) == "")
          assert(phoneControl.isValid)

          assert(preloadedControl.getValue(EventCollector.Throw) == "5551234567")
          assert(preloadedControl.getFormattedValue(EventCollector.Throw).contains("(555) 123-4567"))
          assert(preloadedControl.isValid)

          // 3. Incomplete value is deserialized to raw digits and marked invalid by `xxbl:constraint`
          setControlValue("phone-control", "(555) 123")
          assert(phoneControl.getValue(EventCollector.Throw) == "555123")
          assert(! phoneControl.isValid)

          // 4. Complete value matching the mask is deserialized to raw digits, formatted, and marked valid
          setControlValue("phone-control", "(555) 987-6543")
          assert(phoneControl.getValue(EventCollector.Throw) == "5559876543")
          assert(phoneControl.getFormattedValue(EventCollector.Throw).contains("(555) 987-6543"))
          assert(phoneControl.isValid)
        }
      }
    }
  }
}
