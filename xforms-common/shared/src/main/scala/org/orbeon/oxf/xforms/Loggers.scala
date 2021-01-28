/**
 * Copyright (C) 2007 Orbeon, Inc.
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
package org.orbeon.oxf.xforms

import org.log4s.Logger
import org.orbeon.oxf.util.{IndentedLogger, LoggerFactory}

import scala.collection.mutable


// Global indented loggers
object Loggers {

  val logger: Logger = LoggerFactory.createLogger("org.orbeon.oxf.xforms.processor.XFormsServer")

  private val LoggersByCategory = new mutable.HashMap[String, IndentedLogger]

  // Return an indented logger for the given category
  def getIndentedLogger(category: String): IndentedLogger = synchronized {

    def newLogger = {
      val logger = Loggers.logger
      val isDebugEnabled = logger.isDebugEnabled && XFormsGlobalProperties.getDebugLogging.contains(category)
      new IndentedLogger(logger, isDebugEnabled)
    }

    LoggersByCategory.getOrElseUpdate(category, newLogger)
  }
}
