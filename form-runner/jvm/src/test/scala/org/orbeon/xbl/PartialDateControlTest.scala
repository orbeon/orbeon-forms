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
import org.orbeon.oxf.xforms.control.{XFormsValueComponentControl, XFormsValueControl}
import org.orbeon.oxf.xforms.event.EventCollector
import org.orbeon.oxf.xml.dom.Converter.*
import org.scalatest.funspec.AnyFunSpecLike


class PartialDateControlTest
  extends DocumentTestBase
     with ResourceManagerSupport
     with AnyFunSpecLike
     with XFormsSupport {

  describe("fr:partial-date XBL control and XPath helper functions") {

    it("must validate configured precisions/qualifiers/intervals/year bounds and evaluate XPath helper functions") {

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
                  <strict-date/>
                  <archival-date/>
                  <calc-earliest/>
                  <calc-latest/>
                  <calc-year/>
                </form>
              </xf:instance>
              <xf:bind ref="instance('fr-form-instance')">
                <xf:bind id="strict-date-bind"   ref="strict-date"   type="fr:partial-date"/>
                <xf:bind id="archival-date-bind" ref="archival-date" type="fr:partial-date"/>
                <xf:bind ref="calc-earliest" calculate="string(fr:partial-date-earliest(../archival-date))"/>
                <xf:bind ref="calc-latest"   calculate="string(fr:partial-date-latest(../archival-date))"/>
                <xf:bind ref="calc-year"     calculate="string(fr:partial-date-year(../archival-date))"/>
              </xf:bind>
            </xf:model>
          </xh:head>
          <xh:body>
            <fr:partial-date id="strict-date-control" bind="strict-date-bind">
              <xf:label>Strict Partial Date</xf:label>
            </fr:partial-date>
            <fr:partial-date
              id="archival-date-control"
              bind="archival-date-bind"
              precisions="day month year decade"
              allow-qualifiers="true"
              allow-intervals="true"
              min-year="1850"
              max-year="2026">
              <xf:label>Archival Date</xf:label>
            </fr:partial-date>
            <xf:output id="calc-earliest-output" ref="calc-earliest"/>
            <xf:output id="calc-latest-output"   ref="calc-latest"/>
            <xf:output id="calc-year-output"     ref="calc-year"/>
          </xh:body>
        </xh:html>

      withTestExternalContext { _ =>
        withXFormsDocument(xhtml.toDocument) { doc =>
          withActionAndDoc(doc) {
            val strictCtrl   = resolveObject[XFormsValueComponentControl]("strict-date-control").get
            val archivalCtrl = resolveObject[XFormsValueComponentControl]("archival-date-control").get

            // Initially blank and valid
            assert(strictCtrl.isValid)
            assert(archivalCtrl.isValid)

            // Strict control accepts day/month/year, rejects decade/qualifiers/intervals
            setControlValue("strict-date-control", "2021-11")
            assert(strictCtrl.isValid)
            setControlValue("strict-date-control", "1961")
            assert(strictCtrl.isValid)
            setControlValue("strict-date-control", "192X")
            assert(! strictCtrl.isValid)
            setControlValue("strict-date-control", "1899?")
            assert(! strictCtrl.isValid)
            setControlValue("strict-date-control", "1926/1937")
            assert(! strictCtrl.isValid)

            // Archival control accepts decade, qualifier, interval, and evaluates XPath helper functions
            setControlValue("archival-date-control", "192X")
            assert(archivalCtrl.isValid)
            assert(resolveObject[XFormsValueControl]("calc-earliest-output").get.getValue(EventCollector.Throw) == "1920-01-01")
            assert(resolveObject[XFormsValueControl]("calc-latest-output").get.getValue(EventCollector.Throw)   == "1929-12-31")
            assert(resolveObject[XFormsValueControl]("calc-year-output").get.getValue(EventCollector.Throw)     == "")

            setControlValue("archival-date-control", "1899~")
            assert(archivalCtrl.isValid)
            assert(resolveObject[XFormsValueControl]("calc-earliest-output").get.getValue(EventCollector.Throw) == "1899-01-01")
            assert(resolveObject[XFormsValueControl]("calc-latest-output").get.getValue(EventCollector.Throw)   == "1899-12-31")
            assert(resolveObject[XFormsValueControl]("calc-year-output").get.getValue(EventCollector.Throw)     == "1899")

            setControlValue("archival-date-control", "1926/1937")
            assert(archivalCtrl.isValid)
            assert(resolveObject[XFormsValueControl]("calc-earliest-output").get.getValue(EventCollector.Throw) == "1926-01-01")
            assert(resolveObject[XFormsValueControl]("calc-latest-output").get.getValue(EventCollector.Throw)   == "1937-12-31")
            assert(resolveObject[XFormsValueControl]("calc-year-output").get.getValue(EventCollector.Throw)     == "")

            // Year bounds enforcement
            setControlValue("archival-date-control", "1840")
            assert(! archivalCtrl.isValid)
            setControlValue("archival-date-control", "2030")
            assert(! archivalCtrl.isValid)
          }
        }
      }
    }

    it("must render formatted output for static-readonly and fr:summary bindings") {

      val xhtml =
        <xh:html
          xmlns:xf="http://www.w3.org/2002/xforms"
          xmlns:xh="http://www.w3.org/1999/xhtml"
          xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
          xmlns:xs="http://www.w3.org/2001/XMLSchema"
          xmlns:fr="http://orbeon.org/oxf/xml/form-runner"
          xml:lang="en">
          <xh:head>
            <xf:model
              xxf:expose-xpath-types="true"
              xxf:function-library="org.orbeon.oxf.fr.library.FormRunnerFunctionLibrary">
              <xf:instance id="fr-form-instance">
                <form>
                  <d1>192X</d1>
                  <d2>1899~</d2>
                </form>
              </xf:instance>
              <xf:bind ref="instance('fr-form-instance')">
                <xf:bind id="d1-bind" ref="d1" readonly="true()"/>
                <xf:bind id="d2-bind" ref="d2" readonly="true()"/>
              </xf:bind>
            </xf:model>
          </xh:head>
          <xh:body>
            <fr:partial-date id="d1-control" bind="d1-bind" fr:static-readonly="true">
              <xf:label>Decade</xf:label>
            </fr:partial-date>
            <fr:partial-date id="d2-control" bind="d2-bind" fr:summary="true">
              <xf:label>Approx</xf:label>
            </fr:partial-date>
          </xh:body>
        </xh:html>

      withTestExternalContext { _ =>
        withXFormsDocument(xhtml.toDocument) { doc =>
          withActionAndDoc(doc) {
            val d1Input = doc.getControlByEffectiveId("d1-control≡input").asInstanceOf[XFormsValueControl]
            val d2Input = doc.getControlByEffectiveId("d2-control≡input").asInstanceOf[XFormsValueControl]

            assert(d1Input.getFormattedValue(EventCollector.Throw).contains("1920s"))
            assert(d2Input.getFormattedValue(EventCollector.Throw).contains("c. 1899"))
          }
        }
      }
    }
  }
}
