package org.orbeon.xbl

import org.orbeon.web.DomEventNames
import org.orbeon.web.DomSupport.DomElemOps
import org.orbeon.xforms.EventListenerSupport
import org.scalajs.dom
import org.scalajs.dom.{ResizeObserver, html}

import scala.scalajs.js
import scala.scalajs.js.Dynamic.global as g


object HighlightBackdrop {

  val RelevantStyles: List[String] = List(
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

  def apply(
    containerElem: html.Element,
    classPrefix  : String,
    onInput      : () => Unit = () => ()
  ): HighlightBackdrop = {

    val wrapperElemOpt = containerElem.querySelectorOpt(s".$classPrefix-wrapper")
    val fieldElemOpt =
      containerElem.queryNestedElems[html.Input]("input").headOption
        .orElse(containerElem.queryNestedElems[html.TextArea]("textarea").headOption)
        .orElse(Option(containerElem.querySelector("input, textarea")).map(_.asInstanceOf[html.Element]))
    val backdropElemOpt   = containerElem.querySelectorOpt(s".$classPrefix-backdrop")
    val highlightsElemOpt = containerElem.querySelectorOpt(s".$classPrefix-highlights")

    apply(
      containerElem     = containerElem,
      wrapperElemOpt    = wrapperElemOpt,
      fieldElemOpt      = fieldElemOpt,
      backdropElemOpt   = backdropElemOpt,
      highlightsElemOpt = highlightsElemOpt,
      onInput           = onInput
    )
  }

  def apply(
    containerElem    : html.Element,
    wrapperElemOpt   : Option[html.Element],
    fieldElemOpt     : Option[html.Element],
    backdropElemOpt  : Option[html.Element],
    highlightsElemOpt: Option[html.Element],
    onInput          : () => Unit
  ): HighlightBackdrop = {
    val backdrop = new HighlightBackdrop(
      containerElem     = containerElem,
      wrapperElemOpt    = wrapperElemOpt,
      fieldElemOpt      = fieldElemOpt,
      backdropElemOpt   = backdropElemOpt,
      highlightsElemOpt = highlightsElemOpt,
      onInput           = onInput
    )
    backdrop.init()
    backdrop
  }
}

class HighlightBackdrop(
  val containerElem    : html.Element,
  val wrapperElemOpt   : Option[html.Element],
  val fieldElemOpt     : Option[html.Element],
  val backdropElemOpt  : Option[html.Element],
  val highlightsElemOpt: Option[html.Element],
  onInput              : () => Unit
) extends EventListenerSupport {

  private var resizeObserverOpt: Option[ResizeObserver] = None

  def init(): Unit = {
    fieldElemOpt.foreach { fieldElem =>

      // Sync scroll between field and backdrop
      addListener(fieldElem, DomEventNames.Scroll, (_: dom.Event) => syncScroll())

      // Listen for key/input/focus events to update highlights dynamically
      addListeners(
        fieldElem,
        List(DomEventNames.Input, DomEventNames.KeyUp, DomEventNames.KeyDown, DomEventNames.Change, DomEventNames.FocusIn),
        (_: dom.Event) => onInput()
      )

      // Mouseup on field in case user finishes resizing with native handle
      addListener(fieldElem, "mouseup", (_: dom.Event) => {
        updateSizeAndStyles()
        syncScroll()
      })

      // ResizeObserver to detect when the user resizes the field (e.g. textarea resize handle)
      if (js.typeOf(g.ResizeObserver) != "undefined") {
        val observer = new ResizeObserver((_, _) => {
          updateSizeAndStyles()
          syncScroll()
        })
        observer.observe(fieldElem)
        resizeObserverOpt = Some(observer)
      }
    }

    // Also listen on window resize
    addListener(dom.window, "resize", (_: dom.Event) => {
      updateSizeAndStyles()
      syncScroll()
    })
  }

  def getFieldValue: String =
    fieldElemOpt match {
      case Some(input: html.Input)       => input.value
      case Some(textarea: html.TextArea) => textarea.value
      case Some(elem)                    => elem.textContent
      case None                          => ""
    }

  def updateHighlights(highlightFn: String => String, valueOpt: Option[String] = None): Unit = {
    updateSizeAndStyles()
    syncScroll()
    highlightsElemOpt.foreach { highlightsElem =>
      val value           = valueOpt.getOrElse(getFieldValue)
      val highlightedHtml = highlightFn(value)
      val finalHtml       = if (value.endsWith("\n")) highlightedHtml + " " else highlightedHtml
      highlightsElem.innerHTML = finalHtml
    }
  }

  def clearHighlights(): Unit =
    highlightsElemOpt.foreach(_.innerHTML = "")

  def updateSizeAndStyles(): Unit =
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

      for (prop <- HighlightBackdrop.RelevantStyles) {
        val value = computed.getPropertyValue(prop)
        if (value != null && value.nonEmpty)
          highlightsElem.style.setProperty(prop, value)
      }
    }

  def syncScroll(): Unit =
    for {
      fieldElem    <- fieldElemOpt
      backdropElem <- backdropElemOpt
    } locally {
      backdropElem.scrollTop  = fieldElem.scrollTop
      backdropElem.scrollLeft = fieldElem.scrollLeft
    }

  def destroy(): Unit = {
    clearAllListeners()
    resizeObserverOpt.foreach(_.disconnect())
    resizeObserverOpt = None
  }
}
