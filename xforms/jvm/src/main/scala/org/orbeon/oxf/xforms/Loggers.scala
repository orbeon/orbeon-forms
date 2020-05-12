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

import org.orbeon.oxf.util.IndentedLogger
import collection.mutable.HashMap
import processor.XFormsServer

// Global indented loggers
object Loggers {

  private val LoggersByCategory = new HashMap[String, IndentedLogger]

  // Return an indented logger for the given category
  // FIXME: more than 1 thread access the returned indented logger, which is stateful -> Use threadLocal?
  def getIndentedLogger(category: String): IndentedLogger = synchronized {

    def newLogger = {
      val logger = XFormsServer.logger
      val isDebugEnabled = logger.isDebugEnabled && XFormsProperties.getDebugLogging.contains(category)
      new IndentedLogger(logger, isDebugEnabled)
    }

    LoggersByCategory.getOrElseUpdate(category, newLogger)
  }
}
