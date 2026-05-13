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

import org.orbeon.web.DomSupport.*
import org.orbeon.xforms.{AjaxClient, AjaxEvent, CallbackList, Support}
import org.scalajs.dom
import org.scalajs.dom.html

import scala.annotation.unused
import scala.scalajs.js


object FormBuilderPrivateAPI extends js.Object {

  def updateLocationDocumentId(documentId: String): Unit = {

    val location = dom.window.location

    replaceStateLogError(
      statedata = dom.window.history.state,
      title     = "",
      url       = s"$documentId${location.search}${location.hash}"
    )
  }

  def controlAdded: CallbackList[String] =
    ControlLabelHintTextEditor.controlAdded

  def sectionAdded: CallbackList[String] =
    SectionLabelEditor.sectionAdded

  def updateTestIframeAndDispatch(eventName: String): Unit = {

    // Reset the displayed page as the iframe might show the result from a previous test
    val oldIframe =
      dom.document
        .documentElement
        .queryNestedElems[html.IFrame](".fb-test-iframe")
        .head

    // Clone the iframe node so that nested event handlers like `onbeforeunload` are removed
    oldIframe.parentNode.replaceChild(oldIframe.cloneNodeT(false), oldIframe)

    // Dispatch the event requested
    AjaxClient.fireEvent(
      AjaxEvent(
        eventName = eventName,
        targetId = "fr-form-model",
        form     = Support.allFormElems.headOption // 2023-09-01: only used by Form Builder, so presumably only one
      )
    )
  }

  @unused("Used by Form Builder's publish dialog")
  def focusPublishOpenNewButton(): Unit =
    dom.document.querySelectorOpt(".fb-publish-open-new").foreach(_.focus())

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
