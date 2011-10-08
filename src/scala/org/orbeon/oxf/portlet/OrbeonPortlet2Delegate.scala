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
import org.orbeon.oxf.common.OXFException
import collection.JavaConverters._
import java.util.{Map ⇒ JMap}
import org.orbeon.oxf.pipeline.api.PipelineContext
import org.orbeon.oxf.xml.XMLUtils
import org.orbeon.oxf.processor.serializer.CachedSerializer
import actors.Futures._
import actors.Future
import org.orbeon.oxf.portlet.Portlet2ExternalContext.BufferedResponse
import org.orbeon.oxf.properties.Properties
import org.orbeon.oxf.xforms.XFormsProperties
import org.orbeon.oxf.xforms.processor.{ResourcesAggregator, XFormsFeatures}
import collection.mutable.LinkedHashSet
import org.orbeon.oxf.externalcontext.{WSRPURLRewriter, AsyncRequest, AsyncExternalContext}
import org.orbeon.oxf.pipeline.api.ExternalContext.Response
import OrbeonPortlet2Delegate._
import org.orbeon.oxf.util.{DynamicVariable, URLRewriterUtils, NetUtils}

/**
 * Orbeon portlet.
 *
 * TODO: Support writing render/resource directly without buffering.
 */
class OrbeonPortlet2Delegate extends OrbeonPortlet2DelegateBase {

    private val ResponseSessionKey = "org.orbeon.oxf.response"
    private val FutureResponseSessionKey = "org.orbeon.oxf.future-response"
    private val IFrameContentResourceId = "orbeon-iframe-content"

    private def isAsyncPortletLoad = XFormsProperties.isAsyncPortletLoad
    private def isMinimal = XFormsProperties.isMinimalResources

    private def renderFunction = if (isAsyncPortletLoad) doRenderAsync(_: RenderRequest, _: RenderResponse, renderDiv _) else doRenderDirectly _
    private def serveContentFunction = if (isAsyncPortletLoad) Some(serveContentAsync _) else None

    // Portlet action
    override def processAction(request: ActionRequest, response: ActionResponse) =
        currentPortlet.withValue(this) {
            doProcessAction(request, response)
        }

    // Portlet render
    override def render(request: RenderRequest, response: RenderResponse) =
        currentPortlet.withValue(this) {
            renderFunction(request, response)
        }

    // Portlet resource
    override def serveResource(request: ResourceRequest, response: ResourceResponse) =
        currentPortlet.withValue(this) {
            doServeResource(request, response)
        }

    // Immutable response content which can safely be stored and passed around
    case class ResponseWithParameters(
        responseData: String Either Array[Byte],
        contentType: Option[String],
        title: Option[String],
        parameters: Map[String, List[String]]) {

        def this(response: BufferedResponse, parameters: Map[String, List[String]]) =
            this(getResponseData(response), Option(response.getContentType), Option(response.getTitle), parameters)
    }

    def tryStoringRenderResponse = Properties.instance.getPropertySet.getBoolean("test.store-render", false)

    private def doRenderDirectly(request: RenderRequest, response: RenderResponse): Unit =
        try {
            val responseWithParameters =
                getResponseWithParameters(request) match {
                    case Some(responseWithParameters) if toScalaMap(request.getParameterMap) == responseWithParameters.parameters ⇒
                        // The result of an action with the current parameters was a
                        // stream that we cached. Replay that stream and replace URLs.
                        // CHECK: what about mode / state? If they change, we ignore them totally.
                        responseWithParameters
                    case _ ⇒
                        val pipelineContext = new PipelineContext
                        val externalContext = new Portlet2ExternalContext(pipelineContext, getPortletContext, contextInitParameters.asJava, request, response)

                        // Run the service
                        processorService.service(externalContext, pipelineContext)

                        // NOTE: The response is also buffered, because our rewriting algorithm only operates on strings.
                        // This could be changed.
                        val actualResponse = externalContext.getResponse.asInstanceOf[BufferedResponse]
                        (getResponseData(actualResponse), Option(actualResponse.getContentType), Option(actualResponse.getTitle))

                        // Store response
                        val newResponseWithParameters = new ResponseWithParameters(actualResponse, toScalaMap(request.getParameterMap))
                        if (tryStoringRenderResponse)
                            setResponseWithParameters(request, newResponseWithParameters)
                        newResponseWithParameters
                }
            
            // Set title and content type
            responseWithParameters.title orElse Option(getTitle(request)) foreach (response.setTitle(_))
            responseWithParameters.contentType foreach (response.setContentType(_))

            // Write response out directly
            write(response, responseWithParameters.responseData, responseWithParameters.contentType)
        } catch {
            case e: Exception ⇒ throw new PortletException(OXFException.getRootThrowable(e))
        }

    private def renderDiv(request: RenderRequest, response: RenderResponse) {
        val pipelineContext = new PipelineContext
        try {
            // Create a request just so we can create a rewriter
            val ecRequest = new Portlet2ExternalContext(pipelineContext, getPortletContext, contextInitParameters.asJava, request, response).getRequest
            val rewriter = new WSRPURLRewriter(pipelineContext, ecRequest, URLRewriterUtils.isWSRPEncodeResources)

            // Output scripts needed by the Ajax portlet
            val writer = response.getWriter
            writer.write("""<script type="text/javascript" src="""")

            def rewrite(path: String) =
                rewriter.rewriteResourceURL(path, Response.REWRITE_MODE_ABSOLUTE_PATH) // NOTE: mode is ignored

            val resources = LinkedHashSet(XFormsFeatures.getAsyncPortletLoadScripts map (_.getResourcePath(isMinimal)): _*)
            ResourcesAggregator.aggregate(resources, false, path ⇒ WSRP2Utils.write(response, rewrite(path), shortIdNamespace(response, getPortletContext), false))

            writer.write(""""></script>""")

            // Output placeholder <div>
            val resourceURL = response.createResourceURL
            resourceURL.setResourceID(IFrameContentResourceId)
            resourceURL.setParameter("orbeon-time", System.currentTimeMillis.toString)
            writer.write("""<div class="orbeon-portlet-deferred" style="display: none">""" + resourceURL.toString + """</div>""")

        } finally
            pipelineContext.destroy(true)
    }

    private def doRenderAsync(request: RenderRequest, response: RenderResponse, renderHTML: (RenderRequest, RenderResponse) ⇒ Unit): Unit =
        try {
            // Call block
            val title = {
                // Schedule a future to provide new content

                // Make sure any content is removed, as serveContentAsync checks for this
                if (tryStoringRenderResponse)
                    clearResponseWithParameters(request)

                // Create a temporary Portlet2ExternalContext just so we can wrap its request into an AsyncRequest
                val asyncRequest = {
                    val pipelineContext = new PipelineContext
                    try new AsyncRequest(new Portlet2ExternalContext(pipelineContext, getPortletContext, contextInitParameters.asJava, request, response).getRequest)
                    finally pipelineContext.destroy(true)
                }

                val futureResponse =
                    future {
                        val newPipelineContext = new PipelineContext
                        val asyncExternalContext = new AsyncExternalContext(asyncRequest, new BufferedResponse(newPipelineContext, asyncRequest))
                        newPipelineContext.setAttribute(PipelineContext.EXTERNAL_CONTEXT, asyncExternalContext)

                        // Run the service
                        processorService.service(asyncExternalContext, newPipelineContext)

                        // Store response
                        val actualResponse = asyncExternalContext.getResponse.asInstanceOf[BufferedResponse]
                        new ResponseWithParameters(actualResponse, toScalaMap(request.getParameterMap))
                    }

                setFutureResponse(request, futureResponse)

                None // no title available at this point
            }

            title orElse Option(getTitle(request)) foreach (response.setTitle(_))
            response.setContentType("text/html")

            // Output the  HTML
            renderHTML(request, response)

        } catch {
            case e: Exception ⇒ throw new PortletException(OXFException.getRootThrowable(e))
        }

    private def writeResponseAsResource(responseWithParameters: ResponseWithParameters, request: ResourceRequest, response: ResourceResponse) = {
        response.setContentType("text/html")
        write(response, responseWithParameters.responseData, responseWithParameters.contentType)
    }

    private def serveContentAsync(request: ResourceRequest, response: ResourceResponse): Unit =
        getResponseWithParameters(request) match {
            case Some(responseWithParameters) if toScalaMap(request.getParameterMap) == responseWithParameters.parameters ⇒
                // Content for action response is already available
                writeResponseAsResource(responseWithParameters, request, response)
            case _ ⇒
                // Get content from future
                getFutureResponse(request) foreach { futureResponse ⇒
                    // Make sure the future is cleared
                    clearFutureResponse(request)
                    // Get or wait for response
                    val responseWithParameters = futureResponse()
                    // Store response
                    if (tryStoringRenderResponse)
                        setResponseWithParameters(request, responseWithParameters)
                    // Write it out
                    writeResponseAsResource(responseWithParameters, request, response)
                }
        }

    private def doServeResource(request: ResourceRequest, response: ResourceResponse): Unit =
        try {
            if (request.getResourceID == IFrameContentResourceId) {
                // Special case: serve content previously stored
                serveContentFunction foreach (_(request, response))
            } else {
                // Process request
                val pipelineContext = new PipelineContext
                val externalContext = new Portlet2ExternalContext(pipelineContext, getPortletContext, contextInitParameters.asJava, request, response)
                processorService.service(externalContext, pipelineContext)

                // Write out the response
                val directResponse = externalContext.getResponse.asInstanceOf[BufferedResponse]
                Option(directResponse.getContentType) foreach (response.setContentType(_))
                write(response, getResponseData(directResponse), Option(directResponse.getContentType))
            }
        } catch {
            case e: Exception ⇒ throw new PortletException(OXFException.getRootThrowable(e))
        }

    def doProcessAction(request: ActionRequest, response: ActionResponse): Unit =
        try {
            // Make sure the previously cached output is cleared, if there is any. We keep the result of only one action.
            clearResponseWithParameters(request)

            // Run the service
            val pipelineContext = new PipelineContext
            val externalContext = new Portlet2ExternalContext(pipelineContext, getPortletContext, contextInitParameters.asJava, request)

            // Run the service
            processorService.service(externalContext, pipelineContext)

            val bufferedResponse = externalContext.getResponse.asInstanceOf[BufferedResponse]
            // Check whether a redirect was issued, or some output was generated
            if (bufferedResponse.isRedirect) {
                if (bufferedResponse.isRedirectIsExitPortal) {
                    // Send a portlet response redirect
                    response.sendRedirect(NetUtils.pathInfoParametersToPathInfoQueryString(bufferedResponse.getRedirectPathInfo, bufferedResponse.getRedirectParameters))
                } else {
                    // Just update the render parameters to simulate a redirect within the portlet
                    val redirectParameters =
                        Option(bufferedResponse.getRedirectParameters) map
                            (_.asScala.toMap) getOrElse Map[String, Array[String]]() +
                                (OrbeonPortletXFormsFilter.PATH_PARAMETER_NAME → Array(bufferedResponse.getRedirectPathInfo))

                    // Set the new parameters for the subsequent render requests
                    response.setRenderParameters(redirectParameters.asJava)
                }
            } else if (bufferedResponse.hasContent) {
                // Content was written, keep it in the session for subsequent render requests with the current action parameters
                val updatedRenderParameters = request.getParameterMap.asScala.toMap + ("orbeon.method" → Array("post")) asJava
                
                response.setRenderParameters(updatedRenderParameters)

                // Store response
                setResponseWithParameters(request, new ResponseWithParameters(bufferedResponse, toScalaMap(updatedRenderParameters)))
            } else {
                // Nothing happened, throw an exception (or should we just ignore?)
                throw new IllegalStateException("Processor execution did not return content or issue a redirect.")
            }
        } catch {
            case e: Exception ⇒ throw new PortletException(OXFException.getRootThrowable(e))
        }

    private def write(response: MimeResponse, data: String Either Array[Byte], contentType: Option[String]): Unit =
        contentType match {
            case Some(contentType) if XMLUtils.isTextOrJSONContentType(contentType) || XMLUtils.isXMLMediatype(contentType) ⇒
                // Text/JSON/XML content type: rewrite response content
                data match {
                    case Left(string) ⇒
                        WSRP2Utils.write(response, string, shortIdNamespace(response, getPortletContext), XMLUtils.isXMLMediatype(contentType))
                    case Right(bytes) ⇒
                        val encoding = Option(NetUtils.getContentTypeCharset(contentType)) getOrElse CachedSerializer.DEFAULT_ENCODING
                        WSRP2Utils.write(response, new String(bytes, 0, bytes.length, encoding), shortIdNamespace(response, getPortletContext), XMLUtils.isXMLMediatype(contentType))
                }
            case _ ⇒
                // All other types: just output
                data match {
                    case Left(string) ⇒
                        response.getWriter.write(string)
                    case Right(bytes) ⇒
                        response.getPortletOutputStream.write(bytes)
                }
        }

    private def getResponseData(response: BufferedResponse) =
        if (response.getStringBuilderWriter ne null)
            Left(response.getStringBuilderWriter.toString)
        else if (response.getByteStream ne null)
            Right(response.getByteStream.toByteArray)
        else
            throw new IllegalStateException("Processor execution did not return content.")

    // Convert to immutable String → List[String] so that map equality works as expected
    def toScalaMap(m: JMap[String, Array[String]]) =
        m.asScala map { case (k, v) ⇒ k → v.toList } toMap

    private def getResponseWithParameters(request: PortletRequest) =
        Option(request.getPortletSession.getAttribute(ResponseSessionKey).asInstanceOf[ResponseWithParameters])

    private def setResponseWithParameters(request: PortletRequest, responseWithParameters: ResponseWithParameters) =
        request.getPortletSession.setAttribute(ResponseSessionKey, responseWithParameters)

    private def clearResponseWithParameters(request: PortletRequest) =
        request.getPortletSession.removeAttribute(ResponseSessionKey)

    private def getFutureResponse(request: PortletRequest) =
        Option(request.getPortletSession.getAttribute(FutureResponseSessionKey).asInstanceOf[Future[ResponseWithParameters]])

    private def setFutureResponse(request: PortletRequest, responseWithParameters: Future[ResponseWithParameters]) =
        request.getPortletSession.setAttribute(FutureResponseSessionKey, responseWithParameters)

    private def clearFutureResponse(request: PortletRequest) =
        request.getPortletSession.removeAttribute(FutureResponseSessionKey)
}

object OrbeonPortlet2Delegate {

    val currentPortlet = new DynamicVariable[OrbeonPortlet2Delegate]

    // Immutable portletNamespace → idNamespace information stored in the portlet context
    private object NamespaceMappings {
        private def newId(seq: Int) = "o" + seq
        def apply(portletNamespace: String): NamespaceMappings = NamespaceMappings(0, Map(portletNamespace → newId(0)))
    }

    private case class NamespaceMappings(private val last: Int, map: Map[String, String]) {
        def next(key: String) = NamespaceMappings(last + 1, map + (key → NamespaceMappings.newId(last + 1)))
    }

    // Return the short id namespace for this portlet. The idea of this is that portal-provided namespaces are large,
    // and since the XForms engine produces lots of ids, the DOM size increases a lot. All we want really are unique ids
    // in the DOM, so we make up our own short prefixes, hope they don't conflict within anything, and we map the portal
    // namespaces to our short ids.
    def shortIdNamespace(response: MimeResponse, portletContext: PortletContext) =
        // PLT.10.1: "There is one instance of the PortletContext interface associated with each portlet application
        // deployed into a portlet container." In order for multiple Orbeon portlets to not walk on each other, we
        // synchronize.
        portletContext.synchronized {

            val IdNamespacesSessionKey = "org.orbeon.oxf.id-namespaces"
            val portletNamespace = response.getNamespace

            // Get or create NamespaceMappings
            val mappings = Option(portletContext.getAttribute(IdNamespacesSessionKey).asInstanceOf[NamespaceMappings]) getOrElse {
                val newMappings = NamespaceMappings(portletNamespace)
                portletContext.setAttribute(IdNamespacesSessionKey, newMappings)
                newMappings
            }

            // Get or create specific mapping portletNamespace → idNamespace
            mappings.map.get(portletNamespace) getOrElse {
                val newMappings = mappings.next(portletNamespace)
                portletContext.setAttribute(IdNamespacesSessionKey, newMappings)
                newMappings.map(portletNamespace)
            }
        }
}