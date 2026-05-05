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

import org.orbeon.oxf.util.CollectionUtils.*
import org.orbeon.web.DomSupport.*
import org.scalajs.dom
import org.scalajs.dom.html


case class Block(
  el     : html.Element,
  left   : Double,
  top    : Double,
  width  : Double,
  height : Double
)

object Block {
  def apply(elem: html.Element): Block = {
    val elemOffset = Position.adjustedOffset(elem)
    Block(
      el     = elem,
      left   = elemOffset.left,
      top    = elemOffset.top,
      width  = elem.outerWidth,
      height = elem.outerHeight
    )
  }
}

class BlockCache {
  private var _elems: List[Block] = Nil
  def elems: List[Block] = _elems
}

object BlockCache {

  val SectionSelector  = ".xbl-fr-section"
  val GridSelector     = ".xbl-fr-grid"
  val GridBodySelector = ".fr-grid-body"

  val sectionGridCache = new BlockCache
  val gridBodyCache    = new BlockCache
  val cellCache        = new BlockCache

  private val fbMainCache = new BlockCache

  def onExitFbMainOrOffsetMayHaveChanged(fn: () => Unit): Unit = {
    Position.onOffsetMayHaveChanged(fn)
    Position.currentContainerChanged(
      containerCache = BlockCache.fbMainCache,
      wasCurrent     = (_: Block) => fn(),
      becomesCurrent = (_: Block) => ()
    )
  }

  locally {

    // Keep caches current
    Position.onOffsetMayHaveChanged(() => {

      locally {

        val sectionsIt =
          for {
            section <- collectElems(SectionSelector)
            if section.isVisible && ! isElemInSectionTemplate(section)
            outermostSection =
              section.ancestorOrSelfElem(SectionSelector, includeSelf = false)
                .lastOption()
                .getOrElse(section)
            // Handle both collapsible and non-collapsible title
            // https://github.com/orbeon/orbeon-forms/issues/3530
            titleAnchor <- section.querySelectorOpt(".fr-section-title .fr-section-label")
          } yield
            Block(
              el     = section,
              left   = Position.adjustedOffset(outermostSection).left,
              top    = Position.adjustedOffset(section).top,
              width  = outermostSection.contentWidthOrZero,
              height = titleAnchor.contentHeightOrZero
            )

        val gridsIt =
          for {
            grid <- collectElems(GridSelector).iterator
            if grid.isVisible && ! isElemInSectionTemplate(grid)
          } yield
            Block(grid)

        sectionGridCache._elems = (gridsIt ++ sectionsIt).toList
      }

      locally {

        val gridBodiesIt =
          for (gridBody <- collectElems(s".fr-grid.fr-editable $GridBodySelector"))
            yield Block(gridBody)

        gridBodyCache._elems = gridBodiesIt.toList
      }

      locally {

        val cellsIt =
        for (cell <- collectElems(".fr-grid.fr-editable .fr-grid-td"))
        yield Block(cell)

        cellCache._elems = cellsIt.toList
      }

      fbMainCache._elems = Block(collectElems(".fb-main-inner").head) :: Nil
    })

    def collectElems(selector: String): collection.Seq[html.Element] =
      dom.window.document.querySelectorAllT(selector)

    def isElemInSectionTemplate(elem: html.Element): Boolean =
      elem
        .ancestorOrSelfElem(".fr-section-component", includeSelf = false)
        .nextOption()
        .nonEmpty
  }
}
