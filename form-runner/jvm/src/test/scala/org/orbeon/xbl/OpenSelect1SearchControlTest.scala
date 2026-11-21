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

import org.orbeon.oxf.fr.persistence.relational.SummarySettings
import org.orbeon.oxf.fr.persistence.relational.index.Index
import org.orbeon.oxf.fr.{AppForm, DataFormatVersion}
import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport, XFormsSupport}
import org.orbeon.oxf.util.{IndentedLogger, LoggerFactory}
import org.orbeon.oxf.xforms.control.XFormsValueComponentControl
import org.orbeon.oxf.xforms.control.controls.XFormsSelect1Control
import org.orbeon.oxf.xforms.event.EventCollector
import org.orbeon.oxf.xml.dom.Converter.*
import org.orbeon.scaxon.NodeConversions.*
import org.scalatest.funspec.AnyFunSpecLike


class OpenSelect1SearchControlTest
  extends DocumentTestBase
     with ResourceManagerSupport
     with AnyFunSpecLike
     with XFormsSupport {

  private implicit val Logger: IndentedLogger = new IndentedLogger(LoggerFactory.createLogger(classOf[OpenSelect1SearchControlTest]))

  describe("Searchable Combobox (xf:select1[appearance ~= search][selection = open])") {

    it("must accept predefined items, free-entered custom values via fr-change, and clear cleanly") {

      val xhtml =
        <xh:html
          xmlns:xf="http://www.w3.org/2002/xforms"
          xmlns:xh="http://www.w3.org/1999/xhtml"
          xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
          xmlns:xs="http://www.w3.org/2001/XMLSchema"
          xmlns:fr="http://orbeon.org/oxf/xml/form-runner">
          <xh:head>
            <xf:model xxf:function-library="org.orbeon.oxf.fr.library.FormRunnerFunctionLibrary">
              <xf:instance id="fr-form-instance">
                <form>
                  <brand/>
                </form>
              </xf:instance>
            </xf:model>
          </xh:head>
          <xh:body>
            <xf:select1 id="brand-control" ref="brand" appearance="search" selection="open">
              <xf:label>Brand</xf:label>
              <xf:item>
                <xf:label>Hermes</xf:label>
                <xf:value>hermes</xf:value>
              </xf:item>
              <xf:item>
                <xf:label>Olivetti</xf:label>
                <xf:value>olivetti</xf:value>
              </xf:item>
            </xf:select1>
          </xh:body>
        </xh:html>.toDocument

      withTestExternalContext { _ =>
        withXFormsDocument(xhtml) { doc =>
          def brandCtrl = doc.getControlByEffectiveId("brand-control").asInstanceOf[XFormsValueComponentControl]
          def innerSel  = doc.getControlByEffectiveId("brand-control≡select1").asInstanceOf[XFormsSelect1Control]

          def sendFrChange(v: String): Unit =
            doc.withOutermostActionHandler {
              withActionAndDoc(doc) {
                dispatch(name = "fr-change", effectiveId = "brand-control", properties = Map("fr-value" -> Some(v)))
              }
            }

          // Bound to fr-open-select1-search
          assert(brandCtrl.staticControl.commonBinding.bindingElemId.contains("fr-open-select1-search"))

          // Initially blank and valid
          assert(brandCtrl.getValue(EventCollector.Throw) == "")
          assert(brandCtrl.isValid)

          // Select a predefined item
          sendFrChange("hermes")
          assert(brandCtrl.getValue(EventCollector.Throw) == "hermes")
          assert(brandCtrl.isValid)
          assert(innerSel.getValue(EventCollector.Throw) == "hermes")

          // Enter a custom value not in static itemset
          sendFrChange("Olympia")
          assert(brandCtrl.getValue(EventCollector.Throw) == "Olympia")
          assert(brandCtrl.isValid)
          assert(innerSel.getValue(EventCollector.Throw) == "Olympia")
          val itemValues = innerSel.getItemset(EventCollector.Throw).allItemsWithValueIterator(reverse = false).map(_._1.value.left.getOrElse("")).toList
          assert(itemValues.contains("Olympia"))

          // Clear value
          sendFrChange("")
          assert(brandCtrl.getValue(EventCollector.Throw) == "")
          assert(brandCtrl.isValid)

          // Switch back to predefined item
          sendFrChange("olivetti")
          assert(brandCtrl.getValue(EventCollector.Throw) == "olivetti")
          assert(brandCtrl.isValid)
        }
      }
    }

    it("must automatically index controls with suggest-existing='true' without enabling Summary display or duplicating fr:index") {
      val formDoc =
        <xh:html
          xmlns:xh="http://www.w3.org/1999/xhtml"
          xmlns:xf="http://www.w3.org/2002/xforms"
          xmlns:fr="http://orbeon.org/oxf/xml/form-runner">
          <xh:head>
            <xf:model id="fr-form-model">
              <xf:instance id="fr-form-instance">
                <form>
                  <section-1>
                    <grid-1>
                      <brand/>
                      <typeface/>
                      <plain/>
                    </grid-1>
                  </section-1>
                </form>
              </xf:instance>
              <xf:bind id="fr-form-binds" ref="instance('fr-form-instance')">
                <xf:bind id="section-1-bind" name="section-1" ref="section-1">
                  <xf:bind id="grid-1-bind" name="grid-1" ref="grid-1">
                    <xf:bind id="brand-bind"    name="brand"    ref="brand"/>
                    <xf:bind id="typeface-bind" name="typeface" ref="typeface"/>
                    <xf:bind id="plain-bind"    name="plain"    ref="plain"/>
                  </xf:bind>
                </xf:bind>
              </xf:bind>
              <xf:instance id="fr-form-resources">
                <resources>
                  <resource xml:lang="en">
                    <brand><label>Brand</label></brand>
                    <typeface><label>Typeface</label></typeface>
                    <plain><label>Plain</label></plain>
                  </resource>
                </resources>
              </xf:instance>
            </xf:model>
          </xh:head>
          <xh:body>
            <fr:view>
              <fr:body>
                <fr:section id="section-1-section" bind="section-1-bind">
                  <fr:grid id="grid-1-grid" bind="grid-1-bind">
                    <fr:c x="1" y="1" w="4">
                      <xf:select1 id="brand-control" bind="brand-bind" appearance="search" selection="open" suggest-existing="true">
                        <xf:label ref="$form-resources/brand/label"/>
                        <fr:index><fr:summary-show/></fr:index>
                      </xf:select1>
                    </fr:c>
                    <fr:c x="5" y="1" w="4">
                      <xf:select1 id="typeface-control" bind="typeface-bind" appearance="search" selection="open" suggest-existing="true">
                        <xf:label ref="$form-resources/typeface/label"/>
                      </xf:select1>
                    </fr:c>
                    <fr:c x="9" y="1" w="4">
                      <xf:input id="plain-control" bind="plain-bind">
                        <xf:label ref="$form-resources/plain/label"/>
                      </xf:input>
                    </fr:c>
                  </fr:grid>
                </fr:section>
              </fr:body>
            </fr:view>
          </xh:body>
        </xh:html>

      val indexed = Index.searchableValues(
        formDoc                   = elemToDocumentInfo(formDoc),
        appForm                   = AppForm("test", "form"),
        searchVersionOpt          = None,
        databaseDataFormatVersion = DataFormatVersion.V400
      ).controls

      assert(indexed.map(_.name) == List("brand", "typeface"))
      assert(indexed.find(_.name == "brand").get.summarySettings == SummarySettings(show = true, search = true, edit = false, editProcess = None, sortDirection = None))
      assert(indexed.find(_.name == "typeface").get.summarySettings == SummarySettings(show = false, search = false, edit = false, editProcess = None, sortDirection = None))
    }

    it("must expose predefined label and custom raw value in static-readonly (View) mode") {

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
                  <brand1>hermes</brand1>
                  <brand2>Olympia</brand2>
                </form>
              </xf:instance>
              <xf:bind ref="brand1 | brand2" readonly="true()"/>
            </xf:model>
          </xh:head>
          <xh:body>
            <xf:select1 id="brand1-control" ref="brand1" appearance="search" selection="open" xxf:readonly-appearance="static">
              <xf:label>Brand 1</xf:label>
              <xf:item>
                <xf:label>Hermes</xf:label>
                <xf:value>hermes</xf:value>
              </xf:item>
            </xf:select1>
            <xf:select1 id="brand2-control" ref="brand2" appearance="search" selection="open" xxf:readonly-appearance="static">
              <xf:label>Brand 2</xf:label>
              <xf:item>
                <xf:label>Hermes</xf:label>
                <xf:value>hermes</xf:value>
              </xf:item>
            </xf:select1>
          </xh:body>
        </xh:html>.toDocument

      withTestExternalContext { _ =>
        withXFormsDocument(xhtml) { doc =>
          val inner1 = doc.getControlByEffectiveId("brand1-control≡select1").asInstanceOf[XFormsSelect1Control]
          val inner2 = doc.getControlByEffectiveId("brand2-control≡select1").asInstanceOf[XFormsSelect1Control]

          assert(inner1.isStaticReadonly)
          assert(inner1.getFormattedValue(EventCollector.Throw).contains("Hermes"))

          assert(inner2.isStaticReadonly)
          assert(inner2.getFormattedValue(EventCollector.Throw).contains("Olympia"))
        }
      }
    }

    it("must adjust Edge control paths to the persistence DataFormatVersion using form migration metadata") {
      val metadataElem = elemToNodeInfo(
        <metadata>
          <migration version="2019.1.0">{"""{"migrations":[{"containerPath":[{"value":"overview-section"}],"newGridElem":{"value":"overview-grid"},"afterElem":null,"content":[{"value":"brand"}],"topLevel":true}]}"""}</migration>
        </metadata>
      )

      assert(
        OpenSelect1SearchSupport.adjustedControlPath(
          controlPathEdge   = "overview-section/overview-grid/brand",
          dataFormatVersion = DataFormatVersion.V400,
          metadataElemOpt   = Some(metadataElem)
        ) == "overview-section/brand"
      )
      assert(
        OpenSelect1SearchSupport.adjustedControlPath(
          controlPathEdge   = "overview-section/overview-grid/brand",
          dataFormatVersion = DataFormatVersion.V20191,
          metadataElemOpt   = Some(metadataElem)
        ) == "overview-section/overview-grid/brand"
      )
    }
  }
}
