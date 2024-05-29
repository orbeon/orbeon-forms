/**
  * Copyright (C) 2026 Orbeon, Inc.
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
package org.orbeon.xbl

import org.log4s.Logger
import org.orbeon.oxf.util.LoggerFactory
import org.orbeon.oxf.util.StringUtils.*
import org.orbeon.web.DomEventNames
import org.orbeon.web.DomSupport.*
import org.orbeon.xforms.*
import org.orbeon.xforms.facade.XBLCompanion
import org.scalajs.dom
import org.scalajs.dom.html

import scala.scalajs.js


object Url {

  private val logger: Logger = LoggerFactory.createLogger("org.orbeon.xbl.Url")

  private val DisabledClass = "fr-url-open-disabled"

  XFormsXbl.declareCompanion("fr|url", js.constructorOf[UrlCompanion])

  private class UrlCompanion(containerElem: html.Element) extends XBLCompanion {

    private object EventSupport extends EventListenerSupport

    private var inputElemOpt   : Option[html.Input]           = None
    private var openLinkElemOpt: Option[html.Anchor]          = None
    private var observerOpt    : Option[dom.MutationObserver] = None

    private def isDesignTime: Boolean =
      containerElem.closestOpt(".fb-main").isDefined

    private def syncInputAttributes(inputElem: html.Input): Unit = {
      inputElem.setAttribute("inputmode", "url")
      inputElem.setAttribute("autocorrect", "off")
      inputElem.setAttribute("autocapitalize", "off")
      inputElem.setAttribute("spellcheck", "false")
    }

    private def syncOpenLinkFromValue(rawValue: String): Unit =
      openLinkElemOpt.foreach { openLinkElem =>
        val validUrlOpt =
          if (isDesignTime) None
          else              UrlSupport.normalizeValidWebUrl(rawValue)

        validUrlOpt match {
          case Some(validUrl) =>
            openLinkElem.setAttribute("href", validUrl)
            openLinkElem.setAttribute("aria-disabled", "false")
            openLinkElem.setAttribute("tabindex", "0")
            openLinkElem.classList.remove(DisabledClass)
          case None =>
            openLinkElem.removeAttribute("href")
            openLinkElem.setAttribute("aria-disabled", "true")
            openLinkElem.setAttribute("tabindex", "-1")
            openLinkElem.classList.add(DisabledClass)
        }
      }

    override def init(): Unit = {

      logger.debug("init")

      inputElemOpt    = containerElem.queryNestedElems[html.Input](".xforms-input input").headOption
      openLinkElemOpt = containerElem.queryNestedElems[html.Anchor]("a.fr-url-open").headOption

      inputElemOpt.foreach { inputElem =>
        syncInputAttributes(inputElem)
        syncOpenLinkFromValue(inputElem.value)

        EventSupport.addListener[dom.Event](inputElem, DomEventNames.Input, _ => {
          syncOpenLinkFromValue(inputElem.value)
        })
      }

      openLinkElemOpt.foreach { openLinkElem =>
        if (inputElemOpt.isEmpty)
          syncOpenLinkFromValue(openLinkElem.dataset.get("href").getOrElse(""))

        EventSupport.addListener[dom.MouseEvent](openLinkElem, DomEventNames.Click, e => {
          if (isDesignTime || ! openLinkElem.hasAttribute("href") || openLinkElem.attValueOpt("href").forall(_.isAllBlank))
            e.preventDefault()
        })

        val observer = new dom.MutationObserver((_, _) => {
          val serverHref = openLinkElem.dataset.get("href").getOrElse("")
          syncOpenLinkFromValue(serverHref)
        })
        observer.observe(
          openLinkElem,
          new dom.MutationObserverInit {
            attributes      = true
            attributeFilter = js.Array("data-href")
          }
        )
        observerOpt = Some(observer)
      }
    }

    override def destroy(): Unit = {
      logger.debug("destroy")
      observerOpt.foreach(_.disconnect())
      observerOpt = None
      EventSupport.clearAllListeners()
      inputElemOpt    = None
      openLinkElemOpt = None
    }
  }
}
