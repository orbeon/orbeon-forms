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
import javax.servlet.ServletException
import javax.servlet.http._
import collection.JavaConverters._
import org.orbeon.oxf.webapp.{ServletPortletDefinitions, ProcessorService, WebAppContext}
import org.orbeon.oxf.webapp.ProcessorService._

// For backward compatibility
class OrbeonServletDelegate extends OrbeonServlet

/**
 * This is the Servlet entry point of Orbeon.
 *
 * Several servlets and portlets can be used in the same web application. They all share the same context initialization
 * parameters, but each servlet and portlet can be configured with its own main processor and inputs.
 *
 * All servlets and portlets instances in a given web app share the same resource manager.
 */
class OrbeonServlet extends HttpServlet with ServletPortletDefinitions {

    private val DefaultMethods = "get post head"

    private val InitProcessorPrefix     = "oxf.servlet-initialized-processor."
    private val InitInputPrefix         = "oxf.servlet-initialized-processor.input."
    private val DestroyProcessorPrefix  = "oxf.servlet-destroyed-processor."
    private val DestroyInputPrefix      = "oxf.servlet-destroyed-processor.input."

    private val HttpAcceptMethodsParam = "oxf.http.accept-methods"

    def logPrefix = "Servlet"

    private var _processorService: ProcessorService = _

    // Web application context instance shared between all components of a web application
    private var _webAppContext: WebAppContext = _
    def webAppContext = _webAppContext

    // Immutable map of servlet parameters
    lazy val initParameters =
        getInitParameterNames.asScala.asInstanceOf[Iterator[String]] map
            (n ⇒ n → getInitParameter(n)) toMap

    // Accepted methods for this servlet
    private lazy val acceptedMethods =
        initParameters.getOrElse(HttpAcceptMethodsParam, DefaultMethods) split """[\s,]+""" filter (_.nonEmpty) toSet

    override def init(): Unit =
        try {
            _webAppContext = WebAppContext.instance(getServletContext)
            _processorService = getProcessorService

            // Run listeners
            runListenerProcessor(InitProcessorPrefix, InitInputPrefix)
            logger.info(logPrefix + " - " + "Servlet initialized.")
        } catch {
            case e: Exception ⇒ throw new ServletException(OXFException.getRootThrowable(e))
        }

    override def service(request: HttpServletRequest, response: HttpServletResponse): Unit =
        try {
            val httpMethod = request.getMethod
            if (! acceptedMethods(httpMethod.toLowerCase))
                throw new OXFException("HTTP method not accepted: " + httpMethod + ". You can configure methods in your web.xml using the parameter: " + HttpAcceptMethodsParam)

            val pipelineContext = new PipelineContext
            val externalContext = new ServletExternalContext(pipelineContext, _webAppContext, request, response)
            _processorService.service(externalContext, pipelineContext)
        } catch {
            case e: Exception ⇒ throw new ServletException(OXFException.getRootThrowable(e))
        }

     override def destroy(): Unit =
         try {
            // Run listeners
            logger.info(logPrefix + " - " + "Servlet destroyed.")
            runListenerProcessor(DestroyProcessorPrefix, DestroyInputPrefix)

             // Clean-up
            _processorService.destroy()
            _processorService = null
            _webAppContext = null
        } catch {
            case e: Exception ⇒ throw new ServletException(OXFException.getRootThrowable(e))
        }
}