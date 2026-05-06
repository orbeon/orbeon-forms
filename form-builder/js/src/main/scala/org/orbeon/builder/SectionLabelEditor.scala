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
import org.orbeon.builder.rpc.FormBuilderRpcApi
import org.orbeon.fr.FormRunnerUtils
import org.orbeon.oxf.util.StringUtils.*
import org.orbeon.web.DomSupport.*
import org.orbeon.web.{DomEventNames, DomSupport}
import org.orbeon.xforms.rpc.RpcClient
import org.orbeon.xforms.{AjaxClient, AjaxEvent, CallbackList, XFormsUI}
import org.scalajs.dom
import org.scalajs.dom.html
import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits.*

import scala.scalajs.js
import scala.util.chaining.scalaUtilChainingOps


object SectionLabelEditor {

  val sectionAdded: CallbackList[String] = new CallbackList

  locally {

    val SectionTitleSelector = ".fr-section-title"
    val SectionLabelSelector = ".fr-section-label .btn-link, .fr-section-label .xforms-output-output"

    var labelInputOpt         : js.UndefOr[html.Input] = js.undefined
    var labelClickInterceptors: List[html.Element]     = Nil

    // On click on a trigger inside `.fb-section-grid-editor,` send section id as a property along with the event
    AjaxClient.beforeSendingEvent.add(
      (eventWithProperties: (AjaxEvent, js.Function1[js.Dictionary[js.Any], Unit])) => {

        val (event, addProperties) = eventWithProperties

        event.targetIdOpt foreach { eventTargetId =>

          val eventName        = event.eventName
          val targetEl         = dom.document.getElementById(eventTargetId)
          val inSectionEditor  = targetEl.closestOpt(".fb-section-grid-editor").isDefined

          if (eventName == DomEventNames.DOMActivate && inSectionEditor)
            addProperties(js.Dictionary(
              "section-id" -> SectionGridEditor.currentSectionGridOpt.get.el.id
            ))
        }
      }
    )

    sectionAdded.add((sectionNamespacedId: String) => {
      for {
        _           <- AjaxClient.allEventsProcessedF("sectionAdded")
        sectionEl   <- dom.document.getElementByIdOpt(sectionNamespacedId)
        interceptor <- labelClickInterceptors.find { interceptor =>
          val offset = Position.adjustedOffset(interceptor)
          Position.findInCache(BlockCache.sectionGridCache, offset.top, offset.left).exists { block =>
            block.el.contains(sectionEl)
          }
        }
      } locally {
        showLabelEditor(interceptor)
      }
    })

    def sendNewLabelValue(): Unit = {

      val newLabelValue    = labelInputOpt.get.value
      val labelInputOffset = Position.adjustedOffset(labelInputOpt.get)
      val section          = Position.findInCache(BlockCache.sectionGridCache, labelInputOffset.top, labelInputOffset.left).get
      val sectionId        = section.el.id

      section.el.querySelectorT(SectionLabelSelector).textContent = newLabelValue

      RpcClient[FormBuilderRpcApi].sectionUpdateLabel(sectionId, newLabelValue).call()

      labelInputOpt.get.hide()
    }

    def showLabelEditor(clickInterceptor: html.Element): Unit =
      AjaxClient.allEventsProcessedF("showLabelEditor") foreach { _ =>

        // Clear interceptor click hint, if any
        clickInterceptor.innerText = ""

        // Create single input element, if we don't have one already
        val labelInput = labelInputOpt.getOrElse {

          val labelInputElem =
            dom.document.createInputElement
              .tap(_.classList.add("fb-edit-section-label"))

          dom.document.querySelectorT(".fb-main").appendChild(labelInputElem)

          labelInputElem.addEventListener("blur", (_: dom.Event) => { if (labelInputElem.isVisible) sendNewLabelValue() })
          labelInputElem.addEventListener(DomEventNames.KeyPress, (e: dom.KeyboardEvent) => {
            if (e.code == "Enter") {
              // Avoid "enter" from being dispatched to other control that might get the focus
              e.preventDefault()
              sendNewLabelValue()
            }
          })
          labelInputOpt = labelInputElem
          labelInputElem
        }

        val interceptorOffset = Position.adjustedOffset(clickInterceptor)

        // From the section title, get the anchor element, which contains the title
        val labelAnchor =
          Position
            .findInCache(BlockCache.sectionGridCache, interceptorOffset.top, interceptorOffset.left)
            .get
            .el
            .querySelectorT(SectionLabelSelector)

        // Set placeholder, done every time to account for a value change when changing current language
        locally {
          val placeholderOutput = SectionGridEditor.sectionGridEditorContainer.childrenT(".fb-type-section-title-label").head
          val placeholderValue  = XFormsUI.getCurrentValue(placeholderOutput)
          labelInput.placeholder = placeholderValue.get
        }

        // Populate and show input
        labelInput.value = labelAnchor.textContent
        labelInput.show()

        // Position and size input
        val inputOffset = DomSupport.Offset(
          top = interceptorOffset.top -
            // Interceptor offset is normalized, so we need to remove the scrollTop when setting the offset
            Position.scrollTop +
            // Vertically center input inside click interceptor
            (clickInterceptor.contentHeightOrZero - labelInput.outerHeight) / 2,
          left = interceptorOffset.left
        )
        labelInput.setOffset(inputOffset)
        labelInput.setOffset(inputOffset) // Workaround for issue on Chrome, see https://github.com/orbeon/orbeon-forms/issues/572
        labelInput.setWidth(clickInterceptor.contentWidthOrZero - 10d)
        labelInput.focus()
      }

    // Update highlight of section title, as a hint users can click to edit
    def updateHighlight(
      updateClass      : (String, html.Element) => Unit,
      clickInterceptor : html.Element
    )                  : Unit = {

      val offset  = Position.adjustedOffset(clickInterceptor)
      val section = Position.findInCache(BlockCache.sectionGridCache, offset.top, offset.left).get
      val sectionTitle = section.el.querySelectorT(SectionTitleSelector)
      updateClass("hover", sectionTitle)
    }

    // Show textual indication user can click on empty section title
    def showClickHintIfTitleEmpty(clickInterceptor: html.Element): Unit = {
      val interceptorOffset = Position.adjustedOffset(clickInterceptor)
      val section = Position.findInCache(BlockCache.sectionGridCache, interceptorOffset.top, interceptorOffset.left).get
      val labelAnchor = section.el.querySelectorT(SectionLabelSelector)
      if (labelAnchor.textContent.isAllBlank) {
        val outputWithHintMessage = SectionGridEditor.sectionGridEditorContainer.childrenT(".fb-enter-section-title-label").head
        val hintMessage = XFormsUI.getCurrentValue(outputWithHintMessage).get
        clickInterceptor.innerText = hintMessage
      }
    }

    // Create and position click interceptors
    locally {

      Position.onOffsetMayHaveChanged(() => {

        val sections = BlockCache.sectionGridCache.elems.collect {
          case block if block.el.matches(BlockCache.SectionSelector) => block.el
        }

        // Create interceptor divs, so we have enough to cover all the sections
        locally {

          val newInterceptors =
            List.fill(sections.size - labelClickInterceptors.size) {
              val container = dom.document.createElementT("div")
              container.className = "fb-section-label-editor-click-interceptor"
              container.addEventListener(
                "click",
                (e: dom.Event) => {
                  val isViewMode = FormRunnerUtils.isViewMode(e.targetT)
                  if (! isViewMode)
                    showLabelEditor(e.targetT)
                }
              )
              container.addEventListener(
                "mouseover",
                (e: dom.Event) => {
                  val isViewMode = FormRunnerUtils.isViewMode(e.targetT)
                  if (! isViewMode) {
                    updateHighlight(
                      (cssClass: String, el: html.Element) => el.classList.add(cssClass),
                      e.targetT
                    )
                    showClickHintIfTitleEmpty(e.targetT)
                  }
                }
              )

              container.addEventListener("mouseout", (e: dom.Event) => {
                updateHighlight(
                  (cssClass: String, el: html.Element) => el.classList.remove(cssClass),
                  e.targetT
                )
                e.target.asInstanceOf[dom.Element].textContent = ""
              })

              container
            }

          val fbMain = dom.document.querySelector(".fb-main")
          newInterceptors.foreach(fbMain.appendChild)

          labelClickInterceptors :::= newInterceptors
        }

        // Hide interceptors we don't need
        for (interceptor <- labelClickInterceptors)
          interceptor.hide()

        // Position interceptor for each section
        for ((section, interceptor) <- sections.iterator.zip(labelClickInterceptors.iterator)) {

          val sectionTitle = section.querySelectorT(SectionTitleSelector)
          val sectionLabel = section.querySelectorT(SectionLabelSelector)

          // Show, as this might be an interceptor that was previously hidden, and is now reused
          interceptor.show()

          // Start at the label, but extend all the way to the right to the end of the title
          val labelOffset = sectionLabel.getOffset
          val titleOffset = sectionTitle.getOffset
          val interceptorOffset = DomSupport.Offset(
            top  = titleOffset.top,
            left = labelOffset.left
          )
          interceptor.setOffset(interceptorOffset)
          interceptor.setHeight(sectionTitle.contentHeightOrZero)
          interceptor.setWidth(sectionTitle.contentWidthOrZero - (sectionLabel.getOffset.left - sectionTitle.getOffset.left))
        }
      })
    }
  }
}
