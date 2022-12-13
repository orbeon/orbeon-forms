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
import org.log4s.Logger
import org.log4s.log4sjs.Log4sConfig.setLoggerThreshold
import org.orbeon.oxf.util.LoggerFactory
import org.orbeon.web.DomSupport
import org.scalajs.dom
import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits._

import scala.util.{Failure, Success}


trait App {

  private val logger: Logger = LoggerFactory.createLogger("org.orbeon.xforms.App")

  //  private val LogLevel = log4s.Debug // Debug|Info|Warn|Error
  protected val LogLevel: log4s.LogLevel = log4s.Info // Trace|Debug|Info|Warn|Error

  def onOrbeonApiLoaded(): Unit
  def onPageContainsFormsMarkup(): Unit

  def main(args: Array[String]): Unit = {

    Logging.initialize()

    logger.info("Starting Orbeon client-side web app")

    logger.debug("running initializations after Orbeon API is available")
    onOrbeonApiLoaded()

    DomSupport.atLeastDomReadyStateF(dom.document, DomSupport.DomReadyState.Interactive) flatMap
      (_ => InitSupport.liferayF) onComplete {
      case Success(_) =>
        logger.debug("running initializations after form markup is available")
        onPageContainsFormsMarkup()
      case Failure(t) =>
        throw t
    }
  }

  private object Logging {
    def initialize(): Unit = {
      setLoggerThreshold("", LogLevel)
    }
  }
}
