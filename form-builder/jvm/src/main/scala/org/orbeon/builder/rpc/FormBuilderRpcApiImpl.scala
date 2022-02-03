/**
 * Copyright (C) 2017 Orbeon, Inc.
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
package org.orbeon.builder.rpc

import org.orbeon.datatypes.{AboveBelow, Direction}
import org.orbeon.oxf.fb.UndoAction.ControlSettings
import org.orbeon.oxf.fb._
import org.orbeon.oxf.fr.FormRunner
import org.orbeon.oxf.xforms.action.XFormsAPI
import org.orbeon.oxf.xforms.analysis.controls.LHHA
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.SimplePath._
import org.orbeon.xforms.XFormsId

object FormBuilderRpcApiImpl extends FormBuilderRpcApi {

  private val EditorIdPrefix   = "fb-lhh-editor-for-"
  private val EditorIdPrefixes = List(LHHA.Label.entryName, LHHA.Hint.entryName) map (EditorIdPrefix + _ + '-')

  def unsupportedBrowser(browserName: String, browserVersion: Double): Unit = {
    implicit val ctx = FormBuilderDocContext()

    val rootElem = ctx.userAgentInstance.toList map (_.rootElement)

    XFormsAPI.setvalue(rootElem child "browser-name",         s"$browserName $browserVersion")
    XFormsAPI.setvalue(rootElem child "is-supported-browser", false.toString)
  }

  def controlUpdateLabelOrHintOrText(controlId: String, lhha: String, value: String, isHTML: Boolean): Unit = {

    implicit val ctx = FormBuilderDocContext()

    val staticControlId = XFormsId.getStaticIdFromId(controlId)

    // The target might the control itself, or for grids with LHH in headers, an `xf:output` added to edit the LHH
    val controlName = FormRunner.controlNameFromId(
      EditorIdPrefixes find staticControlId.startsWith match {
        case Some(prefix) => staticControlId.substring(prefix.size)
        case None         => staticControlId
      }
    )

    // The client might send this after the control is deleted and we don't want to crash
    FormRunner.findControlByName(controlName) foreach { controlElem =>

      val xcvElemOpt = ToolboxOps.controlOrContainerElemToXcv(controlElem)

      if (FormBuilder.setControlLabelHintHelpOrText(controlName, lhha, value, None, isHTML))
        xcvElemOpt foreach { xcvElem =>
          Undo.pushUserUndoAction(
            ControlSettings(
              controlName,
              controlName,
              xcvElem
            )
          )
        }
    }
  }

  def controlDelete(controlId: String): Unit = {
    implicit val ctx = FormBuilderDocContext()
    resolveId(controlId) flatMap { controlElem =>
      FormBuilder.deleteControlWithinCell(controlElem.parentUnsafe, updateTemplates = true)
    } foreach
      Undo.pushUserUndoAction
  }

  def controlEditDetails(controlId: String): Unit =
    XFormsAPI.dispatch(
      name       = "fb-show-dialog",
      targetId   = "dialog-control-settings",
      properties = Map("control-id" -> Some(XFormsId.getStaticIdFromId(controlId)))
    )

  def controlEditItems(controlId: String): Unit =
    XFormsAPI.dispatch(
      name       = "fb-show-dialog",
      targetId   = "dialog-itemsets",
      properties = Map("control-element" -> resolveId(controlId)(FormBuilderDocContext()))
    )

  def controlDnD(controlId: String, destCellId: String, copy: Boolean): Unit = {

    implicit val ctx = FormBuilderDocContext()

    val sourceCellElem = resolveId(controlId).get.parentUnsafe
    val targetCellElem = resolveId(destCellId).get

    ToolboxOps.dndControl(sourceCellElem, targetCellElem, copy) foreach Undo.pushUserUndoAction
  }

  def rowInsert(controlId: String, position: Int, aboveBelowString: String): Unit = {
    implicit val ctx = FormBuilderDocContext()

    val (_, undoAction) =
      AboveBelow.withName(aboveBelowString) match {
        case AboveBelow.Above => FormBuilder.rowInsertAbove(FormBuilder.containerById(controlId), position - 1)
        case AboveBelow.Below => FormBuilder.rowInsertBelow(FormBuilder.containerById(controlId), position - 1)
      }

    undoAction foreach Undo.pushUserUndoAction
  }

  def rowDelete(controlId: String, position: Int): Unit = {
    implicit val ctx = FormBuilderDocContext()
    if (FormBuilder.canDeleteRow(controlId, position - 1))
      FormBuilder.rowDelete(controlId, position - 1) foreach Undo.pushUserUndoAction
  }

  def moveWall(cellId: String, startSide: Direction, target: Int): Unit = {
    implicit val ctx = FormBuilderDocContext()
    FormBuilder.moveWall(resolveId(cellId).get, startSide, target) foreach Undo.pushUserUndoAction
  }

  def mergeRight(cellId: String): Unit = {
    implicit val ctx = FormBuilderDocContext()
    FormBuilder.merge(resolveId(cellId).get, Direction.Right) foreach Undo.pushUserUndoAction
  }

  def mergeDown(cellId: String): Unit = {
    implicit val ctx = FormBuilderDocContext()
    FormBuilder.merge(resolveId(cellId).get, Direction.Down) foreach Undo.pushUserUndoAction
  }

  def splitX(cellId: String): Unit = {
    implicit val ctx = FormBuilderDocContext()
    FormBuilder.split(resolveId(cellId).get, Direction.Left, None) foreach Undo.pushUserUndoAction
  }

  def splitY(cellId: String): Unit = {
    implicit val ctx = FormBuilderDocContext()
    FormBuilder.split(resolveId(cellId).get, Direction.Up, None) foreach Undo.pushUserUndoAction
  }

  def sectionUpdateLabel(sectionId: String, label: String): Unit = {
    implicit val ctx = FormBuilderDocContext()
    XFormsAPI.setvalue(FormBuilder.currentResources / FormRunner.controlNameFromId(sectionId) / LHHA.Label.entryName, label)
  }

  def containerEditDetails(containerId: String): Unit =
    XFormsAPI.dispatch(
      name       = "fb-show-dialog",
      targetId   = "dialog-container-settings",
      properties = Map("container" -> Some(FormBuilder.containerById(containerId)(FormBuilderDocContext())))
    )

  def sectionMove(sectionId: String, directionString: String): Unit = {

    implicit val ctx = FormBuilderDocContext()

    FormBuilder.moveSection(
      FormBuilder.containerById(sectionId),
      Direction.withName(directionString)
    ) foreach
      Undo.pushUserUndoAction
  }

  def containerDelete(containerId: String): Unit = {

    implicit val ctx = FormBuilderDocContext()

    val undoAction =
      if (FormRunner.IsGrid(FormBuilder.containerById(containerId)))
        FormBuilder.deleteGridByIdIfPossible(containerId)
      else
        FormBuilder.deleteSectionByIdIfPossible(containerId)

    undoAction foreach Undo.pushUserUndoAction
  }

  def containerCopy(containerId: String): Unit = {
    implicit val ctx = FormBuilderDocContext()
    ToolboxOps.controlOrContainerElemToXcv(FormBuilder.containerById(containerId)) foreach ToolboxOps.writeXcvToClipboard
  }

  def containerCut(containerId: String): Unit = {

    implicit val ctx = FormBuilderDocContext()

    val containerElem = FormBuilder.containerById(containerId)

    if (FormBuilder.canDeleteContainer(containerElem)) {
      containerCopy(containerId)
      containerDelete(containerId)
    }
  }

  def containerMerge(containerId: String): Unit = {
    implicit val ctx = FormBuilderDocContext()
    XFormsAPI.dispatch(
      name       = "fb-show-dialog",
      targetId   = "dialog-ids",
      properties = Map("container-id" -> Some(containerId), "action" -> Some("merge"))
    )
  }

  def resolveId(id: String)(implicit ctx: FormBuilderDocContext): Option[NodeInfo] =
    FormRunner.findInViewTryIndex(XFormsId.getStaticIdFromId(id))
}