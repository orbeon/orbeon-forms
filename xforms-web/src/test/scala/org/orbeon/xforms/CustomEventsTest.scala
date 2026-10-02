/**
 * Copyright (C) 2026 Orbeon, Inc.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version
 * 2.1 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.xforms

import org.orbeon.web.DomSupport
import org.orbeon.web.DomSupport.*
import org.scalajs.dom
import org.scalajs.dom.html
import org.scalatest.funspec.AnyFunSpecLike

import scala.scalajs.js


class CustomEventsTest extends AnyFunSpecLike {

  describe("DomSupport.dispatchCustomEvent") {

    it("must dispatch event with bubbles = true") {
      val parent = dom.document.createElement("div").asInstanceOf[html.Div]
      val child  = dom.document.createElement("span").asInstanceOf[html.Span]
      parent.appendChild(child)
      dom.document.body.appendChild(parent)

      var parentReceived = false
      var docReceived    = false

      val parentListener: js.Function1[dom.Event, Unit] = (e: dom.Event) => {
        parentReceived = true
        assert(e.bubbles === true)
        assert(e.target === child)
      }
      val docListener: js.Function1[dom.Event, Unit] = (e: dom.Event) => {
        docReceived = true
        assert(e.bubbles === true)
        assert(e.target === child)
      }

      parent.addEventListener("test:bubble", parentListener)
      dom.document.addEventListener("test:bubble", docListener)

      try {
        DomSupport.dispatchCustomEvent(child, "test:bubble")
        assert(parentReceived === true)
        assert(docReceived === true)
      } finally {
        parent.removeEventListener("test:bubble", parentListener)
        dom.document.removeEventListener("test:bubble", docListener)
        dom.document.body.removeChild(parent)
      }
    }

    it("must carry detail payload when provided") {
      val elem = dom.document.createElement("div").asInstanceOf[html.Div]
      dom.document.body.appendChild(elem)

      var receivedDetail: js.Dynamic = null

      val listener: js.Function1[dom.CustomEvent, Unit] = (e: dom.CustomEvent) => {
        receivedDetail = e.detail.asInstanceOf[js.Dynamic]
      }

      elem.addEventListener("test:detail", listener)

      try {
        DomSupport.dispatchCustomEvent(
          target    = elem,
          name      = "test:detail",
          detailOpt = js.Dynamic.literal(foo = "bar", count = 42)
        )
        assert(receivedDetail != null)
        assert(receivedDetail.foo.toString === "bar")
        assert(receivedDetail.count.asInstanceOf[Int] === 42)
      } finally {
        elem.removeEventListener("test:detail", listener)
        dom.document.body.removeChild(elem)
      }
    }

    it("must have undefined or null detail when detailOpt is omitted") {
      val elem = dom.document.createElement("div").asInstanceOf[html.Div]
      dom.document.body.appendChild(elem)

      var receivedEvent: dom.CustomEvent = null

      val listener: js.Function1[dom.CustomEvent, Unit] = (e: dom.CustomEvent) => {
        receivedEvent = e
      }

      elem.addEventListener("test:nodetail", listener)

      try {
        DomSupport.dispatchCustomEvent(elem, "test:nodetail")
        assert(receivedEvent != null)
        assert(receivedEvent.detail == null || js.isUndefined(receivedEvent.detail))
      } finally {
        elem.removeEventListener("test:nodetail", listener)
        dom.document.body.removeChild(elem)
      }
    }
  }

  describe("orbeon:error event") {

    it("must dispatch on form element and bubble to document with title and details") {
      val form = dom.document.createElement("form").asInstanceOf[html.Form]
      val formId = "xf-test-error-form"
      form.id = formId
      dom.document.body.appendChild(form)

      var formReceivedEvent: dom.CustomEvent = null
      var docReceivedEvent : dom.CustomEvent = null
      var legacyFired                        = false
      var legacyTitle                        = ""
      var legacyDetails                      = ""

      val formListener: js.Function1[dom.CustomEvent, Unit] = (e: dom.CustomEvent) => {
        formReceivedEvent = e
      }
      val docListener: js.Function1[dom.CustomEvent, Unit] = (e: dom.CustomEvent) => {
        docReceivedEvent = e
      }
      val legacySub: js.Function = (_: String, args: js.Array[js.Any], _: js.Any) => {
        legacyFired = true
        val payload = args(0).asInstanceOf[js.Dynamic]
        legacyTitle   = payload.title.toString
        legacyDetails = payload.details.toString
      }

      form.addEventListener("orbeon:error", formListener)
      dom.document.addEventListener("orbeon:error", docListener)
      XFormsUiEvents.errorEvent.subscribe(legacySub)

      try {
        AjaxClient.showError(
          titleString   = "Error Title 1",
          detailsString = "Detailed error message 1",
          formId        = formId,
          ignoreErrors  = true
        )

        assert(formReceivedEvent != null)
        assert(formReceivedEvent.`type` === "orbeon:error")
        assert(formReceivedEvent.bubbles === true)
        assert(formReceivedEvent.target === form)
        val formDetail = formReceivedEvent.detail.asInstanceOf[js.Dynamic]
        assert(formDetail.title.toString === "Error Title 1")
        assert(formDetail.details.toString === "Detailed error message 1")

        assert(docReceivedEvent != null)
        assert(docReceivedEvent.target === form)

        // Legacy event must still fire
        assert(legacyFired === true)
        assert(legacyTitle === "Error Title 1")
        assert(legacyDetails === "Detailed error message 1")
      } finally {
        form.removeEventListener("orbeon:error", formListener)
        dom.document.removeEventListener("orbeon:error", docListener)
        XFormsUiEvents.errorEvent.unsubscribe(legacySub)
        dom.document.body.removeChild(form)
      }
    }

    it("must fallback to document as target when form is not found") {
      var docReceivedEvent: dom.CustomEvent = null

      val docListener: js.Function1[dom.CustomEvent, Unit] = (e: dom.CustomEvent) => {
        docReceivedEvent = e
      }

      dom.document.addEventListener("orbeon:error", docListener)

      try {
        AjaxClient.showError(
          titleString   = "Global Error",
          detailsString = "Something failed globally",
          formId        = "non-existent-form",
          ignoreErrors  = true
        )

        assert(docReceivedEvent != null)
        assert(docReceivedEvent.`type` === "orbeon:error")
        assert(docReceivedEvent.target === dom.document)
        val detail = docReceivedEvent.detail.asInstanceOf[js.Dynamic]
        assert(detail.title.toString === "Global Error")
        assert(detail.details.toString === "Something failed globally")
      } finally {
        dom.document.removeEventListener("orbeon:error", docListener)
      }
    }
  }

  describe("orbeon:form-loaded event") {

    it("must dispatch on form element and bubble to document and window") {
      val form = dom.document.createElement("form").asInstanceOf[html.Form]
      form.id = "xf-test-loaded-form"
      dom.document.body.appendChild(form)

      var formReceived   = false
      var docReceived    = false
      var windowReceived = false
      var receivedTarget: dom.EventTarget = null

      val formListener: js.Function1[dom.Event, Unit] = (e: dom.Event) => {
        formReceived   = true
        receivedTarget = e.target
      }
      val docListener: js.Function1[dom.Event, Unit] = (e: dom.Event) => {
        docReceived = true
      }
      val windowListener: js.Function1[dom.Event, Unit] = (e: dom.Event) => {
        windowReceived = true
      }

      form.addEventListener("orbeon:form-loaded", formListener)
      dom.document.addEventListener("orbeon:form-loaded", docListener)
      dom.window.addEventListener("orbeon:form-loaded", windowListener)

      try {
        DomSupport.dispatchCustomEvent(form, "orbeon:form-loaded")

        assert(formReceived === true)
        assert(docReceived === true)
        assert(windowReceived === true)
        assert(receivedTarget === form)
      } finally {
        form.removeEventListener("orbeon:form-loaded", formListener)
        dom.document.removeEventListener("orbeon:form-loaded", docListener)
        dom.window.removeEventListener("orbeon:form-loaded", windowListener)
        dom.document.body.removeChild(form)
      }
    }
  }
}
