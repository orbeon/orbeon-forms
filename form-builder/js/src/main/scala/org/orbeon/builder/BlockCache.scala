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
import scala.collection.compat._

case class Block(
  el     : JQuery,
  left   : Double,
  top    : Double,
  width  : Double,
  height : Double
)

object Block {
  def apply(elem: JQuery): Block = {
    val elemOffset = Position.adjustedOffset(elem)
    Block(
      el     = elem,
      left   = elemOffset.left,
      top    = elemOffset.top,
      width  = elem.outerWidth(),
      height = elem.outerHeight()
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
  val fbMainCache      = new BlockCache
  val cellCache        = new BlockCache

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
            e       <- collectElems(s"$SectionSelector:visible")
            section <- elemNotInSectionTemplateOpt(e).iterator
            // Mix of jQuery and Option is not pretty
            mostOuterSection =
              section.parents(SectionSelector)
                .last()
                .pipe(Option(_))
                .filter(_.is("*"))
                .getOrElse(section)
            // Handle both collapsible and non-collapsible title
            // https://github.com/orbeon/orbeon-forms/issues/3530
            titleAnchor = section.find(".fr-section-title .fr-section-label").find("a, .xforms-output-output")
            if titleAnchor.length > 0
          } yield
            Block(
              el     = section,
              left   = Position.adjustedOffset(mostOuterSection).left,
              top    = Position.adjustedOffset(section).top,
              width  = mostOuterSection.width(),
              height = titleAnchor.height()
            )

        val gridsIt =
          for {
            e    <- collectElems(s"$GridSelector:visible")
            grid <- elemNotInSectionTemplateOpt(e).iterator
          } yield
            Block(grid)

        sectionGridCache._elems = (gridsIt ++ sectionsIt).to(List)
      }

      locally {

        val gridBodiesIt =
          for (gridBody <- collectElems(s".fr-grid.fr-editable $GridBodySelector"))
            yield Block($(gridBody))

        gridBodyCache._elems = gridBodiesIt.to(List)
      }

      fbMainCache._elems = Block($(".fb-main-inner")) :: Nil

      locally {

        val cellsIt =
          for (cell <- collectElems(".fr-grid.fr-editable .fr-grid-td"))
            yield Block($(cell))

        cellCache._elems = cellsIt.to(List)
      }
    })

    def collectElems(selector: String): Iterator[dom.Element] =
      $(selector).toArray.iterator collect { case e: dom.Element => e }

    def elemNotInSectionTemplateOpt(domEl: dom.Element): Option[JQuery] = {
      val el = $(domEl)
      val parentSectionTemplate = el.parents(".fr-section-component")
      ! parentSectionTemplate.is("*") option el
    }
  }
}
