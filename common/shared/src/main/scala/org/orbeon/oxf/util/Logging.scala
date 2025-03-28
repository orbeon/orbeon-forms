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

import scala.util.control.NonFatal


// More Scala-friendly indented logger API
trait Logging {

  // Error with optional parameters
  def error(message: => String, parameters: => Seq[(String, String)] = Nil)(implicit logger: IndentedLogger): Unit =
    logger.logError("", message, flattenTuples(parameters)*)

  // Error with optional parameters
  def error(message: => String, throwable: Throwable)(implicit logger: IndentedLogger): Unit =
    logger.logError("", message, throwable)

  // Warn with optional parameters
  def warn(message: => String, parameters: => Seq[(String, String)] = Nil)(implicit logger: IndentedLogger): Unit =
    logger.logWarning("", message, flattenTuples(parameters)*)

  // Info with optional parameters
  def info(message: => String, parameters: => Seq[(String, String)] = Nil)(implicit logger: IndentedLogger): Unit =
    if (logger.infoEnabled)
      logger.logInfo("", message, flattenTuples(parameters)*)

  // Debug with optional parameters
  def debug(message: => String, parameters: => Seq[(String, String)] = Nil)(implicit logger: IndentedLogger): Unit =
    if (logger.debugEnabled)
      logger.logDebug("", message, flattenTuples(parameters)*)

  // Debug with optional parameters
  def log(logLevel: LogLevel, message: => String, parameters: => Seq[(String, String)] = Nil)(implicit logger: IndentedLogger): Unit =
    if (logger.debugEnabled)
      logger.log(logLevel, "", message, flattenTuples(parameters)*)

  // Debug block with optional parameters
  def withDebug[T](message: => String, parameters: => Seq[(String, String)] = Nil)(body: => T)(implicit logger: IndentedLogger): T =
    if (logger.debugEnabled)
      logger.withDebug("", message, flattenTuples(parameters)*)(body)
    else
      body

  def debugResult[T](message: => String, parameters: => Seq[(String, String)] = Nil)(body: => T)(implicit logger: IndentedLogger): T = {
    val r = body
    debug(message, ("result" -> r.toString) +: parameters)
    r
  }

  def maybeWithDebug[T](message: => String, parameters: => Seq[(String, String)] = Nil, condition: Boolean = true)(body: => T)(implicit logger: IndentedLogger): T =
    if (condition)
      withDebug(message, parameters)(body)
    else
      body

  // Run the given block only in debug mode
  def ifDebug[T](body: => T)(implicit logger: IndentedLogger): Unit =
    if (logger.debugEnabled)
      body: Unit

  // Call from a result block to set result parameters
  def debugResults(parameters: => Seq[(String, String)])(implicit logger: IndentedLogger): Unit =
    if (logger.debugEnabled)
      logger.setDebugResults(flattenTuples(parameters)*)

  private def flattenTuples(tuples: Seq[(String, String)]): Seq[String] =
    tuples flatMap { case (n, v) => Seq(n, v) }
}

object Logging extends Logging
