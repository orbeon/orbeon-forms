/**
  * Copyright (C) 2019 Orbeon, Inc.
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
package org.orbeon.builder

import io.udash.wrappers.jquery.JQueryCallbacks
import org.orbeon.web.DomSupport.*
import org.orbeon.xforms.{$, AjaxClient, AjaxEvent, Support}
import org.scalajs.dom
import org.scalajs.dom.html

import scala.scalajs.js


object FormBuilderPrivateAPI extends js.Object {

  def updateLocationDocumentId(documentId: String): Unit = {

    val location = dom.window.location

    dom.window.history.replaceState(
      statedata = dom.window.history.state,
      title     = "",
      url       = s"$documentId${location.search}${location.hash}"
    )
  }

  def controlAdded: JQueryCallbacks[js.Function1[String, js.Any], String] =
    ControlLabelHintTextEditor.controlAdded

  def sectionAdded: JQueryCallbacks[js.Function1[String, js.Any], String] =
    SectionLabelEditor.sectionAdded

  def updateTestIframeAndDispatch(eventName: String): Unit = {

    // Reset the displayed page as the iframe might show the result from a previous test
    val oldIFrame = $(".fb-test-iframe")
    val newIFrame = $(oldIFrame.get(0).get.outerHTML)
    val iframeContainer = oldIFrame.parent()
    oldIFrame.remove()
    iframeContainer.append(newIFrame)

    // Dispatch the event requested
    AjaxClient.fireEvent(
      AjaxEvent(
        eventName = eventName,
        targetId = "fr-form-model",
        form     = Support.allFormElems.headOption // 2023-09-01: only used by Form Builder, so presumably only one
      )
    )
  }

  // Here we implement our own `scrollIntoView()` in order to handle positioning better. For example if a control is
  // below the Form Builder main area, we scroll enough to make it visible at the bottom, and vice-versa if the
  // control is above the main area.
  // Right now this doesn't handle scrolling horizontally.
  def moveFocusedCellIntoView(): Unit =
    for {
      selectedElem  <- dom.document.querySelectorOpt(".fb-main .fb-selected")
      mainInnerElem <- dom.document.querySelectorOpt(".fb-main-inner")
      mainElem      <- dom.document.querySelectorOpt(".fb-main")
    } locally {
      moveIntoViewIfNeeded(mainElem, mainInnerElem, selectedElem, margin = 50)
    }

}
