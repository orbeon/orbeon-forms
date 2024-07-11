/**
 * Copyright (C) 2020 Orbeon, Inc.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version
 *  2.1 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.xforms

import org.log4s.Logger
import org.orbeon.datatypes.BasicLocationData
import org.orbeon.dom.{Namespace, QName}
import org.orbeon.facades.HTMLDialogElement
import org.orbeon.polyfills.HTMLPolyfills._
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.util.LoggerFactory
import org.orbeon.oxf.util.MarkupUtils._
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.web.DomSupport
import org.orbeon.xforms.AjaxClient.fireEvent
import org.orbeon.xforms.Constants.LhhacSeparator
import org.orbeon.xforms.facade.{Controls, Events, Init, Utils, XBL}
import org.scalajs.dom
import org.scalajs.dom.experimental.URL
import org.scalajs.dom.experimental.domparser.{DOMParser, SupportedType}
import org.scalajs.dom.ext._
import org.scalajs.dom.html.{Input, Span}
import org.scalajs.dom.{MouseEvent, html, raw, window}
import org.scalajs.jquery.JQueryPromise
import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits._
import scalatags.JsDom
import scalatags.JsDom.all._
import shapeless.syntax.typeable._

import scala.collection.immutable
import scala.concurrent.duration.{span => _, _}
import scala.concurrent.{Future, Promise}
import scala.scalajs.js
import scala.scalajs.js.Dynamic.{global => g}
import scala.scalajs.js.JSConverters._
import scala.scalajs.js.annotation.{JSExport, JSExportTopLevel}
import scala.scalajs.js.timers.SetTimeoutHandle
import scala.scalajs.js.{JSON, timers, |}
import scala.util.control.NonFatal


// Progressively migrate contents of xforms.js/AjaxServer.js here
@JSExportTopLevel("OrbeonXFormsUi")
object XFormsUI {

  private val logger: Logger = LoggerFactory.createLogger("org.orbeon.xforms.XFormsUI")

  import Private._

  // Algorithm for a single repeated checkbox click:
  //
  // - always remember the last clicked checkbox, whether with shift or not or checked or not
  // - if an unchecked box is clicked with shift, ensure all the checkboxes in the range are unchecked
  // - if a checked box is clicked with shift, ensure all the checkboxes in the range are checked
  //
  // This matches what GMail does AFAICT.
  @JSExport
  def handleShiftSelection(clickEvent: MouseEvent, controlElem: html.Element): Unit =
    if (controlElem.classList.contains("xforms-select-appearance-full")) {
      // Only for "checkbox" controls
      val checkboxInputs = controlElem.getElementsByTagName("input")
      if (XFormsId.hasEffectiveIdSuffix(controlElem.id) && checkboxInputs.length == 1) {
        // Click on a single repeated checkbox

        val checkboxElem = checkboxInputs.head.asInstanceOf[html.Input]

        if (clickEvent.getModifierState("Shift")) {
          // Just got shift-selected

          val newCheckboxChecked = checkboxElem.checked

          def findControlIdsToUpdate(leftId: XFormsId, rightId: XFormsId): Option[immutable.IndexedSeq[String]] =
            if (leftId.isRepeatNeighbor(rightId) && leftId.iterations.last != rightId.iterations.last) {

              val indexes =
                if (leftId.iterations.last > rightId.iterations.last)
                  rightId.iterations.last + 1 to leftId.iterations.last
                else
                  leftId.iterations.last until rightId.iterations.last

              Some(indexes map (index => leftId.copy(iterations = leftId.iterations.init :+ index).toEffectiveId))
            } else {
              None
            }

          for {
            (lastControlElem, _) <- lastCheckboxChecked
            targetId             = XFormsId.fromEffectiveId(controlElem.id)
            controlIds           <- findControlIdsToUpdate(XFormsId.fromEffectiveId(lastControlElem.id), targetId)
            controlId            <- controlIds
            controlElem          <- Option(dom.document.getElementById(controlId))
            checkboxValue        <- if (newCheckboxChecked) nestedInputElems(controlElem).headOption.map(_.value) else Some("")
          } locally {
            DocumentAPI.setValue(controlId, checkboxValue)
          }
        }

        // Update selection no matter what
        lastCheckboxChecked = Some(controlElem -> checkboxElem)

      } else if (checkboxInputs.length > 1) {
        // Within a single group of checkboxes

        // LATER: could support click on `<span>` inside `<label>`
        clickEvent.target.narrowTo[html.Input] foreach { currentCheckboxElem =>
          if (clickEvent.getModifierState("Shift"))
            for {
              (lastControlElem, lastCheckboxElem) <- lastCheckboxChecked
              if lastControlElem.id == controlElem.id
              allCheckboxes                       = checkboxInputs.toList.map(_.asInstanceOf[html.Input])
              lastIndex                           = allCheckboxes.indexWhere(_.value == lastCheckboxElem.value)
              if lastIndex >= 0
              currentIndex                        = allCheckboxes.indexWhere(_.value == currentCheckboxElem.value)
              if currentIndex >= 0 && currentIndex != lastIndex
              curentIndex                         <- if (currentIndex < lastIndex) currentIndex + 1 to lastIndex else lastIndex until currentIndex
              checkboxToUpdate                    = allCheckboxes(curentIndex)
            } locally {
              checkboxToUpdate.checked = currentCheckboxElem.checked
            }

          // Update selection no matter what
          lastCheckboxChecked = Some(controlElem -> currentCheckboxElem)
        }
      } else {
        lastCheckboxChecked = None
      }
    } else {
      lastCheckboxChecked = None
    }

  // Update `xforms-selected`/`xforms-deselected` classes on the parent `<span>` element
  @JSExport
  def setRadioCheckboxClasses(target: html.Element): Unit = {
    for (checkboxInput <- nestedInputElems(target)) {
      var parentSpan = checkboxInput.parentNode.asInstanceOf[html.Element] // boolean checkboxes are directly inside a span
      if (parentSpan.tagName.equalsIgnoreCase("label"))
        parentSpan = parentSpan.parentNode.asInstanceOf[html.Element]      // while xf:select checkboxes have a label in between

      if (checkboxInput.checked) {
        parentSpan.classList.add("xforms-selected")
        parentSpan.classList.remove("xforms-deselected")
      } else {
        parentSpan.classList.add("xforms-deselected")
        parentSpan.classList.remove("xforms-selected")
      }
    }
  }

  @JSExport // 2020-04-27: 6 JavaScript usages from xforms.js
  var modalProgressPanelShown: Boolean = false

  // 2022-03-16: AjaxServer.js
  @JSExport
  def firstChildWithLocalName(node: raw.Element, name: String): js.UndefOr[raw.Element] =
    node.childNodes.collectFirst {
      case n: raw.Element if n.localName == name => n
    } .orUndefined

  // 2022-03-16: AjaxServer.js
  @JSExport
  def findDialogsToShow(controlValuesElems: js.Array[raw.Element]): js.Array[String] = {
    for {
      controlValuesElem <- controlValuesElems.iterator
      dialogElem        <- childrenWithLocalName(controlValuesElem, "dialog")
      visibleValue      <- attValueOpt(dialogElem, "visibility")
      if visibleValue == "visible"
      idValue           <- attValueOpt(dialogElem, "id")
    } yield
      idValue
  } .toJSArray

  // 2022-03-16: AjaxServer.js
  @JSExport
  def handleScriptElem(formID: String, scriptElem: raw.Element): Unit = {

    val functionName  = attValueOrThrow(scriptElem, "name")
    val targetId      = attValueOrThrow(scriptElem, "target-id")
    val observerId    = attValueOrThrow(scriptElem, "observer-id")
    val paramElements = childrenWithLocalName(scriptElem, "param")

    val paramValues = paramElements map (_.textContent.asInstanceOf[js.Any])

    ServerAPI.callUserScript(formID, functionName, targetId, observerId, paramValues.toList: _*)
  }

  // 2023-08-14: AjaxServer.js
  @JSExport
  def handleCallbackElem(formID: String, callbackElem: raw.Element): Unit =
    ServerAPI.callUserCallback(formID, attValueOrThrow(callbackElem, "name"))

  // 2022-03-16: AjaxServer.js
  @JSExport
  def handleDeleteRepeatElements(controlValuesElems: js.Array[raw.Element]): Unit =
    controlValuesElems.iterator foreach { controlValuesElem =>
      childrenWithLocalName(controlValuesElem, "delete-repeat-elements") foreach { deleteElem =>

        // Extract data from server response
        val deleteId      = attValueOrThrow(deleteElem, "id")
        val parentIndexes = attValueOrThrow(deleteElem, "parent-indexes")
        val count         = attValueOrThrow(deleteElem, "count").toInt

        // TODO: Server splits `deleteId`/`parentIndexes` and here we just put them back together!
        // TODO: `deleteId` is namespaced by the server; yet here we prepend `repeat-end`
        val repeatEnd =
          dom.document.getElementById("repeat-end-" + appendRepeatSuffix(deleteId, parentIndexes))

        // Find last element to delete
        var lastNodeToDelete: raw.Node = repeatEnd.previousSibling

        // Perform delete
        for (_ <- 0 until count) {
          var nestedRepeatLevel = 0
          var wasDelimiter = false
          while (! wasDelimiter) {
            lastNodeToDelete match {
              case lastElemToDelete: raw.Element =>

                val classList = lastElemToDelete.classList

                if (classList.contains("xforms-repeat-begin-end") && lastElemToDelete.id.startsWith("repeat-end-"))
                  nestedRepeatLevel += 1 // entering nested repeat
                else if (classList.contains("xforms-repeat-begin-end") && lastElemToDelete.id.startsWith("repeat-begin-"))
                  nestedRepeatLevel -=1 // exiting nested repeat
                else
                  wasDelimiter = nestedRepeatLevel == 0 && classList.contains("xforms-repeat-delimiter")

                // Since we are removing an element that can contain controls, remove the known server value
                lastElemToDelete.getElementsByClassName("xforms-control") foreach
                  (controlElem => ServerValueStore.remove(controlElem.id))

                // We also need to check this on the "root", as the `getElementsByClassName()` function only returns
                // sub-elements of the specified root and doesn't include the root in its search.
                if (lastElemToDelete.classList.contains("xforms-control"))
                  ServerValueStore.remove(lastElemToDelete.id)

              case _ => ()
            }
            val previous = lastNodeToDelete.previousSibling
            lastNodeToDelete.parentNode.removeChild(lastNodeToDelete)
            lastNodeToDelete = previous
          }
        }
      }
    }

  // 2022-03-16: AjaxServer.js
  @JSExport
  def handleInit(elem: raw.Element, controlsWithUpdatedItemsets: js.Dictionary[Boolean]): Unit = {

    val controlId       = attValueOrThrow(elem, "id")
    val documentElement = dom.document.getElementById(controlId).asInstanceOf[html.Element]
    val relevantOpt     = booleanAttValueOpt(elem, "relevant")
    val readonlyOpt     = booleanAttValueOpt(elem, "readonly")

    Option(XBL.instanceForControl(documentElement)) foreach { instance =>

      val becomesRelevant    = relevantOpt.contains(true)
      val becomesNonRelevant = relevantOpt.contains(false)

      def callXFormsUpdateReadonlyIfNeeded(): Unit =
        if (readonlyOpt.isDefined && XFormsXbl.isObjectWithMethod(instance, "xformsUpdateReadonly"))
          instance.xformsUpdateReadonly(readonlyOpt.contains(true))

      if (becomesRelevant) {
        // NOTE: We don't need to call this right now, because  this is done via `instanceForControl`
        // the first time. `init()` is guaranteed to be called only once. Obviously this is a little
        // bit confusing.
        // if (_.isFunction(instance.init))
        //     instance.init();
        callXFormsUpdateReadonlyIfNeeded()
      } else if (becomesNonRelevant) {
        // We ignore `readonly` when we become non-relevant
        // Our component subclass's `destroy()` removes "xforms-xbl-object" data as well
        instance.destroy()
      } else {
        // Stays relevant or non-relevant (but we should never be here if we are non-relevant)
        callXFormsUpdateReadonlyIfNeeded()
      }

      childrenWithLocalName(elem, "value") foreach { childNode =>
        handleValue(childNode, controlId, recreatedInput = false, controlsWithUpdatedItemsets)
      }
    }
  }

  // 2022-03-16: AjaxServer.js
  @JSExport
  def handleValues(
    controlElem                 : raw.Element,
    controlId                   : String,
    recreatedInput              : Boolean,
    controlsWithUpdatedItemsets : js.Dictionary[Boolean]
  ): Unit =
    childrenWithLocalName(controlElem, "value") foreach
      (handleValue(_, controlId, recreatedInput, controlsWithUpdatedItemsets))

  // 2022-04-12: AjaxServer.js
  @JSExport
  def handleDialog(
    controlElem : raw.Element,
    formId      : String
  ): Unit = {

    val id            = controlElem.id
    val visible       = controlElem.getAttribute("visibility") == "visible"
    val neighborIdOpt = controlElem.getAttribute("neighbor").trimAllToOpt

    if (visible)
      showDialog(id, neighborIdOpt, "handleDialog")
    else
      hideDialog(id, formId)
  }

  def showDialogForInit(dialogId: String, neighborIdOpt: Option[String]): Unit =
    showDialog(dialogId, neighborIdOpt, "showDialogForInitWithNeighbor")

  // 2022-04-12: AjaxServer.js
  @JSExport
  def handleAttribute(controlElem : raw.Element): Unit = {

    val newAttributeValue = controlElem.textContent
    val forAttribute      = controlElem.getAttribute("for")
    val nameAttribute     = controlElem.getAttribute("name")
    val htmlElement       = dom.document.getElementById(forAttribute)

    if (htmlElement != null)
      htmlElement.setAttribute(nameAttribute, newAttributeValue) // use case: xh:html/@lang but HTML fragment produced
  }

  // 2022-04-12: AjaxServer.js
  @JSExport
  def handleText(controlElem : raw.Element): Unit = {

    val newTextValue = controlElem.textContent
    val forAttribute = controlElem.getAttribute("for")
    val htmlElement = dom.document.getElementById(forAttribute)

    if (htmlElement != null && htmlElement.tagName.toLowerCase() == "title")
      dom.document.title = newTextValue
  }

  // 2022-04-12: AjaxServer.js
  @JSExport
  def handleRepeatIteration(
    controlElem : raw.Element,
    formID      : String
  ): Unit = {

    val repeatId    = controlElem.getAttribute("id")
    val iteration   = controlElem.getAttribute("iteration")
    val relevantOpt = attValueOpt(controlElem, "relevant")

    // Remove or add `xforms-disabled` on elements after this delimiter
    relevantOpt
      .map(_.toBoolean)
      .foreach(Controls.setRepeatIterationRelevance(formID, repeatId, iteration, _))
  }

  def maybeFutureToScalaFuture(promiseOrUndef: js.UndefOr[js.Promise[Unit] | JQueryPromise]): Future[Unit] = {

    val promiseOrUndefDyn = promiseOrUndef.asInstanceOf[js.Dynamic]

    if (XFormsXbl.isObjectWithMethod(promiseOrUndefDyn, "done")) {
      // JQuery future or similar
      val promise = Promise[Unit]()
      promiseOrUndef.asInstanceOf[JQueryPromise].done((() => promise.success(())): js.Function)
      promise.future
    } else if (XFormsXbl.isObjectWithMethod(promiseOrUndefDyn, "then")) {
      // JavaScript future
      promiseOrUndef.asInstanceOf[js.Promise[Unit]].toFuture
    } else {
      // Not a future
      Future.unit
    }
  }

  def handleValue(
    elem                        : raw.Element,
    controlId                   : String,
    recreatedInput              : Boolean,
    controlsWithUpdatedItemsets : js.Dictionary[Boolean]
  ): Unit = {

    val newControlValue  = elem.textContent
    val documentElement  = dom.document.getElementById(controlId).asInstanceOf[html.Element]

    def containsAnyOf(e: raw.Element, tokens: List[String]) = {
      val classList = e.classList
      tokens.exists(classList.contains)
    }

    if (containsAnyOf(documentElement, HandleValueIgnoredControls)) {
      logger.error(s"Got value from server for element with class: ${documentElement.getAttribute("class")}")
    } else {

      val normalizedPreviousServerValueOpt = Option(ServerValueStore.get(controlId)).map(_.normalizeSerializedHtml)
      val normalizedNewControlValue        = newControlValue.normalizeSerializedHtml
      ServerValueStore.set(controlId, newControlValue)

      if (containsAnyOf(documentElement, HandleValueOutputOnlyControls))
        Controls.setCurrentValue(documentElement, normalizedNewControlValue, force = false)
      else
        Controls.getCurrentValue(documentElement) foreach { currentValue =>

          val normalizedCurrentValue = currentValue.normalizeSerializedHtml

          val doUpdate =
            // If this was an input that was recreated because of a type change, we always set its value
            recreatedInput ||
            // If this is a control for which we recreated the itemset, we want to set its value
            controlsWithUpdatedItemsets.get(controlId).contains(true) ||
            (
              // Update only if the new value is different than the value already have in the HTML area
              normalizedCurrentValue != normalizedNewControlValue
                // Update only if the value in the control is the same now as it was when we sent it to the server,
                // so not to override a change done by the user since the control value was last sent to the server
                && (
                  normalizedPreviousServerValueOpt.isEmpty ||
                  normalizedPreviousServerValueOpt.contains(normalizedCurrentValue) ||
                  // For https://github.com/orbeon/orbeon-forms/issues/3130
                  //
                  // We would like to test for "becomes readonly", but test below is equivalent:
                  //
                  // - either the control was already readonly, so `currentValue != newControlValue` was `true`
                  //   as server wouldn't send a value otherwise
                  // - or it was readwrite and became readonly, in which case we test for this below
                  documentElement.classList.contains("xforms-readonly")
                )
              )

          if (doUpdate) {
            val promiseOrUndef = Controls.setCurrentValue(documentElement, normalizedNewControlValue, force = true)

            // Store the server value as the client sees it, not as the server sees it. There can be a difference in the following cases:
            //
            // 1) For HTML editors, the HTML might change once we put it in the DOM.
            // 2) For select/select1, if the server sends an out-of-range value, the actual value of the field won"t be the out
            //    of range value but the empty string.
            // 3) For boolean inputs, the server might tell us the new value is "" when the field becomes non-relevant, which is
            //    equivalent to "false".
            //
            // It is important to store in the serverValue the actual value of the field, otherwise if the server later sends a new
            // value for the field, since the current value is different from the server value, we will incorrectly think that the
            // user modified the field, and won"t update the field with the value provided by the AjaxServer.

            // `setCurrentValue()` may return a jQuery `Promise` and if it does we update the server value only once it is resolved.
            // For details see https://github.com/orbeon/orbeon-forms/issues/2670.

            maybeFutureToScalaFuture(promiseOrUndef) foreach { _ =>
              ServerValueStore.set(
                controlId,
                Controls.getCurrentValue(documentElement)
              )
            }
          }
        }
    }
  }

  // 2022-03-16: AjaxServer.js
  @JSExport
  def handleErrorsElem(formID: String, ignoreErrors: Boolean, errorsElem: raw.Element): Unit = {

    val serverErrors =
      errorsElem.childNodes collect { case n: raw.Element => n } map { errorElem =>
        // <xxf:error exception="org.orbeon.saxon.trans.XPathException" file="gaga.xhtml" line="24" col="12">
        //     Invalid date "foo" (Year is less than four digits)
        // </xxf:error>
        ServerError(
          message  = errorElem.textContent,
          location = attValueOpt(errorElem, "file") map { file =>
            BasicLocationData(
              file = file,
              line = attValueOpt(errorElem, "line").map(_.toInt).getOrElse(-1),
              col  = attValueOpt(errorElem, "col" ).map(_.toInt).getOrElse(-1)
            )
          },
          classOpt = attValueOpt(errorElem, "exception")
        )
      }

    AjaxClient.showError(
      titleString   = "Non-fatal error",
      detailsString = ServerError.errorsAsHtmlString(serverErrors),
      formId        = formID,
      ignoreErrors  = ignoreErrors
    )
  }

  @JSExport
  def handleSubmission(
    formID           : String,
    submissionElement: raw.Element,
    notifyReplace    : js.Function0[Unit]
  ): Unit = {

    val urlType         = attValueOrThrow(submissionElement, "url-type")
    val submissionId    = attValueOrThrow(submissionElement, "submission-id")

    val showProgressOpt = booleanAttValueOpt(submissionElement, "show-progress")
    val targetOpt       = attValueOpt(submissionElement, "target")


    val form = Page.getXFormsFormFromNamespacedIdOrThrow(formID)
    val formElem = form.elem

    // When the target is an iframe, we add a `?t=id` to work around a Chrome bug happening  when doing a POST to the
    // same page that was just loaded, gut that the POST returns a PDF. See:
    //
    // https://code.google.com/p/chromium/issues/detail?id=330687
    // https://github.com/orbeon/orbeon-forms/issues/1480
    //
    def updatedPath(path: String) =
      if (path.contains("xforms-server-submit")) {

        val isTargetAnIframe =
          targetOpt.flatMap(target => Option(dom.document.getElementById(target))).exists(_.tagName.equalsIgnoreCase("iframe"))

        if (isTargetAnIframe) {
          val url = new URL(path, dom.document.location.href)
          url.searchParams.delete("t")
          url.searchParams.append("t", java.util.UUID.randomUUID().toString)
          url.href
        } else {
          path
        }
      } else {
        path
      }

    val newTargetOpt =
      targetOpt flatMap {
        case target if ! js.isUndefined(window.asInstanceOf[js.Dynamic].selectDynamic(target)) =>
          // Pointing to a frame, so this won't open a new new window
          Some(target)
        case target =>
          // See if we're able to open a new window
          val targetButNotBlank =
            if (target == "_blank")
              Math.random().toString.substring(2) // use target name that we can reuse, in case opening the window works
            else
              target
          // Don't use "noopener" as we do need to use that window to test on it!
          val newWindow = window.open("about:blank", targetButNotBlank)
          if (! js.isUndefined(newWindow) && (newWindow ne null)) // unclear if can be `undefined` or `null` or both!
            Some(targetButNotBlank)
          else
            None
      }

    // Set or reset `target` attribute
    newTargetOpt match {
      case None            => formElem.removeAttribute("target")
      case Some(newTarget) => formElem.target = newTarget
    }

    formElem.action = updatedPath(
      (
        if (urlType == "action")
          form.xformsServerSubmitActionPath
        else
          form.xformsServerSubmitResourcePath
      ).getOrElse(dom.window.location.toString)
    )

    // Notify the caller (to handle the loading indicator)
    if (! showProgressOpt.contains(true))
      notifyReplace()

    // Remove possibly existing hidden fields just in case. In particular, the server should no longer include `$uuid`.
    Iterator(Constants.UuidFieldName, Constants.SubmissionIdFieldName) foreach { name =>
      formElem.querySelectorAll(s":scope > input[type = 'hidden'][name = '$name']")
      .toJSArray
      .foreach(e => e.parentNode.removeChild(e))
    }

    val inputElemsToAddAndRemove = {

      def createHiddenInput(name: String, value: String) =
        dom.document
          .createElement("input")
          .asInstanceOf[html.Input] |!>
          (_.`type` = "hidden")     |!>
          (_.name   = name)         |!>
          (_.value  = value)

      List(
        createHiddenInput(Constants.UuidFieldName,         form.uuid),
        createHiddenInput(Constants.SubmissionIdFieldName, submissionId)
      )
    }

    formElem.asInstanceOf[ElementExt].prepend(inputElemsToAddAndRemove: _*)

    try {
      formElem.submit()
    } catch {
      case NonFatal(t) =>
        // NOP: This is to prevent the error "Unspecified error" in IE. This can
        // happen when navigating away is cancelled by the user pressing cancel
        // on a dialog displayed on unload.
        // 2022-08-01: We no longer support IE, so if indeed this only happened with
        // IE we could remove this code. Adding logging to see if this ever happens.
        logger.warn(s"`requestForm.submit()` caused an error: ${t.getMessage}")
    }

    inputElemsToAddAndRemove foreach { inputElem =>
      inputElem.parentElement.removeChild(inputElem)
    }
  }

  @JSExport // 2020-04-27: 1 JavaScript usage
  def displayModalProgressPanel(): Unit =
    if (! modalProgressPanelShown) {

      modalProgressPanelShown = true

      // Take out the focus from the current control
      // See https://github.com/orbeon/orbeon-forms/issues/4511
      val focusControlIdOpt =
        Option(Globals.currentFocusControlId) map { focusControlId =>
          Controls.removeFocus(focusControlId)
          focusControlId
        }

      val timerIdOpt =
        if (Utils.isIOS() && Utils.getZoomLevel() != 1.0) {
          Utils.resetIOSZoom()
            Some(
              timers.setTimeout(200.milliseconds) {
                showModalProgressPanelRaw()
              }
            )
        } else {
          showModalProgressPanelRaw()
          None
        }

      AjaxClient.ajaxResponseReceivedForCurrentEventQueueF("modal panel") foreach { details =>

        // Hide the modal progress panel, unless the server tells us to do a submission or load, so we don't want
        // to remove it otherwise users could start interacting with a page which is going to be replaced shortly.
        //
        // We remove the modal progress panel before handling DOM response, as script actions may dispatch
        // events and we don't want them to be filtered. If there are server events, we don't remove the
        // panel until they have been processed, i.e. the request sending the server events returns.
        val mustHideModalPanel =
          ! (
            // `exists((//xxf:submission, //xxf:load)[empty(@target) and empty(@show-progress)])`
            details.responseXML.getElementsByTagNameNS(Namespaces.XXF, "submission").iterator ++
              details.responseXML.getElementsByTagNameNS(Namespaces.XXF, "load").iterator exists
              (e => ! e.hasAttribute("target") && e.getAttribute("show-progress") != "false")
          )

        if (mustHideModalPanel)
          hideModalProgressPanel(timerIdOpt, focusControlIdOpt)
      }
    }

  def showModalProgressPanelImmediate(): Unit =
    showModalProgressPanelRaw()

  def hideModalProgressPanelImmediate(): Unit =
    hideModalProgressPanelRaw()

  trait ItemsetItem extends js.Object {
    val attributes: js.UndefOr[js.Dictionary[String]]
    val children  : js.UndefOr[js.Array[ItemsetItem]]
    val label     : js.UndefOr[String]
    val value     : String
    val help      : js.UndefOr[String]
    val hint      : js.UndefOr[String]
  }

  private def updateItemset(
    documentElement: html.Element,
    controlId      : String,
    itemsetTree    : js.Array[ItemsetItem],
    groupName      : Option[String]
  ): Unit =
    if (
      documentElement.classList.contains("xforms-select1-appearance-compact") ||
      documentElement.classList.contains("xforms-select-appearance-compact")  ||
      documentElement.classList.contains("xforms-select1-appearance-minimal")
    )
      updateSelectItemset(documentElement, itemsetTree)
    else
      updateFullAppearanceItemset(
        documentElement  = documentElement,
        into             = documentElement.querySelector("span.xforms-items").asInstanceOf[html.Element],
        afterOpt         = None,
        controlId        = controlId,
        itemsetTree      = itemsetTree,
        isSelect         = documentElement.classList.contains("xforms-select"),
        groupNameOpt     = groupName,
        clearChildrenOpt = Some(_.replaceChildren())
      )

  @JSExport
  def handleControl(
    elem                       : raw.Element,
    recreatedInputs            : js.Dictionary[html.Element],
    controlsWithUpdatedItemsets: js.Dictionary[Boolean],
    formID                     : String
  ): Unit = {

    val controlId           = attValueOrThrow(elem, "id")
    val staticReadonlyOpt   = attValueOpt(elem, "static")
    val relevantOpt         = booleanAttValueOpt(elem, "relevant")
    val readonlyOpt         = booleanAttValueOpt(elem, "readonly")
    val requiredOpt         = booleanAttValueOpt(elem, "required")
    val classesOpt          = attValueOpt(elem, "class")
    val newLevelOpt         = attValueOpt(elem, "level")
    val progressStateOpt    = attValueOpt(elem, "progress-state")
    val progressReceivedOpt = attValueOpt(elem, "progress-received")
    val progressExpectedOpt = attValueOpt(elem, "progress-expected")
    val newSchemaTypeOpt    = attValueOpt(elem, "type")
    val newVisitedOpt       = booleanAttValueOpt(elem, "visited")

    var documentElement = dom.document.getElementById(controlId).asInstanceOf[html.Element]

    // Done to fix #2935; can be removed when we have taken care of #2940
    if (documentElement == null && (controlId  == "fb-static-upload-empty" || controlId == "fb-static-upload-non-empty"))
      return

    if (documentElement == null) {
      documentElement = dom.document.getElementById(s"group-begin-$controlId").asInstanceOf[html.Element]
      if (documentElement == null) {
        logger.error(s"Can't find element or iteration with ID '$controlId'")
        // TODO: throw?
      }
    }

    val isLeafControl = documentElement.classList.contains("xforms-control")

    // TODO: 2024-05-27: Unsure if this should ever kick in or if the logic is up to date!
    var isStaticReadonly = documentElement.classList.contains("xforms-static")
    val newDocumentElement =
      maybeMigrateToStatic(
        documentElement,
        controlId,
        staticReadonlyOpt,
        isLeafControl
      )
    if (newDocumentElement ne documentElement) {
      documentElement = newDocumentElement
      isStaticReadonly = true
    }

    // We update the relevance and readonly before we update the value. If we don't, updating the value
    // can fail on IE in some cases. (The details about this issue have been lost.)

    // Handle becoming relevant
    if (relevantOpt.contains(true))
      Controls.setRelevant(documentElement, relevant = true)

    val recreatedInput = maybeUpdateSchemaType(documentElement, controlId, newSchemaTypeOpt, formID)
    if (recreatedInput)
      recreatedInputs(controlId) = documentElement

    // Handle required
    requiredOpt.foreach {
      case true  => documentElement.classList.add("xforms-required")
      case false => documentElement.classList.remove("xforms-required")
    }

    // Handle readonly
    if (! isStaticReadonly)
      readonlyOpt
        .foreach(Controls.setReadonly(documentElement, _))

    // Handle updates to custom classes
    classesOpt.foreach { classes =>
      classes.splitTo[List]().foreach { currentClass =>
        if (currentClass.startsWith("-"))
          documentElement.classList.remove(currentClass.substring(1))
        else {
          // '+' is optional
          documentElement.classList.add(
            if (currentClass.charAt(0) == '+')
              currentClass.substring(1)
            else
              currentClass
          )
        }
      }
    }

    // Update the `required-empty`/`required-full` even if the required has not changed or
    // is not specified as the value may have changed
    if (! isStaticReadonly)
      attValueOpt(elem, "empty")
        .foreach(Controls.updateRequiredEmpty(documentElement, _))

    // Custom attributes on controls
    if (isLeafControl)
      updateCustomAttributes(documentElement, elem, requiredOpt, newVisitedOpt, newLevelOpt)

    updateControlAttributes(
      documentElement,
      elem,
      newLevelOpt,
      progressStateOpt,
      progressReceivedOpt,
      progressExpectedOpt,
      newVisitedOpt,
      controlId,
      recreatedInput,
      controlsWithUpdatedItemsets,
      relevantOpt
    )
  }

  private def updateControlAttributes(
    documentElement            : html.Element,
    elem                       : raw.Element,
    newLevelOpt                : Option[String],
    progressStateOpt           : Option[String],
    progressReceivedOpt        : Option[String],
    progressExpectedOpt        : Option[String],
    newVisitedOpt              : Option[Boolean],
    controlId                  : String,
    recreatedInput             : Boolean,
    controlsWithUpdatedItemsets: js.Dictionary[Boolean],
    relevantOpt                : Option[Boolean]
  ): Unit = {

    // Store new label message in control attribute
    attValueOpt(elem, "label") foreach { newLabel =>
      Controls.setLabelMessage(documentElement, newLabel)
    }

    // Store new hint message in control attribute
    // See also https://github.com/orbeon/orbeon-forms/issues/3561
    attValueOpt(elem, "hint") match {
      case Some(newHint) =>
        Controls.setHintMessage(documentElement, newHint)
      case None =>
        attValueOpt(elem, "title") foreach { newTitle =>
          Controls.setHintMessage(documentElement, newTitle)
        }
    }

    // Store new help message in control attribute
    attValueOpt(elem, "help") foreach { newHelp =>
      Controls.setHelpMessage(documentElement, newHelp)
    }

    // Store new alert message in control attribute
    attValueOpt(elem, "alert") foreach { newAlert =>
      Controls.setAlertMessage(documentElement, newAlert)
    }

    // Store validity, label, hint, help in element
    newLevelOpt
      .foreach(Controls.setConstraintLevel(documentElement, _))

    // Handle progress for upload controls
    // The attribute `progress-expected="…"` could be missing, even if we have a
    // progress-received="50591"` (see `expectedSize` which is an `Option` in `UploadProgress`),
    // in which case we don't have a "progress" to report to the progress bar.
    (progressStateOpt, progressExpectedOpt) match {
      case (Some(progressState), Some(progressExpected)) if progressState.nonAllBlank && progressExpected.nonAllBlank =>
        Page.getUploadControl(documentElement).progress(
        progressState,
        progressReceivedOpt.getOrElse(throw new IllegalArgumentException).toInt,
        progressExpected.toInt
      )
      case _ =>
    }

    // Handle visited flag
    newVisitedOpt
      .foreach(Controls.updateVisited(documentElement, _))

    // Nested elements
    elem.children.foreach { childNode =>
      Support.getLocalName(childNode) match {
        case "itemset" => handleItemset(childNode, controlId, controlsWithUpdatedItemsets)
        case "case"    => handleSwitchCase(childNode)
        case _         =>
      }
    }

    // Must handle `value` after `itemset`
    handleValues(elem, controlId, recreatedInput, controlsWithUpdatedItemsets)

    // Handle becoming non-relevant after everything so that XBL companion class instances
    // are nulled and can be garbage-collected
    if (relevantOpt.contains(false))
      Controls.setRelevant(documentElement, relevant = false)
  }

  private def handleItemset(elem: raw.Element, controlId: String, controlsWithUpdatedItemsets : js.Dictionary[Boolean]): Unit = {

    val itemsetTree     = Option(JSON.parse(elem.textContent).asInstanceOf[js.Array[ItemsetItem]]).getOrElse(js.Array())
    val documentElement = dom.document.getElementById(controlId).asInstanceOf[html.Element]
    val groupName       = attValueOpt(elem, "group")

    controlsWithUpdatedItemsets(controlId) = true

    updateItemset(documentElement, controlId, itemsetTree, groupName)

    // Call legacy global custom listener if any
    if (js.typeOf(g.xformsItemsetUpdatedListener) != "undefined")
      g.xformsItemsetUpdatedListener.asInstanceOf[js.Function2[String, js.Array[ItemsetItem], js.Any]]
        .apply(controlId, itemsetTree)
  }

  private def handleSwitchCase(elem: raw.Element): Unit = {
    val id      = attValueOrThrow(elem, "id")
    val visible = attValueOrThrow(elem, "visibility") == "visible"
    Controls.toggleCase(id, visible)
  }

  private def maybeMigrateToStatic(
    documentElement  : html.Element,
    controlId        : String,
    staticReadonlyOpt: Option[String],
    isLeafControl    : Boolean
  ): html.Element =
    if (! documentElement.classList.contains("xforms-static") && staticReadonlyOpt.map(_.toBoolean).exists(_ == true)) {
      if (isLeafControl) {
        val parentElement = documentElement.parentNode.asInstanceOf[html.Element]
        val newDocumentElement = dom.document.createElement("span").asInstanceOf[html.Element]
        newDocumentElement.setAttribute("id", controlId)

        newDocumentElement.classList = documentElement.classList;
        newDocumentElement.classList.add("xforms-static")
        parentElement.replaceChild(newDocumentElement, documentElement)

        Controls.getControlLHHA(newDocumentElement, "alert")
          .foreach(parentElement.removeChild)

        Controls.getControlLHHA(newDocumentElement, "hint")
          .foreach(parentElement.removeChild)

          newDocumentElement
      } else {
        documentElement.classList.add("xforms-static")
        documentElement
      }
    } else {
      documentElement
    }

  private val AriaControlClasses = Set(
    "xforms-input",
    "xforms-textarea",
    "xforms-secret",
    "xforms-select1-appearance-compact", // .xforms-select1
    "xforms-select1-appearance-minimal"  // .xforms-select1
  )

  private def updateCustomAttributes(
    documentElement: html.Element,
    elem           : raw.Element,
    requiredOpt    : Option[Boolean],
    newVisitedOpt  : Option[Boolean],
    newLevelOpt    : Option[String],
  ): Unit = {
    if (AriaControlClasses.exists(documentElement.classList.contains)) {
      Option(documentElement.querySelector("input, textarea, select").asInstanceOf[html.Element]).foreach { firstInput =>

        requiredOpt.foreach {
          case true  => firstInput.setAttribute("aria-required", true.toString)
          case false => firstInput.removeAttribute("aria-required")
        }

        val visited = newVisitedOpt.getOrElse(documentElement.classList.contains("xforms-visited"))
        val invalid = newLevelOpt.map(_ == "error").getOrElse(documentElement.classList.contains("xforms-invalid"))

        if (invalid && visited)
          firstInput.setAttribute("aria-invalid", true.toString)
        else
          firstInput.removeAttribute("aria-invalid")
      }
    }

    if (documentElement.classList.contains("xforms-upload")) {
      // Additional attributes for xf:upload
      // <xxf:control id="xforms-control-id"
      //    state="empty|file"
      //    accept=".txt"
      //    filename="filename.txt" mediatype="text/plain" size="23kb"/>

      val fileNameSpan  = documentElement.querySelector(".xforms-upload-filename")
      val mediatypeSpan = documentElement.querySelector(".xforms-upload-mediatype")
      val sizeSpan      = documentElement.querySelector(".xforms-upload-size")
      val uploadSelect  = documentElement.querySelector(".xforms-upload-select").asInstanceOf[html.Input]

      // Set values in DOM
      val upload = Page.getUploadControl(documentElement)

      val state     = attValueOpt(elem, "state")
      val fileName  = attValueOpt(elem, "filename")
      val mediatype = attValueOpt(elem, "mediatype")
      val size      = attValueOpt(elem, "size")
      val accept    = attValueOpt(elem, "accept")

      state.foreach(upload.setState)
      fileName .foreach(fileNameSpan .textContent = _)
      mediatype.foreach(mediatypeSpan.textContent = _)
      size     .foreach(sizeSpan     .textContent = _)
      // NOTE: Server can send a space-separated value but `accept` expects a comma-separated value
      accept.foreach(a => uploadSelect.accept = a.splitTo[List]().mkString(","))

    } else if (documentElement.classList.contains("xforms-output") || documentElement.classList.contains("xforms-static")) {

      attValueOpt(elem, "alt").foreach { alt =>
        if (documentElement.classList.contains("xforms-mediatype-image")) {
          val img = documentElement.querySelector(":scope > img").asInstanceOf[html.Image]
          img.alt = alt
        }
      }

      if (documentElement.classList.contains("xforms-output-appearance-xxforms-download")) {
        attValueOpt(elem, "download").foreach { download =>
          val aElem = documentElement.querySelector(".xforms-output-output").asInstanceOf[html.Anchor]
          aElem.asInstanceOf[js.Dynamic].download = download
        }
      }
    } else if (documentElement.classList.contains("xforms-trigger") || documentElement.classList.contains("xforms-submit")) {
        // It isn't a control that can hold a value (e.g. trigger) and there is no point in trying to update it
        // NOP
    } else if (documentElement.classList.contains("xforms-input") || documentElement.classList.contains("xforms-secret")) {
        // Additional attributes for xf:input and xf:secret

      val inputSize         = attValueOpt(elem, "size")
      val maxlength         = attValueOpt(elem, "maxlength")
      val inputAutocomplete = attValueOpt(elem, "autocomplete")

      // NOTE: Below, we consider an empty value as an indication to remove the attribute. May or may not be
      // the best thing to do.

      val input = documentElement.querySelector("input").asInstanceOf[html.Input]

      inputSize.foreach { size =>
        if (size.isEmpty)
          input.removeAttribute("size")
        else
          input.size = size.toInt
      }

      maxlength.foreach { maxlength =>
        if (maxlength.isEmpty)
          input.removeAttribute("maxlength")
        else
          input.maxLength = maxlength.toInt
      }

      inputAutocomplete.foreach { inputAutocomplete =>
        if (inputAutocomplete.isEmpty)
          input.removeAttribute("autocomplete")
        else
          input.autocomplete = inputAutocomplete
      }

    } else if (documentElement.classList.contains("xforms-textarea")) {
      // Additional attributes for xf:textarea

      val maxlength    = attValueOpt(elem, "maxlength")
      val textareaCols = attValueOpt(elem, "cols")
      val textareaRows = attValueOpt(elem, "rows")

      val textarea = documentElement.querySelector("textarea").asInstanceOf[html.TextArea]

      // NOTE: Below, we consider an empty value as an indication to remove the attribute. May or may not be
      // the best thing to do.
      maxlength.foreach { maxlength =>
        if (maxlength.isEmpty)
          textarea.removeAttribute("maxlength")
        else
          textarea.maxLength = maxlength.toInt
      }

      textareaCols.foreach { textareaCols =>
        if (textareaCols.isEmpty)
          textarea.removeAttribute("cols")
        else
          textarea.cols = textareaCols.toInt
      }

      textareaRows.foreach { textareaRows =>
        if (textareaRows.isEmpty)
          textarea.removeAttribute("rows")
        else
          textarea.rows = textareaRows.toInt
      }
    }
  }

  private val TypePrefix = "xforms-type-"

  private val TypeCssClassToInputType: Map[String, InputType] = Map(
    s"${TypePrefix}boolean" -> InputType.Boolean,
    s"${TypePrefix}string"  -> InputType.String
  )

  private val LhhaClasses = List(
    "xforms-label",
    "xforms-help",
    "xforms-hint",
    "xforms-alert"
  )

  private sealed trait InputType
  private object InputType {
    case object Boolean extends InputType
    case object String  extends InputType
  }

  private def maybeUpdateSchemaType(
    documentElement: html.Element,
    controlId      : String,
    newSchemaType  : Option[String],
    formID         : String,
  ): Boolean =
    newSchemaType match  {
        case Some(newSchemaType) =>

        val newSchemaTypeQName =
          newSchemaType.trimAllToOpt match {
            case None    => Names.XsString
            case Some(s) => QName.fromClarkName(s).getOrElse(throw new IllegalArgumentException(s"Invalid schema type: `$s`"))
          }

        lazy val newInputType =
          newSchemaTypeQName match {
            case Names.XsBoolean | Names.XfBoolean => InputType.Boolean
            case _                                 => InputType.String
          }

        lazy val existingInputType =
          TypeCssClassToInputType
            .collectFirst { case (className, inputType) if documentElement.classList.contains(className) => inputType }
            .getOrElse(InputType.String)

        val mustUpdateInputType =
          documentElement.classList.contains("xforms-input") && existingInputType != newInputType

        if (mustUpdateInputType)
          updateInputType(documentElement, controlId, newInputType, formID)

        // Remove existing CSS `xforms-type-*` classes
        documentElement
          .className
          .splitTo[List]()
          .filter(_.startsWith(TypePrefix))
          .foreach(documentElement.classList.remove)

        // Add new CSS `xforms-type-*` class
        newSchemaTypeQName match { case QName(localName, Namespace(_, uri)) =>
          val isBuiltIn = uri == Namespaces.XS || uri == Namespaces.XF
          val newClass = s"$TypePrefix${if (isBuiltIn) "" else "custom-"}$localName"
          documentElement.classList.add(newClass)
        }

        mustUpdateInputType
      case None =>
        false
    }

  private def updateInputType(
    documentElement: html.Element,
    controlId      : String,
    newInputType   : InputType,
    formID         : String
  ): Unit = {

    // Find the position of the last LHHA before the control "actual content"
    // A value of -1 means that the content came before any label
    val lastLhhaPosition =
      documentElement.children.prefixLength(e => LhhaClasses.exists(e.classList.contains)) - 1

    // Remove all elements that are not labels
    documentElement.children.filter(e => ! LhhaClasses.exists(e.classList.contains))
      .foreach(documentElement.removeChild)

    val inputLabelElement = Controls.getControlLHHA(documentElement, "label").asInstanceOf[html.Label]

    newInputType match {
      case InputType.Boolean =>
        updateBooleanInput(documentElement, controlId, lastLhhaPosition, inputLabelElement)
      case InputType.String =>
        updateStringInput(documentElement, controlId, formID, lastLhhaPosition, inputLabelElement)
    }
  }

  private def updateStringInput(
    documentElement  : html.Element,
    controlId        : String,
    formId           : String,
    lastLhhaPosition : Int,
    inputLabelElement: html.Label,
  ): Unit = {

    def insertIntoDocument(nodes: List[html.Element], lastLhhaPosition: Int): Unit = {
      val childElements = documentElement.children
      if (childElements.isEmpty)
        documentElement.asInstanceOf[ElementExt].append(nodes: _*)
      else if (lastLhhaPosition == -1)
        documentElement.asInstanceOf[ElementExt].prepend(nodes: _*)
      else
        childElements(lastLhhaPosition).asInstanceOf[ElementExt].after(nodes: _*)
    }

    def createNewInputElem(typeClassName: String): html.Input = {
      val newInputElementId = XFormsId.appendToEffectiveId(controlId, "$xforms-input-1")
      input(
        tpe := "text",
        cls := s"xforms-input-input $typeClassName",
        id  := newInputElementId,
        name := Page.deNamespaceIdIfNeeded(formId, newInputElementId) // in portlet mode, name is not prefixed
      ).render
    }

    val newStringInput = createNewInputElem("xforms-type-string")
    insertIntoDocument(List(newStringInput), lastLhhaPosition)
    inputLabelElement.htmlFor = newStringInput.id
  }

  private def updateBooleanInput(
    documentElement  : html.Element,
    controlId        : String,
    lastLabelPosition: Int,
    inputLabelElement: html.Label
  ): Unit = {
    updateFullAppearanceItemset(
      documentElement  = documentElement,
      into             = documentElement,
      afterOpt         = (lastLabelPosition >= 0).flatOption((documentElement.children(lastLabelPosition).asInstanceOf[html.Element]: js.UndefOr[html.Element]).toOption),
      controlId        = controlId,
      itemsetTree      = js.Array(
        new ItemsetItem {
          val attributes = js.undefined // js.UndefOr[js.Dictionary[String]]
          val children   = js.undefined
          val label      = js.undefined
          val value      = true.toString
          val help       = js.undefined
          val hint       = js.undefined
        }
      ),
      isSelect         = true,
      groupNameOpt     = None,
      clearChildrenOpt = None // items have already been removed
    )

    Option(inputLabelElement)
      .foreach(_.htmlFor = XFormsId.appendToEffectiveId(controlId, LhhacSeparator + "e0"))
  }

  // TODO:
  //  - Resolve nested `optgroup`s.
  //  - use direct serialization/deserialization instead of custom JSON.
  //    See `XFormsSelect1Control.outputAjaxDiffUseClientValue()`.

  private def updateSelectItemset(
    documentElement: html.Element,
    itemsetTree    : js.Array[ItemsetItem]
  ): Unit =
    ((documentElement.getElementsByTagName("select")(0): js.UndefOr[raw.Element]): Any) match {
        case select: html.Select =>

          val selectedValues =
            select.options.filter(_.selected).map(_.value)

          def generateItem(itemElement: ItemsetItem): html.Element = {

            val classOpt = itemElement.attributes.toOption.flatMap(_.get("class"))

            itemElement.children.toOption match {
              case None =>
                dom.document.createElement("option").asInstanceOf[html.Option].kestrel { option =>
                  option.value     = itemElement.value
                  option.selected  = selectedValues.contains(itemElement.value)
                  option.innerHTML = itemElement.label.getOrElse("")
                  classOpt.foreach(option.className = _)
                }
              case Some(children) =>
                dom.document.createElement("optgroup").asInstanceOf[html.OptGroup].kestrel { optgroup =>
                  optgroup.label = itemElement.label.getOrElse("")
                  classOpt.foreach(optgroup.className = _)
                  optgroup.replaceChildren(children.toList.map(generateItem): _*)
                }
            }
          }

          select.replaceChildren(itemsetTree.toList.map(generateItem): _*)

        case _ =>
          // This should not happen but if it does we'd like to know about it without entirely stopping the
          // update process so we give the user a chance to recover the form. This should be generalized
          // once we have migrated `AjaxServer.js` entirely to Scala.
          logger.error(s"`<select>` element not found when attempting to update itemset")
      }

  // Concrete scenarios:
  //
  // - insert into the `span.xforms-items` element (replacing all existing content)
  // - insert into the control's container element at the beginning
  // - insert into the control's container element after a given element
  private def updateFullAppearanceItemset(
    documentElement : html.Element,
    into            : html.Element, // this must be the container of the item elements
    afterOpt        : Option[html.Element],
    controlId       : String,
    itemsetTree     : js.Array[ItemsetItem],
    isSelect        : Boolean,
    groupNameOpt    : Option[String],
    clearChildrenOpt: Option[html.Element => Unit]
  ): Unit = {

    // - https://github.com/orbeon/orbeon-forms/issues/5595
    // - https://github.com/orbeon/orbeon-forms/issues/5427
    val isReadonly = Controls.isReadonly(documentElement)

    // Remember currently-checked values
    val checkedValues =
      into.children.flatMap { elem =>
        Option(elem.querySelector("input[type = checkbox], input[type = radio]").asInstanceOf[dom.html.Input])
          .filter(_.checked)
          .map(input => input.value)
      }.toSet

    // Clear existing items if needed
    clearChildrenOpt.foreach(_.apply(into))

    val domParser = new DOMParser

    val itemsToInsertIt: Iterator[html.Span] =
      itemsetTree.iterator.zipWithIndex.map { case (itemElement, itemIndex) =>

        val classOpt = itemElement.attributes.toOption.flatMap(_.get("class"))

        val itemLabelNodesOpt =
          itemElement.label.toOption.map { label =>
            domParser
              .parseFromString(label, SupportedType.`text/html`)
              .querySelector("html > body")
              .childNodes
          }

        val helpOpt: Option[JsDom.TypedTag[Span]] = itemElement.help.toOption.filter(_.nonAllBlank).map { helpString =>
          span(cls := "xforms-help")(helpString)
        }

        val hintOpt: Option[JsDom.TypedTag[Span]] = itemElement.hint.toOption.filter(_.nonAllBlank).map { hintString =>
          span(cls := "xforms-hint") {
            domParser
              .parseFromString(hintString, SupportedType.`text/html`)
              .querySelector("html > body")
              .childNodes
              .toList
          }
        }

        span(cls := ("xforms-deselected" :: classOpt.toList mkString " ")) {

          def createInput: JsDom.TypedTag[Input] =
            input(
              id       := XFormsId.appendToEffectiveId(controlId, Constants.LhhacSeparator + "e" + itemIndex),
              tpe      := (if (isSelect) "checkbox" else "radio"),
              name     := groupNameOpt.getOrElse(controlId),
              value    := itemElement.value
            )(
              checkedValues(itemElement.value).option(checked),
              isReadonly                      .option(disabled)
            )

          itemLabelNodesOpt match {
            case None =>
              createInput
            case Some(itemLabelNodes) =>
              label(cls := (if (isSelect) "checkbox" else "radio"))(
                createInput
              )(
                span(hintOpt.isDefined.option(cls := "xforms-hint-region"))(itemLabelNodes.toList)
              )(
                helpOpt
              )(
                hintOpt
              )
          }
        }.render
      }

    val itemsToInsertAsList = itemsToInsertIt.toList

    afterOpt match {
      case None if into.firstElementChild eq null =>
        into.asInstanceOf[ElementExt].append(itemsToInsertAsList: _*)
      case None =>
        into.firstElementChild.asInstanceOf[ElementExt].prepend(itemsToInsertAsList: _*)
      case Some(after) =>
        after.asInstanceOf[ElementExt].after(itemsToInsertAsList: _*)
    }
  }

  private object Private {

    // TODO: This is missing from the version of `scala-js-dom` we use, but present in newer versions. Once we upgrade
    //  we can remove this.
    trait ElementExt extends js.Object {
      def append(nodes: (raw.Node | String)*): Unit
      def prepend(nodes: (raw.Node | String)*): Unit
      def after(nodes: (raw.Node | String)*): Unit
      def before(nodes: (raw.Node | String)*): Unit
    }

    // Global information about the last checkbox to check
    // Q: Should this be by form?
    var lastCheckboxChecked: Option[(html.Element, html.Input)] = None

    def nestedInputElems(target: dom.Element): Seq[html.Input] =
      target.getElementsByTagName("input").map(_.asInstanceOf[html.Input])

    private def findLoaderElem: Option[raw.Element] =
      Option(dom.document.querySelector("body > .orbeon-loader"))

    private def createLoaderElem: raw.Element = {
      val newDiv = dom.document.createElement("div")
      newDiv.classList.add("orbeon-loader")
      dom.document.body.appendChild(newDiv)
      newDiv
    }

    val HandleValueIgnoredControls    = List("xforms-trigger", "xforms-submit", "xforms-upload")
    val HandleValueOutputOnlyControls = List("xforms-output", "xforms-static", "xforms-label", "xforms-hint", "xforms-help")

    def childrenWithLocalName(node: raw.Element, name: String): Iterator[raw.Element] =
      node.childNodes.iterator collect {
        case n: raw.Element if n.localName == name => n
      }

    def appendRepeatSuffix(id: String, suffix: String): String =
      if (suffix.isEmpty)
        id
      else
        id + Constants.RepeatSeparator + suffix

    def attValueOrThrow(elem: raw.Element, name: String): String =
      attValueOpt(elem, name).getOrElse(throw new IllegalArgumentException(name))

    // Just in case, normalize following:
    // https://developer.mozilla.org/en-US/docs/Web/API/Element/getAttribute#non-existing_attributes
    def attValueOpt(elem: raw.Element, name: String): Option[String] =
      if (elem.hasAttribute(name))
        Option(elem.getAttribute(name)) // `Some()` should be ok but just in case...
      else
        None

    def booleanAttValueOpt(elem: raw.Element, name: String): Option[Boolean] =
      attValueOpt(elem, name).map(_.toBoolean)

    def showModalProgressPanelRaw(): Unit = {
      val elem = findLoaderElem getOrElse createLoaderElem
      val cl = elem.classList
      cl.add("loader") // TODO: `add()` can take several arguments
      cl.add("loader-default")
      cl.add("is-active")
    }

    def hideModalProgressPanelRaw(): Unit =
      findLoaderElem foreach { elem =>
        val cl = elem.classList
        cl.remove("is-active")
      }

    def hideModalProgressPanel(
      timerIdOpt        : Option[SetTimeoutHandle],
      focusControlIdOpt : Option[String]
    ): Unit =
      if (modalProgressPanelShown) {

        modalProgressPanelShown = false

        // So that the modal progress panel doesn't show just after we try to hide it
        timerIdOpt foreach timers.clearTimeout

        hideModalProgressPanelRaw()

        // Restore focus
        // See https://github.com/orbeon/orbeon-forms/issues/4511
        focusControlIdOpt foreach Controls.setFocus
      }

    // Server telling us to show the dialog
    def showDialog(controlId: String, neighborIdOpt: Option[String], reason: String): Unit = {
      val dialogElem = dom.document.getElementById(controlId).asInstanceOf[HTMLDialogElement]
      dialogElem.showModal()
      dialogElem.addEventListener("cancel" , dialogCancelListener )
      dialogElem.addEventListener("keydown", dialogKeydownListener)
    }

    // Server telling us to hide the dialog
    def hideDialog(id: String, formID: String): Unit = {
      val dialogElem = dom.document.getElementById(id).asInstanceOf[HTMLDialogElement]
      dialogElem.removeEventListener("cancel" , dialogCancelListener )
      dialogElem.removeEventListener("keydown", dialogKeydownListener)
      dialogElem.close()
    }

    // Users closed the dialog (Esc): the browser dispatches the `cancel` event, we inform the server
    // Declared as `val` to ensure function identity, for the `removeEventListener` to work
    private val dialogCancelListener: js.Function1[dom.Event, Unit] = (event: dom.Event) => {
      val dialogElem    = event.target.asInstanceOf[html.Element]
      AjaxClient.fireEvent(
        new AjaxEvent(
          js.Dictionary[js.Any](
            "eventName" -> "xxforms-dialog-close",
            "targetId"  -> dialogElem.id
          )
        )
      )
    }

    // Prevent Esc from closing the dialog if the `xxf:dialog` has `close="false"`
    private val dialogKeydownListener: js.Function1[dom.KeyboardEvent, Unit] = (event: dom.KeyboardEvent) => {
      val targetElem    = event.target.asInstanceOf[html.Element]
      val dialogElem    = targetElem.closest("dialog").get
      val supportsClose = dialogElem.classList.contains("xforms-dialog-close-true")
      if (event.key == "Escape" && ! supportsClose) {
        event.preventDefault()
        event.stopPropagation()
      }
    }
  }
}
