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
object SideEditor {

  lazy val sectionEditor = $(".fb-section-editor")
  var currentSectionOpt: js.UndefOr[JQuery] = js.undefined

  // Position editor when block becomes current
  Position.currentContainerChanged(
    containerCache = BlockCache.gridSectionCache,
    wasCurrent = (section: Block) ⇒
      // NOP, instead we hide the section editor when the pointer leaves `.fb-main`
      (),
    becomesCurrent = (section: Block) ⇒ {
      currentSectionOpt = section.el

      // Position the editor
      sectionEditor.show()
      Position.offset(sectionEditor, new Position.Offset {
        // Use `.fr-body` left rather than the section left to account for sub-sections indentation
        override val left = Position.offset($(".fr-body")).left - sectionEditor.outerWidth()
        override val top  = section.top - Position.scrollTop()
      })

      // Update triggers relevance
      val container = section.el.children(".fr-section-container")
      // Hide/show section move icons
      List("up", "right", "down", "left").foreach((direction) ⇒ {
          val relevant = container.hasClass("fb-can-move-" + direction)
          val trigger = sectionEditor.children(".fb-section-move-" + direction)
          if (relevant) trigger.show() else trigger.hide()
      })

      // Hide/show delete icon
      val deleteTrigger = sectionEditor.children(".delete-section-trigger")
      if (container.is(".fb-can-delete")) deleteTrigger.show() else deleteTrigger.hide()
    }
  )

  // Hide editor when the pointer gets out of the Form Builder main area
  Position.currentContainerChanged(
    containerCache = BlockCache.fbMainCache,
    wasCurrent = (section: Block) ⇒ {
      sectionEditor.hide()
      currentSectionOpt = js.undefined
    },
    becomesCurrent = (section: Block) ⇒ ( /* NOP */ )
  )

}
