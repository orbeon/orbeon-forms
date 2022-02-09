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
import org.orbeon.builder.facade.JQueryTooltip._
import org.orbeon.builder.facade._
import org.orbeon.builder.rpc.FormBuilderRpcApi
import org.orbeon.facades.TinyMce.{GlobalTinyMce, TinyMceConfig, TinyMceDefaultConfig, TinyMceEditor}
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.xforms._
import org.orbeon.xforms.rpc.RpcClient
import org.scalajs.dom
import org.scalajs.dom.document
import org.scalajs.jquery.{JQuery, JQueryCallback, JQueryEventObject}

import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits._
import scala.scalajs.js


object ControlLabelHintTextEditor {

  val controlAdded: JQueryCallback = $.Callbacks(flags = "")

  locally {
    val LabelHintSelectorList = List(".xforms-label", ".xforms-hint", ".xforms-text .xforms-output-output") map (".fb-main .fr-editable " + _)
    val LabelHintSelector     = LabelHintSelectorList mkString ","
    val ControlSelector       = ".xforms-control, .xbl-component"
    val ExplanationSelector   = ".xbl-component.xbl-fr-explanation"

    var resourceEditorCurrentControlOpt: Option[JQuery] = None
    var resourceEditorCurrentLabelHint: JQuery = null

    // Heuristic to close the editor based on click and focus events
    def clickOrFocus(event: dom.Event): Unit = {
      val target = $(event.target)
      val eventOnEditor        = target.closest(".fb-label-editor").is("*")
      val eventOnMceFloatPanel = target.closest(".tox-dialog" ).is("*")
      val eventOnControlLabel  =
          // Click on label or element inside label
          (target.is(LabelHintSelector) || target.parents(LabelHintSelector).is("*")) &&
          // Only interested in labels in the "editor" portion of FB
          target.parents(".fb-main").is("*")
      if (! (eventOnEditor || eventOnMceFloatPanel || eventOnControlLabel))
        resourceEditorEndEdit()
    }

    locally {
      // Use capture so we know about the event and send the value to the server before someone else reacting to a `click`
      document.addEventListener("click"  , clickOrFocus _, useCapture = true)
      document.addEventListener("focusin", clickOrFocus _, useCapture = true)

      // Click on label/hint
      $(document).on(
        "click.orbeon.builder.resource-editor",
        LabelHintSelector,
        (event: JQueryEventObject) => {

          // Close current editor, if there is one open
          resourceEditorEndEdit()
          resourceEditorCurrentLabelHint = $(event.currentTarget)
          // Find control for this label
          val th = resourceEditorCurrentLabelHint.parents(".fr-grid-th")
          resourceEditorCurrentControlOpt =
            Some(
              if (th.is("*")) {
                // Case of a repeat: we might not have a control, so instead keep track of the LHH editor
                resourceEditorCurrentLabelHint
              } else {
                val explanation = resourceEditorCurrentLabelHint.parents(ExplanationSelector).toArray()
                val controls = resourceEditorCurrentLabelHint.parents(ControlSelector).toArray()
                val parents = $($.merge(explanation, controls))
                parents.first()
              }
            )
          resourceEditorStartEdit()
        })

        // New control added
        controlAdded.add((containerId: String) => {
          val container = $(document.getElementById(containerId))
          resourceEditorCurrentControlOpt = Some(container.find(ControlSelector))
          val repeat = container.parents(".fr-repeat-single-row").first()
          resourceEditorCurrentLabelHint =
              if (repeat.is("*")) {
                val column = container.index() + 1
                repeat.find(s".fr-grid-head .fr-grid-th:nth-child($column) .xforms-label")
              } else
                container.find(".xforms-label, .xforms-text .xforms-output-output").first()
          if (resourceEditorCurrentLabelHint.is("*"))
              resourceEditorStartEdit()
        }
      )

      (): js.Any
    }

    // Show editor on click on label
    def resourceEditorStartEdit(): Unit = {

      // Remove `for` so browser doesn't set the focus to the control on click
      resourceEditorCurrentLabelHint.removeAttr("for")
      // Show, position, and populate editor
      // Get position before showing editor, so showing doesn't move things in the page
      Private.container.width(resourceEditorCurrentLabelHint.outerWidth())
      // Cannot just use `show()` because we have `display: none` originally, which would default to `display: block`.
      // This is because we can't use an inline `style` attribute anymore, see https://github.com/orbeon/orbeon-forms/issues/3565
      Private.container.css( "display", "flex")
      Private.startEdit()
      val labelHintOffset = resourceEditorCurrentLabelHint.offset()
      Private.container.offset(labelHintOffset)
      Private.setValue(Private.labelHintValue)
      Private.checkbox.prop("checked", Private.isLabelHintHtml)
      // Set tooltip for checkbox and HTML5 placeholders (don't do this once for all, as the language can change)
      Private.checkbox.tooltip(new JQueryTooltipConfig() {
        val title = $(".fb-message-lhha-checkbox").text()
      })
      val labelTextOrHint = Private.labelOrHintOrText
      Private.textfield.attr("placeholder", $(s".fb-message-type-$labelTextOrHint").text())
      // Hide setting visibility instead of .hide(), as we still want the label to take space, on which we show the input
      resourceEditorCurrentLabelHint.css("visibility", "hidden")
      // Add class telling if this is a label or hint editor
      Private.annotateWithLhhaClass(true)
    }

    // Called when users press enter or tab out
    def resourceEditorEndEdit(): Unit = {
      // If editor is hidden, editing has already been ended (endEdit can be called more than once)
      if (Private.container.is(":visible")) {
        resourceEditorCurrentControlOpt foreach { resourceEditorCurrentControl =>
          // Send value to server, handled in FB's model.xml
          val controlId   = resourceEditorCurrentControl.attr("id").get
          val newValue    = Private.getValue
          val isHTML      = Private.isHTML

          RpcClient[FormBuilderRpcApi].controlUpdateLabelOrHintOrText(
            controlId = controlId,
            lhha      = Private.labelOrHintOrText,
            value     = newValue,
            isHTML    = isHTML
          ).call() // ignoring the `Future` completion

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
          resourceEditorCurrentControlOpt = None
          resourceEditorCurrentLabelHint = null
        }
      }
    }

    object Private {

      // Also in `LiferayURL`!
      private val URLBaseMagic = "1b713b2e6d7fd45753f4b8a6270b776e"

      // State
      var tinyMceObject      : TinyMceEditor = _
      var tinyMceInitialized : Boolean       = false

      // Create elements for editing
      val container     = $("""<div   class="xforms-hidden fb-label-editor"/>""")
      val textfield     = $("""<input class="xforms-hidden" type="text">""")
      val checkbox      = $("""<input class="xforms-hidden" type="checkbox">""")
      val tinymceAnchor = $("""<div   class="xforms-hidden">""")

      // Add elements to the page
      locally {
        // Nest and add to the page
        container
          .append(textfield)
          .append(checkbox)
          .append(tinymceAnchor)
         $(".fb-main").append(container)

        // Event handlers
        textfield.on(EventNames.KeyPress, (e: JQueryEventObject) => asUnit {
          // End edit when users press enter
          if (e.which == 13) {
            e.preventDefault()
            resourceEditorEndEdit()
          }
        })
        checkbox.on("click.orbeon.builder.lht-editor", () => asUnit {
          // When checkbox clicked, set focus back on the text field, where it was before
          textfield.focus()
        })
      }

      // Read/write class telling us if the label/hint/text is in HTML, set in grid.xml
      def labelOrHintOrText: String =
        if      (resourceEditorCurrentLabelHint.is(".xforms-label"))             "label"
        else if (resourceEditorCurrentLabelHint.parents(".xforms-text").is("*")) "text"
        else                                                                     "hint"

      def htmlClass: String = "fb-" + labelOrHintOrText + "-is-html"
      def isLabelHintHtml: Boolean = resourceEditorCurrentControlOpt exists (_.is("." + htmlClass))
      def setLabelHintHtml(isHtml: Boolean): Unit = resourceEditorCurrentControlOpt foreach (_.toggleClass(htmlClass, isHtml))
      def annotateWithLhhaClass(add: Boolean) = container.toggleClass("fb-label-editor-for-" + labelOrHintOrText, add)

      def labelHintValue: String =
        if (isLabelHintHtml) resourceEditorCurrentLabelHint.html()
        else                 resourceEditorCurrentLabelHint.text()
      def labelHintValue(value: String): Unit =
        if (isLabelHintHtml) resourceEditorCurrentLabelHint.html(value)
        else                 resourceEditorCurrentLabelHint.text(value)

      def afterTinyMCEInitialized(thunk: () => Unit): Unit =
        if (tinyMceInitialized)
          thunk()
        else
          tinyMceObject.on("init", _ => {
            tinyMceInitialized = true
            thunk()
          })

      def makeSpaceForMCE(): Unit = {
        // Not using tinymceObject.container, as it is not initialized onInit, while editorContainer is
        val mceHeight = $(tinyMceObject.editorContainer).height()
        resourceEditorCurrentLabelHint.height(mceHeight)
      }

      // Function to initialize the TinyMCE, memoized so it runs at most once
      val initTinyMCE: () => Unit = memoize0(() => {

        tinymceAnchor.show()
        tinymceAnchor.attr("id", Underscore.uniqueId())

        // Initialize baseURL
        locally {
          val href = $(".tinymce-base-url").attr("href").getOrElse(throw new IllegalStateException("missing `.tinymce-base-url`"))
          val baseURL = href.substring(0, href.length - s"$URLBaseMagic.js".length)
          GlobalTinyMce.baseURL = baseURL
        }

        // TinyMCE config from property, if defined
        val tinyMceConfig = {
          val customConfigJS = $(".fb-tinymce-config .xforms-output-output").text()
          if (customConfigJS.trim != "")
            js.JSON.parse(customConfigJS).asInstanceOf[TinyMceConfig]
          else
            Underscore.clone(TinyMceDefaultConfig)
        }

        tinyMceConfig.plugins                  += ",autoresize"  // Auto-size MCE height based on the content
        tinyMceConfig.autoresize_min_height    =  100.0          // Min height of 100px
        tinyMceConfig.autoresize_bottom_margin =  16.0           // Default padding for autoresize adds too much empty space at the bottom
        tinyMceConfig.suffix                   =  ".min"
        tinyMceConfig.content_css              =  GlobalTinyMce.baseURL + "/../../content.css"

        tinyMceObject = new TinyMceEditor(tinymceAnchor.attr("id").get, tinyMceConfig, GlobalTinyMce.EditorManager)
        tinyMceObject.render()
        afterTinyMCEInitialized(() => {
          // We don't need the anchor anymore; just used to tell TinyMCE where to go in the DOM
          tinymceAnchor.detach()
          $(tinyMceObject.getWin()).on("resize", makeSpaceForMCE _)
        })
      })

      // Set width of TinyMCE to the width of the container
      // - If not yet initialized, set width on anchor, which is copied by TinyMCE to table
      // - If already initialized, set width directly on table created by TinyMCE
      // (Hacky, but didn't find a better way to do it)
      def setTinyMCEWidth(): Unit = {
        if (tinyMceObject ne null) {
          $(tinyMceObject.container).width(container.outerWidth())
        }
      }

      def getValue: String =
        if (labelOrHintOrText == "text") {
            val content = tinyMceObject.getContent()
            // Workaround to TinyMCE issue, see
            // https://twitter.com/avernet/status/579031182605750272
            if (content == "<div>\u00A0</div>") "" else content
        } else {
            textfield.value().asInstanceOf[String]
        }

      def setValue(newValue: String): Unit =
        if (labelOrHintOrText == "text") {
          afterTinyMCEInitialized(() => {
            tinyMceObject.setContent(newValue)
            // Workaround for resize not happening with empty values, see
            // https://twitter.com/avernet/status/580798585291177984
            tinyMceObject.execCommand("mceAutoResize")
          })
        } else {
          textfield.value(newValue)
          textfield.focus()
        }

      def isHTML: Boolean = labelOrHintOrText == "text" || checkbox.is(":checked")

      def startEdit(): Unit = {
        textfield.hide()
        checkbox.hide()
        if (tinyMceObject ne null) tinyMceObject.hide()
        if (labelOrHintOrText == "text") {
          initTinyMCE()
          afterTinyMCEInitialized(() => {
            // Without hiding first, the first time after render the TinyMCE can't get the focus
            tinyMceObject.hide()
            makeSpaceForMCE()
            setTinyMCEWidth()
            tinyMceObject.show()
            tinyMceObject.focus()
          })
        } else {
          textfield.show()
          checkbox.show()
        }
      }

      def endEdit(): Unit =
        if (labelOrHintOrText == "text")
          // Reset height we might have placed on the explanation element inside the cell
          resourceEditorCurrentLabelHint.css("height", "")
    }
  }
}
