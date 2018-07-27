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

import java.{util ⇒ ju}
import javax.portlet._

import org.orbeon.oxf.externalcontext.WSRPURLRewriter.PathParameterName
import org.orbeon.oxf.fr.embedding.{APISupport, EmbeddingContext, EmbeddingContextWithResponse}
import org.orbeon.oxf.http._
import org.orbeon.oxf.portlet.BufferedPortlet._
import org.orbeon.oxf.portlet.liferay.LiferayURL
import org.orbeon.oxf.util.IOUtils._
import org.orbeon.oxf.util.NetUtils
import org.orbeon.oxf.util.PathUtils._

import scala.collection.JavaConverters._

class PortletEmbeddingContext(
  context            : PortletContext,
  request            : PortletRequest,
  response           : PortletResponse,
  val httpClient     : HttpClient,
  useShortNamespaces : Boolean
) extends EmbeddingContext {

  private val session = request.getPortletSession(true) ensuring (_ ne null)

  val namespace =
    if (useShortNamespaces)
      BufferedPortlet.shortIdNamespace(response.getNamespace, context) ensuring (_ ne null)
    else
      response.getNamespace

  def getSessionAttribute(name: String)                = Option(session.getAttribute(name))
  def setSessionAttribute(name: String, value: AnyRef) = session.setAttribute(name, value)
  def removeSessionAttribute(name: String)             = session.removeAttribute(name)

  val client = Headers.PortletClient
}

class PortletEmbeddingContextWithResponse(
  context            : PortletContext,
  request            : PortletRequest,
  response           : MimeResponse,
  httpClient         : HttpClient,
  useShortNamespaces : Boolean
) extends PortletEmbeddingContext(
  context,
  request,
  response,
  httpClient,
  useShortNamespaces
) with EmbeddingContextWithResponse {

  def writer                     = response.getWriter
  def outputStream               = response.getPortletOutputStream
  def decodeURL(encoded: String) = LiferayURL.wsrpToPortletURL(encoded, response)
  def setStatusCode(code: Int)   = () // Q: Can we do anything meaningful for resource caching?

  def setHeader(name: String, value: String): Unit =
    if (name equalsIgnoreCase Headers.ContentType)
      response.setContentType(value)
    else
      response.setProperty(name, value)
}

// Abstract portlet logic including buffering of portlet actions
// This doesn't deal direct with ProcessorService or HTTP proxying
trait BufferedPortlet {

  def findTitle(request: RenderRequest): Option[String]
  def portletContext: PortletContext

  // Immutable response with parameters
  case class ResponseWithParameters(response: BufferedContentOrRedirect, parameters: Map[String, List[String]])

  def bufferedRender(
    request  : RenderRequest,
    response : RenderResponse,
    render   : ⇒ StreamedContentOrRedirect)(implicit
    ctx      : EmbeddingContextWithResponse
  ): Unit =
    getStoredResponseWithParameters match {
      case Some(ResponseWithParameters(content: BufferedContent, parameters)) if toScalaMap(request.getParameterMap) == parameters ⇒
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
          case content: StreamedContent ⇒ useAndClose(content)(writeResponseWithParameters(request, response, _))
          case redirect: Redirect       ⇒ throw new IllegalStateException("Processor execution did not return content.")
        }
    }

  def bufferedProcessAction(
    request  : ActionRequest,
    response : ActionResponse,
    action   : ⇒ StreamedContentOrRedirect)(implicit
    ctx      : EmbeddingContext
  ): Unit = {
    // Make sure the previously cached output is cleared, if there is any. We keep the result of only one action.
    clearResponseWithParameters()

    action match {
      case Redirect(location, true) ⇒
        response.sendRedirect(location)
      case Redirect(location, false) ⇒
        // Just update the render parameters to simulate a redirect within the portlet
        val (path, queryOpt) = splitQuery(location)
        val parameters = queryOpt match {
          case Some(query) ⇒
            val m = NetUtils.decodeQueryString(query)
            m.put(PathParameter, Array(path))
            ju.Collections.unmodifiableMap[String, Array[String]](m)
          case None ⇒
            ju.Collections.singletonMap(PathParameter, Array(path))
        }

        // Set the new parameters for the subsequent render requests
        response.setRenderParameters(parameters)

      case content: StreamedContent ⇒
        // Content was written, keep it in the session for subsequent render requests with the current action parameters

        useAndClose(content) { _ ⇒

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
          storeResponseWithParameters(ResponseWithParameters(BufferedContent(content), toScalaMap(newRenderParameters)))
        }
    }
  }

  private def writeResponseWithParameters(
    request         : RenderRequest,
    response        : RenderResponse,
    responseContent : Content)(implicit
    ctx             : EmbeddingContextWithResponse
  ): Unit = {
    // Set title and content type
    responseContent.title orElse findTitle(request) foreach response.setTitle
    responseContent.contentType foreach response.setContentType

    // Write response out directly
    APISupport.writeResponseBody(responseContent)
  }

  protected def getStoredResponseWithParameters(implicit ctx: EmbeddingContext) =
    ctx.getSessionAttribute(ResponseSessionKey) map (_.asInstanceOf[ResponseWithParameters])

  private def storeResponseWithParameters(responseWithParameters: ResponseWithParameters)(implicit ctx: EmbeddingContext) =
    ctx.setSessionAttribute(ResponseSessionKey, responseWithParameters)

  private def clearResponseWithParameters()(implicit ctx: EmbeddingContext) =
    ctx.removeSessionAttribute(ResponseSessionKey)
}

object BufferedPortlet {

  val PathParameter      = PathParameterName
  val MethodParameter    = "orbeon.method"
  val ResponseSessionKey = "org.orbeon.oxf.response"

  // Convert to immutable String → List[String] so that map equality works as expected
  def toScalaMap(m: ju.Map[String, Array[String]]) =
    m.asScala map { case (k, v) ⇒ k → v.toList } toMap

  // Convert back an immutable String → List[String] to a Java String → Array[String] map
  def toJavaMap(m: Map[String, List[String]]) =
    m map { case (k, v) ⇒ k → v.toArray } asJava

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
      mappings.map.getOrElse(portletNamespace, {
        val newMappings = mappings.next(portletNamespace)
        portletContext.setAttribute(IdNamespacesSessionKey, newMappings)
        newMappings.map(portletNamespace)
      })
    }
}