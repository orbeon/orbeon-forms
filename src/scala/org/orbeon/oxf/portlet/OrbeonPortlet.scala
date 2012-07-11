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

import collection.JavaConverters._
import org.orbeon.oxf.portlet.Portlet2ExternalContext.BufferedResponse
import org.orbeon.oxf.xforms.XFormsProperties
import OrbeonPortlet._
import BufferedPortlet._
import org.orbeon.oxf.common.Version
import org.orbeon.oxf.util.ScalaUtils._
import javax.portlet._
import org.orbeon.oxf.pipeline.api.{ExternalContext, PipelineContext}
import org.orbeon.oxf.webapp.{ProcessorService, ServletPortlet, WebAppContext}
import org.orbeon.oxf.externalcontext.{AsyncExternalContext, AsyncRequest}
import org.orbeon.oxf.util.{URLRewriterUtils, DynamicVariable}
import org.orbeon.oxf.pipeline.InitUtils.withPipelineContext

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
 * - implement async loading for processAction (fix AsyncRequest which doesn't handle the body)
 * - implement improved caching of page with replay of XForms events, see:
 *   http://wiki.orbeon.com/forms/projects/xforms-improved-portlet-support#TOC-XForms-aware-caching-of-portlet-out
 */
class OrbeonPortlet extends GenericPortlet with ServletPortlet with BufferedPortlet with AsyncPortlet {

    private def isAsyncPortletLoad = XFormsProperties.isAsyncPortletLoad
    private implicit val logger = ProcessorService.Logger

    // For BufferedPortlet
    def title(request: RenderRequest) = getTitle(request)
    def portletContext = getPortletContext

    // For ServletPortlet
    def logPrefix = "Portlet"

    // For AsyncPortlet
    def isMinimalResources = XFormsProperties.isMinimalResources
    def isWSRPEncodeResources = URLRewriterUtils.isWSRPEncodeResources
    case class AsyncContext(externalContext: ExternalContext, pipelineContext: Option[PipelineContext])

    // Immutable map of portlet parameters
    lazy val initParameters =
        getInitParameterNames.asScala map
            (n ⇒ n → getInitParameter(n)) toMap

    // Portlet init
    override def init(): Unit =
        withRootException("initialization", new PortletException(_)) {
            Version.instance.checkPEFeature("Orbeon Forms portlet") // this is a PE feature
            init(WebAppContext.instance(getPortletContext), Some("oxf.portlet-initialized-processor." → "oxf.portlet-initialized-processor.input."))
        }

    // Portlet destroy
    override def destroy(): Unit =
        withRootException("destruction", new PortletException(_)) {
            destroy(Some("oxf.portlet-destroyed-processor." → "oxf.portlet-destroyed-processor.input."))
        }

    // Portlet render
    override def render(request: RenderRequest, response: RenderResponse): Unit =
        currentPortlet.withValue(this) {
            withRootException("render", new PortletException(_)) {
                def renderFunction =
                    if (isAsyncPortletLoad)
                        startAsyncRender(request, asyncContext(request), callService)
                    else
                        callService(directContext(request))

                bufferedRender(request, response, renderFunction)
            }
        }

    // Portlet action
    override def processAction(request: ActionRequest, response: ActionResponse): Unit =
        currentPortlet.withValue(this) {
            withRootException("action", new PortletException(_)) {
                bufferedProcessAction(request, response, callService(directContext(request)))
            }
        }

    // Portlet resource
    override def serveResource(request: ResourceRequest, response: ResourceResponse) =
        currentPortlet.withValue(this) {
            withRootException("resource", new PortletException(_)) {
                if (isAsyncPortletLoad)
                    asyncServeResource(request, response, directServeResource(request, response))
                else
                    directServeResource(request, response)
            }
        }

    private def directContext(request: PortletRequest): AsyncContext = {
        val pipelineContext = new PipelineContext
        val externalContext = new Portlet2ExternalContext(pipelineContext, webAppContext, request, true)

        AsyncContext(externalContext, Some(pipelineContext))
    }

    private def asyncContext(request: PortletRequest): AsyncContext = {
        // Create a temporary Portlet2ExternalContext just so we can wrap its request into an AsyncRequest
        val asyncRequest =
            withPipelineContext { pipelineContext ⇒
                new AsyncRequest(new Portlet2ExternalContext(pipelineContext, webAppContext, request, true).getRequest)
            }

        // Prepare clean, async contexts
        val externalContext = new AsyncExternalContext(webAppContext, asyncRequest, new BufferedResponse(asyncRequest))

        AsyncContext(externalContext, None)
    }

    // Call the Orbeon Forms pipeline processor service
    private def callService(context: AsyncContext): ContentOrRedirect = {
        processorService.service(context.pipelineContext getOrElse new PipelineContext, context.externalContext)
        bufferedResponseToResponse(context.externalContext.getResponse.asInstanceOf[BufferedResponse])
    }

    private def directServeResource(request: ResourceRequest, response: ResourceResponse): Unit = {
        // Process request
        val pipelineContext = new PipelineContext
        val externalContext = new Portlet2ExternalContext(pipelineContext, webAppContext, request, true)
        processorService.service(pipelineContext, externalContext)

        // Write out the response
        val directResponse = externalContext.getResponse.asInstanceOf[BufferedResponse]
        Option(directResponse.getContentType) foreach response.setContentType
        write(response, getResponseData(directResponse), Option(directResponse.getContentType))
    }

    private def bufferedResponseToResponse(bufferedResponse: BufferedResponse): ContentOrRedirect =
        if (bufferedResponse.isRedirect)
            Redirect(bufferedResponse.getRedirectPathInfo, toScalaMap(bufferedResponse.getRedirectParameters), bufferedResponse.isRedirectIsExitPortal)
        else
            Content(getResponseData(bufferedResponse), Option(bufferedResponse.getContentType), Option(bufferedResponse.getTitle))

    private def getResponseData(response: BufferedResponse) =
        if (response.getStringBuilderWriter ne null)
            Left(response.getStringBuilderWriter.toString)
        else if (response.getByteStream ne null)
            Right(response.getByteStream.toByteArray)
        else
            throw new IllegalStateException("Processor execution did not return content.")
}

object OrbeonPortlet {
    // As of 2012-05-08, used only by LocalPortletSubmission to get access to ProcessorService
    val currentPortlet = new DynamicVariable[OrbeonPortlet]
}