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
package org.orbeon.xbl

import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport, XFormsSupport}
import org.orbeon.oxf.xforms.control.controls.XFormsSelect1Control
import org.orbeon.oxf.xforms.control.{XFormsValueComponentControl, XFormsValueControl}
import org.orbeon.oxf.xforms.event.EventCollector
import org.orbeon.oxf.xforms.itemset.Item
import org.orbeon.oxf.xml.dom.Converter.*
import org.scalatest.funspec.AnyFunSpecLike


class YearControlsTest
  extends DocumentTestBase
     with ResourceManagerSupport
     with AnyFunSpecLike
     with XFormsSupport {

  describe("fr:year, fr:dropdown-year, and fr:dropdown-month-year controls") {

    it("must validate 4-digit years in fr:year") {

      val xhtml =
        <xh:html
          xmlns:xf="http://www.w3.org/2002/xforms"
          xmlns:xh="http://www.w3.org/1999/xhtml"
          xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
          xmlns:fr="http://orbeon.org/oxf/xml/form-runner">
          <xh:head>
            <xf:model xxf:function-library="org.orbeon.oxf.fr.library.FormRunnerFunctionLibrary">
              <xf:instance id="fr-form-instance">
                <form>
                  <my-year>2026</my-year>
                </form>
              </xf:instance>
              <xf:bind ref="my-year" type="xf:gYear"/>
            </xf:model>
          </xh:head>
          <xh:body>
            <fr:year id="my-year-control" ref="my-year">
              <xf:label>Year</xf:label>
            </fr:year>
          </xh:body>
        </xh:html>.toDocument

      withTestExternalContext { _ =>
        withXFormsDocument(xhtml) { doc =>
          val yearComponent = doc.resolveObjectByIdInScope("#document", "my-year-control", None).get.asInstanceOf[XFormsValueComponentControl]
          val innerInput    = yearComponent.nestedContainerOpt.get.resolveObjectByIdInScope(yearComponent.innerRootControl.effectiveId, "input", None).get.asInstanceOf[XFormsValueControl]

          assert(yearComponent.getValue(EventCollector.Throw) == "2026")
          assert(yearComponent.isValid)

          setControlValueWithEventSearchNested(innerInput.effectiveId, "202")
          assert(! yearComponent.isValid)

          setControlValueWithEventSearchNested(innerInput.effectiveId, "abcd")
          assert(! yearComponent.isValid)

          setControlValueWithEventSearchNested(innerInput.effectiveId, "")
          assert(yearComponent.isValid)

          setControlValueWithEventSearchNested(innerInput.effectiveId, "1999")
          assert(yearComponent.isValid)
          assert(yearComponent.getValue(EventCollector.Throw) == "1999")
        }
      }
    }

    it("must populate years itemset and synchronize value in fr:dropdown-year") {

      val xhtml =
        <xh:html
          xmlns:xf="http://www.w3.org/2002/xforms"
          xmlns:xh="http://www.w3.org/1999/xhtml"
          xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
          xmlns:fr="http://orbeon.org/oxf/xml/form-runner">
          <xh:head>
            <xf:model xxf:function-library="org.orbeon.oxf.fr.library.FormRunnerFunctionLibrary">
              <xf:instance id="fr-form-instance">
                <form>
                  <my-dropdown-year>2023</my-dropdown-year>
                </form>
              </xf:instance>
              <xf:bind ref="my-dropdown-year" type="xf:gYear"/>
            </xf:model>
          </xh:head>
          <xh:body>
            <fr:dropdown-year
              id="my-dropdown-year-control"
              ref="my-dropdown-year"
              start-year="2020"
              end-year="2025"
              xxf:encrypt-item-values="false">
              <xf:label>Dropdown Year</xf:label>
            </fr:dropdown-year>
          </xh:body>
        </xh:html>.toDocument

      withTestExternalContext { _ =>
        withXFormsDocument(xhtml) { doc =>
          val dropdownYearComponent = doc.resolveObjectByIdInScope("#document", "my-dropdown-year-control", None).get.asInstanceOf[XFormsValueComponentControl]
          val innerSelect1          = dropdownYearComponent.nestedContainerOpt.get.resolveObjectByIdInScope(dropdownYearComponent.innerRootControl.effectiveId, "select1", None).get.asInstanceOf[XFormsSelect1Control]

          val items = innerSelect1.getItemset(EventCollector.Throw).allItemsIterator.collect { case v: Item.ValueNode => (v.label.label, v.value.left.getOrElse("")) }.toList
          assert(items == List("" -> "", "2025" -> "2025", "2024" -> "2024", "2023" -> "2023", "2022" -> "2022", "2021" -> "2021", "2020" -> "2020"))

          assert(dropdownYearComponent.getValue(EventCollector.Throw) == "2023")
          assert(innerSelect1.getValue(EventCollector.Throw) == "2023")

          setControlValueWithEventSearchNested(innerSelect1.effectiveId, "2025")
          assert(dropdownYearComponent.getValue(EventCollector.Throw) == "2025")
          assert(dropdownYearComponent.isValid)

          setControlValueWithEventSearchNested(innerSelect1.effectiveId, "")
          assert(dropdownYearComponent.getValue(EventCollector.Throw) == "")
          assert(dropdownYearComponent.isValid)
        }
      }
    }

    it("must populate months and years itemsets and synchronize value in fr:dropdown-month-year") {

      val xhtml =
        <xh:html
          xmlns:xf="http://www.w3.org/2002/xforms"
          xmlns:xh="http://www.w3.org/1999/xhtml"
          xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
          xmlns:fr="http://orbeon.org/oxf/xml/form-runner">
          <xh:head>
            <xf:model xxf:function-library="org.orbeon.oxf.fr.library.FormRunnerFunctionLibrary">
              <xf:instance id="fr-form-instance">
                <form>
                  <my-dropdown-month-year>2023-05</my-dropdown-month-year>
                </form>
              </xf:instance>
              <xf:bind ref="my-dropdown-month-year" type="xf:gYearMonth"/>
              <xf:instance id="fr-fr-resources" xxf:readonly="true">
                <resources>
                  <resource xml:lang="en">
                    <components>
                      <date-time>
                        <month>Month</month>
                        <year>Year</year>
                      </date-time>
                    </components>
                  </resource>
                </resources>
              </xf:instance>
            </xf:model>
          </xh:head>
          <xh:body>
            <fr:dropdown-month-year
              id="my-dropdown-month-year-control"
              ref="my-dropdown-month-year"
              start-year="2020"
              end-year="2025"
              xxf:encrypt-item-values="false">
              <xf:label>Dropdown Month/Year</xf:label>
            </fr:dropdown-month-year>
          </xh:body>
        </xh:html>.toDocument

      withTestExternalContext { _ =>
        withXFormsDocument(xhtml) { doc =>
          val dropdownMonthYearComponent = doc.resolveObjectByIdInScope("#document", "my-dropdown-month-year-control", None).get.asInstanceOf[XFormsValueComponentControl]
          val monthSelect1               = dropdownMonthYearComponent.nestedContainerOpt.get.resolveObjectByIdInScope(dropdownMonthYearComponent.innerRootControl.effectiveId, "month-select", None).get.asInstanceOf[XFormsSelect1Control]
          val yearSelect1                = dropdownMonthYearComponent.nestedContainerOpt.get.resolveObjectByIdInScope(dropdownMonthYearComponent.innerRootControl.effectiveId, "year-select", None).get.asInstanceOf[XFormsSelect1Control]

          val monthItemValues = monthSelect1.getItemset(EventCollector.Throw).allItemsIterator.collect { case v: Item.ValueNode => v.value.left.getOrElse("") }.toList
          assert(monthItemValues == "" :: (1 to 12).map(_.toString).toList)

          val yearItemValues = yearSelect1.getItemset(EventCollector.Throw).allItemsIterator.collect { case v: Item.ValueNode => v.value.left.getOrElse("") }.toList
          assert(yearItemValues == List("", "2025", "2024", "2023", "2022", "2021", "2020"))

          assert(dropdownMonthYearComponent.getValue(EventCollector.Throw) == "2023-05")
          assert(monthSelect1.getValue(EventCollector.Throw) == "5")
          assert(yearSelect1.getValue(EventCollector.Throw) == "2023")
          assert(dropdownMonthYearComponent.isValid)

          // Select month 11 and year 2025
          setControlValueWithEventSearchNested(monthSelect1.effectiveId, "11")
          setControlValueWithEventSearchNested(yearSelect1.effectiveId, "2025")
          assert(dropdownMonthYearComponent.getValue(EventCollector.Throw) == "2025-11")
          assert(dropdownMonthYearComponent.isValid)

          // Partially clear month -> invalid (`2025-__`)
          setControlValueWithEventSearchNested(monthSelect1.effectiveId, "")
          assert(dropdownMonthYearComponent.getValue(EventCollector.Throw) == "2025-__")
          assert(! dropdownMonthYearComponent.isValid)

          // Clear year as well -> empty and valid (`""`)
          setControlValueWithEventSearchNested(yearSelect1.effectiveId, "")
          assert(dropdownMonthYearComponent.getValue(EventCollector.Throw) == "")
          assert(dropdownMonthYearComponent.isValid)

          // Partially select month only -> invalid (`____-09`)
          setControlValueWithEventSearchNested(monthSelect1.effectiveId, "9")
          assert(dropdownMonthYearComponent.getValue(EventCollector.Throw) == "____-09")
          assert(! dropdownMonthYearComponent.isValid)
        }
      }
    }
  }
}
