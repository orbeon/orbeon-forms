/**
 * Copyright (C) 2010 Orbeon, Inc.
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

import java.{lang => jl}

import org.log4s
import org.orbeon.exception.OrbeonFormatter
import org.orbeon.oxf.util.IndentedLogger._

import scala.annotation.varargs

/**
 * Abstraction which provides:
 *
 * - start/end operation with parameters
 * - indenting depending on current nesting of operations
 * - custom handling of debug level
 */
object IndentedLogger {

  def apply(logger: IndentedLogger): IndentedLogger =
    new IndentedLogger(logger.logger, logger.debugEnabled, logger.indentation)

  def apply(logger: IndentedLogger, debugEnabled: Boolean): IndentedLogger =
    new IndentedLogger(logger.logger, debugEnabled, logger.indentation)

  private def getLogIndentSpaces(level: Int): String = {
    val sb = new jl.StringBuilder
    for (_ <- 0 until level)
      sb.append("  ")
    sb.toString
  }

  private def log(
    logger      : log4s.Logger.LevelLogger,
    indentLevel : Int,
    `type`      : String,
    message     : String,
    parameters  : String*
  ): Unit = {
    val parametersString =
      if ((parameters ne null) && parameters.nonEmpty) {
        val sb = new jl.StringBuilder(" {")
        var first = true
        var i = 0
        while (i < parameters.length) {
          val paramName = parameters(i)
          val paramValue = parameters(i + 1)
          if ((paramName ne null) && (paramValue ne null)) {
            if (! first)
              sb.append(", ")
            sb.append(paramName)
            sb.append(": \"")
            sb.append(paramValue)
            sb.append('\"')
            first = false
          }
          i += 2
        }
        sb.append('}')
        sb.toString
      } else
        ""
    val text = (if (`type`.nonEmpty) `type` + " - " else "") + message + parametersString
    val indentation = getLogIndentSpaces(indentLevel)
    logger(indentation + text)
  }

  class Indentation(var indentation: Int) {
    def this() = this(0)
  }
}

class IndentedLogger(val logger: log4s.Logger, val debugEnabled: Boolean, val indentation: Indentation) {

  private var stack: List[Operation] = Nil

  def this(logger: log4s.Logger) =
    this(logger, logger.isDebugEnabled, new Indentation)

  def this(logger: log4s.Logger, isDebugEnabled: Boolean) =
    this(logger, isDebugEnabled, new Indentation)

  // 2 usages
  def this(indentedLogger: IndentedLogger, indentation: Indentation, isDebugEnabled: Boolean) =
    this(indentedLogger.logger, isDebugEnabled, indentation)

  def infoEnabled: Boolean =
    logger.isInfoEnabled

  def startHandleOperation(`type`: String, message: String, parameters: String*): Unit =
    if (debugEnabled) {
      stack ::= new Operation(`type`, message)
      logDebug(`type`, "start " + message, parameters: _*)
      indentation.indentation += 1
    }

  def endHandleOperation(success: Boolean = true): Unit =
    if (debugEnabled) {
      val resultParameters = stack.head.resultParameters
      indentation.indentation -= 1

      stack match {
        case operation :: tail =>

          stack = tail

          val timeParams = List("time (ms)", operation.timeElapsed.toString)

          val newParams =
            if (resultParameters ne null)
              timeParams ::: resultParameters.toList
            else
              timeParams

          logDebug(
            operation.`type`,
            s"end ${operation.message} with ${if (success) "success" else "failure"}",
            newParams: _*
          )
        case _ =>
      }
    }

  def setDebugResults(parameters: String*): Unit =
    stack.head.resultParameters = parameters

  def log(level: log4s.LogLevel, `type`: String, message: String, parameters: String*): Unit =
    log(level, indentation.indentation, `type`, message, parameters: _*)

  @varargs
  def logDebug(`type`: String, message: String, parameters: String*): Unit =
    if (debugEnabled)
      IndentedLogger.log(logger(log4s.Debug), indentation.indentation, `type`, message, parameters: _*)

  def logDebug(`type`: String, message: String, throwable: Throwable): Unit =
    if (debugEnabled)
      IndentedLogger.log(logger(log4s.Debug), indentation.indentation, `type`, message, "throwable", OrbeonFormatter.format(throwable))

  def logWarning(`type`: String, message: String, parameters: String*): Unit =
    log(log4s.Warn, indentation.indentation, `type`, message, parameters: _*)

  def logInfo(`type`: String, message: String, parameters: String*): Unit =
    log(log4s.Info, indentation.indentation, `type`, message, parameters: _*)

  def logWarning(`type`: String, message: String, throwable: Throwable): Unit =
    log(log4s.Warn, indentation.indentation, `type`, message, "throwable", OrbeonFormatter.format(throwable))

  def logError(`type`: String, message: String, parameters: String*): Unit =
    log(log4s.Error, indentation.indentation, `type`, message, parameters: _*)

  def logError(`type`: String, message: String, throwable: Throwable): Unit =
    log(log4s.Error, indentation.indentation, `type`, message, "throwable", OrbeonFormatter.format(throwable))

  // Handle DEBUG level locally, everything else goes through
  private def log(level: log4s.LogLevel, indentLevel: Int, `type`: String, message: String, parameters: String*): Unit =
    if (! ((level eq log4s.Debug) && ! debugEnabled))
      IndentedLogger.log(logger(level), indentLevel, `type`, message, parameters: _*)

  private class Operation(val `type`: String, val message: String) {

    private val startTime =
      if (debugEnabled)
        System.currentTimeMillis
      else
        0L

    var resultParameters: Seq[String] = null

    def timeElapsed: Long = System.currentTimeMillis - startTime
  }
}