package org.orbeon.oxf.externalcontext

import java.io.{OutputStream, PrintWriter}

class ResponseAdapter extends ExternalContext.Response {
  def getWriter: PrintWriter = null
  def getOutputStream: OutputStream = null
  def isCommitted = false
  def reset(): Unit = ()
  def setContentType(contentType: String): Unit = ()
  def setStatus(status: Int): Unit = ()
  def setContentLength(len: Int): Unit = ()
  def setHeader(name: String, value: String): Unit = ()
  def addHeader(name: String, value: String): Unit = ()
  def sendError(len: Int): Unit = ()
  def getCharacterEncoding: String = null
  def sendRedirect(location: String, isServerSide: Boolean, isExitPortal: Boolean): Unit = ()
  def setPageCaching(lastModified: Long): Unit = ()
  def setResourceCaching(lastModified: Long, expires: Long): Unit = ()
  def checkIfModifiedSince(request: ExternalContext.Request, lastModified: Long): Boolean =
    // Always indicate that the resource has been modified. If needed we could use:
    // `NetUtils.checkIfModifiedSince(request, lastModified)`
    true
  def rewriteActionURL(urlString: String): String = null
  def rewriteRenderURL(urlString: String): String = null
  def rewriteActionURL(urlString: String, portletMode: String, windowState: String): String = null
  def rewriteRenderURL(urlString: String, portletMode: String, windowState: String): String = null
  def rewriteResourceURL(urlString: String, rewriteMode: UrlRewriteMode): String = null
  def getNamespacePrefix: String = null
  def setTitle(title: String): Unit = ()
  def getNativeResponse: AnyRef = null
}