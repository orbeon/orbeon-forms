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

import cats.syntax.option.*
import org.orbeon.builder.rpc.{ControlPosition as McpControlPosition, ControlSettingsUpdateR, FormBuilderMcpRpcApiImpl, FormBuilderRpcApiImpl}
import org.orbeon.oxf.fb.FormBuilder.*
import org.orbeon.oxf.fr.FormRunner.*
import org.orbeon.oxf.fr.NodeInfoCell.{CellTest, NodeInfoCellOps}
import org.orbeon.oxf.fr.{AppForm, ControlOps as FRControlOps, FormRunner, FormRunnerParams}
import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport}
import org.orbeon.scaxon.NodeConversions.*
import org.orbeon.scaxon.SimplePath.*
import org.scalatest.funspec.AnyFunSpecLike

import scala.concurrent.Await
import scala.concurrent.duration.Duration


class SeparateLhhaTest
  extends DocumentTestBase
     with ResourceManagerSupport
     with AnyFunSpecLike
     with FormBuilderSupport {

  private val SectionsGridsRepeatsDoc = "oxf:/org/orbeon/oxf/fb/template-with-sections-grids-repeats.xhtml"
  private val SectionsRepeatsDoc      = "oxf:/org/orbeon/oxf/fb/template-with-sections-repeats.xhtml"

  describe("Separate cell control labels (#2865)") {

    it("splits a control cell proportionally when moving label to left and merges back on move to top, with Undo/Redo") {
      withActionAndFBDoc(SectionsRepeatsDoc) { implicit ctx =>
        implicit val formRunnerParams: FormRunnerParams =
          FormRunnerParams(AppForm.FormBuilder.app, AppForm.FormBuilder.form, 1.some, None, None, "new")

        val control1 = FormRunner.findControlByName("control-1").get
        val initW    = NodeInfoCellOps.w(control1.parentUnsafe).getOrElse(12)
        val initX    = NodeInfoCellOps.x(control1.parentUnsafe).getOrElse(1)
        assert(initW >= 6)
        assert(! FormBuilder.hasSeparateLabel(control1))
        assert(FormBuilder.canMoveLabelLeft(control1))

        val expectedLabelW = FormBuilder.defaultSeparateLabelWidth(initW, 12)

        FormBuilder.moveControlLabelToLeft(control1).foreach(Undo.pushUserUndoAction)

        assert(FormBuilder.hasSeparateLabel(control1))
        assert((control1 / "*:label").isEmpty)

        val sepLabel = FormRunner.findSeparateLabelForControl(control1).get
        assert(sepLabel.id == FRControlOps.labelId("control-1"))
        assert(sepLabel.attValue("for") == "control-1-control")
        assert(NodeInfoCellOps.x(sepLabel.parentUnsafe).contains(initX))
        assert(NodeInfoCellOps.w(sepLabel.parentUnsafe).contains(expectedLabelW))
        assert(NodeInfoCellOps.x(control1.parentUnsafe).contains(initX + expectedLabelW))
        assert(NodeInfoCellOps.w(control1.parentUnsafe).contains(initW - expectedLabelW))

        // Undo restores top label and merges back to original cell width
        FormBuilderXPathApi.undoActionImpl()
        val c1AfterUndo = FormRunner.findControlByName("control-1").get
        assert(! FormBuilder.hasSeparateLabel(c1AfterUndo))
        assert((c1AfterUndo / "*:label").nonEmpty)
        assert(NodeInfoCellOps.x(c1AfterUndo.parentUnsafe).contains(initX))
        assert(NodeInfoCellOps.w(c1AfterUndo.parentUnsafe).contains(initW))

        // Redo re-splits and moves label left again
        FormBuilderXPathApi.redoActionImpl()
        val c1AfterRedo = FormRunner.findControlByName("control-1").get
        assert(FormBuilder.hasSeparateLabel(c1AfterRedo))
        assert(NodeInfoCellOps.w(c1AfterRedo.parentUnsafe).contains(initW - expectedLabelW))

        // Explicit move back to top merges the adjacent left cell back
        FormBuilder.moveControlLabelToTop(c1AfterRedo).foreach(Undo.pushUserUndoAction)
        assert(! FormBuilder.hasSeparateLabel(c1AfterRedo))
        assert(NodeInfoCellOps.x(c1AfterRedo.parentUnsafe).contains(initX))
        assert(NodeInfoCellOps.w(c1AfterRedo.parentUnsafe).contains(initW))
      }
    }

    it("uses an existing empty matching left cell without splitting") {
      withActionAndFBDoc(SectionsGridsRepeatsDoc) { implicit ctx =>
        // Find a grid with at least 2 cells on row 1, put a control in the second cell while keeping the first empty
        val gridElem = findNestedContainers(ctx.bodyElem).find(c => IsGrid(c) && ! FormRunner.isRepeat(c)).get
        val firstCell = (gridElem / CellTest).head
        FormBuilder.deleteControlWithinCell(firstCell)
        FormBuilder.split(firstCell, org.orbeon.datatypes.Direction.Left, Some(4))

        val cellsRow1 = (gridElem / CellTest).filter(c => NodeInfoCellOps.y(c).contains(1)).toList
        val leftEmpty = cellsRow1(0)
        val rightCell = cellsRow1(1)

        // Move an existing control into `rightCell` or insert a control
        val existingCtrl = findNestedControls(ctx.bodyElem).head
        implicit val formRunnerParams: FormRunnerParams =
          FormRunnerParams(AppForm.FormBuilder.app, AppForm.FormBuilder.form, 1.some, None, None, "new")
        ToolboxOps.dndControl(existingCtrl.parentUnsafe, rightCell, copy = true)

        val rightCtrl = (rightCell / *).head
        val rightWBefore = NodeInfoCellOps.w(rightCell).get
        assert(! NodeInfoCellOps.hasChildElement(leftEmpty))

        FormBuilder.moveControlLabelToLeft(rightCtrl).foreach(Undo.pushUserUndoAction)
        assert(FormBuilder.hasSeparateLabel(rightCtrl))
        assert(NodeInfoCellOps.hasChildElement(leftEmpty))
        assert(NodeInfoCellOps.w(rightCell).contains(rightWBefore))

        // Undo leaves `leftEmpty` as an empty cell rather than merging it away!
        FormBuilderXPathApi.undoActionImpl()
        assert(! FormBuilder.hasSeparateLabel(rightCtrl))
        assert(! NodeInfoCellOps.hasChildElement(leftEmpty))
        assert(NodeInfoCellOps.w(rightCell).contains(rightWBefore))
      }
    }

    it("renames separate label id, @for, and @ref and preserves HTML mediatype when control is renamed") {
      withActionAndFBDoc(SectionsRepeatsDoc) { implicit ctx =>
        val control1 = FormRunner.findControlByName("control-1").get
        FormBuilder.setControlLabelHintHelpOrText("control-1", "label", "<b>First</b>", None, isHTML = true)
        FormBuilder.moveControlLabelToLeft(control1)

        assert(FormBuilder.isControlLhhatHtmlMediatype("control-1", "label"))

        FormBuilder.renameControlIfNeeded("control-1", "first-name")

        val renamedControl = FormRunner.findControlByName("first-name").get
        val sepLabel       = FormRunner.findSeparateLabelForControl(renamedControl).get
        assert(sepLabel.id == "first-name-label")
        assert(sepLabel.attValue("for") == "first-name-control")
        assert(sepLabel.attValue("ref") == "$form-resources/first-name/label")
        assert(FormBuilder.isControlLhhatHtmlMediatype("first-name", "label"))
      }
    }

    it("handles Delete on separate label (moves back to top), Delete/Cut/Copy/Paste on control, and Drag-and-Drop") {
      withActionAndFBDoc(SectionsRepeatsDoc) { implicit ctx =>
        implicit val formRunnerParams: FormRunnerParams =
          FormRunnerParams(AppForm.FormBuilder.app, AppForm.FormBuilder.form, 1.some, None, None, "new")

        val control1 = FormRunner.findControlByName("control-1").get
        FormBuilder.moveControlLabelToLeft(control1)
        val sepLabel1 = FormRunner.findSeparateLabelForControl(control1).get

        // Deleting the separate label cell moves the label back onto top of `control-1`
        FormBuilder.deleteControlWithinCell(sepLabel1.parentUnsafe)
        assert(! FormBuilder.hasSeparateLabel(control1))
        assert((control1 / "*:label").nonEmpty)

        // Move back to left, Copy, and Paste into a new row -> pasted control gets its own separate label!
        FormBuilder.moveControlLabelToLeft(control1)
        ToolboxOps.copyToClipboard(control1.parentUnsafe)
        FormBuilder.selectCell(control1.parentUnsafe)
        ToolboxOps.pasteFromClipboardImpl()

        val allControls = findNestedControls(ctx.bodyElem).filterNot(FormRunner.isSeparateLhhaElem)
        val pastedCtrl  = allControls.find(c => FormRunner.getControlName(c) != "control-1" && FormBuilder.hasSeparateLabel(c))
        assert(pastedCtrl.isDefined)

        // Dragging the separate label to an empty cell in the same grid keeps the control in place
        val gridElem = FormBuilder.getContainingGrid(control1.parentUnsafe)
        val (newRowCell, _) = FormBuilder.rowInsertBelow(gridElem, 0)
        val currentSepLabel = FormRunner.findSeparateLabelForControl(control1).get
        val ctrlXBefore     = NodeInfoCellOps.x(control1.parentUnsafe)
        val ctrlYBefore     = NodeInfoCellOps.y(control1.parentUnsafe)

        ToolboxOps.dndControl(currentSepLabel.parentUnsafe, newRowCell, copy = false).foreach(Undo.pushUserUndoAction)
        assert(NodeInfoCellOps.x(control1.parentUnsafe) == ctrlXBefore)
        assert(NodeInfoCellOps.y(control1.parentUnsafe) == ctrlYBefore)
        assert(FormRunner.findSeparateLabelForControl(control1).get.parentUnsafe == newRowCell)

        // Undo DnD of separate label
        FormBuilderXPathApi.undoActionImpl()
        assert(FormRunner.findSeparateLabelForControl(control1).get.parentUnsafe != newRowCell)

        // Deleting the control also deletes its separate label (no dangling `@for`)
        FormBuilder.deleteControlWithinCell(control1.parentUnsafe, updateTemplates = true).foreach(Undo.pushUserUndoAction)
        assert(FormRunner.findControlByName("control-1").isEmpty)
        assert((ctx.bodyElem descendant "*:label").forall(! _.attValueOpt("for").contains("control-1-control")))

        // Undo delete restores both control and separate label
        FormBuilderXPathApi.undoActionImpl()
        val restored = FormRunner.findControlByName("control-1").get
        assert(FormBuilder.hasSeparateLabel(restored))
      }
    }

    it("supports grid-wide moveGridLabelsToLeft / moveGridLabelsToTop and MCP labelPosition / gridSetLabelsPosition") {
      withActionAndFBDoc(SectionsRepeatsDoc) { implicit ctx =>
        val control1 = FormRunner.findControlByName("control-1").get
        val gridElem = FormBuilder.getContainingGrid(control1.parentUnsafe)
        val gridName = FormRunner.getControlName(gridElem)

        // MCP `gridSetLabelsPosition("left")`
        Await.result(FormBuilderMcpRpcApiImpl.gridSetLabelsPosition("", gridName, "left"), Duration.Inf)
        assert(FormBuilder.hasSeparateLabel(FormRunner.findControlByName("control-1").get))

        // MCP `formGetStructure` reports `separateLabelPosition` and does not emit bogus label controls
        val structure = Await.result(FormBuilderMcpRpcApiImpl.formGetStructure(""), Duration.Inf)
        val mcpGrid   = structure.sections.flatMap(_.grids).find(_.name == gridName).get
        assert(mcpGrid.controls.forall(_.controlType.localName != "label"))
        val mcpCtrl1  = mcpGrid.controls.find(_.name == "control-1").get
        assert(mcpCtrl1.separateLabelPosition.isDefined)

        // MCP `controlGetSettings` reports `labelPosition = Some("separate")`
        val settings1 = Await.result(FormBuilderMcpRpcApiImpl.controlGetSettings("", "control-1"), Duration.Inf)
        assert(settings1.labelPosition.contains("separate"))
        assert(settings1.separateLabelPosition.isDefined)

        // MCP `controlSetSettings` with `labelPosition = Some("top")` moves label back above control
        Await.result(
          FormBuilderMcpRpcApiImpl.controlSetSettings("", "control-1", ControlSettingsUpdateR(labelPosition = Some("top"))),
          Duration.Inf
        )
        assert(! FormBuilder.hasSeparateLabel(FormRunner.findControlByName("control-1").get))

        // MCP `controlSetSettings` with explicit `separateLabelPosition`
        val rowCell = FormBuilder.rowInsertBelow(gridElem, 0)._1
        val targetY = NodeInfoCellOps.y(rowCell).get
        Await.result(
          FormBuilderMcpRpcApiImpl.controlSetSettings(
            "",
            "control-1",
            ControlSettingsUpdateR(separateLabelPosition = Some(McpControlPosition(x = 1, y = targetY, width = 4, height = 1)))
          ),
          Duration.Inf
        )
        val settingsAfterCustomPos = Await.result(FormBuilderMcpRpcApiImpl.controlGetSettings("", "control-1"), Duration.Inf)
        assert(settingsAfterCustomPos.separateLabelPosition.contains(McpControlPosition(1, targetY, 4, 1)))

        // MCP `gridSetLabelsPosition("top")` moves all separate labels back above controls
        Await.result(FormBuilderMcpRpcApiImpl.gridSetLabelsPosition("", gridName, "top"), Duration.Inf)
        assert(! FormBuilder.hasSeparateLabel(FormRunner.findControlByName("control-1").get))
      }
    }

    it("disallows separate labels on single-row repeated grids") {
      withActionAndFBDoc(SectionsGridsRepeatsDoc) { implicit ctx =>
        val singleRowRepeatGridOpt =
          findNestedContainers(ctx.bodyElem).find { c =>
            IsGrid(c) && FormBuilder.isSingleRowRepeatGrid(c)
          }
        assert(singleRowRepeatGridOpt.isDefined)
        val repeatCtrl = findNestedControls(singleRowRepeatGridOpt.get).head
        assert(! FormBuilder.canHaveSeparateLabel(repeatCtrl))
        assert(! FormBuilder.canMoveLabelLeft(repeatCtrl))
      }
    }

    it("moves separate labels (left or outside cell) back inside controls when converting a 1-row grid to repeated, with Undo") {
      withActionAndFBDoc("oxf:/org/orbeon/oxf/fr/template-for-repeated-sections.xhtml") { implicit ctx =>
        implicit val formRunnerParams: FormRunnerParams =
          FormRunnerParams(AppForm.FormBuilder.app, AppForm.FormBuilder.form, 1.some, None, None, "new")

        // `grid-1` is a 1-row non-repeated grid with `my-input` (w=12); split into two 6-col cells and place a second control
        val grid1     = FormRunner.findControlByName("grid-1").get
        val myInput   = FormRunner.findControlByName("my-input").get
        val cell1     = myInput.parentUnsafe
        FormBuilder.split(cell1, org.orbeon.datatypes.Direction.Left, Some(6))

        val row1Cells = (grid1 / CellTest).toList
        assert(row1Cells.size == 2)
        FormBuilder.selectCell(row1Cells(1))
        val secondName = ToolboxOps.insertNewControl(<binding element="xf|input" xmlns:xf="http://www.w3.org/2002/xforms"/>).get
        val secondCtrl = FormRunner.findControlByName(secondName).get

        // Put `my-input`'s label on the left (`x=1, w=2`, control `x=3, w=4`), and `secondCtrl`'s label on the RIGHT (`control x=7, w=4`, `label x=11, w=2`)
        FormBuilder.moveControlLabelToLeft(myInput)
        FormBuilder.split(secondCtrl.parentUnsafe, org.orbeon.datatypes.Direction.Left, Some(4))
        val rightEmptyCell = (grid1 / CellTest).find(c => NodeInfoCellOps.x(c).contains(11)).get
        FormBuilder.moveControlLabelToPosition(secondCtrl, UndoAction.CellRect(11, 1, 2, 1))

        assert(FormBuilder.hasSeparateLabel(myInput))
        assert(FormBuilder.hasSeparateLabel(secondCtrl))
        assert(NodeInfoCellOps.x(FormRunner.findSeparateLabelForControl(secondCtrl).get.parentUnsafe).contains(11))

        // Select the separate label cell to verify selection moves cleanly onto the control cell when merged
        val myInputLabelCell = FormRunner.findSeparateLabelForControl(myInput).get.parentUnsafe
        FormBuilder.selectCell(myInputLabelCell)

        // Save container undo snapshot (as Grid Settings dialog does) and convert `grid-1` to repeated
        FormBuilderXPathApi.saveControlToUndoStack("grid-1", "grid-1")
        FormBuilder.setRepeatProperties(
          controlName          = "grid-1",
          repeat               = true,
          userCanAddRemove     = true,
          numberRows           = false,
          usePaging            = false,
          min                  = "",
          max                  = "",
          freeze               = "",
          iterationNameOrEmpty = "",
          applyDefaults        = false,
          initialIterations    = ""
        )

        val myInputAfterRepeat    = FormRunner.findControlByName("my-input").get
        val secondCtrlAfterRepeat = FormRunner.findControlByName(secondName).get

        assert(FormRunner.isRepeat(grid1))
        assert(! FormBuilder.hasSeparateLabel(myInputAfterRepeat))
        assert(! FormBuilder.hasSeparateLabel(secondCtrlAfterRepeat))
        assert((myInputAfterRepeat / "*:label").nonEmpty)
        assert((secondCtrlAfterRepeat / "*:label").nonEmpty)

        // Both adjacent label cells (left on `my-input`, right on `secondCtrl`) merged back to width 6
        assert(NodeInfoCellOps.x(myInputAfterRepeat.parentUnsafe).contains(1))
        assert(NodeInfoCellOps.w(myInputAfterRepeat.parentUnsafe).contains(6))
        assert(NodeInfoCellOps.x(secondCtrlAfterRepeat.parentUnsafe).contains(7))
        assert(NodeInfoCellOps.w(secondCtrlAfterRepeat.parentUnsafe).contains(6))
        assert((grid1 / CellTest).size == 2)
        assert(FormBuilder.findSelectedCell.contains(myInputAfterRepeat.parentUnsafe))

        // Undo restores non-repeated grid AND separate labels!
        FormBuilderXPathApi.undoActionImpl()
        val grid1AfterUndo = FormRunner.findControlByName("grid-1").get
        assert(! FormRunner.isRepeat(grid1AfterUndo))
        assert(FormBuilder.hasSeparateLabel(FormRunner.findControlByName("my-input").get))
        assert(FormBuilder.hasSeparateLabel(FormRunner.findControlByName(secondName).get))
      }
    }

    it("preserves separate labels when converting a multi-row grid to repeated, and moves them to top if shrunk to 1 row") {
      withActionAndFBDoc("oxf:/org/orbeon/oxf/fr/template-for-repeated-sections.xhtml") { implicit ctx =>
        implicit val formRunnerParams: FormRunnerParams =
          FormRunnerParams(AppForm.FormBuilder.app, AppForm.FormBuilder.form, 1.some, None, None, "new")

        val grid1   = FormRunner.findControlByName("grid-1").get
        val myInput = FormRunner.findControlByName("my-input").get
        val initW   = NodeInfoCellOps.w(myInput.parentUnsafe).get
        FormBuilder.moveControlLabelToLeft(myInput)
        FormBuilder.rowInsertBelow(grid1, 0)

        // Converting a 2-row grid to repeated keeps separate labels intact
        FormBuilder.setRepeatProperties(
          controlName          = "grid-1",
          repeat               = true,
          userCanAddRemove     = true,
          numberRows           = false,
          usePaging            = false,
          min                  = "",
          max                  = "",
          freeze               = "",
          iterationNameOrEmpty = "",
          applyDefaults        = false,
          initialIterations    = ""
        )
        assert(FormRunner.isRepeat(grid1))
        assert(! FormBuilder.isSingleRowRepeatGrid(grid1))
        assert(FormBuilder.hasSeparateLabel(FormRunner.findControlByName("my-input").get))

        // Deleting row 2 reduces the repeated grid to 1 row, moving the separate label back inside `my-input`
        FormBuilder.rowDelete(grid1.id, 1)
        assert(FormBuilder.isSingleRowRepeatGrid(grid1))
        val myInputAfterRowDelete = FormRunner.findControlByName("my-input").get
        assert(! FormBuilder.hasSeparateLabel(myInputAfterRowDelete))
        assert((myInputAfterRowDelete / "*:label").nonEmpty)
        assert(NodeInfoCellOps.w(myInputAfterRowDelete.parentUnsafe).contains(initW))
      }
    }
  }
}
