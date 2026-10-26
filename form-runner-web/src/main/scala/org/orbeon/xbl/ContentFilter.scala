package org.orbeon.xbl

import org.orbeon.oxf.xforms.contentfilter.ContentFilter as CoreContentFilter
import org.orbeon.xforms.XFormsXbl
import org.orbeon.xforms.facade.XBLCompanion
import org.scalajs.dom.html

import scala.scalajs.js


object ContentFilter {

  XFormsXbl.declareCompanion("fr|content-filter", js.constructorOf[ContentFilterCompanion])

  private class ContentFilterCompanion(containerElem: html.Element) extends XBLCompanion {

    private var backdropOpt: Option[HighlightBackdrop] = None
    private var valueOpt   : Option[String]            = None

    override def init(): Unit = {
      val backdrop = HighlightBackdrop(
        containerElem = containerElem,
        classPrefix   = "fr-content-filter",
        onInput       = () => updateOrClearHighlights()
      )
      backdropOpt = Some(backdrop)
      updateOrClearHighlights()
    }

    // Keep track of the value (`external-value` mode) so we can be notified when the control value changes for
    // example through a calculation. We can't simply get the nested field value, because this might have been updated
    // already, in which case the code which calls `xformsUpdateValue()` might not call `xformsUpdateValue()` again,
    // and we would not be notified of the change.
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
      backdropOpt.foreach(_.destroy())
      backdropOpt = None
      valueOpt    = None
    }

    private def updateOrClearHighlights(valueOpt: Option[String] = None): Unit =
      backdropOpt.foreach { backdrop =>
        if (isMarkedReadonly)
          backdrop.clearHighlights()
        else
          backdrop.updateHighlights(CoreContentFilter.highlightContent(_), valueOpt)
      }
  }
}
