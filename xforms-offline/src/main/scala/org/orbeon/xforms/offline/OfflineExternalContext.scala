package org.orbeon.xforms.offline

import java.io.{InputStream, OutputStream, PrintWriter}
import java.net.URL
import java.util
import java.util.Locale

import cats.implicits.catsSyntaxOptionId
import org.orbeon.io.CharsetNames
import org.orbeon.oxf.externalcontext.{Credentials, ExternalContext, SimpleSession, WebAppContext}
import org.orbeon.oxf.http.HttpMethod
import org.orbeon.oxf.util.CoreCrossPlatformSupport
import org.scalajs.dom

import scala.collection.mutable
import scala.jdk.CollectionConverters._


object OfflineExternalContext {

  private var session: Option[ExternalContext.Session] = None

  def newExternalContext: ExternalContext =
    new ExternalContext {

      selfExternalContext =>

      // Probably unused by offline
      def getWebAppContext: WebAppContext = new WebAppContext {
        def getResource(s: String)                    : URL                         = throw new NotImplementedError("getResource")
        def getResourceAsStream(s: String)            : InputStream                 = throw new NotImplementedError("getResourceAsStream")
        def getRealPath(s: String)                    : String                      = throw new NotImplementedError("getRealPath")
        def initParameters                            : Map[String, String]         = throw new NotImplementedError("initParameters")
        def attributes                                : mutable.Map[String, AnyRef] = throw new NotImplementedError("attributes")
        def log(message: String, throwable: Throwable): Unit                        = throw new NotImplementedError("log")
        def log(message: String)                      : Unit                        = throw new NotImplementedError("log")
        def getNativeContext                          : AnyRef                      = throw new NotImplementedError("getNativeContext")
      }

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
        def getContainerType                        : String = "offline"
        def getContainerNamespace                   : String = ""
        def getPathInfo                             : String = getRequestPath
        def getRequestPath                          : String = dom.window.document.location.pathname
        def getContextPath                          : String = ""
        def getServletPath                          : String = ""
        def getClientContextPath(urlString: String) : String = ""
        val getAttributesMap                        : util.Map[String, AnyRef]        = mutable.Map.empty.asJava
        def getHeaderValuesMap                      : util.Map[String, Array[String]] = Map.empty.asJava
        def getParameterMap                         : util.Map[String, Array[AnyRef]] = Map.empty.asJava
        def getCharacterEncoding                    : String = CharsetNames.Utf8
        def getContentLength                        : Int = -1
        def getContentType                          : String = throw new NotImplementedError("getContentType")
        def getInputStream                          : InputStream = throw new NotImplementedError("getInputStream")
        def getProtocol                             : String = throw new NotImplementedError("getProtocol")
        def getRemoteHost                           : String = dom.window.document.location.hostname
        def getRemoteAddr                           : String = throw new NotImplementedError("getRemoteAddr")
        def getScheme                               : String = "http"
        def getMethod                               : HttpMethod = HttpMethod.GET
        def getServerName                           : String = dom.window.document.location.hostname
        def getServerPort                           : Int = dom.window.document.location.port.toInt
        def getSession(create: Boolean)             : ExternalContext.Session = selfExternalContext.getSession(create)
        def sessionInvalidate()                     : Unit = throw new NotImplementedError("sessionInvalidate")
        def isRequestedSessionIdValid               : Boolean = throw new NotImplementedError("isRequestedSessionIdValid")
        def getRequestedSessionId                   : String = throw new NotImplementedError("getRequestedSessionId")
        def getAuthType                             : String = throw new NotImplementedError("getAuthType")
        def isSecure                                : Boolean = dom.window.document.location.protocol == "https:/"
        def credentials                             : Option[Credentials] = None
        def isUserInRole(role: String)              : Boolean = false
        def getLocale                               : Locale = throw new NotImplementedError("getLocale")
        def getLocales                              : util.Enumeration[_] = throw new NotImplementedError("getLocales")
        def getPathTranslated                       : String = throw new NotImplementedError("getPathTranslated")
        def getQueryString                          : String = throw new NotImplementedError("getQueryString")
        def getRequestURI                           : String = throw new NotImplementedError("getRequestURI")
        def getRequestURL                           : String = throw new NotImplementedError("getRequestURL")

        // Not relevant
        def getPortletMode: String = throw new NotImplementedError("getPortletMode")
        def getWindowState: String = throw new NotImplementedError("getWindowState")
        def getNativeRequest: Any = throw new NotImplementedError("getNativeRequest")
      }

      val getResponse: ExternalContext.Response = new ExternalContext.Response {
        def getWriter: PrintWriter = throw new NotImplementedError("getWriter")

        def getOutputStream: OutputStream = throw new NotImplementedError("getOutputStream")

        def isCommitted: Boolean = throw new NotImplementedError("isCommitted")

        def reset(): Unit = ()
        def setContentType(contentType: String): Unit = ()
        def setStatus(status: Int): Unit = ()
        def setContentLength(len: Int): Unit = ()
        def setHeader(name: String, value: String): Unit = ()
        def addHeader(name: String, value: String): Unit = ()

        def sendError(code: Int): Unit = ()
        def getCharacterEncoding: String = throw new NotImplementedError("getCharacterEncoding")

        def sendRedirect(location: String, isServerSide: Boolean, isExitPortal: Boolean): Unit = throw new NotImplementedError("sendRedirect")

        def setPageCaching(lastModified: Long): Unit = ()
        def setResourceCaching(lastModified: Long, expires: Long): Unit = ()

        def checkIfModifiedSince(request: ExternalContext.Request, lastModified: Long): Boolean = throw new NotImplementedError("checkIfModifiedSince")

        def setTitle(title: String): Unit = ()

        def getNativeResponse: AnyRef = throw new NotImplementedError("getNativeResponse")

        def rewriteActionURL  (urlString: String): String                                           = urlString // TODO: CHECK
        def rewriteRenderURL  (urlString: String): String                                           = urlString // TODO: CHECK
        def rewriteActionURL  (urlString: String, portletMode: String, windowState: String): String = urlString // TODO: CHECK
        def rewriteRenderURL  (urlString: String, portletMode: String, windowState: String): String = urlString // TODO: CHECK
        def rewriteResourceURL(urlString: String, rewriteMode: Int): String                         = urlString // TODO: CHECK

        def getNamespacePrefix: String = throw new NotImplementedError("getNamespacePrefix")
      }

      def getStartLoggerString: String = ""
      def getEndLoggerString: String = ""
    }
}