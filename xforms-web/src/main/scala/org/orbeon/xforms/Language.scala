/**
  * Copyright (C) 20017 Orbeon, Inc.
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

import org.orbeon.web.DomSupport
import org.orbeon.xforms.Constants.HtmlLangAttr
import org.scalajs.dom
import org.scalajs.dom.{Element, MutationObserver}

import scala.collection.mutable as m


object Language {

  import Private.*

  val DefaultLang = "en"

  // Return the untruncated language for the page (e.g. `pt-PT`), or empty when no lang attribute is found
  def findFullLang: Option[String] =
    langElement
      .flatMap(el => Option(el.getAttribute(HtmlLangAttr)))
      .map(_.trim)
      .filter(_.nonEmpty)

  // See also https://github.com/orbeon/orbeon-forms/issues/3787
  def fullLangOrDefault: String =
    findFullLang.getOrElse(DefaultLang)

  def onLangChange(listenerId: String, listener: Option[String] => Unit): Unit =
    langElement.foreach { elem =>
      val mutationObserver = DomSupport.onAttributeChange(elem, HtmlLangAttr, () => listener(findFullLang))
      langListeners.put(listenerId, mutationObserver)
    }

  def offLangChange(listenerId: String): Unit = {
    langListeners.get(listenerId).foreach { mutationObserver =>
      mutationObserver.disconnect()
      langListeners.remove(listenerId)
    }
  }

  private object Private {

    val langListeners = m.Map.empty[String, MutationObserver]

    def langElement: Option[dom.Element] = {

      // Prefer the wrapper: when embedded, it carries the form language, while the root element belongs to the host
      // page, which can have a lang of its own, as with Liferay
      val langElements: Iterator[() => Option[Element]] = Iterator(
        () => Option(dom.document.querySelector(s".orbeon-portlet-div[$HtmlLangAttr]")),
        () => Option(dom.document.documentElement)
      )
      langElements
        .map(_.apply())
        .collectFirst { case Some(element) if element.hasAttribute(HtmlLangAttr) => element }
    }
  }
}
