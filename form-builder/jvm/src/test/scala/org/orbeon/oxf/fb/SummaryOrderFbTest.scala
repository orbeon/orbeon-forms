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
package org.orbeon.oxf.fb

import org.orbeon.builder.rpc.*
import org.orbeon.oxf.fb.FormBuilder.*
import org.orbeon.oxf.fr.FormRunnerSummaryOrder
import org.orbeon.oxf.fr.FormRunnerSummaryOrder.SummaryControlColumn
import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport}
import org.orbeon.scaxon.SimplePath.*
import org.scalatest.funspec.AnyFunSpecLike

import scala.concurrent.Await
import scala.concurrent.duration.*


class SummaryOrderFbTest
  extends DocumentTestBase
     with ResourceManagerSupport
     with AnyFunSpecLike
     with FormBuilderSupport {

  private val SectionTemplatesDoc = "oxf:/org/orbeon/oxf/fb/template-with-section-templates.xhtml"

  describe("Form Builder Summary control column ordering, position info, rename/undo, and MCP settings") {

    it("must compute default and custom orders, position indicators, and omit metadata when reset to form order") {
      withActionAndFBDoc(SectionTemplatesDoc) { implicit ctx =>

        // Enable Summary on two top-level controls (`control-1`, `control-2` in `section-0`)
        FormBuilderXPathApi.setControlSummaryShow("control-1", enabled = true)
        FormBuilderXPathApi.setControlSummaryShow("control-2", enabled = true)

        // Default order: `control-1` (pos 1), `control-2` (pos 2)
        val initialXml  = SummaryOrderOps.buildSummaryControlColumnsXml
        val initialCols = (initialXml / "column").toList
        assert(initialCols.map(c => (c.attValue("control"), c.attValue("default-pos"))) == List(
          ("control-1", "1"),
          ("control-2", "2")
        ))

        val pos1 = SummaryOrderOps.controlSummaryPositionInfo("control-1")
        assert(pos1.attValue("initially-enabled") == "true")
        assert(pos1.attValue("active-pos")        == "1")
        assert(pos1.attValue("other-count")       == "1")

        val pos2 = SummaryOrderOps.controlSummaryPositionInfo("control-2")
        assert(pos2.attValue("initially-enabled") == "true")
        assert(pos2.attValue("active-pos")        == "2")
        assert(pos2.attValue("other-count")       == "1")

        // Set custom order `[control-2, control-1]` via Form Builder MCP
        Await.result(
          FormBuilderMcpRpcApiImpl.formSetSettings(
            "",
            FormSettingsUpdateR(
              summary = Some(
                FormSettingsSummaryUpdateR(
                  controlColumns = Some(List(
                    SummaryControlColumnR(control = "control-2"),
                    SummaryControlColumnR(control = "control-1")
                  ))
                )
              )
            )
          ),
          5.seconds
        )

        assert(
          FormRunnerSummaryOrder.readSummaryControlColumns(ctx.metadataRootElemOpt) == List(
            SummaryControlColumn("control-2", None),
            SummaryControlColumn("control-1", None)
          )
        )

        // MCP `formGetSettings` and `buildSummaryControlColumnsXml` reflect the reversed order while preserving `@default-pos`
        val settingsAfterReorder = Await.result(FormBuilderMcpRpcApiImpl.formGetSettings(""), 5.seconds)
        assert(settingsAfterReorder.summary.controlColumns == List(
          SummaryControlColumnR(control = "control-2", sectionName = None),
          SummaryControlColumnR(control = "control-1", sectionName = None)
        ))

        val reorderedCols = (SummaryOrderOps.buildSummaryControlColumnsXml / "column").toList
        assert(reorderedCols.map(c => (c.attValue("control"), c.attValue("default-pos"))) == List(
          ("control-2", "2"),
          ("control-1", "1")
        ))

        val pos1After = SummaryOrderOps.controlSummaryPositionInfo("control-1")
        assert(pos1After.attValue("active-pos") == "2")

        // Resetting back to `[control-1, control-2]` (or `Nil`) removes `<summary-control-columns>` from metadata
        Await.result(
          FormBuilderMcpRpcApiImpl.formSetSettings(
            "",
            FormSettingsUpdateR(
              summary = Some(
                FormSettingsSummaryUpdateR(
                  controlColumns = Some(List(
                    SummaryControlColumnR(control = "control-1"),
                    SummaryControlColumnR(control = "control-2")
                  ))
                )
              )
            )
          ),
          5.seconds
        )

        assert((ctx.metadataRootElemOpt.toList / FormRunnerSummaryOrder.SummaryControlColumnsElemName).isEmpty)
      }
    }

    it("must update `<summary-control-columns>` on control and section-template host rename and restore on Undo") {
      withActionAndFBDoc(SectionTemplatesDoc) { implicit ctx =>

        FormBuilderXPathApi.setControlSummaryShow("control-1", enabled = true)
        FormBuilderXPathApi.setControlSummaryShow("control-2", enabled = true)

        // Include both a top-level control and a section-template control (`section-1` / `control-11`) in metadata
        Await.result(
          FormBuilderMcpRpcApiImpl.formSetSettings(
            "",
            FormSettingsUpdateR(
              summary = Some(
                FormSettingsSummaryUpdateR(
                  controlColumns = Some(List(
                    SummaryControlColumnR(control = "control-2"),
                    SummaryControlColumnR(control = "control-11", sectionName = Some("section-1")),
                    SummaryControlColumnR(control = "control-1")
                  ))
                )
              )
            )
          ),
          5.seconds
        )

        // Note: `control-11` in `section-1` wasn't statically marked `summary-show` in the template, so manually write
        // a `<column section-name="section-1" control="control-11"/>` entry into metadata to test rename of both kinds
        Await.result(
          FormBuilderMcpRpcApiImpl.formSetSettings(
            "",
            FormSettingsUpdateR(
              summary = Some(FormSettingsSummaryUpdateR(controlColumns = Some(Nil)))
            )
          ),
          5.seconds
        )

        import org.orbeon.oxf.xforms.action.XFormsAPI.insert
        import org.orbeon.scaxon.NodeConversions.elemToNodeInfo
        val customElem: org.orbeon.saxon.om.NodeInfo =
          <summary-control-columns>
            <column control="control-2"/>
            <column section-name="section-1" control="control-2"/>
            <column control="control-1"/>
          </summary-control-columns>
        insert(into = ctx.metadataRootElemOpt.toList, origin = List(customElem))

        // 1. Rename top-level `control-2` -> `renamed-two`: updates top-level `@control`, leaves section-template `@control="control-2"` untouched
        FormBuilderXPathApi.renameControlIfNeeded("control-2", "renamed-two", addToUndoStack = true)
        assert(
          FormRunnerSummaryOrder.readSummaryControlColumns(ctx.metadataRootElemOpt) == List(
            SummaryControlColumn("renamed-two", None),
            SummaryControlColumn("control-2",   Some("section-1")),
            SummaryControlColumn("control-1",   None)
          )
        )

        // 2. Rename section-template host `section-1` -> `renamed-sec`: updates `@section-name`
        FormBuilderXPathApi.renameControlIfNeeded("section-1", "renamed-sec", addToUndoStack = true)
        assert(
          FormRunnerSummaryOrder.readSummaryControlColumns(ctx.metadataRootElemOpt) == List(
            SummaryControlColumn("renamed-two", None),
            SummaryControlColumn("control-2",   Some("renamed-sec")),
            SummaryControlColumn("control-1",   None)
          )
        )

        // 3. Simulate Undo (reverse renames) and verify exact restoration
        renameControlIfNeeded("renamed-sec", "section-1")
        renameControlIfNeeded("renamed-two", "control-2")
        assert(
          FormRunnerSummaryOrder.readSummaryControlColumns(ctx.metadataRootElemOpt) == List(
            SummaryControlColumn("control-2", None),
            SummaryControlColumn("control-2", Some("section-1")),
            SummaryControlColumn("control-1", None)
          )
        )
      }
    }
  }
}
