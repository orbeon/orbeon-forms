/**
 *  Copyright (C) 2012 Orbeon, Inc.
 *
 *  This program is free software; you can redistribute it and/or modify it under the terms of the
 *  GNU Lesser General Public License as published by the Free Software Foundation; either version
 *  2.1 of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU Lesser General Public License for more details.
 *
 *  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.controller

import org.orbeon.oxf.servlet._

// For backward compatibility
class AuthorizerServlet extends JavaxAuthorizerServlet

class JavaxAuthorizerServlet   extends JavaxServiceHttpServlet  (new AuthorizerServletImpl)
class JakartaAuthorizerServlet extends JakartaServiceHttpServlet(new AuthorizerServletImpl)

// This servlet just returns an ok response when accessed
class AuthorizerServletImpl extends ServiceHttpServlet {
  override def service(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    response.setStatus(200)
    response.setContentType("text/plain")
  }
}
