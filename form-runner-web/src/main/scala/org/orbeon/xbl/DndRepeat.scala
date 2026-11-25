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

import org.orbeon.facades.{Dragula, DragulaOptions, Drake}
import org.orbeon.fr.DropIndicatorDnD
import org.orbeon.fr.DropIndicatorDnD.RepeatDropIndicatorDnD
import org.orbeon.web.DomSupport.*
import org.orbeon.xforms.facade.XBLCompanion
import org.orbeon.xforms.{AjaxClient, AjaxEvent, EventNames, XFormsXbl}
import org.scalajs.dom
import org.scalajs.dom.html
import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits.*

import scala.scalajs.js
import scala.scalajs.js.UndefOr


// Companion for `fr:dnd-repeat`
object DndRepeat {

  val IsRepeatBeginEndSelector  = DropIndicatorDnD.RepeatBeginEndSelector
  val IsRepeatDelimiterSelector = ".xforms-repeat-delimiter"

  val IsNotGuSelector           = ":not(.gu-transit):not(.gu-mirror)"
  val IsDndItemSelector         = ".xforms-dnd-item"
  val IsDndMovesSelector        = ".xforms-dnd-moves"
  val IsDndLevelPrefix          = "xforms-dnd-level-"

  val FindDndLevelRe            = """^xforms-dnd-level-(\d+)$""".r

  private val IsDropIndicatorClass       = "fr-dnd-drop-indicator"
  private val IsActiveDndItemSelector    = s".xforms-dnd-item:not(.${DropIndicatorDnD.MirrorClass}):not(.xforms-disabled)"
  private val InvalidInteractiveSelector = ".fr-attachment-remove, .fr-attachment-zoom, a, button, input, select, textarea"

  XFormsXbl.declareCompanion("fr|dnd-repeat", js.constructorOf[DndRepeatCompanion])

  private class DndRepeatCompanion(containerElem: html.Element) extends XBLCompanion {

    // Legacy Dragula mode state
    case class DragState(
      currentDragStartPrev     : dom.Element,
      currentDragStartPosition : Int,
      excludedTargets          : List[dom.Element]
    )

    private var dragState        : Option[DragState] = None
    private var drake            : Option[Drake]     = None
    private var useDropIndicator : Boolean           = false

    private lazy val repeatLineDnd = new RepeatDropIndicatorDnD(
      repeatContainerOpt = () => Some(containerElem),
      formElem           = () => getXFormsFormOrThrow.elem,
      itemSelector       = IsActiveDndItemSelector,
      isItemMovable      = _.matches(IsDndMovesSelector),
      resolveHandleOpt   = (target, itemEl) =>
        if (target.closestOpt(InvalidInteractiveSelector).isDefined)
          None
        else if (itemEl.querySelectorOpt(".xforms-dnd-handle").isDefined)
          target.closestOpt(".xforms-dnd-handle").filter(itemEl.contains)
        else
          Some(itemEl)
    )

    override def init(): Unit =
      if (containerElem.hasClass(IsDropIndicatorClass)) {
        useDropIndicator = true
        repeatLineDnd.attach(containerElem)
      } else {
        useDropIndicator = false
        initDragula()
      }

    override def destroy(): Unit =
      if (useDropIndicator) {
        repeatLineDnd.detach(containerElem)
      } else {
        drake foreach (_.destroy())
        drake = None
      }

    private def initDragula(): Unit = {
      val newDrake =
        Dragula(
          js.Array(),
          new DragulaOptions {

            override val mirrorContainer: UndefOr[html.Element] = containerElem

            override val direction: UndefOr[String] =
              if (
                containerElem.closestOpt(".fr-attachment-image-preview, .fr-dnd-horizontal").isDefined ||
                Option(containerElem.getAttribute("dnd-direction")).contains("horizontal")
              )
                "horizontal"
              else
                js.undefined

            override def isContainer(el: html.Element): UndefOr[Boolean] =
              el eq containerElem

            override def moves(el: html.Element, source: html.Element, handle: html.Element, sibling: html.Element): UndefOr[Boolean] =
              el.matches(IsDndMovesSelector) && (
                el.querySelectorOpt(".xforms-dnd-handle").isEmpty ||
                ((handle ne null) && handle.closestOpt(".xforms-dnd-handle").isDefined)
              )

            override def invalid(el: html.Element, handle: html.Element): UndefOr[Boolean] =
              (handle ne null) && handle.closestOpt(InvalidInteractiveSelector).isDefined

            override def accepts(el: html.Element, target: html.Element, source: html.Element, sibling: html.Element): UndefOr[Boolean] =
              (sibling ne null)                  &&
                sibling.matches(IsNotGuSelector) && (
                  sibling.matches(IsDndItemSelector)         || // regular case
                  sibling.matches(IsRepeatDelimiterSelector) || // between repeat items when dragging past midpoint
                  sibling.matches(IsRepeatBeginEndSelector)     // at the end of the repeat see https://github.com/orbeon/orbeon-forms/issues/6000
                )                                &&
                (! dragState.exists(_.excludedTargets.exists(_ eq sibling)))
          }
        )

      newDrake.onDrag((el: dom.Element, source: html.Element) => {

        def findElemLevel(el: dom.Element): Option[Int] =
          el.classList collectFirst { case FindDndLevelRe(level) => level.toInt }

        val startLevelOpt = findElemLevel(el)

        val nextDndItemIt = el.nextElementSiblings(IsDndItemSelector).iterator

        val excludedTargets = startLevelOpt match {
          case Some(startLevel) => nextDndItemIt.takeWhile(e => findElemLevel(e).exists(_ > startLevel)).toList
          case None             => Nil
        }

        dragState = Some(
          DragState(
            currentDragStartPrev     = el.previousElementSiblingT,
            currentDragStartPosition = el.previousElementSiblings(IsDndItemSelector).length,
            excludedTargets          = excludedTargets
          )
        )
      })

      newDrake.onDragend((_: html.Element) => {
        dragState = None
      })

      newDrake.onDrop((el: html.Element, _: html.Element, _: html.Element, _: html.Element) => {
        dragState foreach { dragState =>

          val dndEnd   = el.previousElementSiblings(IsDndItemSelector).length
          val repeatId = el.previousElementSiblings(IsRepeatBeginEndSelector).next().id.substring("repeat-begin-".length)

          val beforeEl = dragState.currentDragStartPrev
          val dndStart = dragState.currentDragStartPosition

          if (dndStart != dndEnd) {

            AjaxClient.ajaxResponseReceivedForCurrentEventQueueF("dndrepeat") foreach { _ =>
              beforeEl.insertAdjacentElement("afterend", el)
            }

            AjaxClient.fireEvent(
              AjaxEvent(
                eventName  = EventNames.XXFormsDnD,
                targetId   = repeatId,
                form       = Some(getXFormsFormOrThrow.elem),
                properties = Map(
                  "dnd-start" -> (dndStart + 1),
                  "dnd-end"   -> (dndEnd + 1)
                )
              )
            )
          }
        }
      })

      drake = Some(newDrake)
    }
  }
}
