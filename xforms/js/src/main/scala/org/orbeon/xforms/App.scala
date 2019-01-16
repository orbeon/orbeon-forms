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
import org.orbeon.oxf.util.FutureUtils
import org.orbeon.xforms.facade.Init
import org.scalajs.dom
import scribe.format._
import scribe.output.{LogOutput, TextOutput}
import scribe.{Level, LogRecord}

import scala.scalajs.js.Dynamic.{global ⇒ g}
import scala.scalajs.{LinkingInfo, js}


trait App {

  // Custom log formatter to output something that is readable
  private object CustomPosition extends FormatBlock {

    import org.orbeon.oxf.util.StringUtils._
    import perfolation._

    def format[M](record: LogRecord[M]): LogOutput = {

      val tokens    = record.className.splitTo[List](".").reverse
      val className = tokens.find(! _.startsWith("$")) getOrElse tokens.last

      record.methodName match {
        case Some(name) ⇒ new TextOutput(p"$className.$name")
        case None       ⇒ new TextOutput(p"$className")
      }
    }
  }

  private val customFormatter: Formatter = Formatter.fromBlocks(
    FormatBlock.Level,
    FormatBlock.RawString(" "),
    CustomPosition,
    FormatBlock.RawString(" - "),
    FormatBlock.Message
  )

  def load(): Unit

  def main(args: Array[String]): Unit = {

    val rootLevel = if (LinkingInfo.developmentMode) Level.Debug else Level.Error

    scribe.Logger.root.clearHandlers().clearModifiers().withHandler(
      minimumLevel = Some(rootLevel),
      formatter    = customFormatter
    ).replace()

    scribe.info("Starting XForms app")

    def loadAndInit(): Unit = {
      scribe.debug("running initializations")
      load()
      Init.initializeGlobals()
      InitSupport.initializeAllForms()
    }

    var waitLogged = false

    // https://github.com/orbeon/orbeon-forms/issues/3893
    lazy val waitForOrbeonInitData: js.Function0[js.Any] =
      () ⇒ {
        if (js.isUndefined(g.orbeonInitData)) {
          if (! waitLogged)
            scribe.debug("waiting for `orbeonInitData` to be available")
          else
            scribe.trace("waiting for `orbeonInitData` to be available")
          waitLogged = true
          js.timers.RawTimers.setTimeout(
            waitForOrbeonInitData,
            100
          )
        } else {
          loadAndInit(): js.Any
        }
      }

    $.apply({
      () ⇒ {
        scribe.debug("starting DOM ready initializations")
        dom.window.Liferay.toOption match {
          case None          ⇒ waitForOrbeonInitData()
          case Some(liferay) ⇒ liferay.on("allPortletsReady", waitForOrbeonInitData)
        }
      }
    }: js.Function)
  }

}
