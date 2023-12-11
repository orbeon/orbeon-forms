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

trait ServiceHttpServlet {
  def service(req: HttpServletRequest, resp: HttpServletResponse): Unit
}

class JavaxServiceHttpServlet(impl: ServiceHttpServlet) extends javax.servlet.http.HttpServlet {
  override def service(req: javax.servlet.http.HttpServletRequest, resp: javax.servlet.http.HttpServletResponse): Unit =
    impl.service(HttpServletRequest(req), HttpServletResponse(resp))
}

class JakartaServiceHttpServlet(impl: ServiceHttpServlet) extends jakarta.servlet.http.HttpServlet {
  override def service(req: jakarta.servlet.http.HttpServletRequest, resp: jakarta.servlet.http.HttpServletResponse): Unit =
    impl.service(HttpServletRequest(req), HttpServletResponse(resp))
}
