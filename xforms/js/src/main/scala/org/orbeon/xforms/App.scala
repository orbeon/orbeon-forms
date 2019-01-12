/**
 * Copyright (C) 2018 Orbeon, Inc.
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

import org.orbeon.liferay._
import org.orbeon.xforms.facade.Init
import org.scalajs.dom
import scribe.Level
import scribe.format._

import scala.scalajs.LinkingInfo


trait App {

  private val myFormatter: Formatter = formatter"$level $positionAbbreviated - $message$mdc$newLine"

  private def debug(s: String): Unit = scribe.debug(s)

  def load(): Unit

  def main(args: Array[String]): Unit = {

    val rootLevel = if (LinkingInfo.developmentMode) Level.Debug else Level.Error

    scribe.Logger.root.clearHandlers().clearModifiers().withHandler(
      minimumLevel = Some(rootLevel),
      formatter    = myFormatter
    ).replace()

    scribe.info("Starting XForms app")

    def loadAndInit(): Unit = {
      debug("starting DOM ready initializations")
      load()
      Init.initializeGlobals()
      InitSupport.initializeAllForms()
    }

    $(() ⇒ {
      dom.window.Liferay.toOption match {
        case None          ⇒ $(loadAndInit _)
        case Some(liferay) ⇒ liferay.on("allPortletsReady", loadAndInit _)
      }
    })
  }

}
