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

import org.apache.commons.io.IOUtils
import org.orbeon.connection.*
import org.orbeon.io.IOUtils.*
import org.orbeon.oxf.fr.embedding.APISupport.{BufferedContentOrRedirect, Redirect, StreamedContentOrRedirect}
import org.orbeon.oxf.fr.embedding.{APISupport, EmbeddingContext, EmbeddingContextWithResponse}
import org.orbeon.oxf.http.*
import org.orbeon.oxf.portlet.BufferedPortlet.*
import org.orbeon.oxf.portlet.liferay.LiferayURL
import org.orbeon.oxf.util.PathUtils.*
import org.orbeon.oxf.util.*
import org.orbeon.wsrp.WSRPSupport.PathParameterName

import java.io.{OutputStream, PrintWriter, Writer}
import java.util as ju
import javax.portlet.*
import scala.jdk.CollectionConverters.*
import scala.util.{Failure, Success, Try}


class PortletEmbeddingContext(
  context            : PortletContext,
  request            : PortletRequest,
  response           : PortletResponse,
  val httpClient     : HttpClient[org.apache.http.client.CookieStore]
) extends EmbeddingContext {

  private val session = request.getPortletSession(true) ensuring (_ ne null)

  val namespace: String = response.getNamespace

  def getSessionAttribute   (name: String)               : Option[AnyRef] = Option(session.getAttribute(name))
  def setSessionAttribute   (name: String, value: AnyRef): Unit           = session.setAttribute(name, value)
  def removeSessionAttribute(name: String)               : Unit           = session.removeAttribute(name)

  val client: String = Headers.PortletEmbeddingClient
}

class PortletEmbeddingContextWithResponse(
  context            : PortletContext,
  request            : PortletRequest,
  response           : MimeResponse,
  httpClient         : HttpClient[org.apache.http.client.CookieStore]
) extends PortletEmbeddingContext(
  context,
  request,
  response,
  httpClient
) with EmbeddingContextWithResponse {

  def output: Either[Writer, OutputStream] =
    Try(response.getPortletOutputStream) match {
      case Success(outputStream) => Right(outputStream)
      case Failure(_)            => Left(response.getWriter)
    }

  def decodeURL(encoded: String): String            = LiferayURL.wsrpToPortletURL(encoded, response)
  def setStatusCode(code: Int)  : Unit              = () // Q: Can we do anything meaningful for resource caching?

  def setHeader(name: String, value: String): Unit =
    if (name equalsIgnoreCase Headers.ContentType)
      response.setContentType(value)
    else
      response.setProperty(name, value)
}

// Abstract portlet logic including buffering of portlet actions
// This doesn't deal directly with HTTP proxying
trait BufferedPortlet {

  import StoredResponse._

  def findTitle(request: RenderRequest): Option[String]
  def portletContext: PortletContext

  def bufferedRender(
    request  : RenderRequest,
    response : RenderResponse,
    render   : => StreamedContentOrRedirect)(implicit
    ctx      : EmbeddingContextWithResponse
  ): Unit =
    findStoredResponseWithParameters match {

      case Some(ResponseWithParams(Left(content), params)) if matchesStoredResponse(request.getRenderParameters, params) =>
        // The result of an action with the current parameters is available
        // NOTE: Until we can correctly handle multiple render requests for an XForms page, we should detect the
        // situation where a second render request tries to load a deferred action response, and display an
        // error message.
        writeResponseWithParameters(request, response, content)
      case _ =>
        // No matching action result, call the render function
        // NOTE: The Portlet API does not support `sendRedirect()` and `setRenderParameters()` upon `render()`. This
        // means we cannot easily simulate redirects upon render. For internal redirects, we could maybe
        // implement the redirect loop here. The issue would be what happens upon subsequent renders, as they
        // would again request the first path, not the redirected path. For now we throw.
        render match {
          case Left(content: StreamedContent) => useAndClose(content)(writeResponseWithParameters(request, response, _))
          case Right(_: Redirect)             => throw new IllegalStateException("Processor execution did not return content.")
        }
    }

  def bufferedProcessAction(
    request  : ActionRequest,
    response : ActionResponse,
    action   : => StreamedContentOrRedirect)(implicit
    ctx      : EmbeddingContext
  ): Unit = {
    // Make sure the previously cached output is cleared, if there is any. We keep the result of only one action.
    clearResponseWithParameters()

    action match {
      case Right(Redirect(location, true)) =>
        response.sendRedirect(location)
      case Right(Redirect(location, false)) =>
        // Just update the render parameters to simulate a redirect within the portlet
        val (path, queryOpt) = splitQuery(location)
        val parameters = queryOpt match {
          case Some(query) =>
            (decodeQueryString(query) + (PathParameter -> Array(path))).asJava
          case None =>
            ju.Collections.singletonMap(PathParameter, Array(path))
        }

        // Set the new parameters for the subsequent render requests
        response.setRenderParameters(parameters)

      case Left(content: StreamedContent) =>
        // Content was written, keep it in the session for subsequent render requests with the current action parameters

        useAndClose(content) { _ =>

          // NOTE: Don't use the action parameters, as in the case of a form POST there can be dozens of those
          // or more, and anyway those don't make sense as subsequent render parameters. Instead, we just use
          // the path and a method indicator. Later we should either indicate an error, or handle XForms Ajax
          // updates properly.

          val storedParams = StoredParams(HttpMethod.POST, request.getParameter(PathParameter))

          response.setRenderParameters(
            Map(
              PathParameter   -> Array(storedParams.path),
              MethodParameter -> Array(storedParams.method.entryName)
            ).asJava
          )

          // Store response
          storeResponseWithParameters(
            ResponseWithParams(Left(BufferedContent(content)(IOUtils.toByteArray)), storedParams)
          )
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
    APISupport.writeResponseBody(APISupport.mustRewriteForMediatype)(responseContent)
  }

  private object StoredResponse {

    case class StoredParams(
      method : HttpMethod,
      path   : String
    )

    object StoredParams {
      def fromRenderParameters(renderParams: RenderParameters): Option[StoredParams] = {
        val paramsMap = renderParams.getNames.asScala.map(name => name -> renderParams.getValues(name)).toMap
        for {
          methodString <- Headers.firstItemIgnoreCase(paramsMap, MethodParameter)
          method       <- HttpMethod.withNameOption(methodString)
          path         <- Headers.firstItemIgnoreCase(paramsMap, PathParameter)
        } yield StoredParams(method, path)
      }
    }

    // Immutable response with parameters
    case class ResponseWithParams(
      response   : BufferedContentOrRedirect,
      parameters : StoredParams
    )

    // https://github.com/orbeon/orbeon-forms/issues/3978
    def matchesStoredResponse(
      renderParams : RenderParameters,
      storedParams : StoredParams
    ): Boolean =
      StoredParams.fromRenderParameters(renderParams) contains storedParams

    def findStoredResponseWithParameters(implicit ctx: EmbeddingContext): Option[ResponseWithParams] =
      ctx.getSessionAttribute(ResponseSessionKey) map (_.asInstanceOf[ResponseWithParams])

    def storeResponseWithParameters(responseWithParameters: ResponseWithParams)(implicit ctx: EmbeddingContext): Unit =
      ctx.setSessionAttribute(ResponseSessionKey, responseWithParameters)

    def clearResponseWithParameters()(implicit ctx: EmbeddingContext): Unit =
      ctx.removeSessionAttribute(ResponseSessionKey)
  }
}

object BufferedPortlet {

  val PathParameter: String = PathParameterName
  val MethodParameter       = "orbeon.method"
  val ResponseSessionKey    = "org.orbeon.oxf.response"
}