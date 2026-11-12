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
import org.orbeon.oxf.xforms.action.XFormsAPI
import org.orbeon.oxf.xforms.control.{XFormsValueComponentControl, XFormsValueControl}
import org.orbeon.oxf.xforms.event.EventCollector
import org.orbeon.oxf.xml.dom.Converter.*
import org.scalatest.funspec.AnyFunSpecLike


class OpenSelectControlTest
  extends DocumentTestBase
     with ResourceManagerSupport
     with AnyFunSpecLike
     with XFormsSupport {

  describe("OpenSelectSupport space & escape character encoding") {

    it("must encode and decode tokens losslessly with '+', '%2B', and '%25'") {
      val testCases = List(
        "Raspberry"         -> "Raspberry",
        "Ice cream"         -> "Ice+cream",
        "  Ice   cream  "   -> "Ice+cream",
        "C++"               -> "C%2B%2B",
        "C++ dev"           -> "C%2B%2B+dev",
        "100% organic"      -> "100%25+organic",
        "A + B = 50% + 50%" -> "A+%2B+B+=+50%25+%2B+50%25"
      )

      testCases.foreach { case (raw, expectedEncoded) =>
        val encodedOpt = OpenSelectSupport.encodeToken(raw)
        assert(encodedOpt.contains(expectedEncoded))
        assert(OpenSelectSupport.decodeToken(expectedEncoded) == raw.trim.replaceAll("\\s+", " "))
      }

      assert(OpenSelectSupport.encodeToken("").isEmpty)
      assert(OpenSelectSupport.encodeToken("   ").isEmpty)
      assert(OpenSelectSupport.decodeToken("c%2b%2b") == "c++")
    }

    it("must partition closed and open tokens") {
      val itemValues = Set("apple", "orange")
      val (closed, open) = OpenSelectSupport.partitionTokens("apple Ice+cream orange Dark+chocolate", itemValues)
      assert(closed == List("apple", "orange"))
      assert(open   == List("Ice+cream", "Dark+chocolate"))
    }

    it("must validate individual tags and token lists according to validation-type, pattern, and max-tags") {
      assert(OpenSelectSupport.isValidTag("Alice Smith", "", ""))
      assert(OpenSelectSupport.isValidTag("alice@example.com", "email", ""))
      assert(! OpenSelectSupport.isValidTag("not-an-email", "email", ""))
      assert(OpenSelectSupport.isValidTag("https://www.orbeon.com/forms", "url", ""))
      assert(! OpenSelectSupport.isValidTag("ftp://invalid", "url", ""))
      assert(OpenSelectSupport.isValidTag("-42.5", "number", ""))
      assert(! OpenSelectSupport.isValidTag("42a", "number", ""))
      assert(OpenSelectSupport.isValidTag("INV-1024", "pattern", "INV-[0-9]{4}"))
      assert(! OpenSelectSupport.isValidTag("INV-99", "pattern", "INV-[0-9]{4}"))

      assert(OpenSelectSupport.isValidTags("alice@example.com bob@example.org", "email", "", "2"))
      assert(! OpenSelectSupport.isValidTags("alice@example.com bob@example.org charlie@example.net", "email", "", "2"))
      assert(! OpenSelectSupport.isValidTags("alice@example.com invalid-email", "email", "", "5"))
      assert(OpenSelectSupport.isValidTags("", "email", "", "2"))
    }
  }

  describe("fr:open-select control") {

    it("must synchronize closed checkboxes and space-escaped open tokens") {

      val xhtml =
        <xh:html
          xmlns:xf="http://www.w3.org/2002/xforms"
          xmlns:xh="http://www.w3.org/1999/xhtml"
          xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
          xmlns:xs="http://www.w3.org/2001/XMLSchema"
          xmlns:fr="http://orbeon.org/oxf/xml/form-runner">
          <xh:head>
            <xf:model xxf:function-library="org.orbeon.oxf.fr.library.FormRunnerFunctionLibrary">
              <xf:instance id="fr-form-instance">
                <form>
                  <fruits>apple Ice+cream C%2B%2B+dev</fruits>
                </form>
              </xf:instance>
            </xf:model>
          </xh:head>
          <xh:body>
            <fr:open-select id="fruits-control" ref="fruits" xxf:encrypt-item-values="false">
              <xf:label>Favorite Fruits</xf:label>
              <xf:item>
                <xf:label>Apple</xf:label>
                <xf:value>apple</xf:value>
              </xf:item>
              <xf:item>
                <xf:label>Orange</xf:label>
                <xf:value>orange</xf:value>
              </xf:item>
              <xf:item>
                <xf:label>Banana</xf:label>
                <xf:value>banana</xf:value>
              </xf:item>
            </fr:open-select>
          </xh:body>
        </xh:html>.toDocument

      withTestExternalContext { _ =>
        withXFormsDocument(xhtml) { doc =>
          withActionAndDoc(doc) {
            val component       = doc.getControlByEffectiveId("fruits-control").asInstanceOf[XFormsValueComponentControl]
            val selectControl   = doc.getControlByEffectiveId("fruits-control≡select").asInstanceOf[XFormsValueControl]
            val openTokensInput = doc.getControlByEffectiveId("fruits-control≡open-tokens-input").asInstanceOf[XFormsValueControl]

            // Initial state: 'apple' + two open tokens ('Ice+cream' and 'C%2B%2B+dev')
            assert(component.getValue(EventCollector.Throw)       == "apple Ice+cream C%2B%2B+dev")
            assert(selectControl.getValue(EventCollector.Throw)   == "apple")
            assert(openTokensInput.getValue(EventCollector.Throw) == "Ice+cream C%2B%2B+dev")

            // Check 'orange' as well
            setControlValueWithEventSearchNested(selectControl.effectiveId, "apple orange")
            assert(component.getValue(EventCollector.Throw) == "apple orange Ice+cream C%2B%2B+dev")

            // Update open tokens (e.g., user adds 'Dark+chocolate' and removes 'C%2B%2B+dev')
            setControlValueWithEventSearchNested(openTokensInput.effectiveId, "Ice+cream Dark+chocolate")
            assert(component.getValue(EventCollector.Throw) == "apple orange Ice+cream Dark+chocolate")

            // Clear all open tokens -> closed tokens remain
            setControlValueWithEventSearchNested(openTokensInput.effectiveId, "")
            assert(openTokensInput.getValue(EventCollector.Throw) == "")
            assert(component.getValue(EventCollector.Throw)       == "apple orange")

            // External update with new open token updates both closed selection and open tokens
            withAction {
              XFormsAPI.setvalue(component.boundNodeOpt.toList, "banana Green+tea")
            }
            assert(selectControl.getValue(EventCollector.Throw)   == "banana")
            assert(openTokensInput.getValue(EventCollector.Throw) == "Green+tea")
          }
        }
      }
    }
  }

  describe("fr:tags-input control") {

    it("must synchronize space-escaped tokens with the bound element") {

      val xhtml =
        <xh:html
          xmlns:xf="http://www.w3.org/2002/xforms"
          xmlns:xh="http://www.w3.org/1999/xhtml"
          xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
          xmlns:xs="http://www.w3.org/2001/XMLSchema"
          xmlns:fr="http://orbeon.org/oxf/xml/form-runner">
          <xh:head>
            <xf:model xxf:function-library="org.orbeon.oxf.fr.library.FormRunnerFunctionLibrary">
              <xf:instance id="fr-form-instance">
                <form>
                  <keywords>urgent Q4+review C%2B%2B</keywords>
                </form>
              </xf:instance>
            </xf:model>
          </xh:head>
          <xh:body>
            <fr:tags-input id="keywords-control" ref="keywords">
              <xf:label>Keywords</xf:label>
            </fr:tags-input>
          </xh:body>
        </xh:html>.toDocument

      withTestExternalContext { _ =>
        withXFormsDocument(xhtml) { doc =>
          withActionAndDoc(doc) {
            val component       = doc.getControlByEffectiveId("keywords-control").asInstanceOf[XFormsValueComponentControl]
            val openTokensInput = doc.getControlByEffectiveId("keywords-control≡open-tokens-input").asInstanceOf[XFormsValueControl]

            assert(component.getValue(EventCollector.Throw)       == "urgent Q4+review C%2B%2B")
            assert(openTokensInput.getValue(EventCollector.Throw) == "urgent Q4+review C%2B%2B")

            // Client-side companion updates open-tokens-input
            setControlValueWithEventSearchNested(openTokensInput.effectiveId, "urgent Q4+review 100%25+ready")
            assert(component.getValue(EventCollector.Throw) == "urgent Q4+review 100%25+ready")

            // External setvalue updates open-tokens-input
            withAction {
              XFormsAPI.setvalue(component.boundNodeOpt.toList, "alpha+release")
            }
            assert(component.getValue(EventCollector.Throw)       == "alpha+release")
            assert(openTokensInput.getValue(EventCollector.Throw) == "alpha+release")
          }
        }
      }
    }

    it("must enforce validation-type and max-tags constraints on fr:tags-input") {

      val xhtml =
        <xh:html
          xmlns:xf="http://www.w3.org/2002/xforms"
          xmlns:xh="http://www.w3.org/1999/xhtml"
          xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
          xmlns:xs="http://www.w3.org/2001/XMLSchema"
          xmlns:fr="http://orbeon.org/oxf/xml/form-runner">
          <xh:head>
            <xf:model
              id="fr-form-model"
              xxf:lang="en"
              xxf:xpath-analysis="true"
              xxf:function-library="org.orbeon.oxf.fr.library.FormRunnerFunctionLibrary">
              <xf:instance id="fr-form-instance">
                <form>
                  <emails>alice@example.com bob@example.org</emails>
                </form>
              </xf:instance>
              <xf:bind id="emails-bind" name="emails" ref="emails" type="fr:tags-input" xxf:functions="restricted"/>
            </xf:model>
          </xh:head>
          <xh:body>
            <fr:tags-input id="emails-control" bind="emails-bind" validation-type="email" max-tags="2">
              <xf:label>CC Emails</xf:label>
            </fr:tags-input>
          </xh:body>
        </xh:html>.toDocument

      withTestExternalContext { _ =>
        withXFormsDocument(xhtml) { doc =>
          withActionAndDoc(doc) {
            val component = doc.getControlByEffectiveId("emails-control").asInstanceOf[XFormsValueComponentControl]
            assert(isValid("emails-control"))

            // Invalid email tag
            withAction {
              XFormsAPI.setvalue(component.boundNodeOpt.toList, "alice@example.com bad-email")
            }
            assert(! isValid("emails-control"))

            // Valid emails but exceeds max-tags="2"
            withAction {
              XFormsAPI.setvalue(component.boundNodeOpt.toList, "a@example.com b@example.com c@example.com")
            }
            assert(! isValid("emails-control"))

            // Back to 2 valid emails
            withAction {
              XFormsAPI.setvalue(component.boundNodeOpt.toList, "a@example.com b@example.com")
            }
            assert(isValid("emails-control"))
          }
        }
      }
    }
  }
}
