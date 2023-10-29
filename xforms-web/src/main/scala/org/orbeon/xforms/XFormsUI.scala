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
import org.orbeon.oxf.util.CoreUtils.PipeOps
import org.orbeon.oxf.util.LoggerFactory
import org.orbeon.oxf.util.MarkupUtils.MarkupStringOps
import org.orbeon.oxf.util.StringUtils.OrbeonStringOps
import org.orbeon.web.DomSupport
import org.orbeon.xforms.facade.{Controls, Init, Utils, XBL}
import org.scalajs.dom
import org.scalajs.dom.experimental.URL
import org.scalajs.dom.ext._
import org.scalajs.dom.{MouseEvent, html, raw, window}
import org.scalajs.jquery.JQueryPromise
import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits._
import shapeless.syntax.typeable._

import scala.collection.immutable
import scala.concurrent.duration._
import scala.concurrent.{Future, Promise}
import scala.scalajs.js
import scala.scalajs.js.JSConverters._
import scala.scalajs.js.annotation.{JSExport, JSExportTopLevel}
import scala.scalajs.js.timers.SetTimeoutHandle
import scala.scalajs.js.{timers, |}
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
    val relevant        = attValueOpt(elem, "relevant")
    val readonly        = attValueOpt(elem, "readonly")

    Option(XBL.instanceForControl(documentElement)) foreach { instance =>

      val becomesRelevant    = relevant.contains("true")
      val becomesNonRelevant = relevant.contains("false")

      def callXFormsUpdateReadonlyIfNeeded(): Unit =
        if (readonly.isDefined && XFormsXbl.isObjectWithMethod(instance, "xformsUpdateReadonly"))
          instance.xformsUpdateReadonly(readonly.contains("true"))

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

    val repeatId  = controlElem.getAttribute("id")
    val iteration = controlElem.getAttribute("iteration")
    val relevant  = controlElem.getAttribute("relevant")

    // Remove or add `xforms-disabled` on elements after this delimiter
    if (relevant != null)
      Controls.setRepeatIterationRelevance(formID, repeatId, iteration, relevant == "true")
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
      Future(())
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

    val showProgressOpt = attValueOpt(submissionElement, "show-progress")
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
    if (! showProgressOpt.contains("false"))
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

    // TODO: This is missing from the version of `scala-js-dom` we use, but present in newer versions. Once we upgrade
    //  we can remove this.
    trait ElementExt extends js.Object {
      def prepend(nodes: (raw.Node | String)*): Unit
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

  @js.native trait ItemsetItem extends js.Object {
    def attributes: js.UndefOr[js.Dictionary[String]] = js.native
    def children: js.UndefOr[js.Array[ItemsetItem]] = js.native
    def label: String = js.native
    def value: String = js.native
  }

  // TODO:
  //  - Resolve nested `optgroup`s.
  //  - use direct serialization/deserialization instead of custom JSON.
  //    See `XFormsSelect1Control.outputAjaxDiffUseClientValue()`.
  @JSExport
  def updateSelectItemset(documentElement: html.Element, itemsetTree: js.Array[ItemsetItem]): Unit =
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
                  option.innerHTML = itemElement.label
                  classOpt.foreach(option.className = _)
                }
              case Some(children) =>
                dom.document.createElement("optgroup").asInstanceOf[html.OptGroup].kestrel { optgroup =>
                  optgroup.label = itemElement.label
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

  private object Private {

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

    def showDialog(controlId: String, neighborIdOpt: Option[String], reason: String): Unit = {

      def showDialogImpl(): Unit = {

        val dialogUiElem = dom.document.getElementById(controlId).asInstanceOf[html.Element]

        // Initialize dialog now, if it hasn't been done already
        val yuiDialog = Globals.dialogs.getOrElseUpdate(controlId, {
          Init._dialog(dialogUiElem)
        })

        // Take out the focus from the current control. This is particularly important with non-modal dialogs
        // opened with a minimal trigger, otherwise we have a dotted line around the link after it opens.
        Option(Globals.currentFocusControlId) foreach { currentFocusControlId =>
          Option(dom.document.getElementById(currentFocusControlId).asInstanceOf[html.Element]) foreach
            (_.blur())
        }

        // Adjust classes on dialog
        dialogUiElem.classList.remove("xforms-dialog-visible-false")
        dialogUiElem.classList.add("xforms-dialog-visible-true")

        // Render the dialog if needed
        if (dialogUiElem.classList.contains("xforms-initially-hidden")) {
          dialogUiElem.classList.remove("xforms-initially-hidden")
          yuiDialog.render()
        }

        // Reapply those classes. Those are classes added by YUI when creating the dialog, but they are then removed
        // by YUI if you close the dialog using the "X". So when opening the dialog, we add those again, just to make sure.
        // A better way to handle this would be to create the YUI dialog every time when we open it, instead of doing this
        // during initialization.
        val innerElem = yuiDialog.innerElement.asInstanceOf[html.Element]
        innerElem.classList.add("yui-module")
        innerElem.classList.add("yui-overlay")
        innerElem.classList.add("yui-panel")

        // Fixes cursor Firefox issue; more on this in dialog init code
        // 2022-12-12: This seems to be still needed
        yuiDialog.element.style.display = "block"

        // 2022-12-12: Not sure who sets those to other values, but we need to control that now
        yuiDialog.element.style.top = 0;
        yuiDialog.element.style.left = 0;

        // Make sure that this dialog is on top of everything else
        yuiDialog.cfg.setProperty("zIndex", {
          Globals.lastDialogZIndex += 1
          Globals.lastDialogZIndex
        })

        // Show the dialog
        yuiDialog.show()

        // See for old centering logic: https://github.com/orbeon/orbeon-forms/issues/4475
        neighborIdOpt foreach { neighborId =>
          yuiDialog.cfg.setProperty("context", js.Array(neighborId, "tl", "bl"))
          yuiDialog.align()
        }
      }

      // Check if we need to wait until all the CSS and images are loaded, as they can influence positioning
      // NOTE: We want dialogs to be shown synchronously if possible so we don't use a future here
      if (DomSupport.interactiveReadyState(dom.document, DomSupport.DomReadyState.Complete))
        showDialogImpl()
      else
        DomSupport.atLeastDomReadyStateF(dom.document, DomSupport.DomReadyState.Complete) foreach (_ => showDialogImpl())
    }

    def hideDialog(id: String, formID: String): Unit =
      Globals.dialogs.get(id) foreach { yuiDialog =>
        // Remove timer to show the dialog asynchronously so it doesn't show later!
        Page.getXFormsFormFromNamespacedIdOrThrow(formID).removeDialogTimerId(id)

        Globals.maskDialogCloseEvents = true
        yuiDialog.hide()
        Globals.maskDialogCloseEvents = false
        // Fixes cursor Firefox issue; more on this in dialog init code
        yuiDialog.element.style.display = "none"

        // Adjust classes on dialog
        val dialogUiElem = dom.document.getElementById(id)
        dialogUiElem.classList.remove("xforms-dialog-visible-true")
        dialogUiElem.classList.add("xforms-dialog-visible-false")
      }
  }
}
