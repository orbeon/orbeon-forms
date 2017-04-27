package org.orbeon.oxf.fr

import org.orbeon.oxf.fr.persistence.relational.index.Index
import org.orbeon.oxf.fr.persistence.relational.index.Index.IndexedControl
import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport}
import org.orbeon.scaxon.XML._
import org.scalatest.FunSpecLike


class IndexTest
  extends DocumentTestBase
     with ResourceManagerSupport
     with FunSpecLike {

  describe("The `findIndexedControls` function") {

    val formElem =
      <xh:html xmlns:xh="http://www.w3.org/1999/xhtml"
           xmlns:xf="http://www.w3.org/2002/xforms"
           xmlns:fr="http://orbeon.org/oxf/xml/form-runner"
           xmlns:xs="http://www.w3.org/2001/XMLSchema">
        <xh:head>
          <xf:model id="fr-form-model">
            <xf:bind id="fr-form-binds" ref="instance('fr-form-instance')">
              <xf:bind id="my-section-bind" name="my-section" ref="my-section">
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
              <fr:section id="my-section-control" bind="my-section-bind">
                <xf:label ref="$form-resources/my-section/label"/>
                <xf:help ref="$form-resources/my-section/help"/>
                <fr:grid>
                  <xh:tr>
                    <xh:td>
                      <xf:input id="in-summary-control" bind="in-summary-bind" class="fr-summary">
                        <xf:label ref="$form-resources/in-summary/label" mediatype="text/html"/>
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

    val expected = List(
      IndexedControl("in-summary", inSearch = false, inSummary = true,  "my-section/in-summary",       "xs:string", "input", htmlLabel = true,  resources = Nil),
      IndexedControl("in-search",  inSearch = true,  inSummary = false, "my-section/in-search",        "xs:string", "input", htmlLabel = false, resources = Nil),
      IndexedControl("in-both",    inSearch = true,  inSummary = true,  "my-section/in-both",          "xs:string", "input", htmlLabel = false, resources = Nil),
      IndexedControl("date",       inSearch = true,  inSummary = true,  "my-section/date",             "xf:date"  , "input", htmlLabel = false, resources = Nil),
      IndexedControl("in-repeat",  inSearch = true,  inSummary = true,  "my-section/repeat/in-repeat", "xs:string", "input", htmlLabel = false, resources = Nil)
    )

    it("must find the expected indexed controls") {
      assert(expected == Index.findIndexedControls(elemToDocumentInfo(formElem), app = "test", form = "index"))
    }
  }
}
