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
package org.orbeon.xbl

import org.orbeon.oxf.util.StringUtils._
import org.orbeon.xforms
import org.orbeon.xforms.facade.{AjaxServer, XBL, XBLCompanion}
import org.orbeon.xforms.$
import org.scalajs.dom.html.Element

import scala.scalajs.js
import scala.scalajs.js.UndefOr

// Companion for `fr:dnd-repeat`
object DndRepeat {

  val IsRepeatBeginEndSelector   = ".xforms-repeat-begin-end"
  val IsRepeatDelimiterSelector  = ".xforms-repeat-delimiter"
  val IsRepeatTemplateSelector   = ".xforms-repeat-template"

  val IsNotGuSelector            = ":not(.gu-transit):not(.gu-mirror)"
  val IsDndItemSelector          = ".xforms-dnd-item"
  val IsDndMovesSelector         = ".xforms-dnd-moves"
  val IsDndLevelPrefix           = "xforms-dnd-level-"

  val FindDndLevelRe           = """^xforms-dnd-level-(\d+)$""".r

  XBL.declareCompanion("fr|dnd-repeat",
    new XBLCompanion {

      case class DragState(
        currentDragStartPrev     : Element,
        currentDragStartPosition : Int,
        excludedTargets          : List[Element]
      )

      private var dragState : Option[DragState] = None
      private var drake     : Option[Drake]     = None

      override def init(): Unit = {

        val newDrake =
          Dragula(
            js.Array(),
            new DragulaOptions {

              override val mirrorContainer: UndefOr[Element] = containerElem

              def isContainer(el: Element): Boolean =
                el eq containerElem

              def moves(el: Element, source: Element, handle: Element, sibling: Element): Boolean =
                $(el).is(IsDndMovesSelector)

              def accepts(el: Element, target: Element, source: Element, sibling: Element): Boolean = {

                val jSibling = $(sibling)

                (sibling ne null)                &&
                  jSibling.is(IsNotGuSelector)   && (
                    jSibling.is(IsDndItemSelector)                || // regular case
                    jSibling.is(IsRepeatTemplateSelector)         || // at the end of the repeat
                    jSibling.next().is(IsRepeatDelimiterSelector)    // at the end of the repeat when there is an empty `<span>` (unclear, see Actions Editor)
                  )                              &&
                  ! dragState.exists(_.excludedTargets.exists(_ eq sibling))
              }

              def invalid(el: Element, handle: Element) = false
              def copy   (el: Element, source: Element) = false
            }
          )

        newDrake.onDrag((el: Element, source: Element) ⇒ {

          val jEl = $(el)

          def findElemLevel(el: Element) =
            el.className.splitTo[List]() collectFirst { case FindDndLevelRe(level) ⇒ level.toInt }

          val startLevelOpt = findElemLevel(el)

          val nextAllItems = jEl.nextAll(IsDndItemSelector)

          val nextDndItemIt =
            for (i ← 0 until nextAllItems.length iterator)
              yield nextAllItems(i)

          val excludedTargets = startLevelOpt match {
            case Some(startLevel) ⇒ nextDndItemIt.takeWhile(e ⇒ findElemLevel(e).exists(_ > startLevel)).to[List]
            case None             ⇒ Nil
          }

          dragState = Some(
            DragState(
              currentDragStartPrev     = jEl.prev()(0),
              currentDragStartPosition = jEl.prevAll(IsDndItemSelector).length,
              excludedTargets          = excludedTargets
            )
          )
        })

        newDrake.onDragend((el: Element) ⇒ {
          dragState = None
        })

        newDrake.onDrop((el: Element, target: Element, source: Element, sibling: Element) ⇒ {
          dragState foreach { dragState ⇒

            val dndEnd     = $(el).prevAll(IsDndItemSelector).length
            val repeatId   = $(el).prevAll(IsRepeatBeginEndSelector).attr("id").get.substring("repeat-begin-".length)

            val beforeEl   = dragState.currentDragStartPrev
            val dndStart   = dragState.currentDragStartPosition

            if (dndStart != dndEnd) {

              lazy val moveBack: js.Function = () ⇒ {
                $(beforeEl).after(el)
                // TODO: Fix this if we switch to `jquery-facade`
                AjaxServer.ajaxResponseReceived.asInstanceOf[js.Dynamic].remove(moveBack)
              }

              // Restore order once we get an Ajax response back
              // NOTE: You might think that we should wait for the specific response to the Ajax request corresponding to
              // the event below. However, we should move the element back to its original location before *any*
              // subsequent Ajax response is processed, because it might touch parts of the DOM which have been moved. So
              // doing this is probably the right thing to do.
              AjaxServer.ajaxResponseReceived.add(moveBack)

              // Thinking this should instead block input, but only after a while show a modal screen.
              // ORBEON.util.Utils.displayModalProgressPanel(ORBEON.xforms.Controls.getForm(container).id)

              xforms.DocumentAPI.dispatchEvent(
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

      override def destroy(): Unit = {
        drake foreach (_.destroy())
        drake = None
      }
    }
  )
}