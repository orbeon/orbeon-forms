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
import javax.servlet.http.{HttpServletRequest, HttpServletRequestWrapper, HttpServletResponse}
import org.orbeon.oxf.fr.FormRunnerAuth
import org.orbeon.oxf.util.StringUtils._
import org.slf4j.LoggerFactory

class FormRunnerAuthFilter extends Filter {

  import FormRunnerAuthFilter.Logger._

  private case class FilterSettings(contentSecurityPolicy: Option[String])

  private var settingsOpt: Option[FilterSettings] = None

  def init(filterConfig: FilterConfig): Unit = {
    info("initializing")
    val settings = FilterSettings(Option(filterConfig.getInitParameter("content-security-policy")) flatMap (_.trimAllToOpt))
    info(s"configuring: $settings")
    settingsOpt = Some(settings)
  }

  def destroy(): Unit = {
    info(s"destroying")
    settingsOpt = None
  }

  def doFilter(req: ServletRequest, res: ServletResponse, chain: FilterChain): Unit = {

    // Set `Content-Security-Policy` response header if configured
    settingsOpt flatMap (_.contentSecurityPolicy) foreach { value ⇒
      res.asInstanceOf[HttpServletResponse].addHeader("Content-Security-Policy", value)
    }

    chain.doFilter(FormRunnerAuthFilter.amendRequest(req.asInstanceOf[HttpServletRequest]), res)
  }
}

object FormRunnerAuthFilter {

  import scala.collection.JavaConverters._

  val Logger = LoggerFactory.getLogger("org.orbeon.filter.form-runner-auth")

  def amendRequest(servletRequest: HttpServletRequest): HttpServletRequest = {

    val authHeaders = FormRunnerAuth.getCredentialsAsHeadersUseSession(
      userRoles = servletRequest,
      session   = servletRequest.getSession(true),
      getHeader = servletRequest.getHeaders(_).asScala.to[List]
    ).toMap

    trait CustomHeaders extends RequestRemoveHeaders with RequestPrependHeaders  {
      val headersToRemove  = FormRunnerAuth.AllHeaderNamesLower
      val headersToPrepend = authHeaders
    }

    new HttpServletRequestWrapper(servletRequest) with CustomHeaders
  }
}
