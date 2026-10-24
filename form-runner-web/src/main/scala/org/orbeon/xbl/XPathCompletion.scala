package org.orbeon.xbl

import org.orbeon.oxf.util.StringUtils.OrbeonStringOps
import org.orbeon.web.{DomEventNames, DomSupport}
import org.orbeon.web.DomSupport.DomElemOps
import org.orbeon.xpath.formatter.{XPathFormatter, XPathHighlighter}
import org.orbeon.xforms.facade.XBLCompanion
import org.orbeon.xforms.{AjaxClient, AjaxEvent, EventListenerSupport, XFormsXbl}
import org.scalajs.dom
import org.scalajs.dom.{CustomEvent, FocusEvent, ResizeObserver}
import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits.*
import scalatags.JsDom.all.*

import scala.concurrent.Promise
import scala.scalajs.js
import scala.scalajs.js.Dynamic.global as g
import scala.scalajs.js.JSConverters.*
import scala.scalajs.js.|


object XPathCompletion {

  XFormsXbl.declareCompanion("fb|xpath-completion", js.constructorOf[XPathCompletionCompanion])

  private val RelevantStyles = List(
    "font-family",
    "font-size",
    "font-weight",
    "font-style",
    "font-variant",
    "letter-spacing",
    "word-spacing",
    "line-height",
    "text-align",
    "text-indent",
    "text-transform",
    "tab-size",
    "padding-top",
    "padding-right",
    "padding-bottom",
    "padding-left",
    "box-sizing",
    "white-space",
    "word-break",
    "overflow-wrap"
  )

  private class XPathCompletionCompanion(containerElem: dom.html.Element) extends XBLCompanion {

    private var pendingPromiseOpt : Option[Promise[Completions]] = None
    private var textExpanderActive: Boolean                     = false
    private var stoppedChangeEvent: Boolean                     = false

    private var wrapperElemOpt   : Option[dom.html.Element] = None
    private var fieldElemOpt     : Option[dom.html.Element] = None
    private var backdropElemOpt  : Option[dom.html.Element] = None
    private var highlightsElemOpt: Option[dom.html.Element] = None
    private var formatButtonOpt  : Option[dom.html.Element] = None
    private var valueOpt         : Option[String]           = None
    private var resizeObserverOpt: Option[ResizeObserver]   = None

    private object EventSupport extends EventListenerSupport

    override def init(): Unit = {

      val expander   = containerElem.querySelector("text-expander")
      val inputField = containerElem.querySelector("input, textarea").asInstanceOf[dom.html.Element]

      wrapperElemOpt = containerElem.querySelectorOpt(".fb-xpath-completion-wrapper")
      fieldElemOpt =
        containerElem.queryNestedElems[dom.html.Input]("input").headOption
          .orElse(containerElem.queryNestedElems[dom.html.TextArea]("textarea").headOption)
          .orElse(Option(inputField))

      backdropElemOpt   = containerElem.querySelectorOpt(".fb-xpath-completion-backdrop")
      highlightsElemOpt = containerElem.querySelectorOpt(".fb-xpath-completion-highlights")
      formatButtonOpt   = containerElem.querySelectorOpt(".fb-xpath-completion-format-button")

      formatButtonOpt.foreach { formatButton =>
        EventSupport.addListener(formatButton, "click", (event: dom.MouseEvent) => {
          event.preventDefault()
          event.stopPropagation()
          reformatExpression()
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
        EventSupport.addListener(expander, "text-expander-committed", (_: CustomEvent) => updateOrClearHighlights())
      }

      if (inputField != null) {
        // Prevent a `xxforms-blur` when users click on a completion
        EventSupport.addListener(inputField, "focusout",
          (event: FocusEvent) => {
            if (textExpanderActive) {
              event.stopPropagation()
            } else if (stoppedChangeEvent) {
              // If we stopped the change event earlier and the field value hasn't changed since then, the browser
              // dispatch a `change` event on focus out, so we need to dispatch one manually here.
              DomSupport.dispatchChange(inputField)
              stoppedChangeEvent = false
            }
          },
          useCapture = true
        )

        // Sync scroll between field and backdrop
        EventSupport.addListener(inputField, DomEventNames.Scroll, (_: dom.Event) => syncScroll())

        // Listen for key/input/focus events to update highlights dynamically
        EventSupport.addListeners(
          inputField,
          List(DomEventNames.Input, DomEventNames.KeyUp, DomEventNames.KeyDown, DomEventNames.Change, DomEventNames.FocusIn),
          (_: dom.Event) => updateOrClearHighlights()
        )

        // Listen for reformat keyboard shortcut (Shift+Alt+F or Cmd/Ctrl+Option+L)
        EventSupport.addListener(inputField, DomEventNames.KeyDown, (event: dom.KeyboardEvent) => {
          if (isFormatShortcut(event)) {
            event.preventDefault()
            event.stopPropagation()
            reformatExpression()
          }
        })

        // Mouseup on field in case user finishes resizing with native handle
        EventSupport.addListener(inputField, "mouseup", (_: dom.Event) => {
          updateSizeAndStyles()
          syncScroll()
        })

        // ResizeObserver to detect when the user resizes the field (e.g. textarea resize handle)
        if (js.typeOf(g.ResizeObserver) != "undefined") {
          val observer = new ResizeObserver((_, _) => {
            updateSizeAndStyles()
            syncScroll()
          })
          observer.observe(inputField)
          resizeObserverOpt = Some(observer)
        }
      }

      // Also listen on window resize
      EventSupport.addListener(dom.window, "resize", (_: dom.Event) => {
        updateSizeAndStyles()
        syncScroll()
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

      updateOrClearHighlights()
    }

    override def xformsGetValue(): String =
      valueOpt.getOrElse("")

    override def xformsUpdateValue(newValue: String): js.UndefOr[Nothing] = {
      valueOpt = Some(newValue)
      updateOrClearHighlights(valueOpt)
      js.undefined
    }

    override def xformsUpdateReadonly(readonly: Boolean): Unit =
      updateOrClearHighlights()

    override def destroy(): Unit = {
      EventSupport.clearAllListeners()
      resizeObserverOpt.foreach(_.disconnect())
      resizeObserverOpt = None
      pendingPromiseOpt = None
      wrapperElemOpt    = None
      fieldElemOpt      = None
      backdropElemOpt   = None
      highlightsElemOpt = None
      formatButtonOpt   = None
      valueOpt          = None
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
        updateOrClearHighlights(Some(formatted))
      }

    private def flashFormatError(): Unit =
      formatButtonOpt.foreach { button =>
        button.classList.add("fb-xpath-format-error")
        dom.window.setTimeout(() => {
          button.classList.remove("fb-xpath-format-error")
        }, 1000)
      }

    private def getFieldValue: String =
      fieldElemOpt match {
        case Some(input: dom.html.Input)       => input.value
        case Some(textarea: dom.html.TextArea) => textarea.value
        case Some(elem)                        => elem.textContent
        case None                              => ""
      }

    private def updateOrClearHighlights(valueOpt: Option[String] = None): Unit =
      updateHighlights(valueOpt)

    private def updateHighlights(valueOpt: Option[String] = None): Unit = {
      updateSizeAndStyles()
      syncScroll()
      highlightsElemOpt.foreach { highlightsElem =>
        val value           = valueOpt.getOrElse(getFieldValue)
        val highlightedHtml = XPathHighlighter.highlightToHtml(value)
        val finalHtml       = if (value.endsWith("\n")) highlightedHtml + " " else highlightedHtml
        highlightsElem.innerHTML = finalHtml
      }
    }

    private def updateSizeAndStyles(): Unit =
      for {
        fieldElem      <- fieldElemOpt
        backdropElem   <- backdropElemOpt
        highlightsElem <- highlightsElemOpt
      } locally {
        val width  = fieldElem.clientWidth
        val height = fieldElem.clientHeight

        if (width > 0 && height > 0) {
          val wrapperElem = wrapperElemOpt.getOrElse(containerElem)
          val wrapperRect = wrapperElem.getBoundingClientRect()
          val fieldRect   = fieldElem.getBoundingClientRect()

          val left = fieldRect.left - wrapperRect.left - wrapperElem.clientLeft + fieldElem.clientLeft
          val top  = fieldRect.top  - wrapperRect.top  - wrapperElem.clientTop  + fieldElem.clientTop

          backdropElem.style.left   = s"${left}px"
          backdropElem.style.top    = s"${top}px"
          backdropElem.style.width  = s"${width}px"
          backdropElem.style.height = s"${height}px"
        }

        val computed = dom.window.getComputedStyle(fieldElem)

        for (prop <- RelevantStyles) {
          val value = computed.getPropertyValue(prop)
          if (value != null && value.nonEmpty)
            highlightsElem.style.setProperty(prop, value)
        }
      }

    private def syncScroll(): Unit =
      for {
        fieldElem    <- fieldElemOpt
        backdropElem <- backdropElemOpt
      } locally {
        backdropElem.scrollTop  = fieldElem.scrollTop
        backdropElem.scrollLeft = fieldElem.scrollLeft
      }

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
