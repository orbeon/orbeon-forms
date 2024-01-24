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

import java.util.concurrent.ConcurrentHashMap


// Global indented loggers
object Loggers {

  val logger: Logger = LoggerFactory.createLogger("org.orbeon.oxf.xforms.processor.XFormsServer")

  private val LoggersByCategory = new ConcurrentHashMap[String, IndentedLogger]

  def getIndentedLogger(category: String): IndentedLogger =
    LoggersByCategory.computeIfAbsent(
      category,
      _ => newIndentedLogger(category)
    )

  // Used by:
  // - ContainingDocumentLogging: "document", etc. (11 categories) that share indentation within a document
  // - XFormsStaticStateImpl: "analysis"
  def newIndentedLogger(
    category   : String,
    indentation: IndentedLogger.Indentation = new IndentedLogger.Indentation
  ): IndentedLogger =
    new IndentedLogger(
      logger,
      isDebugEnabled(category),
      indentation
    )

  def isDebugEnabled(category: String): Boolean =
    logger.isDebugEnabled && XFormsGlobalProperties.getDebugLogging.contains(category)
}
