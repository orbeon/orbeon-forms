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
package org.orbeon.oxf.http

import org.orbeon.exception.OrbeonFormatter

trait HttpStatusCode extends RuntimeException { def code: Int }

case class HttpStatusCodeException(
  code      : Int,
  resource  : Option[String]    = None,
  throwable : Option[Throwable] = None
) extends HttpStatusCode {
  override def toString =
    s"HttpStatusCodeException(code = $code, resource = $resource, throwable = ${throwable map OrbeonFormatter.message getOrElse ""})"
}

case class HttpRedirectException(
  location   : String,
  serverSide : Boolean = false,
  exitPortal : Boolean = false
) extends HttpStatusCode {
  val code = StatusCode.Found // using 302 instead of 303 as 302 is still the de facto standard
  override def toString = s"HttpRedirectException(location = $location, serverSide = $serverSide, exitPortal = $exitPortal)"
}

case class SessionExpiredException(message: String) extends HttpStatusCode {
  val code = StatusCode.Forbidden
  override def toString = s"SessionExpiredException(message= $message)"
}