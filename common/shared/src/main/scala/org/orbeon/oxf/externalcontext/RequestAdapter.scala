package org.orbeon.oxf.externalcontext

import org.orbeon.oxf.http.HttpMethod

import java.io.InputStream
import java.{util => ju}


class RequestAdapter extends ExternalContext.Request {
  def getContainerType: String = null
  def getContainerNamespace: String = null
  def getPathInfo: String = null
  def getRequestPath: String = null
  def getContextPath: String = null
  def getServletPath: String = null
  def getClientContextPath(urlString: String): String = null
  def getAttributesMap: ju.Map[String, AnyRef] = null
  def getHeaderValuesMap: ju.Map[String, Array[String]] = null
  def getParameterMap: ju.Map[String, Array[AnyRef]] = null
  def getCharacterEncoding: String = null
  def getContentLength: Int = 0
  def getContentType: String = null
  def getInputStream: InputStream = null
  def getProtocol: String = null
  def getRemoteHost: String = null
  def getRemoteAddr: String = null
  def getScheme: String = null
  def getMethod: HttpMethod = null
  def getServerName: String = null
  def getServerPort: Int = 0
  def getSession(create: Boolean): ExternalContext.Session = null
  def sessionInvalidate() = ()
  def isRequestedSessionIdValid: Boolean = false
  def getRequestedSessionId: String = null
  def getAuthType: String = null
  def isSecure: Boolean = false
  def credentials: Option[Credentials] = None
  def isUserInRole(role: String): Boolean = false
  def getLocale: ju.Locale = null
  def getLocales: ju.Enumeration[_] = null
  def getPathTranslated: String = null
  def getQueryString: String = null
  def getRequestURI: String = null
  def getRequestURL: String = null
  def getPortletMode: String = null
  def getWindowState: String = null
  def getNativeRequest: AnyRef = null
}
