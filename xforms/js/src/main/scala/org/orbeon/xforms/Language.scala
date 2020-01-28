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

import org.orbeon.xforms.Constants.HtmlLangAttr
import org.scalajs.dom
import org.scalajs.dom.Element
import org.scalajs.dom.raw.{MutationObserver, MutationRecord}

import scala.scalajs.js
import scala.scalajs.js.annotation.{JSExport, JSExportTopLevel}
import scala.collection.{mutable => m}

@JSExportTopLevel("ORBEON.xforms.Language")
object Language {

  import Private._

  // noinspection AccessorLikeMethodIsEmptyParen
  // Return the language for the page, defaulting to `en` if none is found
  // See also https://github.com/orbeon/orbeon-forms/issues/3787
  @JSExport
  def getLang(): String = {
    langElement
      .map(_.getAttribute(HtmlLangAttr).substring(0, 2))
      .getOrElse("en")
  }

  def onLangChange(listenerId: String, listener: String => Unit): Unit =
    langElement.foreach { elem =>
      val callback =
        (_: js.Array[MutationRecord], _: MutationObserver) => listener(getLang())
      val mutationObserver = new MutationObserver(callback)
      mutationObserver.observe(
        target  = elem,
        options = dom.MutationObserverInit(
          attributes      = true,
          attributeFilter = js.Array(HtmlLangAttr)
        )
      )
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

      val langElements: Iterator[() => Option[Element]] = Iterator(
        () => Option(dom.document.documentElement),
        () => Option(dom.document.querySelector(s".orbeon-portlet-div[$HtmlLangAttr]"))
      )
      langElements
        .map(_.apply)
        .collectFirst { case Some(element) if element.hasAttribute(HtmlLangAttr) => element }
    }
  }
}
