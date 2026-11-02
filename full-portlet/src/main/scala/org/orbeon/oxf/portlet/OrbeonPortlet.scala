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

import org.log4s.Logger
import org.orbeon.connection.StreamedContentT
import org.orbeon.oxf.common.Version
import org.orbeon.oxf.externalcontext.{ExternalContext, PortletWebAppContext}
import org.orbeon.oxf.fr.embedding.*
import org.orbeon.oxf.fr.embedding.APISupport.{Redirect, StreamedContentOrRedirect}
import org.orbeon.oxf.http.*
import org.orbeon.oxf.pipeline.InitUtils
import org.orbeon.oxf.pipeline.api.PipelineContext
import org.orbeon.oxf.portlet.Portlet2ExternalContext.BufferedResponseImpl
import org.orbeon.oxf.webapp.ServletPortlet.*
import org.orbeon.oxf.webapp.{ProcessorService, ServletPortlet}

import javax.portlet.{
  ActionRequest as JActionRequest,
  ActionResponse as JActionResponse,
  GenericPortlet as JGenericPortlet,
  PortletException,
  PortletRequest as JPortletRequest,
  RenderRequest as JRenderRequest,
  RenderResponse as JRenderResponse,
  ResourceRequest as JResourceRequest,
  ResourceResponse as JResourceResponse
}
import scala.jdk.CollectionConverters.*

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
class OrbeonPortlet extends JGenericPortlet with ServletPortlet with BufferedPortlet {

  private implicit val logger: Logger = ProcessorService.Logger

  // For BufferedPortlet
  def findTitle(request: RenderRequest) = Option(getTitle(request.getNativePortletRequest.asInstanceOf[JRenderRequest]))
  def portletContext = PortletContext(getPortletContext)

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
  override def render(request: JRenderRequest, response: JRenderResponse): Unit =
    ProcessorService.withProcessorService(processorService) {
      withRootException("render", new PortletException(_)) {
        implicit val ctx = new PortletEmbeddingContextWithResponse(portletContext, RenderRequest(request), RenderResponse(response), null)
        bufferedRender(RenderRequest(request), RenderResponse(response), callService(directContext(request)))
      }
    }

  // Portlet action
  override def processAction(request: JActionRequest, response: JActionResponse): Unit =
    ProcessorService.withProcessorService(processorService) {
      withRootException("action", new PortletException(_)) {
        implicit val ctx = new PortletEmbeddingContext(portletContext, ActionRequest(request), ActionResponse(response), null)
        bufferedProcessAction(ActionRequest(request), ActionResponse(response), callService(directContext(request)))
      }
    }

  // Portlet resource
  override def serveResource(request: JResourceRequest, response: JResourceResponse): Unit =
    ProcessorService.withProcessorService(processorService) {
      withRootException("resource", new PortletException(_)) {
        implicit val ctx = new PortletEmbeddingContextWithResponse(portletContext, ResourceRequest(request), ResourceResponse(response), null)
        directServeResource(request, response)
      }
    }

  private def directContext(request: JPortletRequest): AsyncContext = {
    val pipelineContext = new PipelineContext("OrbeonPortlet.directContext()")
    val externalContext = new Portlet2ExternalContext(pipelineContext, webAppContext, request, true)

    AsyncContext(externalContext, Some(pipelineContext))
  }

  // Call the Orbeon Forms pipeline processor service
  private def callService(context: AsyncContext): StreamedContentOrRedirect = {
    // withNewPipelineContext { pipelineContext =>
    processorService.service(context.pipelineContext.getOrElse(new PipelineContext("OrbeonPortlet.callService()")), context.externalContext)
    context.externalContext.getResponse.asInstanceOf[BufferedResponseImpl].responseContent
  }

  private def directServeResource(request: JResourceRequest, response: JResourceResponse)(implicit ctx: EmbeddingContextWithResponse): Unit = {
    InitUtils.withNewPipelineContext("OrbeonPortlet.directServeResource()") { pipelineContext =>

      val externalContext = new Portlet2ExternalContext(pipelineContext, webAppContext, request, true)

      // Process request
      processorService.service(
        pipelineContext,
        externalContext
      )

      // Write out the response
      externalContext.getResponse.responseContent match {
        case Right(_: Redirect) =>
          throw new NotImplementedError("redirect not supported when serving resource")
        case Left(s @ StreamedContentT(_, contentType, _, _)) =>
          contentType foreach response.setContentType
          APISupport.writeResponseBody(APISupport.mustRewriteForMediatype)(s)
      }
    }
  }
}
