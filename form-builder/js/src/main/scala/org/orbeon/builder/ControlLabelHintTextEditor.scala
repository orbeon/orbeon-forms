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
import cats.Eval
import enumeratum._
import enumeratum.EnumEntry.Lowercase
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

  sealed trait EditorType extends EnumEntry with Lowercase
  object EditorType extends Enum[EditorType] {
    val values = findValues
    case object Label extends EditorType
    case object Hint  extends EditorType
    case object Text  extends EditorType
  }

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
      val eventOnMceDialog     = target.closest(".tox-dialog").is("*")
      val eventOnMceToolbar    = target.closest(".tox-tinymce").is("*")
      val eventOnMceMenu       = target.closest(".tox-menu").is("*")
      val eventOnControlLabel  =
          // Click on label or element inside label
          (target.is(LabelHintSelector) || target.parents(LabelHintSelector).is("*")) &&
          // Only interested in labels in the "editor" portion of FB
          target.parents(".fb-main").is("*")
      if (! (eventOnEditor || eventOnMceDialog || eventOnMceToolbar || eventOnMceMenu || eventOnControlLabel))
        resourceEditorEndEdit()
    }

    locally {
      // Use capture so we know about the event and send the value to the server before someone else reacting to a `click`
      document.addEventListener("click"  , clickOrFocus _, useCapture = true)
      document.addEventListener("focusin", clickOrFocus _, useCapture = true)

      // Click on label/hint
      $(document).on(
        s"${EventNames.Click}${Private.ListenerSuffix}",
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
      Private.containerDiv.width(resourceEditorCurrentLabelHint.outerWidth())
      // Cannot just use `show()` because we have `display: none` originally, which would default to `display: block`.
      // This is because we can't use an inline `style` attribute anymore, see https://github.com/orbeon/orbeon-forms/issues/3565
      Private.containerDiv.css("display", "flex")
      Private.startEdit()
      val labelHintOffset = resourceEditorCurrentLabelHint.offset()
      Private.containerDiv.offset(labelHintOffset)
      Private.setValue(Private.labelHintValue)
      Private.checkboxInput.prop("checked", Private.isLabelHintHtml)
      // Set tooltip for checkbox and HTML5 placeholders (don't do this once for all, as the language can change)
      Private.checkboxInput.tooltip(new JQueryTooltipConfig {
        val title = $(".fb-message-lhha-checkbox").text()
      })
      val labelTextOrHint = Private.getEditorType.entryName
      Private.textInput.attr("placeholder", $(s".fb-message-type-$labelTextOrHint").text())
      // Hide setting visibility instead of .hide(), as we still want the label to take space, on which we show the input
      resourceEditorCurrentLabelHint.css("visibility", "hidden")
      // Add class telling if this is a label or hint editor
      Private.annotateWithLhhaClass(true)
    }

    // Called when users press enter or tab out
    def resourceEditorEndEdit(): Unit = {
      // If editor is hidden, editing has already been ended (endEdit can be called more than once)
      if (Private.containerDiv.is(":visible")) {
        resourceEditorCurrentControlOpt foreach { resourceEditorCurrentControl =>
          // Send value to server, handled in Form Builder's `model.xml`
          val controlId   = resourceEditorCurrentControl.attr("id").get
          val newValue    = Private.getValue
          val isHTML      = Private.isHTML

          RpcClient[FormBuilderRpcApi].controlUpdateLabelOrHintOrText(
            controlId = controlId,
            lhha      = Private.getEditorType.entryName,
            value     = newValue,
            isHTML    = isHTML
          ).call() // ignoring the `Future` completion

          // Destroy tooltip, or it doesn't get recreated on startEdit()
          Private.checkboxInput.tooltip("destroy")
          Private.containerDiv.hide()
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

      private val TinyMceEmptyContent = "<div>\u00A0</div>"
      val ListenerSuffix      = s".orbeon.builder.lht-editor"

      // State
      var tinyMceObjectOpt  : Option[TinyMceEditor] = None
      var tinyMceInitialized: Boolean               = false

      // Create elements for editing
      val containerDiv        = $("""<div   class="xforms-hidden fb-label-editor"/>""")
      val textInput           = $("""<input class="xforms-hidden" type="text">""")
      val checkboxInput       = $("""<input class="xforms-hidden" type="checkbox">""")
      val tinyMceContainerDiv = $("""<div   class="xforms-hidden">""")

      // Add elements to the page
      locally {
        // Nest and add to the page
        containerDiv
          .append(textInput)
          .append(checkboxInput)
          .append(tinyMceContainerDiv)
         $(".fb-main").append(containerDiv)

        // Event handlers
        textInput.on(s"${EventNames.KeyPress}$ListenerSuffix", (e: JQueryEventObject) => asUnit {
          // End edit when users press enter
          if (e.which == 13) {
            e.preventDefault()
            resourceEditorEndEdit()
          }
        })
        checkboxInput.on(s"${EventNames.Click}$ListenerSuffix", () => asUnit {
          // When checkbox clicked, set focus back on the text field, where it was before
          textInput.focus()
        })
      }

      // Read/write class telling us if the label/hint/text is in HTML, set in grid.xml
      def getEditorType: EditorType =
        if      (resourceEditorCurrentLabelHint.is(".xforms-label"))             EditorType.Label
        else if (resourceEditorCurrentLabelHint.parents(".xforms-text").is("*")) EditorType.Text
        else                                                                     EditorType.Hint

      def htmlClass: String = s"fb-${getEditorType.entryName}-is-html"
      def isLabelHintHtml: Boolean = resourceEditorCurrentControlOpt exists (_.is("." + htmlClass))
      def setLabelHintHtml(isHtml: Boolean): Unit = resourceEditorCurrentControlOpt foreach (_.toggleClass(htmlClass, isHtml))
      def annotateWithLhhaClass(add: Boolean): JQuery = containerDiv.toggleClass(s"fb-label-editor-for-${getEditorType.entryName}", add)

      def labelHintValue: String =
        if (isLabelHintHtml) resourceEditorCurrentLabelHint.html()
        else                 resourceEditorCurrentLabelHint.text()
      def labelHintValue(value: String): Unit =
        if (isLabelHintHtml) resourceEditorCurrentLabelHint.html(value)
        else                 resourceEditorCurrentLabelHint.text(value)

      def withInitializedTinyMce(thunk: TinyMceEditor => Unit): Unit =
        if (tinyMceInitialized)
          tinyMceObjectOpt foreach thunk
        else
          tinyMceObjectOpt foreach { tinyMceObject =>
            tinyMceObject.on("init", _ => {
              tinyMceInitialized = true
              thunk(tinyMceObject)
            })
          }

      // Not using tinymceObject.container, as it is not initialized onInit, while editorContainer is
      def makeSpaceForTinyMce(): Unit =
        resourceEditorCurrentLabelHint.height(tinyMceContainerDiv.height())

      // Function to initialize the TinyMCE, memoized so it runs at most once
      val initTinyMce: Eval[TinyMceEditor] = Eval.later {

        val anchorId = Underscore.uniqueId() // TODO: Don't use `Underscore`

        tinyMceContainerDiv.show()
        tinyMceContainerDiv.attr("id", anchorId)

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

        // Force these important settings
        tinyMceConfig.inline       = true
        tinyMceConfig.hidden_input = false

        tinyMceConfig.plugins                  += ",autoresize"  // Auto-size MCE height based on the content
        tinyMceConfig.autoresize_min_height    =  100.0          // Min height of 100px
        tinyMceConfig.autoresize_bottom_margin =  16.0           // Default padding for autoresize adds too much empty space at the bottom
        tinyMceConfig.suffix                   =  ".min"

        val tinyMceObject = new TinyMceEditor(anchorId, tinyMceConfig, GlobalTinyMce.EditorManager)
        tinyMceObjectOpt = Some(tinyMceObject)
        withInitializedTinyMce { tinyMceObject =>
          // Q: Not sure why we need to add listener after initialization?
          // Found out experimentally that this event fires (but not `ResizeEditor`/`ResizeWindow`)
          tinyMceObject.on("ResizeContent", _ => {
            makeSpaceForTinyMce()
          })
        }
        tinyMceObject.render()
        tinyMceObject
      }

      // Set width of TinyMCE to the width of the container
      def setTinyMceWidth(): Unit =
        $(tinyMceContainerDiv).width(containerDiv.outerWidth())

      def getValue: String =
        getEditorType match {
          case EditorType.Text =>
            tinyMceObjectOpt map (_.getContent()) match {
              case Some(TinyMceEmptyContent) => "" // https://twitter.com/avernet/status/579031182605750272
              case Some(content)             => content
              case None                      => ""
            }
          case EditorType.Label | EditorType.Hint =>
            textInput.value().asInstanceOf[String]
        }

      def setValue(newValue: String): Unit =
        getEditorType match {
          case EditorType.Text =>
            withInitializedTinyMce { tinyMceObject =>
              tinyMceObject.setContent(newValue)
              // Workaround for resize not happening with empty values, see
              // https://twitter.com/avernet/status/580798585291177984
              tinyMceObject.execCommand("mceAutoResize")
            }
          case EditorType.Label | EditorType.Hint =>
            textInput.value(newValue)
            textInput.focus()
        }

      def isHTML: Boolean = getEditorType == EditorType.Text || checkboxInput.is(":checked")

      def startEdit(): Unit = {
        textInput.hide()
        checkboxInput.hide()
        tinyMceObjectOpt foreach (_.hide())
        tinyMceContainerDiv.hide()

        getEditorType match {
          case EditorType.Text =>
            initTinyMce.value
            tinyMceContainerDiv.show()
            withInitializedTinyMce { tinyMceObject =>
              // Without hiding first, the first time after render the TinyMCE can't get the focus
              tinyMceObject.hide()
              makeSpaceForTinyMce()
              setTinyMceWidth()
              tinyMceObject.show()
              tinyMceObject.focus()
            }
          case EditorType.Label | EditorType.Hint =>
            textInput.show()
            checkboxInput.show()
        }
      }

      def endEdit(): Unit =
        if (getEditorType == EditorType.Text)
          // Reset height we might have placed on the explanation element inside the cell
          resourceEditorCurrentLabelHint.css("height", "")
    }
  }
}
