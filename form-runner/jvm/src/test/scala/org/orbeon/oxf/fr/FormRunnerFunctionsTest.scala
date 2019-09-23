/**
 * Copyright (C) 2011 Orbeon, Inc.
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

import org.junit.Test
import org.orbeon.dom.{Document ⇒ JDocument}
import org.orbeon.oxf.fr.FormRunner._
import org.orbeon.oxf.fr.FormRunnerPersistence._
import org.orbeon.oxf.test.DocumentTestBase
import org.orbeon.oxf.util.NetUtils
import org.orbeon.oxf.xforms.action.XFormsAPI._
import org.orbeon.oxf.xml.Dom4j.elemToDocument
import org.orbeon.oxf.xml.TransformerUtils
import org.orbeon.xbl.ErrorSummary
import org.orbeon.xforms.XFormsId
import org.scalatest.junit.AssertionsForJUnit

import scala.collection.JavaConverters._

class FormRunnerFunctionsTest extends DocumentTestBase with AssertionsForJUnit {

  @Test def persistenceHeaders(): Unit = {

    val obf = getPersistenceHeadersAsXML("cities", "form1", FormOrData.Form)
    assert(TransformerUtils.tinyTreeToString(obf) ===
      """<headers><header><name>Orbeon-City-Uri</name><value>http://en.wikipedia.org/wiki/Mexico_City</value></header><header><name>Orbeon-City-Name</name><value>Mexico City</value></header><header><name>Orbeon-Population</name><value>8851080</value></header></headers>""")

    val obd = getPersistenceHeadersAsXML("cities", "form1", FormOrData.Data)
    assert(TransformerUtils.tinyTreeToString(obd) ===
      """<headers><header><name>Orbeon-City-Uri</name><value>http://en.wikipedia.org/wiki/S%C3%A3o_Paulo</value></header><header><name>Orbeon-City-Name</name><value>São Paulo</value></header><header><name>Orbeon-Population</name><value>11244369</value></header></headers>""")
  }

  @Test def language(): Unit = {

    val App  = "acme"
    val Form = "order"

    // oxf.fr.default-language not set so "en" is the default
    assert("en" === getDefaultLang(getAppForm(App, Form)))

    // oxf.fr.available-languages not set so all languages are allowed
    assert(isAllowedLang(getAppForm(App, Form))("en"))
    assert(isAllowedLang(getAppForm(App, Form))("foo"))

    // Requested language
    assert(Some("en") === findRequestedLang(getAppForm(App, Form), null))
    assert(Some("en") === findRequestedLang(getAppForm(App, Form), "   "))

    assert(Some("es") === findRequestedLang(getAppForm(App, Form), "es"))
    assert(Some("en") === findRequestedLang(getAppForm(App, Form), "en"))

    NetUtils.getExternalContext.getRequest.getSession(true).setAttribute("fr-language", "fr")

    assert(Some("fr") === findRequestedLang(getAppForm(App, Form), null))
    assert(Some("it") === findRequestedLang(getAppForm(App, Form), "it"))

    // Language selector
    assert(List("en", "fr", "it") === getFormLangSelection(App, Form, List("fr", "it", "en").asJava))
    assert(List("fr", "it", "es") === getFormLangSelection(App, Form, List("fr", "it", "es").asJava))
    assert(Nil                    === getFormLangSelection(App, Form, Nil.asJava))

    // Select form language
    assert("it" === selectFormLang(App, Form, "it", List("fr", "it", "en").asJava))
    assert("en" === selectFormLang(App, Form, "zh", List("fr", "it", "en").asJava))
    assert("fr" === selectFormLang(App, Form, "zh", List("fr", "it", "es").asJava))
    assert(null eq  selectFormLang(App, Form, "fr", Nil.asJava))

    // Select Form Runner language
    assert("it" === selectFormRunnerLang(App, Form, "it", List("fr", "it", "en").asJava))
    assert("en" === selectFormRunnerLang(App, Form, "zh", List("fr", "it", "en").asJava))
    assert("fr" === selectFormRunnerLang(App, Form, "zh", List("fr", "it", "es").asJava))
  }

  // Test for https://github.com/orbeon/orbeon-forms/issues/2688
  @Test def languageIssue2688(): Unit = {

    val App  = "acme"
    val Form = "order"

    for (lang ← List("zh-Hans", "zh-Hant"))
      assert(lang === selectFormRunnerLang(App, Form, lang, List("en", "zh-Hans", "zh-Hant", "es").asJava))

    assert("en" === selectFormRunnerLang(App, Form, "en_US", List("en", "zh-Hans", "zh-Hant", "es").asJava))
  }

  @Test def errorSummarySortString(): Unit = {

    def source: JDocument =
      <xh:html xmlns:xh="http://www.w3.org/1999/xhtml" xmlns:xf="http://www.w3.org/2002/xforms">
        <xh:head>
          <xf:model>
            <xf:instance>
              <data>
                <c1/>
                <c2/>
                <c3>
                  <c4/>
                  <c5/>
                  <c6>
                    <c7/>
                    <c8/>
                  </c6>
                  <c6>
                    <c7/>
                    <c8/>
                  </c6>
                  <c9/>
                  <c10/>
                </c3>
                <c3>
                  <c4/>
                  <c5/>
                  <c6>
                    <c7/>
                    <c8/>
                  </c6>
                  <c6>
                    <c7/>
                    <c8/>
                  </c6>
                  <c9/>
                  <c10/>
                </c3>
                <c11/>
                <c12/>
              </data>
            </xf:instance>
          </xf:model>
        </xh:head>
        <xh:body>
          <xf:input id="c1" ref="c1"/>
          <xf:input id="c2" ref="c2"/>
          <xf:repeat id="c3" ref="c3">
            <xf:input id="c4" ref="c4"/>
            <xf:input id="c5" ref="c5"/>
            <xf:repeat id="c6" ref="c6">
              <xf:input id="c7" ref="c7"/>
              <xf:input id="c8" ref="c8"/>
            </xf:repeat>
            <xf:input id="c9" ref="c9"/>
            <xf:input id="c10" ref="c10"/>
          </xf:repeat>
          <xf:input id="c11" ref="c11"/>
          <xf:input id="c12" ref="c12"/>
        </xh:body>
      </xh:html>

    withActionAndDoc(setupDocument(source)) {

      val doc = inScopeContainingDocument

      val controlIds     = 1 to 12 map ("c" +)
      val controlIndexes = controlIds map doc.getStaticOps.getControlPosition

      // Static control position follows source document order
      assert(controlIndexes.sorted === controlIndexes)

      val effectiveAbsoluteIds =
        doc.getControls.getCurrentControlTree.effectiveIdsToControls map
        { case (id, _) ⇒ XFormsId.effectiveIdToAbsoluteId(id) } toList

      // Effective sort strings follow document order
      assert(effectiveAbsoluteIds.sortBy(ErrorSummary.controlSearchIndexes)(ErrorSummary.IntIteratorOrdering) === effectiveAbsoluteIds)
    }
  }
}