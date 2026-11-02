/**
 * Copyright (C) 2026 Orbeon, Inc.
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

import org.orbeon.oxf.servlet.Cookie

import java.io.{OutputStream, PrintWriter, Serializable}
import java.util as ju
import javax.xml.namespace.QName
import scala.annotation.nowarn


object PortletResponse {
  def apply(portletResponse: javax.portlet.PortletResponse): PortletResponse =
    portletResponse match {
      case renderResponse: javax.portlet.RenderResponse         => new JavaxRenderResponse(renderResponse)
      case resourceResponse: javax.portlet.ResourceResponse     => new JavaxResourceResponse(resourceResponse)
      case mimeResponse: javax.portlet.MimeResponse             => new JavaxMimeResponse(mimeResponse)
      case actionResponse: javax.portlet.ActionResponse         => new JavaxActionResponse(actionResponse)
      case eventResponse: javax.portlet.EventResponse           => new JavaxEventResponse(eventResponse)
      case stateAwareResponse: javax.portlet.StateAwareResponse => new JavaxStateAwareResponse(stateAwareResponse)
      case _                                                    => new JavaxPortletResponse(portletResponse)
    }

  def apply(portletResponse: jakarta.portlet.PortletResponse): PortletResponse =
    portletResponse match {
      case renderResponse: jakarta.portlet.RenderResponse         => new JakartaRenderResponse(renderResponse)
      case resourceResponse: jakarta.portlet.ResourceResponse     => new JakartaResourceResponse(resourceResponse)
      case mimeResponse: jakarta.portlet.MimeResponse             => new JakartaMimeResponse(mimeResponse)
      case actionResponse: jakarta.portlet.ActionResponse         => new JakartaActionResponse(actionResponse)
      case eventResponse: jakarta.portlet.EventResponse           => new JakartaEventResponse(eventResponse)
      case stateAwareResponse: jakarta.portlet.StateAwareResponse => new JakartaStateAwareResponse(stateAwareResponse)
      case _                                                      => new JakartaPortletResponse(portletResponse)
    }
}

trait PortletResponse {
  // javax/jakarta.portlet.PortletResponse
  def getNativePortletResponse: AnyRef

  def addProperty(cookie: Cookie): Unit
  def addProperty(key: String, value: String): Unit
  def encodeURL(path: String): String
  def getNamespace: String
  def setProperty(key: String, value: String): Unit
}

object MimeResponse {
  def apply(mimeResponse: javax.portlet.MimeResponse): MimeResponse =
    mimeResponse match {
      case renderResponse: javax.portlet.RenderResponse     => new JavaxRenderResponse(renderResponse)
      case resourceResponse: javax.portlet.ResourceResponse => new JavaxResourceResponse(resourceResponse)
      case _                                                => new JavaxMimeResponse(mimeResponse)
    }

  def apply(mimeResponse: jakarta.portlet.MimeResponse): MimeResponse =
    mimeResponse match {
      case renderResponse: jakarta.portlet.RenderResponse     => new JakartaRenderResponse(renderResponse)
      case resourceResponse: jakarta.portlet.ResourceResponse => new JakartaResourceResponse(resourceResponse)
      case _                                                  => new JakartaMimeResponse(mimeResponse)
    }
}

trait MimeResponse extends PortletResponse {
  def createActionURL(): PortletURL
  def createRenderURL(): PortletURL
  def createResourceURL(): ResourceURL
  def flushBuffer(): Unit
  def getBufferSize: Int
  def getCharacterEncoding: String
  def getContentType: String
  def getLocale: ju.Locale
  def getPortletOutputStream: OutputStream
  def getWriter: PrintWriter
  def isCommitted: Boolean
  def reset(): Unit
  def resetBuffer(): Unit
  def setBufferSize(size: Int): Unit
  def setContentType(`type`: String): Unit
}

object RenderResponse {
  def apply(renderResponse: javax.portlet.RenderResponse): RenderResponse     = new JavaxRenderResponse(renderResponse)
  def apply(renderResponse: jakarta.portlet.RenderResponse): RenderResponse = new JakartaRenderResponse(renderResponse)
}

trait RenderResponse extends MimeResponse {
  def setTitle(title: String): Unit
}

object ResourceResponse {
  val HttpStatusCode: String = "javax.portlet.http-status-code"
  val HTTP_STATUS_CODE: String = HttpStatusCode

  def apply(resourceResponse: javax.portlet.ResourceResponse): ResourceResponse     = new JavaxResourceResponse(resourceResponse)
  def apply(resourceResponse: jakarta.portlet.ResourceResponse): ResourceResponse = new JakartaResourceResponse(resourceResponse)
}

trait ResourceResponse extends MimeResponse {
  val HttpStatusCode: String
  def getStatus: Int
  def setCharacterEncoding(charset: String): Unit
  def setContentLength(len: Int): Unit
  def setContentLengthLong(len: Long): Unit
  def setLocale(loc: ju.Locale): Unit
  def setStatus(sc: Int): Unit
}

object StateAwareResponse {
  def apply(stateAwareResponse: javax.portlet.StateAwareResponse): StateAwareResponse =
    stateAwareResponse match {
      case actionResponse: javax.portlet.ActionResponse => new JavaxActionResponse(actionResponse)
      case eventResponse: javax.portlet.EventResponse   => new JavaxEventResponse(eventResponse)
      case _                                            => new JavaxStateAwareResponse(stateAwareResponse)
    }

  def apply(stateAwareResponse: jakarta.portlet.StateAwareResponse): StateAwareResponse =
    stateAwareResponse match {
      case actionResponse: jakarta.portlet.ActionResponse => new JakartaActionResponse(actionResponse)
      case eventResponse: jakarta.portlet.EventResponse   => new JakartaEventResponse(eventResponse)
      case _                                              => new JakartaStateAwareResponse(stateAwareResponse)
    }
}

trait StateAwareResponse extends PortletResponse {
  def getPortletMode: PortletMode
  def getRenderParameterMap: ju.Map[String, Array[String]]
  def getWindowState: WindowState
  def removePublicRenderParameter(name: String): Unit
  def setEvent(name: QName, value: Serializable): Unit
  def setEvent(name: String, value: Serializable): Unit
  def setPortletMode(portletMode: PortletMode): Unit
  def setRenderParameter(key: String, value: String): Unit
  def setRenderParameter(key: String, values: Array[String]): Unit
  def setRenderParameters(parameters: ju.Map[String, Array[String]]): Unit
  def setWindowState(windowState: WindowState): Unit
}

object ActionResponse {
  def apply(actionResponse: javax.portlet.ActionResponse): ActionResponse     = new JavaxActionResponse(actionResponse)
  def apply(actionResponse: jakarta.portlet.ActionResponse): ActionResponse = new JakartaActionResponse(actionResponse)
}

trait ActionResponse extends StateAwareResponse {
  def sendRedirect(location: String): Unit
  def sendRedirect(location: String, renderUrlParamName: String): Unit
}

object EventResponse {
  def apply(eventResponse: javax.portlet.EventResponse): EventResponse     = new JavaxEventResponse(eventResponse)
  def apply(eventResponse: jakarta.portlet.EventResponse): EventResponse = new JakartaEventResponse(eventResponse)
}

trait EventResponse extends StateAwareResponse {
  def setRenderParameters(request: EventRequest): Unit
}

class JavaxPortletResponse(portletResponse: javax.portlet.PortletResponse) extends PortletResponse {
  override def getNativePortletResponse: javax.portlet.PortletResponse = portletResponse

  override def addProperty(cookie: Cookie): Unit =
    portletResponse.addProperty(cookie.getNativeCookie.asInstanceOf[javax.servlet.http.Cookie])
  override def addProperty(key: String, value: String): Unit = portletResponse.addProperty(key, value)
  override def encodeURL(path: String): String = portletResponse.encodeURL(path)
  override def getNamespace: String = portletResponse.getNamespace
  override def setProperty(key: String, value: String): Unit = portletResponse.setProperty(key, value)
}

class JakartaPortletResponse(portletResponse: jakarta.portlet.PortletResponse) extends PortletResponse {
  override def getNativePortletResponse: jakarta.portlet.PortletResponse = portletResponse

  override def addProperty(cookie: Cookie): Unit =
    portletResponse.addProperty(cookie.getNativeCookie.asInstanceOf[jakarta.servlet.http.Cookie])
  override def addProperty(key: String, value: String): Unit = portletResponse.addProperty(key, value)
  override def encodeURL(path: String): String = portletResponse.encodeURL(path)
  override def getNamespace: String = portletResponse.getNamespace
  override def setProperty(key: String, value: String): Unit = portletResponse.setProperty(key, value)
}

@nowarn("cat=deprecation")
class JavaxMimeResponse(mimeResponse: javax.portlet.MimeResponse)
  extends JavaxPortletResponse(mimeResponse)
     with MimeResponse {
  override def getNativePortletResponse: javax.portlet.MimeResponse = mimeResponse

  override def createActionURL(): PortletURL = new JavaxPortletURL(mimeResponse.createActionURL())
  override def createRenderURL(): PortletURL = new JavaxPortletURL(mimeResponse.createRenderURL())
  override def createResourceURL(): ResourceURL = ResourceURL(mimeResponse.createResourceURL())
  override def flushBuffer(): Unit = mimeResponse.flushBuffer()
  override def getBufferSize: Int = mimeResponse.getBufferSize
  override def getCharacterEncoding: String = mimeResponse.getCharacterEncoding
  override def getContentType: String = mimeResponse.getContentType
  override def getLocale: ju.Locale = mimeResponse.getLocale
  override def getPortletOutputStream: OutputStream = mimeResponse.getPortletOutputStream
  override def getWriter: PrintWriter = mimeResponse.getWriter
  override def isCommitted: Boolean = mimeResponse.isCommitted
  override def reset(): Unit = mimeResponse.reset()
  override def resetBuffer(): Unit = mimeResponse.resetBuffer()
  override def setBufferSize(size: Int): Unit = mimeResponse.setBufferSize(size)
  override def setContentType(`type`: String): Unit = mimeResponse.setContentType(`type`)
}

@nowarn("cat=deprecation")
class JakartaMimeResponse(mimeResponse: jakarta.portlet.MimeResponse)
  extends JakartaPortletResponse(mimeResponse)
     with MimeResponse {
  override def getNativePortletResponse: jakarta.portlet.MimeResponse = mimeResponse

  override def createActionURL(): PortletURL = new JakartaPortletURL(mimeResponse.createActionURL())
  override def createRenderURL(): PortletURL = new JakartaPortletURL(mimeResponse.createRenderURL())
  override def createResourceURL(): ResourceURL = ResourceURL(mimeResponse.createResourceURL())
  override def flushBuffer(): Unit = mimeResponse.flushBuffer()
  override def getBufferSize: Int = mimeResponse.getBufferSize
  override def getCharacterEncoding: String = mimeResponse.getCharacterEncoding
  override def getContentType: String = mimeResponse.getContentType
  override def getLocale: ju.Locale = mimeResponse.getLocale
  override def getPortletOutputStream: OutputStream = mimeResponse.getPortletOutputStream
  override def getWriter: PrintWriter = mimeResponse.getWriter
  override def isCommitted: Boolean = mimeResponse.isCommitted
  override def reset(): Unit = mimeResponse.reset()
  override def resetBuffer(): Unit = mimeResponse.resetBuffer()
  override def setBufferSize(size: Int): Unit = mimeResponse.setBufferSize(size)
  override def setContentType(`type`: String): Unit = mimeResponse.setContentType(`type`)
}

@nowarn("cat=deprecation")
class JavaxRenderResponse(renderResponse: javax.portlet.RenderResponse)
  extends JavaxMimeResponse(renderResponse)
     with RenderResponse {
  override def getNativePortletResponse: javax.portlet.RenderResponse = renderResponse
  override def setTitle(title: String): Unit = renderResponse.setTitle(title)
}

@nowarn("cat=deprecation")
class JakartaRenderResponse(renderResponse: jakarta.portlet.RenderResponse)
  extends JakartaMimeResponse(renderResponse)
     with RenderResponse {
  override def getNativePortletResponse: jakarta.portlet.RenderResponse = renderResponse
  override def setTitle(title: String): Unit = renderResponse.setTitle(title)
}

class JavaxResourceResponse(resourceResponse: javax.portlet.ResourceResponse)
  extends JavaxMimeResponse(resourceResponse)
     with ResourceResponse {
  override def getNativePortletResponse: javax.portlet.ResourceResponse = resourceResponse

  override val HttpStatusCode: String = javax.portlet.ResourceResponse.HTTP_STATUS_CODE
  override def getStatus: Int = resourceResponse.getStatus
  override def setCharacterEncoding(charset: String): Unit = resourceResponse.setCharacterEncoding(charset)
  override def setContentLength(len: Int): Unit = resourceResponse.setContentLength(len)
  override def setContentLengthLong(len: Long): Unit = resourceResponse.setContentLengthLong(len)
  override def setLocale(loc: ju.Locale): Unit = resourceResponse.setLocale(loc)
  override def setStatus(sc: Int): Unit = resourceResponse.setStatus(sc)
}

class JakartaResourceResponse(resourceResponse: jakarta.portlet.ResourceResponse)
  extends JakartaMimeResponse(resourceResponse)
     with ResourceResponse {
  override def getNativePortletResponse: jakarta.portlet.ResourceResponse = resourceResponse

  override val HttpStatusCode: String = jakarta.portlet.ResourceResponse.HTTP_STATUS_CODE
  override def getStatus: Int = resourceResponse.getStatus
  override def setCharacterEncoding(charset: String): Unit = resourceResponse.setCharacterEncoding(charset)
  override def setContentLength(len: Int): Unit = resourceResponse.setContentLength(len)
  override def setContentLengthLong(len: Long): Unit = resourceResponse.setContentLengthLong(len)
  override def setLocale(loc: ju.Locale): Unit = resourceResponse.setLocale(loc)
  override def setStatus(sc: Int): Unit = resourceResponse.setStatus(sc)
}

@nowarn("cat=deprecation")
class JavaxStateAwareResponse(stateAwareResponse: javax.portlet.StateAwareResponse)
  extends JavaxPortletResponse(stateAwareResponse)
     with StateAwareResponse {
  override def getNativePortletResponse: javax.portlet.StateAwareResponse = stateAwareResponse

  override def getPortletMode: PortletMode = PortletMode(stateAwareResponse.getPortletMode)
  override def getRenderParameterMap: ju.Map[String, Array[String]] = stateAwareResponse.getRenderParameterMap
  override def getWindowState: WindowState = WindowState(stateAwareResponse.getWindowState)
  override def removePublicRenderParameter(name: String): Unit = stateAwareResponse.removePublicRenderParameter(name)
  override def setEvent(name: QName, value: Serializable): Unit = stateAwareResponse.setEvent(name, value)
  override def setEvent(name: String, value: Serializable): Unit = stateAwareResponse.setEvent(name, value)
  override def setPortletMode(portletMode: PortletMode): Unit =
    stateAwareResponse.setPortletMode(Option(portletMode).map(m => new javax.portlet.PortletMode(m.toString)).orNull)
  override def setRenderParameter(key: String, value: String): Unit = stateAwareResponse.setRenderParameter(key, value)
  override def setRenderParameter(key: String, values: Array[String]): Unit = stateAwareResponse.setRenderParameter(key, values*)
  override def setRenderParameters(parameters: ju.Map[String, Array[String]]): Unit = stateAwareResponse.setRenderParameters(parameters)
  override def setWindowState(windowState: WindowState): Unit =
    stateAwareResponse.setWindowState(Option(windowState).map(s => new javax.portlet.WindowState(s.toString)).orNull)
}

@nowarn("cat=deprecation")
class JakartaStateAwareResponse(stateAwareResponse: jakarta.portlet.StateAwareResponse)
  extends JakartaPortletResponse(stateAwareResponse)
     with StateAwareResponse {
  override def getNativePortletResponse: jakarta.portlet.StateAwareResponse = stateAwareResponse

  override def getPortletMode: PortletMode = PortletMode(stateAwareResponse.getPortletMode)
  override def getRenderParameterMap: ju.Map[String, Array[String]] = stateAwareResponse.getRenderParameterMap
  override def getWindowState: WindowState = WindowState(stateAwareResponse.getWindowState)
  override def removePublicRenderParameter(name: String): Unit = stateAwareResponse.removePublicRenderParameter(name)
  override def setEvent(name: QName, value: Serializable): Unit = stateAwareResponse.setEvent(name, value)
  override def setEvent(name: String, value: Serializable): Unit = stateAwareResponse.setEvent(name, value)
  override def setPortletMode(portletMode: PortletMode): Unit =
    stateAwareResponse.setPortletMode(Option(portletMode).map(m => new jakarta.portlet.PortletMode(m.toString)).orNull)
  override def setRenderParameter(key: String, value: String): Unit = stateAwareResponse.setRenderParameter(key, value)
  override def setRenderParameter(key: String, values: Array[String]): Unit = stateAwareResponse.setRenderParameter(key, values*)
  override def setRenderParameters(parameters: ju.Map[String, Array[String]]): Unit = stateAwareResponse.setRenderParameters(parameters)
  override def setWindowState(windowState: WindowState): Unit =
    stateAwareResponse.setWindowState(Option(windowState).map(s => new jakarta.portlet.WindowState(s.toString)).orNull)
}

class JavaxActionResponse(actionResponse: javax.portlet.ActionResponse)
  extends JavaxStateAwareResponse(actionResponse)
     with ActionResponse {
  override def getNativePortletResponse: javax.portlet.ActionResponse = actionResponse

  override def sendRedirect(location: String): Unit = actionResponse.sendRedirect(location)
  override def sendRedirect(location: String, renderUrlParamName: String): Unit =
    actionResponse.sendRedirect(location, renderUrlParamName)
}

class JakartaActionResponse(actionResponse: jakarta.portlet.ActionResponse)
  extends JakartaStateAwareResponse(actionResponse)
     with ActionResponse {
  override def getNativePortletResponse: jakarta.portlet.ActionResponse = actionResponse

  override def sendRedirect(location: String): Unit = actionResponse.sendRedirect(location)
  override def sendRedirect(location: String, renderUrlParamName: String): Unit =
    actionResponse.sendRedirect(location, renderUrlParamName)
}

@nowarn("cat=deprecation")
class JavaxEventResponse(eventResponse: javax.portlet.EventResponse)
  extends JavaxStateAwareResponse(eventResponse)
     with EventResponse {
  override def getNativePortletResponse: javax.portlet.EventResponse = eventResponse

  override def setRenderParameters(request: EventRequest): Unit =
    eventResponse.setRenderParameters(request.getNativePortletRequest.asInstanceOf[javax.portlet.EventRequest])
}

@nowarn("cat=deprecation")
class JakartaEventResponse(eventResponse: jakarta.portlet.EventResponse)
  extends JakartaStateAwareResponse(eventResponse)
     with EventResponse {
  override def getNativePortletResponse: jakarta.portlet.EventResponse = eventResponse

  override def setRenderParameters(request: EventRequest): Unit =
    eventResponse.setRenderParameters(request.getNativePortletRequest.asInstanceOf[jakarta.portlet.EventRequest])
}
