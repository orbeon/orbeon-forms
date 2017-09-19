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

import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.xforms.$
import org.scalajs.dom
import org.scalajs.jquery.JQuery

import scala.scalajs.js
import scala.scalajs.js.annotation.{JSExportAll, JSExportTopLevel, ScalaJSDefined}

@JSExportTopLevel("ORBEON.builder.BlockCache")
@JSExportAll
object BlockCache {

  @ScalaJSDefined
  class Block extends js.Object {

    val el: JQuery = null
    val left: Double = 0
    val top: Double = 0
    val width: Double = 0
    val height: Double = 0
    val titleOffset: Position.Offset = null
    val rows: js.Array[Block] = js.Array()
    val cols: js.Array[Block] = js.Array()
  }

  val gridSectionCache = js.Array[Block]()
  val fbMainCache      = js.Array[Block]()
  val cellCache        = js.Array[Block]()

  // Keep `gridSectionCache` and `fbMainCache` current
  Position.onOffsetMayHaveChanged(() ⇒ {

    locally {
      gridSectionCache.length = 0
      $(".xbl-fr-section:visible").each((domSection: dom.Element) ⇒ {
        val section = $(domSection)

        val mostOuterSection =
          section.parents(".xbl-fr-section").last()
            .pipe(Option(_)).filter(_.is("*"))
            .getOrElse(section)

        val titleAnchor = section.find("a")

        gridSectionCache.unshift(new Block {
          override val el          = section
          override val top         = Position.adjustedOffset(section).top
          override val left        = Position.adjustedOffset(mostOuterSection).left
          override val height      = titleAnchor.height()
          override val width       = mostOuterSection.width()
          override val titleOffset = Position.offset(titleAnchor)
        })
      })
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

  private def addToCache(cache: js.Array[Block], elem: JQuery): Unit = {
    val elemOffset = Position.adjustedOffset(elem)
    cellCache.unshift(new Block {
      override val el          = elem
      override val top         = elemOffset.top
      override val left        = elemOffset.left
      override val height      = elem.height()
      override val width       = elem.width()
      override val titleOffset = null
    })
  }

}
