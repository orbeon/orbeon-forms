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

import org.log4s
import org.orbeon.web.DomSupport
import org.scalajs.dom
import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits._

import scala.util.{Failure, Success}


trait App {

  //  private val LogLevel = log4s.Debug // Debug|Info|Warn|Error
  protected val LogLevel: log4s.LogLevel = log4s.Info // Trace|Debug|Info|Warn|Error

  def onOrbeonApiLoaded(): Unit
  def onPageContainsFormsMarkup(): Unit

  def main(args: Array[String]): Unit = {

    Logging.initialize()

    scribe.info("Starting Orbeon client-side web app")

    scribe.debug("running initializations after Orbeon API is available")
    onOrbeonApiLoaded()

    DomSupport.atLeastDomInteractiveF(dom.document) flatMap (_ => InitSupport.liferayF) onComplete {
      case Success(_) =>
        scribe.debug("running initializations after form markup is available")
        onPageContainsFormsMarkup()
      case Failure(t) =>
        throw t
    }
  }

  private object Logging {

    import scribe.format._
    import scribe.output.{LogOutput, TextOutput}
    import scribe.{Level, LogRecord}

    // Custom log formatter to output something that is readable
    private object CustomPosition extends FormatBlock {

      import org.orbeon.oxf.util.StringUtils._
      import perfolation._

      def format[M](record: LogRecord[M]): LogOutput = {

        val tokens    = record.className.splitTo[List](".").reverse
        val className = tokens.find(! _.startsWith("$")) getOrElse tokens.last

        record.methodName match {
          case Some(name) => new TextOutput(s"$className.$name")
          case None       => new TextOutput(s"$className")
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

    def initialize(): Unit = {

      val rootLevel =
        LogLevel match {
          case log4s.Trace => Level.Trace
          case log4s.Debug => Level.Debug
          case log4s.Info  => Level.Info
          case log4s.Warn  => Level.Warn
          case log4s.Error => Level.Error
        }

      scribe.Logger.root.clearHandlers().clearModifiers().withHandler(
        minimumLevel = Some(rootLevel),
        formatter    = customFormatter
      ).replace()
    }
  }
}
