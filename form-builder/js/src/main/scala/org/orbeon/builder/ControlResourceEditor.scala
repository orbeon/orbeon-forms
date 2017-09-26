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
import org.orbeon.xforms._
import org.orbeon.xforms.facade.JQueryTooltip._
import org.orbeon.xforms.facade.{JQueryTooltipConfig, TinyMceDefaultConfig, TinyMceEditor, Underscore}
import org.scalajs.dom.document
import org.scalajs.jquery.{JQuery, JQueryEventObject}

import scala.scalajs.js
import scala.scalajs.js.annotation.JSExportTopLevel

private object ControlResourceEditor {

  val LabelHintSelector   = ".fr-editable .xforms-label, .fr-editable .xforms-hint, .fr-editable .xforms-text .xforms-output-output"
  val ControlSelector     = ".xforms-control, .xbl-component"
  val ExplanationSelector = ".xbl-component.xbl-fr-explanation"

  @JSExportTopLevel("ORBEON.Builder.controlAdded")
  val controlAdded = $.Callbacks()

  var resourceEditorCurrentControl: JQuery = null
  var resourceEditorCurrentLabelHint: JQuery = null

  // Heuristic to close the editor based on click and focus events
  def clickOrFocus(event: JQueryEventObject): Unit = {
    val target = $(event.target)
    val eventOnEditor = target.closest(".fb-label-editor").is("*")
    val eventOnControlLabel =
        // Click on label or element inside label
        (target.is(LabelHintSelector) || target.parents(LabelHintSelector).is("*")) &&
        // Only interested in labels in the "editor" portion of FB
        target.parents(".fb-main").is("*")
    if (! (eventOnEditor || eventOnControlLabel))
      resourceEditorEndEdit()
  }

  $(document).ready(() ⇒ {

    $(document).on("click", clickOrFocus _)
    $(document).on("focusin", clickOrFocus _)

    // Click on label/hint
    $(".fb-main").on("click", LabelHintSelector, (event: JQueryEventObject) ⇒ {
      // Close current editor, if there is one open
      if (resourceEditorCurrentControl != null) resourceEditorEndEdit()
      resourceEditorCurrentLabelHint = $(event.currentTarget)
      // Find control for this label
      val th = resourceEditorCurrentLabelHint.parents("th")
      resourceEditorCurrentControl =
        if (th.is("*")) {
          // Case of a repeat: we might not have a control, so instead keep track of the LHH editor
          resourceEditorCurrentLabelHint.parents(ControlSelector).first()
        } else {
          val explanation = resourceEditorCurrentLabelHint.parents(ExplanationSelector).toArray()
          val controls = resourceEditorCurrentLabelHint.parents(ControlSelector).toArray()
          val parents = $($.merge(explanation, controls))
          parents.first()
        }
      resourceEditorStartEdit()
    })

    // New control added
    controlAdded.add((containerId: String) ⇒ {
      val container = $(document.getElementById(containerId))
      resourceEditorCurrentControl = container.find(ControlSelector)
      val repeat = container.parents(".fr-repeat").first()
      resourceEditorCurrentLabelHint =
          if (repeat.is("*"))
            repeat.find(
              "thead tr th:nth-child(" +
                (container.index() + 1) +
                ") .xforms-label, tbody tr td:nth-child(" +
                (container.index() + 1) +
                ") .xforms-text .xforms-output-output")
          else
            container.find(".xforms-label, .xforms-text .xforms-output-output").first()
      if (resourceEditorCurrentLabelHint.is("*"))
          resourceEditorStartEdit()
    })
  })

  // Show editor on click on label
  def resourceEditorStartEdit(): Unit = {

    // Remove `for` so browser doesn't set the focus to the control on click
    resourceEditorCurrentLabelHint.removeAttr("for")
    // Show, position, and populate editor
    // Get position before showing editor, so showing doesn"t move things in the page
    Private.container.width(resourceEditorCurrentLabelHint.outerWidth())
    Private.container.show()
    Private.startEdit()
    val labelHintOffset = resourceEditorCurrentLabelHint.offset()
    Private.container.offset(labelHintOffset)
    Private.setValue(Private.labelHintValue)
    Private.checkbox.prop("checked", Private.isLabelHintHtml)
    // Set tooltip for checkbox and HTML5 placeholders (don"t do this once for all, as the language can change)
    Private.checkbox.tooltip(new JQueryTooltipConfig() {
      override val title = $(".fb-message-lhha-checkbox").text()
    })
    val lhha = Private.lhha
    Private.textfield.attr("placeholder", $(s".fb-message-type-$lhha").text())
    // Hide setting visibility instead of .hide(), as we still want the label to take space, on which we show the input
    resourceEditorCurrentLabelHint.css("visibility", "hidden")
    // Add class telling if this is a label or hint editor
    Private.annotateWithLhhaClass(true)
  }

  // Called when users press enter or tab out
  def resourceEditorEndEdit(): Unit = {

    // If editor is hidden, editing has already been ended (endEdit can be called more than once)
    if (Private.container.is(":visible")) {
      // Send value to server, handled in FB"s model.xml
      val newValue = Private.getValue
      val isHTML = Private.isHTML
      DocumentAPI.dispatchEvent(
        targetId = resourceEditorCurrentControl.attr("id").get,
        eventName = "fb-update-control-lhha",
        properties = js.Dictionary(
          "lhha" → Private.lhha,
          "value" → newValue,
          "isHtml" → isHTML.toString()
        )
      )
      // Destroy tooltip, or it doesn't get recreated on startEdit()
      Private.checkbox.tooltip("destroy")
      Private.container.hide()
      Private.endEdit()
      Private.annotateWithLhhaClass(false)
      resourceEditorCurrentLabelHint.css("visibility", "")
      // Update values in the DOM, without waiting for the server to send us the value
      Private.setLabelHintHtml(isHTML)
      Private.labelHintValue(newValue)
      // Clean state
      resourceEditorCurrentControl = null
      resourceEditorCurrentLabelHint = null
    }
  }

  private object Private {

    // State
    var tinymceObject: TinyMceEditor = null

    // Create elements for editing
    val container     = $("""<div   style="display: none" class = "fb-label-editor"/>""")
    val textfield     = $("""<input style="display: none" type="text">""")
    val checkbox      = $("""<input style="display: none" type="checkbox">""")
    val tinymceAnchor = $("""<div   style="display: none">""")

    // Add elements to the page
    locally {
      // Nest and add to the page
      container
        .append(textfield)
        .append(checkbox)
        .append(tinymceAnchor)
       $(".fb-main").append(container)

      // Event handlers
      textfield.on("keypress", (e: JQueryEventObject) ⇒
        // End edit when users press enter
        if (e.which == 13) resourceEditorEndEdit()
      )
      checkbox.on("click", () ⇒
        // When checkbox clicked, set focus back on the textfield, where it was before
        textfield.focus()
      )
    }

    // Read/write class telling us if the label/hint is in HTML, set in grid.xml
    def lhha: String =
      if      (resourceEditorCurrentLabelHint.is(".xforms-label"))             "label"
      else if (resourceEditorCurrentLabelHint.parents(".xforms-text").is("*")) "text"
      else                                                                     "hint"

    def htmlClass: String = "fb-" + lhha + "-is-html"
    def isLabelHintHtml: Boolean = resourceEditorCurrentControl.is("." + htmlClass)
    def setLabelHintHtml(isHtml: Boolean): Unit = resourceEditorCurrentControl.toggleClass(htmlClass, isHtml)
    def annotateWithLhhaClass(add: Boolean) = container.toggleClass("fb-label-editor-for-" + lhha, add)

    def labelHintValue: String =
      if (isLabelHintHtml) resourceEditorCurrentLabelHint.html()
      else                 resourceEditorCurrentLabelHint.text()
    def labelHintValue(value: String): Unit =
      if (isLabelHintHtml) resourceEditorCurrentLabelHint.html(value)
      else                 resourceEditorCurrentLabelHint.text(value)

    def afterTinyMCEInitialized(f: TinyMceEditor ⇒ Unit): Unit =
      tinymceObject.initialized.toOption match {
        case Some(true)  ⇒ f(tinymceObject)
        case _           ⇒ tinymceObject.onInit.add(f)
      }

    def makeSpaceForMCE(): Unit = {
      // Not using tinymceObject.container, as it is not initialized onInit, while editorContainer is
      val mceContainer = document.getElementById(tinymceObject.editorContainer)
      val mceHeight = $(mceContainer).height()
      resourceEditorCurrentLabelHint.height(mceHeight)
    }

    // Function to initialize the TinyMCE, memoized so it runs at most once
    val initTinyMCE: () ⇒ Unit = memoize0(() ⇒ {

      tinymceAnchor.show()
      tinymceAnchor.attr("id", Underscore.uniqueId())

      // Auto-size MCE height based on the content, with min height of 100px
      val mceConfig = Underscore.clone(TinyMceDefaultConfig)
      mceConfig.plugins += ",autoresize"
      mceConfig.autoresize_min_height = 100
      mceConfig.autoresize_bottom_margin = 16 // Default padding for autoresize adds too much empty space at the bottom

      tinymceObject = new TinyMceEditor(tinymceAnchor.attr("id").get, mceConfig)
      tinymceObject.render()
      afterTinyMCEInitialized((_) ⇒ {
        // We don"t need the anchor anymore; just used to tell TinyMCE where to go in the DOM
        tinymceAnchor.detach()
        $(tinymceObject.getWin()).on("resize", makeSpaceForMCE _)
      })
    })

    // Set width of TinyMCE to the width of the container
    // - If not yet initialized, set width on anchor, which is copied by TinyMCE to table
    // - If already initialized, set width directly on table created by TinyMCE
    // (Hacky, but didn't find a better way to do it)
    def setTinyMCEWidth(): Unit = {
      if (tinymceObject != null) {
        val tinymceTable = $(tinymceObject.container).find(".mceLayout")
        val widthSetOn = if (tinymceTable.is("*")) tinymceTable else tinymceAnchor
        widthSetOn.width(container.outerWidth())
      }
    }

    def getValue: String =
      if (lhha == "text") {
          val content = tinymceObject.getContent()
          // Workaround to TinyMCE issue, see
          // https://twitter.com/avernet/status/579031182605750272
          if (content == "<div>\u00A0</div>") "" else content
      } else {
          textfield.value().asInstanceOf[String]
      }

    def setValue(newValue: String): Unit =
      if (lhha == "text") {
          afterTinyMCEInitialized((_) ⇒ {
            tinymceObject.setContent(newValue)
            // Workaround for resize not happening with empty values, see
            // https://twitter.com/avernet/status/580798585291177984
            tinymceObject.execCommand("mceAutoResize")
          })
      } else {
        textfield.value(newValue)
        textfield.focus()
      }

    def isHTML: Boolean = lhha == "text" || checkbox.is(":checked")

    def startEdit(): Unit = {
      textfield.hide()
      checkbox.hide()
      if (tinymceObject != null) tinymceObject.hide()
      if (lhha == "text") {
        setTinyMCEWidth()
        initTinyMCE()
        afterTinyMCEInitialized((_) ⇒ {
          makeSpaceForMCE()
          tinymceObject.show()
          tinymceObject.focus()
        })
      } else {
        textfield.show()
        checkbox.show()
      }
    }

    def endEdit(): Unit =
      if (lhha == "text")
          // Reset height we might have placed on the explanation element inside the cell
          resourceEditorCurrentLabelHint.css("height", "")
  }
}
