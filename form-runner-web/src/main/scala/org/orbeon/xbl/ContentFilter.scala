package org.orbeon.xbl

import org.orbeon.oxf.xforms.contentfilter.ContentFilter as CoreContentFilter
import org.orbeon.web.DomEventNames
import org.orbeon.web.DomSupport.DomElemOps
import org.orbeon.xforms.facade.XBLCompanion
import org.orbeon.xforms.{EventListenerSupport, XFormsXbl}
import org.scalajs.dom
import org.scalajs.dom.html

import scala.scalajs.js


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
    "padding-top",
    "padding-right",
    "padding-bottom",
    "padding-left",
    "border-top-width",
    "border-right-width",
    "border-bottom-width",
    "border-left-width",
    "box-sizing",
    "white-space",
    "word-break",
    "overflow-wrap"
  )

  private class ContentFilterCompanion(containerElem: html.Element) extends XBLCompanion {

    private object EventSupport extends EventListenerSupport

    private var fieldElemOpt     : Option[html.Element] = None
    private var backdropElemOpt  : Option[html.Element] = None
    private var highlightsElemOpt: Option[html.Element] = None
    private var valueOpt          : Option[String]      = None

    override def init(): Unit = {

      fieldElemOpt =
        containerElem.queryNestedElems[html.Input]("input").headOption
          .orElse(containerElem.queryNestedElems[html.TextArea]("textarea").headOption)

      backdropElemOpt   = containerElem.querySelectorOpt(".fr-content-filter-backdrop")
      highlightsElemOpt = containerElem.querySelectorOpt(".fr-content-filter-highlights")

      fieldElemOpt.foreach { fieldElem =>

        // Sync scroll between field and backdrop
        EventSupport.addListener(fieldElem, DomEventNames.Scroll, (_: dom.Event) => syncScroll())

        // Listen for key/input events to update highlights dynamically
        EventSupport.addListeners(
          fieldElem,
          List(DomEventNames.Input, DomEventNames.KeyUp, DomEventNames.KeyDown, DomEventNames.Change),
          (_: dom.Event) => updateOrClearHighlights()
        )
      }

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
      updateSize()
      syncScroll()
      highlightsElemOpt.foreach { highlightsElem =>
        val value           = valueOpt.getOrElse(getFieldValue)
        val highlightedHtml = CoreContentFilter.highlightContent(value)
        highlightsElem.innerHTML = highlightedHtml
      }
    }

    private def clearHighlights(): Unit =
      highlightsElemOpt.foreach(_.innerHTML = "")

    private def updateSize(): Unit =
      for {
        fieldElem      <- fieldElemOpt
        backdropElem   <- backdropElemOpt
        highlightsElem <- highlightsElemOpt
      } locally {
        val width  = fieldElem.clientWidth
        val height = fieldElem.clientHeight
        if (width > 0)  backdropElem.style.width  = s"${width}px"
        if (height > 0) backdropElem.style.height = s"${height}px"

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
