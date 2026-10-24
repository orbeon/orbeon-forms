package org.orbeon.xbl

import org.orbeon.oxf.xforms.contentfilter.ContentFilter as CoreContentFilter
import org.orbeon.web.DomEventNames
import org.orbeon.web.DomSupport.DomElemOps
import org.orbeon.xforms.facade.XBLCompanion
import org.orbeon.xforms.{EventListenerSupport, XFormsXbl}
import org.scalajs.dom
import org.scalajs.dom.{ResizeObserver, html}

import scala.scalajs.js
import scala.scalajs.js.Dynamic.global as g


object ContentFilter {

  XFormsXbl.declareCompanion("fr|content-filter", js.constructorOf[ContentFilterCompanion])

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

  private class ContentFilterCompanion(containerElem: html.Element) extends XBLCompanion {

    private object EventSupport extends EventListenerSupport

    private var wrapperElemOpt   : Option[html.Element]   = None
    private var fieldElemOpt     : Option[html.Element]   = None
    private var backdropElemOpt  : Option[html.Element]   = None
    private var highlightsElemOpt: Option[html.Element]   = None
    private var valueOpt         : Option[String]         = None
    private var resizeObserverOpt: Option[ResizeObserver] = None

    override def init(): Unit = {

      wrapperElemOpt = containerElem.querySelectorOpt(".fr-content-filter-wrapper")
      fieldElemOpt =
        containerElem.queryNestedElems[html.Input]("input").headOption
          .orElse(containerElem.queryNestedElems[html.TextArea]("textarea").headOption)

      backdropElemOpt   = containerElem.querySelectorOpt(".fr-content-filter-backdrop")
      highlightsElemOpt = containerElem.querySelectorOpt(".fr-content-filter-highlights")

      fieldElemOpt.foreach { fieldElem =>

        // Sync scroll between field and backdrop
        EventSupport.addListener(fieldElem, DomEventNames.Scroll, (_: dom.Event) => syncScroll())

        // Listen for key/input/focus events to update highlights dynamically
        EventSupport.addListeners(
          fieldElem,
          List(DomEventNames.Input, DomEventNames.KeyUp, DomEventNames.KeyDown, DomEventNames.Change, DomEventNames.FocusIn),
          (_: dom.Event) => updateOrClearHighlights()
        )

        // Mouseup on field in case user finishes resizing with native handle
        EventSupport.addListener(fieldElem, "mouseup", (_: dom.Event) => {
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
      EventSupport.addListener(dom.window, "resize", (_: dom.Event) => {
        updateSizeAndStyles()
        syncScroll()
      })

      updateOrClearHighlights()
    }

    // Keep track of the value (`external-value` mode) so we can be notified when the control value changes for
    // example through a calculation. We can't simply get the nested field value, because this might have been updated
    // already, in which case the code which calls `xformsUpdateValue()` might not call `xformsUpdateValue()` again,
    // and we would not be notified of the change.
    override def xformsGetValue(): String = valueOpt.getOrElse("")

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
      wrapperElemOpt    = None
      fieldElemOpt      = None
      backdropElemOpt   = None
      highlightsElemOpt = None
      valueOpt          = None
    }

    private def getFieldValue: String =
      fieldElemOpt match {
        case Some(input: html.Input)       => input.value
        case Some(textarea: html.TextArea) => textarea.value
        case Some(elem)                    => elem.textContent
        case None                          => ""
      }

    private def updateOrClearHighlights(valueOpt: Option[String] = None): Unit =
      if (isMarkedReadonly)
        clearHighlights()
      else
        updateHighlights(valueOpt)

    private def updateHighlights(valueOpt: Option[String]): Unit = {
      updateSizeAndStyles()
      syncScroll()
      highlightsElemOpt.foreach { highlightsElem =>
        val value           = valueOpt.getOrElse(getFieldValue)
        val highlightedHtml = CoreContentFilter.highlightContent(value)
        val finalHtml       = if (value.endsWith("\n")) highlightedHtml + " " else highlightedHtml
        highlightsElem.innerHTML = finalHtml
      }
    }

    private def clearHighlights(): Unit =
      highlightsElemOpt.foreach(_.innerHTML = "")

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
  }
}
