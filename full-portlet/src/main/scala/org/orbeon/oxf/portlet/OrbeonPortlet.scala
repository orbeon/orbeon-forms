/**
 * Copyright (C) 2011 Orbeon, Inc.
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
package org.orbeon.oxf.portlet

import javax.portlet._
import org.orbeon.oxf.common.Version
import org.orbeon.oxf.externalcontext.{ExternalContext, PortletWebAppContext}
import org.orbeon.oxf.fr.embedding._
import org.orbeon.oxf.http._
import org.orbeon.oxf.pipeline.api.PipelineContext
import org.orbeon.oxf.portlet.Portlet2ExternalContext.BufferedResponseImpl
import org.orbeon.oxf.webapp.ServletPortlet._
import org.orbeon.oxf.webapp.{ProcessorService, ServletPortlet}

import scala.jdk.CollectionConverters._

// For backward compatibility
class OrbeonPortlet2         extends OrbeonPortlet
class OrbeonPortletDelegate  extends OrbeonPortlet
class OrbeonPortlet2Delegate extends OrbeonPortlet

/**
 * This is the Portlet (JSR-286) entry point of Orbeon.
 *
 * Several servlets and portlets can be used in the same web application. They all share the same context initialization
 * parameters, but each servlet and portlet can be configured with its own main processor and inputs.
 *
 * All servlets and portlets instances in a given web app share the same resource manager.
 *
 * Ideas for improvements:
 *
 * - support writing render/resource directly without buffering when possible (not action or async load)
 * - warning message if user is re-rendering a page which is the result of an action
 * - implement improved caching of page with replay of XForms events, see:
 *   http://wiki.orbeon.com/forms/projects/xforms-improved-portlet-support#TOC-XForms-aware-caching-of-portlet-out
 */
class OrbeonPortlet extends GenericPortlet with ServletPortlet with BufferedPortlet {

  private implicit val logger = ProcessorService.Logger

  // For BufferedPortlet
  def findTitle(request: RenderRequest) = Option(getTitle(request))
  def portletContext = getPortletContext

  // For ServletPortlet
  def logPrefix = "Portlet"

  // Immutable map of portlet parameters
  lazy val initParameters =
    getInitParameterNames.asScala map
      (n => n -> getInitParameter(n)) toMap

  case class AsyncContext(externalContext: ExternalContext, pipelineContext: Option[PipelineContext])

  // Portlet init
  override def init(): Unit =
    withRootException("initialization", new PortletException(_)) {
      // Obtain WebAppContext as that will initialize the resource manager if needed, which is required by
      // Version
      val webAppContext = PortletWebAppContext(getPortletContext)
      Version.instance.requirePEFeature("Orbeon Forms portlet")
      init(webAppContext, Some("oxf.portlet-initialized-processor." -> "oxf.portlet-initialized-processor.input."))

      webAppContext.addListener(() => PropertiesApacheHttpClient.shutdown())
    }

  // Portlet destroy
  override def destroy(): Unit =
    withRootException("destruction", new PortletException(_)) {
      destroy(Some("oxf.portlet-destroyed-processor." -> "oxf.portlet-destroyed-processor.input."))
    }

  // Portlet render
  override def render(request: RenderRequest, response: RenderResponse): Unit =
    ProcessorService.withProcessorService(processorService) {
      withRootException("render", new PortletException(_)) {
        implicit val ctx = new PortletEmbeddingContextWithResponse(getPortletContext, request, response, null)
        bufferedRender(request, response, callService(directContext(request)))
      }
    }

  // Portlet action
  override def processAction(request: ActionRequest, response: ActionResponse): Unit =
    ProcessorService.withProcessorService(processorService) {
      withRootException("action", new PortletException(_)) {
        implicit val ctx = new PortletEmbeddingContext(getPortletContext, request, response, null)
        bufferedProcessAction(request, response, callService(directContext(request)))
      }
    }

  // Portlet resource
  override def serveResource(request: ResourceRequest, response: ResourceResponse): Unit =
    ProcessorService.withProcessorService(processorService) {
      withRootException("resource", new PortletException(_)) {
        implicit val ctx = new PortletEmbeddingContextWithResponse(getPortletContext, request, response, null)
        directServeResource(request, response)
      }
    }

  private def directContext(request: PortletRequest): AsyncContext = {
    val pipelineContext = new PipelineContext
    val externalContext = new Portlet2ExternalContext(pipelineContext, webAppContext, request, true)

    AsyncContext(externalContext, Some(pipelineContext))
  }

  // Call the Orbeon Forms pipeline processor service
  private def callService(context: AsyncContext): StreamedContentOrRedirect = {
    processorService.service(context.pipelineContext getOrElse new PipelineContext, context.externalContext)
    context.externalContext.getResponse.asInstanceOf[BufferedResponseImpl].responseContent
  }

  private def directServeResource(request: ResourceRequest, response: ResourceResponse)(implicit ctx: EmbeddingContextWithResponse): Unit = {
    // Process request
    val pipelineContext = new PipelineContext
    val externalContext = new Portlet2ExternalContext(pipelineContext, webAppContext, request, true)
    processorService.service(pipelineContext, externalContext)

    // Write out the response
    externalContext.getResponse.responseContent match {
      case _: Redirect =>
        throw new NotImplementedError("redirect not supported when serving resource")
      case s @ StreamedContent(_, contentType, _, _) =>
        contentType foreach response.setContentType
        APISupport.writeResponseBody(APISupport.mustRewriteForMediatype)(s)
    }
  }
}
