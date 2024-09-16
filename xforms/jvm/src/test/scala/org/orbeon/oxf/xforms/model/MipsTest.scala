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
}
