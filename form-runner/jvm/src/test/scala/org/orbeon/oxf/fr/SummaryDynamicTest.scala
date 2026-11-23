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

import org.orbeon.dom
import org.orbeon.oxf.fr.library.FormRunnerFunctionLibrary
import org.orbeon.oxf.fr.persistence.relational.RelationalUtils
import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport, XFormsSupport}
import org.orbeon.oxf.util.StringUtils.*
import org.orbeon.oxf.util.{IndentedLogger, StaticXPath}
import org.orbeon.oxf.xforms.analysis.PartAnalysisBuilder
import org.orbeon.oxf.xforms.control.controls.XFormsTriggerControl
import org.orbeon.oxf.xforms.processor.handlers.XHTMLOutput
import org.orbeon.oxf.xforms.state.{AnnotatedTemplate, XFormsStateManager}
import org.orbeon.oxf.xml.dom.Converter.*
import org.orbeon.oxf.xml.dom.Extensions.*
import org.orbeon.oxf.xml.dom.LocationSAXContentHandler
import org.orbeon.scaxon.NodeConversions.*
import org.orbeon.scaxon.SimplePath.*
import org.orbeon.xforms.XFormsCrossPlatformSupport
import org.scalatest.funspec.AnyFunSpecLike

import java.net.URI


class SummaryDynamicTest
  extends DocumentTestBase
     with ResourceManagerSupport
     with AnyFunSpecLike
     with XFormsSupport {

  private implicit val indentedLogger: IndentedLogger = RelationalUtils.newIndentedLogger

  private lazy val summaryTemplateNode = {
    val summaryViewDoc =
      XFormsCrossPlatformSupport.readTinyTreeFromUrl(
        URI.create("oxf:/apps/fr/summary/view.xhtml"),
        handleXInclude = false
      )
    (summaryViewDoc.rootElement descendant "*:instance")
      .find(_.id == "fr-dynamic-instance-template")
      .flatMap(_.firstChildOpt(*))
      .getOrElse(throw new IllegalStateException("Missing `fr-dynamic-instance-template` in `summary/view.xhtml`"))
  }

  describe("`FormRunnerSummary.plainOrFilteredHtml`") {

    it("must escape plain text and sanitize HTML while keeping safe links") {
      assert(FormRunnerSummary.plainOrFilteredHtml(null) == "")
      assert(FormRunnerSummary.plainOrFilteredHtml("   ") == "")
      assert(FormRunnerSummary.plainOrFilteredHtml("A < B & C > D") == "A &lt; B &amp; C &gt; D")

      val sanitized =
        FormRunnerSummary.plainOrFilteredHtml(
          """<div>Hello <b>World</b> <a href="https://orbeon.com">Orbeon</a><script>alert(1)</script></div>"""
        )
      assert(sanitized.contains("<b>World</b>"))
      assert(sanitized.contains("""<a href="https://orbeon.com">Orbeon</a>"""))
      assert(! sanitized.contains("<script"))
    }
  }

  describe("`FormRunnerSummary.buildSummaryDynamicFormImpl`") {

    val sampleSourceForm =
      <xh:html
        xmlns:xh="http://www.w3.org/1999/xhtml"
        xmlns:xf="http://www.w3.org/2002/xforms"
        xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
        xmlns:fr="http://orbeon.org/oxf/xml/form-runner"
        xmlns:xbl="http://www.w3.org/ns/xbl"
        xmlns:my="http://orbeon.org/oxf/xml/form-builder/component/acme/library">
        <xh:head>
          <xf:model id="fr-form-model">
            <xf:instance id="fr-form-instance">
              <form>
                <top-section>
                  <item-name-extra/>
                  <legacy-date/>
                  <legacy-time/>
                  <amount/>
                  <custom-left-num/>
                  <custom-right-text/>
                  <summary-note/>
                  <search-only/>
                </top-section>
                <section-a>
                  <item-grid>
                    <item-name/>
                  </item-grid>
                </section-a>
                <section-b>
                  <item-grid>
                    <item-name/>
                  </item-grid>
                </section-b>
              </form>
            </xf:instance>
            <xf:bind id="fr-form-binds" ref="instance('fr-form-instance')">
              <xf:bind id="top-section-bind" name="top-section" ref="top-section">
                <xf:bind id="item-name-extra-bind" name="item-name-extra" ref="item-name-extra"/>
                <xf:bind id="legacy-date-bind" name="legacy-date" ref="legacy-date" type="xf:date"/>
                <xf:bind id="legacy-time-bind" name="legacy-time" ref="legacy-time" type="xs:time"/>
                <xf:bind id="amount-bind" name="amount" ref="amount" type="xs:decimal"/>
                <xf:bind id="custom-left-num-bind" name="custom-left-num" ref="custom-left-num" type="xs:decimal"/>
                <xf:bind id="custom-right-text-bind" name="custom-right-text" ref="custom-right-text"/>
                <xf:bind id="summary-note-bind" name="summary-note" ref="summary-note"/>
                <xf:bind id="search-only-bind" name="search-only" ref="search-only"/>
              </xf:bind>
              <xf:bind id="section-a-bind" name="section-a" ref="section-a"/>
              <xf:bind id="section-b-bind" name="section-b" ref="section-b"/>
            </xf:bind>
            <xf:instance id="fr-form-metadata" xxf:readonly="true">
              <metadata>
                <application-name>acme</application-name>
                <form-name>order</form-name>
                <title xml:lang="en">Order</title>
              </metadata>
            </xf:instance>
            <xf:instance id="fr-form-resources" xxf:readonly="true">
              <resources>
                <resource xml:lang="en">
                  <top-section><label>Top</label></top-section>
                  <section-a><label>Section A</label></section-a>
                  <section-b><label>Section B</label></section-b>
                  <item-name-extra><label>Extra</label></item-name-extra>
                  <legacy-date><label>Legacy Date</label></legacy-date>
                  <legacy-time><label>Legacy Time</label></legacy-time>
                  <amount><label>Amount</label></amount>
                  <custom-left-num><label>Left Num</label></custom-left-num>
                  <custom-right-text><label>Right Text</label></custom-right-text>
                  <summary-note><label>Summary Note</label></summary-note>
                  <search-only><label>Search Only</label></search-only>
                </resource>
              </resources>
            </xf:instance>
          </xf:model>
          <xbl:xbl>
            <xbl:binding id="acme-item-section-component" element="my|item-section" class="fr-section-component">
              <xbl:implementation>
                <xf:model id="item-section-model">
                  <xf:instance id="fr-form-template">
                    <item-section>
                      <item-grid>
                        <item-name/>
                      </item-grid>
                    </item-section>
                  </xf:instance>
                  <xf:bind id="fr-form-binds" ref="instance('fr-form-instance')">
                    <xf:bind id="item-grid-bind" name="item-grid" ref="item-grid">
                      <xf:bind id="item-name-bind" name="item-name" ref="item-name"/>
                    </xf:bind>
                  </xf:bind>
                  <xf:instance id="fr-form-resources" xxf:readonly="true">
                    <resources>
                      <resource xml:lang="en">
                        <item-name>
                          <label>Template Item</label>
                          <item><label>Alpha</label><value>a</value></item>
                        </item-name>
                        <item-name-extra><label>Extra Hint</label></item-name-extra>
                      </resource>
                    </resources>
                  </xf:instance>
                </xf:model>
              </xbl:implementation>
              <xbl:template>
                <xf:group appearance="xxf:internal" model="item-section-model">
                  <fr:grid id="item-grid-grid" bind="item-grid-bind">
                    <fr:c x="1" y="1" w="12">
                      <xf:select1 id="item-name-control" bind="item-name-bind" appearance="cards character-counter">
                        <fr:index><fr:summary-show/></fr:index>
                        <xf:label ref="$form-resources/item-name/label"/>
                        <xf:hint ref="$form-resources/item-name-extra/label"/>
                        <xf:itemset ref="$form-resources/item-name/item">
                          <xf:label ref="label"/>
                          <xf:value ref="value"/>
                        </xf:itemset>
                      </xf:select1>
                    </fr:c>
                  </fr:grid>
                </xf:group>
              </xbl:template>
            </xbl:binding>
          </xbl:xbl>
        </xh:head>
        <xh:body>
          <fr:view>
            <fr:body>
              <fr:section id="top-section-section" bind="top-section-bind">
                <fr:grid id="grid-top">
                  <fr:c x="1" y="1" w="6">
                    <xf:input id="item-name-extra-control" bind="item-name-extra-bind" appearance="character-counter xxf:autosize">
                      <fr:index><fr:summary-show/></fr:index>
                      <xf:label ref="$form-resources/item-name-extra/label"/>
                    </xf:input>
                  </fr:c>
                  <fr:c x="1" y="2" w="6">
                    <xf:input id="legacy-date-control" bind="legacy-date-bind">
                      <fr:index><fr:summary-show/></fr:index>
                      <xf:label ref="$form-resources/legacy-date/label"/>
                    </xf:input>
                  </fr:c>
                  <fr:c x="7" y="2" w="6">
                    <xf:input id="legacy-time-control" bind="legacy-time-bind">
                      <fr:index><fr:summary-show/></fr:index>
                      <xf:label ref="$form-resources/legacy-time/label"/>
                    </xf:input>
                  </fr:c>
                  <fr:c x="1" y="3" w="4">
                    <fr:currency id="amount-control" bind="amount-bind" prefix="$">
                      <fr:index><fr:summary-show/></fr:index>
                      <xf:label ref="$form-resources/amount/label"/>
                    </fr:currency>
                  </fr:c>
                  <fr:c x="5" y="3" w="4">
                    <fr:number id="custom-left-num-control" bind="custom-left-num-bind" field-align="left">
                      <fr:index><fr:summary-show/></fr:index>
                      <xf:label ref="$form-resources/custom-left-num/label"/>
                    </fr:number>
                  </fr:c>
                  <fr:c x="9" y="3" w="4">
                    <xf:input id="custom-right-text-control" bind="custom-right-text-bind" field-align="right">
                      <fr:index><fr:summary-show/></fr:index>
                      <xf:label ref="$form-resources/custom-right-text/label"/>
                    </xf:input>
                  </fr:c>
                  <fr:c x="1" y="4" w="6">
                    <xf:output id="summary-note-control" bind="summary-note-bind" value="concat($amount, ':', $search-only, ':', $unindexed-var)" xxf:functions="all">
                      <fr:index><fr:summary-show/></fr:index>
                      <xf:label ref="$form-resources/summary-note/label"/>
                    </xf:output>
                  </fr:c>
                  <fr:c x="7" y="4" w="6">
                    <xf:input id="search-only-control" bind="search-only-bind">
                      <fr:index><fr:summary-show column="false" search="true"/></fr:index>
                      <xf:label ref="$form-resources/search-only/label"/>
                    </xf:input>
                  </fr:c>
                </fr:grid>
              </fr:section>
              <fr:section id="section-a-section" bind="section-a-bind">
                <my:item-section id="section-a-content-control"/>
              </fr:section>
              <fr:section id="section-b-section" bind="section-b-bind">
                <my:item-section id="section-b-content-control"/>
              </fr:section>
            </fr:body>
          </fr:view>
        </xh:body>
      </xh:html>

    it("must convert legacy date/time inputs, sanitize controls, align columns, disambiguate resources, and inject variables") {
      val builtNode =
        FormRunnerSummary.buildSummaryDynamicFormImpl(
          formDoc                   = elemToDocumentInfo(sampleSourceForm),
          templateNode              = summaryTemplateNode,
          appForm                   = AppForm("acme", "order"),
          databaseDataFormatVersion = DataFormatVersion.V20191,
          resourcesRootElemOpt      = None,
          functionLibrary           = FormRunnerFunctionLibrary
        )

      val domRoot  = StaticXPath.tinyTreeToOrbeonDom(builtNode).getRootElement
      val allElems = domRoot.descendantElementIterator(includeSelf = true).toList

      // Custom namespace copied from source form root
      assert(domRoot.getNamespaceForPrefix("my") != null)
      assert(domRoot.getNamespaceForPrefix("my").uri == "http://orbeon.org/oxf/xml/form-builder/component/acme/library")

      // Binds created for all 10 indexed controls (8 top-level + 2 section-template controls; `search-only` is pos 8)
      val colBinds = allElems.filter(e => e.getName == "bind" && e.attributeValueOpt("id").exists(_.startsWith("fr-col-")))
      assert(colBinds.size == 10)
      assert(colBinds.exists(b => b.attributeValue("id") == "fr-col-8-bind" && b.attributeValue("ref").contains("top-section/search-only")))

      // 9 visible summary columns (`search-only` has `column="false"`)
      val cellGroups = allElems.filter(_.attributeValue("class") == "fr-summary-cell-value")
      assert(cellGroups.size == 9)
      assert(cellGroups.forall(_.attributeValue("functions") == "restricted"))

      val controls = cellGroups.flatMap(_.elements)
      assert(controls.size == 9)

      // 1. Legacy date/time converted to `fr:date` and `fr:time`
      assert(controls(1).getQualifiedName == "fr:date")
      assert(controls(2).getQualifiedName == "fr:time")

      // 2. Controls marked `fr:static-readonly="true"` and `xxf:readonly-appearance="static"`, LHHA & `fr:index` stripped,
      // edit-only appearances removed while preserving `cards`, and author `xxf:functions` stripped
      assert(controls.forall(_.attributeValue("static-readonly") == "true"))
      assert(controls.forall(_.attributeValue("readonly-appearance") == "static"))
      assert(controls.forall(c => ! c.elements.exists(ch => Set("label", "hint", "help", "alert", "index")(ch.getName))))

      assert(controls(0).attributeValueOpt("appearance").isEmpty)
      assert(controls(6).attributeValueOpt("functions").isEmpty)
      assert(controls(7).attributeValueOpt("appearance").contains("cards"))
      assert(controls(8).attributeValueOpt("appearance").contains("cards"))

      // 3. Right alignment: `fr:currency` (visible col index 3) and `field-align="right"` (visible col index 5) are right-aligned;
      // `fr:number` with `field-align="left"` (visible col index 4) is NOT right-aligned
      val controlSortableThs =
        allElems.filter(e => e.getQualifiedName == "fr:sortable-th" && e.attributeValueOpt("column").exists(_.contains("/")))
      assert(controlSortableThs.size == 9)
      assert(controlSortableThs(3).attributeValueOpt("class").contains("fr-align-right"))
      assert(controlSortableThs(4).attributeValueOpt("class").isEmpty)
      assert(controlSortableThs(5).attributeValueOpt("class").contains("fr-align-right"))

      // 4. Duplicate `item-name` controls in `section-a` (detailPos 9) and `section-b` (detailPos 10) get disambiguated resources
      val itemset9Ref  = controls(7).element("itemset").attributeValue("ref")
      val itemset10Ref = controls(8).element("itemset").attributeValue("ref")
      assert(itemset9Ref == "$form-resources/fr-summary-col-9/item")
      assert(itemset10Ref == "$form-resources/fr-summary-col-10/item")

      val resourcesInst = allElems.find(e => e.getName == "instance" && e.attributeValue("id") == "fr-form-resources").get
      val enResource    = resourcesInst.element("resources").element("resource")
      assert(enResource.element("fr-summary-col-9") != null)
      assert(enResource.element("fr-summary-col-10") != null)

      // 5. Referenced `$amount`, `$search-only`, and `$unindexed-var` variables injected into `documents-repeat`
      val repeatElem   = allElems.find(_.attributeValue("id") == "documents-repeat").get
      val injectedVars = repeatElem.elements.filter(_.getName == "var").map(v => v.attributeValue("name") -> v.attributeValue("value")).toMap
      assert(injectedVars("amount") == "details/(detail[@path = 'top-section/amount'], detail[4])[1]")
      assert(injectedVars("search-only") == "details/(detail[@path = 'top-section/search-only'], detail[8])[1]")
      assert(injectedVars("unindexed-var") == "()")
    }

    it("must not add `xxf:functions=\"restricted\"` when building the Form Builder Summary dynamic form") {
      val builtFbNode =
        FormRunnerSummary.buildSummaryDynamicFormImpl(
          formDoc                   = elemToDocumentInfo(sampleSourceForm),
          templateNode              = summaryTemplateNode,
          appForm                   = AppForm.FormBuilder,
          databaseDataFormatVersion = DataFormatVersion.V20191,
          resourcesRootElemOpt      = None,
          functionLibrary           = FormRunnerFunctionLibrary
        )

      val domRoot    = StaticXPath.tinyTreeToOrbeonDom(builtFbNode).getRootElement
      val cellGroups = domRoot.descendantElementIterator(includeSelf = true).filter(_.attributeValue("class") == "fr-summary-cell-value").toList
      assert(cellGroups.nonEmpty)
      assert(cellGroups.forall(_.attributeValueOpt("functions").isEmpty))
    }

    it("must place non-indexed computed `<fr:summary-show/>` columns in source document order without shifting indexed detail positions") {
      val formWithComputedCol =
        <xh:html
          xmlns:xh="http://www.w3.org/1999/xhtml"
          xmlns:xf="http://www.w3.org/2002/xforms"
          xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
          xmlns:fr="http://orbeon.org/oxf/xml/form-runner">
          <xh:head>
            <xf:model id="fr-form-model">
              <xf:instance id="fr-form-instance">
                <form>
                  <main>
                    <first-col/>
                    <third-col/>
                  </main>
                </form>
              </xf:instance>
              <xf:bind id="fr-form-binds" ref="instance('fr-form-instance')">
                <xf:bind id="main-bind" name="main" ref="main">
                  <xf:bind id="first-col-bind" name="first-col" ref="first-col"/>
                  <xf:bind id="third-col-bind" name="third-col" ref="third-col"/>
                </xf:bind>
              </xf:bind>
              <xf:instance id="fr-form-metadata" xxf:readonly="true">
                <metadata>
                  <application-name>acme</application-name>
                  <form-name>computed</form-name>
                </metadata>
              </xf:instance>
              <xf:instance id="fr-form-resources" xxf:readonly="true">
                <resources>
                  <resource xml:lang="en">
                    <main><label>Main</label></main>
                    <first-col><label>First</label></first-col>
                    <computed-middle><label>Computed Middle</label></computed-middle>
                    <third-col><label>Third</label></third-col>
                  </resource>
                </resources>
              </xf:instance>
            </xf:model>
          </xh:head>
          <xh:body>
            <fr:view>
              <fr:body>
                <fr:section id="main-section" bind="main-bind">
                  <fr:grid id="main-grid">
                    <fr:c x="1" y="1" w="4">
                      <xf:input id="first-col-control" bind="first-col-bind">
                        <xf:label ref="$form-resources/first-col/label"/>
                        <fr:index><fr:summary-show/></fr:index>
                      </xf:input>
                    </fr:c>
                    <fr:c x="5" y="1" w="4">
                      <xf:output id="computed-middle-control" value="concat($first-col, '-', $third-col)">
                        <xf:label ref="$form-resources/computed-middle/label"/>
                        <fr:summary-show/>
                      </xf:output>
                    </fr:c>
                    <fr:c x="9" y="1" w="4">
                      <xf:input id="third-col-control" bind="third-col-bind">
                        <xf:label ref="$form-resources/third-col/label"/>
                        <fr:index><fr:summary-show/></fr:index>
                      </xf:input>
                    </fr:c>
                  </fr:grid>
                </fr:section>
              </fr:body>
            </fr:view>
          </xh:body>
        </xh:html>

      val docInfo = elemToDocumentInfo(formWithComputedCol)
      val appForm = AppForm("acme", "computed")

      // Non-indexed `<fr:summary-show/>` control must not appear in `Index.searchableValues`
      val indexed =
        org.orbeon.oxf.fr.persistence.relational.index.Index.searchableValues(
          formDoc                   = docInfo,
          appForm                   = appForm,
          searchVersionOpt          = None,
          databaseDataFormatVersion = DataFormatVersion.V20191
        )
      assert(indexed.controls.map(_.name) == List("first-col", "third-col"))

      val builtNode =
        FormRunnerSummary.buildSummaryDynamicFormImpl(
          formDoc                   = docInfo,
          templateNode              = summaryTemplateNode,
          appForm                   = appForm,
          databaseDataFormatVersion = DataFormatVersion.V20191,
          resourcesRootElemOpt      = None,
          functionLibrary           = FormRunnerFunctionLibrary
        )

      val domRoot = StaticXPath.tinyTreeToOrbeonDom(builtNode).getRootElement

      // Header order: `main/first-col` (sortable), `computed-middle` (non-sortable), `main/third-col` (sortable)
      val sortableThs =
        domRoot.descendantElementIterator(includeSelf = true)
          .filter(_.getName == "sortable-th")
          .filter(_.attributeValue("column") != "{$column}")
          .toList

      assert(sortableThs.map(th => th.attributeValue("column") -> th.attributeValue("sort-supported")) == List(
        "main/first-col"  -> "{$sort-supported}",
        "computed-middle" -> "false",
        "main/third-col"  -> "{$sort-supported}"
      ))

      // `fr-col-2-bind` still points to `detail[2]` (`main/third-col`), unaffected by the computed middle column
      val col2Bind = domRoot.descendantElementIterator(includeSelf = true).find(_.attributeValue("id") == "fr-col-2-bind").get
      assert(col2Bind.attributeValue("ref") == "(detail[@path = 'main/third-col'], detail[2])[1]")

      // Computed column keeps its `value` attribute, has no `bind`, and strips `<fr:summary-show/>`
      val computedCtrl = domRoot.descendantElementIterator(includeSelf = true).find(_.attributeValue("id") == "fr-summary-computed-col-1-control").get
      assert(computedCtrl.attributeValueOpt("bind").isEmpty)
      assert(computedCtrl.attributeValue("value") == "concat($first-col, '-', $third-col)")
      assert(! computedCtrl.elements.exists(_.getName == "summary-show"))

      // Referenced variables `$first-col` and `$third-col` are injected into `documents-repeat`
      val repeatElem = domRoot.descendantElementIterator(includeSelf = true).find(_.attributeValue("id") == "documents-repeat").get
      val injectedVarNames = repeatElem.elements.filter(_.getName == "var").map(_.attributeValue("name")).toSet
      assert(injectedVarNames.contains("first-col"))
      assert(injectedVarNames.contains("third-col"))
    }
  }

  describe("Live `<xxf:dynamic>` Summary table rendering and interaction") {

    it("must render XBL and selection controls in static-readonly mode, preserve sibling cell links, sort via `fr:sortable-th`, and support dynamic search updates") {

      val sourceForm =
        <xh:html
          xmlns:xh="http://www.w3.org/1999/xhtml"
          xmlns:xf="http://www.w3.org/2002/xforms"
          xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
          xmlns:fr="http://orbeon.org/oxf/xml/form-runner">
          <xh:head>
            <xf:model id="fr-form-model">
              <xf:instance id="fr-form-instance">
                <form>
                  <main>
                    <qty/>
                    <price/>
                    <ratio/>
                    <tier/>
                    <approved/>
                    <other-choice/>
                    <code/>
                    <tags/>
                    <phone/>
                    <website/>
                    <computed/>
                  </main>
                </form>
              </xf:instance>
              <xf:bind id="fr-form-binds" ref="instance('fr-form-instance')">
                <xf:bind id="main-bind" name="main" ref="main">
                  <xf:bind id="qty-bind" name="qty" ref="qty" type="xs:decimal"/>
                  <xf:bind id="price-bind" name="price" ref="price" type="xs:decimal"/>
                  <xf:bind id="ratio-bind" name="ratio" ref="ratio" type="xs:decimal"/>
                  <xf:bind id="tier-bind" name="tier" ref="tier"/>
                  <xf:bind id="approved-bind" name="approved" ref="approved" type="xs:boolean"/>
                  <xf:bind id="other-choice-bind" name="other-choice" ref="other-choice"/>
                  <xf:bind id="code-bind" name="code" ref="code"/>
                  <xf:bind id="tags-bind" name="tags" ref="tags"/>
                  <xf:bind id="phone-bind" name="phone" ref="phone" type="fr:phone"/>
                  <xf:bind id="website-bind" name="website" ref="website"/>
                  <xf:bind id="computed-bind" name="computed" ref="computed"/>
                </xf:bind>
              </xf:bind>
              <xf:instance id="fr-form-metadata" xxf:readonly="true">
                <metadata>
                  <application-name>sqlite</application-name>
                  <form-name>invoice</form-name>
                  <title xml:lang="en">Invoice</title>
                  <summary-metadata-columns>
                    <column name="created" show="false"/>
                    <column name="last-modified" show="true"/>
                    <column name="workflow-stage" show="false"/>
                    <column name="created-by" show="false"/>
                    <column name="last-modified-by" show="false"/>
                  </summary-metadata-columns>
                </metadata>
              </xf:instance>
              <xf:instance id="fr-form-resources" xxf:readonly="true">
                <resources>
                  <resource xml:lang="en">
                    <main><label>Main</label></main>
                    <qty><label>Quantity</label></qty>
                    <price><label>Price</label></price>
                    <ratio><label>Ratio</label></ratio>
                    <tier>
                      <label>Tier</label>
                      <item><label>Starter</label><value>starter</value><hint>Basic</hint></item>
                      <item><label>Professional</label><value>pro</value><hint>Full</hint></item>
                    </tier>
                    <approved><label>Approved</label></approved>
                    <other-choice>
                      <label>Choice</label>
                      <item><label>Option One</label><value>one</value></item>
                    </other-choice>
                    <code><label>Code</label></code>
                    <tags><label>Tags</label></tags>
                    <phone><label>Phone</label></phone>
                    <website><label>Website</label></website>
                    <computed><label>Computed</label></computed>
                  </resource>
                </resources>
              </xf:instance>
            </xf:model>
          </xh:head>
          <xh:body>
            <fr:view>
              <fr:body>
                <fr:section id="main-section" bind="main-bind">
                  <fr:grid id="main-grid">
                    <fr:c x="1" y="1" w="3">
                      <fr:number id="qty-control" bind="qty-bind" decimal-separator="." grouping-separator=",">
                        <fr:index><fr:summary-show/></fr:index>
                        <xf:label ref="$form-resources/qty/label"/>
                      </fr:number>
                    </fr:c>
                    <fr:c x="4" y="1" w="3">
                      <fr:currency id="price-control" bind="price-bind" prefix="$" digits-after-decimal="2" decimal-separator="." grouping-separator=",">
                        <fr:index><fr:summary-show/></fr:index>
                        <xf:label ref="$form-resources/price/label"/>
                      </fr:currency>
                    </fr:c>
                    <fr:c x="7" y="1" w="3">
                      <fr:percentage id="ratio-control" bind="ratio-bind">
                        <fr:index><fr:summary-show/></fr:index>
                        <xf:label ref="$form-resources/ratio/label"/>
                      </fr:percentage>
                    </fr:c>
                    <fr:c x="10" y="1" w="3">
                      <xf:select1 id="tier-control" bind="tier-bind" appearance="cards">
                        <fr:index><fr:summary-show/></fr:index>
                        <xf:label ref="$form-resources/tier/label"/>
                        <xf:itemset ref="$form-resources/tier/item">
                          <xf:label ref="label"/>
                          <xf:value ref="value"/>
                          <xf:hint ref="hint"/>
                        </xf:itemset>
                      </xf:select1>
                    </fr:c>
                    <fr:c x="1" y="2" w="3">
                      <fr:checkbox-input id="approved-control" bind="approved-bind">
                        <fr:index><fr:summary-show/></fr:index>
                        <xf:label ref="$form-resources/approved/label"/>
                      </fr:checkbox-input>
                    </fr:c>
                    <fr:c x="4" y="2" w="3">
                      <fr:open-select1 id="other-choice-control" bind="other-choice-bind">
                        <fr:index><fr:summary-show/></fr:index>
                        <xf:label ref="$form-resources/other-choice/label"/>
                        <xf:itemset ref="$form-resources/other-choice/item">
                          <xf:label ref="label"/>
                          <xf:value ref="value"/>
                        </xf:itemset>
                      </fr:open-select1>
                    </fr:c>
                    <fr:c x="7" y="2" w="3">
                      <fr:code-input id="code-control" bind="code-bind">
                        <fr:index><fr:summary-show/></fr:index>
                        <xf:label ref="$form-resources/code/label"/>
                      </fr:code-input>
                    </fr:c>
                    <fr:c x="10" y="2" w="3">
                      <fr:tags-input id="tags-control" bind="tags-bind">
                        <fr:index><fr:summary-show/></fr:index>
                        <xf:label ref="$form-resources/tags/label"/>
                      </fr:tags-input>
                    </fr:c>
                    <fr:c x="1" y="3" w="4">
                      <fr:phone id="phone-control" bind="phone-bind">
                        <fr:index><fr:summary-show/></fr:index>
                        <xf:label ref="$form-resources/phone/label"/>
                      </fr:phone>
                    </fr:c>
                    <fr:c x="5" y="3" w="4">
                      <fr:url id="website-control" bind="website-bind">
                        <fr:index><fr:summary-show/></fr:index>
                        <xf:label ref="$form-resources/website/label"/>
                      </fr:url>
                    </fr:c>
                    <fr:c x="9" y="3" w="4">
                      <xf:output id="computed-control" bind="computed-bind" value="concat('Qty=', $qty)">
                        <fr:index><fr:summary-show/></fr:index>
                        <xf:label ref="$form-resources/computed/label"/>
                      </xf:output>
                    </fr:c>
                  </fr:grid>
                </fr:section>
              </fr:body>
            </fr:view>
          </xh:body>
        </xh:html>

      val dynamicNode =
        FormRunnerSummary.buildSummaryDynamicFormImpl(
          formDoc                   = elemToDocumentInfo(sourceForm),
          templateNode              = summaryTemplateNode,
          appForm                   = AppForm("sqlite", "invoice"),
          databaseDataFormatVersion = DataFormatVersion.V20191,
          resourcesRootElemOpt      = None,
          functionLibrary           = FormRunnerFunctionLibrary
        )

      val dynamicRootElem = StaticXPath.tinyTreeToOrbeonDom(dynamicNode).getRootElement
      // Populate initial search response inside `<xf:instance id="fr-form-instance">` of the dynamic document
      val innerFormInstance =
        dynamicRootElem
          .descendantElementIterator(includeSelf = true)
          .find(e => e.getName == "instance" && e.attributeValue("id") == "fr-form-instance")
          .get
      innerFormInstance.clearContent()
      innerFormInstance.add(
        <documents search-total="2">
          <document
            name="doc-1"
            created="2026-05-01T10:00:00Z"
            last-modified="2026-05-02T12:00:00Z"
            operations="*">
            <details>
              <detail path="main/qty">1234.5</detail>
              <detail path="main/price">99.95</detail>
              <detail path="main/ratio">0.42</detail>
              <detail path="main/tier">pro</detail>
              <detail path="main/approved">true</detail>
              <detail path="main/other-choice"/>
              <detail path="main/code">482910</detail>
              <detail path="main/tags">urgent billing</detail>
              <detail path="main/phone">+33123456789</detail>
              <detail path="main/website">https://www.orbeon.com</detail>
              <detail path="main/computed"/>
            </details>
          </document>
          <document
            name="doc-2"
            created="2026-05-03T10:00:00Z"
            last-modified="2026-05-04T12:00:00Z"
            operations="read">
            <details>
              <detail path="main/qty">7</detail>
              <detail path="main/price">15</detail>
              <detail path="main/ratio">0.05</detail>
              <detail path="main/tier">starter</detail>
              <detail path="main/approved">false</detail>
              <detail path="main/other-choice">Custom Value</detail>
              <detail path="main/code">901234</detail>
              <detail path="main/tags">review</detail>
              <detail path="main/phone">+14155550100</detail>
              <detail path="main/website">https://doc.orbeon.com</detail>
              <detail path="main/computed"/>
            </details>
          </document>
        </documents>.toDocument.getRootElement.createCopy
      )

      val hostDocDom =
        <xh:html
          xmlns:xh="http://www.w3.org/1999/xhtml"
          xmlns:xf="http://www.w3.org/2002/xforms"
          xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
          xmlns:fr="http://orbeon.org/oxf/xml/form-runner"
          xmlns:frf="java:org.orbeon.oxf.fr.FormRunner">
          <xh:head>
            <xf:model
              id="fr-form-model"
              xxf:lang="en"
              xxf:function-library="org.orbeon.oxf.fr.library.FormRunnerFunctionLibrary"
              xxf:readonly-appearance.static.select="minimal"
              xxf:readonly-appearance.static.select1="minimal">
              <xf:instance id="fr-parameters-instance">
                <parameters>
                  <app>sqlite</app>
                  <form>invoice</form>
                  <form-version>1</form-version>
                  <mode>summary</mode>
                </parameters>
              </xf:instance>
              <xf:instance id="fr-language-instance"><lang>en</lang></xf:instance>
              <xf:instance id="fr-authorized-operations"><operations>*</operations></xf:instance>
              <xf:instance id="fr-selection-instance"><selection/></xf:instance>
              <xf:instance id="fr-order-by">
                <order-by>
                  <column>last-modified</column>
                  <direction>desc</direction>
                </order-by>
              </xf:instance>
              <xf:instance id="fr-search-instance">
                <search>
                  <query/>
                  <query metadata="last-modified" match="gte" sort="desc"/>
                  <query path="main/qty"/>
                </search>
              </xf:instance>
              <xf:instance id="fr-search-labels-instance">
                <searchable-values>
                  <query path="main/qty"><resources lang="en"><label>Quantity</label></resources></query>
                  <query path="main/price"><resources lang="en"><label>Price</label></resources></query>
                  <query path="main/ratio"><resources lang="en"><label>Ratio</label></resources></query>
                  <query path="main/tier"><resources lang="en"><label>Tier</label></resources></query>
                  <query path="main/approved"><resources lang="en"><label>Approved</label></resources></query>
                  <query path="main/other-choice"><resources lang="en"><label>Choice</label></resources></query>
                  <query path="main/code"><resources lang="en"><label>Code</label></resources></query>
                  <query path="main/tags"><resources lang="en"><label>Tags</label></resources></query>
                  <query path="main/phone"><resources lang="en"><label>Phone</label></resources></query>
                  <query path="main/website"><resources lang="en"><label>Website</label></resources></query>
                  <query path="main/computed"><resources lang="en"><label>Computed</label></resources></query>
                </searchable-values>
              </xf:instance>
              <xf:instance id="fr-source-form-instance">
                <xh:html>
                  <xh:head>
                    <xf:model>
                      <xf:instance id="fr-form-metadata">
                        <metadata>
                          <summary-metadata-columns>
                            <column name="created" show="false"/>
                            <column name="last-modified" show="true"/>
                            <column name="workflow-stage" show="false"/>
                            <column name="created-by" show="false"/>
                            <column name="last-modified-by" show="false"/>
                          </summary-metadata-columns>
                        </metadata>
                      </xf:instance>
                    </xf:model>
                  </xh:head>
                </xh:html>
              </xf:instance>
              <xf:instance id="fr-fr-resources">
                <resources>
                  <resource xml:lang="en">
                    <common>
                      <formats>
                        <within-day>[h]:[m01] [P]</within-day>
                        <day-this-year>[MNn] [D]</day-this-year>
                        <short-date>[M01]/[D01]/[Y01]</short-date>
                      </formats>
                    </common>
                    <components>
                      <labels>
                        <please-select>Please select:</please-select>
                        <other>Other</other>
                        <open-in-new-tab>Open in new tab</open-in-new-tab>
                      </labels>
                    </components>
                    <summary>
                      <titles>
                        <edit>Edit</edit>
                        <view>View</view>
                        <last-modified>Modified</last-modified>
                        <selected>Selected</selected>
                      </titles>
                      <labels>
                        <draft>Draft</draft>
                        <read-only>Read-only</read-only>
                        <select-all>Select all</select-all>
                      </labels>
                    </summary>
                  </resource>
                </resources>
              </xf:instance>
              <xf:instance id="fr-dynamic-instance"/>
              <xf:action event="fr-order-by">
                <xf:setvalue ref="instance('fr-order-by')/column"    value="event('column')"/>
                <xf:setvalue ref="instance('fr-order-by')/direction" value="'asc'"/>
                <xf:setvalue ref="instance('fr-selection-instance')" value="concat('sorted:', instance('fr-order-by')/column, ':', instance('fr-order-by')/direction)"/>
              </xf:action>
              <xf:action event="fr-clear-documents">
                <xf:delete ref="instance('fr-dynamic-instance')//xf:instance[@id = 'fr-form-instance']/documents/document"/>
              </xf:action>
            </xf:model>
          </xh:head>
          <xh:body>
            <xxf:dynamic id="fr-summary-dynamic" ref="instance('fr-dynamic-instance')" class="fr-dynamic"/>
          </xh:body>
        </xh:html>.toDocument

      val hostDynamicInst =
        hostDocDom.getRootElement
          .descendantElementIterator(includeSelf = true)
          .find(e => e.getName == "instance" && e.attributeValue("id") == "fr-dynamic-instance")
          .get
      hostDynamicInst.add(dynamicRootElem)

      withTestExternalContext { implicit ec =>
        withXFormsDocument(hostDocDom) { doc =>
          withActionAndDoc(doc) {

            // Summary page runs with `containingDocument.staticReadonly == false`
            assert(! doc.staticReadonly)

            val template = AnnotatedTemplate(PartAnalysisBuilder.createFromDocument(hostDocDom)(XFormsStateManager.newIndentedLogger)._1)

            def renderXhtml(): dom.Document = {
              val receiver = new LocationSAXContentHandler
              XHTMLOutput.send(doc, template, ec)(receiver)
              receiver.getDocument
            }

            val rendered1 = renderXhtml()
            val allElems1 = rendered1.getRootElement.descendantElementIterator(includeSelf = true).toList

            // 1. No nested `<a>` tags anywhere in the rendered Summary table (even with `fr:url` rendering an inner `<a>`)
            val anchors = allElems1.filter(_.getName == "a")
            assert(anchors.nonEmpty)
            assert(anchors.forall(a => ! a.descendantElementIterator(includeSelf = false).exists(_.getName == "a")))

            // Metadata and data `.fr-summary-cell-link` anchors point to `/fr/sqlite/invoice/edit/doc-1` (row 1) and `/fr/sqlite/invoice/view/doc-2` (row 2)
            val cellLinks = anchors.filter(_.attributeValueOpt("class").exists(_.splitTo[Set]().contains("fr-summary-cell-link")))
            assert(cellLinks.size == 24)
            assert(cellLinks.take(12).forall(_.attributeValue("href") == "/fr/sqlite/invoice/edit/doc-1"))
            assert(cellLinks.drop(12).forall(_.attributeValue("href") == "/fr/sqlite/invoice/view/doc-2"))
            assert(cellLinks.forall(a => ! a.attributeValueOpt("tabindex").contains("-1")))

            // Data cell `.fr-summary-cell-link` anchors reference their sibling `.fr-summary-cell-value` container via `aria-labelledby`
            val dataCellLinks = cellLinks.filter(_.attributeValueOpt("aria-labelledby").isDefined)
            assert(dataCellLinks.size == 22)

            // Unlabeled static-readonly `.xforms-field` elements inside `.fr-summary-cell-value` do not add extra `tabindex` stops
            val summaryFields =
              allElems1
                .filter(_.attributeValueOpt("class").exists(_.splitTo[Set]().contains("fr-summary-cell-value")))
                .flatMap(_.descendantElementIterator(includeSelf = false))
                .filter(_.attributeValueOpt("class").exists(_.splitTo[Set]().contains("xforms-field")))
            assert(summaryFields.nonEmpty)
            assert(summaryFields.forall(_.attributeValueOpt("tabindex").isEmpty))

            // Inner `fr:url` anchors point to `https://www.orbeon.com` and `https://doc.orbeon.com`
            assert(anchors.exists(_.attributeValue("href") == "https://www.orbeon.com"))
            assert(anchors.exists(_.attributeValue("href") == "https://doc.orbeon.com"))

            // Nested XBL CSS assets and `<xf:action type="javascript">` scripts from `xxf:dynamic` are exposed for client loading
            assert(doc.staticOps.bindingAssets.css.exists(_.toString.contains("open-select.css")))
            assert(doc.staticOps.bindingAssets.css.exists(_.toString.contains("phone.css")))
            doc.setInitializationData()
            val dynamicJsOpt = doc.getInitializationData.flatMap(_._3)
            assert(dynamicJsOpt.exists(_.contains("function xf_")))

            // 2. Formatted XBL & selection values inside `.fr-summary-cell-value` across BOTH rows
            val cellValueDivs =
              allElems1.filter(_.attributeValueOpt("class").exists(_.splitTo[Set]().contains("fr-summary-cell-value")))
            assert(cellValueDivs.size == 22)

            def visibleText(elem: dom.Element): String = {
              val classes = elem.attributeValueOpt("class").map(_.splitTo[Set]()).getOrElse(Set.empty)
              if (classes.contains("xforms-hidden") || classes.contains("xforms-disabled"))
                ""
              else
                elem.nodeIterator.map {
                  case child: dom.Element => visibleText(child)
                  case text: dom.Text     => text.getText
                  case _                  => ""
                }.mkString
            }

            def cellText(idx: Int): String =
              visibleText(cellValueDivs(idx)).trimAllToEmpty

            assert(cellText(0) == "1,234.5")
            assert(cellText(1) == "$ 99.95")
            assert(cellText(2) == "42 %")
            assert(cellText(11) == "7")
            assert(cellText(12) == "$ 15.00")
            assert(cellText(13) == "5 %")
            assert(cellText(14) == "Starter")
            assert(cellText(16) == "Custom Value")
            assert(cellText(21) == "Qty=7")

            // Multi-choice `cards` select1 renders minimal selected item label ("Professional") with zero `<input type="radio">`
            assert(cellText(3) == "Professional")
            assert(! cellValueDivs(3).descendantElementIterator(includeSelf = true).exists(e => e.getName == "input" && e.attributeValue("type") == "radio"))

            // Single-item boolean `fr:checkbox-input` keeps its static checkbox `<span role="checkbox" aria-checked="true" aria-readonly="true">`
            val boolCheckboxes =
              cellValueDivs(4).descendantElementIterator(includeSelf = true)
                .filter(e => e.getName == "span" && e.attributeValue("role") == "checkbox")
                .toList
            assert(boolCheckboxes.size == 1)
            assert(boolCheckboxes.head.attributeValue("aria-checked") == "true")
            assert(boolCheckboxes.head.attributeValue("aria-readonly") == "true")
            assert(boolCheckboxes.head.attributeValue("class").splitTo[Set]().contains("xforms-selected"))

            // Blank `fr:open-select1` renders empty string rather than "Other"
            assert(cellText(5) == "")

            // `fr:code-input`, `fr:tags-input` (static pills), `fr:phone`, and `$qty` variable reference in `computed`
            assert(cellText(6) == "482910")
            val staticPillLabels =
              cellValueDivs(7).descendantElementIterator(includeSelf = true)
                .filter(_.attributeValueOpt("class").exists(_.splitTo[Set]().contains("fr-open-select-pill-label")))
                .map(_.getStringValue.trimAllToEmpty)
                .toList
            assert(staticPillLabels == List("urgent", "billing"))
            assert(cellText(8) == "+33 1 23 45 67 89")
            assert(cellText(10) == "Qty=1234.5")

            // 3. `fr:sortable-th` triggers inside `xxf:dynamic` render interactive `<button>` elements (NOT readonly)
            // and dispatch `fr-set-search-instance-sort-attribute` to outer `fr-form-model`
            val qtySortButton =
              allElems1
                .find(e => e.getName == "button" && e.getStringValue.contains("Quantity"))
                .getOrElse(throw new NoSuchElementException("Quantity sort `<button>` not found inside `xxf:dynamic`"))

            val qtySortTrigger =
              doc.getControlByEffectiveId(qtySortButton.getParent.attributeValue("id")).asInstanceOf[XFormsTriggerControl]
            assert(! qtySortTrigger.isReadonly)

            activateControlWithEvent(qtySortTrigger.effectiveId)

            val selectionAfterSort = instance("fr-selection-instance").get.rootElement.stringValue
            assert(selectionAfterSort == "sorted:main/qty:asc")

            // Verify active ascending caret (`▲`) on the Quantity header button after re-rendering
            val renderedAfterSort = renderXhtml()
            val qtyButtonAfterSort =
              renderedAfterSort.getRootElement
                .descendantElementIterator(includeSelf = true)
                .find(e => e.getName == "button" && e.getStringValue.contains("Quantity"))
                .get
            val activeCaretSpans =
              qtyButtonAfterSort
                .descendantElementIterator(includeSelf = true)
                .filter(_.attributeValueOpt("class").exists(_.splitTo[Set]().contains("active-caret")))
                .toList
            assert(activeCaretSpans.size == 1)
            assert(activeCaretSpans.head.getStringValue.trimAllToEmpty == "▲")

            // 4. Clearing `<document>` rows via `InstanceMirror` does not unbind `xxf:dynamic` or fail full updates
            withAction {
              dispatch("fr-clear-documents", "fr-form-model")
            }
            separateClientEvents()

            val renderedEmpty = renderXhtml()
            val remainingCellDivs =
              renderedEmpty.getRootElement
                .descendantElementIterator(includeSelf = true)
                .filter(_.attributeValueOpt("class").exists(_.splitTo[Set]().contains("fr-summary-cell-value")))
                .toList
            assert(remainingCellDivs.isEmpty)
          }
        }
      }
    }

    it("renders single and multiple `fr:image-attachment` controls via `fr:summary='true'` binding with `+N` badge and non-sortable headers") {

      val sourceForm =
        <xh:html
          xmlns:xh="http://www.w3.org/1999/xhtml"
          xmlns:xf="http://www.w3.org/2002/xforms"
          xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
          xmlns:fr="http://orbeon.org/oxf/xml/form-runner">
          <xh:head>
            <xf:model id="fr-form-model">
              <xf:instance id="fr-form-instance">
                <form>
                  <main>
                    <title/>
                    <hero filename="" mediatype="" size=""/>
                    <gallery/>
                  </main>
                </form>
              </xf:instance>
              <xf:bind id="fr-form-binds" ref="instance('fr-form-instance')">
                <xf:bind id="main-bind" name="main" ref="main">
                  <xf:bind id="title-bind" name="title" ref="title"/>
                  <xf:bind id="hero-bind" name="hero" ref="hero" type="xf:anyURI"/>
                  <xf:bind id="gallery-bind" name="gallery" ref="gallery"/>
                </xf:bind>
              </xf:bind>
              <xf:instance id="fr-form-resources" xxf:readonly="true">
                <resources>
                  <resource xml:lang="en">
                    <title><label>Title</label></title>
                    <hero><label>Hero</label></hero>
                    <gallery><label>Gallery</label></gallery>
                  </resource>
                </resources>
              </xf:instance>
            </xf:model>
          </xh:head>
          <xh:body>
            <fr:view>
              <fr:body>
                <fr:section id="main-section" bind="main-bind">
                  <fr:grid id="main-grid">
                    <fr:c x="1" y="1" w="4">
                      <xf:input id="title-control" bind="title-bind">
                        <fr:index><fr:summary-show/></fr:index>
                        <xf:label ref="$form-resources/title/label"/>
                      </xf:input>
                    </fr:c>
                    <fr:c x="5" y="1" w="4">
                      <fr:image-attachment id="hero-control" bind="hero-bind" class="fr-attachment">
                        <fr:index><fr:summary-show/></fr:index>
                        <xf:label ref="$form-resources/hero/label"/>
                      </fr:image-attachment>
                    </fr:c>
                    <fr:c x="9" y="1" w="4">
                      <fr:image-attachment id="gallery-control" bind="gallery-bind" multiple="true" class="fr-attachment">
                        <fr:index><fr:summary-show/></fr:index>
                        <xf:label ref="$form-resources/gallery/label"/>
                      </fr:image-attachment>
                    </fr:c>
                  </fr:grid>
                </fr:section>
              </fr:body>
            </fr:view>
          </xh:body>
        </xh:html>

      val dynamicNode =
        FormRunnerSummary.buildSummaryDynamicFormImpl(
          formDoc                   = elemToDocumentInfo(sourceForm),
          templateNode              = summaryTemplateNode,
          appForm                   = AppForm("ebruchez", "typewriter-collection"),
          databaseDataFormatVersion = DataFormatVersion.V20191,
          resourcesRootElemOpt      = None,
          functionLibrary           = FormRunnerFunctionLibrary
        )

      val dynamicRootElem = StaticXPath.tinyTreeToOrbeonDom(dynamicNode).getRootElement

      // Verify `fr:summary="true"` is stamped on Summary controls and `sort-supported="false"` on image-attachment headers
      val frSummaryQName = dom.QName("summary", XMLNames.FRNamespace)
      val summaryControlIds = List("fr-summary-col-1-control", "fr-summary-col-2-control", "fr-summary-col-3-control")
      summaryControlIds.foreach { ctrlId =>
        val ctrlElem = dynamicRootElem.descendantElementIterator(includeSelf = true).find(_.attributeValue("id") == ctrlId).get
        assert(ctrlElem.attributeValue(frSummaryQName) == "true")
      }

      val sortableThs =
        dynamicRootElem
          .descendantElementIterator(includeSelf = true)
          .filter(_.getQName == dom.QName("sortable-th", XMLNames.FRNamespace))
          .filter(_.attributeValueOpt("column").exists(_.startsWith("main/")))
          .toList
      assert(sortableThs.map(th => th.attributeValue("column") -> th.attributeValue("sort-supported")) == List(
        "main/title"   -> "{$sort-supported}",
        "main/hero"    -> "false",
        "main/gallery" -> "false"
      ))

      val sampleImgUri = "data:image/gif;base64,R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7"

      val innerFormInstance =
        dynamicRootElem
          .descendantElementIterator(includeSelf = true)
          .find(e => e.getName == "instance" && e.attributeValue("id") == "fr-form-instance")
          .get
      innerFormInstance.clearContent()
      innerFormInstance.add(
        <documents search-total="2">
          <document
            name="doc-1"
            created="2026-05-01T10:00:00Z"
            last-modified="2026-05-02T12:00:00Z"
            operations="*">
            <details>
              <detail path="main/title">1961 Hermes Baby</detail>
              <detail path="main/hero">{sampleImgUri}</detail>
              <detail path="main/gallery">{s"$sampleImgUri, $sampleImgUri, $sampleImgUri"}</detail>
            </details>
          </document>
          <document
            name="doc-2"
            created="2026-05-03T10:00:00Z"
            last-modified="2026-05-04T12:00:00Z"
            operations="read">
            <details>
              <detail path="main/title">Sheet Only Machine</detail>
              <detail path="main/hero"/>
              <detail path="main/gallery">{sampleImgUri}</detail>
            </details>
          </document>
        </documents>.toDocument.getRootElement.createCopy
      )

      val hostDocDom =
        <xh:html
          xmlns:xh="http://www.w3.org/1999/xhtml"
          xmlns:xf="http://www.w3.org/2002/xforms"
          xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
          xmlns:fr="http://orbeon.org/oxf/xml/form-runner"
          xmlns:frf="java:org.orbeon.oxf.fr.FormRunner">
          <xh:head>
            <xf:model
              id="fr-form-model"
              xxf:lang="en"
              xxf:function-library="org.orbeon.oxf.fr.library.FormRunnerFunctionLibrary"
              xxf:readonly-appearance.static.select="minimal"
              xxf:readonly-appearance.static.select1="minimal">
              <xf:instance id="fr-parameters-instance">
                <parameters>
                  <app>ebruchez</app>
                  <form>typewriter-collection</form>
                  <form-version>1</form-version>
                  <mode>summary</mode>
                </parameters>
              </xf:instance>
              <xf:instance id="fr-language-instance"><lang>en</lang></xf:instance>
              <xf:instance id="fr-authorized-operations"><operations>*</operations></xf:instance>
              <xf:instance id="fr-selection-instance"><selection/></xf:instance>
              <xf:instance id="fr-order-by">
                <order-by>
                  <column>last-modified</column>
                  <direction>desc</direction>
                </order-by>
              </xf:instance>
              <xf:instance id="fr-search-labels-instance">
                <searchable-values>
                  <query path="main/title"><resources lang="en"><label>Title</label></resources></query>
                  <query path="main/hero"><resources lang="en"><label>Hero</label></resources></query>
                  <query path="main/gallery"><resources lang="en"><label>Gallery</label></resources></query>
                </searchable-values>
              </xf:instance>
              <xf:instance id="fr-source-form-instance">
                <xh:html>
                  <xh:head>
                    <xf:model>
                      <xf:instance id="fr-form-metadata">
                        <metadata>
                          <summary-metadata-columns>
                            <column name="created" show="false"/>
                            <column name="last-modified" show="false"/>
                            <column name="workflow-stage" show="false"/>
                            <column name="created-by" show="false"/>
                            <column name="last-modified-by" show="false"/>
                          </summary-metadata-columns>
                        </metadata>
                      </xf:instance>
                    </xf:model>
                  </xh:head>
                </xh:html>
              </xf:instance>
              <xf:instance id="fr-last-search-context-instance">
                <ctx><encoded/><page-offset>0</page-offset></ctx>
              </xf:instance>
              <xf:instance id="fr-dynamic-instance"><_/></xf:instance>
            </xf:model>
            <xf:model id="fr-resources-model">
              <xf:instance id="fr-fr-resources">
                <resources>
                  <resource xml:lang="en">
                    <summary>
                      <titles>
                        <edit>Edit</edit>
                        <view>View</view>
                      </titles>
                    </summary>
                  </resource>
                </resources>
              </xf:instance>
            </xf:model>
          </xh:head>
          <xh:body>
            <xxf:dynamic id="fr-summary-dynamic" ref="instance('fr-dynamic-instance')"/>
          </xh:body>
        </xh:html>.toDocument

      val outerDynamicInst =
        hostDocDom.getRootElement
          .descendantElementIterator(includeSelf = true)
          .find(e => e.getName == "instance" && e.attributeValue("id") == "fr-dynamic-instance")
          .get
      outerDynamicInst.clearContent()
      outerDynamicInst.add(dynamicRootElem.createCopy)

      withTestExternalContext { implicit ec =>
        withXFormsDocument(hostDocDom) { doc =>
          withActionAndDoc(doc) {

            val template = AnnotatedTemplate(PartAnalysisBuilder.createFromDocument(hostDocDom)(XFormsStateManager.newIndentedLogger)._1)

            def renderXhtml(): dom.Document = {
              val receiver = new LocationSAXContentHandler
              XHTMLOutput.send(doc, template, ec)(receiver)
              receiver.getDocument
            }

            val rendered = renderXhtml()
            val root     = rendered.getRootElement

            // Zero lightbox `<dialog>` elements or `<fr:attachment>` filename controls rendered in Summary cells
            assert(! root.descendantElementIterator(includeSelf = true).exists(_.attributeValueOpt("class").exists(_.contains("fr-image-attachment-lightbox"))))
            assert(! root.descendantElementIterator(includeSelf = true).exists(_.attributeValueOpt("class").exists(_.contains("fr-attachment-filename"))))

            val cellValueDivs =
              root.descendantElementIterator(includeSelf = true)
                .filter(_.attributeValueOpt("class").exists(_.splitTo[Set]().contains("fr-summary-cell-value")))
                .toVector
            assert(cellValueDivs.size == 6)

            def isVisiblyRendered(elem: dom.Element): Boolean =
              Iterator.iterate[dom.Element](elem)(_.getParent).takeWhile(_ ne null).forall { a =>
                ! a.attributeValueOpt("class").exists(_.splitTo[Set]().contains("xforms-disabled"))
              }

            def hasVisibleThumbnail(cell: dom.Element): Boolean =
              cell.descendantElementIterator(includeSelf = true).exists { e =>
                val classes = e.attributeValueOpt("class").map(_.splitTo[Set]()).getOrElse(Set.empty)
                classes("fr-image-attachment-summary-frame") && isVisiblyRendered(e)
              }

            def activeBadgeTexts(cell: dom.Element): List[String] =
              cell.descendantElementIterator(includeSelf = true)
                .filter { e =>
                  val classes = e.attributeValueOpt("class").map(_.splitTo[Set]()).getOrElse(Set.empty)
                  classes("fr-image-attachment-summary-badge") && isVisiblyRendered(e)
                }
                .map(_.getStringValue.trimAllToEmpty)
                .toList

            // Row 1: hero (1 image, no badge) + gallery (3 images -> "+2" badge)
            assert(hasVisibleThumbnail(cellValueDivs(1)))
            assert(activeBadgeTexts(cellValueDivs(1)).isEmpty)

            assert(hasVisibleThumbnail(cellValueDivs(2)))
            assert(activeBadgeTexts(cellValueDivs(2)) == List("+2"))

            // Row 2: blank hero (no visible frame) + 1-image gallery (visible frame, no badge)
            assert(! hasVisibleThumbnail(cellValueDivs(4)))
            assert(hasVisibleThumbnail(cellValueDivs(5)))
            assert(activeBadgeTexts(cellValueDivs(5)).isEmpty)
          }
        }
      }
    }
  }
}
