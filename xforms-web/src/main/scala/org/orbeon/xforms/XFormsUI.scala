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
import org.orbeon.xforms.facade.{Controls, Utils}
import org.scalajs.dom
import org.scalajs.dom.ext._
import org.scalajs.dom.{html, raw}
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

  @JSExport // 2020-04-27: 6 JavaScript usages from xforms.js
  var modalProgressPanelShown: Boolean = false

  // 2022-03-16: AjaxServer.js
  @JSExport
  def firstChildWithLocalName(node: raw.Element, name: String): js.UndefOr[raw.Element] =
    node.childNodes.collectFirst{
      case n: raw.Element if n.localName == name => n
    }.orUndefined

  private def childrenWithLocalName(node: raw.Element, name: String): Iterator[raw.Element] =
    node.childNodes.iterator collect {
      case n: raw.Element if n.localName == name => n
    }

  private def appendRepeatSuffix(id: String, suffix: String): String =
    if (suffix.isEmpty)
      id
    else
      id + Constants.RepeatSeparator + suffix

  private def attValueOrThrow(elem: raw.Element, name: String): String =
    attValueOpt(elem, name).getOrElse(throw new IllegalArgumentException(name))

  // Just in case, normalize following:
  // https://developer.mozilla.org/en-US/docs/Web/API/Element/getAttribute#non-existing_attributes
  private def attValueOpt(elem: raw.Element, name: String): Option[String] =
    if (elem.hasAttribute(name))
      Option(elem.getAttribute(name)) // `Some()` should be ok but just in case...
    else
      None

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
                Private.showModalProgressPanelRaw()
              }
            )
        } else {
          Private.showModalProgressPanelRaw()
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
          Private.hideModalProgressPanel(timerIdOpt, focusControlIdOpt)
      }
    }

  def showModalProgressPanelImmediate(): Unit =
    Private.showModalProgressPanelRaw()

  def hideModalProgressPanelImmediate(): Unit =
    Private.hideModalProgressPanelRaw()

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
