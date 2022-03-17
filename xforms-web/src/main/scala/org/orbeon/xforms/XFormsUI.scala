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

import org.orbeon.datatypes.BasicLocationData
import org.orbeon.oxf.util.CoreUtils.BooleanOps
import org.orbeon.oxf.util.MarkupUtils.MarkupStringOps
import org.orbeon.xforms.facade.{Controls, Utils, XBL}
import org.scalajs.dom
import org.scalajs.dom.ext._
import org.scalajs.dom.{html, raw}
import org.scalajs.jquery.JQueryPromise
import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits._
import scalatags.JsDom
import scalatags.JsDom.all._

import scala.concurrent.duration._
import scala.scalajs.js
import scala.scalajs.js.JSConverters._
import scala.scalajs.js.annotation.{JSExport, JSExportTopLevel}
import scala.scalajs.js.timers
import scala.scalajs.js.timers.SetTimeoutHandle


// Progressively migrate contents of xforms.js/AjaxServer.js here
@JSExportTopLevel("OrbeonXFormsUi")
object XFormsUI {

  import Private._

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
  def findDialogsToShow(controlValuesElems: js.Array[raw.Element]): Iterator[String] =
    for {
      controlValuesElem <- controlValuesElems.iterator
      dialogElem        <- childrenWithLocalName(controlValuesElem, "dialog")
      visibleValue      <- attValueOpt(dialogElem, "visibility")
      if visibleValue == "visible"
      idValue           <- attValueOpt(dialogElem, "id")
    } yield
      idValue

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

    val instance = XBL.instanceForControl(documentElement)

    if (instance.isInstanceOf[js.Object]) {

      val becomesRelevant    = relevant.contains("true")
      val becomesNonRelevant = relevant.contains("false")

      def callXFormsUpdateReadonlyIfNeeded(): Unit =
        if (readonly.isDefined && instance.asInstanceOf[js.Dynamic].xformsUpdateReadonly.isInstanceOf[js.Function])
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

        if (instance.asInstanceOf[js.Dynamic].destroy.isInstanceOf[js.Function])
          instance.destroy()

        // The class's `destroy()` should do that anyway as we inject our own `destroy()`, but ideally
        // `destroy()` should only be called from there, and so the `null`ing of `xforms-xbl-object` should
        // take place here as well.
        $(documentElement).data("xforms-xbl-object", null)
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
  def handleValues(controlElem: raw.Element, controlId: String, recreatedInput: Boolean, controlsWithUpdatedItemsets: js.Dictionary[Boolean]): Unit =
    childrenWithLocalName(controlElem, "value") foreach
      (handleValue(_, controlId, recreatedInput, controlsWithUpdatedItemsets))

  def handleValue(elem: raw.Element, controlId: String, recreatedInput: Boolean, controlsWithUpdatedItemsets: js.Dictionary[Boolean]): Unit = {

    val newControlValue  = elem.textContent
    val documentElement  = dom.document.getElementById(controlId).asInstanceOf[html.Element]

    def containsAnyOf(e: raw.Element, tokens: List[String]) = {
      val classList = e.classList
      tokens.exists(classList.contains)
    }

    if (containsAnyOf(documentElement, HandleValueIgnoredControls)) {
      scribe.error(s"Got value from server for element with class: ${documentElement.getAttribute("class")}")
    } else {

      ServerValueStore.set(controlId, newControlValue)

      val normalizedNewControlValue = newControlValue.normalizeSerializedHtml

      if (containsAnyOf(documentElement, HandleValueOutputOnlyControls))
        Controls.setCurrentValue(documentElement, normalizedNewControlValue, force = false)
      else
        Controls.getCurrentValue(documentElement) foreach { currentValue =>

          val normalizedPreviousServerValueOpt = Option(ServerValueStore.get(controlId)).map(_.normalizeSerializedHtml)
          val normalizedCurrentValue           = currentValue.normalizeSerializedHtml

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

            def setServerValue(): Unit =
              ServerValueStore.set(
                controlId,
                Controls.getCurrentValue(documentElement)
              )

            val promiseOrUndefDyn = promiseOrUndef.asInstanceOf[js.Dynamic]

            if (promiseOrUndefDyn.isInstanceOf[js.Object] && promiseOrUndefDyn.done.isInstanceOf[js.Function])
              promiseOrUndef.asInstanceOf[JQueryPromise].done(setServerValue _: js.Function)
            else if (promiseOrUndefDyn.isInstanceOf[js.Object] && promiseOrUndefDyn.then.isInstanceOf[js.Function])
              promiseOrUndef.asInstanceOf[js.Promise[Unit]].toFuture.foreach(_ => setServerValue())
            else
              setServerValue()
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
        val mustHideProgressDialog =
          ! (
            // `exists((//xxf:submission, //xxf:load)[empty(@target) and empty(@show-progress)])`
            details.responseXML.getElementsByTagNameNS(Namespaces.XXF, "submission").iterator ++
              details.responseXML.getElementsByTagNameNS(Namespaces.XXF, "load").iterator exists
              (e => ! e.hasAttribute("target") && e.getAttribute("show-progress") != "false")
          )

        if (mustHideProgressDialog)
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

          def generateItem(itemElement: ItemsetItem): JsDom.TypedTag[raw.HTMLElement] = {

            val classOpt = itemElement.attributes.toOption.flatMap(_.get("class"))

            itemElement.children.toOption match {
              case None =>
                option(
                  (value := itemElement.value)                              ::
                  selectedValues.contains(itemElement.value).list(selected) :::
                  classOpt.toList.map(`class` := _)
                )(
                  itemElement.label
                )
              case Some(children) =>
                optgroup(
                  (attr("label") := itemElement.label) :: classOpt.toList.map(`class` := _)
                ) {
                  children.map(generateItem): _*
                }
            }
          }

          // IE 11 doesn't support `replaceChildren()`
          select.innerHTML = ""
          itemsetTree.toList.map(generateItem).map(_.render).foreach(select.appendChild)

        case _ =>
          // This should not happen but if it does we'd like to know about it without entirely stopping the
          // update process so we give the user a chance to recover the form. This should be generalized
          // once we have migrated `AjaxServer.js` entirely to Scala.
          scribe.error(s"`<select>` element not found when attempting to update itemset")
      }

  private object Private {

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
  }
}
