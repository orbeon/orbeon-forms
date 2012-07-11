/**
 * Copyright (C) 2012 Orbeon, Inc.
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

import org.orbeon.oxf.util.NetUtils
import scala.Array
import BufferedPortlet._
import javax.portlet._
import java.util.{Map ⇒ JMap}
import collection.JavaConverters._
import org.orbeon.oxf.xml.XMLUtils
import org.orbeon.oxf.processor.serializer.CachedSerializer
import org.orbeon.oxf.externalcontext.WSRPURLRewriter.PathParameterName

// Abstract portlet logic including buffering of portlet actions
// This doesn't deal direct with ProcessorService or HTTP proxying
trait BufferedPortlet {

    def title(request: RenderRequest): String
    def portletContext: PortletContext

    // Immutable responses
    sealed trait ContentOrRedirect
    case class Content(body: String Either Array[Byte], contentType: Option[String], title: Option[String]) extends ContentOrRedirect
    case class Redirect(path: String, parameters: Map[String, List[String]], exitPortal: Boolean = false) extends ContentOrRedirect

    // Immutable response with parameters
    case class ResponseWithParameters(response: ContentOrRedirect, parameters: Map[String, List[String]])

    def bufferedRender(request: RenderRequest, response: RenderResponse, render: ⇒ ContentOrRedirect): Unit =
        getResponseWithParameters(request) match {
            case Some(ResponseWithParameters(content: Content, parameters)) if toScalaMap(request.getParameterMap) == parameters ⇒
                // The result of an action with the current parameters is available
                // NOTE: Until we can correctly handle multiple render requests for an XForms page, we should detect the
                // situation where a second render request tries to load a deferred action response, and display an
                // error message.
                writeResponseWithParameters(request, response, content)
            case _ ⇒
                // No matching action result, call the render function
                // NOTE: The Portlet API does not support sendRedirect() and setRenderParameters() upon render(). This
                // means we cannot easily simulate redirects upon render. For internal redirects, we could maybe
                // implement the redirect loop here. The issue would be what happens upon subsequent renders, as they
                // would again request the first path, not the redirected path. For now we throw.
                render match {
                    case content: Content ⇒ writeResponseWithParameters(request, response, content)
                    case _ ⇒ throw new IllegalStateException("Processor execution did not return content.")
                }
        }

    def bufferedProcessAction(request: ActionRequest, response: ActionResponse, action: ⇒ ContentOrRedirect): Unit = {
        // Make sure the previously cached output is cleared, if there is any. We keep the result of only one action.
        clearResponseWithParameters(request)

        action match {
            case Redirect(path, parameters, true) ⇒
                response.sendRedirect(NetUtils.pathInfoParametersToPathInfoQueryString(path, parameters.asJava))
            case Redirect(path, parameters, false) ⇒
                // Just update the render parameters to simulate a redirect within the portlet
                val redirectParameters = parameters + (PathParameter → List(path))

                // Set the new parameters for the subsequent render requests
                response.setRenderParameters(toJavaMap(redirectParameters))

            case content: Content ⇒
                // Content was written, keep it in the session for subsequent render requests with the current action parameters

                // NOTE: Don't use the action parameters, as in the case of a form POST there can be dozens of those
                // or more, and anyway those don't make sense as subsequent render parameters. Instead, we just use
                // the path and a method indicator. Later we should either indicate an error, or handle XForms Ajax
                // updates properly.
                val newRenderParameters = Map(
                    PathParameter   → Array(request.getParameter(PathParameter)),
                    MethodParameter → Array("post")
                ).asJava

                response.setRenderParameters(newRenderParameters)

                // Store response
                setResponseWithParameters(request, ResponseWithParameters(content, toScalaMap(newRenderParameters)))
        }
    }

    private def writeResponseWithParameters(request: RenderRequest, response: RenderResponse, contentResponse: Content) {
        // Set title and content type
        contentResponse.title orElse Option(title(request)) foreach response.setTitle
        contentResponse.contentType foreach response.setContentType

        // Write response out directly
        write(response, contentResponse.body, contentResponse.contentType)
    }

    protected def write(response: MimeResponse, data: String Either Array[Byte], contentType: Option[String]): Unit =
        contentType map NetUtils.getContentTypeMediaType match {
            case Some(mediatype) if XMLUtils.isTextOrJSONContentType(mediatype) || XMLUtils.isXMLMediatype(mediatype) ⇒
                // Text/JSON/XML content type: rewrite response content
                data match {
                    case Left(string) ⇒
                        WSRP2Utils.write(response, string, shortIdNamespace(response.getNamespace, portletContext), XMLUtils.isXMLMediatype(mediatype))
                    case Right(bytes) ⇒
                        val encoding = contentType flatMap (ct ⇒ Option(NetUtils.getContentTypeCharset(ct))) getOrElse CachedSerializer.DEFAULT_ENCODING
                        WSRP2Utils.write(response, new String(bytes, 0, bytes.length, encoding), shortIdNamespace(response.getNamespace, portletContext), XMLUtils.isXMLMediatype(mediatype))
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

    protected def getResponseWithParameters(request: PortletRequest) =
        Option(request.getPortletSession.getAttribute(ResponseSessionKey).asInstanceOf[ResponseWithParameters])

    private def setResponseWithParameters(request: PortletRequest, responseWithParameters: ResponseWithParameters) =
        request.getPortletSession.setAttribute(ResponseSessionKey, responseWithParameters)

    private def clearResponseWithParameters(request: PortletRequest) =
        request.getPortletSession.removeAttribute(ResponseSessionKey)
}

object BufferedPortlet {

    val PathParameter = PathParameterName
    val MethodParameter = "orbeon.method"

    val ResponseSessionKey = "org.orbeon.oxf.response"

    // Convert to immutable String → List[String] so that map equality works as expected
    def toScalaMap(m: JMap[String, Array[String]]) =
        m.asScala mapValues (_.toList) toMap

    // Convert back an immutable String → List[String] to a Java String → Array[String] map
    def toJavaMap(m: Map[String, List[String]]) =
        m mapValues (_.toArray) asJava

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
    def shortIdNamespace(portletNamespace: String, portletContext: PortletContext) =
        // PLT.10.1: "There is one instance of the PortletContext interface associated with each portlet application
        // deployed into a portlet container." In order for multiple Orbeon portlets to not walk on each other, we
        // synchronize.
        portletContext.synchronized {

            val IdNamespacesSessionKey = "org.orbeon.oxf.id-namespaces"

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