/**
  * Copyright (C) 2016 Orbeon, Inc.
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
package org.orbeon.builder

import org.scalajs.dom.html

import scala.scalajs.js

// Companion for `fb:dnd-repeat`
object DndRepeat {

  val RepeatDelimiterSelector  = ".xforms-repeat-delimiter"
  val RepeatBeginEndSelector   = ".xforms-repeat-begin-end"
  val NotRepeatClassesSelector = s":not($RepeatDelimiterSelector):not($RepeatBeginEndSelector)"
  val NotGuSelector            = ":not(.gu-transit):not(.gu-mirror)"
  val DndItemSelector          = ".xforms-dnd-item"
  val DndMovesSelector         = ".xforms-dnd-moves"
  val DndLevelPrefix           = "xforms-dnd-level-"

  val FindDndLevelRe           = """^xforms-dnd-level-(\d+)$""".r

  @js.native
  trait XBLCompanion extends js.Object {
    def container: html.Element = js.native
  }

  // See Dragula doc at https://github.com/bevacqua/dragula
  ORBEON.xforms.XBL.declareCompanion("fb|dnd-repeat",
    new js.Object {

      def containerElem = this.asInstanceOf[XBLCompanion].container

      case class DragState(
        currentDragStartPrev     : html.Element,
        currentDragStartPosition : Int,
        excludedTargets          : List[html.Element]
      )

      private var dragState: Option[DragState]     = None
      private var drake    : Option[Drake] = None

      def init(): Unit = {

        println(s" ${containerElem.className}  for ${containerElem.id} ")

        val newDrake =
          Dragula(
            js.Array(),
            new DragulaOptions {

              def isContainer(el: html.Element) =
                el eq containerElem

              def moves(el: html.Element, source: html.Element, handle: html.Element, sibling: html.Element) =
                $(el).is(DndMovesSelector)

              def accepts(el: html.Element, target: html.Element, source: html.Element, sibling: html.Element) = {

                val jSibling = $(sibling)

                (sibling ne null)              &&
                  jSibling.is(NotGuSelector)   &&
                  jSibling.is(DndItemSelector) &&
                  ! dragState.exists(_.excludedTargets.exists(_ eq sibling))
              }

              def invalid(el: html.Element, handle: html.Element) = false

              val direction                = "vertical"    // Y axis is considered when determining where an element would be dropped
              val copy                     = false         // elements are moved by default, not copied
              val copySortSource           = false         // elements in copy-source containers can be reordered
              val revertOnSpill            = false         // spilling will put the element back where it was dragged from, if this is true
              val removeOnSpill            = false         // spilling will `.remove` the element, if this is true
              val mirrorContainer          = containerElem
              val ignoreInputTextSelection = true          // allows users to select input text
            }
          )

        newDrake.onDrag((el: html.Element, source: html.Element) ⇒ {
          val jEl   = $(el)

          import org.orbeon.oxf.util.StringUtils._

          def findElemLevel(el: html.Element) =
            el.className.splitTo[List]() collectFirst { case FindDndLevelRe(level) ⇒ level.toInt }

          val startLevelOpt = findElemLevel(el)

          val nextAllItems = jEl.nextAll(DndItemSelector)

          val it =
            for (i ← 0 until nextAllItems.length iterator)
              yield nextAllItems(i)

          dragState = Some(
            DragState(
              currentDragStartPrev     = jEl.prev()(0),
              currentDragStartPosition = jEl.prevAll(DndItemSelector).length,
              excludedTargets          = startLevelOpt map (startLevel ⇒ it.takeWhile(e ⇒ findElemLevel(e) exists (_ > startLevel)) toList) getOrElse Nil
            )
          )
        })

        newDrake.onDragend((el: html.Element) ⇒ {
          dragState = None
        })

        newDrake.onDrop((el: html.Element, target: html.Element, source: html.Element, sibling: html.Element) ⇒ {
          dragState foreach { dragState ⇒

            val dndEnd     = $(el).prevAll(DndItemSelector).length
            val repeatId   = $(el).prevAll(RepeatBeginEndSelector).attr("id").get.substring("repeat-begin-".length)

            val beforeEl   = dragState.currentDragStartPrev
            val dndStart   = dragState.currentDragStartPosition

            if (dndStart != dndEnd) {

              println(s"moved from $dndStart to $dndEnd")

              lazy val moveBack: js.Function = () ⇒ {
                $(beforeEl).after(el)
                AjaxServer.ajaxResponseReceived.remove(moveBack)
              }

              // Restore order once we get an Ajax response back
              AjaxServer.ajaxResponseReceived.add(moveBack)

              // Thinking this should instead block input, but only after a while show a modal screen.
              // ORBEON.util.Utils.displayModalProgressPanel(ORBEON.xforms.Controls.getForm(container).id)

              ORBEON.xforms.Document.dispatchEvent(
                new js.Object {
                  val targetId  = repeatId
                  val eventName = "xxforms-dnd"
                  val properties = new js.Object {
                    val `dnd-start` = dndStart + 1
                    val `dnd-end`   = dndEnd + 1
                  }
                }
              )
            }
          }
        })

        drake = Some(newDrake)
      }

      def destroy(): Unit = {
        drake foreach (_.destroy())
        drake = None
      }
    }
  )
}
