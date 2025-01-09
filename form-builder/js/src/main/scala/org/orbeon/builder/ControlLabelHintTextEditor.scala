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
import cats.Eval
import enumeratum.*
import enumeratum.EnumEntry.Lowercase
import io.udash.wrappers.jquery.{JQuery, JQueryCallbacks}
import org.orbeon.builder.facade.*
import org.orbeon.builder.facade.JQueryTooltip.*
import org.orbeon.builder.rpc.FormBuilderRpcApi
import org.orbeon.facades.TinyMce.{GlobalTinyMce, TinyMceConfig, TinyMceDefaultConfig, TinyMceEditor}
import org.orbeon.oxf.util.CoreUtils.*
import org.orbeon.oxf.util.StringUtils.*
import org.orbeon.web.DomEventNames
import org.orbeon.web.DomSupport.*
import org.orbeon.xforms.*
import org.orbeon.xforms.rpc.RpcClient
import org.scalajs.dom
import org.scalajs.dom.document
import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits.*

import scala.scalajs.js


object ControlLabelHintTextEditor {

  sealed trait EditorType extends EnumEntry with Lowercase
  object EditorType extends Enum[EditorType] {
    val values = findValues
    case object Label extends EditorType
    case object Hint  extends EditorType
    case object Text  extends EditorType
  }

  val controlAdded: JQueryCallbacks[js.Function1[String, js.Any], String] = $.callbacks[js.Function1[String, js.Any], String](flags = "")

  locally {
    val LabelHintSelectorList = List(".xforms-label", ".xforms-hint", ".xforms-text .xforms-output-output") map (".fb-main .fr-editable " + _)
    val LabelHintSelector     = LabelHintSelectorList mkString ","
    val ControlSelector       = ".xforms-control, .xbl-component"
    val ExplanationSelector   = ".xbl-component.xbl-fr-explanation"

    var resourceEditorCurrentControlOpt: Option[JQuery] = None
    var jResourceEditorCurrentLabelHint: JQuery = null

    // Heuristic to close the editor based on click and focus events
    def clickOrFocus(event: dom.Event): Unit = {
      val target = event.targetT
      val eventOnEditor        = target.closestOpt(".fb-label-editor").isDefined
      val eventOnMceDialog     = target.closestOpt(".tox-dialog").isDefined
      val eventOnMceToolbar    = target.closestOpt(".tox-tinymce").isDefined
      val eventOnMceToolbarOvf = target.closestOpt(".tox-toolbar__overflow").isDefined // https://github.com/orbeon/orbeon-forms/issues/6315
      val eventOnMceMenu       = target.closestOpt(".tox-menu").isDefined
      val eventOnControlLabel  =
          // Click on label or element inside label
          target.ancestorOrSelfElem(LabelHintSelector).nonEmpty &&
          // Only interested in labels in the "editor" portion of FB
          target.ancestorOrSelfElem(".fb-main").nonEmpty
      if (! (eventOnEditor || eventOnMceDialog || eventOnMceToolbar || eventOnMceToolbarOvf || eventOnMceMenu || eventOnControlLabel))
        resourceEditorEndEdit()
    }

    locally {
      // Use capture so we know about the event and send the value to the server before someone else reacting to a `click`
      document.addEventListener("click"  , clickOrFocus _, useCapture = true)
      document.addEventListener("focusin", clickOrFocus _, useCapture = true)

      // Click on label/hint
      document.addEventListener(DomEventNames.Click, (event: dom.Event) => {
        event.targetT.closestOpt(LabelHintSelector).foreach { labelHint =>
          // Close current editor, if there is one open
          resourceEditorEndEdit()
          jResourceEditorCurrentLabelHint = $(labelHint)
          // Find control for this label
          resourceEditorCurrentControlOpt =
            Some(
              labelHint.ancestorOrSelfElem(".fr-grid-th").nextOption() match {
                case Some(_) =>
                  // Case of a repeat: we might not have a control, so instead keep track of the LHH editor
                  jResourceEditorCurrentLabelHint
                case None =>
                  labelHint.closestOpt(ExplanationSelector)
                    .getOrElse(labelHint.closest(ControlSelector))
                    .pipe($(_))
              }
            )
          resourceEditorStartEdit(jResourceEditorCurrentLabelHint)
        }
      })

      // New control added
      controlAdded.add((containerId: String) => {
        val container = $(document.getElementById(containerId))
        resourceEditorCurrentControlOpt = Some(container.find(ControlSelector))
        val repeat = container.parents(".fr-repeat-single-row").first()
        jResourceEditorCurrentLabelHint =
            if (repeat.is("*")) {
              val column = container.index() + 1
              repeat.find(s".fr-grid-head .fr-grid-th:nth-child($column) .xforms-label")
            } else
              container.find(".xforms-label, .xforms-text .xforms-output-output").first()
        if (jResourceEditorCurrentLabelHint.is("*"))
            resourceEditorStartEdit(jResourceEditorCurrentLabelHint)
      })

    }

    // Show editor on click on label
    def resourceEditorStartEdit(currentLabelHint: JQuery): Unit =
      AjaxClient.allEventsProcessedF("resourceEditorStartEdit") foreach { _ =>
        // Remove `for` so browser doesn't set the focus to the control on click
        currentLabelHint.removeAttr("for")
        // Show, position, and populate editor
        // Get position before showing editor, so showing doesn't move things in the page
        //val x: Double =
        Private.containerDiv.width(currentLabelHint.outerWidth().get)
        // Cannot just use `show()` because we have `display: none` originally, which would default to `display: block`.
        // This is because we can't use an inline `style` attribute anymore, see https://github.com/orbeon/orbeon-forms/issues/3565
        Private.containerDiv.css("display", "flex")
        Private.startEdit()
        Private.containerDiv.offset(currentLabelHint.offset())
        Private.setValue(Private.labelHintValue(currentLabelHint))
        Private.checkboxInput.prop("checked", Private.isLabelHintHtml)
        // Set tooltip for checkbox and HTML5 placeholders (don't do this once for all, as the language can change)
        Private.checkboxInput.tooltip(new JQueryTooltipConfig {
          val title = $(".fb-message-lhha-checkbox").text()
        })
        val labelTextOrHint = Private.getEditorType.entryName // TODO: pass `resourceEditorCurrentLabelHint`
        Private.textInput.attr("placeholder", $(s".fb-message-type-$labelTextOrHint").text())
        // Hide setting visibility instead of .hide(), as we still want the label to take space, on which we show the input
        currentLabelHint.css("visibility", "hidden")
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
          jResourceEditorCurrentLabelHint.css("visibility", "")
          // Update values in the DOM, without waiting for the server to send us the value
          Private.setLabelHintHtml(isHTML)
          Private.labelHintValue(jResourceEditorCurrentLabelHint, newValue)
          // Clean state
          resourceEditorCurrentControlOpt = None
          jResourceEditorCurrentLabelHint = null
        }
      }
    }

    object Private {

      // Also in `LiferayURL`!
      private val URLBaseMagic = "1b713b2e6d7fd45753f4b8a6270b776e"

      private val TinyMceEmptyContent = "<div>\u00A0</div>"

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
        textInput.get(0).foreach(_.addEventListener(DomEventNames.KeyPress, (e: dom.KeyboardEvent) => {
          // End edit when users press enter
          if (e.keyCode == 13) {
            e.preventDefault()
            resourceEditorEndEdit()
          }
        }))
        checkboxInput.get(0).foreach(_.addEventListener(DomEventNames.Click, (_: dom.Event) => {
          // When checkbox clicked, set focus back on the text field, where it was before
          textInput.focus()
        }))
      }

      // Read/write class telling us if the label/hint/text is in HTML, set in grid.xml
      def getEditorType: EditorType =
        if      (jResourceEditorCurrentLabelHint.is(".xforms-label"))             EditorType.Label
        else if (jResourceEditorCurrentLabelHint.parents(".xforms-text").is("*")) EditorType.Text
        else                                                                     EditorType.Hint

      def htmlClass: String = s"fb-${getEditorType.entryName}-is-html"
      def isLabelHintHtml: Boolean = resourceEditorCurrentControlOpt exists (_.is("." + htmlClass))
      def setLabelHintHtml(isHtml: Boolean): Unit = resourceEditorCurrentControlOpt foreach (_.toggleClass(htmlClass, isHtml))
      def annotateWithLhhaClass(add: Boolean): JQuery = containerDiv.toggleClass(s"fb-label-editor-for-${getEditorType.entryName}", add)

      def labelHintValue(labelHint: JQuery): String =
        if (isLabelHintHtml) labelHint.html()
        else                 labelHint.text()

      def labelHintValue(labelHint: JQuery, value: String): Unit =
        if (isLabelHintHtml) labelHint.html(value)
        else                 labelHint.text(value)

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
        Option(jResourceEditorCurrentLabelHint).foreach(_.height(tinyMceContainerDiv.height()))

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
          if (customConfigJS.nonAllBlank)
            js.JSON.parse(customConfigJS).asInstanceOf[TinyMceConfig]
          else
            Underscore.clone(TinyMceDefaultConfig)
        }

        tinyMceConfig.plugins                  = tinyMceConfig.plugins.map(_ + ",autoresize") // Auto-size MCE height based on the content
        tinyMceConfig.autoresize_min_height    =  100.0                                       // Min height of 100px
        tinyMceConfig.autoresize_bottom_margin =  16.0                                        // Default padding for autoresize adds too much empty space at the bottom
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
        $(tinyMceContainerDiv).width(containerDiv.outerWidth().get)

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
          jResourceEditorCurrentLabelHint.css("height", "")
    }
  }
}
