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

import org.orbeon.oxf.util.MarkupUtils
import org.orbeon.xforms.facade.{Controls, Utils}
import org.scalajs.dom
import org.scalajs.dom.ext._
import org.scalajs.dom.html
import org.scalajs.dom.raw.{Element, Node}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.scalajs.js
import scala.scalajs.js.annotation.{JSExport, JSExportTopLevel}
import scala.scalajs.js.timers
import scala.scalajs.js.timers.SetTimeoutHandle


// Progressively migrate contents of xforms.js here
@JSExportTopLevel("OrbeonXFormsUi")
object XFormsUI {

  @JSExport // 2020-04-27: 6 JavaScript usages from xforms.js
  var modalProgressPanelShown: Boolean = false

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

  @JSExport
  def updateSelectItemset(documentElement: html.Element, itemsetTree: js.Array[ItemsetItem]): Unit =
    (documentElement.getElementsByTagName("select")(0): js.UndefOr[Element]) foreach {
      case select: html.Select =>

        val selectedValues =
          select.options.filter(_.selected).map(_.value)

        val sb = new java.lang.StringBuilder
        var inOptgroup = false

        // 2021-12-08: We need utilities to generate markup!
        def generateAtt(name: String, value: String) = {
          sb.append(" ")
          sb.append(name)
          sb.append(" =\"")
          sb.append(MarkupUtils.escapeXmlForAttribute(value))
          sb.append("\"")
        }

        def generateOption(label: String, value: String, classOpt: Option[String], selectedValues: js.Array[String]): Unit = {
          sb.append("<option")
          generateAtt("value", value)
          if (selectedValues.contains(value))
            generateAtt("selected", "selected")
          classOpt.foreach(generateAtt("class", _))
          sb.append(">")
          sb.append(label)
          sb.append("</option>")
        }

        def  generateItem(itemElement: ItemsetItem): Unit = {

          val classOpt = itemElement.attributes.toOption.flatMap(_.get("class"))

          itemElement.children.toOption match {
            case None =>
              generateOption(itemElement.label, itemElement.value, classOpt, selectedValues)
            case Some(children) =>

              if (inOptgroup)
                sb.append("</optgroup>") // nested `optgroup`s are not allowed

              sb.append("<optgroup")
              generateAtt("label", itemElement.label)
              classOpt.foreach(generateAtt("class", _))
              sb.append(">")

              inOptgroup = true

              children.foreach(generateItem)

              if (inOptgroup)
                  sb.append("</optgroup>")

              inOptgroup = false
          }
        }

        itemsetTree.foreach(generateItem)

        select.innerHTML = sb.toString

      case _ =>
    }

  private object Private {

    private def findLoaderElem: Option[Element] =
      Option(dom.document.querySelector("body > .orbeon-loader"))

    private def createLoaderElem: Element = {
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
