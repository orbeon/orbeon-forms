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

import com.typesafe.scalalogging.Logger
import javax.servlet._
import javax.servlet.http.{HttpServletRequest, HttpServletRequestWrapper, HttpServletResponse}
import org.orbeon.oxf.fr.FormRunnerAuth
import org.orbeon.oxf.util.StringUtils._

import scala.collection.compat._

class FormRunnerAuthFilter extends Filter {

  import FormRunnerAuthFilter._

  private case class FilterSettings(contentSecurityPolicy: Option[String])

  private var settingsOpt: Option[FilterSettings] = None

  override def init(filterConfig: FilterConfig): Unit = {
    logger.info("initializing")
    val settings = FilterSettings(Option(filterConfig.getInitParameter("content-security-policy")) flatMap (_.trimAllToOpt))
    logger.info(s"configuring: $settings")
    settingsOpt = Some(settings)
  }

  override def destroy(): Unit = {
    logger.info(s"destroying")
    settingsOpt = None
  }

  def doFilter(req: ServletRequest, res: ServletResponse, chain: FilterChain): Unit = {

    // Set `Content-Security-Policy` response header if configured
    settingsOpt flatMap (_.contentSecurityPolicy) foreach { value =>
      res.asInstanceOf[HttpServletResponse].setHeader("Content-Security-Policy", value)
    }

    chain.doFilter(amendRequest(req.asInstanceOf[HttpServletRequest]), res)
  }
}

object FormRunnerAuthFilter {

  import scala.collection.JavaConverters._

  private val logger = Logger("org.orbeon.filter.form-runner-auth")

  def amendRequest(servletRequest: HttpServletRequest): HttpServletRequest = {

    val authHeaders = FormRunnerAuth.getCredentialsAsHeadersUseSession(
      userRoles = servletRequest,
      session   = new ServletSessionImpl(servletRequest.getSession(true)),
      getHeader = servletRequest.getHeaders(_).asScala.to(List)
    ).toMap

    trait CustomHeaders extends RequestRemoveHeaders with RequestPrependHeaders  {
      val headersToRemove  = FormRunnerAuth.AllHeaderNamesLower
      val headersToPrepend = authHeaders
    }

    def headersAsString(r: HttpServletRequest) = {
      r.getHeaderNames.asScala flatMap { name =>
        r.getHeaders(name).asScala map { value =>
          s"$name: $value"
        }
      } mkString "\n"
    }

    logger.debug("incoming headers:\n" + headersAsString(servletRequest))
    val amendedHeaders = new HttpServletRequestWrapper(servletRequest) with CustomHeaders
    logger.debug("amended headers:\n" + headersAsString(amendedHeaders))
    amendedHeaders
  }
}
