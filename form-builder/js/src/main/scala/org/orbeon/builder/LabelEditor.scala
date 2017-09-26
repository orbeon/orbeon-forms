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

import org.orbeon.builder.BlockCache.Block
import org.orbeon.builder.Position.Offset
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.xforms.$
import org.orbeon.xforms._
import org.orbeon.xforms.facade._

import scala.scalajs.js
import org.scalajs.dom
import org.scalajs.jquery.{JQuery, JQueryEventObject}

import scala.scalajs.js.annotation.{JSExportAll, JSExportTopLevel, ScalaJSDefined}

@JSExportTopLevel("ORBEON.builder.LabelEditor")
@JSExportAll
object LabelEditor {

  val SectionTitleSelector = ".fr-section-title:first"
  val SectionLabelSelector = ".fr-section-label:first a, .fr-section-label:first .xforms-output-output"

  var labelInputOpt: js.UndefOr[JQuery] = js.undefined

  // On click on a trigger inside .fb-section-grid-editor, send section id as a property along with the event
  AjaxServer.beforeSendingEvent.add((
    event         : js.Dynamic,
    addProperties : js.Function1[js.Dictionary[String], Unit]
  ) ⇒ {

    val eventTargetId    = event.targetId.asInstanceOf[String]
    val eventName        = event.eventName.asInstanceOf[String]
    val targetEl         = $(dom.document.getElementById(eventTargetId))
    val inSectionEditor  = targetEl.closest(".fb-section-grid-editor").is("*")

    if (eventName == "DOMActivate" && inSectionEditor)
      addProperties(js.Dictionary(
        "section-id" → SectionGridEditor.currentSectionGridOpt.get.el.attr("id").get
      ))
  })

  def sendNewLabelValue(): Unit = {

    val newLabelValue    = labelInputOpt.get.value().asInstanceOf[String]
    val labelInputOffset = Position.adjustedOffset(labelInputOpt.get)
    val section          = Position.findInCache(BlockCache.sectionGridCache, labelInputOffset.top, labelInputOffset.left).get
    val sectionId        = section.el.attr("id").get

    section.el.find(SectionLabelSelector).text(newLabelValue)
    DocumentAPI.dispatchEvent(
      targetId   = sectionId,
      eventName  = "fb-update-section-label",
      properties = js.Dictionary("label" → newLabelValue)
    )
    labelInputOpt.get.hide()
  }

  def showLabelEditor(clickInterceptor: JQuery): Unit = {

    if (Globals.eventQueue.length > 0 || Globals.requestInProgress) {

      // If we have a request in progress or events in the queue, try this again later
      js.timers.setTimeout(
        interval = Properties.internalShortDelay.get())(
        body     = () ⇒ showLabelEditor(clickInterceptor)
      )

    } else {

      // Clear interceptor click hint, if any
      clickInterceptor.text("")

      // Create single input element, if we don't have one already
      val labelInput = labelInputOpt.getOrElse {
        val labelInput = $("<input class='fb-edit-section-label'/>")
        $(".fb-main").append(labelInput)
        labelInput.on("blur", () ⇒ if (labelInput.is(":visible")) sendNewLabelValue())
        labelInput.on("keypress", (e: JQueryEventObject) ⇒ if (e.which == 13) sendNewLabelValue())
        Events.ajaxResponseProcessedEvent.subscribe(() ⇒ labelInput.hide())
        labelInputOpt = labelInput
        labelInput
      }

      val interceptorOffset = Position.adjustedOffset(clickInterceptor)

      // From the section title, get the anchor element, which contains the title
      val labelAnchor = {
          val section = Position.findInCache(BlockCache.sectionGridCache, interceptorOffset.top, interceptorOffset.left).get
          section.el.find(SectionLabelSelector)
      }

      // Set placeholder, done every time to account for a value change when changing current language
      locally {
        val placeholderOutput = SectionGridEditor.sectionGridEditorContainer.children(".fb-type-section-title-label")
        val placeholderValue  = Controls.getCurrentValue(placeholderOutput.get(0).asInstanceOf[dom.html.Element])
        labelInput.attr("placeholder", placeholderValue)
      }

      // Populate and show input
      labelInput.value(labelAnchor.text())
      labelInput.show()

      // Position and size input
      val inputOffset = new Position.Offset {
          override val top = interceptorOffset.top -
            // Interceptor offset is normalized, so we need to remove the scrollTop when setting the offset
            Position.scrollTop() +
            // Vertically center input inside click interceptor
            (clickInterceptor.height() - labelInput.outerHeight()) / 2
        override val left = interceptorOffset.left
      }
      labelInput.offset(inputOffset)
      labelInput.offset(inputOffset) // Workaround for issue on Chrome, see https://github.com/orbeon/orbeon-forms/issues/572
      labelInput.width(clickInterceptor.width() - 10)
      labelInput.focus()
    }
  }

  // Update highlight of section title, as a hint users can click to edit
  def updateHighlight(
    updateClass      : js.Function2[String, JQuery, Unit],
    clickInterceptor : JQuery
  )                  : Unit = {

    val offset  = Position.adjustedOffset(clickInterceptor)
    val section = Position.findInCache(BlockCache.sectionGridCache, offset.top, offset.left)
    val sectionTitle = section.get.el.find(SectionTitleSelector)
    updateClass("hover", sectionTitle)
  }

  // Show textual indication user can click on empty section title
  def showClickHintIfTitleEmpty(clickInterceptor: JQuery): Unit = {
    val interceptorOffset = Position.adjustedOffset(clickInterceptor)
    val section = Position.findInCache(BlockCache.sectionGridCache, interceptorOffset.top, interceptorOffset.left).get
    val labelAnchor = section.el.find(SectionLabelSelector)
    if (labelAnchor.text() == "") {
      val outputWithHintMessage = SectionGridEditor.sectionGridEditorContainer.children(".fb-enter-section-title-label")
      val hintMessage = Controls.getCurrentValue(outputWithHintMessage.get(0).asInstanceOf[dom.html.Element]).get
      clickInterceptor.text(hintMessage)
    }
  }

  // Create and position click interceptors
  locally {
    val labelClickInterceptors = js.Array[JQuery]()
    Position.onOffsetMayHaveChanged(() ⇒ {
      val sections = $(".xbl-fr-section:visible:not(.xforms-disabled)")

      // Create interceptor divs, so we have enough to cover all the sections
      for (_ ← 1 to sections.length - labelClickInterceptors.length) {
        val container = $("<div class='fb-section-label-editor-click-interceptor'>")
        $(".fb-main").append(container)
        container.on("click", (e: JQueryEventObject) ⇒ LabelEditor.showLabelEditor($(e.target)))
        container.on("mouseover", (e: JQueryEventObject) ⇒ {
            updateHighlight((cssClass: String, el: JQuery) ⇒ { el.addClass(cssClass); () }, $(e.target))
            showClickHintIfTitleEmpty($(e.target))
        })
        container.on("mouseout", (e: JQueryEventObject) ⇒ {
          updateHighlight((cssClass: String, el: JQuery) ⇒ { el.removeClass(cssClass); () }, $(e.target))
          $(e.target).text("")
        })
        labelClickInterceptors.push(container)
      }

      // Hide interceptors we don't need
      for (pos ← sections.length until labelClickInterceptors.length)
        labelClickInterceptors(pos).hide()
      // Position interceptor for each section
      for (pos ← 0 until sections.length) {
        val sectionTitle = $(sections(pos)).find(SectionTitleSelector)
        val sectionLabel = $(sections(pos)).find(SectionLabelSelector)
        val interceptor = labelClickInterceptors(pos)
        // Show, as this might be an interceptor that was previously hidden, and is now reused
        interceptor.show()
        // Start at the label, but extend all the way to the right to the end of the title
        interceptor.offset(sectionLabel.offset())
        interceptor.height(sectionTitle.height())
        interceptor.width(sectionTitle.width() - (Position.offset(sectionLabel).left - Position.offset(sectionTitle).left))
      }
    })
  }
}
