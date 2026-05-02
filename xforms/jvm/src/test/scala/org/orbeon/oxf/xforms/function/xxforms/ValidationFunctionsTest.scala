package org.orbeon.oxf.xforms.function.xxforms

import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport}
import org.orbeon.oxf.xml.dom.Converter.*
import org.scalatest.funspec.AnyFunSpecLike


class ValidationFunctionsTest
  extends DocumentTestBase
     with ResourceManagerSupport
     with AnyFunSpecLike {

  describe("xxf:max-length() and xxf:min-length()") {
    it("counts the text content for xf:HTMLFragment") {

      val doc =
        <xh:html
            xmlns:xh="http://www.w3.org/1999/xhtml"
            xmlns:xf="http://www.w3.org/2002/xforms"
            xmlns:xxf="http://orbeon.org/oxf/xml/xforms">
            <xh:head>
                <xf:model id="my-model">
                    <xf:instance id="my-instance">
                        <_ xmlns="">
                            <max-html>&lt;p&gt;12345&lt;/p&gt;</max-html>
                            <min-html>&lt;p&gt;12345&lt;/p&gt;</min-html>
                            <inner-text/>
                        </_>
                    </xf:instance>
                    <xf:bind ref="max-html" type="xf:HTMLFragment" constraint="xxf:max-length(5)"/>
                    <xf:bind ref="min-html" type="xf:HTMLFragment" constraint="xxf:min-length(5)"/>
                    <xf:bind ref="inner-text" calculate="xxf:inner-text(../max-html)"/>
                </xf:model>
            </xh:head>
            <xh:body>
                <xf:input id="max-html-input" ref="max-html">
                    <xf:label>HTML</xf:label>
                </xf:input>
                <xf:input id="min-html-input" ref="min-html">
                    <xf:label>HTML</xf:label>
                </xf:input>
                <xf:output id="inner-text-output" ref="inner-text">
                    <xf:label>Inner text</xf:label>
                </xf:output>
            </xh:body>
        </xh:html>.toDocument

      withTestExternalContext { _ =>
        withActionAndDoc(setupDocument(doc)) {
          assert(isValid("max-html-input"))
          assert(isValid("min-html-input"))
          assert(getControlValue("inner-text-output") == "12345")

          setControlValue("max-html-input", "<p>123456</p>")
          setControlValue("min-html-input", "<p>1234</p>")

          assert(! isValid("max-html-input"))
          assert(! isValid("min-html-input"))
        }
      }
    }
  }
}
