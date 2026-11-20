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
import org.orbeon.oxf.xforms.control.XFormsValueControl
import org.orbeon.oxf.xforms.event.EventCollector
import org.orbeon.oxf.xml.dom.Converter.*
import org.scalatest.funspec.AnyFunSpecLike


class MarkdownControlTest
  extends DocumentTestBase
     with ResourceManagerSupport
     with AnyFunSpecLike
     with XFormsSupport {

  describe("fr:markdown / xf:textarea[mediatype='text/markdown'] XBL control") {

    it("must toggle between Write and Preview modes and render sanitized HTML") {
      val xhtml =
        <xh:html
          xmlns:xh="http://www.w3.org/1999/xhtml"
          xmlns:xf="http://www.w3.org/2002/xforms"
          xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
          xmlns:fr="http://orbeon.org/oxf/xml/form-runner">
          <xh:head>
            <xf:model>
              <xf:instance id="main">
                <form>
                  <notes>**Bold** and ~~old~~</notes>
                  <readonly-notes>## Heading&#10;&#10;- [x] Done</readonly-notes>
                </form>
              </xf:instance>
            </xf:model>
          </xh:head>
          <xh:body>
            <xf:textarea id="notes-control" mediatype="text/markdown" ref="notes">
              <xf:label>Notes</xf:label>
            </xf:textarea>
            <xf:textarea id="readonly-notes-control" mediatype="text/markdown" fr:static-readonly="true" ref="readonly-notes">
              <xf:label>Readonly Notes</xf:label>
            </xf:textarea>
          </xh:body>
        </xh:html>

      withTestExternalContext { implicit ec =>
        withXFormsDocument(xhtml.toDocument) { doc =>
          withActionAndDoc(doc) {
            // Starts in write mode: textarea is relevant
            val textareaControl = doc.getControlByEffectiveId("notes-control≡textarea").asInstanceOf[XFormsValueControl]
            assert(textareaControl.isRelevant)
            assert(textareaControl.getValue(EventCollector.Throw) == "**Bold** and ~~old~~")

            // Switch to preview tab
            activateControlWithEvent("notes-control≡preview-tab")
            val previewOutput = doc.getControlByEffectiveId("notes-control≡preview-output").asInstanceOf[XFormsValueControl]
            assert(previewOutput.isRelevant)
            val previewHtml = previewOutput.getValue(EventCollector.Throw)
            assert(previewHtml.contains("<strong>Bold</strong>"))
            assert(previewHtml.contains("<del>old</del>"))

            // Switch back to write tab
            activateControlWithEvent("notes-control≡write-tab")
            assert(doc.getControlByEffectiveId("notes-control≡textarea").asInstanceOf[XFormsValueControl].isRelevant)

            // Static readonly binding renders sanitized HTML directly
            val staticOutput = doc.getControlByEffectiveId("readonly-notes-control≡output").asInstanceOf[XFormsValueControl]
            assert(staticOutput.isRelevant)
            val staticHtml = staticOutput.getValue(EventCollector.Throw)
            assert(staticHtml.contains("<h2>Heading</h2>"))
            assert(staticHtml.contains("fr-markdown-task-checked"))
          }
        }
      }
    }
  }
}
