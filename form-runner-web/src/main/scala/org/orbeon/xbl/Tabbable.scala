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
import org.orbeon.web.DomSupport.*
import org.orbeon.xbl.DndRepeat.*
import org.orbeon.xforms.*
import org.orbeon.xforms.KeyboardShortcuts.KeyBoardIconCharacter
import org.orbeon.xforms.facade.{XBL, XBLCompanion}
import org.scalajs.dom
import org.scalajs.dom.html
import org.scalajs.dom.html.Element
import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits.*

import scala.scalajs.js
import scala.scalajs.js.UndefOr


object Tabbable {

  private val ActiveClass         = "active"

  private val ExcludeNotVisible   = ":not(.xforms-repeat-delimiter, .xforms-repeat-begin-end, .xforms-group-begin-end, .fr-tabbable-add, .xforms-hidden)"
  private val NavTabsSelector     = ".nav-tabs"
  private val TabPaneSelector     = ".tab-pane:not(.xforms-disabled)"
  private val TabContentSelector  = ".tab-content"
  private val ActiveSelector      = s".$ActiveClass"

  XBL.declareCompanion("fr|tabbable", js.constructorOf[TabbableCompanion])

  private class TabbableCompanion(containerElem: html.Element) extends XBLCompanion {

    private case class DragState(
      currentDragStartPrev     : Element,
      currentDragStartPosition : Int
    )

    private var dragState : Option[DragState] = None
    private var drake     : Option[Drake]     = None
    private val eventListenerSupport = new EventListenerSupport {}

    override def init(): Unit = {
      eventListenerSupport.addListener(dom.document , "keydown", onDOMKeydown)
      eventListenerSupport.addListener(containerElem, "focus"  , onDOMFocus, useCapture = true)

      if (containerElem.classList.contains("fr-tabbable-dnd")) {

        val firstRepeatContainer = containerElem.querySelectorT(NavTabsSelector)

        val newDrake =
          Dragula(
            js.Array(firstRepeatContainer),
            new DragulaOptions {

              override val mirrorContainer: UndefOr[Element] = firstRepeatContainer

              // Only elements in drake.containers will be taken into account
              override def isContainer(el: html.Element) = false

              override def moves(el: html.Element, source: html.Element, handle: html.Element, sibling: html.Element) = {
                (
                    el.previousElementOpt.exists(_.matches(IsRepeatDelimiterSelector)) ||
                    el.previousElementOpt.flatMap(_.previousElementOpt).exists(_.matches(IsRepeatDelimiterSelector))
                ) &&
                    el.matches(IsDndMovesSelector)
              }

              override def accepts(el: html.Element, target: html.Element, source: html.Element, sibling: html.Element) =
                sibling != null && sibling.matches(IsNotGuSelector)
            }
          )

        newDrake.onDrag((el: html.Element, source: html.Element) =>
          dragState = Some(
            DragState(
              currentDragStartPrev     = el.previousElementOpt.orNull,
              currentDragStartPosition = el.previousElementSiblings(IsDndMovesSelector + ExcludeNotVisible).size
            )
          )
        )

        newDrake.onDragend((el: html.Element) =>
          dragState = None
        )

        // This is almost identical in `DndRepeat`. Should remove code duplication.
        newDrake.onDrop((el: html.Element, target: html.Element, source: html.Element, sibling: html.Element) => {
          dragState foreach { dragState =>

            val dndEnd = el.previousElementSiblings(IsDndMovesSelector + ExcludeNotVisible).size

            val repeatId =
              el.previousElementSiblings(IsRepeatBeginEndSelector)
                .nextOption()
                .map(_.id.substring("repeat-begin-".length))
                .getOrElse(throw new IllegalStateException)

            val beforeEl = dragState.currentDragStartPrev
            val dndStart = dragState.currentDragStartPosition

            if (dndStart != dndEnd) {

              // Restore order once we get an Ajax response back
              // NOTE: You might think that we should wait for the specific response to the Ajax request corresponding to
              // the event below. However, we should move the element back to its original location before *any*
              // subsequent Ajax response is processed, because it might touch parts of the DOM which have been moved. So
              // doing this is probably the right thing to do.
              AjaxClient.ajaxResponseReceivedForCurrentEventQueueF("tabbable") foreach { _ =>
                beforeEl.after(el)
              }

              // Thinking this should instead block input, but only after a while show a modal screen.
              // XFormsUI.displayModalProgressPanel(ORBEON.xforms.Controls.getForm(companion.container).id)

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
      }

      // Select first visible tab
      // https://github.com/orbeon/orbeon-forms/issues/3458
      selectTab(0)

      // 2016-10-13: We use our own logic to show/hide tabs based on position as we want to be able to
      // support dynamically repeated tabs.

      eventListenerSupport.addListener(containerElem, "click", (event: dom.Event) => {
        event.targetT
          .closestOpt("[data-toggle = 'tabbable']")
          .filter(containerElem.contains)
          .foreach { matchedElem =>

            event.preventDefault()  // don't allow anchor navigation
            event.stopPropagation() // prevent ancestor tab handlers from running

            val newLi = matchedElem.parentElement

            if (newLi.matches(ExcludeNotVisible) && ! newLi.matches(ActiveSelector)) {
              val tabPosition = newLi.previousElementSiblings(ExcludeNotVisible).size
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

    def findAllTabPanes: collection.Seq[html.Element] =
      containerElem
        .childrenT
        .flatMap(_.childrenT(TabContentSelector))
        .flatMap(_.childrenT(TabPaneSelector + ExcludeNotVisible))

    private def onDOMFocus(event: dom.FocusEvent): Unit =
      onFocus(event.targetT)

    // noinspection ScalaWeakerAccess (called from `tabbable.xbl`)
    def onFocus(targetElem: Element): Unit = {
      val allTabPanes = findAllTabPanes
      allTabPanes.indexWhere(_.contains(targetElem)) match {
        case -1    =>
        case index => selectTab(index)
      }
    }

    // noinspection ScalaWeakerAccess (called from `tabbable.xbl`)
    def selectTab(tabPosition: Int): Unit = {

      // Ignore if we get this after the dialog closed
      if (containerElem.matches(".xforms-disabled")) return

      // Ignore out of bound tab positions
      val allLis = getAllLis
      if (tabPosition < 0              ) return
      if (tabPosition > allLis.size - 1) return

      // Update keyboard shortcuts tooltips
      if (isOutermostTabbable) {
        val isAppleOs            = KeyboardShortcuts.isAppleOs
        def addIcon(kbd: String) = s"$KeyBoardIconCharacter $kbd"
        val nextTabShortcut      = addIcon("Ctrl + }")
        val previousTabShortcut  = addIcon("Ctrl + {")
        def setTooltip(position: Int, title: String): Unit =
          allLis.lift(position).foreach(_.querySelectorOpt("a").foreach { aElem =>
            aElem.setAttribute("title", title)
            $(aElem).asInstanceOf[js.Dynamic].tooltip()
          })

        allLis.foreach(_.querySelectorAllT("a").foreach($(_).asInstanceOf[js.Dynamic].tooltip("destroy")))
        setTooltip(tabPosition + 1, nextTabShortcut)
        setTooltip(tabPosition - 1, previousTabShortcut)
      }

      // Switch tab
      val newLi = allLis(tabPosition)
      if (! newLi.matches(ActiveSelector)) {
        val allTabPanes = newLi.closestT(NavTabsSelector).nextElementSiblings.flatMap(_.childrenT(TabPaneSelector)).filter(_.matches(ExcludeNotVisible)).toList
        val newTabPane  = allTabPanes(tabPosition)
        allLis.foreach(_.classList.remove(ActiveClass))
        allTabPanes.foreach(_.classList.remove(ActiveClass))
        newLi.classList.add(ActiveClass)
        newTabPane.classList.add(ActiveClass)
      }
    }

    // noinspection ScalaWeakerAccess (called from `tabbable.xbl`)
    def maybeAdjustCurrentTab(): Unit = {
      val tabsCount       = getAllLis.size
      val currentTabIndex = getCurrentTabIndex
      if (currentTabIndex < 0            ) selectTab(0)
      if (currentTabIndex > tabsCount - 1) selectTab(tabsCount - 1)
    }

    private def getAllLis: collection.Seq[Element] =
      containerElem.querySelectorAllT(s":scope > div > .nav-tabs > $ExcludeNotVisible")

    private def onDOMKeydown(event: dom.KeyboardEvent): Unit = {
      // Only handle keyboard shortcuts on the outermost tabbable
      if (isOutermostTabbable) {
        if (event.ctrlKey && !event.altKey) {
            event.key match {
              case "}" =>
                event.preventDefault()
                moveToNextTab()
              case "{" =>
                event.preventDefault()
                moveToPreviousTab()
              case _ =>
            }
          }
      }
    }

    private def isOutermostTabbable: Boolean =
      containerElem.matches(
        // Either no dialog open, or inside an open dialog
        ":is(:root:not(:has(dialog[open])), dialog[open]) " +
        // A tabbable that is not inside another tabbable
        ".xbl-fr-tabbable:not(.xbl-fr-tabbable .xbl-fr-tabbable)"
      )

    private def getCurrentTabIndex: Int =
      getAllLis.indexWhere(_.matches(ActiveSelector))

    private def moveToNextTab(): Unit = {
      val currentIndex = getCurrentTabIndex
      if (currentIndex < getAllLis.size - 1)
        selectTab(currentIndex + 1)
    }

    private def moveToPreviousTab(): Unit = {
      val currentIndex = getCurrentTabIndex
      if (currentIndex > 0)
        selectTab(currentIndex - 1)
    }
  }
}
