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
import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport, XFormsSupport}
import org.orbeon.oxf.xforms.control.{XFormsComponentControl, XFormsControl, XFormsValueComponentControl, XFormsValueControl}
import org.orbeon.oxf.xforms.event.EventCollector
import org.orbeon.oxf.xml.dom.Converter.*
import org.scalatest.funspec.AnyFunSpecLike


class RangeTest
  extends DocumentTestBase
     with ResourceManagerSupport
     with AnyFunSpecLike
     with XFormsSupport {

  describe("RangeSupport") {

    it("must normalize effective bounds and step with sensible defaults") {
      assert(RangeSupport.effectiveStart("") == "0")
      assert(RangeSupport.effectiveStart("  -25.50 ") == "-25.5")
      assert(RangeSupport.effectiveEnd("", "") == "100")
      assert(RangeSupport.effectiveEnd("10", "50") == "50")
      assert(RangeSupport.effectiveEnd("50", "10") == "50")
      assert(RangeSupport.effectiveStep("") == "1")
      assert(RangeSupport.effectiveStep("0.25") == "0.25")
      assert(RangeSupport.effectiveStep("0") == "1")
      assert(RangeSupport.effectiveStep("-5") == "1")
      assert(RangeSupport.effectiveStep("any") == "any")
    }

    it("must keep unset slider thumb at minimum and compute clamped thumb position and progress percentage") {
      assert(RangeSupport.initialSliderValue("", "10", "90") == "10")
      assert(RangeSupport.initialSliderValue("   ", "-20", "20") == "-20")
      assert(RangeSupport.initialSliderValue("35", "10", "90") == "35")
      assert(RangeSupport.initialSliderValue("5", "10", "90") == "10")
      assert(RangeSupport.initialSliderValue("120", "10", "90") == "90")

      assert(RangeSupport.progressCssPercent("", "0", "100") == "0%")
      assert(RangeSupport.progressCssPercent("25", "0", "100") == "25%")
      assert(RangeSupport.progressCssPercent("0", "-50", "50") == "50%")
      assert(RangeSupport.progressCssPercent("10", "10", "10") == "0%")
      assert(RangeSupport.progressCssPercent("150", "0", "100") == "100%")
      assert(RangeSupport.progressCssPercent("-10", "0", "100") == "0%")
    }

    it("must validate in-range values and format prefix/suffix display values") {
      assert(RangeSupport.isValueInRange("", "0", "10"))
      assert(RangeSupport.isValueInRange("0", "0", "10"))
      assert(RangeSupport.isValueInRange("10", "0", "10"))
      assert(RangeSupport.isValueInRange("5.5", "0", "10"))
      assert(! RangeSupport.isValueInRange("-0.1", "0", "10"))
      assert(! RangeSupport.isValueInRange("10.1", "0", "10"))

      assert(RangeSupport.formatDisplayValue("", "$", "USD") == "")
      assert(RangeSupport.formatDisplayValue("42.00", "", "") == "42")
      assert(RangeSupport.formatDisplayValue("75.5", "", "%") == "75.5 %")
      assert(RangeSupport.formatDisplayValue("250", "$", "USD") == "$ 250 USD")
    }
  }

  describe("fr:range and xf:range controls") {

    it("must preserve blank initial state, enforce dynamic range bounds, format values, and render static-readonly and pdf-template modes") {

      val xhtml =
        <xh:html
          xml:lang="en"
          xmlns:xf="http://www.w3.org/2002/xforms"
          xmlns:xh="http://www.w3.org/1999/xhtml"
          xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
          xmlns:fr="http://orbeon.org/oxf/xml/form-runner">
          <xh:head>
            <xf:model xxf:function-library="org.orbeon.oxf.fr.library.FormRunnerFunctionLibrary">
              <xf:instance id="fr-form-instance">
                <form>
                  <min-bound>10</min-bound>
                  <max-bound>50</max-bound>
                  <rating/>
                  <legacy-range>16</legacy-range>
                  <readonly-rating>30</readonly-rating>
                  <pdf-rating>40</pdf-rating>
                </form>
              </xf:instance>
              <xf:bind id="rating-bind" name="rating" ref="rating" type="fr:range"/>
              <xf:bind id="legacy-range-bind" name="legacy-range" ref="legacy-range" type="xf:decimal"/>
              <xf:bind id="readonly-rating-bind" name="readonly-rating" ref="readonly-rating" type="fr:range"/>
              <xf:bind id="pdf-rating-bind" name="pdf-rating" ref="pdf-rating" type="fr:range"/>
            </xf:model>
          </xh:head>
          <xh:body>
            <xf:input id="min-bound-control" ref="min-bound">
              <xf:label>Min</xf:label>
            </xf:input>
            <xf:input id="max-bound-control" ref="max-bound">
              <xf:label>Max</xf:label>
            </xf:input>
            <fr:range
              id="rating-control"
              ref="rating"
              start="{../min-bound}"
              end="{../max-bound}"
              step="5"
              prefix="$"
              suffix="USD"
              show-endpoints="true">
              <xf:label>Budget</xf:label>
            </fr:range>
            <xf:range
              id="legacy-range-control"
              ref="legacy-range"
              start="10"
              end="20"
              step="2">
              <xf:label>Legacy Range</xf:label>
            </xf:range>
            <fr:range
              id="readonly-rating-control"
              ref="readonly-rating"
              prefix="$"
              suffix="USD"
              fr:static-readonly="true">
              <xf:label>Readonly Budget</xf:label>
            </fr:range>
            <fr:range
              id="pdf-rating-control"
              ref="pdf-rating"
              suffix="pts"
              fr:pdf-template="true">
              <xf:label>PDF Score</xf:label>
            </fr:range>
          </xh:body>
        </xh:html>.toDocument

      withTestExternalContext { _ =>
        withXFormsDocument(xhtml) { doc =>

          val ratingComponent =
            doc.resolveObjectByIdInScope("#document", "rating-control", None).get.asInstanceOf[XFormsValueComponentControl]

          def inputAttr(attrName: String): String = {
            val rangeInputControl =
              ratingComponent.nestedContainerOpt.get
                .resolveObjectByIdInScope(ratingComponent.innerRootControl.effectiveId, "range-input", None)
                .get.asInstanceOf[XFormsControl]
            rangeInputControl.evaluatedExtensionAttributes(QName(attrName))
          }

          // Blank initial value stays blank and valid (not clamped to midpoint)
          assert(ratingComponent.getValue(EventCollector.Throw) == "")
          assert(ratingComponent.getExternalValue(EventCollector.Throw) == "")
          assert(ratingComponent.getFormattedValue(EventCollector.Throw).contains(""))
          assert(ratingComponent.isValid)
          assert(inputAttr("min") == "10")
          assert(inputAttr("max") == "50")
          assert(inputAttr("step") == "5")
          assert(inputAttr("data-prefix") == "$")
          assert(inputAttr("data-suffix") == "USD")
          assert(inputAttr("data-not-set-label") == "Not set")

          // Out-of-range value is rejected by `xxbl:constraint`
          setControlValueWithEventSearchNested(ratingComponent.effectiveId, "150")
          assert(ratingComponent.getValue(EventCollector.Throw) == "150")
          assert(! ratingComponent.isValid)

          // Set an in-range value via external value event
          setControlValueWithEventSearchNested(ratingComponent.effectiveId, "25")
          assert(ratingComponent.getValue(EventCollector.Throw) == "25")
          assert(ratingComponent.getFormattedValue(EventCollector.Throw).contains("$ 25 USD"))
          assert(ratingComponent.isValid)

          // Dynamic AVT bound change invalidates out-of-range value and re-validates when back in range
          setControlValueWithEventSearchNested("min-bound-control", "30")
          assert(inputAttr("min") == "30")
          assert(! ratingComponent.isValid)

          setControlValueWithEventSearchNested(ratingComponent.effectiveId, "35")
          assert(ratingComponent.isValid)
          assert(ratingComponent.getFormattedValue(EventCollector.Throw).contains("$ 35 USD"))

          // Clearing back to blank restores valid empty state
          setControlValueWithEventSearchNested(ratingComponent.effectiveId, "")
          assert(ratingComponent.getValue(EventCollector.Throw) == "")
          assert(ratingComponent.isValid)

          // `xf:range` also uses the enhanced `fr-range` binding in Form Runner
          val legacyRange =
            doc.resolveObjectByIdInScope("#document", "legacy-range-control", None).get.asInstanceOf[XFormsValueComponentControl]
          assert(legacyRange.getValue(EventCollector.Throw) == "16")
          assert(legacyRange.isValid)

          // Static-readonly and PDF-template modes render formatted value via `fr-range-output`
          val readonlyRange =
            doc.resolveObjectByIdInScope("#document", "readonly-rating-control", None).get.asInstanceOf[XFormsComponentControl]
          val readonlyInnerInput =
            readonlyRange.nestedContainerOpt.get
              .resolveObjectByIdInScope(readonlyRange.innerRootControl.effectiveId, "input", None)
              .get.asInstanceOf[XFormsValueControl]
          assert(readonlyInnerInput.getFormattedValue(EventCollector.Throw).contains("$ 30 USD"))

          val pdfRange =
            doc.resolveObjectByIdInScope("#document", "pdf-rating-control", None).get.asInstanceOf[XFormsComponentControl]
          val pdfInnerInput =
            pdfRange.nestedContainerOpt.get
              .resolveObjectByIdInScope(pdfRange.innerRootControl.effectiveId, "input", None)
              .get.asInstanceOf[XFormsValueControl]
          assert(pdfInnerInput.getFormattedValue(EventCollector.Throw).contains("40 pts"))
        }
      }
    }
  }
}
