/**
 * Copyright (C) 2010 Orbeon, Inc.
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

import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.pipeline.api._
import org.orbeon.oxf.processor.ServletFilterGenerator
import javax.servlet._
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import collection.JavaConverters._
import org.orbeon.oxf.webapp.{ServletPortletDefinitions, ProcessorService, WebAppContext}

// For backward compatibility
class OrbeonServletFilterDelegate extends OrbeonServletFilter

class OrbeonServletFilter extends Filter with ServletPortletDefinitions {

    def logPrefix = "Servlet filter"

    private var _processorService: ProcessorService = _

    // Web application context instance shared between all components of a web application
    private var _webAppContext: WebAppContext = _
    def webAppContext = _webAppContext

    // Immutable map of servlet parameters
    private var _initParameters: Map[String, String] = _
    def initParameters = _initParameters

    def init(config: FilterConfig): Unit =
        try {
            _webAppContext = WebAppContext.instance(config.getServletContext)

            _initParameters =
                config.getInitParameterNames.asScala.asInstanceOf[Iterator[String]] map
                    (n ⇒ n → config.getInitParameter(n)) toMap

             _processorService = getProcessorService
        } catch {
            case e: Exception ⇒ throw new ServletException(OXFException.getRootThrowable(e))
        }

    def doFilter(request: ServletRequest, response: ServletResponse, chain: FilterChain): Unit =
        try {
            val pipelineContext = new PipelineContext
            pipelineContext.setAttribute(ServletFilterGenerator.FILTER_CHAIN, chain)
            val externalContext = new ServletExternalContext(pipelineContext, webAppContext, request.asInstanceOf[HttpServletRequest], response.asInstanceOf[HttpServletResponse])
            _processorService.service(externalContext, pipelineContext)
        } catch {
            case e: Exception ⇒ throw new ServletException(OXFException.getRootThrowable(e))
        }

    def destroy(): Unit =
        try {
            _processorService.destroy()
            _processorService = null
            _webAppContext = null
        } catch {
            case e: Exception ⇒ throw new ServletException(OXFException.getRootThrowable(e))
        }
}