/**
  * Copyright (C) 2017 Orbeon, Inc.
  *
  * This program is free software you can redistribute it and/or modify it under the terms of the
  * GNU Lesser General Public License as published by the Free Software Foundation either version
  *  2.1 of the License, or (at your option) any later version.
  *
  * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY
  * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  * See the GNU Lesser General Public License for more details.
  *
  * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
  */
package org.orbeon.xbl

import org.orbeon.fr.DropIndicatorDnD
import org.orbeon.fr.DropIndicatorDnD.RepeatDropIndicatorDnD
import org.orbeon.web.DomSupport.*
import org.orbeon.xforms.XFormsXbl
import org.orbeon.xforms.facade.XBLCompanion
import org.scalajs.dom.html

import scala.scalajs.js


object Grid {

  private val IsIterationSelector = s".fr-grid-repeat-iteration:not(.${DropIndicatorDnD.MirrorClass}):not(.xforms-disabled)"

  XFormsXbl.declareCompanion("fr|grid-multiple", js.constructorOf[GridCompanion])

  private class GridCompanion(containerElem: html.Element) extends XBLCompanion {

    private var repeatGridElemOpt: Option[html.Element] = None

    private val repeatLineDnd = new RepeatDropIndicatorDnD(
      repeatContainerOpt = () => repeatGridElemOpt,
      formElem           = () => getXFormsFormOrThrow.elem,
      itemSelector       = IsIterationSelector,
      isItemMovable      = _.matches(":is(.can-move-up, .can-move-down)"),
      resolveHandleOpt   = (target, rowEl) =>
        if (target.closestOpt(".fr-grid-dropdown-menu").isDefined)
          None
        else
          target.closestOpt(".fr-repeat-column-left").filter(rowEl.contains),
      customizeMirror    = (origRow, cloneRow) => {
        val origCells  = origRow.querySelectorAllT(":scope > tr > td, :scope > tr > th")
        val cloneCells = cloneRow.querySelectorAllT(":scope > tr > td, :scope > tr > th")
        origCells.zip(cloneCells).foreach { case (origCell, copyCell) =>
          copyCell.style.width = s"${origCell.getBoundingClientRect().width}px"
        }
      },
      onDragStartHook    = () => GridMenus.onDragStart(),
      onDragEndHook      = () => GridMenus.onDragEnd()
    )

    override def init(): Unit = {
      GridMenus // initialize menus once

      containerElem.querySelectorOpt(":scope > .fr-grid.fr-repeat").foreach { repeatGridElem =>
        repeatGridElemOpt = Some(repeatGridElem)
        repeatLineDnd.attach(repeatGridElem)
      }
    }

    override def destroy(): Unit = {
      repeatGridElemOpt.foreach(repeatLineDnd.detach)
      repeatGridElemOpt = None
    }
  }
}

object GridMenus extends GridSectionMenus {
  override def componentName = "grid"
}
