/**
 * Copyright (C) 2023 Orbeon, Inc.
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

import java.{util => ju}

// TODO: merge JavaxOrJakartaServlet and ServletContextProvider?

trait JavaxOrJakartaServlet

trait ServletContextProvider {
  def getInitParameter(name: String): String
  def getInitParameterNames: ju.Enumeration[String]
  def getOrbeonServletContext: ServletContext
}

trait HttpServlet {
  def destroy(): Unit = ()
  def init(): Unit
  def service(req: HttpServletRequest, resp: HttpServletResponse): Unit
}

abstract class JavaxHttpServlet extends javax.servlet.http.HttpServlet with JavaxOrJakartaServlet with ServletContextProvider {
  def httpServlet: HttpServlet

  override def getOrbeonServletContext: ServletContext =
    ServletContext(getServletContext)

  override def destroy(): Unit =
    httpServlet.destroy()
  override def init(): Unit =
    httpServlet.init()
  override def service(req: javax.servlet.http.HttpServletRequest, resp: javax.servlet.http.HttpServletResponse): Unit =
    httpServlet.service(HttpServletRequest(req), HttpServletResponse(resp))
}

abstract class JakartaHttpServlet extends jakarta.servlet.http.HttpServlet with JavaxOrJakartaServlet with ServletContextProvider {
  def httpServlet: HttpServlet

  override def getOrbeonServletContext: ServletContext =
    ServletContext(super.getServletContext)

  override def destroy(): Unit =
    httpServlet.destroy()
  override def init(): Unit =
    httpServlet.init()
  override def service(req: jakarta.servlet.http.HttpServletRequest, resp: jakarta.servlet.http.HttpServletResponse): Unit =
    httpServlet.service(HttpServletRequest(req), HttpServletResponse(resp))
}
