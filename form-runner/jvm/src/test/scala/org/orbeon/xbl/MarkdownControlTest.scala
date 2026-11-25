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
            <xf:model xxf:function-library="org.orbeon.oxf.fr.library.FormRunnerFunctionLibrary">
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

    it("must respect the initial-mode custom setting in editable modes") {
      val xhtml =
        <xh:html
          xmlns:xh="http://www.w3.org/1999/xhtml"
          xmlns:xf="http://www.w3.org/2002/xforms"
          xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
          xmlns:fr="http://orbeon.org/oxf/xml/form-runner">
          <xh:head>
            <xf:model xxf:function-library="org.orbeon.oxf.fr.library.FormRunnerFunctionLibrary">
              <xf:instance id="main">
                <form>
                  <preview-notes>**Preview** first</preview-notes>
                  <fr-preview-notes>*FR element* preview</fr-preview-notes>
                  <write-notes>Write first</write-notes>
                  <invalid-notes>Fallback to write</invalid-notes>
                  <readonly-write-notes>**Readonly** always previews</readonly-write-notes>
                </form>
              </xf:instance>
              <xf:bind ref="readonly-write-notes" readonly="true()"/>
            </xf:model>
          </xh:head>
          <xh:body>
            <xf:textarea id="preview-control" mediatype="text/markdown" initial-mode="preview" ref="preview-notes">
              <xf:label>Preview Notes</xf:label>
            </xf:textarea>
            <fr:markdown id="fr-preview-control" initial-mode="preview" ref="fr-preview-notes">
              <xf:label>FR Preview Notes</xf:label>
            </fr:markdown>
            <xf:textarea id="write-control" mediatype="text/markdown" initial-mode="write" ref="write-notes">
              <xf:label>Write Notes</xf:label>
            </xf:textarea>
            <xf:textarea id="invalid-control" mediatype="text/markdown" initial-mode="unknown" ref="invalid-notes">
              <xf:label>Invalid Mode Notes</xf:label>
            </xf:textarea>
            <xf:textarea id="readonly-write-control" mediatype="text/markdown" initial-mode="write" ref="readonly-write-notes">
              <xf:label>Readonly Notes</xf:label>
            </xf:textarea>
          </xh:body>
        </xh:html>

      withTestExternalContext { implicit ec =>
        withXFormsDocument(xhtml.toDocument) { doc =>
          withActionAndDoc(doc) {
            // initial-mode="preview" on xf:textarea[mediatype='text/markdown'] starts in Preview mode
            assert(! doc.getControlByEffectiveId("preview-control≡textarea").asInstanceOf[XFormsValueControl].isRelevant)
            val previewOutput = doc.getControlByEffectiveId("preview-control≡preview-output").asInstanceOf[XFormsValueControl]
            assert(previewOutput.isRelevant)
            assert(previewOutput.getValue(EventCollector.Throw).contains("<strong>Preview</strong>"))

            // Can still switch to Write tab and back to Preview tab
            activateControlWithEvent("preview-control≡write-tab")
            assert(doc.getControlByEffectiveId("preview-control≡textarea").asInstanceOf[XFormsValueControl].isRelevant)
            assert(! doc.getControlByEffectiveId("preview-control≡preview-output").asInstanceOf[XFormsValueControl].isRelevant)

            activateControlWithEvent("preview-control≡preview-tab")
            assert(! doc.getControlByEffectiveId("preview-control≡textarea").asInstanceOf[XFormsValueControl].isRelevant)
            assert(doc.getControlByEffectiveId("preview-control≡preview-output").asInstanceOf[XFormsValueControl].isRelevant)

            // initial-mode="preview" on fr:markdown starts in Preview mode
            assert(! doc.getControlByEffectiveId("fr-preview-control≡textarea").asInstanceOf[XFormsValueControl].isRelevant)
            val frPreviewOutput = doc.getControlByEffectiveId("fr-preview-control≡preview-output").asInstanceOf[XFormsValueControl]
            assert(frPreviewOutput.isRelevant)
            assert(frPreviewOutput.getValue(EventCollector.Throw).contains("<em>FR element</em>"))

            // initial-mode="write" starts in Write mode
            assert(doc.getControlByEffectiveId("write-control≡textarea").asInstanceOf[XFormsValueControl].isRelevant)
            assert(! doc.getControlByEffectiveId("write-control≡preview-output").asInstanceOf[XFormsValueControl].isRelevant)

            // Unknown initial-mode falls back to Write mode
            assert(doc.getControlByEffectiveId("invalid-control≡textarea").asInstanceOf[XFormsValueControl].isRelevant)
            assert(! doc.getControlByEffectiveId("invalid-control≡preview-output").asInstanceOf[XFormsValueControl].isRelevant)

            // Readonly binding shows Preview even when initial-mode="write"
            assert(! doc.getControlByEffectiveId("readonly-write-control≡textarea").asInstanceOf[XFormsValueControl].isRelevant)
            val roOutput = doc.getControlByEffectiveId("readonly-write-control≡preview-output").asInstanceOf[XFormsValueControl]
            assert(roOutput.isRelevant)
            assert(roOutput.getValue(EventCollector.Throw).contains("<strong>Readonly</strong>"))
          }
        }
      }
    }

    it("must respect form-level metadata default for initial-mode and allow per-control override") {
      val xhtml =
        <xh:html
          xmlns:xh="http://www.w3.org/1999/xhtml"
          xmlns:xf="http://www.w3.org/2002/xforms"
          xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
          xmlns:fr="http://orbeon.org/oxf/xml/form-runner">
          <xh:head>
            <xf:model id="fr-form-model" xxf:function-library="org.orbeon.oxf.fr.library.FormRunnerFunctionLibrary">
              <xf:instance id="fr-form-instance">
                <form>
                  <default-notes>**Inherited** preview</default-notes>
                  <override-notes>Explicit write</override-notes>
                </form>
              </xf:instance>
              <xf:instance id="fr-form-metadata" xxf:readonly="true">
                <metadata>
                  <xbl>
                    <fr:markdown initial-mode="preview"/>
                  </xbl>
                </metadata>
              </xf:instance>
            </xf:model>
          </xh:head>
          <xh:body>
            <xf:textarea id="default-control" mediatype="text/markdown" ref="default-notes">
              <xf:label>Default Notes</xf:label>
            </xf:textarea>
            <xf:textarea id="override-control" mediatype="text/markdown" initial-mode="write" ref="override-notes">
              <xf:label>Override Notes</xf:label>
            </xf:textarea>
          </xh:body>
        </xh:html>

      withTestExternalContext { implicit ec =>
        withXFormsDocument(xhtml.toDocument) { doc =>
          withActionAndDoc(doc) {
            // Inherits initial-mode="preview" from form metadata
            assert(! doc.getControlByEffectiveId("default-control≡textarea").asInstanceOf[XFormsValueControl].isRelevant)
            val defaultPreview = doc.getControlByEffectiveId("default-control≡preview-output").asInstanceOf[XFormsValueControl]
            assert(defaultPreview.isRelevant)
            assert(defaultPreview.getValue(EventCollector.Throw).contains("<strong>Inherited</strong>"))

            // Control-level initial-mode="write" overrides form metadata
            assert(doc.getControlByEffectiveId("override-control≡textarea").asInstanceOf[XFormsValueControl].isRelevant)
            assert(! doc.getControlByEffectiveId("override-control≡preview-output").asInstanceOf[XFormsValueControl].isRelevant)
          }
        }
      }
    }
  }
}
