package org.orbeon.xforms.offline.demo

import java.io.{InputStream, OutputStream, PrintWriter}
import java.net.URL
import java.util
import java.util.Locale

import cats.implicits.catsSyntaxOptionId
import org.orbeon.oxf.externalcontext.{Credentials, ExternalContext, SimpleSession, WebAppContext}
import org.orbeon.oxf.http.HttpMethod
import org.orbeon.oxf.util.CoreCrossPlatformSupport

import scala.collection.mutable


object DemoExternalContext {

  def externalContext: ExternalContext =
    new ExternalContext {

      def getWebAppContext: WebAppContext = new WebAppContext {
        def getResource(s: String): URL = ???
        def getResourceAsStream(s: String): InputStream = ???
        def getRealPath(s: String): String = ???
        def initParameters: Map[String, String] = ???
        def attributes: mutable.Map[String, AnyRef] = ???
        def log(message: String, throwable: Throwable): Unit = ???
        def log(message: String): Unit = ???
        def getNativeContext: AnyRef = ???
      }

      private var session: Option[ExternalContext.Session] = None

      def getSession(create: Boolean): ExternalContext.Session = {
        session.getOrElse {
          if (create) {
            val newSession = new SimpleSession(CoreCrossPlatformSupport.randomHexId)
            session = newSession.some
            newSession
          } else
            null
        }
      }

      val getRequest: ExternalContext.Request = new ExternalContext.Request {
        def getContainerType: String = ???
        def getContainerNamespace: String = ???
        def getPathInfo: String = ???
        def getRequestPath: String = ???
        def getContextPath: String = ???
        def getServletPath: String = ???
        def getClientContextPath(urlString: String): String = ???
        def getAttributesMap: util.Map[String, AnyRef] = ???
        def getHeaderValuesMap: util.Map[String, Array[String]] = ???
        def getParameterMap: util.Map[String, Array[AnyRef]] = ???
        def getCharacterEncoding: String = ???
        def getContentLength: Int = ???
        def getContentType: String = ???
        def getInputStream: InputStream = ???
        def getProtocol: String = ???
        def getRemoteHost: String = ???
        def getRemoteAddr: String = ???
        def getScheme: String = ???
        def getMethod: HttpMethod = ???
        def getServerName: String = ???
        def getServerPort: Int = ???
        def getSession(create: Boolean): ExternalContext.Session = getSession(create)
        def sessionInvalidate(): Unit = ???
        def isRequestedSessionIdValid: Boolean = ???
        def getRequestedSessionId: String = ???
        def getAuthType: String = ???
        def isSecure: Boolean = ???
        def credentials: Option[Credentials] = ???
        def isUserInRole(role: String): Boolean = ???
        def getLocale: Locale = ???
        def getLocales: util.Enumeration[_] = ???
        def getPathTranslated: String = ???
        def getQueryString: String = ???
        def getRequestURI: String = ???
        def getRequestURL: String = ???

        // Not relevant
        def getPortletMode: String = ???
        def getWindowState: String = ???
        def getNativeRequest: Any = ???
      }

      val getResponse: ExternalContext.Response = new ExternalContext.Response {
        def getWriter: PrintWriter = ???

        def getOutputStream: OutputStream = ???

        def isCommitted: Boolean = ???

        def reset(): Unit = ()
        def setContentType(contentType: String): Unit = ()
        def setStatus(status: Int): Unit = ()
        def setContentLength(len: Int): Unit = ()
        def setHeader(name: String, value: String): Unit = ()
        def addHeader(name: String, value: String): Unit = ()

        def sendError(code: Int): Unit = ()
        def getCharacterEncoding: String = ???

        def sendRedirect(location: String, isServerSide: Boolean, isExitPortal: Boolean): Unit = ???

        def setPageCaching(lastModified: Long): Unit = ()
        def setResourceCaching(lastModified: Long, expires: Long): Unit = ()

        def checkIfModifiedSince(request: ExternalContext.Request, lastModified: Long): Boolean = ???

        def setTitle(title: String): Unit = ()

        def getNativeResponse: AnyRef = ???

        def rewriteActionURL(urlString: String): String = urlString // TODO: CHECK
        def rewriteRenderURL(urlString: String): String = urlString // TODO: CHECK
        def rewriteActionURL(urlString: String, portletMode: String, windowState: String): String = urlString // TODO: CHECK
        def rewriteRenderURL(urlString: String, portletMode: String, windowState: String): String = urlString // TODO: CHECK
        def rewriteResourceURL(urlString: String, rewriteMode: Int): String = urlString                       // TODO: CHECK
        def getNamespacePrefix: String = ???
      }

      def getStartLoggerString: String = ""
      def getEndLoggerString: String = ""
    }
}
