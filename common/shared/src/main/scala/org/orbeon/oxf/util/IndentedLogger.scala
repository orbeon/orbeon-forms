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

import org.log4s
import org.orbeon.exception.OrbeonFormatter
import org.orbeon.oxf.util.IndentedLogger.*

import java.lang as jl
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
    new IndentedLogger(logger.logger, logger.debugEnabled, logger.indentation, logger.categoryOpt)

  def apply(logger: IndentedLogger, debugEnabled: Boolean): IndentedLogger =
    new IndentedLogger(logger.logger, debugEnabled, logger.indentation, logger.categoryOpt)

  private val Separator = " - "

  private def getLogIndentSpaces(level: Int): String = {
    val sb = new jl.StringBuilder
    for (_ <- 0 until level)
      sb.append("  ")
    sb.toString
  }

  private def log(
    logger     : log4s.Logger.LevelLogger,
    indentLevel: Int,
    `type`     : String,
    categoryOpt: Option[String],
    message    : String,
    parameters : String*
  ): Unit = {

    val sb = new jl.StringBuilder

    sb.append(getLogIndentSpaces(indentLevel))

    // Deprecated
    if (`type`.nonEmpty) {
      sb.append(`type`)
      sb.append(Separator)
    }

    categoryOpt.foreach { category =>
      sb.append(category)
      sb.append(Separator)
    }

    sb.append(message)

    if ((parameters ne null) && parameters.nonEmpty) {
      sb.append(" {")
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
    }

    logger(sb.toString)
  }

  class Indentation(var indentation: Int) {
    def this() = this(0)
  }
}

class IndentedLogger(
  val logger      : log4s.Logger,
  val debugEnabled: Boolean,
  val indentation : Indentation,
  val categoryOpt : Option[String] = None
) {

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

  // Debug block with optional parameters
  def withDebug[T](`type`: String, message: String, parameters: String*)(body: => T): T = {
    var success = true
    try {
      if (debugEnabled)
        startHandleOperation(`type`, message, parameters*)

      body
    } catch {
      case t: Throwable => // don't use `NonFatal()` here as we want to catch all for example `NoClassDefFoundError`
        success = false
        throw t
    } finally {
      if (debugEnabled)
        endHandleOperation(success)
    }
  }

  private def startHandleOperation(`type`: String, message: String, parameters: String*): Unit =
    if (debugEnabled) {
      stack ::= new Operation(`type`, message)
      logDebug(`type`, "start " + message, parameters*)
      indentation.indentation += 1
    }

  private def endHandleOperation(success: Boolean = true): Unit =
    if (debugEnabled)
      stack match {
        case operation :: tail =>

          stack = tail
          indentation.indentation -= 1

          val newParams =
            "time (ms)" :: operation.timeElapsed.toString :: operation.resultParameters.toList

          logDebug(
            operation.`type`,
            s"end ${operation.message} with ${if (success) "success" else "failure"}",
            newParams*
          )
        case _ =>
      }

  def setDebugResults(parameters: String*): Unit =
    if (debugEnabled)
      stack.head.resultParameters = parameters

  def log(level: log4s.LogLevel, `type`: String, message: String, parameters: String*): Unit =
    if (!((level eq log4s.Debug) && ! debugEnabled))
      log(level, indentation.indentation, `type`, message, parameters*)

  @varargs
  def logDebug(`type`: String, message: String, parameters: String*): Unit =
    if (debugEnabled)
      IndentedLogger.log(logger(log4s.Debug), indentation.indentation, `type`, categoryOpt, message, parameters*)

  def logWarning(`type`: String, message: String, parameters: String*): Unit =
    log(log4s.Warn, indentation.indentation, `type`, message, parameters*)

  def logInfo(`type`: String, message: String, parameters: String*): Unit =
    log(log4s.Info, indentation.indentation, `type`, message, parameters*)

  def logWarning(`type`: String, message: String, throwable: Throwable): Unit =
    log(log4s.Warn, indentation.indentation, `type`, message, "throwable", OrbeonFormatter.format(throwable))

  def logError(`type`: String, message: String, parameters: String*): Unit =
    log(log4s.Error, indentation.indentation, `type`, message, parameters*)

  def logError(`type`: String, message: String, throwable: Throwable): Unit =
    log(log4s.Error, indentation.indentation, `type`, message, "throwable", OrbeonFormatter.format(throwable))

  private def log(level: log4s.LogLevel, indentLevel: Int, `type`: String, message: String, parameters: String*): Unit =
    IndentedLogger.log(logger(level), indentLevel, `type`, categoryOpt, message, parameters*)

  private class Operation(val `type`: String, val message: String) {

    private val startTime =
      if (debugEnabled)
        System.currentTimeMillis
      else
        0L

    var resultParameters: Seq[String] = Nil

    def timeElapsed: Long = System.currentTimeMillis - startTime
  }
}