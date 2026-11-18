package org.orbeon.oxf.xforms.model

import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport}
import org.orbeon.oxf.xml.dom.Converter.*
import org.scalatest.funspec.AnyFunSpecLike


class MipsTest
extends DocumentTestBase
   with ResourceManagerSupport
   with AnyFunSpecLike {

  describe("#6442: Possible `rebuild` followed by `refresh` but without `recalculate`") {
    it("must preserve `readonly` and `relevant` MIPs") {

      val doc =
          <xh:html
              xmlns:xh="http://www.w3.org/1999/xhtml"
              xmlns:xf="http://www.w3.org/2002/xforms"
              xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
              xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xh:head>
                  <xf:model id="my-model" xxf:xpath-analysis="true">
                      <xf:instance id="my-instance">
                          <_ xmlns="">
                              <readonly/>
                              <relevant/>
                          </_>
                      </xf:instance>
                      <xf:bind id="readonly-bind" ref="readonly" readonly="true()"/>
                      <xf:bind id="relevant-bind" ref="relevant" relevant="false()"/>
                  </xf:model>
              </xh:head>
              <xh:body>
                  <xf:input id="my-readonly-input" ref="readonly">
                      <xf:label>readonly</xf:label>
                  </xf:input>
                  <xf:input id="my-nonrelevant-input" ref="relevant">
                      <xf:label>relevant</xf:label>
                  </xf:input>
                  <xf:trigger id="my-trigger">
                      <xf:label>Test</xf:label>
                      <xf:action event="DOMActivate">
                          <xf:insert context="instance('my-instance')" ref="*" origin="xf:element('foo')"/>
                          <!-- This takes place before a rebuild. -->
                          <xf:recalculate/>
                          <!-- Later, rebuild will take place, but no recalculate (before fixing #6442). -->
                      </xf:action>
                  </xf:trigger>
              </xh:body>
          </xh:html>.toDocument

      withTestExternalContext { implicit ec =>

        withActionAndDoc(setupDocument(doc, setupBeforeExternalEvents = false)) {

          activateControlWithEvent("my-trigger")

          assert(isReadonly("my-readonly-input"))
          assert(! isRelevant("my-nonrelevant-input"))
        }
      }
    }
  }

  describe("Unknown schema type on `xf:bind`") {
    it("must dispatch `xxforms-binding-error` and mark node invalid without failing fatally when no schema is attached") {

      val doc =
        <xh:html
            xmlns:xh="http://www.w3.org/1999/xhtml"
            xmlns:xf="http://www.w3.org/2002/xforms"
            xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
            xmlns:bk="http://example.org/books">
            <xh:head>
                <xf:model id="my-model">
                    <xf:instance id="my-instance">
                        <_ xmlns="">
                            <year>2024</year>
                            <errors>0</errors>
                        </_>
                    </xf:instance>
                    <xf:bind id="year-bind" ref="year" type="bk:publication-year"/>
                    <xf:action event="xxforms-binding-error" target="#observer" defaultAction="cancel">
                        <xf:setvalue ref="instance('my-instance')/errors" value="xs:integer(.) + 1"/>
                    </xf:action>
                </xf:model>
            </xh:head>
            <xh:body>
                <xf:input id="year-input" ref="year">
                    <xf:label>Year</xf:label>
                </xf:input>
                <xf:output id="errors-output" ref="errors"/>
            </xh:body>
        </xh:html>.toDocument

      withTestExternalContext { implicit ec =>
        withActionAndDoc(setupDocument(doc)) {
          assert(! isValid("year-input"))
          assert(getControlValue("errors-output") == "1")
        }
      }
    }

    it("must dispatch `xxforms-binding-error` and mark node invalid when attached schema does not define the type") {

      val doc =
        <xh:html
            xmlns:xh="http://www.w3.org/1999/xhtml"
            xmlns:xf="http://www.w3.org/2002/xforms"
            xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
            xmlns:xs="http://www.w3.org/2001/XMLSchema"
            xmlns:bk="http://example.org/books">
            <xh:head>
                <xf:model id="my-model">
                    <xs:schema targetNamespace="http://example.org/books" xmlns="http://example.org/books">
                        <xs:simpleType name="rating">
                            <xs:restriction base="xs:integer">
                                <xs:minInclusive value="1"/>
                                <xs:maxInclusive value="5"/>
                            </xs:restriction>
                        </xs:simpleType>
                    </xs:schema>
                    <xf:instance id="my-instance">
                        <_ xmlns="">
                            <year>2024</year>
                            <errors>0</errors>
                        </_>
                    </xf:instance>
                    <xf:bind id="year-bind" ref="year" type="bk:publication-year"/>
                    <xf:action event="xxforms-binding-error" target="#observer" defaultAction="cancel">
                        <xf:setvalue ref="instance('my-instance')/errors" value="xs:integer(.) + 1"/>
                    </xf:action>
                </xf:model>
            </xh:head>
            <xh:body>
                <xf:input id="year-input" ref="year">
                    <xf:label>Year</xf:label>
                </xf:input>
                <xf:output id="errors-output" ref="errors"/>
            </xh:body>
        </xh:html>.toDocument

      withTestExternalContext { implicit ec =>
        withActionAndDoc(setupDocument(doc)) {
          assert(! isValid("year-input"))
          assert(getControlValue("errors-output") == "1")
        }
      }
    }
  }
}
