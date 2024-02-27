package org.orbeon.oxf.externalcontext

import java.io.{OutputStream, PrintWriter}


class ResponseWrapper(var _response: ExternalContext.Response)
  extends ExternalContext.Response {

  require(_response ne null)

  def checkIfModifiedSince(request: ExternalContext.Request, lastModified: Long): Boolean = _response.checkIfModifiedSince(request, lastModified)
  def getCharacterEncoding: String = _response.getCharacterEncoding
  def getNamespacePrefix: String = _response.getNamespacePrefix
  def getOutputStream: OutputStream = _response.getOutputStream
  def getWriter: PrintWriter = _response.getWriter
  def isCommitted: Boolean = _response.isCommitted

  def reset(): Unit =
    _response.reset()

  def rewriteActionURL(urlString: String): String = _response.rewriteActionURL(urlString)
  def rewriteRenderURL(urlString: String): String = _response.rewriteRenderURL(urlString)
  def rewriteActionURL(urlString: String, portletMode: String, windowState: String): String = _response.rewriteActionURL(urlString, portletMode, windowState)
  def rewriteRenderURL(urlString: String, portletMode: String, windowState: String): String = _response.rewriteRenderURL(urlString, portletMode, windowState)
  def rewriteResourceURL(urlString: String, rewriteMode: UrlRewriteMode): String = _response.rewriteResourceURL(urlString, rewriteMode)

  def sendError(len: Int): Unit =
    _response.sendError(len)

  def sendRedirect(location: String, isServerSide: Boolean, isExitPortal: Boolean): Unit =
    _response.sendRedirect(location, isServerSide, isExitPortal)

  def setPageCaching(lastModified: Long): Unit =
    _response.setPageCaching(lastModified)

  def setResourceCaching(lastModified: Long, expires: Long): Unit =
    _response.setResourceCaching(lastModified, expires)

  def setContentLength(len: Int): Unit =
    _response.setContentLength(len)

  def setContentType(contentType: String): Unit =
    _response.setContentType(contentType)

  def setHeader(name: String, value: String): Unit =
    _response.setHeader(name, value)

  def addHeader(name: String, value: String): Unit =
    _response.addHeader(name, value)

  def setStatus(status: Int): Unit =
    _response.setStatus(status)

  def getStatus: Int = _response.getStatus

  def setTitle(title: String): Unit =
    _response.setTitle(title)

  def getNativeResponse: AnyRef = _response.getNativeResponse
  def _getResponse: ExternalContext.Response = _response
}