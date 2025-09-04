package org.orbeon.fr

import org.log4s.Logger
import org.orbeon.oxf.fr.ControlOps
import org.orbeon.oxf.util.LoggerFactory
import org.orbeon.oxf.util.StringUtils.OrbeonStringOps
import org.orbeon.web.DomSupport.*
import org.orbeon.xbl.Pager
import org.orbeon.xbl.Pager.PagerCompanion
import org.orbeon.xforms
import org.orbeon.xforms.*
import org.orbeon.xforms.facade.XBL
import org.scalajs.dom
import org.scalajs.dom.html
import org.scalajs.dom.html.Element

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import scala.scalajs.js.JSConverters.{JSRichFutureNonThenable, JSRichIterableOnce, JSRichOption}
import scala.scalajs.js.annotation.JSExport
import scala.scalajs.js.|


// Form Runner-specific facade as we don't want to expose internal `xforms.Form` members
class FormRunnerForm(private val form: xforms.Form) extends js.Object {

  def addCallback(name: String, fn: js.Function): Unit =
    form.addCallback(name, fn)

  def removeCallback(name: String, fn: js.Function): Unit =
    form.removeCallback(name, fn)

  def isFormDataSafe(): Boolean =
    form.formDataSafe

  def activateProcessButton(buttonName: String): Unit = {

    def fromProcessButton =
      dom.document.querySelectorOpt(s".fr-buttons .xbl-fr-process-button .fr-$buttonName-button button")

    def fromDropTrigger =
      dom.document.querySelectorOpt(s".fr-buttons .xbl-fr-drop-trigger button.fr-$buttonName-button, .fr-buttons .xbl-fr-drop-trigger li a.fr-$buttonName-button")

    fromProcessButton
      .orElse(fromDropTrigger)
      .foreach(_.click())
  }

  def findControlsByName(controlName: String): js.Array[Element] =
    $(form.elem)
      .find(s".xforms-control[id *= '$controlName-control'], .xbl-component[id *= '$controlName-control']")
      .toArray collect {
      // The result must be an `html.Element` already
      case e: html.Element => e
    } filter {
      // Check the id matches the requested name
      e => (e.id ne null) && (ControlOps.controlNameFromIdOpt(XFormsId.getStaticIdFromId(e.id)) contains controlName)
    } toJSArray

  // - returns `undefined` if
  //   - the control is not found
  //   - the index is not in range
  //   - the control is an XBL component which doesn't support the JavaScript lifecycle
  // - returns a `Promise` that resolves when the server has processed the value change
  def setControlValue(
    controlName : String,
    controlValue: String | Int | Boolean,
    index       : js.UndefOr[Int] = js.undefined
  ): js.UndefOr[js.Promise[Unit]] =
    findControlMaybeNested(controlName, index)
      .map(DocumentAPI.setValue(_, controlValue.toString, form.elem, waitForServer = true))
      .orUndefined

  // - returns `undefined` if
  //   - the control is not found
  //   - the index is not in range
  //   - the control is not a trigger or input control
  // - returns a `Promise` that resolves when the server has processed the activation
  def activateControl(
    controlName: String,
    index      : js.UndefOr[Int] = js.undefined
  ): js.UndefOr[js.Promise[Unit]] =
    findControlsByName(controlName).lift(index.getOrElse(0)).map { e =>

      val elemToClickOpt =
        if (e.classList.contains("xforms-trigger"))
          e.querySelectorOpt("button")
        else if (e.classList.contains("xforms-input"))
          e.querySelectorOpt("input")
        else
          None

      elemToClickOpt.foreach(_.click())
      AjaxClient.allEventsProcessedF("activateControl").toJSPromise
    }
    .orUndefined

  // - returns `undefined` if
  //   - the control is not found
  //   - the index is not in range
  //   - the control doesn't support returning a value
  // - TODO: Array[String] for multiple selection controls
  def getControlValue(controlName: String, index: js.UndefOr[Int] = js.undefined): js.UndefOr[String] =
    findControlMaybeNested(controlName, index)
      .flatMap(DocumentAPI.getValue(_, form.elem).toOption)
      .orUndefined

  private def findControlMaybeNested(
    controlName: String,
    index      : js.UndefOr[Int] = js.undefined
  ): Option[Element] =
    findControlsByName(controlName).lift(index.getOrElse(0)).flatMap {
      case e if e.classList.contains("xbl-fr-dropdown-select1") =>
        e.querySelectorOpt(".xforms-select1")
      case e if XFormsXbl.isJavaScriptLifecycle(e) =>
        Some(e)
      case e if XFormsXbl.isComponent(e) =>
        // NOP, as the server will reject the value anyway in this case
        None
      case e =>
        Some(e)
    }

  class Pager(_repeatedSectionName: String, pagerElem: Element) {

    private val logger: Logger = LoggerFactory.createLogger("org.orbeon.fr.FormRunnerForm.Pager")

    @JSExport def repeatedSectionName: String          = _repeatedSectionName
    @JSExport def itemFrom           : js.UndefOr[Int] = undefOrInt("from")
    @JSExport def itemTo             : js.UndefOr[Int] = undefOrInt("to")
    @JSExport def itemCount          : js.UndefOr[Int] = undefOrInt("search-total")
    @JSExport def pageSize           : js.UndefOr[Int] = undefOrInt("page-size")
    @JSExport def pageNumber         : js.UndefOr[Int] = undefOrInt("page-number")
    @JSExport def pageCount          : js.UndefOr[Int] = undefOrInt("page-count")

    @JSExport
    def setCurrentPage(page: Int): Unit =
      AjaxClient.fireEvent(
        AjaxEvent(
          eventName  = "fr-set-current-page",
          targetId   = pagerElem.id,
          properties = Map("page-number" -> page),
        )
      )

    @JSExport
    def addPageChangeListener(listener: js.Function1[Pager.PageChangeEvent, Any]): Unit =
      pagerCompanionOpt.foreach(_.addPageChangeListener(listener))

    @JSExport
    def removePageChangeListener(listener: js.Function1[Pager.PageChangeEvent, Any]): Unit =
      pagerCompanionOpt.foreach(_.removePageChangeListener(listener))

    private def pagerCompanionOpt: Option[PagerCompanion] =
      XBL.instanceForControl(pagerElem) match {
        case pagerCompanion: PagerCompanion =>
          Some(pagerCompanion)
        case _ =>
          logger.error(s"Couldn't find pager companion for section ${_repeatedSectionName}")
          None
      }

    private def pagerDiv: Element =
      pagerElem.querySelector(".pagination").asInstanceOf[Element]

    private def undefOrInt(value: String): js.UndefOr[Int] =
      Option(pagerDiv.getAttribute(s"data-$value"))
        .filter(_.nonEmpty)
        .flatMap(_.toIntOption)
        .orUndefined
  }

  def getPager(repeatedSectionName: String): js.UndefOr[Pager] = {
    val pagerOpt =
      for {
        sectionElem <- dom.document.querySelectorOpt(s"#$repeatedSectionName-section")
        if ! sectionElem.classList.contains("xforms-disabled")
        pagerElem   <- sectionElem .querySelectorOpt(".xbl-fr-pager")
      } yield new Pager(repeatedSectionName, pagerElem)

    pagerOpt.orUndefined
  }

  def getPagers(): js.Array[Pager] = {
    val pagers =
      for {
        sectionElem <- dom.document.querySelectorAll("[id $= '-section']").toSeq
        if ! sectionElem.classList.contains("xforms-disabled")
        pagerElem   <- sectionElem.querySelectorOpt(".xbl-fr-pager")
      } yield new Pager(sectionElem.id.trimSuffixIfPresent("-section"), pagerElem.asInstanceOf[html.Element])

    pagers.toJSArray
  }
}
