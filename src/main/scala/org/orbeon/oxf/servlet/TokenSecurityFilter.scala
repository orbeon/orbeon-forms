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
package org.orbeon.oxf.servlet

import org.orbeon.oxf.controller.Authorizer

// This filter checks that the caller provides the appropriate request token. If not, it sends a 403 back.
class TokenSecurityFilter extends Filter {

  private var servletContext: ServletContext = _

  override def init(config: FilterConfig): Unit = servletContext = config.getServletContext
  override def destroy(): Unit = servletContext = null

  def doFilter(req: ServletRequest, res: ServletResponse, chain: FilterChain): Unit = {
    val httpReq = req.asInstanceOf[HttpServletRequest]

    def authorized =
      Authorizer.authorizedWithToken(
        k => Option(httpReq.getHeader(k)) map (v => Array(v)),
        k => Option(servletContext.getAttribute(k)))

    if (authorized) {
      // Go along
      chain.doFilter(req, res)
    } else {
      // Tell the client access is forbidden
      val httpRes = res.asInstanceOf[HttpServletResponse]
      httpRes.setStatus(403)
    }
  }
}
