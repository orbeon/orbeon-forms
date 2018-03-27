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

import org.orbeon.jquery.Offset
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.xforms.$
import org.scalajs.dom
import org.scalajs.jquery.JQuery

import scala.scalajs.js

object BlockCache {

  val SectionSelector  = ".xbl-fr-section"
  val GridSelector     = ".xbl-fr-grid"
  val GridBodySelector = ".fr-grid-body"

  class Block {

    val el: JQuery = null
    val left: Double = 0
    val top: Double = 0
    val width: Double = 0
    val height: Double = 0
    val titleOffset: Offset = null
    val rows: js.Array[Block] = js.Array()
    val cols: js.Array[Block] = js.Array()
  }

  val sectionGridCache     = js.Array[Block]()
  val gridBodyCache        = js.Array[Block]()
  val fbMainCache          = js.Array[Block]()
  val cellCache            = js.Array[Block]()

  def onExitFbMainOrOffsetMayHaveChanged(fn: () ⇒ Unit): Unit = {
    Position.onOffsetMayHaveChanged(fn)
    Position.currentContainerChanged(
      containerCache = BlockCache.fbMainCache,
      wasCurrent     = (_: Block) ⇒ fn(),
      becomesCurrent = (_: Block) ⇒ ()
    )
  }

  locally {

    // Keep caches current
    Position.onOffsetMayHaveChanged(() ⇒ {

      def ifNotInSectionTemplate(domEl: dom.Element, f: (JQuery ⇒ Unit)): Unit = {
        val el = $(domEl)
        val parentSectionTemplate = el.parents(".fr-section-component")
        if (! parentSectionTemplate.is("*"))
          f(el)
      }

      locally {
        sectionGridCache.length = 0
        $(".xbl-fr-section:visible").each((domSection: dom.Element) ⇒ {
          ifNotInSectionTemplate(domSection, (section) ⇒ {
            val mostOuterSection =
              section.parents(SectionSelector).last()
                .pipe(Option(_)).filter(_.is("*"))
                .getOrElse(section)

            // Handle both collapsible and non-collapsible title
            // https://github.com/orbeon/orbeon-forms/issues/3530
            val titleAnchor =
              section.find(".fr-section-title .fr-section-label").find("a, .xforms-output-output")

            // The section content might be empty when Form Builder optimizes non-relevant sections
            if (titleAnchor.length > 0) {
              sectionGridCache.unshift(new Block {
                override val el          = section
                override val top         = Position.adjustedOffset(section).top
                override val left        = Position.adjustedOffset(mostOuterSection).left
                override val height      = titleAnchor.height()
                override val width       = mostOuterSection.width()
                override val titleOffset = Offset(titleAnchor)
              })
            }
          })
        })
        $(s"$GridSelector:visible").each((grid: dom.Element) ⇒
          ifNotInSectionTemplate(grid, addToCache(sectionGridCache, _))
        )
      }

      locally {
        gridBodyCache.length = 0
        val gridBodies = $(".fr-grid.fr-editable .fr-grid-body")
        gridBodies.each((gridBody: dom.Element) ⇒ addToCache(gridBodyCache, $(gridBody)))
      }

      locally {
        fbMainCache.length = 0
        val fbMain = $(".fb-main-inner")
        addToCache(fbMainCache, fbMain)
      }

      locally {
        cellCache.length = 0
        val cells = $(".fr-grid.fr-editable .fr-grid-td")
        cells.each((cell: dom.Element) ⇒ addToCache(cellCache, $(cell)))
      }
    })

    def addToCache(cache: js.Array[Block], elem: JQuery): Unit = {
      val elemOffset = Position.adjustedOffset(elem)
      cache.unshift(new Block {
        override val el          = elem
        override val top         = elemOffset.top
        override val left        = elemOffset.left
        override val height      = elem.outerHeight()
        override val width       = elem.outerWidth()
        override val titleOffset = null
      })
    }
  }
}
