package org.orbeon.oxf.fr

import org.scalatest.junit.AssertionsForJUnit
import org.junit.Test
import org.orbeon.scaxon.XML._
import org.orbeon.oxf.fr.persistence.relational.Index
import org.orbeon.oxf.fr.persistence.relational.Index.IndexedControl
import org.orbeon.oxf.test.DocumentTestBase

class IndexTest extends DocumentTestBase with AssertionsForJUnit {

  @Test def formBuilderPermissions(): Unit = {

    val formElem =
      <xh:html xmlns:xh="http://www.w3.org/1999/xhtml"
           xmlns:xf="http://www.w3.org/2002/xforms"
           xmlns:fr="http://orbeon.org/oxf/xml/form-runner"
           xmlns:xs="http://www.w3.org/2001/XMLSchema">
        <xh:head>
          <xf:model id="fr-form-model">
            <xf:bind id="fr-form-binds" ref="instance('fr-form-instance')">
              <xf:bind id="section-1-bind" name="section-1" ref="section-1">
                <xf:bind id="in-summary-bind" name="in-summary" ref="in-summary"/>
                <xf:bind id="in-search-bind" ref="in-search" name="in-search"/>
                <xf:bind id="in-both-bind" ref="in-both" name="in-both"/>
                <xf:bind id="in-none-bind" ref="in-none" name="in-none"/>
                <xf:bind id="date-bind" ref="date" name="date" type="xf:date"/>
                <xf:bind id="repeat-bind" ref="repeat" name="repeat">
                  <xf:bind id="in-repeat-bind" ref="in-repeat" name="in-repeat"/>
                </xf:bind>
              </xf:bind>
            </xf:bind>
          </xf:model>
        </xh:head>
        <xh:body>
          <fr:view>
            <fr:body>
              <fr:section id="section-1-control" bind="section-1-bind">
                <xf:label ref="$form-resources/section-1/label"/>
                <xf:help ref="$form-resources/section-1/help"/>
                <fr:grid>
                  <xh:tr>
                    <xh:td>
                      <xf:input id="in-summary-control" bind="in-summary-bind" class="fr-summary">
                        <xf:label ref="$form-resources/in-summary/label"/>
                        <xf:hint ref="$form-resources/in-summary/hint"/>
                        <xf:help ref="$form-resources/in-summary/help"/>
                        <xf:alert ref="$fr-resources/detail/labels/alert"/>
                      </xf:input>
                    </xh:td>
                    <xh:td>
                      <xf:input id="in-search-control" bind="in-search-bind" class="fr-search">
                        <xf:label ref="$form-resources/in-search/label"/>
                        <xf:hint ref="$form-resources/in-search/hint"/>
                        <xf:help ref="$form-resources/in-search/help"/>
                        <xf:alert ref="$fr-resources/detail/labels/alert"/>
                      </xf:input>
                    </xh:td>
                  </xh:tr>
                  <xh:tr>
                    <xh:td>
                      <xf:input id="in-both-control" bind="in-both-bind" class="fr-search fr-summary">
                        <xf:label ref="$form-resources/in-both/label"/>
                        <xf:hint ref="$form-resources/in-both/hint"/>
                        <xf:help ref="$form-resources/in-both/help"/>
                        <xf:alert ref="$fr-resources/detail/labels/alert"/>
                      </xf:input>
                    </xh:td>
                    <xh:td>
                      <xf:input id="in-none-control" bind="in-none-bind">
                        <xf:label ref="$form-resources/in-none/label"/>
                        <xf:hint ref="$form-resources/in-none/hint"/>
                        <xf:help ref="$form-resources/in-none/help"/>
                        <xf:alert ref="$fr-resources/detail/labels/alert"/>
                      </xf:input>
                    </xh:td>
                  </xh:tr>
                  <xh:tr>
                    <xh:td>
                      <xf:input id="date-control" bind="date-bind" class="fr-summary fr-search">
                        <xf:label ref="$form-resources/date/label"/>
                        <xf:hint ref="$form-resources/date/hint"/>
                        <xf:help ref="$form-resources/date/help"/>
                        <xf:alert ref="$fr-resources/detail/labels/alert"/>
                      </xf:input>
                    </xh:td>
                    <xh:td/>
                  </xh:tr>
                </fr:grid>
                <fr:grid id="repeat-control" repeat="true" bind="repeat-bind"
                     origin="instance('repeat-template')"
                     min="1">
                  <xh:tr>
                    <xh:td>
                      <xf:input id="in-repeat-control" bind="in-repeat-bind" class="fr-summary fr-search">
                        <xf:label ref="$form-resources/in-repeat/label"/>
                        <xf:hint ref="$form-resources/in-repeat/hint"/>
                        <xf:help ref="$form-resources/in-repeat/help"/>
                        <xf:alert ref="$fr-resources/detail/labels/alert"/>
                      </xf:input>
                    </xh:td>
                  </xh:tr>
                </fr:grid>
              </fr:section>
            </fr:body>
          </fr:view>
        </xh:body>
      </xh:html>

    val indexedControls = Index.findIndexedControls(elemToDocumentInfo(formElem))

    assert(indexedControls(0) === IndexedControl("in-summary", false, true,  "section-1/in-summary",       "xs:string", "input", false))
    assert(indexedControls(1) === IndexedControl("in-search",  true,  false, "section-1/in-search",        "xs:string", "input", false))
    assert(indexedControls(2) === IndexedControl("in-both",    true,  true,  "section-1/in-both",          "xs:string", "input", false))
    assert(indexedControls(3) === IndexedControl("date",       true,  true,  "section-1/date",             "xf:date"  , "input", false))
    assert(indexedControls(4) === IndexedControl("in-repeat",  true,  true,  "section-1/repeat/in-repeat", "xs:string", "input", false))
  }
}
