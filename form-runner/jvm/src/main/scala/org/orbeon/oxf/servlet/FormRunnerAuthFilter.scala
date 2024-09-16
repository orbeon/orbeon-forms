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

import org.apache.logging.log4j.ThreadContext
import org.orbeon.oxf.externalcontext.ServletPortletRequest
import org.orbeon.oxf.fr.FormRunnerAuth
import org.orbeon.oxf.http.Headers
import org.orbeon.oxf.properties.Properties
import org.orbeon.oxf.util.StringUtils.*
import org.slf4j.LoggerFactory

import scala.jdk.CollectionConverters.*

// For backward compatibility
class FormRunnerAuthFilter extends JavaxFormRunnerAuthFilter

class JavaxFormRunnerAuthFilter   extends JavaxFilter  (new FormRunnerAuthFilterImpl)
class JakartaFormRunnerAuthFilter extends JakartaFilter(new FormRunnerAuthFilterImpl)

class FormRunnerAuthFilterImpl extends Filter {

  import FormRunnerAuthFilterImpl._

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

    val addHttpHeadersToThreadContext =
      Properties.instance.getPropertySet.getBoolean("oxf.log4j.thread-context.http-headers", default = false)
    if (addHttpHeadersToThreadContext)
      req.asInstanceOf[HttpServletRequest]
        .headerNamesWithValues
        .foreach { case (name, values) =>
          ThreadContext.put(s"orbeon-incoming-http-header-${name.toLowerCase}", values.head)
        }

    chain.doFilter(amendRequest(req.asInstanceOf[HttpServletRequest]), res)
  }
}

object FormRunnerAuthFilterImpl {

  private val logger = LoggerFactory.getLogger("org.orbeon.filter.form-runner-auth")

  def amendRequest(servletRequest: HttpServletRequest): HttpServletRequest = {

    // Do not create a session for fonts, source maps, or OPTIONS requests
    val createSession  = ! (servletRequest.isFont || servletRequest.isSourceMap || servletRequest.isOptions)
    val httpSession    = new ServletSessionImpl(servletRequest.getSession(createSession))
    val getHttpHeaders = (name: String) => servletRequest.headerValuesList(name)

    logger.debug(s"incoming headers:\n${servletRequest.headersAsString}")

    // The Form Runner service path is hardcoded but that's ok. When we are filtering a service, we don't retrieve the
    // credentials, which would be provided by the container or by incoming headers. Instead, credentials are provided
    // directly with `Orbeon-*` headers. See https://github.com/orbeon/orbeon-forms/issues/2275
    val requestWithAmendedHeaders =
      if (servletRequest.getRequestPathInfo.startsWith("/fr/service/")) {

        // `ServletPortletRequest` gets credentials from the session, which means we need to store the credentials into
        // the session. This is done by `getCredentialsAsHeadersUseSession()` if we are not a service, but here we
        // don't use that function so we need to do make sure they are stored.

        ServletPortletRequest.findCredentialsInSession(httpSession) match {
          case None =>
            ServletPortletRequest.storeCredentialsInSession(
              httpSession,
              FormRunnerAuth.fromHeaderValues(
                credentialsOpt = servletRequest.headerFirstValueOpt(Headers.OrbeonCredentials),
                usernameOpt    = servletRequest.headerFirstValueOpt(Headers.OrbeonUsername),
                rolesList      = getHttpHeaders(Headers.OrbeonRoles),
                groupOpt       = servletRequest.headerFirstValueOpt(Headers.OrbeonGroup),
              )
            )
          case Some(_) =>
        }

        servletRequest
      } else if (servletRequest.getRequestPathInfo.endsWith(".map")) {
        // Don't amend headers for `.map` as this would cause the credentials code to clear the credentials
        // unnecessarily. https://github.com/orbeon/orbeon-forms/issues/6080
        servletRequest
      } else {

        trait CustomHeaders extends RequestRemoveHeaders with RequestPrependHeaders  {
          override def headersToRemoveAsSet: Set[String] = FormRunnerAuth.AllAuthHeaderNames
          val headersToPrependAsMap = FormRunnerAuth.getCredentialsAsHeadersUseSession(
            userRoles = servletRequest,
            session   = httpSession,
            getHeader = getHttpHeaders
          ).toMap
        }

        new HttpServletRequestWrapper(servletRequest) with CustomHeaders
      }

    logger.debug(s"amended headers:\n${requestWithAmendedHeaders.headersAsString}")

    requestWithAmendedHeaders
  }
}
