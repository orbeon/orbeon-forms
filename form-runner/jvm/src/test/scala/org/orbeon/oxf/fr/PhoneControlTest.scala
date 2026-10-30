/**
 * Copyright (C) 2026 Orbeon, Inc.
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
package org.orbeon.oxf.fr

import org.orbeon.dom.QName
import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport}
import org.orbeon.oxf.xforms.control.XFormsValueControl
import org.orbeon.oxf.xforms.control.controls.{XFormsInputControl, XFormsSelect1Control}
import org.orbeon.oxf.xforms.event.EventCollector
import org.orbeon.oxf.xforms.itemset.Item
import org.orbeon.oxf.xml.dom.Converter.*
import org.scalatest.funspec.AnyFunSpecLike


class PhoneControlTest
  extends DocumentTestBase
     with ResourceManagerSupport
     with AnyFunSpecLike {

  private def findInnerCountrySelect1(outerControlId: String): XFormsSelect1Control =
    document
      .controls
      .getCurrentControlTree
      .effectiveIdsToControls
      .collectFirst {
        case (id, c: XFormsSelect1Control) if id.startsWith(s"$outerControlId≡") && id.endsWith("≡select1") => c
      }
      .getOrElse(throw new NoSuchElementException(s"Inner country select1 not found for $outerControlId"))

  private def findInnerPhoneInput(outerControlId: String): XFormsInputControl =
    document
      .controls
      .getCurrentControlTree
      .effectiveIdsToControls
      .collectFirst {
        case (id, c: XFormsInputControl) if id.startsWith(s"$outerControlId≡") && id.endsWith("≡input") => c
      }
      .getOrElse(throw new NoSuchElementException(s"Inner phone input not found for $outerControlId"))

  private def findOuterControl(outerControlId: String): XFormsValueControl =
    getValueControl(outerControlId)

  private def nonEmptyItems(control: XFormsSelect1Control): List[Item.ValueNode] =
    control
      .getItemset(EventCollector.Throw)
      .allItemsWithValueIterator(reverse = false)
      .map(_._1)
      .filter(_.externalValue(encode = false).nonEmpty)
      .toList

  describe("`<fr:phone>` XBL component") {

    it("must format and store valid phone numbers in E.164 across countries and auto-detect international prefixes") {
      withTestExternalContext { _ =>
        this setupDocument
          <xh:html
            xmlns:xh="http://www.w3.org/1999/xhtml"
            xmlns:xf="http://www.w3.org/2002/xforms"
            xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
            xmlns:fr="http://orbeon.org/oxf/xml/form-runner">
            <xh:head>
              <xf:model
                id="model"
                xxf:lang="en"
                xxf:function-library="org.orbeon.oxf.fr.library.FormRunnerFunctionLibrary">
                <xf:instance id="main">
                  <form>
                    <phone/>
                    <preloaded>+33123456789</preloaded>
                  </form>
                </xf:instance>
                <xf:bind ref="phone" type="fr:phone"/>
                <xf:bind ref="preloaded" type="fr:phone"/>
                <xf:instance id="fr-fr-resources">
                  <resources>
                    <resource xml:lang="en">
                      <components><labels><please-select>Please select:</please-select></labels></components>
                    </resource>
                  </resources>
                </xf:instance>
              </xf:model>
            </xh:head>
            <xh:body>
              <fr:phone id="my-phone" ref="phone">
                <xf:label>Phone</xf:label>
              </fr:phone>
              <fr:phone id="preloaded-phone" ref="preloaded">
                <xf:label>Preloaded</xf:label>
              </fr:phone>
            </xh:body>
          </xh:html>.toDocument

        val countrySelect = findInnerCountrySelect1("my-phone")
        val phoneInput    = findInnerPhoneInput("my-phone")
        val outerPhone    = findOuterControl("my-phone")

        // 1. Initially empty, valid, and defaults to US
        assert(getControlValue(countrySelect.effectiveId) == "US")
        assert(getControlValue(phoneInput.effectiveId) == "")
        assert(outerPhone.getValue(EventCollector.Throw) == "")
        assert(outerPhone.isValid)

        // 2. Preloaded E.164 phone number (+33123456789) selects FR and formats in national format
        val preloadedSelect = findInnerCountrySelect1("preloaded-phone")
        val preloadedInput  = findInnerPhoneInput("preloaded-phone")
        val outerPreloaded  = findOuterControl("preloaded-phone")
        assert(getControlValue(preloadedSelect.effectiveId) == "FR")
        assert(getControlValue(preloadedInput.effectiveId) == "01 23 45 67 89")
        assert(outerPreloaded.getValue(EventCollector.Throw) == "+33123456789")
        assert(outerPreloaded.getFormattedValue(EventCollector.Throw).contains("+33 1 23 45 67 89"))
        assert(outerPreloaded.isValid)

        // 3. Enter US phone number in national format
        setControlValue(phoneInput.effectiveId, "6502530000")
        assert(getControlValue(countrySelect.effectiveId) == "US")
        assert(getControlValue(phoneInput.effectiveId) == "(650) 253-0000")
        assert(outerPhone.getValue(EventCollector.Throw) == "+16502530000")
        assert(outerPhone.getFormattedValue(EventCollector.Throw).contains("+1 650-253-0000"))
        assert(outerPhone.isValid)

        // 4. Enter Swiss phone number with international `+` prefix -> auto-switches country to CH
        setControlValue(phoneInput.effectiveId, "+41 44 668 18 00")
        assert(getControlValue(countrySelect.effectiveId) == "CH")
        assert(getControlValue(phoneInput.effectiveId) == "044 668 18 00")
        assert(outerPhone.getValue(EventCollector.Throw) == "+41446681800")
        assert(outerPhone.getFormattedValue(EventCollector.Throw).contains("+41 44 668 18 00"))
        assert(outerPhone.isValid)

        // 5. Enter UK phone number with international `00` prefix -> auto-switches country to GB
        setControlValue(phoneInput.effectiveId, "0044 20 7946 0000")
        assert(getControlValue(countrySelect.effectiveId) == "GB")
        assert(getControlValue(phoneInput.effectiveId) == "020 7946 0000")
        assert(outerPhone.getValue(EventCollector.Throw) == "+442079460000")
        assert(outerPhone.getFormattedValue(EventCollector.Throw).contains("+44 20 7946 0000"))
        assert(outerPhone.isValid)

        // 6. Enter Canadian phone number with `+1` -> auto-switches country to CA (NANP disambiguation)
        setControlValue(phoneInput.effectiveId, "+1 416-555-0123")
        assert(getControlValue(countrySelect.effectiveId) == "CA")
        assert(getControlValue(phoneInput.effectiveId) == "(416) 555-0123")
        assert(outerPhone.getValue(EventCollector.Throw) == "+14165550123")
        assert(outerPhone.isValid)
      }
    }

    it("must validate invalid numbers, extensions, and country restrictions while preserving user input") {
      withTestExternalContext { _ =>
        this setupDocument
          <xh:html
            xmlns:xh="http://www.w3.org/1999/xhtml"
            xmlns:xf="http://www.w3.org/2002/xforms"
            xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
            xmlns:fr="http://orbeon.org/oxf/xml/form-runner">
            <xh:head>
              <xf:model
                id="model"
                xxf:lang="en"
                xxf:function-library="org.orbeon.oxf.fr.library.FormRunnerFunctionLibrary">
                <xf:instance id="main">
                  <form>
                    <phone/>
                    <restricted/>
                  </form>
                </xf:instance>
                <xf:bind ref="phone" type="fr:phone"/>
                <xf:bind ref="restricted" type="fr:phone"/>
                <xf:instance id="fr-fr-resources">
                  <resources>
                    <resource xml:lang="en">
                      <components><labels><please-select>Please select:</please-select></labels></components>
                    </resource>
                  </resources>
                </xf:instance>
              </xf:model>
            </xh:head>
            <xh:body>
              <fr:phone id="my-phone" ref="phone" default-country="ch">
                <xf:label>Phone</xf:label>
              </fr:phone>
              <fr:phone
                id="restricted-phone"
                ref="restricted"
                preferred-countries="ch us"
                include-countries="US, CH, GB, FR"
                exclude-countries="fr"
                show-code="true"
                show-flag="true">
                <xf:label>Restricted Phone</xf:label>
              </fr:phone>
            </xh:body>
          </xh:html>.toDocument

        val countrySelect = findInnerCountrySelect1("my-phone")
        val phoneInput    = findInnerPhoneInput("my-phone")
        val outerPhone    = findOuterControl("my-phone")

        // 1. Respects `default-country="ch"`
        assert(getControlValue(countrySelect.effectiveId) == "CH")
        assert(outerPhone.isValid)

        // 2. Invalid phone number is preserved and marks control invalid
        setControlValue(phoneInput.effectiveId, "12345")
        assert(getControlValue(phoneInput.effectiveId) == "12345")
        assert(outerPhone.getValue(EventCollector.Throw) == "12345")
        assert(! outerPhone.isValid)

        // 3. Phone number with extension is rejected as invalid
        setControlValue(countrySelect.effectiveId, "US")
        setControlValue(phoneInput.effectiveId, "650-253-0000 ext. 123")
        assert(getControlValue(phoneInput.effectiveId) == "650-253-0000 ext. 123")
        assert(! outerPhone.isValid)

        // 4. US national number when CA is selected is invalid, and becomes valid when switching to US
        setControlValue(countrySelect.effectiveId, "CA")
        setControlValue(phoneInput.effectiveId, "(201) 555-0123")
        assert(getControlValue(countrySelect.effectiveId) == "CA")
        assert(! outerPhone.isValid)

        setControlValue(countrySelect.effectiveId, "US")
        assert(outerPhone.getValue(EventCollector.Throw) == "+12015550123")
        assert(outerPhone.isValid)

        // 5. Restricted phone control itemset and country filtering
        val restrictedSelect = findInnerCountrySelect1("restricted-phone")
        val restrictedInput  = findInnerPhoneInput("restricted-phone")
        val outerRestricted  = findOuterControl("restricted-phone")
        val restrictedItems  = nonEmptyItems(restrictedSelect)

        assert(restrictedItems.map(_.externalValue(encode = false)) == List("CH", "US", "GB"))
        assert(restrictedItems(1).attributes.toMap.get(QName("class")).contains("fr-country-last-preferred"))
        assert(
          restrictedItems.map(_.label.label) == List(
            "\uD83C\uDDE8\uD83C\uDDED Switzerland (CH, +41)",
            "\uD83C\uDDFA\uD83C\uDDF8 United States (US, +1)",
            "\uD83C\uDDEC\uD83C\uDDE7 United Kingdom (GB, +44)"
          )
        )

        // Entering a French number (+33, excluded by `exclude-countries="fr"`) marks the control invalid
        setControlValue(restrictedInput.effectiveId, "+33 1 23 45 67 89")
        assert(getControlValue(restrictedInput.effectiveId) == "+33 1 23 45 67 89")
        assert(! outerRestricted.isValid)

        // Entering an allowed international Swiss number switches country to CH and marks the control valid
        setControlValue(restrictedInput.effectiveId, "+41 44 668 18 00")
        assert(getControlValue(restrictedSelect.effectiveId) == "CH")
        assert(getControlValue(restrictedInput.effectiveId) == "044 668 18 00")
        assert(outerRestricted.getValue(EventCollector.Throw) == "+41446681800")
        assert(outerRestricted.isValid)
      }
    }
  }
}
