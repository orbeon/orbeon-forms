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
import org.orbeon.oxf.xforms.analysis.model.ValidationLevel._
import org.orbeon.oxf.xforms.submission.XFormsModelSubmissionBase
import org.orbeon.oxf.xml.dom.Converter._
import org.orbeon.oxf.xml.dom.IOSupport
import org.orbeon.xforms.RelevanceHandling

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
      assert(Some(ErrorLevel) === numberControl.alertLevel)
      assert(DefaultAlertMessage === numberControl.getAlert)

      setControlValue(NumberControlId, "a")
      assert(Some(ErrorLevel) === numberControl.alertLevel)
      assert(DefaultAlertMessage === numberControl.getAlert)

      setControlValue(NumberControlId, "10")
      assert(Some(ErrorLevel) === numberControl.alertLevel)
      assert("Must be 50 or more" === numberControl.getAlert)

      setControlValue(NumberControlId, "50")
      assert(Some(WarningLevel) === numberControl.alertLevel)
      assert("Should be 100 or more" === numberControl.getAlert)

      setControlValue(NumberControlId, "1000")
      assert(None === numberControl.alertLevel)
      assert(null eq numberControl.getAlert)

      setControlValue(NumberControlId, "1001")
      assert(Some(InfoLevel) === numberControl.alertLevel)
      assert("Nice, greater than 1000!" == numberControl.getAlert)

      // text-control
      assert(None === textControl.alertLevel)
      assert(null eq textControl.getAlert)

      setControlValue(TextControlId, "This is…")
      assert(None === textControl.alertLevel)
      assert(null eq textControl.getAlert)

      setControlValue(TextControlId, "This is a little bit too long!")
      assert(Some(WarningLevel) === textControl.alertLevel)
      assert("Should be shorter than 10 characters" === textControl.getAlert)

      setControlValue(TextControlId, "this!")
      assert(Some(WarningLevel) === textControl.alertLevel)
      assert("Should not start with a lowercase letter" === textControl.getAlert)

      setControlValue(TextControlId, "this is a little bit too long and starts with a lowercase letter!")
      assert(Some(WarningLevel) === textControl.alertLevel)
      assert("<ul><li>Should be shorter than 10 characters</li><li>Should not start with a lowercase letter</li></ul>" === textControl.getAlert)
    }

  @Test def annotate(): Unit =
    withActionAndDoc(setupDocument(WarningsInfosTemplate)) {

      def copyFormInstance = {
        val formInstance = instance("fr-form-instance").get.underlyingDocumentOpt.get
        org.orbeon.dom.Document(formInstance.getRootElement.createCopy)
      }

      def copyAndAnnotate(tokens: Set[String]) =
        XFormsModelSubmissionBase.prepareXML(
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
