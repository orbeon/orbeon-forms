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
import cats.implicits.catsSyntaxOptionId
import enumeratum.*
import enumeratum.EnumEntry.Lowercase
import org.orbeon.builder.facade.*
import org.orbeon.builder.facade.JQueryTooltip.*
import org.orbeon.builder.rpc.FormBuilderRpcApi
import org.orbeon.facades.TinyMce
import org.orbeon.facades.TinyMce.{GlobalTinyMce, TinyMceConfig, TinyMceDefaultConfig, TinyMceEditor}
import org.orbeon.fr.FormRunnerUtils
import org.orbeon.oxf.util.HtmlParsing
import org.orbeon.oxf.util.StringUtils.*
import org.orbeon.web.DomEventNames
import org.orbeon.web.DomSupport.*
import org.orbeon.xforms.*
import org.orbeon.xforms.rpc.RpcClient
import org.scalajs.dom
import org.scalajs.dom.{document, html}
import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits.*

import scala.scalajs.js
import scala.util.chaining.scalaUtilChainingOps


object ControlLabelHintTextEditor {

  private sealed trait EditorType extends EnumEntry with Lowercase
  private object EditorType extends Enum[EditorType] {
    val values: IndexedSeq[EditorType] = findValues
    case object Label extends EditorType
    case object Hint  extends EditorType
    case object Text  extends EditorType
  }

  val controlAdded: CallbackList[String] = new CallbackList

  locally {
    val LabelHintSelectorList = List(".xforms-label", ".xforms-hint", ".xforms-text .xforms-output-output") map (".fb-main .fr-editable " + _)
    val LabelHintSelector     = LabelHintSelectorList mkString ","
    val ControlSelector       = ".xforms-control, .xbl-component"
    val ExplanationSelector   = ".xbl-component.xbl-fr-explanation"

    var resourceEditorCurrentControlOpt  : Option[html.Element] = None
    var resourceEditorCurrentLabelHintOpt: Option[html.Element] = None

    // Heuristic to close the editor based on mousedown and focus events
    def mousedownOrFocus(event: dom.Event): Unit = {
      val target = event.targetT
      val eventOnEditor        = target.closestOpt(".fb-label-editor").isDefined
      val eventOnMceDialog     = target.closestOpt(".tox-dialog").isDefined
      val eventOnMceToolbar    = target.closestOpt(".tox-tinymce").isDefined
      val eventOnMceToolbarOvf = target.closestOpt(".tox-toolbar__overflow").isDefined // https://github.com/orbeon/orbeon-forms/issues/6315
      val eventOnMceMenu       = target.closestOpt(".tox-menu").isDefined
      val eventOnControlLabel  =
          // Event on label or element inside label
          target.ancestorOrSelfElem(LabelHintSelector, includeSelf = true).nonEmpty &&
          // Only interested in labels in the "editor" portion of FB
          target.ancestorOrSelfElem(".fb-main", includeSelf = true).nonEmpty
      if (! (eventOnEditor || eventOnMceDialog || eventOnMceToolbar || eventOnMceToolbarOvf || eventOnMceMenu || eventOnControlLabel))
        resourceEditorEndEdit()
    }

    locally {
      // Use capture so we know about the event and send the value to the server before someone else reacting to a `mousedown`
      document.addEventListener("mousedown", mousedownOrFocus _, useCapture = true)
      document.addEventListener("focusin"  , mousedownOrFocus _, useCapture = true)

      // Click on label/hint
      document.addEventListener(DomEventNames.Click, (event: dom.Event) => {
        event.targetT.closestOpt(LabelHintSelector).foreach { labelHint =>
          val isViewMode = FormRunnerUtils.isViewMode(labelHint)
          if (! isViewMode) {
            // Close current editor, if there is one open
            resourceEditorEndEdit()
            resourceEditorCurrentLabelHintOpt = labelHint.some
            // Find control for this label
            resourceEditorCurrentControlOpt =
              labelHint.ancestorOrSelfElem(".fr-grid-th", includeSelf = true).nextOption() match {
                case Some(_) =>
                  // Case of a repeat: we might not have a control, so instead keep track of the LHH editor
                  resourceEditorCurrentLabelHintOpt
                case None =>
                  labelHint.closestOpt(ExplanationSelector)
                    .orElse(labelHint.closestOpt(ControlSelector))
              }
            resourceEditorCurrentLabelHintOpt
              .foreach(resourceEditorStartEdit)
          }
        }
      })

      // New control added
      controlAdded.add((containerId: String) => {
        val container = dom.document.getElementByIdT(containerId)
        resourceEditorCurrentControlOpt = container.querySelectorOpt(ControlSelector)
        val repeatOpt = container.parentElementOpt.filter(_.matches(".fr-repeat-single-row"))
        resourceEditorCurrentLabelHintOpt =
            repeatOpt match {
              case Some(repeat) =>
                val column = container.index + 1
                repeat.querySelectorOpt(s".fr-grid-head .fr-grid-th:nth-child($column) .xforms-label")
              case None =>
               container.querySelectorOpt(".xforms-label, .xforms-text .xforms-output-output")
            }
        resourceEditorCurrentLabelHintOpt
          .foreach(resourceEditorStartEdit)
      })
    }

    // Show editor on click on label
    def resourceEditorStartEdit(currentLabelHint: html.Element): Unit =
      AjaxClient.allEventsProcessedF("resourceEditorStartEdit") foreach { _ =>
        // Remove `for` so browser doesn't set the focus to the control on click
        currentLabelHint.removeAttribute("for")
        // Show, position, and populate editor
        // Get position before showing editor, so showing doesn't move things in the page
        Private.containerDivElem.style.width = s"${currentLabelHint.offsetWidth}px"
        // Cannot just use `show()` because we have `display: none` originally, which would default to `display: block`.
        // This is because we can't use an inline `style` attribute anymore, see https://github.com/orbeon/orbeon-forms/issues/3565
        Private.containerDivElem.style.display = "flex"
        Private.startEdit()
        Private.containerDivElem.setOffset(currentLabelHint.getOffset)
        Private.setValue(Private.labelHintValue(currentLabelHint))
        Private.checkboxInputElem.checked = Private.isLabelHintHtml
        // Set tooltip for checkbox and HTML5 placeholders (don't do this once for all, as the language can change)
        $(Private.checkboxInputElem).tooltip(new JQueryTooltipConfig {
          val title: String = dom.document.querySelectorT(".fb-message-lhha-checkbox").textContent
        })
        val labelTextOrHint = Private.getEditorType.entryName // TODO: pass `resourceEditorCurrentLabelHint`
        Private.textInputElem.placeholder = dom.document.querySelectorT(s".fb-message-type-$labelTextOrHint").innerText
        // Hide setting visibility instead of .hide(), as we still want the label to take space, on which we show the input
        currentLabelHint.style.visibility = "hidden"
        // Add class telling if this is a label or hint editor
        Private.annotateWithLhhaClass(true)
      }

    // Called when users press enter or tab out
    def resourceEditorEndEdit(): Unit =
      // If editor is hidden, editing has already been ended (endEdit can be called more than once)
      if (Private.containerDivElem.isVisible) {
        resourceEditorCurrentControlOpt.foreach { resourceEditorCurrentControl =>
          // Send value to server, handled in Form Builder's `model.xml`
          val controlId   = resourceEditorCurrentControl.id
          val newRawValue = Private.getValue
          val isHTML      = Private.isHTML
          val sanitized   = if (isHTML) HtmlParsing.sanitizeHtmlString(newRawValue) else newRawValue

          RpcClient[FormBuilderRpcApi].controlUpdateLabelOrHintOrText(
            controlId = controlId,
            lhha      = Private.getEditorType.entryName,
            value     = sanitized,
            isHTML    = isHTML
          ).call() // ignoring the `Future` completion

          // Destroy tooltip, or it doesn't get recreated on `startEdit()`
          $(Private.checkboxInputElem).tooltip("destroy")
          Private.containerDivElem.hide()
          Private.endEdit()
          Private.annotateWithLhhaClass(false)
          resourceEditorCurrentLabelHintOpt.foreach { labelHint =>
            labelHint.style.visibility = ""
            Private.setLabelHintHtml(isHTML)
            // Update values in the DOM, without waiting for the server to send us the value
            Private.labelHintValue(labelHint, sanitized)
          }

          // Clean state
          resourceEditorCurrentControlOpt = None
          resourceEditorCurrentLabelHintOpt = None
        }
      }

    object Private {

      // Also in `LiferayURL`!
      private val URLBaseMagic = "1b713b2e6d7fd45753f4b8a6270b776e"

      private val TinyMceEmptyContent = "<div>\u00A0</div>"

      // State
      var tinyMceObjectOpt   : Option[TinyMceEditor] = None
      var tinyMceInitialized : Boolean               = false

      val textInputElem: html.Input =
        document.createInputElement.tap { element =>
          element.className = "xforms-hidden"
          element.`type` = "text"
        }

      val checkboxInputElem: html.Input =
        document.createInputElement.tap { element =>
          element.className = "xforms-hidden"
          element.`type` = "checkbox"
        }

      val tinyMceContainerDivElem: html.Element =
        document.createElementT("div")
          .tap(_.className = "xforms-hidden")

      // Create elements for editing
      val containerDivElem: html.Element =
        document.createElementT("div")
          .tap(_.className = "xforms-hidden fb-label-editor")
          .tap(_.append(textInputElem, checkboxInputElem, tinyMceContainerDivElem))

      // Add elements to the page
      locally {

        val fbMain = document.querySelectorT(".fb-main")

        Page.findXFormsFormFromHtmlElemOrDefault(fbMain).foreach { form =>
          // Add `name` to form fields for correctness
          textInputElem    .name = form.namespaceIdIfNeeded("fb-label-editor-text"    )
          checkboxInputElem.name = form.namespaceIdIfNeeded("fb-label-editor-checkbox")
        }

        fbMain.append(containerDivElem)

        // Event handlers

        // End edit when users press enter
        GlobalEventListenerSupport.addJsListener(
          textInputElem,
          DomEventNames.KeyPress,
          (e: dom.KeyboardEvent) => {

            if (e.keyCode == 13) {
              e.preventDefault()
              resourceEditorEndEdit()
            }
          }
        )

        // When checkbox clicked, set focus back on the text field, where it was before
        GlobalEventListenerSupport.addJsListener(
          checkboxInputElem,
          DomEventNames.Click,
          (_: dom.Event) => {
            textInputElem.focus()
          }
        )
      }

      // Read/write class telling us if the label/hint/text is in HTML, set in grid.xml
      def getEditorType: EditorType =
        if      (resourceEditorCurrentLabelHintOpt.exists(_.matches(".xforms-label")))              EditorType.Label
        else if (resourceEditorCurrentLabelHintOpt.exists(_.parentElement.matches(".xforms-text"))) EditorType.Text
        else                                                                                     EditorType.Hint

      def htmlClass: String = s"fb-${getEditorType.entryName}-is-html"
      def isLabelHintHtml: Boolean = resourceEditorCurrentControlOpt.exists(_.hasClass(htmlClass))
      def setLabelHintHtml(isHtml: Boolean): Unit = resourceEditorCurrentControlOpt foreach (_.toggleClass(htmlClass, isHtml))
      def annotateWithLhhaClass(add: Boolean): Unit = containerDivElem.toggleClass(s"fb-label-editor-for-${getEditorType.entryName}", add)

      def labelHintValue(labelHint: html.Element): String =
        if (isLabelHintHtml) labelHint.innerHTML
        else                 labelHint.innerText

      def labelHintValue(labelHint: html.Element, value: String): Unit =
        if (isLabelHintHtml) labelHint.innerHTML = value
        else                 labelHint.innerText = value

      // TODO: duplicate, but needs a `var` to store state
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
        resourceEditorCurrentLabelHintOpt.foreach(_.setHeight(tinyMceContainerDivElem.contentHeightOrZero: Double))

      // Function to initialize the TinyMCE, memoized so it runs at most once
      val initTinyMce: Eval[TinyMceEditor] = Eval.later {

        val anchorId = Underscore.uniqueId() // TODO: Don't use `Underscore`

        tinyMceContainerDivElem.show()
        tinyMceContainerDivElem.id = anchorId

        locally {
          val href =
            dom.window
              .document
              .documentElement
              .queryNestedElems[html.Anchor](".tinymce-base-url")
              .headOption
              .map(_.href).getOrElse(throw new IllegalStateException("missing `.tinymce-base-url`"))
          GlobalTinyMce.baseURL = href.substring(0, href.length - s"$URLBaseMagic.js".length)
        }

        // TinyMCE config from property, if defined
        val tinyMceConfig = {
          val customConfigJS = dom.document.querySelectorT(".fb-tinymce-config .xforms-output-output").textContent
          if (customConfigJS.nonAllBlank)
            js.JSON.parse(customConfigJS).asInstanceOf[TinyMceConfig]
          else
            Underscore.clone(TinyMceDefaultConfig)
        }

        tinyMceConfig.plugins                  = tinyMceConfig.plugins.map(_ + ",autoresize") // Auto-size MCE height based on the content
        tinyMceConfig.autoresize_min_height    =  100.0                                       // Min height of 100px
        tinyMceConfig.autoresize_bottom_margin =  16.0                                        // Default padding for autoresize adds too much empty space at the bottom
        TinyMce.setLanguage(tinyMceConfig, Option(document.documentElement.getAttribute("lang")))
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
        tinyMceContainerDivElem.style.width = s"${containerDivElem.offsetWidth}px"

      def getValue: String =
        getEditorType match {
          case EditorType.Text =>
            tinyMceObjectOpt map (_.getContent()) match {
              case Some(TinyMceEmptyContent) => "" // https://twitter.com/avernet/status/579031182605750272
              case Some(content)             => content
              case None                      => ""
            }
          case EditorType.Label | EditorType.Hint =>
            textInputElem.value
        }

      def setValue(newValue: String): Unit =
        getEditorType match {
          case EditorType.Text =>
            withInitializedTinyMce { tinyMceObject =>
              tinyMceObject.setContent(newValue)
              // Workaround for resize not happening with empty values, see
              // https://twitter.com/avernet/status/580798585291177984
              tinyMceObject.execCommand("mceAutoResize")
              tinyMceObject.focus()
            }
          case EditorType.Label | EditorType.Hint =>
            textInputElem.value = newValue
            textInputElem.focus()
        }

      def isHTML: Boolean = getEditorType == EditorType.Text || checkboxInputElem.checked

      def startEdit(): Unit = {
        textInputElem.hide()
        checkboxInputElem.hide()
        tinyMceObjectOpt.foreach(_.hide())
        tinyMceContainerDivElem.hide()

        getEditorType match {
          case EditorType.Text =>
            initTinyMce.value
            tinyMceContainerDivElem.show()
            withInitializedTinyMce { tinyMceObject =>
              // Without hiding first, the first time after render the TinyMCE can't get the focus
              tinyMceObject.hide()
              makeSpaceForTinyMce()
              setTinyMceWidth()
              tinyMceObject.show()
            }
          case EditorType.Label | EditorType.Hint =>
            textInputElem.show()
            checkboxInputElem.show()
        }
      }

      def endEdit(): Unit =
        if (getEditorType == EditorType.Text)
          // Reset height we might have placed on the explanation element inside the cell
          resourceEditorCurrentLabelHintOpt.foreach(_.style.height = "")
    }
  }
}
