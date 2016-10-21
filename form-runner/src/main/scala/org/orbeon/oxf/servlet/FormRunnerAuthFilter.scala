/**
 * Copyright (C) 2015 Orbeon, Inc.
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

import javax.servlet._
import javax.servlet.http.HttpServletRequest

import org.orbeon.oxf.fr.FormRunnerAuth

class FormRunnerAuthFilter extends Filter {

  def doFilter(req: ServletRequest, res: ServletResponse, chain: FilterChain) =
    chain.doFilter(FormRunnerAuthFilter.amendRequest(req.asInstanceOf[HttpServletRequest]), res)

  def init(filterConfig: FilterConfig) = ()
  def destroy() = ()
}

object FormRunnerAuthFilter {

  import java.{util ⇒ ju}

  import scala.collection.JavaConverters._

  def amendRequest(servletRequest: HttpServletRequest): HttpServletRequest = {

    val authHeaders = FormRunnerAuth.getCredentialsAsHeadersUseSession(
      userRoles = servletRequest,
      session   = servletRequest.getSession(true),
      getHeader = servletRequest.getHeaders(_).asInstanceOf[ju.Enumeration[String]].asScala.to[List]
    ).toMap

    trait CustomHeaders extends RequestRemoveHeaders with RequestPrependHeaders  {
      val headersToRemove  = FormRunnerAuth.AllHeaderNamesLower
      val headersToPrepend = authHeaders
    }

    new BaseServletRequestWrapper(servletRequest) with CustomHeaders
  }
}