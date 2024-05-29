/**
  * Copyright (C) 2026 Orbeon, Inc.
  *
  * This program is free software; you can redistribute it and/or modify it under the terms of the
  * GNU Lesser General Public License as published by the Free Software Foundation; either version
  *  2.1 of the License, or (at your option) any later version.
  *
  * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  * See the GNU Lesser General Public License for more details.
  *
  * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
  */
package org.orbeon.xbl

import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport, XFormsSupport}
import org.orbeon.oxf.xforms.analysis.controls.AttributeControl
import org.orbeon.oxf.xforms.control.controls.XXFormsAttributeControl
import org.orbeon.oxf.xforms.control.{XFormsComponentControl, XFormsControl, XFormsValueComponentControl, XFormsValueControl}
import org.orbeon.oxf.xforms.event.EventCollector
import org.orbeon.oxf.xml.dom.Converter.*
import org.scalatest.funspec.AnyFunSpecLike


class UrlAndHyperlinkTest
  extends DocumentTestBase
     with ResourceManagerSupport
     with AnyFunSpecLike
     with XFormsSupport {

  describe("UrlSupport") {

    it("must accept valid http and https web URLs and trim whitespace") {
      val validUrls = List(
        "https://www.orbeon.com"                                    -> "https://www.orbeon.com",
        "  https://doc.orbeon.com/form-runner/overview  "           -> "https://doc.orbeon.com/form-runner/overview",
        "http://example.co.uk:8080/path/to/page?a=1&b=2#section-2"  -> "http://example.co.uk:8080/path/to/page?a=1&b=2#section-2",
        "http://localhost:9090/orbeon-forms-pe/fr/"                 -> "http://localhost:9090/orbeon-forms-pe/fr/",
        "http://192.168.1.42:3000/status"                           -> "http://192.168.1.42:3000/status",
        "http://[2001:db8::1]:8080/health"                          -> "http://[2001:db8::1]:8080/health",
        "https://münchen.de/rathaus"                                -> "https://münchen.de/rathaus",
        "https://xn--mnchen-3ya.de"                                 -> "https://xn--mnchen-3ya.de",
        "HTTPS://Example.COM/Path"                                  -> "HTTPS://Example.COM/Path",
      )

      for ((input, expectedNormalized) <- validUrls) {
        assert(UrlSupport.isValidWebUrl(input), s"Expected `$input` to be valid")
        assert(UrlSupport.normalizeValidWebUrl(input).contains(expectedNormalized), s"Unexpected normalization for `$input`")
      }

      assert(UrlSupport.isValidWebUrl(""))
      assert(UrlSupport.isValidWebUrl("   "))
      assert(UrlSupport.normalizeValidWebUrl("").isEmpty)
      assert(UrlSupport.normalizeValidWebUrl("   ").isEmpty)
    }

    it("must reject malformed or non-http(s) URLs for fr:url") {
      val invalidUrls = List(
        "orbeon.com",
        "www.orbeon.com",
        "ftp://files.orbeon.com/archive.zip",
        "mailto:info@orbeon.com",
        "javascript:alert(1)",
        "data:text/html,<script>alert(1)</script>",
        "https://",
        "https:///path-only",
        "https://user:pass@example.com",
        "https://example..com",
        "https://.example.com",
        "https://example.com.",
        "https://-example.com",
        "https://example-.com",
        "https://example.c",
        "https://example.123",
        "https://999.999.999.999",
        "https://192.168.1",
        "https://example.com:0",
        "https://example.com:65536",
        "https://example.com:abc",
        "https://example.com/has space",
      )

      for (input <- invalidUrls) {
        assert(! UrlSupport.isValidWebUrl(input), s"Expected `$input` to be invalid")
        assert(UrlSupport.normalizeValidWebUrl(input).isEmpty, s"Expected `$input` normalization to be empty")
      }
    }

    it("must allow safe hyperlink schemes and relative paths while blocking dangerous schemes") {
      val safeUrls = List(
        "https://www.orbeon.com/forms",
        "http://intranet/wiki/page",
        "mailto:support@orbeon.com?subject=Help",
        "tel:+1-555-0100",
        "/fr/orbeon/bookshelf/summary",
        "/",
        "./details",
        "../summary",
        "#section-terms",
        "?page=2",
      )

      for (url <- safeUrls)
        assert(UrlSupport.isSafeHyperlinkUrl(url), s"Expected `$url` to be safe")

      val unsafeUrls = List(
        "",
        "   ",
        "javascript:alert(document.cookie)",
        "  JaVaScRiPt:alert(1)  ",
        "data:text/html;base64,PHNjcmlwdD5hbGVydCgxKTwvc2NyaXB0Pg==",
        "vbscript:msgbox(1)",
        "file:///etc/passwd",
        "//evil.example.com/phish",
        "/\\evil.example.com",
        "mailto:   ",
        "tel:",
        "https://user@evil.example.com",
        "https://example.com/\nheader:injected",
      )

      for (url <- unsafeUrls)
        assert(! UrlSupport.isSafeHyperlinkUrl(url), s"Expected `$url` to be rejected")
    }
  }

  describe("fr:url control") {

    it("must validate web URLs, trim whitespace, and update open-link state in edit and static-readonly modes") {

      val xhtml =
        <xh:html
          xmlns:xf="http://www.w3.org/2002/xforms"
          xmlns:xh="http://www.w3.org/1999/xhtml"
          xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
          xmlns:fr="http://orbeon.org/oxf/xml/form-runner">
          <xh:head>
            <xf:model xxf:function-library="org.orbeon.oxf.fr.library.FormRunnerFunctionLibrary">
              <xf:instance id="fr-form-instance">
                <form>
                  <website>https://www.orbeon.com</website>
                  <readonly-website>https://doc.orbeon.com</readonly-website>
                </form>
              </xf:instance>
              <xf:bind ref="website" type="fr:url" xxf:whitespace="trim"/>
              <xf:bind ref="readonly-website" type="fr:url" xxf:whitespace="trim"/>
            </xf:model>
          </xh:head>
          <xh:body>
            <fr:url id="website-control" ref="website">
              <xf:label>Website</xf:label>
            </fr:url>
            <fr:url id="readonly-website-control" ref="readonly-website" fr:static-readonly="true">
              <xf:label>Readonly Website</xf:label>
            </fr:url>
          </xh:body>
        </xh:html>.toDocument

      withTestExternalContext { _ =>
        withXFormsDocument(xhtml) { doc =>
          val urlComponent =
            doc.resolveObjectByIdInScope("#document", "website-control", None).get.asInstanceOf[XFormsValueComponentControl]
          val innerInput =
            urlComponent.nestedContainerOpt.get
              .resolveObjectByIdInScope(urlComponent.innerRootControl.effectiveId, "input", None)
              .get.asInstanceOf[XFormsValueControl]

          def openDataHref: String =
            doc.controls.getCurrentControlTree.effectiveIdsToControls.values
              .collect {
                case c: XXFormsAttributeControl
                  if c.effectiveId.startsWith(urlComponent.effectiveId + "≡") &&
                     c.staticControl.asInstanceOf[AttributeControl].attributeName == "data-href" =>
                  c.getExternalValue(EventCollector.Throw)
              }
              .head

          assert(urlComponent.getValue(EventCollector.Throw) == "https://www.orbeon.com")
          assert(urlComponent.isValid)
          assert(openDataHref == "https://www.orbeon.com")

          setControlValueWithEventSearchNested(innerInput.effectiveId, "  https://localhost:9090/orbeon-forms-pe/fr/  ")
          assert(urlComponent.getValue(EventCollector.Throw) == "https://localhost:9090/orbeon-forms-pe/fr/")
          assert(urlComponent.isValid)
          assert(openDataHref == "https://localhost:9090/orbeon-forms-pe/fr/")

          setControlValueWithEventSearchNested(innerInput.effectiveId, "not-a-valid-url")
          assert(urlComponent.getValue(EventCollector.Throw) == "not-a-valid-url")
          assert(! urlComponent.isValid)
          assert(openDataHref == "")

          setControlValueWithEventSearchNested(innerInput.effectiveId, "")
          assert(urlComponent.getValue(EventCollector.Throw) == "")
          assert(urlComponent.isValid)
          assert(openDataHref == "")

          val readonlyComponent =
            doc.resolveObjectByIdInScope("#document", "readonly-website-control", None).get.asInstanceOf[XFormsComponentControl]
          val readonlyOutputs =
            doc.controls.getCurrentControlTree.effectiveIdsToControls.values.collect {
              case c: XFormsValueControl
                if ! c.isInstanceOf[XXFormsAttributeControl] &&
                   c.effectiveId.startsWith(readonlyComponent.effectiveId + "≡") &&
                   c.isRelevant =>
                c.getValue(EventCollector.Throw)
            }.toList
          assert(readonlyOutputs == List("https://doc.orbeon.com"))
        }
      }
    }
  }

  describe("fr:hyperlink control") {

    it("must resolve static and AVT hrefs, fallback to bound value, enforce safe schemes, and support open-in-new-tab") {

      val xhtml =
        <xh:html
          xmlns:xf="http://www.w3.org/2002/xforms"
          xmlns:xh="http://www.w3.org/1999/xhtml"
          xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
          xmlns:fr="http://orbeon.org/oxf/xml/form-runner">
          <xh:head>
            <xf:model xxf:function-library="org.orbeon.oxf.fr.library.FormRunnerFunctionLibrary">
              <xf:instance id="fr-form-instance">
                <form>
                  <order-id>ORD-42</order-id>
                  <static-link/>
                  <avt-link/>
                  <bound-link>https://www.orbeon.com/pricing</bound-link>
                  <unsafe-link>javascript:alert(1)</unsafe-link>
                </form>
              </xf:instance>
            </xf:model>
          </xh:head>
          <xh:body>
            <xf:input id="order-id-control" ref="order-id">
              <xf:label>Order ID</xf:label>
            </xf:input>

            <fr:hyperlink id="static-link-control" ref="static-link" href="https://www.orbeon.com">
              <xf:label>Visit Orbeon</xf:label>
            </fr:hyperlink>

            <fr:hyperlink id="avt-link-control" ref="avt-link" href="https://orders.example.com/{../order-id}" open-in-new-tab="false">
              <xf:label/>
            </fr:hyperlink>

            <fr:hyperlink id="bound-link-control" ref="bound-link">
              <xf:label>Pricing</xf:label>
            </fr:hyperlink>

            <fr:hyperlink id="unsafe-link-control" ref="unsafe-link">
              <xf:label>Unsafe</xf:label>
            </fr:hyperlink>
          </xh:body>
        </xh:html>.toDocument

      withTestExternalContext { _ =>
        withXFormsDocument(xhtml) { doc =>

          def relevantAttrValues(componentId: String): Map[String, String] = {
            val comp = doc.resolveObjectByIdInScope("#document", componentId, None).get.asInstanceOf[XFormsComponentControl]
            doc.controls.getCurrentControlTree.effectiveIdsToControls.values
              .collect {
                case c: XXFormsAttributeControl
                  if c.effectiveId.startsWith(comp.effectiveId + "≡") && c.isRelevant =>
                  c.staticControl.asInstanceOf[AttributeControl].attributeName -> c.getExternalValue(EventCollector.Throw)
              }
              .toMap
          }

          def relevantOutputText(componentId: String): List[String] = {
            val comp = doc.resolveObjectByIdInScope("#document", componentId, None).get.asInstanceOf[XFormsControl]
            doc.controls.getCurrentControlTree.effectiveIdsToControls.values
              .collect {
                case c: XFormsValueControl
                  if ! c.isInstanceOf[XXFormsAttributeControl] &&
                     c.effectiveId.startsWith(comp.effectiveId + "≡") &&
                     c.isRelevant =>
                  c.getValue(EventCollector.Throw)
              }
              .toList
          }

          // 1. Static href + default open-in-new-tab = true
          assert(relevantAttrValues("static-link-control") == Map(
            "href"   -> "https://www.orbeon.com",
            "target" -> "_blank",
            "rel"    -> "noopener noreferrer"
          ))
          assert(relevantOutputText("static-link-control") == List("Visit Orbeon"))

          // 2. Dynamic AVT href + open-in-new-tab = false + blank label falls back to resolved URL
          assert(relevantAttrValues("avt-link-control") == Map(
            "href"   -> "https://orders.example.com/ORD-42",
            "target" -> "_self",
            "rel"    -> ""
          ))
          assert(relevantOutputText("avt-link-control") == List("https://orders.example.com/ORD-42"))

          // Update order-id and verify AVT updates reactively
          setControlValueWithEventSearchNested("order-id-control", "ORD-99")
          assert(relevantAttrValues("avt-link-control")("href") == "https://orders.example.com/ORD-99")
          assert(relevantOutputText("avt-link-control") == List("https://orders.example.com/ORD-99"))

          // 3. Fallback to bound value when @href is absent
          assert(relevantAttrValues("bound-link-control")("href") == "https://www.orbeon.com/pricing")
          assert(relevantOutputText("bound-link-control") == List("Pricing"))

          // 4. Dangerous javascript: URL is blocked (no active <a href> attributes are relevant)
          assert(relevantAttrValues("unsafe-link-control").isEmpty)
          assert(relevantOutputText("unsafe-link-control") == List("Unsafe"))
        }
      }
    }
  }
}
