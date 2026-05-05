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

import autowire.*
import enumeratum.EnumEntry.Hyphencase
import enumeratum.*
import org.orbeon.builder.rpc.FormBuilderRpcApi
import org.orbeon.datatypes.Direction
import org.orbeon.xforms.rpc.RpcClient
import org.scalajs.dom
import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits.*
import org.orbeon.fr.FormRunnerUtils
import org.orbeon.web.DomSupport
import org.orbeon.web.DomSupport.*
import org.scalajs.dom.html


object SectionGridEditor {

  def sectionGridEditorContainer : html.Element  = dom.document.querySelectorT(".fb-section-grid-editor")
  var currentSectionGridOpt      : Option[Block] = None

  private def allIcons: collection.Seq[html.Element] =
    sectionGridEditorContainer.querySelectorAllT(".fa")

  private def allIconsWith(selector: String): collection.Seq[html.Element] =
    sectionGridEditorContainer.querySelectorAllT(selector)

  locally {

    sealed trait ContainerEditor extends EnumEntry with Hyphencase {
      def className = s".fb-$entryName"
    }

    object ContainerEditor extends Enum[ContainerEditor] {
      val values = findValues
      case object ContainerMoveUp      extends ContainerEditor
      case object ContainerMoveDown    extends ContainerEditor
      case object ContainerMoveRight   extends ContainerEditor
      case object ContainerMoveLeft    extends ContainerEditor
      case object ContainerDelete      extends ContainerEditor
      case object ContainerEditDetails extends ContainerEditor
      case object ContainerCopy        extends ContainerEditor
      case object ContainerCut         extends ContainerEditor
      case object ContainerMerge       extends ContainerEditor
    }

    import ContainerEditor.*

    // Position editor when block becomes current
    Position.currentContainerChanged(
      containerCache = BlockCache.sectionGridCache,
      wasCurrent = (sectionGridBody: Block) => {
        if (sectionGridBody.el.matches(BlockCache.GridSelector))
          sectionGridBody.el.childrenT(".fr-grid").foreach(_.classList.remove("fb-hover"))
      },
      becomesCurrent = (sectionGridBody: Block) => {
        currentSectionGridOpt = Some(sectionGridBody)
        val isViewMode        = FormRunnerUtils.isViewMode(sectionGridBody.el)

        // Position the editor
        sectionGridEditorContainer.show()
        sectionGridEditorContainer.setOffset(
          DomSupport.Offset(
            // Use `.fr-body` left rather than the section left to account for sub-sections indentation
            left = dom.document.querySelectorT(".fr-body").getOffset.left - sectionGridEditorContainer.outerWidth,
            top  = sectionGridBody.top - Position.scrollTop
          )
        )

        // Start by hiding all the icons
        allIcons.foreach(_.hide())

        def showMoveDeleteIcons(container: html.Element): Unit = {

          Direction.values foreach { direction =>
            val relevant = ! isViewMode && container.hasClass("fb-can-move-" + direction.entryName.toLowerCase)
            val trigger  = allIconsWith(".fb-container-move-" + direction.entryName.toLowerCase)
            if (relevant)
              trigger.foreach(_.show())
          }

          if (! isViewMode && container.matches(".fb-can-delete"))
            Set(ContainerDelete, ContainerCut)
              .map(_.className)
              .flatMap(allIconsWith)
              .foreach(_.show())
        }

        // Edit and copy icons
        allIconsWith(ContainerEditDetails.className).foreach(_.show())
        if (! isViewMode)
          allIconsWith(ContainerCopy.className).foreach(_.show())

        // Sections
        if (sectionGridBody.el.matches(BlockCache.SectionSelector)) {
          val container = sectionGridBody.el.childrenT(".fr-section-container").head
          showMoveDeleteIcons(container)
          if (! isViewMode && container.queryNestedElems(".fr-section-component", includeSelf = false).nonEmpty)
            allIconsWith(ContainerMerge.className).foreach(_.show())
        }

        // Grids
        if (sectionGridBody.el.matches(BlockCache.GridSelector)) {
          val container = sectionGridBody.el.childrenT(".fr-grid").head
          showMoveDeleteIcons(container)
          container.classList.add("fb-hover")
        }
      }
    )

    BlockCache.onExitFbMainOrOffsetMayHaveChanged { () =>
      sectionGridEditorContainer.hide()
      currentSectionGridOpt = None
    }

    // Register listener on editor icons
    ContainerEditor.values foreach { editor =>

      allIconsWith(s".fb-${editor.entryName}")
        .foreach(_.addEventListener("click", (_: dom.Event) => {
        currentSectionGridOpt foreach { currentSectionGrid =>

          val sectionGridId = currentSectionGrid.el.id
          val client        = RpcClient[FormBuilderRpcApi]

          editor match {
            case ContainerMoveUp      => client.sectionMove         (sectionGridId, Direction.Up.entryName).call()
            case ContainerMoveDown    => client.sectionMove         (sectionGridId, Direction.Down.entryName).call()
            case ContainerMoveRight   => client.sectionMove         (sectionGridId, Direction.Right.entryName).call()
            case ContainerMoveLeft    => client.sectionMove         (sectionGridId, Direction.Left.entryName).call()
            case ContainerDelete      => client.containerDelete     (sectionGridId).call()
            case ContainerEditDetails => client.containerEditDetails(sectionGridId).call()
            case ContainerCopy        => client.containerCopy       (sectionGridId).call()
            case ContainerCut         => client.containerCut        (sectionGridId).call()
            case ContainerMerge       => client.containerMerge      (sectionGridId).call()
          }
        }
      }))
    }
  }
}
