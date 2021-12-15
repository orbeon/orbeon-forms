/**
 * Copyright (C) 2009 Orbeon, Inc.
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

object LoggerFactory {

  val logger: org.log4s.Logger = createLogger("org.orbeon.oxf.util.LoggerFactory")

  def createLogger(name: String)   : log4s.Logger = org.log4s.getLogger(name)
  def createLogger(clazz: Class[_]): log4s.Logger = org.log4s.getLogger(clazz.getName)

  def createLoggerJava(name: String):    org.slf4j.Logger = createLogger(name).logger
  def createLoggerJava(clazz: Class[_]): org.slf4j.Logger = createLogger(clazz).logger
}