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

import org.orbeon.datatypes.{Coordinate1, Direction}
import org.orbeon.dom.QName
import org.orbeon.oxf.fb.UndoAction.{CellRect, ControlSettingsBatch, MoveControlLabel, MoveSeparateLhha}
import org.orbeon.oxf.fr.NodeInfoCell.*
import org.orbeon.oxf.fr.{Cell, ControlOps as FRControlOps, FormRunner, GridModel}
import org.orbeon.oxf.util.CoreUtils.*
import org.orbeon.oxf.util.StringUtils.*
import org.orbeon.oxf.xforms.NodeInfoFactory.*
import org.orbeon.oxf.xforms.action.XFormsAPI.{delete, ensureAttribute, insert, setvalue}
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.Implicits.*
import org.orbeon.scaxon.NodeConversions.*
import org.orbeon.scaxon.SimplePath.*
import org.orbeon.xforms.Namespaces


trait SeparateLhhaOps {

  self: GridOps with ControlOps with BindingOps =>

  import Private.*

  def defaultSeparateLabelWidth(cellW: Int, maxGridWidth: Int): Int =
    if (cellW >= 4)
      math.min(maxGridWidth / 4, (cellW + 2) / 3)
    else
      1

  def hasSeparateLabel(controlElem: NodeInfo): Boolean =
    FormRunner.findSeparateLabelForControl(controlElem).isDefined

  def isSingleRowRepeatGrid(gridElem: NodeInfo): Boolean =
    FormRunner.isRepeat(gridElem) &&
      Cell.analyze12ColumnGridAndFillHoles(gridElem, simplify = false, transpose = false).height <= 1

  def canHaveSeparateLabel(controlElem: NodeInfo)(implicit ctx: FormBuilderDocContext): Boolean =
    (controlElem ne null)                                     &&
      ! FormRunner.IsContainer(controlElem)                   &&
      ! FormRunner.isSeparateLhhaElem(controlElem)            &&
      controlElem.parentOption.exists(p => (p self CellTest).nonEmpty) &&
      ! isTriggerControl(controlElem)                         &&
      ! isInSingleRowRepeatGrid(controlElem)                  &&
      supportsMovableLabel(controlElem)

  def canMoveLabelLeft(controlElem: NodeInfo)(implicit ctx: FormBuilderDocContext): Boolean =
    canHaveSeparateLabel(controlElem) && ! hasSeparateLabel(controlElem) && {
      val cellElem  = controlElem.parentUnsafe
      val gridElem  = getContainingGrid(cellElem)
      val gridModel = Cell.analyze12ColumnGridAndFillHoles(gridElem, simplify = false, transpose = false)
      findEmptyMatchingLeftCell(gridModel, cellElem).isDefined ||
        NodeInfoCellOps.w(cellElem).getOrElse(1) >= 2
    }

  def isSeparateLabelAdjacentLeft(controlElem: NodeInfo): Boolean =
    FormRunner.findSeparateLabelForControl(controlElem).exists { labelElem =>
      val controlCellElem = controlElem.parentUnsafe
      val labelCellElem   = labelElem.parentUnsafe
      val gridElem        = getContainingGrid(controlCellElem)
      if (getContainingGrid(labelCellElem) == gridElem) {
        val gridModel = Cell.analyze12ColumnGridAndFillHoles(gridElem, simplify = false, transpose = false)
        (for {
          cOrigin <- Cell.findOriginCell(gridModel, controlCellElem)
          lOrigin <- Cell.findOriginCell(gridModel, labelCellElem)
        } yield isImmediatelyLeftAdjacent(lOrigin, cOrigin)).contains(true)
      } else
        false
    }

  def canGridMoveLabelsLeft(gridElem: NodeInfo)(implicit ctx: FormBuilderDocContext): Boolean =
    (gridElem ne null) &&
      ! isSingleRowRepeatGrid(gridElem) &&
      (gridElem / CellTest / *).exists(c => ! FormRunner.isSeparateLhhaElem(c) && canMoveLabelLeft(c))

  def canGridMoveLabelsTop(gridElem: NodeInfo): Boolean =
    (gridElem ne null) &&
      (gridElem / CellTest / *).exists(FormRunner.isSeparateLhhaElem)

  def findEmptyMatchingLeftCell(
    gridModel      : GridModel[NodeInfo],
    controlCellElem: NodeInfo
  ): Option[Cell[NodeInfo]] =
    for {
      originCell <- Cell.findOriginCell(gridModel, controlCellElem)
      neighbors  = Cell.nonOverflowingNeighbors(gridModel, originCell, Direction.Left)
      if neighbors.size == 1
      leftCell   = neighbors.head
      if leftCell.y == originCell.y && leftCell.h == originCell.h
      leftElem   <- leftCell.u
      if ! NodeInfoCellOps.hasChildElement(leftElem)
    } yield
      leftCell

  def renameSeparateLabelByElement(
    separateLabelElem: NodeInfo,
    newControlName   : String
  ): Unit = {
    ensureAttribute(separateLabelElem, "id",  FRControlOps.labelId(newControlName))
    ensureAttribute(separateLabelElem, "for", FormRunner.controlId(newControlName))
    separateLabelElem.att("ref").headOption.foreach { ref =>
      FormBuilder.findZeroBasedIndexFromAlertRefHandleBlankRef(ref.stringValue, "label").foreach { explicitIndexOpt =>
        setvalue(List(ref), FormBuilder.buildResourcePointer(newControlName, "label", explicitIndexOpt))
      }
    }
  }

  def moveControlLabelToLeft(
    controlElem: NodeInfo,
    labelWidth : Option[Int] = None
  )(implicit
    ctx        : FormBuilderDocContext
  ): Option[UndoAction] =
    (canHaveSeparateLabel(controlElem) && ! hasSeparateLabel(controlElem)) flatOption {
      val controlName = FormRunner.getControlName(controlElem)
      val cellElem    = controlElem.parentUnsafe
      val gridElem    = getContainingGrid(cellElem)
      val gridModel   = Cell.analyze12ColumnGridAndFillHoles(gridElem, simplify = false, transpose = false)

      for {
        originCell <- Cell.findOriginCell(gridModel, cellElem)
        localLabel <- (controlElem / (Namespaces.XF -> "label")).headOption
        result     <-
          labelWidth match {
            case Some(explicitW) if originCell.w > explicitW && explicitW >= 1 =>
              Some(splitCellAndPlaceLabelLeft(controlName, cellElem, originCell, localLabel, explicitW))
            case _ =>
              findEmptyMatchingLeftCell(gridModel, cellElem) match {
                case Some(leftCell) =>
                  val leftCellElem = leftCell.u.get
                  val detached     = buildDetachedSeparateLabel(controlName, localLabel)
                  delete(localLabel)
                  insert(into = leftCellElem, origin = detached)
                  Some(
                    MoveControlLabel(
                      controlName     = controlName,
                      toTop           = true,
                      origControlRect = cellRectOf(originCell),
                      labelRectOpt    = Some(cellRectOf(leftCell)),
                      splitOccurred   = false
                    )
                  )
                case None if originCell.w >= 2 =>
                  val maxW   = NodeInfoCellOps.maxGridWidth(gridElem)
                  val splitW = defaultSeparateLabelWidth(originCell.w, maxW)
                  Some(splitCellAndPlaceLabelLeft(controlName, cellElem, originCell, localLabel, splitW))
                case None =>
                  None
              }
          }
      } yield
        result
    }

  def moveControlLabelToPosition(
    controlElem: NodeInfo,
    targetRect : CellRect
  )(implicit
    ctx        : FormBuilderDocContext
  ): Option[UndoAction] =
    canHaveSeparateLabel(controlElem) flatOption {
      val controlName     = FormRunner.getControlName(controlElem)
      val controlCellElem = controlElem.parentUnsafe
      val gridElem        = getContainingGrid(controlCellElem)

      FormRunner.findSeparateLabelForControl(controlElem) match {
        case Some(existingSeparateLabel) =>
          val oldLabelCellElem = existingSeparateLabel.parentUnsafe
          val gridModelBefore  = Cell.analyze12ColumnGridAndFillHoles(gridElem, simplify = false, transpose = false)
          val oldOriginOpt     = Cell.findOriginCell(gridModelBefore, oldLabelCellElem)

          if (oldOriginOpt.exists(o => cellRectOf(o) == targetRect)) {
            None
          } else {
            val clonedLabel = buildDetachedSeparateLabel(controlName, existingSeparateLabel)
            delete(existingSeparateLabel)
            val targetCellElem =
              ensureCellWithPositionAndSize(gridElem, targetRect.x, targetRect.y, targetRect.w, targetRect.h).get.td
            insert(into = targetCellElem, origin = clonedLabel)
            oldOriginOpt.map { oldOrigin =>
              MoveSeparateLhha(
                lhhaId    = FRControlOps.labelId(controlName),
                gridName  = FormRunner.getControlName(gridElem),
                fromCoord = Coordinate1(oldOrigin.x, oldOrigin.y),
                toCoord   = Coordinate1(targetRect.x, targetRect.y)
              )
            }
          }

        case None =>
          val gridModel  = Cell.analyze12ColumnGridAndFillHoles(gridElem, simplify = false, transpose = false)
          val originCell = Cell.findOriginCell(gridModel, controlCellElem).get
          val localLabel = (controlElem / (Namespaces.XF -> "label")).headOption

          localLabel.map { labelElem =>
            // Check if targetRect carves the left portion of the control's own cell
            if (
              targetRect.x == originCell.x &&
              targetRect.y == originCell.y &&
              targetRect.h == originCell.h &&
              targetRect.w >= 1            &&
              targetRect.w < originCell.w
            ) {
              splitCellAndPlaceLabelLeft(controlName, controlCellElem, originCell, labelElem, targetRect.w)
            } else {
              val targetCellElem =
                ensureCellWithPositionAndSize(gridElem, targetRect.x, targetRect.y, targetRect.w, targetRect.h).get.td
              val detached = buildDetachedSeparateLabel(controlName, labelElem)
              delete(labelElem)
              insert(into = targetCellElem, origin = detached)
              MoveControlLabel(
                controlName     = controlName,
                toTop           = true,
                origControlRect = cellRectOf(originCell),
                labelRectOpt    = Some(targetRect),
                splitOccurred   = false
              )
            }
          }
      }
    }

  def moveControlLabelToTop(
    controlElem         : NodeInfo,
    mergeAdjacentIfLeft : Boolean = true
  )(implicit
    ctx                 : FormBuilderDocContext
  ): Option[UndoAction] =
    FormRunner.findSeparateLabelForControl(controlElem).flatMap { separateLabelElem =>
      val controlName     = FormRunner.getControlName(controlElem)
      val controlCellElem = controlElem.parentUnsafe
      val labelCellElem   = separateLabelElem.parentUnsafe
      val gridElem        = getContainingGrid(controlCellElem)
      val gridModel       = Cell.analyze12ColumnGridAndFillHoles(gridElem, simplify = false, transpose = false)

      for {
        controlOrigin <- Cell.findOriginCell(gridModel, controlCellElem)
        labelOrigin   <- Cell.findOriginCell(gridModel, labelCellElem)
      } yield {
        val localLabel = buildLocalLabelFromSeparate(separateLabelElem)
        delete(separateLabelElem)
        insertLocalLabelIntoControl(controlElem, localLabel)

        val shouldMerge =
          mergeAdjacentIfLeft && (
            isImmediatelyLeftAdjacent(labelOrigin, controlOrigin) ||
            isImmediatelyLeftAdjacent(controlOrigin, labelOrigin)
          )
        if (shouldMerge) {
          val mergedX = math.min(labelOrigin.x, controlOrigin.x)
          val mergedW = labelOrigin.w + controlOrigin.w
          NodeInfoCellOps.updateX(controlCellElem, mergedX)
          NodeInfoCellOps.updateW(controlCellElem, mergedW)
          if (ctx.formBuilderModel.isDefined && findSelectedCell.contains(labelCellElem))
            selectCell(controlCellElem)
          delete(labelCellElem)
          MoveControlLabel(
            controlName     = controlName,
            toTop           = false,
            origControlRect = CellRect(mergedX, controlOrigin.y, mergedW, controlOrigin.h),
            labelRectOpt    = Some(cellRectOf(labelOrigin)),
            splitOccurred   = true
          )
        } else {
          MoveControlLabel(
            controlName     = controlName,
            toTop           = false,
            origControlRect = cellRectOf(controlOrigin),
            labelRectOpt    = Some(cellRectOf(labelOrigin)),
            splitOccurred   = false
          )
        }
      }
    }

  def moveGridLabelsToLeft(gridElem: NodeInfo)(implicit ctx: FormBuilderDocContext): Option[UndoAction] =
    (! isSingleRowRepeatGrid(gridElem)) flatOption {
      val gridModel = Cell.analyze12ColumnGridAndFillHoles(gridElem, simplify = false, transpose = false)
      // Snapshot controls in row-major order so splitting a cell doesn't disturb iteration
      val controlsInOrder =
        Cell.originCells(gridModel)
          .flatMap(_.u)
          .flatMap(_ firstChildOpt *)
          .filter(c => ! FormRunner.isSeparateLhhaElem(c))
          .toList

      val actions = controlsInOrder.flatMap(moveControlLabelToLeft(_, None))
      actions.nonEmpty option ControlSettingsBatch(actions)
    }

  def moveGridLabelsToTop(gridElem: NodeInfo)(implicit ctx: FormBuilderDocContext): Option[UndoAction] = {
    val gridModel = Cell.analyze12ColumnGridAndFillHoles(gridElem, simplify = false, transpose = false)
    val controlsWithSeparateLabels =
      Cell.originCells(gridModel)
        .flatMap(_.u)
        .flatMap(_ firstChildOpt *)
        .filter(c => ! FormRunner.isSeparateLhhaElem(c) && hasSeparateLabel(c))
        .toList

    val actions = controlsWithSeparateLabels.flatMap(moveControlLabelToTop(_, mergeAdjacentIfLeft = true))
    actions.nonEmpty option ControlSettingsBatch(actions)
  }

  def buildDetachedSeparateLabel(controlName: String, sourceLabelElem: NodeInfo): NodeInfo = {
    val preservedAtts =
      (sourceLabelElem /@ @*).filter(a => a.localname != "id" && a.localname != "for")
    elementInfo(
      Namespaces.XF -> "label",
      List(
        attributeInfo("id",  FRControlOps.labelId(controlName)),
        attributeInfo("for", FormRunner.controlId(controlName))
      ) ++ preservedAtts ++ (sourceLabelElem / AnyTest)
    )
  }

  def buildLocalLabelFromSeparate(separateLabelElem: NodeInfo): NodeInfo = {
    val preservedAtts =
      (separateLabelElem /@ @*).filter(a => a.localname != "id" && a.localname != "for")
    elementInfo(
      Namespaces.XF -> "label",
      preservedAtts ++ (separateLabelElem / AnyTest)
    )
  }

  def insertLocalLabelIntoControl(controlElem: NodeInfo, localLabelElem: NodeInfo): Unit =
    insert(
      into   = controlElem,
      before = (controlElem / *).filter(c => FormRunner.LHHAInOrder.contains(c.localname)),
      origin = localLabelElem
    )

  def applyMoveControlLabelUndo(action: MoveControlLabel)(implicit ctx: FormBuilderDocContext): Option[UndoAction] =
    FormRunner.findControlByName(action.controlName).flatMap { controlElem =>
      if (action.toTop) {
        moveControlLabelToTop(controlElem, mergeAdjacentIfLeft = action.splitOccurred)
      } else {
        action.labelRectOpt match {
          case Some(labelRect) if action.splitOccurred && labelRect.x == action.origControlRect.x =>
            moveControlLabelToLeft(controlElem, Some(labelRect.w))
          case Some(labelRect) if action.splitOccurred =>
            val controlCellElem = controlElem.parentUnsafe
            val newControlW     = action.origControlRect.w - labelRect.w
            if (newControlW >= 1) {
              NodeInfoCellOps.updateX(controlCellElem, action.origControlRect.x)
              NodeInfoCellOps.updateW(controlCellElem, newControlW)
              val newLabelCell: NodeInfo =
                <fr:c
                  xmlns:fr="http://orbeon.org/oxf/xml/form-runner"
                  id={nextTmpId()}
                  x={labelRect.x.toString}
                  y={labelRect.y.toString}
                  w={labelRect.w.toString}/>
              val insertedLabelCell = insert(into = Nil, after = List(controlCellElem), origin = newLabelCell).head
              if (labelRect.h > 1)
                NodeInfoCellOps.updateH(insertedLabelCell, labelRect.h)
              (controlElem / (Namespaces.XF -> "label")).headOption.map { localLabel =>
                val detached = buildDetachedSeparateLabel(action.controlName, localLabel)
                delete(localLabel)
                insert(into = insertedLabelCell, origin = detached)
                MoveControlLabel(
                  controlName     = action.controlName,
                  toTop           = true,
                  origControlRect = CellRect(action.origControlRect.x, action.origControlRect.y, newControlW, action.origControlRect.h),
                  labelRectOpt    = Some(labelRect),
                  splitOccurred   = true
                )
              }
            } else
              None
          case Some(labelRect) =>
            val controlCellElem = controlElem.parentUnsafe
            val gridElem        = getContainingGrid(controlCellElem)
            val targetCellOpt   =
              (gridElem / CellTest).find { c =>
                NodeInfoCellOps.x(c).contains(labelRect.x) &&
                NodeInfoCellOps.y(c).contains(labelRect.y) &&
                ! NodeInfoCellOps.hasChildElement(c)
              }
            targetCellOpt match {
              case Some(targetCellElem) =>
                (controlElem / (Namespaces.XF -> "label")).headOption.map { localLabel =>
                  val detached = buildDetachedSeparateLabel(action.controlName, localLabel)
                  delete(localLabel)
                  insert(into = targetCellElem, origin = detached)
                  MoveControlLabel(
                    controlName     = action.controlName,
                    toTop           = true,
                    origControlRect = action.origControlRect,
                    labelRectOpt    = Some(labelRect),
                    splitOccurred   = false
                  )
                }
              case None =>
                moveControlLabelToPosition(controlElem, labelRect)
            }
          case None =>
            moveControlLabelToLeft(controlElem, None)
        }
      }
    }

  def applyMoveSeparateLhhaUndo(action: MoveSeparateLhha)(implicit ctx: FormBuilderDocContext): Option[UndoAction] =
    for {
      gridElem <- FormRunner.findControlByName(action.gridName)
      fromCell <- (gridElem / CellTest).find(c => NodeInfoCellOps.x(c).contains(action.toCoord.x) && NodeInfoCellOps.y(c).contains(action.toCoord.y))
      toCell   <- (gridElem / CellTest).find(c => NodeInfoCellOps.x(c).contains(action.fromCoord.x) && NodeInfoCellOps.y(c).contains(action.fromCoord.y))
      if ! NodeInfoCellOps.hasChildElement(toCell)
      elem     <- fromCell firstChildOpt *
    } yield {
      val inserted = insert(into = toCell, origin = elem).head
      delete(elem)
      if (! FormRunner.isSeparateLhhaElem(inserted))
        reorderBindsForGrid(gridElem)
      selectCell(toCell)
      MoveSeparateLhha(action.lhhaId, action.gridName, action.toCoord, action.fromCoord)
    }

  private object Private {

    val XXBLModeQName    : QName = QName("mode",      Namespaces.XXBL, "xxbl:mode")
    val XXBLLabelForQName: QName = QName("label-for", Namespaces.XXBL, "xxbl:label-for")

    def cellRectOf(cell: Cell[NodeInfo]): CellRect =
      CellRect(cell.x, cell.y, cell.w, cell.h)

    def isImmediatelyLeftAdjacent(left: Cell[NodeInfo], right: Cell[NodeInfo]): Boolean =
      left.x + left.w == right.x && left.y == right.y && left.h == right.h

    def isTriggerControl(controlElem: NodeInfo): Boolean =
      controlElem.namespaceURI == Namespaces.XF && (controlElem.localname == "trigger" || controlElem.localname == "submit")

    def isInSingleRowRepeatGrid(controlElem: NodeInfo): Boolean =
      controlElem.parentOption.exists(cellElem => isSingleRowRepeatGrid(getContainingGrid(cellElem)))

    def supportsMovableLabel(controlElem: NodeInfo)(implicit ctx: FormBuilderDocContext): Boolean = {
      import ctx.bindingIndex
      val bindingOpt = bindingForControlElement(controlElem)
      val hasLabelSupport =
        FormRunner.findControlOrSeparateLabelElem(controlElem).nonEmpty ||
          bindingOpt.exists(hasViewTemplateSupportElementFor(_, "label"))

      hasLabelSupport && bindingOpt.forall { binding =>
        val modeTokens    = binding.attValueOpt(XXBLModeQName).toList.flatMap(_.splitTo[List]()).toSet
        val isCustomLabel = modeTokens("custom-lhha") && ! modeTokens("-custom-label")
        (! isCustomLabel) || binding.attValueOpt(XXBLLabelForQName).exists(_.nonAllBlank)
      }
    }

    def splitCellAndPlaceLabelLeft(
      controlName   : String,
      controlCell   : NodeInfo,
      originCell    : Cell[NodeInfo],
      localLabelElem: NodeInfo,
      labelW        : Int
    )(implicit
      ctx           : FormBuilderDocContext
    ): MoveControlLabel = {
      // Split `controlCell` horizontally so `controlCell` shrinks to `labelW` on the left and a new cell is inserted on the right.
      // To keep `controlCell` (and its selection/attributes) with the control on the right, move `controlCell`'s x/w to the right
      // and insert the new label cell on the left!
      val gridElem   = getContainingGrid(controlCell)
      val controlNewX = originCell.x + labelW
      val controlNewW = originCell.w - labelW

      NodeInfoCellOps.updateX(controlCell, controlNewX)
      NodeInfoCellOps.updateW(controlCell, controlNewW)

      val newLabelCell: NodeInfo =
        <fr:c
          xmlns:fr="http://orbeon.org/oxf/xml/form-runner"
          id={nextTmpId()}
          x={originCell.x.toString}
          y={originCell.y.toString}
          w={labelW.toString}/>

      val insertedLabelCell = insert(into = Nil, before = List(controlCell), origin = newLabelCell).head
      if (originCell.h > 1)
        NodeInfoCellOps.updateH(insertedLabelCell, originCell.h)

      val detached = buildDetachedSeparateLabel(controlName, localLabelElem)
      delete(localLabelElem)
      insert(into = insertedLabelCell, origin = detached)

      MoveControlLabel(
        controlName     = controlName,
        toTop           = true,
        origControlRect = cellRectOf(originCell),
        labelRectOpt    = Some(CellRect(originCell.x, originCell.y, labelW, originCell.h)),
        splitOccurred   = true
      )
    }
  }
}
