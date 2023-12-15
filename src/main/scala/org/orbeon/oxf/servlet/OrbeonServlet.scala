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

import cats.effect.unsafe.IORuntime
import org.orbeon.oxf.externalcontext.ServletWebAppContext
import org.orbeon.oxf.http.{PropertiesApacheHttpClient, StatusCode}
import org.orbeon.oxf.pipeline.api._
import org.orbeon.oxf.webapp.ServletPortlet._
import org.orbeon.oxf.webapp.{ProcessorService, ServletPortlet}

import scala.jdk.CollectionConverters._

// For backward compatibility
class OrbeonServletDelegate extends JavaxOrbeonServlet

/**
 * This is the Servlet entry point of Orbeon.
 *
 * Several servlets and portlets can be used in the same web application. They all share the same context initialization
 * parameters, but each servlet and portlet can be configured with its own main processor and inputs.
 *
 * All servlets and portlets instances in a given web app share the same resource manager.
 */

class JavaxOrbeonServlet extends JavaxHttpServlet {
  override val httpServlet: HttpServlet = new OrbeonServletImpl(this) with WithJavaxServletException
}
class JakartaOrbeonServlet extends JakartaHttpServlet {
  override val httpServlet: HttpServlet = new OrbeonServletImpl(this) with WithJakartaServletException
}

abstract class OrbeonServletImpl(servletContextProvider: ServletContextProvider) extends HttpServlet with ServletPortlet with WithServletException {

  def newServletException(throwable: Throwable): Exception

  private implicit val logger = ProcessorService.Logger

  val HttpAcceptMethodsParam = "http.accept-methods"
  val DefaultMethods = "get post head"

  // Accepted methods for this servlet
  private lazy val acceptedMethods =
    initParameters.getOrElse(HttpAcceptMethodsParam, DefaultMethods) split """[\s,]+""" filter (_.nonEmpty) toSet

  def logPrefix = "Servlet"

  // Immutable map of servlet parameters
  lazy val initParameters =
    servletContextProvider.getInitParameterNames.asScala map
      (n => n -> servletContextProvider.getInitParameter(n)) toMap

  // Servlet init
  override def init(): Unit =
    withRootException("initialization", newServletException) {
      init(ServletWebAppContext(servletContextProvider.getOrbeonServletContext), Some("servlet-initialized-processor." -> "servlet-initialized-processor.input."))

      // Unclear whether there is a better place to do this
      webAppContext.addListener(() => {
        PropertiesApacheHttpClient.shutdown()
        cats.effect.unsafe.implicits.global.shutdown()
      })
    }

  // Servlet destroy
  override def destroy(): Unit =
    withRootException("destruction", newServletException) {
      destroy(Some("servlet-destroyed-processor." -> "servlet-destroyed-processor.input."))
    }

  // Servlet request
  override def service(request: HttpServletRequest, response: HttpServletResponse): Unit =
    ProcessorService.withProcessorService(processorService) {
      withRootException("request", newServletException) {
        val httpMethod = request.getMethod

        if (! acceptedMethods(httpMethod.toLowerCase)) {
          logger.info("HTTP method not accepted: " + httpMethod + ". You can configure methods in your web.xml using the parameter: " + HttpAcceptMethodsParam)
          response.setStatus(StatusCode.MethodNotAllowed)
        } else {
          val pipelineContext = new PipelineContext
          val externalContext = new ServletExternalContext(pipelineContext, webAppContext, request, response)
          processorService.service(pipelineContext, externalContext)
        }
      }
    }
}
