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


class RatingScaleTest
  extends DocumentTestBase
     with ResourceManagerSupport
     with AnyFunSpecLike
     with XFormsSupport {

  describe("RatingScaleSupport") {

    it("must normalize effective rating max, validate bounds, and format display values") {
      assert(RatingScaleSupport.effectiveRatingMax("") == 5)
      assert(RatingScaleSupport.effectiveRatingMax("  10 ") == 10)
      assert(RatingScaleSupport.effectiveRatingMax("0") == 5)
      assert(RatingScaleSupport.effectiveRatingMax("-3") == 5)
      assert(RatingScaleSupport.effectiveRatingMax("99") == RatingScaleSupport.MaxRenderedStars)

      assert(RatingScaleSupport.isRatingValueInRange("", ""))
      assert(RatingScaleSupport.isRatingValueInRange("1", ""))
      assert(RatingScaleSupport.isRatingValueInRange("5", ""))
      assert(! RatingScaleSupport.isRatingValueInRange("0", ""))
      assert(! RatingScaleSupport.isRatingValueInRange("6", ""))
      assert(RatingScaleSupport.isRatingValueInRange("8", "10"))
      assert(! RatingScaleSupport.isRatingValueInRange("11", "10"))
      assert(! RatingScaleSupport.isRatingValueInRange("3.5", "5"))

      assert(RatingScaleSupport.formatRatingDisplayValue("", "5") == "")
      assert(RatingScaleSupport.formatRatingDisplayValue("4", "") == "4 / 5")
      assert(RatingScaleSupport.formatRatingDisplayValue("9", "10") == "9 / 10")
    }

    it("must normalize effective scale bounds, validate NPS and negative scales, and format display values") {
      assert(RatingScaleSupport.effectiveScaleBounds("", "") == (1, 5))
      assert(RatingScaleSupport.effectiveScaleBounds("0", "10") == (0, 10))
      assert(RatingScaleSupport.effectiveScaleBounds("-2", "2") == (-2, 2))
      assert(RatingScaleSupport.effectiveScaleBounds("0", "100") == (0, 20))

      assert(RatingScaleSupport.isScaleValueInRange("", "0", "10"))
      assert(RatingScaleSupport.isScaleValueInRange("0", "0", "10"))
      assert(RatingScaleSupport.isScaleValueInRange("10", "0", "10"))
      assert(! RatingScaleSupport.isScaleValueInRange("-1", "0", "10"))
      assert(! RatingScaleSupport.isScaleValueInRange("11", "0", "10"))
      assert(RatingScaleSupport.isScaleValueInRange("-2", "-2", "2"))
      assert(! RatingScaleSupport.isScaleValueInRange("-3", "-2", "2"))

      assert(RatingScaleSupport.formatScaleDisplayValue("", "0", "10") == "")
      assert(RatingScaleSupport.formatScaleDisplayValue("8", "0", "10") == "8 / 10")
      assert(RatingScaleSupport.formatScaleDisplayValue("4", "", "") == "4 / 5")
      assert(RatingScaleSupport.formatScaleDisplayValue("-1", "-2", "2") == "-1")
    }
  }

  describe("fr:rating and fr:scale controls") {

    it("must preserve blank state, enforce dynamic AVT bounds, format values, and render static-readonly and pdf-template modes") {

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
                  <max-stars>5</max-stars>
                  <scale-end>10</scale-end>
                  <satisfaction/>
                  <nps/>
                  <readonly-rating>4</readonly-rating>
                  <pdf-rating>5</pdf-rating>
                  <readonly-scale>9</readonly-scale>
                  <pdf-scale>7</pdf-scale>
                </form>
              </xf:instance>
              <xf:bind id="satisfaction-bind" name="satisfaction" ref="satisfaction" type="fr:rating"/>
              <xf:bind id="nps-bind" name="nps" ref="nps" type="fr:scale"/>
              <xf:bind id="readonly-rating-bind" name="readonly-rating" ref="readonly-rating" type="fr:rating"/>
              <xf:bind id="pdf-rating-bind" name="pdf-rating" ref="pdf-rating" type="fr:rating"/>
              <xf:bind id="readonly-scale-bind" name="readonly-scale" ref="readonly-scale" type="fr:scale"/>
              <xf:bind id="pdf-scale-bind" name="pdf-scale" ref="pdf-scale" type="fr:scale"/>
            </xf:model>
          </xh:head>
          <xh:body>
            <xf:input id="max-stars-control" ref="max-stars">
              <xf:label>Max Stars</xf:label>
            </xf:input>
            <xf:input id="scale-end-control" ref="scale-end">
              <xf:label>Scale End</xf:label>
            </xf:input>
            <fr:rating
              id="satisfaction-control"
              ref="satisfaction"
              max="{../max-stars}">
              <xf:label>Satisfaction</xf:label>
            </fr:rating>
            <fr:scale
              id="nps-control"
              ref="nps"
              start="0"
              end="{../scale-end}"
              start-label="Not at all likely"
              end-label="Extremely likely">
              <xf:label>Recommendation</xf:label>
            </fr:scale>
            <fr:rating
              id="readonly-rating-control"
              ref="readonly-rating"
              max="5"
              fr:static-readonly="true">
              <xf:label>Readonly Rating</xf:label>
            </fr:rating>
            <fr:rating
              id="pdf-rating-control"
              ref="pdf-rating"
              max="5"
              fr:pdf-template="true">
              <xf:label>PDF Rating</xf:label>
            </fr:rating>
            <fr:scale
              id="readonly-scale-control"
              ref="readonly-scale"
              start="0"
              end="10"
              fr:static-readonly="true">
              <xf:label>Readonly NPS</xf:label>
            </fr:scale>
            <fr:scale
              id="pdf-scale-control"
              ref="pdf-scale"
              start="0"
              end="10"
              fr:pdf-template="true">
              <xf:label>PDF NPS</xf:label>
            </fr:scale>
          </xh:body>
        </xh:html>.toDocument

      withTestExternalContext { _ =>
        withXFormsDocument(xhtml) { doc =>

          val ratingComponent =
            doc.resolveObjectByIdInScope("#document", "satisfaction-control", None).get.asInstanceOf[XFormsValueComponentControl]
          val scaleComponent =
            doc.resolveObjectByIdInScope("#document", "nps-control", None).get.asInstanceOf[XFormsValueComponentControl]

          def groupAttr(comp: XFormsValueComponentControl, innerId: String, attrName: String): String = {
            val groupCtrl =
              comp.nestedContainerOpt.get
                .resolveObjectByIdInScope(comp.innerRootControl.effectiveId, innerId, None)
                .get.asInstanceOf[XFormsControl]
            groupCtrl.evaluatedExtensionAttributes(QName(attrName))
          }

          // Blank initial state for `fr:rating`
          assert(ratingComponent.getValue(EventCollector.Throw) == "")
          assert(ratingComponent.getExternalValue(EventCollector.Throw) == "")
          assert(ratingComponent.getFormattedValue(EventCollector.Throw).contains(""))
          assert(ratingComponent.isValid)
          assert(groupAttr(ratingComponent, "rating-group", "data-max") == "5")
          assert(groupAttr(ratingComponent, "rating-group", "data-allow-clear") == "true")

          // Out-of-range rating value rejected by `xxbl:constraint`
          setControlValueWithEventSearchNested(ratingComponent.effectiveId, "6")
          assert(! ratingComponent.isValid)

          // Valid rating value accepted and formatted
          setControlValueWithEventSearchNested(ratingComponent.effectiveId, "4")
          assert(ratingComponent.isValid)
          assert(ratingComponent.getFormattedValue(EventCollector.Throw).contains("4 / 5"))

          // Dynamic `max` AVT shrinking invalidates out-of-range value
          setControlValueWithEventSearchNested("max-stars-control", "3")
          assert(groupAttr(ratingComponent, "rating-group", "data-max") == "3")
          assert(! ratingComponent.isValid)

          // Clear back to blank
          setControlValueWithEventSearchNested(ratingComponent.effectiveId, "")
          assert(ratingComponent.isValid)

          // Blank initial state and NPS `0..10` for `fr:scale`
          assert(scaleComponent.getValue(EventCollector.Throw) == "")
          assert(scaleComponent.isValid)
          assert(groupAttr(scaleComponent, "scale-group", "data-start") == "0")
          assert(groupAttr(scaleComponent, "scale-group", "data-end") == "10")
          assert(groupAttr(scaleComponent, "scale-group", "data-start-label") == "Not at all likely")
          assert(groupAttr(scaleComponent, "scale-group", "data-end-label") == "Extremely likely")

          // `0` is valid in NPS `0..10`
          setControlValueWithEventSearchNested(scaleComponent.effectiveId, "0")
          assert(scaleComponent.isValid)
          assert(scaleComponent.getFormattedValue(EventCollector.Throw).contains("0 / 10"))

          // `8` becomes invalid when `end` shrinks dynamically to `5`
          setControlValueWithEventSearchNested(scaleComponent.effectiveId, "8")
          assert(scaleComponent.isValid)
          setControlValueWithEventSearchNested("scale-end-control", "5")
          assert(groupAttr(scaleComponent, "scale-group", "data-end") == "5")
          assert(! scaleComponent.isValid)

          // Static-readonly and PDF-template output bindings
          def innerOutputFormatted(controlId: String): Option[String] = {
            val comp = doc.resolveObjectByIdInScope("#document", controlId, None).get.asInstanceOf[XFormsComponentControl]
            val innerInput =
              comp.nestedContainerOpt.get
                .resolveObjectByIdInScope(comp.innerRootControl.effectiveId, "input", None)
                .get.asInstanceOf[XFormsValueControl]
            innerInput.getFormattedValue(EventCollector.Throw)
          }

          assert(innerOutputFormatted("readonly-rating-control").contains("4 / 5"))
          assert(innerOutputFormatted("pdf-rating-control").contains("5 / 5"))
          assert(innerOutputFormatted("readonly-scale-control").contains("9 / 10"))
          assert(innerOutputFormatted("pdf-scale-control").contains("7 / 10"))
        }
      }
    }
  }
}
