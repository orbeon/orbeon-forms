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

import org.orbeon.io.IOUtils._
import org.orbeon.oxf.common.Defaults
import org.orbeon.oxf.http.StatusCode
import org.orbeon.oxf.util.ContentTypes
import org.orbeon.oxf.util.PathUtils._
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.webapp.ServletSupport

import java.io._
import java.util._


private case class FilterSettings(context: ServletContext, orbeonContextPathOpt: Option[String], defaultEncoding: String) {
  // NOTE: Never match anything if there is no context path
  val OrbeonResourceRegex = orbeonContextPathOpt map (path => s"$path(/.*)".r) getOrElse "$.".r
}

// For backward compatibility
trait OrbeonXFormsFilter extends JavaxOrbeonXFormsFilter

class JavaxOrbeonXFormsFilter   extends JavaxFilter  (new OrbeonXFormsFilterImpl)
class JakartaOrbeonXFormsFilter extends JakartaFilter(new OrbeonXFormsFilterImpl)

// This filter allows forwarding requests from your web app to an separate Orbeon Forms context.
class OrbeonXFormsFilterImpl extends Filter {

  import org.orbeon.oxf.servlet.OrbeonXFormsFilterImpl._

  private var settingsOpt: Option[FilterSettings] = None

  override def init(filterConfig: FilterConfig): Unit =
    settingsOpt =
      Some(
        FilterSettings(
          filterConfig.getServletContext,
          Option(filterConfig.getInitParameter(RendererContextParameterName)) map normalizeContextPath,
          Option(filterConfig.getInitParameter(DefaultEncodingParameterName)) getOrElse Defaults.DefaultEncodingForServletCompatibility
        )
      )

  override def destroy(): Unit = settingsOpt = None

  def doFilter(servletRequest: ServletRequest, servletResponse: ServletResponse, filterChain: FilterChain): Unit =
    settingsOpt foreach {
      case settings @ FilterSettings(servletContext, orbeonContextPathOpt, defaultEncoding) =>

        val orbeonContext = (
          orbeonContextPathOpt
          map { orbeonContextPath =>
            servletContext.getContext(orbeonContextPath) ensuring (
              _ ne null,
              s"Can't find Orbeon Forms context called '$orbeonContextPath'. Check the " +
              s"'$RendererContextParameterName' filter initialization parameter and the " +
              s"<Context crossContext='true'/> attribute."
            )
          }
          getOrElse servletContext
        )

        def getOrbeonDispatcher(path: String) =
          orbeonContext.getRequestDispatcher(path) ensuring
          (_ ne null, "Can't find Orbeon Forms request dispatcher.")

        val httpRequest  = servletRequest.asInstanceOf[HttpServletRequest]
        val httpResponse = servletResponse.asInstanceOf[HttpServletResponse]
        val requestPath  = ServletSupport.getRequestPathInfo(httpRequest)

        // Set whether deployment is integrated or separate
        // NOTE: Also for resources so that e.g. /xforms-server, /xforms-server-submit can handle URLs properly.
        httpRequest.setAttribute(
          RendererDeploymentAttributeName,
          if (orbeonContext eq servletContext) "integrated" else "separate"
        )

        requestPath match {
          case settings.OrbeonResourceRegex(subRequestPath) =>
            // Directly forward all requests meant for Orbeon Forms resources (including /xforms-server)

            // Check that the session exists for any request to /xforms-server
            // The purpose of this check is that if the application has invalidated the session, we don't
            // want to allow further interactions with a page.
            // NOTE: With Tomcat this doesn't seem necessary. Other containers might work differently.
            if (subRequestPath.startsWith("/xforms-server"))
              httpRequest.getSession(false) ensuring
              (_ ne null, "Session has expired. Unable to process incoming request.")

            getOrbeonDispatcher(subRequestPath).forward(httpRequest, httpResponse)
          case _ =>
            // Forward the request to the Orbeon Forms renderer
            val requestWrapper  = new FilterRequestWrapper(httpRequest)
            val responseWrapper = new FilterResponseWrapper(httpResponse, defaultEncoding)

            // Execute filter
            filterChain.doFilter(requestWrapper, responseWrapper)

            // Restore content length if needed, see https://github.com/orbeon/orbeon-forms/issues/2775
            //httpResponse.setContentLength(-1)

            val failureCodeOpt =
              responseWrapper.statusCode filterNot StatusCode.isSuccessCode

            failureCodeOpt match {
              case None =>
                // Content can be a String or other content set as a request attribute which can be read by the
                // scope generator
                val content =
                  Option(httpRequest.getAttribute(RendererDocumentAttributeName)) orElse
                  responseWrapper.content

                val nonEmptyContent = content match {
                  case Some(s: String) => s.nonAllBlank
                  case Some(_)         => true
                  case None            => false
                }

                // Forward to Orbeon Forms for rendering only if there is content to be rendered, otherwise just
                // return and let the filterChain finish its life naturally, assuming that when sendRedirect is
                // used, no content is available in the response object.
                if (nonEmptyContent) {

                  // Make sure document is set when available
                  content foreach {
                    httpRequest.setAttribute(
                      RendererDocumentAttributeName,
                      _
                    )
                  }

                  // Whether there is a session
                  httpRequest.setAttribute(
                    RendererHasSessionAttributeName,
                    (httpRequest.getSession(false) ne null).toString
                  )

                  // Mediatype if available
                  responseWrapper.mediaType foreach {
                    httpRequest.setAttribute(
                      RendererContentTypeAttributeName,
                      _
                    )
                  }

                  httpRequest.setAttribute(
                    RendererBaseUriAttributeName,
                    requestPath
                  )

                  // The request wrapper provides an empty request body if the filtered resource already
                  // attempted to read the body.
                  val orbeonRequestWrapper =
                    new OptionalBodyFilterRequestWrapper(httpRequest, requestWrapper.isRequestBodyRead)

                  getOrbeonDispatcher(RendererPath).forward(orbeonRequestWrapper, httpResponse)
                } else {
                  // No content was produced, consider this equivalent to a "not found" status
                  httpResponse.setStatus(404)
                }
              case Some(failureCode) =>
                // Forward status code
                httpResponse.setStatus(failureCode)
            }
        }
    }
}

object OrbeonXFormsFilterImpl {

  val RendererDeploymentAttributeName      = "oxf.xforms.renderer.deployment"
  val RendererBaseUriAttributeName         = "oxf.xforms.renderer.base-uri"
  val RendererDocumentAttributeName        = "oxf.xforms.renderer.document"
  val RendererContentTypeAttributeName     = "oxf.xforms.renderer.content-type"
  val RendererHasSessionAttributeName      = "oxf.xforms.renderer.has-session"

  private val RendererContextParameterName = "oxf.xforms.renderer.context"
  private val DefaultEncodingParameterName = "oxf.xforms.renderer.default-encoding"
  private val RendererPath                 = "/xforms-renderer"

  private def normalizeContextPath(s: String) =
    "/" + s.dropTrailingSlash.dropStartingSlash
}

private class FilterRequestWrapper(httpServletRequest: HttpServletRequest)
    extends HttpServletRequestWrapper(httpServletRequest)
    with RequestRemoveHeaders {

  private var _requestBodyRead = false
  def isRequestBodyRead = _requestBodyRead

  override def getReader = {
    this._requestBodyRead = true
    super.getReader
  }

  override def getInputStream = {
    this._requestBodyRead = true
    super.getInputStream
  }

  // See https://github.com/orbeon/orbeon-forms/issues/1796
  def headersToRemove = (name: String) => name.toLowerCase.startsWith("if-")
}

private class OptionalBodyFilterRequestWrapper(httpServletRequest: HttpServletRequest, forceEmptyBody: Boolean)
    extends FilterRequestWrapper(httpServletRequest) {

  override def getContentType   = if (forceEmptyBody) null else super.getContentType
  override def getContentLength = if (forceEmptyBody) 0    else super.getContentLength

  override def getInputStream =
    if (forceEmptyBody)
      new ServletInputStream {
        def read: Int = -1
        def isFinished: Boolean = true
        def isReady: Boolean = false
        def setReadListener(readListener: ReadListener): Unit = ()
      }
    else
      super.getInputStream

  override def getReader =
    if (forceEmptyBody)
      new BufferedReader(
        new Reader {
          def read(cbuf: Array[Char], off: Int, len: Int) = 0
          def close() = ()
        }
      )
    else
      super.getReader
}

private class FilterResponseWrapper(response: HttpServletResponse, defaultEncoding: String)
    extends HttpServletResponseWrapper(response) {

  private var _statusCode: Option[Int]                      = None
  def statusCode = _statusCode

  private var _byteArrayOutputStream: ByteArrayOutputStream = null
  private var _servletOutputStream: ServletOutputStream     = null
  private var _stringWriter: StringWriter                   = null
  private var _printWriter: PrintWriter                     = null
  private var _encoding: Option[String]                     = None
  private var _mediatype: Option[String]                    = None

  override def setStatus(code: Int)                         = _statusCode = Some(code)
  override def setStatus(code: Int, message: String)        = setStatus(code)
  override def sendError(code: Int, string: String)         = setStatus(code)
  override def sendError(code: Int)                         = setStatus(code)

  override def setDateHeader(name: String, value: Long)     = () // SHOULD HANDLE
  override def addDateHeader(name: String, value: Long)     = () // SHOULD HANDLE
  override def setHeader(name: String, value: String)       = () // SHOULD HANDLE
  override def addHeader(name: String, value: String)       = () // SHOULD HANDLE
  override def setIntHeader(name: String, value: Int)       = () // SHOULD HANDLE
  override def addIntHeader(name: String, value: Int)       = () // SHOULD HANDLE
  override def setLocale(locale: Locale)                    = () // SHOULD HANDLE

  override def setContentLength(i: Int)                     = () // NOP
  override def setBufferSize(i: Int)                        = () // NOP
  override def getBufferSize                                = Integer.MAX_VALUE // as if we had an "infinite" buffer
  override def flushBuffer()                                = () // NOP
  override def isCommitted                                  = false
  override def reset()                                      = resetBuffer()
  override def getCharacterEncoding                         = _encoding getOrElse defaultEncoding

  override def getOutputStream = {
    if (_byteArrayOutputStream eq null) {
      _byteArrayOutputStream = new ByteArrayOutputStream
      _servletOutputStream = new ServletOutputStream {
        def write(i: Int) = _byteArrayOutputStream.write(i)
        def isReady: Boolean = true
        def setWriteListener(writeListener: WriteListener): Unit = ()
      }
    }
    _servletOutputStream
  }

  override def getWriter = {
    if (_printWriter eq null) {
      _stringWriter = new StringWriter
      _printWriter = new PrintWriter(_stringWriter)
    }
    _printWriter
  }

  override def setContentType(contentType: String): Unit = {
    this._encoding  = ContentTypes.getContentTypeCharset(contentType)
    this._mediatype = ContentTypes.getContentTypeMediaType(contentType)
  }

  override def resetBuffer(): Unit = {
    if (_byteArrayOutputStream ne null) {
      runQuietly(_servletOutputStream.flush())
      _byteArrayOutputStream.reset()
    } else if (_stringWriter ne null) {
      _printWriter.flush()
      val sb = _stringWriter.getBuffer
      sb.delete(0, sb.length)
    }
  }

  def mediaType = _mediatype

  def content =
    if (_stringWriter ne null) {
      _printWriter.flush()
      Some(_stringWriter.toString)
    } else if (_servletOutputStream ne null) {
      _servletOutputStream.flush()
      Some(_byteArrayOutputStream.toString(getCharacterEncoding))
    } else
      None
}