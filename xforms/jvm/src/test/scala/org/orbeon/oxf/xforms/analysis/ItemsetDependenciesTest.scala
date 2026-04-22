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

package org.orbeon.oxf.xforms.analysis

import org.orbeon.oxf.common.Version
import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport}
import org.orbeon.oxf.xml.dom.Converter.*
import org.scalatest.funspec.AnyFunSpecLike


class ItemsetDependenciesTest
  extends DocumentTestBase
     with ResourceManagerSupport
     with AnyFunSpecLike {

  // See: [ #315557 ] XPath analysis: Checkbox with both itemset and value changing ends up in incorrect state
  //      http://forge.ow2.org/tracker/?func=detail&atid=350207&aid=315557&group_id=168
  describe("select value depending on itemset") {

    val TestDoc =
      <xh:html xmlns:xh="http://www.w3.org/1999/xhtml"
           xmlns:xf="http://www.w3.org/2002/xforms"
           xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
           xmlns:xs="http://www.w3.org/2001/XMLSchema">

        <xh:head>
          <xf:model id="model" xxf:xpath-analysis="true" xxf:encrypt-item-values="false">
            <xf:instance id="instance">
              <instance xmlns="">
                <selection>1 2</selection>
                <value>1</value>
                <value>2</value>
                <index>1</index>
              </instance>
            </xf:instance>
          </xf:model>
        </xh:head>
        <xh:body>
          <xf:select id="checkbox" ref="selection" appearance="full">
            <xf:item>
              <xf:label/>
              <xf:value ref="../value[xs:integer(../index)]"/>
            </xf:item>
          </xf:select>

          <xf:select1 id="value-selection" ref="index" appearance="full">
            <xf:item>
              <xf:label>1</xf:label>
              <xf:value>1</xf:value>
            </xf:item>
            <xf:item>
              <xf:label>2</xf:label>
              <xf:value>2</xf:value>
            </xf:item>
          </xf:select1>
        </xh:body>
      </xh:html>.toDocument

    it("must pass all checks") {
      assume(Version.isPE)
      withTestExternalContext { _ =>
        withActionAndDoc(setupDocument(TestDoc)) {

          assert(getControlExternalValue("checkbox") == "1")
          assert(getControlExternalValue("value-selection") == "1")
          assert(getItemset("checkbox") == """[{"label":"","value":"1"}]""")

          setControlValue("value-selection", "2")

          assert(getControlExternalValue("checkbox") == "2")
          assert(getItemset("checkbox") == """[{"label":"","value":"2"}]""")
        }
      }
    }
  }

  describe("#7401: Label of the dynamic dropdown doesn't update on language change") {

    // The following form reproduces a scenario similar to what `databound-select1-search.xbl` does, but in a simplified
    // way to test most of the parts involved:
    //
    // - XBL component
    // - itemset included with `xbl:content`
    // - hidden internal select1 to provide the itemset to the main select1
    // - use of the `xxf:itemset()` function to get the itemset
    // - language change
    // - variable (here an `xf:output` field) holding the current label
    val TestDoc =
      <xh:html
          xmlns:xh="http://www.w3.org/1999/xhtml"
          xmlns:xs="http://www.w3.org/2001/XMLSchema"
          xmlns:xf="http://www.w3.org/2002/xforms"
          xmlns:fr="http://orbeon.org/oxf/xml/form-runner"
          xmlns:xbl="http://www.w3.org/ns/xbl"
          xmlns:xxbl="http://orbeon.org/oxf/xml/xbl"
          xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
      >
          <xh:head>
              <xf:model
                  xxf:xpath-analysis="true"
                  xxf:expose-xpath-types="true"
                  xxf:analysis.calculate="true"
                  id="model"
              >
                  <xf:instance id="instance">
                      <_/>
                  </xf:instance>
                  <xf:instance id="lang">
                      <_>en</_>
                  </xf:instance>
              </xf:model>
              <xbl:xbl>
                  <xbl:binding
                      id="fr-foo"
                      element="fr|foo"
                      xxbl:mode="binding"
                  >
                      <xbl:implementation>
                          <xf:model id="model-fr-foo">
                              <xf:instance id="_">
                                  <_/>
                              </xf:instance>
                              <xf:instance id="itemset">
                                  <items>
                                      <item>
                                          <label xml:lang="en">Item label 1</label>
                                          <label xml:lang="fr">Libellé d'entrée 1</label>
                                          <value>v1</value>
                                      </item>
                                      <item>
                                          <label xml:lang="en">Item label 2</label>
                                          <label xml:lang="fr">Libellé d'entrée 2</label>
                                          <value>v2</value>
                                      </item>
                                  </items>
                              </xf:instance>
                          </xf:model>
                      </xbl:implementation>
                      <xbl:template>

                          <xf:var name="itemset" value="instance('itemset')"/>
                          <xf:var name="itemset" xxbl:scope="outer">
                              <xxf:value value="$itemset" xxbl:scope="inner"/>
                          </xf:var>

                          <xf:select1
                              id="internal-select1"
                              appearance="xxf:internal"
                              ref="xf:element('_')"
                          >
                              <xf:choices context="$itemset" xxbl:scope="outer">
                                  <xbl:content includes=":root > xf|itemset, :root > xf|item, :root > xf|choices"/>
                              </xf:choices>
                          </xf:select1>

                          <xf:output
                              id="my-output"
                              value="xxf:itemset('internal-select1', 'xml', false())/itemset/choices/item[value = xxf:binding('fr-foo')]/label/string()"/>

                          <xf:select1
                              ref="xxf:binding('fr-foo')"
                              id="select1"
                          >
                              <xf:itemset ref="xxf:itemset('internal-select1', 'xml', false())/itemset/choices/item">
                                  <xf:label ref="label"/>
                                  <xf:value ref="value"/>
                              </xf:itemset>
                          </xf:select1>
                      </xbl:template>
                  </xbl:binding>
              </xbl:xbl>
          </xh:head>
          <xh:body>
              <xf:input id="lang-input" ref="instance('lang')">
                  <xf:label>Language</xf:label>
              </xf:input>
              <fr:foo id="my-foo" ref="instance()">
                  <xf:itemset ref="/items/item">
                      <xf:label ref="label[@xml:lang = instance('lang')]"/>
                      <xf:value ref="value"/>
                  </xf:itemset>
              </fr:foo>
          </xh:body>
      </xh:html>.toDocument

    it("must test that the label updates when the language changes") {
      assume(Version.isPE)
      withTestExternalContext { _ =>
        withActionAndDoc(setupDocument(TestDoc)) {

          setControlValue("my-foo≡select1", "0")
          assert(getControlExternalValue("my-foo≡my-output") == "Item label 1")
          setControlValue("my-foo≡select1", "1")
          assert(getControlExternalValue("my-foo≡my-output") == "Item label 2")

          setControlValue("lang-input", "fr")
          assert(getControlExternalValue("my-foo≡my-output") == "Libellé d'entrée 2")
          setControlValue("my-foo≡select1", "0")
          assert(getControlExternalValue("my-foo≡my-output") == "Libellé d'entrée 1")
          setControlValue("my-foo≡select1", "1")
          assert(getControlExternalValue("my-foo≡my-output") == "Libellé d'entrée 2")

          setControlValue("lang-input", "en")
          assert(getControlExternalValue("my-foo≡my-output") == "Item label 2")
        }
      }
    }
  }
}