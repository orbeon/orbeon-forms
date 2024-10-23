/**
 * Copyright (C) 2017 Orbeon, Inc.
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
package org.orbeon.xbl

import org.orbeon.xforms.$
import org.orbeon.xforms.facade.{XBL, XBLCompanion}
import org.scalajs.dom
import org.scalajs.dom.html

import scala.scalajs.js


object HrefButton {

  XBL.declareCompanion("fr|href-button", js.constructorOf[HrefButton])

  private class HrefButton(containerElem: html.Element) extends XBLCompanion {

    override def init(): Unit =
      containerElem.querySelector("button").addEventListener("click", (_: dom.Event) => onClick())

    def enabled() = ()

    private def onClick(): Unit = {
      val a = $(containerElem).find(".fr-href-button-anchor")
      org.scalajs.dom.window.open(
        url      = a.attr("href"  ).toString,
        target   = a.attr("target").toString,
        features = "noopener"
      )
    }
  }
}
