/**
 * Copyright (C) 2012 Orbeon, Inc.
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
package org.orbeon.oxf.util

import org.log4s.LogLevel


// More Scala-friendly indented logger API
trait Logging {

  // Error with optional parameters
  def error(message: => String, parameters: => Seq[(String, String)] = Nil)(implicit logger: IndentedLogger): Unit =
    logger.logError("", message, flattenTuples(parameters): _*)

  // Warn with optional parameters
  def warn(message: => String, parameters: => Seq[(String, String)] = Nil)(implicit logger: IndentedLogger): Unit =
    logger.logWarning("", message, flattenTuples(parameters): _*)

  // Info with optional parameters
  def info(message: => String, parameters: => Seq[(String, String)] = Nil)(implicit logger: IndentedLogger): Unit =
    if (logger.infoEnabled)
      logger.logInfo("", message, flattenTuples(parameters): _*)

  // Debug with optional parameters
  def debug(message: => String, parameters: => Seq[(String, String)] = Nil)(implicit logger: IndentedLogger): Unit =
    if (logger.debugEnabled)
      logger.logDebug("", message, flattenTuples(parameters): _*)

  // Debug with optional parameters
  def log(logLevel: LogLevel, message: => String, parameters: => Seq[(String, String)] = Nil)(implicit logger: IndentedLogger): Unit =
    if (logger.debugEnabled)
      logger.log(logLevel, "", message, flattenTuples(parameters): _*)

  // Debug block with optional parameters
  def withDebug[T](message: => String, parameters: => Seq[(String, String)] = Nil)(body: => T)(implicit logger: IndentedLogger): T =
    try {
      if (logger.debugEnabled)
        logger.startHandleOperation("", message, flattenTuples(parameters): _*)

      body
    } finally {
      if (logger.debugEnabled)
        logger.endHandleOperation()
    }

  // Run the given block only in debug mode
  def ifDebug[T](body: => T)(implicit logger: IndentedLogger): Unit =
    if (logger.debugEnabled)
      body

  // Whether debug logging is enabled
  def debugEnabled(implicit logger: IndentedLogger): Boolean = logger.debugEnabled

  // Call from a result block to set result parameters
  def debugResults(parameters: => Seq[(String, String)])(implicit logger: IndentedLogger): Unit =
    if (logger.debugEnabled)
      logger.setDebugResults(flattenTuples(parameters): _*)

  private def flattenTuples(tuples: Seq[(String, String)]) =
    tuples flatMap { case (n, v) => Seq(n, v) }
}

object Logging extends Logging
