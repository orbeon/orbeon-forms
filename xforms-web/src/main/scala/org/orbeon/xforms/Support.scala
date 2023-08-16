/**
  * Copyright (C) 2017 Orbeon, Inc.
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

import org.orbeon.oxf.http.Headers
import org.orbeon.oxf.util.ContentTypes
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.web.DomEventNames
import org.scalajs.dom
import org.scalajs.dom.experimental._
import org.scalajs.dom.experimental.domparser.{DOMParser, SupportedType}
import org.scalajs.dom.{Element, EventTarget, FocusEvent, FormData, html}

import scala.concurrent.Future
import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits._

import scala.scalajs.js
import scala.scalajs.js.|
import scala.util.control.NonFatal


object Support {

  def formElemOrDefaultForm(formElem: js.UndefOr[html.Form]): html.Form =
    formElem getOrElse getFirstForm

  def getFirstForm: html.Form =
    $(dom.document.forms).filter(".xforms-form")(0).asInstanceOf[html.Form]

  def adjustIdNamespace(
    formElem : js.UndefOr[html.Form],
    targetId : String
  ): (html.Element, String) = {

    val form   = Support.formElemOrDefaultForm(formElem)
    val formId = form.id

    // See comment on `namespaceIdIfNeeded`
    form -> Page.namespaceIdIfNeeded(formId, targetId)
  }

  private def parseStringAsXml(xmlString: String): Option[dom.Document] =
    try {
      Option((new DOMParser).parseFromString(xmlString, SupportedType.`application/xml`)) filter
        (_.documentElement.getElementsByTagName("parsererror").length == 0)
    } catch {
      case NonFatal(_) =>
        // If `xmlString` can't be parsed, `parseFromString()` is expected to return a document with `<parsererror/>`
        // (which is crazy), but IE11 throws an exception instead.
        None
    }

  def getLocalName(n: dom.Node): Option[String] =
    Option(n) collect { case e: dom.Element => getLocalName(e)}

  def getLocalName(e: dom.Element): String =
    if (e.tagName.contains(":")) e.tagName.substringAfter(":") else e.tagName

  // TODO: Rename as we are returning a `dom.Document`?
  def fetchText(
    url         : String,
    requestBody : String | FormData,
    contentType : Option[String],
    acceptLang  : Option[String],
    transform   : (String, String) => String,
    abortSignal : Option[AbortSignal]
  ): Future[(Int, String, Option[dom.Document])] = {

    val customHeaders = js.Dictionary[String]()
    contentType.foreach(customHeaders(Headers.ContentType)    = _)
    acceptLang .foreach(customHeaders(Headers.AcceptLanguage) = _)

    val fetchPromise =
      Fetch.fetch(
        url,
        new RequestInit {
          method         = HttpMethod.POST
          body           = requestBody
          headers        = if (customHeaders.nonEmpty) customHeaders else js.undefined
          referrer       = js.undefined
          referrerPolicy = js.undefined
          mode           = js.undefined
          credentials    = js.undefined
          cache          = js.undefined
          redirect       = RequestRedirect.follow // only one supported with the polyfill
          integrity      = js.undefined
          keepalive      = js.undefined
          signal         = abortSignal map js.defined.apply getOrElse js.undefined
          window         = null
        }
      )

    for {
      response <- fetchPromise.toFuture
      text     <- response.text().toFuture
      newText = transform(text, ContentTypes.XmlContentType)
    } yield
      (
        response.status,
        newText,
        Support.parseStringAsXml(newText)
      )
  }

  def stopFocusOutPropagation(
    element     : Element,
    eventTarget : FocusEvent => EventTarget,
    targetClass : String
  ): Unit =
    element.addEventListener(
      DomEventNames.FocusOut,
      focusFunction(eventTarget, targetClass),
      useCapture = true
    )

  def stopFocusOutPropagationUseEventListenerSupport(
    element     : Element,
    eventTarget : FocusEvent => EventTarget,
    targetClass : String,
    support     : EventListenerSupport
  ): Unit =
    support.addListener(
      element,
      DomEventNames.FocusOut,
      focusFunction(eventTarget, targetClass),
      useCapture = true
    )

  private def focusFunction(
    eventTarget : FocusEvent => EventTarget,
    targetClass : String
  ): FocusEvent => Unit =
    (event: FocusEvent) => {
      // 2020-12-22: Noted that `relatedTarget` can be `null` in plain XForms.
      // Not sure why, but protecting against crash here with pattern match.
      eventTarget(event) match {
        case relatedTarget: dom.html.Element =>
          if (relatedTarget.classList.contains(targetClass))
            event.stopPropagation()
        case _ =>
      }
    }
}
