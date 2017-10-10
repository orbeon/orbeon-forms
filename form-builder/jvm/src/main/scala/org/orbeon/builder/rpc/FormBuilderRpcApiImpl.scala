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

import org.orbeon.oxf.fb.{FormBuilder, FormBuilderDocContext, ToolboxOps}
import org.orbeon.oxf.xforms.action.XFormsAPI
import org.orbeon.oxf.xml.TransformerUtils
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.SimplePath._
import org.orbeon.xforms.XFormsId

object FormBuilderRpcApiImpl extends FormBuilderRpcApi {

  private val EditorIdPrefix = "fb-lhh-editor-for-"

  def controlUpdateLHHA(controlId: String, lhha: String, value: String, isHTML: Boolean): Unit = {

    implicit val ctx = FormBuilderDocContext()

    val staticControlId = XFormsId.getStaticIdFromId(controlId)

    // The target might the control itself, or for grids with LHH in headers, an `xf:output` added to edit the LHH
    val controlName = FormBuilder.controlNameFromId(
      if (staticControlId.startsWith(EditorIdPrefix))
        staticControlId.substring(EditorIdPrefix.size)
      else
        staticControlId
    )

    XFormsAPI.setvalue(FormBuilder.currentResources / controlName / lhha, value)
    FormBuilder.setControlLHHAMediatype(controlName, lhha, isHTML)
  }

  def controlDelete(controlId: String): Unit =
    resolveId(controlId) foreach { controlElem ⇒
      FormBuilder.deleteControlWithinCell(controlElem.parentUnsafe, updateTemplates = true)
    }

  def controlEditDetails(controlId: String): Unit =
    XFormsAPI.dispatch(
      name       = "fb-show-dialog",
      targetId   = "dialog-control-details",
      properties = Map("control-id" → Some(XFormsId.getStaticIdFromId(controlId)))
    )

  def controlEditItems(controlId: String): Unit =
    XFormsAPI.dispatch(
      name       = "fb-show-dialog",
      targetId   = "dialog-itemsets",
      properties = Map("control-element" → resolveId(controlId))
    )

  def controlDnD(controlId: String, destCellId: String, copy: Boolean): Unit = {

    implicit val ctx = FormBuilderDocContext()

    val sourceCellElem = resolveId(controlId).get.parentUnsafe
    val targetCellElem = resolveId(destCellId).get

    ToolboxOps.dndControl(sourceCellElem, targetCellElem, copy)
  }

  def rowInsertAbove(controlId: String, position: Int): Unit = {
    implicit val ctx = FormBuilderDocContext()
    FormBuilder.rowInsertAbove(controlId, position - 1)
  }

  def rowDelete(controlId: String, position: Int): Unit =
    FormBuilder.rowDelete(controlId, position - 1)(FormBuilderDocContext())

  def rowInsertBelow(controlId: String, position: Int): Unit = {
    implicit val ctx = FormBuilderDocContext()
    FormBuilder.rowInsertBelow(FormBuilder.containerById(controlId), position - 1)
  }

  def shrinkDown(cellId: String): Unit =
    FormBuilder.shrinkCellDown(resolveId(cellId).get, 1)

  def expandRight(cellId: String): Unit =
    FormBuilder.expandCellRight(resolveId(cellId).get, 1)

  def expandDown(cellId: String): Unit =
    FormBuilder.expandCellDown(resolveId(cellId).get, 1)

  def shrinkRight(cellId: String): Unit =
    FormBuilder.shrinkCellRight(resolveId(cellId).get, 1)

  def sectionDelete(sectionId: String): Unit =
    FormBuilder.deleteSectionById(sectionId)(FormBuilderDocContext())

  def sectionUpdateLabel(sectionId: String, label: String): Unit =
    XFormsAPI.setvalue(FormBuilder.currentResources / FormBuilder.controlNameFromId(sectionId) / "label", label)

  def containerEditDetails(containerId: String): Unit =
    XFormsAPI.dispatch(
      name       = "fb-show-dialog",
      targetId   = "dialog-container-details",
      properties = Map("container" → Some(FormBuilder.containerById(containerId)(FormBuilderDocContext())))
    )

  def sectionEditHelp(sectionId: String): Unit =
    XFormsAPI.dispatch(
      name       = "fb-show-dialog",
      targetId   = "dialog-help",
      properties = Map("control-id" → Some(sectionId))
    )

  def sectionMoveUp(sectionId: String): Unit = {
    implicit val ctx = FormBuilderDocContext()
    FormBuilder.moveSectionUp(FormBuilder.containerById(sectionId))
  }

  def sectionMoveDown(sectionId: String): Unit = {
    implicit val ctx = FormBuilderDocContext()
    FormBuilder.moveSectionDown(FormBuilder.containerById(sectionId))
  }

  def sectionMoveRight(sectionId: String): Unit = {
    implicit val ctx = FormBuilderDocContext()
    FormBuilder.moveSectionRight(FormBuilder.containerById(sectionId))
  }

  def sectionMoveLeft(sectionId: String): Unit = {
    implicit val ctx = FormBuilderDocContext()
    FormBuilder.moveSectionLeft(FormBuilder.containerById(sectionId))
  }

  def gridDelete(gridId: String): Unit =
    FormBuilder.deleteGridById(gridId)(FormBuilderDocContext())

  def containerCopy(containerId: String): Unit = {
    implicit val ctx = FormBuilderDocContext()
    ToolboxOps.writeXcvToClipboard(ToolboxOps.controlOrContainerElemToXcv(FormBuilder.containerById(containerId)))
  }

  def containerCut(containerId: String): Unit = {

    implicit val ctx = FormBuilderDocContext()

    val containerElem = FormBuilder.containerById(containerId)

    if (FormBuilder.IsGrid(containerElem) && FormBuilder.canDeleteGrid(containerElem) ||
      FormBuilder.IsSection(containerElem) && FormBuilder.canDeleteSection(containerElem)) {

      containerCopy(containerId)

      if (FormBuilder.IsGrid(FormBuilder.containerById(containerId)))
        FormBuilder.deleteGridById(containerId)
      else
        FormBuilder.deleteSectionById(containerId)
    }
  }

  private def resolveId(id: String): Option[NodeInfo] =
    FormBuilder.findInViewTryIndex(FormBuilder.fbFormInstance.root, XFormsId.getStaticIdFromId(id))
}