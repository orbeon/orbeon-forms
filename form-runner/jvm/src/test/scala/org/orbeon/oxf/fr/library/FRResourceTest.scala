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
package org.orbeon.oxf.fr.library

import org.orbeon.dom
import org.orbeon.oxf.fr.{AppForm, FormRunnerSupport}
import org.orbeon.oxf.properties.PropertySet
import org.orbeon.oxf.properties.PropertySet.PropertyParams
import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport}
import org.orbeon.oxf.xforms.action.XFormsAPI.*
import org.orbeon.oxf.xforms.control.XFormsValueControl
import org.orbeon.oxf.xforms.event.EventCollector
import org.orbeon.oxf.xml.XMLConstants.XS_STRING_QNAME
import org.orbeon.oxf.xml.dom.Converter.*
import org.scalatest.funspec.AnyFunSpecLike


class FRResourceTest
  extends DocumentTestBase
     with ResourceManagerSupport
     with FormRunnerSupport
     with AnyFunSpecLike {

  describe("XBL resource overrides (#3235)") {

    def testDoc(lang: String = "en"): dom.Document =
      <xh:html xmlns:xh="http://www.w3.org/1999/xhtml"
               xmlns:xf="http://www.w3.org/2002/xforms"
               xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
               xmlns:fr="http://orbeon.org/oxf/xml/form-runner"
               xmlns:xbl="http://www.w3.org/ns/xbl"
               xmlns:map="http://www.w3.org/2005/xpath-functions/map"
               xml:lang={lang}>
        <xh:head>
          <xf:model id="fr-form-model"
                    xxf:function-library="org.orbeon.oxf.fr.library.FormRunnerFunctionLibrary">
            <xf:instance id="fr-form-metadata" xxf:readonly="true">
              <metadata>
                <application-name>foo</application-name>
                <form-name>bar</form-name>
              </metadata>
            </xf:instance>
          </xf:model>
          <xbl:xbl>
            <xbl:binding id="fr-test-comp" element="fr|test-comp">
              <xbl:implementation>
                <xf:model id="test-comp-model"
                          xxf:function-library="org.orbeon.oxf.fr.library.FormRunnerFunctionLibrary">
                  <xf:instance id="orbeon-resources" xxf:readonly="true">
                    <resources>
                      <resource xml:lang="en">
                        <default-alert>Default Component Alert</default-alert>
                      </resource>
                      <resource xml:lang="fr">
                        <default-alert>Alerte par défaut</default-alert>
                      </resource>
                    </resources>
                  </xf:instance>
                </xf:model>
              </xbl:implementation>
              <xbl:template>
                <xf:group id="inner-group">
                  <xf:output id="out-default"       value="fr:r('default-alert')"/>
                  <xf:output id="out-prop-wildcard" value="fr:r('alert-from-property')"/>
                </xf:group>
              </xbl:template>
            </xbl:binding>
          </xbl:xbl>
        </xh:head>
        <xh:body>
          <fr:test-comp id="my-component"/>
        </xh:body>
      </xh:html>.toDocument

    it("must resolve default component resources from `orbeon-resources`") {
      withTestExternalContext { _ =>
        withActionAndDoc(setupDocument(testDoc("en"))) {
          val out = resolveObjectInsideComponent[XFormsValueControl]("my-component", targetStaticOrAbsoluteId = "out-default").get
          assert(out.getValue(EventCollector.Throw) == "Default Component Alert")
        }
      }
    }

    it("must override component resource with property using colon syntax (fr:test-comp)") {
      withTestExternalContext { _ =>
        withActionAndDoc(setupDocument(testDoc("en"))) {
          val out = resolveObjectInsideComponent[XFormsValueControl]("my-component", targetStaticOrAbsoluteId = "out-prop-wildcard").get
          assert(out.getValue(EventCollector.Throw) == "Alert from property wildcard")
        }
      }
    }

    it("must respect language in XBL resource resolution") {
      withTestExternalContext { _ =>
        withActionAndDoc(setupDocument(testDoc("fr"))) {
          val outDefault = resolveObjectInsideComponent[XFormsValueControl]("my-component", targetStaticOrAbsoluteId = "out-default").get
          assert(outDefault.getValue(EventCollector.Throw) == "Alerte par défaut")
        }
      }
    }
  }

  describe("Property specificity and language fallback (#3235)") {

    def docWithAppForm(app: String, form: String, lang: String = "en"): dom.Document =
      <xh:html xmlns:xh="http://www.w3.org/1999/xhtml"
               xmlns:xf="http://www.w3.org/2002/xforms"
               xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
               xmlns:fr="http://orbeon.org/oxf/xml/form-runner"
               xmlns:xbl="http://www.w3.org/ns/xbl"
               xml:lang={lang}>
        <xh:head>
          <xf:model id="fr-form-model"
                    xxf:function-library="org.orbeon.oxf.fr.library.FormRunnerFunctionLibrary">
            <xf:instance id="fr-form-metadata" xxf:readonly="true">
              <metadata>
                <application-name>{app}</application-name>
                <form-name>{form}</form-name>
              </metadata>
            </xf:instance>
            <xf:instance id="fr-form-resources" xxf:readonly="true">
              <resources>
                <resource xml:lang="en">
                  <buttons>
                    <save>Save Document</save>
                  </buttons>
                </resource>
                <resource xml:lang="es">
                  <buttons>
                    <save>Guardar</save>
                  </buttons>
                </resource>
              </resources>
            </xf:instance>
          </xf:model>
          <xbl:xbl>
            <xbl:binding id="fr-test-comp" element="fr|test-comp">
              <xbl:implementation>
                <xf:model id="test-comp-model"
                          xxf:function-library="org.orbeon.oxf.fr.library.FormRunnerFunctionLibrary">
                  <xf:instance id="orbeon-resources" xxf:readonly="true">
                    <resources>
                      <resource xml:lang="en">
                        <alert>Default comp alert</alert>
                      </resource>
                    </resources>
                  </xf:instance>
                </xf:model>
              </xbl:implementation>
              <xbl:template>
                <xf:group id="inner-group">
                  <xf:output id="comp-alert" value="fr:r('alert-specificity')"/>
                </xf:group>
              </xbl:template>
            </xbl:binding>
          </xbl:xbl>
        </xh:head>
        <xh:body>
          <fr:test-comp id="comp"/>
          <xf:output id="out-save"  value="fr:r('buttons.save')"/>
        </xh:body>
      </xh:html>.toDocument

    it("must select specific app/form override over wildcard app override") {
      withTestExternalContext { _ =>
        withActionAndDoc(setupDocument(docWithAppForm("acme", "order", "en"))) {
          val comp = resolveObjectInsideComponent[XFormsValueControl]("comp", targetStaticOrAbsoluteId = "comp-alert").get
          assert(comp.getValue(EventCollector.Throw) == "Specific app-form alert")
        }
      }
    }

    it("must select app wildcard override when form does not match") {
      withTestExternalContext { _ =>
        withActionAndDoc(setupDocument(docWithAppForm("acme", "invoice", "en"))) {
          val comp = resolveObjectInsideComponent[XFormsValueControl]("comp", targetStaticOrAbsoluteId = "comp-alert").get
          assert(comp.getValue(EventCollector.Throw) == "App wildcard alert")
        }
      }
    }

    it("must select global wildcard override when neither app nor form matches") {
      withTestExternalContext { _ =>
        withActionAndDoc(setupDocument(docWithAppForm("foo", "bar", "en"))) {
          val comp = resolveObjectInsideComponent[XFormsValueControl]("comp", targetStaticOrAbsoluteId = "comp-alert").get
          assert(comp.getValue(EventCollector.Throw) == "Global wildcard alert")
        }
      }
    }

    it("must resolve global resource from fr-form-resources when no property override is present") {
      withTestExternalContext { _ =>
        withActionAndDoc(setupDocument(docWithAppForm("acme", "order", "en"))) {
          val out = resolveObject[XFormsValueControl]("out-save").get
          assert(out.getValue(EventCollector.Throw) == "Save Document")
        }
      }
    }

    it("must resolve Spanish global resource from fr-form-resources when language is es") {
      withTestExternalContext { _ =>
        withActionAndDoc(setupDocument(docWithAppForm("acme", "order", "es"))) {
          val out = resolveObject[XFormsValueControl]("out-save").get
          assert(out.getValue(EventCollector.Throw) == "Guardar")
        }
      }
    }
  }
}
