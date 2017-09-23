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
package org.orbeon.builder

import org.orbeon.builder.BlockCache.Block
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.xforms._

import scala.scalajs.js
import org.scalajs.dom
import org.scalajs.jquery.JQuery

import scala.scalajs.js.annotation.{JSExportAll, JSExportTopLevel, ScalaJSDefined}

@JSExportTopLevel("ORBEON.builder.SideEditor")
@JSExportAll
object SectionGridEditor {

  lazy val sectionGridEditor                    = $(".fb-section-grid-editor")
  lazy val rowEditor                            = $(".fb-row-editor")
  var currentSectionGridOpt: js.UndefOr[JQuery] = js.undefined

  // Position editor when block becomes current
  Position.currentContainerChanged(
    containerCache = BlockCache.sectionGridCache,
    wasCurrent = (_: Block) ⇒
      rowEditor.hide(),
    becomesCurrent = (sectionGrid: Block) ⇒ {
      currentSectionGridOpt = sectionGrid.el

      // Position the editor
      sectionGridEditor.show()
      Position.offset(sectionGridEditor, new Position.Offset {
        // Use `.fr-body` left rather than the section left to account for sub-sections indentation
        override val left = Position.offset($(".fr-body")).left - sectionGridEditor.outerWidth()
        override val top  = sectionGrid.top - Position.scrollTop()
      })

      // Start by hiding all the icons
      sectionGridEditor.children().hide()

      // Update triggers relevance for section
      if (sectionGrid.el.is(BlockCache.SectionSelector)) {

        // Edit details and help are always visible
        sectionGridEditor.children(".fb-section-edit-details, .fb-section-edit-help").show()

        // Hide/show section move icons
        val container = sectionGrid.el.children(".fr-section-container")
        List("up", "right", "down", "left").foreach((direction) ⇒ {
            val relevant = container.hasClass("fb-can-move-" + direction)
            val trigger  = sectionGridEditor.children(".fb-section-move-" + direction)
            if (relevant) trigger.show()
        })

        // Hide/show delete icon
        val deleteTrigger = sectionGridEditor.children(".delete-section-trigger")
        if (container.is(".fb-can-delete")) deleteTrigger.show()
      }

      // Update triggers relevance for section
      if (sectionGrid.el.is(BlockCache.GridSelector)) {
        sectionGridEditor.children(".fb-grid-edit-details, .fb-grid-delete").show()
      }
    }
  )

  Position.currentContainerChanged(
    wasCurrent = (_: Block) ⇒ (),
    containerCache = BlockCache.cellCache,
    becomesCurrent = (cellBlock: Block) ⇒ {
      rowEditor.show()
      rowEditor.children().hide()

      val frGridEl      = cellBlock.el.closest(".fr-grid")
      val xblGridEl     = frGridEl.parent()
      val xblGridOffset = Position.offset(xblGridEl)

      def positionElWithClass(cssClass: String, topOffset: (JQuery) ⇒ Double): Unit = {
        val elem = rowEditor.children(cssClass)
        elem.show()
        Position.offset(
          el = elem,
          offset = new Position.Offset {
            override val left: Double = xblGridOffset.left
            override val top: Double = topOffset(elem)
          }
        )
      }

      positionElWithClass(".icon-chevron-up",   (_) ⇒ cellBlock.top)
      positionElWithClass(".icon-minus-sign",   (e) ⇒ cellBlock.top + cellBlock.height/2 - e.height()/2)
      positionElWithClass(".icon-chevron-down", (e) ⇒ cellBlock.top + cellBlock.height - e.height())
    }
  )

  // Hide editor when the pointer gets out of the Form Builder main area
  Position.currentContainerChanged(
    containerCache = BlockCache.fbMainCache,
    wasCurrent = (_: Block) ⇒ {
      sectionGridEditor.hide()
      rowEditor.hide()
      currentSectionGridOpt = js.undefined
    },
    becomesCurrent = (_: Block) ⇒ ( /* NOP */ )
  )
}
