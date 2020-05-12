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
        logger.debug(
          s"start $message",
          parameters collect {
            case (k, v) if (k ne null) && (v ne null) => s"""$k: "$v""""
          } mkString ("{", ", ", "}")
        )

      body
    } finally {
      if (logger.isDebugEnabled)
        logger.debug(s"end $message")
    }
}
