package org.orbeon.fr

import org.orbeon.fr.FormRunnerPrivateAPI.logger
import org.orbeon.oxf.fr.{ControlOps, Names}
import org.orbeon.web.DomSupport.*
import org.orbeon.xbl.FrWizard.WizardCompanion
import org.orbeon.xbl.Pager.PagerCompanion
import org.orbeon.xbl.{FrWizard, Pager}
import org.orbeon.xforms
import org.orbeon.xforms.*
import org.orbeon.xforms.facade.XBL
import org.scalajs.dom
import org.scalajs.dom.html
import org.scalajs.dom.html.Element

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import scala.scalajs.js.JSConverters.{JSRichFutureNonThenable, JSRichIterableOnce, JSRichOption}
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

  val wizard: FormRunnerFormWizardAPI = new FormRunnerFormWizardAPI(form)

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

  class Pager(
    private val form                : xforms.Form,
    private val _repeatedSectionName: String,
    private val pagerElem           : Element
  ) extends js.Object {

    def repeatedSectionName: String = _repeatedSectionName
    def itemFrom           : Int    = pagerDiv.dataset("from").toInt
    def itemTo             : Int    = pagerDiv.dataset("to").toInt
    def itemCount          : Int    = pagerDiv.dataset("searchTotal").toInt
    def pageSize           : Int    = pagerDiv.dataset("pageSize").toInt
    def pageNumber         : Int    = pagerDiv.dataset("pageNumber").toInt
    def pageCount          : Int    = pagerDiv.dataset("pageCount").toInt

    def setCurrentPage(page: Int): Unit =
      AjaxClient.fireEvent(
        AjaxEvent(
          eventName  = "fr-set-current-page",
          targetId   = pagerElem.id,
          form       = Some(form.elem),
          properties = Map("page-number" -> page),
        )
      )

    def addPageChangeListener(listener: js.Function1[Pager.PageChangeEvent, Any]): Unit =
      companionOpt.foreach(_.addPageChangeListener(listener))

    def removePageChangeListener(listener: js.Function1[Pager.PageChangeEvent, Any]): Unit =
      companionOpt.foreach(_.removePageChangeListener(listener))

    private def companionOpt: Option[PagerCompanion] =
      XBL.instanceForControl(pagerElem) match {
        case pagerCompanion: PagerCompanion =>
          Some(pagerCompanion)
        case _ =>
          logger.error(s"Pager: Couldn't find pager companion for section `${_repeatedSectionName}`")
          None
      }

    private def pagerDiv: Element =
      pagerElem.querySelector(".pagination").asInstanceOf[Element]
  }

  def getPager(repeatedSectionName: String): js.UndefOr[Pager] =
    getPagers()
      .find(_.repeatedSectionName == repeatedSectionName)
      .orUndefined

  def getPagers(): js.Array[Pager] = {
    val pagers =
      for {
        sectionElem         <- form.elem.querySelectorAll(".xbl-fr-section:not(.xforms-disabled)").toSeq
        pagerElem           <- sectionElem.querySelectorOpt(".xbl-fr-pager") // TODO: what if nesting?
        repeatedSectionName <- ControlOps.controlNameFromIdOpt(sectionElem.id)
      } yield new Pager(form, repeatedSectionName, pagerElem.asInstanceOf[html.Element])

    pagers.toJSArray
  }
}

class FormRunnerFormWizardAPI(private val form: xforms.Form) extends js.Object {

  def focus(
    controlName       : String,
    repeatIndexes     : js.UndefOr[js.Array[Int]] = js.undefined,
  ): js.Promise[Unit] = {

    AjaxClient.fireEvent(
      AjaxEvent(
        eventName  = "fr-wizard-focus",
        targetId   = Names.ViewComponent,
        form       = Some(form.elem),
        properties = Map(
          "fr-control-name"   -> controlName,
          "fr-repeat-indexes" -> (repeatIndexes.map(_.mkString(" ")).getOrElse(""): String)
        )
      )
    )
    AjaxClient.allEventsProcessedF("activateControl").toJSPromise
  }

  def addPageChangeListener(listener: js.Function1[FrWizard.PageChangeEvent, Any]): Unit =
    companionOpt.foreach(_.addPageChangeListener(listener))

  def removePageChangeListener(listener: js.Function1[FrWizard.PageChangeEvent, Any]): Unit =
    companionOpt.foreach(_.removePageChangeListener(listener))

  private def companionOpt: Option[WizardCompanion] =
    XBL.instanceForControl(
      form.elem.querySelectorOpt(".xbl-fr-wizard")
        .getOrElse(throw new IllegalArgumentException("no wizard element found"))
    ) match {
      case pagerCompanion: WizardCompanion =>
        Some(pagerCompanion)
      case _ =>
        logger.error(s"Wizard: Couldn't find wizard companion")
        None
    }
}

