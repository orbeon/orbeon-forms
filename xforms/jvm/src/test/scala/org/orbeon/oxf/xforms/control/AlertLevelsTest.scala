/**
 * Copyright (C) 2013 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.control

import org.junit.Test
import org.orbeon.oxf.test.{DocumentTestBase, XFormsSupport}
import org.orbeon.oxf.xforms.event.EventCollector
import org.orbeon.oxf.xforms.submission.XFormsModelSubmissionSupport
import org.orbeon.oxf.xml.dom.Converter.*
import org.orbeon.xforms.RelevanceHandling
import org.orbeon.xforms.analysis.model.ValidationLevel

class AlertLevelsTest extends DocumentTestBase with XFormsSupport {

  val WarningsInfosTemplate = "oxf:/org/orbeon/oxf/xforms/forms/form-with-alert-levels.xhtml"

  val NumberControlId     = "number-control"
  val TextControlId       = "text-control"
  val DefaultAlertMessage = "Missing or incorrect value"

  def numberControl = getSingleNodeControl(NumberControlId)
  def textControl   = getSingleNodeControl(TextControlId)

  @Test def alertLevels(): Unit =
    withActionAndDoc(setupDocument(WarningsInfosTemplate)) {

      // number-control
      assert(numberControl.alertLevel.contains(ValidationLevel.ErrorLevel))
      assert(numberControl.getAlert(EventCollector.Throw).contains(DefaultAlertMessage))

      setControlValue(NumberControlId, "a")
      assert(numberControl.alertLevel.contains(ValidationLevel.ErrorLevel))
      assert(numberControl.getAlert(EventCollector.Throw).contains(DefaultAlertMessage))

      setControlValue(NumberControlId, "10")
      assert(numberControl.alertLevel.contains(ValidationLevel.ErrorLevel))
      assert(numberControl.getAlert(EventCollector.Throw).contains("Must be 50 or more"))

      setControlValue(NumberControlId, "50")
      assert(numberControl.alertLevel.contains(ValidationLevel.WarningLevel))
      assert(numberControl.getAlert(EventCollector.Throw).contains("Should be 100 or more"))

      setControlValue(NumberControlId, "1000")
      assert(numberControl.alertLevel.isEmpty)
      assert(numberControl.getAlert(EventCollector.Throw).isEmpty)

      setControlValue(NumberControlId, "1001")
      assert(numberControl.alertLevel.contains(ValidationLevel.InfoLevel))
      assert(numberControl.getAlert(EventCollector.Throw).contains("Nice, greater than 1000!"))

      // text-control
      assert(textControl.alertLevel.isEmpty)
      assert(textControl.getAlert(EventCollector.Throw).isEmpty)

      setControlValue(TextControlId, "This is…")
      assert(textControl.alertLevel.isEmpty)
      assert(textControl.getAlert(EventCollector.Throw).isEmpty)

      setControlValue(TextControlId, "This is a little bit too long!")
      assert(textControl.alertLevel.contains(ValidationLevel.WarningLevel))
      assert(textControl.getAlert(EventCollector.Throw).contains("Should be shorter than 10 characters"))

      setControlValue(TextControlId, "this!")
      assert(textControl.alertLevel.contains(ValidationLevel.WarningLevel))
      assert(textControl.getAlert(EventCollector.Throw).contains("Should not start with a lowercase letter"))

      setControlValue(TextControlId, "this is a little bit too long and starts with a lowercase letter!")
      assert(textControl.alertLevel.contains(ValidationLevel.WarningLevel))
      assert(textControl.getAlert(EventCollector.Throw).contains("<ul><li>Should be shorter than 10 characters</li><li>Should not start with a lowercase letter</li></ul>"))
    }

  @Test def annotate(): Unit =
    withActionAndDoc(setupDocument(WarningsInfosTemplate)) {

      def copyFormInstance = {
        val formInstance = instance("fr-form-instance").get.underlyingDocumentOpt.get
        org.orbeon.dom.Document(formInstance.getRootElement.createCopy)
      }

      def copyAndAnnotate(tokens: Set[String]) =
        XFormsModelSubmissionSupport.prepareXML(
          xfcd              = document,
          ref               = instance("fr-form-instance").get.root,
          relevanceHandling = RelevanceHandling.Keep,
          namespaceContext  = Map.empty,
          annotateWith      = tokens,
          relevantAttOpt    = None
        )

      // Cause warnings and info
      setControlValue(NumberControlId, "1001")
      setControlValue(TextControlId, "this is a little bit too long and starts with a lowercase letter!")

      // No annotation
      assertXMLDocumentsIgnoreNamespacesInScope(copyFormInstance, copyAndAnnotate(Set.empty))

      locally {
        val expected =
          <form xmlns:xxf="http://orbeon.org/oxf/xml/xforms">
            <my-section>
              <number>1001</number>
              <text xxf:warning="&lt;ul&gt;&lt;li&gt;Should be shorter than 10 characters&lt;/li&gt;&lt;li&gt;Should not start with a lowercase letter&lt;/li&gt;&lt;/ul&gt;">this is a little bit too long and starts with a lowercase letter!</text>
            </my-section>
          </form>.toDocument

        assertXMLDocumentsIgnoreNamespacesInScope(expected, copyAndAnnotate(Set("warning")))
      }

      locally {
        val expected =
          <form xmlns:xxf="http://orbeon.org/oxf/xml/xforms">
            <my-section>
              <number xxf:info="Nice, greater than 1000!">1001</number>
              <text>this is a little bit too long and starts with a lowercase letter!</text>
            </my-section>
          </form>.toDocument

        assertXMLDocumentsIgnoreNamespacesInScope(expected, copyAndAnnotate(Set("info")))
      }

      locally {
        setControlValue(TextControlId, "This is a little bit too long!")

        val expected =
          <form xmlns:xxf="http://orbeon.org/oxf/xml/xforms">
            <my-section>
              <number xxf:info="Nice, greater than 1000!">1001</number>
              <text xxf:warning="Should be shorter than 10 characters">This is a little bit too long!</text>
            </my-section>
          </form>.toDocument

        assertXMLDocumentsIgnoreNamespacesInScope(expected, copyAndAnnotate(Set("warning", "info")))
      }
    }
}
