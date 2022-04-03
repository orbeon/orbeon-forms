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

import org.orbeon.dom
import org.orbeon.oxf.fr.FormRunner._
import org.orbeon.oxf.fr.FormRunnerPersistence._
import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport}
import org.orbeon.oxf.util.NetUtils
import org.orbeon.oxf.xforms.action.XFormsAPI._
import org.orbeon.oxf.xml.TransformerUtils
import org.orbeon.oxf.xml.dom.Converter._
import org.orbeon.xbl.ErrorSummary
import org.orbeon.xforms.XFormsId
import org.scalatest.funspec.AnyFunSpecLike

import scala.jdk.CollectionConverters._


class FormRunnerFunctionsTest
  extends DocumentTestBase
     with ResourceManagerSupport
     with AnyFunSpecLike {

  describe("Persistence headers") {

    val Expected = List(
      FormOrData.Form ->
        """<headers><header><name>Orbeon-City-Uri</name><value>http://en.wikipedia.org/wiki/Mexico_City</value></header><header><name>Orbeon-City-Name</name><value>Mexico City</value></header><header><name>Orbeon-Population</name><value>8851080</value></header></headers>""",
      FormOrData.Data ->
        """<headers><header><name>Orbeon-City-Uri</name><value>http://en.wikipedia.org/wiki/S%C3%A3o_Paulo</value></header><header><name>Orbeon-City-Name</name><value>São Paulo</value></header><header><name>Orbeon-Population</name><value>11244369</value></header></headers>"""
    )

    for ((formOrData, expected) <- Expected)
      it(s"must get headers for ${formOrData.entryName}") {
        val headersAsXml = getPersistenceHeadersAsXML(AppForm("cities", "form1"), formOrData)
        assert(TransformerUtils.tinyTreeToString(headersAsXml) === expected)
      }
  }

  describe("Language") {

    val App  = "acme"
    val Form = "order"

    it("`oxf.fr.default-language` not set so `en` is the default") {
      withTestExternalContext { _ =>
        assert("en" === getDefaultLang(getAppForm(App, Form)))
      }
    }

    it("`oxf.fr.available-languages` not set so all languages are allowed") {
      withTestExternalContext { _ =>
        assert(isAllowedLang(getAppForm(App, Form))("en"))
        assert(isAllowedLang(getAppForm(App, Form))("foo"))
      }
    }

    describe("Requested language") {
      for (v <- List(null, "   "))
        it (s"must default to `en` for `$v` requested language") {
          withTestExternalContext { _ =>
            assert(Some("en") === findRequestedLang(getAppForm(App, Form), v))
          }
        }

      for (v <- List("es", "en"))
        it(s"must find requested language for `$v`") {
          withTestExternalContext { _ =>
            assert(Some(v) === findRequestedLang(getAppForm(App, Form), v))
          }
        }

      it("must find language in session if present and not requested") {
        withTestExternalContext { _ =>
          NetUtils.getExternalContext.getRequest.getSession(true).setAttribute("fr-language", "fr")
          assert(Some("fr") === findRequestedLang(getAppForm(App, Form), null))
        }
      }

      it("must use requested language even if a language is present in session") {
        withTestExternalContext { _ =>
          assert(Some("it") === findRequestedLang(getAppForm(App, Form), "it"))
        }
      }
    }

    describe("Language selector") {

      describe("Selection") {
        it(s"default language is put first`") {
            withTestExternalContext { _ =>
              assert(List("en", "fr", "it") === getFormLangSelection(App, Form, List("fr", "it", "en").asJava))
            }
        }

        it(s"other languages order is preserved") {
            withTestExternalContext { _ =>
              assert(List("fr", "it", "es") === getFormLangSelection(App, Form, List("fr", "it", "es").asJava))
            }
        }

        it(s"no language available") {
            withTestExternalContext { _ =>
              assert(Nil                    === getFormLangSelection(App, Form, Nil.asJava))
            }
        }
      }

      describe("Form language") {
        val Expected = List(
          ("it" , "it", List("fr", "it", "en")),
          ("en" , "zh", List("fr", "it", "en")),
          ("fr" , "zh", List("fr", "it", "es")),
          (null , "fr", Nil)
        )

        for ((expected, requested, available) <- Expected)
          it(s"must select language for `$requested` and available `$available`") {
            withTestExternalContext { _ =>
              assert(expected === selectFormLang(App, Form, requested, available.asJava))
            }
          }
      }

      describe("Form Runner language") {
        val Expected = List(
          ("it" , "it", List("fr", "it", "en")),
          ("en" , "zh", List("fr", "it", "en")),
          ("fr" , "zh", List("fr", "it", "es"))
        )

        for ((expected, requested, available) <- Expected)
          it(s"must select language for `$requested` and available `$available`") {
            withTestExternalContext { _ =>
              assert(expected === selectFormRunnerLang(App, Form, requested,available.asJava))
            }
          }
      }
    }
  }

  describe("Language issue #2688") { // https://github.com/orbeon/orbeon-forms/issues/2688

    val App  = "acme"
    val Form = "order"

    it("must select the correct language") {
      withTestExternalContext { _ =>
        for (lang <- List("zh-Hans", "zh-Hant"))
          assert(lang === selectFormRunnerLang(App, Form, lang, List("en", "zh-Hans", "zh-Hant", "es").asJava))

        assert("en" === selectFormRunnerLang(App, Form, "en_US", List("en", "zh-Hans", "zh-Hant", "es").asJava))
      }
    }
  }

  describe("Error Summary sorting") {

    def source: dom.Document =
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
        </xh:html>.toDocument

    it("must order the controls in document order") {
      withTestExternalContext { _ =>
        withActionAndDoc(setupDocument(source)) {

          val doc = inScopeContainingDocument

          val controlIds     = 1 to 12 map ("c" +)
          val controlIndexes = controlIds map doc.staticOps.getControlPosition

          // Static control position follows source document order
          assert(controlIndexes.sorted === controlIndexes)

          val effectiveAbsoluteIds =
            doc.controls.getCurrentControlTree.effectiveIdsToControls map
            { case (id, _) => XFormsId.effectiveIdToAbsoluteId(id) } toList

          assert(effectiveAbsoluteIds.sortBy(ErrorSummary.controlSearchIndexes)(ErrorSummary.IntIteratorOrdering) === effectiveAbsoluteIds)
        }
      }
    }
  }
}