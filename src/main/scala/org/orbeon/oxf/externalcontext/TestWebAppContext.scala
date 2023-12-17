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
package org.orbeon.oxf.externalcontext

import org.log4s.Logger

import scala.collection.mutable

class TestWebAppContext(logger: Logger, val attributes: mutable.Map[String, AnyRef]) extends WebAppContext {
  def getResource(s: String)                     = throw new UnsupportedOperationException
  def getResourceAsStream(s: String)             = throw new UnsupportedOperationException
  val initParameters                             = Map.empty[String, String]
  def log(message: String, throwable: Throwable) = logger.error(throwable)(message)
  def log(message: String)                       = logger.info(message)
  def getNativeContext                           = null
}
