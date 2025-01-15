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
import org.orbeon.oxf.util.StringUtils.*
import org.orbeon.web.DomEventNames
import org.orbeon.xforms.Constants.FormClass
import org.scalajs.dom
import org.scalajs.dom.html

import scala.concurrent.{ExecutionContext, Future}
import scala.scalajs.js
import scala.scalajs.js.|
import scala.util.control.NonFatal


object Support {

  def allFormElems: Iterable[html.Form] =
    dom.document.forms collect { case f: html.Form if isXFormsFormElem(f) => f }

  def isXFormsFormElem(formElem: html.Form): Boolean =
    formElem.classList.contains(FormClass) && formElem.id.nonAllBlank

  def adjustIdNamespace(
    elem    : js.UndefOr[html.Element],
    targetId: String
  ): (html.Element, String) = {

    val form   = Page.findAncestorOrSelfHtmlFormFromHtmlElemOrDefault(elem).getOrElse(throw new IllegalArgumentException("form not found"))
    val formId = form.id

    // See comment on `namespaceIdIfNeeded`
    form -> Page.namespaceIdIfNeeded(formId, targetId)
  }

  private def parseStringAsXml(xmlString: String): Option[dom.Document] =
    try {
      Option((new dom.DOMParser).parseFromString(xmlString, dom.MIMEType.`application/xml`)) filter
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
    requestBody : String | dom.FormData,
    contentType : Option[String],
    acceptLang  : Option[String],
    transform   : (String, String) => String,
    abortSignal : Option[dom.AbortSignal])(implicit
    executor    : ExecutionContext
  ): Future[(Int, String, Option[dom.Document])] = {

    val customHeaders = js.Dictionary[String]()
    contentType.foreach(customHeaders(Headers.ContentType)    = _)
    acceptLang .foreach(customHeaders(Headers.AcceptLanguage) = _)

    val fetchPromise =
      dom.Fetch.fetch(
        url,
        new dom.RequestInit {
          method         = dom.HttpMethod.POST
          body           = requestBody
          headers        = if (customHeaders.nonEmpty) customHeaders else js.undefined
          referrer       = js.undefined
          referrerPolicy = js.undefined
          mode           = js.undefined
          credentials    = dom.RequestCredentials.include
          cache          = js.undefined
          redirect       = dom.RequestRedirect.follow // only one supported with the polyfill
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
    element       : dom.Element,
    eventToTarget : dom.FocusEvent => dom.EventTarget,
    targetClass   : String
  ): Unit =
    element.addEventListener(
      DomEventNames.FocusOut,
      focusFunction(eventToTarget, targetClass),
      useCapture = true
    )

  def stopFocusOutPropagationUseEventListenerSupport(
    element     : dom.Element,
    eventTarget : dom.FocusEvent => dom.EventTarget,
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
    eventToTarget : dom.FocusEvent => dom.EventTarget,
    targetClass   : String
  ): dom.FocusEvent => Unit =
    (event: dom.FocusEvent) => {
      // 2020-12-22: Noted that `relatedTarget` can be `null` in plain XForms.
      // Not sure why, but protecting against crash here with pattern match.
      eventToTarget(event) match {
        case relatedTarget: dom.html.Element =>
          if (relatedTarget.classList.contains(targetClass))
            event.stopPropagation()
        case _ =>
      }
    }

  // Handle progress as ‰ (per mille) but represent it as a percent with one decimal
  // https://github.com/orbeon/orbeon-forms/issues/6666
  // Use `Long`s as file sizes can go over 2^31 - 1!
  def computePercentStringToOneDecimal(received: Long, expected: Long): String = {
    require(received >= 0 && expected >= 0)
    require(received <= expected)

    val perMille =
      if (expected == 0)
        1000L
      else
        1000L * received / expected

    val perMilleString = perMille.toString
    s"${perMilleString.init}.${perMilleString.last}"
  }
}
