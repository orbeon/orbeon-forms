/**
  * Copyright (C) 2018 Orbeon, Inc.
  *
  * This program is free software; you can redistribute it and/or modify it under the terms of the
  * GNU Lesser General Public License as published by the Free Software Foundation; either version
  *  2.1 of the License, or (at your option) any later version.
  *
  * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  * See the GNU Lesser General Public License for more details.
  *
  * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
  */
package org.orbeon.xbl

import org.orbeon.facades.{Dragula, DragulaOptions, Drake}
import org.orbeon.xbl.DndRepeat.*
import org.orbeon.xforms.facade.{XBL, XBLCompanion}
import org.orbeon.xforms.{$, AjaxClient, AjaxEvent, EventListenerSupport, EventNames, Page}
import org.scalajs.dom.html
import org.scalajs.dom.html.Element
import io.udash.wrappers.jquery.{JQuery, JQueryEvent}
import org.scalajs.dom
import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits.*

import scala.scalajs.js
import scala.scalajs.js.UndefOr


object Tabbable {

  val ActiveClass                  = "active"

  val ExcludeRepeatClassesSelector = ":not(.xforms-repeat-delimiter):not(.xforms-repeat-begin-end):not(.fr-tabbable-add)"
  val NavTabsSelector              = ".nav-tabs"
  val TabPaneSelector              = ".tab-pane"
  val TabContentSelector           = ".tab-content"
  val ActiveSelector               = s".$ActiveClass"

  XBL.declareCompanion("fr|tabbable", js.constructorOf[TabbableCompanion])

  private class TabbableCompanion(containerElem: html.Element) extends XBLCompanion {

    case class DragState(
      currentDragStartPrev     : Element,
      currentDragStartPosition : Int
    )

    private var dragState : Option[DragState] = None
    private var drake     : Option[Drake]     = None
    private val eventListenerSupport = new EventListenerSupport {}

    override def init(): Unit = {

      if (containerElem.classList.contains("fr-tabbable-dnd")) {

        val firstRepeatContainer = containerElem.querySelector(NavTabsSelector).asInstanceOf[html.Element]

        val newDrake =
          Dragula(
            js.Array(firstRepeatContainer),
            new DragulaOptions {

              override val mirrorContainer: UndefOr[Element] = firstRepeatContainer

              // Only elements in drake.containers will be taken into account
              override def isContainer(el: Element) = false

              override def moves(el: Element, source: Element, handle: Element, sibling: Element) = {
                val jEl = $(el)
                (
                    jEl.prev().is(IsRepeatDelimiterSelector) ||
                    jEl.prev().prev().is(IsRepeatDelimiterSelector)
                ) &&
                    jEl.is(IsDndMovesSelector)
              }

              override def accepts(el: Element, target: Element, source: Element, sibling: Element) =
                sibling != null && $(sibling).is(IsNotGuSelector)
            }
          )

        newDrake.onDrag((el: Element, source: Element) => {

          val jEl = $(el)

          dragState = Some(
            DragState(
              currentDragStartPrev     = jEl.prev().get(0).get.asInstanceOf[html.Element],
              currentDragStartPosition = jEl.prevAll(IsDndMovesSelector + ExcludeRepeatClassesSelector).length
            )
          )
        })

        newDrake.onDragend((el: Element) => {
          dragState = None
        })

        // This is almost identical in `DndRepeat`. Should remove code duplication.
        newDrake.onDrop((el: Element, target: Element, source: Element, sibling: Element) => {
          dragState foreach { dragState =>

            val jEl = $(el)

            val dndEnd = jEl.prevAll(IsDndMovesSelector + ExcludeRepeatClassesSelector).length

            val repeatId = jEl.prevAll(IsRepeatBeginEndSelector).attr("id").get.substring("repeat-begin-".length)

            val beforeEl = dragState.currentDragStartPrev
            val dndStart = dragState.currentDragStartPosition

            if (dndStart != dndEnd) {

              // Restore order once we get an Ajax response back
              // NOTE: You might think that we should wait for the specific response to the Ajax request corresponding to
              // the event below. However, we should move the element back to its original location before *any*
              // subsequent Ajax response is processed, because it might touch parts of the DOM which have been moved. So
              // doing this is probably the right thing to do.
              AjaxClient.ajaxResponseReceivedForCurrentEventQueueF("tabbable") foreach { _ =>
                $(beforeEl).after(el)
              }

              // Thinking this should instead block input, but only after a while show a modal screen.
              // XFormsUI.displayModalProgressPanel(ORBEON.xforms.Controls.getForm(companion.container).id)

              AjaxClient.fireEvent(
                AjaxEvent(
                  eventName  = EventNames.XXFormsDnD,
                  targetId   = repeatId,
                  form       = Page.findXFormsForm(containerElem).map(_.elem),
                  properties = Map(
                    "dnd-start" -> (dndStart + 1),
                    "dnd-end"   -> (dndEnd + 1)
                  )
                )
              )
            }
          }
        })
      }

      // Select first tab
      // https://github.com/orbeon/orbeon-forms/issues/3458
      selectTab(0)

      // 2016-10-13: We use our own logic to show/hide tabs based on position as we want to be able to
      // support dynamically repeated tabs.

      eventListenerSupport.addListener(containerElem, "click", (e: dom.Event) => {
        val targetElem  = e.target.asInstanceOf[dom.Element]
        val matchedElem = targetElem.closest("[data-toggle = 'tabbable']")
        if (matchedElem != null && containerElem.contains(matchedElem)) {
          e.preventDefault()  // don't allow anchor navigation
          e.stopPropagation() // prevent ancestor tab handlers from running

          val newLi = $(matchedElem).parent(ExcludeRepeatClassesSelector)

          if (! newLi.is(ActiveSelector)) {
            val tabPosition = newLi.prevAll(ExcludeRepeatClassesSelector).length
            selectTab(tabPosition)
          }
        }
      })
    }

    override def destroy(): Unit = {
      eventListenerSupport.clearAllListeners()
      drake foreach (_.destroy())
      drake = None
    }

    def findAllTabPanes: JQuery =
      $(containerElem).children().children(TabContentSelector).children(TabPaneSelector + ExcludeRepeatClassesSelector)

    // Called from XBL component
    def selectTabForPane(targetElem: html.Element): Unit = {

      val allTabPanes            = findAllTabPanes
      val ancestorOrSelfTabPanes = $(targetElem).parents(TabPaneSelector).add(targetElem) // exact order doesn't matter
      val intersectionPane       = allTabPanes.filter(ancestorOrSelfTabPanes)

      val index = allTabPanes.index(intersectionPane)
      if (index < 0)
          return

      selectTab(index)
    }

    // Called from XBL component
    def selectTab(tabPosition: Int): Unit = {

      if (tabPosition < 0)
          return

      val allLis = $(containerElem).find("> div > .nav-tabs").children(ExcludeRepeatClassesSelector)
      if (tabPosition > allLis.length - 1)
          return

      val newLi = allLis.at(tabPosition)
      if (newLi.is(ActiveSelector))
          return

      val allTabPanes = newLi.closest(NavTabsSelector).nextAll().children(TabPaneSelector).filter(ExcludeRepeatClassesSelector)
      val newTabPane  = allTabPanes.at(tabPosition)

      allLis.removeClass(ActiveClass)
      allTabPanes.removeClass(ActiveClass)

      newLi.addClass(ActiveClass)
      $(newTabPane).addClass(ActiveClass)
    }
  }
}
