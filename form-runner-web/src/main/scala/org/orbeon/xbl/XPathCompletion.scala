package org.orbeon.xbl

import org.orbeon.oxf.util.StringUtils.OrbeonStringOps
import org.orbeon.web.{DomEventNames, DomSupport}
import org.orbeon.web.DomSupport.DomElemOps
import org.orbeon.xpath.formatter.{XPathFormatter, XPathHighlighter}
import org.orbeon.xforms.facade.XBLCompanion
import org.orbeon.xforms.{AjaxClient, AjaxEvent, EventListenerSupport, XFormsXbl}
import org.scalajs.dom
import org.scalajs.dom.{CustomEvent, FocusEvent, html}
import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits.*
import scalatags.JsDom.all.*

import scala.concurrent.Promise
import scala.scalajs.js
import scala.scalajs.js.JSConverters.*
import scala.scalajs.js.|


object XPathCompletion {

  XFormsXbl.declareCompanion("fb|xpath-completion", js.constructorOf[XPathCompletionCompanion])

  private[xbl] class XPathCompletionCompanion(containerElem: dom.html.Element) extends XBLCompanion {

    private var pendingPromiseOpt : Option[Promise[Completions]] = None
    private var textExpanderActive: Boolean                     = false
    private var stoppedChangeEvent: Boolean                     = false

    private var backdropOpt             : Option[HighlightBackdrop] = None
    private var expandButtonOpt         : Option[dom.html.Element]  = None
    private var formatButtonOpt         : Option[dom.html.Element]  = None
    private var copyButtonOpt           : Option[dom.html.Element]  = None
    private var placeholderElemOpt      : Option[dom.html.Element]  = None
    private var isExpanded              : Boolean                   = false
    private var suppressNextOutsideClick: Boolean                   = false
    private var valueOpt                : Option[String]            = None

    private object EventSupport extends EventListenerSupport

    private def fieldElemOpt: Option[dom.html.Element] =
      backdropOpt.flatMap(_.fieldElemOpt)

    private def getFieldValue: String =
      backdropOpt.map(_.getFieldValue).getOrElse("")

    override def init(): Unit = {

      val expander = containerElem.querySelector("text-expander")

      val backdrop = HighlightBackdrop(
        containerElem = containerElem,
        classPrefix   = "fb-xpath-completion",
        onInput       = () => updateHighlights()
      )
      backdropOpt = Some(backdrop)

      expandButtonOpt = containerElem.querySelectorOpt(".fb-xpath-completion-expand-button")
      formatButtonOpt = containerElem.querySelectorOpt(".fb-xpath-completion-format-button")
      copyButtonOpt   = containerElem.querySelectorOpt(".fb-xpath-completion-copy-button")

      expandButtonOpt.foreach { expandButton =>
        EventSupport.addListener(expandButton, "mousedown", (event: dom.MouseEvent) => {
          event.preventDefault()
        })
        EventSupport.addListener(expandButton, "click", (event: dom.MouseEvent) => {
          event.preventDefault()
          event.stopPropagation()
          toggleExpand()
        })
      }

      formatButtonOpt.foreach { formatButton =>
        EventSupport.addListener(formatButton, "mousedown", (event: dom.MouseEvent) => {
          event.preventDefault()
        })
        EventSupport.addListener(formatButton, "click", (event: dom.MouseEvent) => {
          event.preventDefault()
          event.stopPropagation()
          reformatExpression()
        })
      }

      copyButtonOpt.foreach { copyButton =>
        EventSupport.addListener(copyButton, "mousedown", (event: dom.MouseEvent) => {
          event.preventDefault()
        })
        EventSupport.addListener(copyButton, "click", (event: dom.MouseEvent) => {
          event.preventDefault()
          event.stopPropagation()
          copyExpression()
        })
      }

      if (expander != null) {
        EventSupport.addListener(expander, "text-expander-change", (event: CustomEvent) => {
          val details = event.detail.asInstanceOf[TextExpanderChangeEventDetail]

          if (details.key == "$") {

            val p = Promise[Completions]()
            pendingPromiseOpt = Some(p)
            details.provide(p.future.toJSPromise)

            AjaxClient.fireEvent(
              AjaxEvent(
                eventName = "fb-query-completions",
                targetId  = this.containerElem.id,
                properties = Map(
                  "text" -> details.text
                )
              )
            )
          }
        })

        EventSupport.addListener(expander, "text-expander-value", (event: CustomEvent) => {
          val details = event.detail.asInstanceOf[TextExpanderValueEventDetail]
          details.value = s"$$${details.item.getAttribute("data-value")}"
        })

        EventSupport.addListener(expander, "text-expander-activate"  , (_: CustomEvent) => { textExpanderActive = true  })
        EventSupport.addListener(expander, "text-expander-deactivate", (_: CustomEvent) => { textExpanderActive = false })

        // When a completion is committed into the input/textarea, update highlights
        EventSupport.addListener(expander, "text-expander-committed", (_: CustomEvent) => updateHighlights())
      }

      fieldElemOpt.foreach { fieldElem =>
        // Prevent a `xxforms-blur` when users click on a completion
        EventSupport.addListener(fieldElem, "focusout",
          (event: FocusEvent) => {
            if (textExpanderActive) {
              event.stopPropagation()
            } else if (stoppedChangeEvent) {
              // If we stopped the change event earlier and the field value hasn't changed since then, the browser
              // dispatch a `change` event on focus out, so we need to dispatch one manually here.
              DomSupport.dispatchChange(fieldElem)
              stoppedChangeEvent = false
            }
          },
          useCapture = true
        )

        // Listen for reformat keyboard shortcut (Shift+Alt+F or Cmd/Ctrl+Option+L) and Esc to restore expanded view
        EventSupport.addListener(fieldElem, DomEventNames.KeyDown, (event: dom.KeyboardEvent) => {
          if (isFormatShortcut(event)) {
            event.preventDefault()
            event.stopPropagation()
            reformatExpression()
          } else if (event.key == "Escape" && isExpanded && !textExpanderActive) {
            event.preventDefault()
            event.stopPropagation()
            collapse()
          }
        })
      }

      // Restore expanded textarea when clicking outside of it
      EventSupport.addListener(
        dom.document,
        "mousedown",
        (event: dom.MouseEvent) => {
          if (isExpanded) {
            backdropOpt.flatMap(_.wrapperElemOpt).foreach { wrapperElem =>
              val targetNode = event.target.asInstanceOf[dom.Node]
              if (targetNode != null && !wrapperElem.contains(targetNode)) {
                suppressNextOutsideClick = true
                event.preventDefault()
                event.stopPropagation()
                collapse()
              }
            }
          }
        },
        useCapture = true
      )

      EventSupport.addListener(
        dom.document,
        "mouseup",
        (_: dom.MouseEvent) => {
          if (suppressNextOutsideClick) {
            dom.window.setTimeout(() => {
              suppressNextOutsideClick = false
            }, 0)
          }
        },
        useCapture = true
      )

      EventSupport.addListener(
        dom.document,
        "click",
        (event: dom.MouseEvent) => {
          if (suppressNextOutsideClick) {
            suppressNextOutsideClick = false
            event.preventDefault()
            event.stopPropagation()
          }
        },
        useCapture = true
      )

      EventSupport.addListener(dom.window, "resize", (_: dom.Event) => {
        if (isExpanded) {
          updateExpandedBounds()
          updateHighlights()
        }
      })

      // Prevent a `xxforms-value` when users click on a completion
      EventSupport.addListener(containerElem, "change",
        (event: dom.Event) => {
          if (textExpanderActive) {
            stoppedChangeEvent = true
            event.stopPropagation()
          }
        },
        useCapture = true
      )

      updateHighlights()
    }

    override def xformsGetValue(): String =
      valueOpt.getOrElse("")

    override def xformsUpdateValue(newValue: String): js.UndefOr[Nothing] = {
      valueOpt = Some(newValue)
      updateHighlights(valueOpt)
      js.undefined
    }

    override def xformsUpdateReadonly(readonly: Boolean): Unit =
      updateHighlights()

    override def destroy(): Unit = {
      collapse()
      EventSupport.clearAllListeners()
      backdropOpt.foreach(_.destroy())
      backdropOpt        = None
      pendingPromiseOpt  = None
      expandButtonOpt    = None
      formatButtonOpt    = None
      copyButtonOpt      = None
      placeholderElemOpt = None
      valueOpt           = None
    }

    private val DefaultExpandTitle = "Expand"
    private val ExpandedTitle      = "Restore (Esc)"
    private val ExpandedInsetPx    = 10.0

    private def toggleExpand(): Unit =
      if (isExpanded) collapse() else expand()

    private def expand(): Unit =
      if (!isExpanded) {
        backdropOpt.flatMap(_.wrapperElemOpt).foreach { wrapperElem =>
          val inlineRect = wrapperElem.getBoundingClientRect()

          if (placeholderElemOpt.isEmpty) {
            val placeholder = dom.document.createElement("span").asInstanceOf[dom.html.Element]
            placeholder.className           = "fb-xpath-completion-placeholder"
            placeholder.style.display       = "inline-block"
            placeholder.style.width         = s"${inlineRect.width}px"
            placeholder.style.height        = s"${inlineRect.height}px"
            placeholder.style.verticalAlign = "top"
            Option(wrapperElem.parentNode).foreach(_.insertBefore(placeholder, wrapperElem))
            placeholderElemOpt = Some(placeholder)
          }

          isExpanded = true
          wrapperElem.classList.add("fb-xpath-completion-expanded")
          updateExpandedBounds()
          updateExpandButtonState()
          fieldElemOpt.foreach(_.focus())
          updateHighlights()
        }
      }

    private def collapse(): Unit =
      if (isExpanded) {
        isExpanded = false
        backdropOpt.flatMap(_.wrapperElemOpt).foreach { wrapperElem =>
          wrapperElem.classList.remove("fb-xpath-completion-expanded")
          wrapperElem.style.left   = ""
          wrapperElem.style.top    = ""
          wrapperElem.style.width  = ""
          wrapperElem.style.height = ""
        }
        placeholderElemOpt.foreach { placeholder =>
          Option(placeholder.parentNode).foreach(_.removeChild(placeholder))
        }
        placeholderElemOpt = None
        updateExpandButtonState()
        updateHighlights()
      }

    private def updateExpandedBounds(): Unit =
      backdropOpt.flatMap(_.wrapperElemOpt).foreach { wrapperElem =>
        val targetContainerOpt =
          containerElem.closestOpt(".xxforms-dialog-body")
            .orElse(containerElem.closestOpt("dialog, .xforms-dialog"))
            .orElse(Option(dom.document.querySelector(".fb-main")).map(_.asInstanceOf[dom.html.Element]))

        val (left, top, width, height) =
          targetContainerOpt match {
            case Some(targetContainer) =>
              val rect = targetContainer.getBoundingClientRect()
              (
                rect.left + ExpandedInsetPx,
                rect.top  + ExpandedInsetPx,
                math.max(100.0, rect.width  - ExpandedInsetPx * 2),
                math.max(100.0, rect.height - ExpandedInsetPx * 2)
              )
            case None =>
              val vw = dom.window.innerWidth
              val vh = dom.window.innerHeight
              val inset = 20.0
              (
                inset,
                inset,
                math.max(100.0, vw - inset * 2),
                math.max(100.0, vh - inset * 2)
              )
          }

        wrapperElem.style.left   = s"${left}px"
        wrapperElem.style.top    = s"${top}px"
        wrapperElem.style.width  = s"${width}px"
        wrapperElem.style.height = s"${height}px"
      }

    private def updateExpandButtonState(): Unit =
      expandButtonOpt.foreach { button =>
        val title     = if (isExpanded) ExpandedTitle          else DefaultExpandTitle
        val ariaLabel = if (isExpanded) "Restore"              else "Expand"
        val iconClass = if (isExpanded) "fa fa-compress fa-fw" else "fa fa-expand fa-fw"
        button.title = title
        button.setAttribute("aria-label", ariaLabel)
        button.setAttribute("aria-expanded", isExpanded.toString)
        Option(button.querySelector("i")).foreach(_.setAttribute("class", iconClass))
      }

    private def isFormatShortcut(event: dom.KeyboardEvent): Boolean = {
      val isShiftAltF = event.shiftKey && event.altKey && !event.ctrlKey && !event.metaKey &&
        (event.key.equalsIgnoreCase("f") || event.keyCode == 70)
      val isCtrlAltL = (event.ctrlKey || event.metaKey) && event.altKey && !event.shiftKey &&
        (event.key.equalsIgnoreCase("l") || event.keyCode == 76)
      isShiftAltF || isCtrlAltL
    }

    private def reformatExpression(): Unit =
      if (!isMarkedReadonly) {
        val isTextarea = fieldElemOpt.exists(_.isInstanceOf[dom.html.TextArea])
        if (isTextarea) {
          val currentValue = getFieldValue
          if (currentValue.trimAllToOpt.nonEmpty) {
            scala.util.Try(XPathFormatter.format(currentValue)) match {
              case scala.util.Success(formatted) =>
                if (formatted != currentValue)
                  applyFormattedValue(formatted)
              case scala.util.Failure(_) =>
                flashFormatError()
            }
          }
        }
      }

    private def applyFormattedValue(formatted: String): Unit =
      fieldElemOpt.foreach { fieldElem =>
        fieldElem.focus()
        fieldElem match {
          case ta: dom.html.TextArea => ta.select()
          case in: dom.html.Input    => in.select()
          case _                     =>
        }

        val execSuccess =
          try {
            dom.document.asInstanceOf[js.Dynamic].execCommand("insertText", false, formatted).asInstanceOf[Boolean]
          } catch {
            case _: Throwable => false
          }

        if (!execSuccess || getFieldValue != formatted) {
          fieldElem match {
            case ta: dom.html.TextArea => ta.value = formatted
            case in: dom.html.Input    => in.value = formatted
            case _                     =>
          }
          fieldElem.dispatchEvent(new dom.Event("input", new dom.EventInit { bubbles = true }))
        }

        valueOpt = Some(formatted)
        DomSupport.dispatchChange(fieldElem)
        updateHighlights(Some(formatted))
      }

    private def flashFormatError(): Unit =
      formatButtonOpt.foreach { button =>
        button.classList.add("fb-xpath-format-error")
        dom.window.setTimeout(() => {
          button.classList.remove("fb-xpath-format-error")
        }, 1000)
      }

    private val DefaultCopyTitle = "Copy to clipboard"

    private def copyExpression(): Unit = {
      val value = getFieldValue
      copyToClipboard(
        value,
        onSuccess = () => flashCopySuccess(),
        onFailure = () => flashCopyError()
      )
    }

    private def copyToClipboard(text: String, onSuccess: () => Unit, onFailure: () => Unit): Unit = {
      val nav = dom.window.navigator.asInstanceOf[js.Dynamic]
      val hasClipboardApi =
        !js.isUndefined(nav) &&
        !js.isUndefined(nav.clipboard) &&
        !js.isUndefined(nav.clipboard.writeText)

      if (hasClipboardApi) {
        nav.clipboard.writeText(text).asInstanceOf[js.Promise[Unit]].toFuture.onComplete {
          case scala.util.Success(_) =>
            onSuccess()
          case scala.util.Failure(_) =>
            if (copyViaExecCommand(text)) onSuccess()
            else onFailure()
        }
      } else {
        if (copyViaExecCommand(text)) onSuccess()
        else onFailure()
      }
    }

    private def copyViaExecCommand(text: String): Boolean =
      try {
        val textarea = dom.document.createElement("textarea").asInstanceOf[dom.html.TextArea]
        textarea.value = text
        textarea.style.position = "fixed"
        textarea.style.left = "-9999px"
        textarea.style.top = "-9999px"
        dom.document.body.appendChild(textarea)
        textarea.focus()
        textarea.select()
        val success = dom.document.asInstanceOf[js.Dynamic].execCommand("copy").asInstanceOf[Boolean]
        dom.document.body.removeChild(textarea)
        success
      } catch {
        case _: Throwable => false
      }

    private def flashCopySuccess(): Unit =
      copyButtonOpt.foreach { button =>
        button.title = "Copied!"
        button.classList.add("fb-xpath-copy-success")
        val iconElemOpt = Option(button.querySelector("i"))
        iconElemOpt.foreach { icon =>
          icon.setAttribute("class", "fa fa-check fa-fw")
        }
        dom.window.setTimeout(() => {
          button.title = DefaultCopyTitle
          button.classList.remove("fb-xpath-copy-success")
          iconElemOpt.foreach { icon =>
            icon.setAttribute("class", "fa fa-copy fa-fw")
          }
        }, 1500)
      }

    private def flashCopyError(): Unit =
      copyButtonOpt.foreach { button =>
        button.classList.add("fb-xpath-format-error")
        dom.window.setTimeout(() => {
          button.classList.remove("fb-xpath-format-error")
        }, 1000)
      }

    private def updateHighlights(valueOpt: Option[String] = None): Unit =
      backdropOpt.foreach(_.updateHighlights(XPathHighlighter.highlightToHtml(_), valueOpt))

    // Callback from the server
    def returnCompletions(jsonText: String): Unit = {
      pendingPromiseOpt.foreach { pendingPromise =>

        pendingPromiseOpt = None

        val array = js.JSON.parse(jsonText).asInstanceOf[js.Array[js.Dictionary[String]]]

        pendingPromise.success(
          new Completions {
            val matched: Boolean = array.nonEmpty
            val fragment: dom.html.Element =
              ul(
                `class` := "dropdown-menu"
              )(
                array.map { labelValue =>

                  val value = labelValue.get("v").getOrElse(throw new IllegalStateException) // value is mandatory
                  val label = labelValue.get("l").flatMap(_.trimAllToOpt).map(l => s""""$l" ($value)""").getOrElse(value)

                  li(
                    label,
                    role := "option",
                    data("value") := value
                  )
                }.toSeq*
              ).render
          }
        )
      }
    }
  }
}

// Facades
trait Completions extends js.Object {
  val matched: Boolean
  val fragment: dom.html.Element
}

trait TextExpanderChangeEventDetail extends CustomEvent {
  val key: String
  val text: String
  val provide: js.Function1[js.Promise[Completions], Unit]
}

trait TextExpanderValueEventDetail extends CustomEvent {
  var value: String
  val item: dom.html.Element
}

trait TextExpanderCommittedEventDetail extends CustomEvent {
  val input: dom.html.Input | dom.html.TextArea
}
