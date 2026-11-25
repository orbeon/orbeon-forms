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
package org.orbeon.oxf.xforms.analysis

import org.orbeon.oxf.common.Version
import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport}
import org.orbeon.oxf.xforms.action.XFormsAPI
import org.orbeon.oxf.xml.dom.Converter.*
import org.orbeon.scaxon.SimplePath.*
import org.scalatest.funspec.AnyFunSpecLike


class NonRelevantMipTest
  extends DocumentTestBase
     with ResourceManagerSupport
     with AnyFunSpecLike {

  describe("#2760: `xxf:nonrelevant` MIP") {

    it("must clear non-relevant elements and attributes on initial load and dynamic toggles while respecting closest-ancestor inheritance") {
      val doc =
        <xh:html
          xmlns:xh="http://www.w3.org/1999/xhtml"
          xmlns:xf="http://www.w3.org/2002/xforms"
          xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
          xmlns:xs="http://www.w3.org/2001/XMLSchema">
          <xh:head>
            <xf:model id="my-model" xxf:expose-xpath-types="true" xxf:xpath-analysis="true">
              <xf:instance id="my-instance">
                <form xmlns="">
                  <show-s1>false</show-s1>
                  <show-s2>true</show-s2>
                  <section-1>
                    <a code="A1">init-a</a>
                    <b>init-b</b>
                  </section-1>
                  <section-2>
                    <c>init-c</c>
                    <d>init-d</d>
                  </section-2>
                </form>
              </xf:instance>
              <xf:bind id="root-bind" ref="instance('my-instance')" xxf:nonrelevant="empty">
                <xf:bind ref="show-s1" type="xs:boolean"/>
                <xf:bind ref="show-s2" type="xs:boolean"/>
                <xf:bind id="s1-bind" ref="section-1" relevant="../show-s1 = true()">
                  <xf:bind ref="a"/>
                  <xf:bind ref="b" xxf:nonrelevant="keep"/>
                </xf:bind>
                <xf:bind id="s2-bind" ref="section-2" relevant="../show-s2 = true()" xxf:nonrelevant="keep">
                  <xf:bind ref="c"/>
                  <xf:bind ref="d" xxf:nonrelevant="empty"/>
                </xf:bind>
              </xf:bind>
            </xf:model>
          </xh:head>
          <xh:body>
            <xf:input id="show-s1-input" ref="show-s1"/>
            <xf:input id="show-s2-input" ref="show-s2"/>
            <xf:input id="a-input" ref="section-1/a"/>
            <xf:input id="b-input" ref="section-1/b"/>
            <xf:input id="c-input" ref="section-2/c"/>
            <xf:input id="d-input" ref="section-2/d"/>
          </xh:body>
        </xh:html>.toDocument

      withTestExternalContext { _ =>
        withActionAndDoc(setupDocument(doc)) {
          val root = document.findInstance("my-instance").get.rootElement

          // Initial load: section-1 is non-relevant; `a` (and `@code`) inherits `empty`, while `b` overrides to `keep`
          assert((root / "section-1" / "a").stringValue == "")
          assert((root / "section-1" / "a" /@ "code").stringValue == "")
          assert((root / "section-1" / "b").stringValue == "init-b")

          // section-2 is initially relevant
          assert((root / "section-2" / "c").stringValue == "init-c")
          assert((root / "section-2" / "d").stringValue == "init-d")

          // Hide section-2: `c` inherits `keep` from section-2, while `d` overrides back to `empty`
          setControlValueWithEventSearchNested("show-s2-input", "false")
          assert((root / "section-2" / "c").stringValue == "init-c")
          assert((root / "section-2" / "d").stringValue == "")

          // Show section-1, enter new value in `a`, then hide section-1 again
          setControlValueWithEventSearchNested("show-s1-input", "true")
          setControlValueWithEventSearchNested("a-input", "typed-a")
          assert(getControlValue("a-input") == "typed-a")

          setControlValueWithEventSearchNested("show-s1-input", "false")
          assert((root / "section-1" / "a").stringValue == "")
          assert((root / "section-1" / "b").stringValue == "init-b")
        }
      }
    }

    it("must cascade cleared values to downstream `calculate`, `relevant`, `required`, and `constraint` MIPs in the same RRR") {
      val doc =
        <xh:html
          xmlns:xh="http://www.w3.org/1999/xhtml"
          xmlns:xf="http://www.w3.org/2002/xforms"
          xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
          xmlns:xs="http://www.w3.org/2001/XMLSchema">
          <xh:head>
            <xf:model id="my-model" xxf:expose-xpath-types="true" xxf:xpath-analysis="true">
              <xf:instance id="my-instance">
                <form xmlns="">
                  <include-extra>true</include-extra>
                  <extra>25</extra>
                  <chained>bonus</chained>
                  <base>100</base>
                  <total/>
                  <note>ok</note>
                </form>
              </xf:instance>
              <xf:bind ref="include-extra" name="include-extra" type="xs:boolean"/>
              <xf:bind ref="extra" name="extra" relevant="$include-extra = true()" xxf:nonrelevant="empty"/>
              <xf:bind ref="chained" name="chained" relevant="xxf:non-blank($extra)" xxf:nonrelevant="empty"/>
              <xf:bind ref="base" name="base" type="xs:integer"/>
              <xf:bind
                ref="total"
                name="total"
                calculate="$base + (if ($extra castable as xs:integer) then xs:integer($extra) else 0) + (if (xxf:non-blank($chained)) then 5 else 0)"/>
              <xf:bind
                ref="note"
                required="xxf:non-blank($chained)"
                constraint="if ($total = 100) then . = 'base-only' else true()"/>
            </xf:model>
          </xh:head>
          <xh:body>
            <xf:input id="include-extra-input" ref="include-extra"/>
            <xf:input id="extra-input" ref="extra"/>
            <xf:input id="chained-input" ref="chained"/>
            <xf:output id="total-output" ref="total"/>
            <xf:input id="note-input" ref="note"/>
          </xh:body>
        </xh:html>.toDocument

      withTestExternalContext { _ =>
        withActionAndDoc(setupDocument(doc)) {
          val root = document.findInstance("my-instance").get.rootElement

          assert(getControlValue("total-output") == "130")
          assert(isRequired("note-input"))
          assert(isValid("note-input"))

          // Hiding `extra` clears `extra`, which in Pass 2 makes `chained` non-relevant and clears `chained`,
          // which in Pass 3 updates `total` to 100, makes `note` non-required, and invalidates `note`'s constraint!
          setControlValueWithEventSearchNested("include-extra-input", "false")

          assert((root / "extra").stringValue == "")
          assert((root / "chained").stringValue == "")
          assert(getControlValue("total-output") == "100")
          assert(! isRequired("note-input"))
          assert(! isValid("note-input"))
        }
      }
    }

    it("must keep calculated nodes empty while non-relevant and recompute in topological order when becoming relevant again, without re-applying `xxf:default`") {
      assume(Version.isPE)
      val doc =
        <xh:html
          xmlns:xh="http://www.w3.org/1999/xhtml"
          xmlns:xf="http://www.w3.org/2002/xforms"
          xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
          xmlns:xs="http://www.w3.org/2001/XMLSchema">
          <xh:head>
            <xf:model id="my-model" xxf:expose-xpath-types="true" xxf:xpath-analysis="true" xxf:analysis.calculate="true">
              <xf:instance id="my-instance">
                <form xmlns="">
                  <show>false</show>
                  <other>21</other>
                  <c1/>
                  <c2/>
                  <def/>
                </form>
              </xf:instance>
              <xf:bind ref="show" name="show" type="xs:boolean"/>
              <xf:bind ref="other" name="other" type="xs:integer"/>
              <xf:bind id="c2-bind" ref="c2" name="c2" relevant="$show = true()" calculate="if ($c1 castable as xs:integer) then xs:integer($c1) * 2 else -1" xxf:nonrelevant="empty"/>
              <xf:bind id="c1-bind" ref="c1" name="c1" relevant="$show = true()" calculate="$other" xxf:nonrelevant="empty"/>
              <xf:bind id="def-bind" ref="def" name="def" relevant="$show = true()" xxf:default="'initial-default'" xxf:nonrelevant="empty"/>
            </xf:model>
          </xh:head>
          <xh:body>
            <xf:input id="show-input" ref="show"/>
            <xf:input id="other-input" ref="other"/>
            <xf:input id="c1-input" ref="c1"/>
            <xf:input id="c2-input" ref="c2"/>
            <xf:input id="def-input" ref="def"/>
          </xh:body>
        </xh:html>.toDocument

      withTestExternalContext { _ =>
        withActionAndDoc(setupDocument(doc)) {
          val root = document.findInstance("my-instance").get.rootElement

          // Initially `show = false`: `c1`, `c2`, and `def` are all cleared to ""
          assert((root / "c1").stringValue == "")
          assert((root / "c2").stringValue == "")
          assert((root / "def").stringValue == "")

          // Updating `other` while `show = false` must NOT populate `c1` or `c2`
          setControlValueWithEventSearchNested("other-input", "25")
          assert((root / "c1").stringValue == "")
          assert((root / "c2").stringValue == "")

          // Turning `show = true` (without touching `other`) must recompute `c1` ("25") then `c2` ("50"), while `def` stays ""
          setControlValueWithEventSearchNested("show-input", "true")
          assert(getControlValue("c1-input") == "25")
          assert(getControlValue("c2-input") == "50")
          assert(getControlValue("def-input") == "")

          // Toggle false then true again (#6370 check)
          setControlValueWithEventSearchNested("show-input", "false")
          assert((root / "c1").stringValue == "")
          assert((root / "c2").stringValue == "")

          setControlValueWithEventSearchNested("show-input", "true")
          assert(getControlValue("c1-input") == "25")
          assert(getControlValue("c2-input") == "50")
        }
      }
    }

    it("must clear composite control attributes and unbound child elements while preserving namespaced annotations and UI switch values") {
      val doc =
        <xh:html
          xmlns:xh="http://www.w3.org/1999/xhtml"
          xmlns:xf="http://www.w3.org/2002/xforms"
          xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
          xmlns:my="http://example.org/my"
          xmlns:xs="http://www.w3.org/2001/XMLSchema">
          <xh:head>
            <xf:model id="my-model" xxf:expose-xpath-types="true">
              <xf:instance id="my-instance">
                <form xmlns="">
                  <show>true</show>
                  <single-att filename="doc.pdf" mediatype="application/pdf" size="123" my:relevant="false">file:/tmp/1</single-att>
                  <multi-att>
                    <_ filename="a.pdf" mediatype="application/pdf" size="10">file:/tmp/a</_>
                    <_ filename="b.pdf" mediatype="application/pdf" size="20">file:/tmp/b</_>
                  </multi-att>
                  <switched>keep-across-case-toggle</switched>
                </form>
              </xf:instance>
              <xf:bind ref="instance('my-instance')" xxf:nonrelevant="empty">
                <xf:bind ref="show" type="xs:boolean"/>
                <xf:bind ref="single-att" relevant="../show = true()"/>
                <xf:bind ref="multi-att" relevant="../show = true()"/>
                <xf:bind ref="switched"/>
              </xf:bind>
            </xf:model>
          </xh:head>
          <xh:body>
            <xf:input id="show-input" ref="show"/>
            <xf:switch id="my-switch" xxf:xforms11-switch="true">
              <xf:case id="case-1" selected="true">
                <xf:input id="switched-input" ref="switched"/>
              </xf:case>
              <xf:case id="case-2"/>
            </xf:switch>
          </xh:body>
        </xh:html>.toDocument

      withTestExternalContext { _ =>
        withActionAndDoc(setupDocument(doc)) {
          val root = document.findInstance("my-instance").get.rootElement

          // Deselecting UI switch case makes `switched-input` UI-non-relevant, but model relevance is still true -> must NOT clear!
          XFormsAPI.toggle("case-2")
          assert((root / "switched").stringValue == "keep-across-case-toggle")

          // Making `single-att` and `multi-att` model-non-relevant clears text and un-namespaced attributes, preserves `@my:relevant`, and keeps `<_>` structure
          setControlValueWithEventSearchNested("show-input", "false")

          val singleAtt = (root / "single-att").head
          assert(singleAtt.stringValue == "")
          assert((singleAtt /@ "filename").stringValue == "")
          assert((singleAtt /@ "mediatype").stringValue == "")
          assert((singleAtt /@ "size").stringValue == "")
          assert(singleAtt.attValueOpt("http://example.org/my" -> "relevant").contains("false"))

          val multiItems = (root / "multi-att" / "_").toList
          assert(multiItems.size == 2)
          assert(multiItems.forall(_.stringValue == ""))
          assert(multiItems.forall(i => (i /@ "filename").stringValue == "" && (i /@ "size").stringValue == ""))
        }
      }
    }

    it("must terminate cleanly without hanging on pathological relevant/calculate cycles") {
      val doc =
        <xh:html
          xmlns:xh="http://www.w3.org/1999/xhtml"
          xmlns:xf="http://www.w3.org/2002/xforms"
          xmlns:xxf="http://orbeon.org/oxf/xml/xforms">
          <xh:head>
            <xf:model id="my-model">
              <xf:instance id="my-instance">
                <form xmlns="">
                  <cycle/>
                </form>
              </xf:instance>
              <xf:bind ref="cycle" calculate="'1'" relevant=". = ''" xxf:nonrelevant="empty"/>
            </xf:model>
          </xh:head>
          <xh:body>
            <xf:output id="cycle-out" ref="cycle"/>
          </xh:body>
        </xh:html>.toDocument

      withTestExternalContext { _ =>
        withActionAndDoc(setupDocument(doc)) {
          assert(document.findInstance("my-instance").isDefined)
        }
      }
    }
  }
}
