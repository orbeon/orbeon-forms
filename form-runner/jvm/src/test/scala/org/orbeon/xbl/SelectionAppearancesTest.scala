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
package org.orbeon.xbl

import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport, XFormsSupport}
import org.orbeon.oxf.util.StringUtils.*
import org.orbeon.oxf.xforms.analysis.PartAnalysisBuilder
import org.orbeon.oxf.xforms.processor.handlers.XHTMLOutput
import org.orbeon.oxf.xforms.state.{AnnotatedTemplate, XFormsStateManager}
import org.orbeon.oxf.xml.dom.Converter.*
import org.orbeon.oxf.xml.dom.Extensions.*
import org.orbeon.oxf.xml.dom.LocationSAXContentHandler
import org.scalatest.funspec.AnyFunSpecLike


class SelectionAppearancesTest
  extends DocumentTestBase
     with ResourceManagerSupport
     with AnyFunSpecLike
     with XFormsSupport {

  describe("Segmented Button Bar and Choice Cards selection appearances") {

    it("must bind xf:select1[appearance ~= segmented], xf:select1[appearance ~= cards], and xf:select[appearance ~= cards] and render full items with inline hints") {

      val xhtml =
        <xh:html
          xmlns:xf="http://www.w3.org/2002/xforms"
          xmlns:xh="http://www.w3.org/1999/xhtml"
          xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
          xmlns:fr="http://orbeon.org/oxf/xml/form-runner">
          <xh:head>
            <xf:model id="fr-form-model" xxf:function-library="org.orbeon.oxf.fr.library.FormRunnerFunctionLibrary">
              <xf:instance id="fr-form-instance">
                <form>
                  <billing>monthly</billing>
                  <tier>pro</tier>
                  <addons>sso audit</addons>
                </form>
              </xf:instance>
            </xf:model>
          </xh:head>
          <xh:body>
            <xf:select1 id="billing-control" ref="billing" appearance="segmented" xxf:encrypt-item-values="false">
              <xf:label>Billing Cycle</xf:label>
              <xf:item>
                <xf:label>Monthly</xf:label>
                <xf:value>monthly</xf:value>
              </xf:item>
              <xf:item>
                <xf:label>Quarterly</xf:label>
                <xf:value>quarterly</xf:value>
              </xf:item>
              <xf:item>
                <xf:label>Annual</xf:label>
                <xf:value>annual</xf:value>
              </xf:item>
            </xf:select1>

            <xf:select1 id="tier-control" ref="tier" appearance="cards" xxf:encrypt-item-values="false">
              <xf:label>Plan Tier</xf:label>
              <xf:item>
                <xf:label>Starter</xf:label>
                <xf:value>starter</xf:value>
                <xf:hint>Up to 5 team members and basic forms</xf:hint>
              </xf:item>
              <xf:item>
                <xf:label>Professional</xf:label>
                <xf:value>pro</xf:value>
                <xf:hint>Unlimited forms, workflows, and PDF export</xf:hint>
              </xf:item>
              <xf:item>
                <xf:label>Enterprise</xf:label>
                <xf:value>enterprise</xf:value>
                <xf:hint>Dedicated SLA, SSO, and custom integrations</xf:hint>
              </xf:item>
            </xf:select1>

            <xf:select id="addons-control" ref="addons" appearance="cards" xxf:encrypt-item-values="false">
              <xf:label>Optional Add-ons</xf:label>
              <xf:item>
                <xf:label>Single Sign-On (SSO)</xf:label>
                <xf:value>sso</xf:value>
                <xf:hint>SAML 2.0 and OIDC integration</xf:hint>
              </xf:item>
              <xf:item>
                <xf:label>Audit Trail</xf:label>
                <xf:value>audit</xf:value>
                <xf:hint>Full revision history and compliance export</xf:hint>
              </xf:item>
              <xf:item>
                <xf:label>Dedicated Support</xf:label>
                <xf:value>support</xf:value>
                <xf:hint>24/7 phone and Slack channel</xf:hint>
              </xf:item>
            </xf:select>
          </xh:body>
        </xh:html>.toDocument

      withTestExternalContext { implicit ec =>
        withXFormsDocument(xhtml) { doc =>
          withActionAndDoc(doc) {

            val template = AnnotatedTemplate(PartAnalysisBuilder.createFromDocument(xhtml)(XFormsStateManager.newIndentedLogger)._1)

            def renderXhtml() = {
              val receiver = new LocationSAXContentHandler
              XHTMLOutput.send(doc, template, ec)(receiver)
              receiver.getDocument
            }

            val rendered = renderXhtml()
            val allElems = rendered.getRootElement.descendantElementIterator(includeSelf = true).toList

            // 1. Segmented select1
            val billingElem = allElems.find(_.attributeValue("id") == "billing-control").get
            val billingClasses = billingElem.attributeValue("class").splitTo[Set]()
            assert(billingClasses.contains("xbl-fr-segmented-select1"))
            val billingRadios =
              billingElem.descendantElementIterator(includeSelf = true)
                .filter(e => e.getName == "input" && e.attributeValue("type") == "radio")
                .toList
            assert(billingRadios.size == 3)
            assert(getControlValue("billing-control≡select-elem") == "monthly")

            // Change value of segmented select1 and verify visited state and value propagation
            setControlValueWithEventSearchNested("billing-control", "annual")
            assert(getControlValue("billing-control≡select-elem") == "annual")
            assert(doc.getControlByEffectiveId("billing-control").visited)

            // 2. Choice Cards single-selection (select1)
            val tierElem = allElems.find(_.attributeValue("id") == "tier-control").get
            val tierClasses = tierElem.attributeValue("class").splitTo[Set]()
            assert(tierClasses.contains("xbl-fr-cards-select1"))
            val tierHints =
              tierElem.descendantElementIterator(includeSelf = true)
                .filter(e => e.getName == "span" && Option(e.attributeValue("class")).exists(_.splitTo[Set]().contains("xforms-hint")))
                .map(_.getText)
                .toList
            assert(tierHints == List(
              "Up to 5 team members and basic forms",
              "Unlimited forms, workflows, and PDF export",
              "Dedicated SLA, SSO, and custom integrations"
            ))
            assert(getControlValue("tier-control≡select-elem") == "pro")

            setControlValueWithEventSearchNested("tier-control", "enterprise")
            assert(getControlValue("tier-control≡select-elem") == "enterprise")
            assert(doc.getControlByEffectiveId("tier-control").visited)

            // 3. Choice Cards multi-selection (select)
            val addonsElem = allElems.find(_.attributeValue("id") == "addons-control").get
            val addonsClasses = addonsElem.attributeValue("class").splitTo[Set]()
            assert(addonsClasses.contains("xbl-fr-cards-select"))
            val addonsCheckboxes =
              addonsElem.descendantElementIterator(includeSelf = true)
                .filter(e => e.getName == "input" && e.attributeValue("type") == "checkbox")
                .toList
            assert(addonsCheckboxes.size == 3)
            val addonsHints =
              addonsElem.descendantElementIterator(includeSelf = true)
                .filter(e => e.getName == "span" && Option(e.attributeValue("class")).exists(_.splitTo[Set]().contains("xforms-hint")))
                .map(_.getText)
                .toList
            assert(addonsHints.size == 3)
            assert(getControlValue("addons-control≡select-elem") == "sso audit")

            setControlValueWithEventSearchNested("addons-control", "sso support")
            assert(getControlValue("addons-control≡select-elem") == "sso support")
            assert(doc.getControlByEffectiveId("addons-control").visited)
          }
        }
      }
    }
  }
}
