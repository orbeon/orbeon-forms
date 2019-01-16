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

import org.orbeon.jquery._
import org.orbeon.xforms.facade.Init

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}


trait App {

  import Private._

  def load(): Unit

  def main(args: Array[String]): Unit = {

    Logging.initialize()

    scribe.info("Starting XForms app")

    $.readyF                flatMap
      (_ ⇒ liferayF)        flatMap
      (_ ⇒ orbeonInitDataF) onComplete {

      case Success(initData) ⇒
        scribe.debug("running initializations")
        load()
        Init.initializeGlobals()
        InitSupport.initializeAllForms(initData)
      case Failure(t) ⇒
        throw t
    }
  }

  private object Private {

    import org.orbeon.liferay._
    import org.orbeon.oxf.util.FutureUtils
    import org.orbeon.xforms.facade.InitData
    import org.scalajs.dom

    import scala.concurrent.ExecutionContext.Implicits.global
    import scala.concurrent.Future
    import scala.concurrent.duration._
    import scala.scalajs.js
    import scala.scalajs.js.Dictionary
    import scala.scalajs.js.Dynamic.{global ⇒ g}
    import scala.util.control.ControlThrowable

    private val Interval = 100.milliseconds
    private val Timeout  = 2.minutes

    private var waitLogged = false

    private val dummyException = new ControlThrowable {}

    def liferayF: Future[Unit] = {
      scribe.debug("checking for Liferay object")
      dom.window.Liferay.toOption match {
        case None          ⇒ Future.successful(())
        case Some(liferay) ⇒ liferay.allPortletsReadyF
      }
    }

    // https://github.com/orbeon/orbeon-forms/issues/3893
    def orbeonInitDataF: Future[Dictionary[InitData]] =
      FutureUtils.eventually(Interval, Timeout) {
        if (js.isUndefined(g.orbeonInitData)) {
          if (! waitLogged)
            scribe.debug("`orbeonInitData` not found, waiting")
          waitLogged = true
          Future.failed(dummyException)
        } else {
          scribe.debug("`orbeonInitData` found")
          Future.successful(g.orbeonInitData.asInstanceOf[js.Dictionary[InitData]])
        }
      }
  }

  private object Logging {

    import scribe.format._
    import scribe.output.{LogOutput, TextOutput}
    import scribe.{Level, LogRecord}

    import scala.scalajs.LinkingInfo

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

    def initialize(): Unit = {

      val rootLevel = if (LinkingInfo.developmentMode) Level.Debug else Level.Error

      scribe.Logger.root.clearHandlers().clearModifiers().withHandler(
        minimumLevel = Some(rootLevel),
        formatter    = customFormatter
      ).replace()
    }
  }
}
