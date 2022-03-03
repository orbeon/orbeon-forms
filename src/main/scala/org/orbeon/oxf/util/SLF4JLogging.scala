/**
  * Copyright (C) 2018 Orbeon, Inc.
  *
  * This program is free software; you can redistribute it and/or modify it under the terms of the
  * GNU Lesser General Public License as published by the Free Software Foundation; either version
  *  2.1 of the License, or (at your option) any later version.
  *
  * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  * See the GNU Lesser General Public License for more details.
  *
  * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
  */
package org.orbeon.oxf.util


object SLF4JLogging {

  def withDebug[T](
    message    : => String,
    parameters : => Seq[(String, String)] = Nil)(
    body       : => T)(implicit
    logger     : org.slf4j.Logger
  ): T =
    try {
      if (logger.isDebugEnabled)
        logger.debug(s"start $message", flattenParams(parameters)
        )

      body
    } finally {
      if (logger.isDebugEnabled)
        logger.debug(s"end $message")
    }

  def error(message: => String, parameters: => Seq[(String, String)] = Nil)(implicit logger: org.slf4j.Logger): Unit =
    logger.error(message, flattenParams(parameters))

  def warn(message: => String, parameters: => Seq[(String, String)] = Nil)(implicit logger: org.slf4j.Logger): Unit =
    logger.warn(message, flattenParams(parameters))

  def info(message: => String, parameters: => Seq[(String, String)] = Nil)(implicit logger: org.slf4j.Logger): Unit =
    logger.info(message, flattenParams(parameters))

  def debug(message: => String, parameters: => Seq[(String, String)] = Nil)(implicit logger: org.slf4j.Logger): Unit =
    logger.debug(message, flattenParams(parameters))

  def ifDebug[T](body: => T)(implicit logger: org.slf4j.Logger): Unit =
    if (logger.isDebugEnabled)
      body

  private def flattenParams(tuples: Seq[(String, String)]): String =
    tuples collect {
      case (k, v) if (k ne null) && (v ne null) => s"""$k: "$v""""
    } mkString ("{", ", ", "}")
}
