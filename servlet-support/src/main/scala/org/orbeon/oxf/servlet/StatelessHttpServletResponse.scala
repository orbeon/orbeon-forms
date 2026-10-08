/**
 * Copyright (C) 2026 Orbeon, Inc.
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
package org.orbeon.oxf.servlet

class StatelessHttpServletResponse(
  response: HttpServletResponse
) extends HttpServletResponseWrapper(response) {

  private def isJSessionIdCookie(name: String, value: String): Boolean =
    Option(name).exists(n => n.equalsIgnoreCase("Set-Cookie") || n.equalsIgnoreCase("Set-Cookie2")) &&
      Option(value).exists(_.toUpperCase.contains("JSESSIONID"))

  override def setHeader(name: String, value: String): Unit =
    if (! isJSessionIdCookie(name, value))
      super.setHeader(name, value)

  override def addHeader(name: String, value: String): Unit =
    if (! isJSessionIdCookie(name, value))
      super.addHeader(name, value)
}
