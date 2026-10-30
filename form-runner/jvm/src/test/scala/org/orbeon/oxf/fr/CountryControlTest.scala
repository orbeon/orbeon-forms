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
import org.orbeon.oxf.xforms.control.controls.XFormsSelect1Control
import org.orbeon.oxf.xforms.event.EventCollector
import org.orbeon.oxf.xforms.itemset.Item
import org.orbeon.oxf.xml.dom.Converter.*
import org.scalatest.funspec.AnyFunSpecLike


class CountryControlTest
  extends DocumentTestBase
     with ResourceManagerSupport
     with AnyFunSpecLike {

  private def findInnerSelect1(outerControlId: String): XFormsSelect1Control =
    document
      .controls
      .getCurrentControlTree
      .effectiveIdsToControls
      .collectFirst {
        case (id, c: XFormsSelect1Control) if id.startsWith(s"$outerControlId≡") && id.endsWith("≡select1") => c
      }
      .getOrElse(throw new NoSuchElementException(s"Inner select1 not found for $outerControlId"))

  private def nonEmptyItems(control: XFormsSelect1Control): List[Item.ValueNode] =
    control
      .getItemset(EventCollector.Throw)
      .allItemsWithValueIterator(reverse = false)
      .map(_._1)
      .filter(_.externalValue(encode = false).nonEmpty)
      .toList

  describe("`<fr:country>` XBL component") {

    it("must provide all 249 ISO 3166-1 countries with localized names and collation order") {
      withTestExternalContext { _ =>
        this setupDocument
          <xh:html
            xmlns:xh="http://www.w3.org/1999/xhtml"
            xmlns:xf="http://www.w3.org/2002/xforms"
            xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
            xmlns:fr="http://orbeon.org/oxf/xml/form-runner"
            xml:lang="{xxf:instance('lang')}">
            <xh:head>
              <xf:model
                id="model"
                xxf:function-library="org.orbeon.oxf.fr.library.FormRunnerFunctionLibrary">
                <xf:instance id="main">
                  <form>
                    <country>CH</country>
                  </form>
                </xf:instance>
                <xf:instance id="lang">
                  <lang>en</lang>
                </xf:instance>
                <xf:instance id="fr-fr-resources">
                  <resources>
                    <resource xml:lang="en">
                      <components><labels><please-select>Please select:</please-select></labels></components>
                    </resource>
                    <resource xml:lang="fr">
                      <components><labels><please-select>Veuillez sélectionner :</please-select></labels></components>
                    </resource>
                  </resources>
                </xf:instance>
              </xf:model>
            </xh:head>
            <xh:body>
              <xf:input id="lang-input" ref="instance('lang')"/>
              <fr:country id="my-country" ref="country">
                <xf:label>Country</xf:label>
              </fr:country>
            </xh:body>
          </xh:html>.toDocument

        val select1 = findInnerSelect1("my-country")
        val enItems = nonEmptyItems(select1)

        assert(enItems.size == 249)
        assert(enItems.head.externalValue(encode = false) == "AF")
        assert(enItems.head.label.label == "Afghanistan")
        assert(enItems.find(_.externalValue(encode = false) == "CH").exists(_.label.label == "Switzerland"))

        // Switch language to French and verify localized names and French dictionary collation (Égypte under E)
        setControlValue("lang-input", "fr")

        val frItems = nonEmptyItems(findInnerSelect1("my-country"))
        assert(frItems.size == 249)
        assert(frItems.find(_.externalValue(encode = false) == "CH").exists(_.label.label == "Suisse"))
        val dmIdx = frItems.indexWhere(_.externalValue(encode = false) == "DM") // Dominique
        val egIdx = frItems.indexWhere(_.externalValue(encode = false) == "EG") // Égypte
        val esIdx = frItems.indexWhere(_.externalValue(encode = false) == "ES") // Espagne
        assert(dmIdx < egIdx && egIdx < esIdx)
      }
    }

    it("must support `preferred-countries`, `include-countries`, `exclude-countries`, `show-code`, and `show-flag`") {
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
                    <c-preferred/>
                    <c-filtered/>
                    <c-code-flag/>
                  </form>
                </xf:instance>
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
              <fr:country
                id="preferred-country"
                ref="c-preferred"
                preferred-countries="ch us gb">
                <xf:label>Preferred</xf:label>
              </fr:country>
              <fr:country
                id="filtered-country"
                ref="c-filtered"
                include-countries="US, CA, MX, GB"
                exclude-countries="mx">
                <xf:label>Filtered</xf:label>
              </fr:country>
              <fr:country
                id="code-flag-country"
                ref="c-code-flag"
                include-countries="CH US"
                show-code="true"
                show-flag="true">
                <xf:label>Code and Flag</xf:label>
              </fr:country>
              <xf:output id="filtered-output" ref="c-filtered"/>
            </xh:body>
          </xh:html>.toDocument

        // 1. Preferred countries appear first in specified order, without duplication, and last preferred has separator class
        val preferredItems = nonEmptyItems(findInnerSelect1("preferred-country"))
        assert(preferredItems.size == 249)
        assert(preferredItems.take(3).map(_.externalValue(encode = false)) == List("CH", "US", "GB"))
        assert(preferredItems.count(_.externalValue(encode = false) == "CH") == 1)
        assert(preferredItems(2).attributes.toMap.get(QName("class")).contains("fr-country-last-preferred"))
        assert(! preferredItems.head.attributes.toMap.get(QName("class")).contains("fr-country-last-preferred"))

        // 2. Include and exclude countries filter the itemset
        val filteredItems = nonEmptyItems(findInnerSelect1("filtered-country"))
        assert(filteredItems.map(_.externalValue(encode = false)) == List("CA", "GB", "US"))

        // 3. `show-code="true"` and `show-flag="true"` format item labels with flag emoji and ISO code
        val codeFlagItems = nonEmptyItems(findInnerSelect1("code-flag-country"))
        assert(codeFlagItems.map(_.label.label) == List("\uD83C\uDDE8\uD83C\uDDED Switzerland (CH)", "\uD83C\uDDFA\uD83C\uDDF8 United States (US)"))

        // 4. Selecting an unencrypted ISO code stores the code in the instance
        val select1 = findInnerSelect1("filtered-country")
        setControlValue(select1.effectiveId, "CA")
        assert(getControlValue(select1.effectiveId) == "CA")
        assert(getControlValue("filtered-output") == "CA")
      }
    }
  }
}
