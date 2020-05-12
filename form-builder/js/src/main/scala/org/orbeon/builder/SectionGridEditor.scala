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

import autowire._
import enumeratum.EnumEntry.Hyphencase
import enumeratum._
import io.circe.generic.auto._
import org.orbeon.builder.rpc.FormBuilderRpcApi
import org.orbeon.datatypes.Direction
import org.orbeon.jquery.Offset
import org.orbeon.oxf.util.CoreUtils.asUnit
import org.orbeon.xforms._
import org.orbeon.xforms.rpc.RpcClient
import org.scalajs.jquery.JQuery

import scala.concurrent.ExecutionContext.Implicits.global

object SectionGridEditor {

  lazy val sectionGridEditorContainer : JQuery        = $(".fb-section-grid-editor")
  var currentSectionGridOpt           : Option[Block] = None

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

    import ContainerEditor._

    val SectionGridAlwaysVisibleIcons =
      List(
        ContainerEditDetails,
        ContainerCopy
      )

    // Position editor when block becomes current
    Position.currentContainerChanged(
      containerCache = BlockCache.sectionGridCache,
      wasCurrent = (sectionGridBody: Block) => {
        if (sectionGridBody.el.is(BlockCache.GridSelector))
          sectionGridBody.el.children(".fr-grid").removeClass("fb-hover")
      },
      becomesCurrent = (sectionGridBody: Block) => {
        currentSectionGridOpt = Some(sectionGridBody)

        // Position the editor
        sectionGridEditorContainer.show()
        Offset.offset(
          sectionGridEditorContainer,
          Offset(
            // Use `.fr-body` left rather than the section left to account for sub-sections indentation
            left = Offset($(".fr-body")).left - sectionGridEditorContainer.outerWidth(),
            top  = sectionGridBody.top - Position.scrollTop()
          )
        )

        // Start by hiding all the icons
        sectionGridEditorContainer.children().hide()

        def showContainerIcons(container: JQuery): Unit = {

           Direction.values foreach { direction =>

            val relevant = container.hasClass("fb-can-move-" + direction.entryName.toLowerCase)
            val trigger  = sectionGridEditorContainer.children(".fb-container-move-" + direction.entryName.toLowerCase)

            if (relevant)
              trigger.show()
          }

          if (container.is(".fb-can-delete"))
            Set(ContainerDelete, ContainerCut)
              .map(_.className)
              .map(sectionGridEditorContainer.children(_))
              .foreach(_.show())
        }

        // Icons which are always visible
        sectionGridEditorContainer.children(SectionGridAlwaysVisibleIcons map (_.className) mkString ",").show()

        // Sections
        if (sectionGridBody.el.is(BlockCache.SectionSelector)) {

          // Hide/show section move icons
          val container = sectionGridBody.el.children(".fr-section-container")
          showContainerIcons(container)

          if (container.find(".fr-section-component").length > 0)
            sectionGridEditorContainer.children(ContainerMerge.className).show()
        }

        // Grids
        if (sectionGridBody.el.is(BlockCache.GridSelector)) {

          val container = sectionGridBody.el.children(".fr-grid")
          showContainerIcons(container)

          container.addClass("fb-hover")
        }
      }
    )

    BlockCache.onExitFbMainOrOffsetMayHaveChanged { () =>
      sectionGridEditorContainer.hide()
      currentSectionGridOpt = None
    }

    // Register listener on editor icons
    ContainerEditor.values foreach { editor =>

      val iconEl = sectionGridEditorContainer.children(s".fb-${editor.entryName}")

      iconEl.on("click.orbeon.builder.section-grid-editor", () => asUnit {
        currentSectionGridOpt foreach { currentSectionGrid =>

          val sectionGridId = currentSectionGrid.el.attr("id").get
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
      })
    }
  }
}
