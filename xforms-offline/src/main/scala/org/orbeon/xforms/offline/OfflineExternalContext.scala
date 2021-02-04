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
        def getResource(s: String)                    : URL                         = ???
        def getResourceAsStream(s: String)            : InputStream                 = ???
        def getRealPath(s: String)                    : String                      = ???
        def initParameters                            : Map[String, String]         = ???
        def attributes                                : mutable.Map[String, AnyRef] = ???
        def log(message: String, throwable: Throwable): Unit                        = ???
        def log(message: String)                      : Unit                        = ???
        def getNativeContext                          : AnyRef                      = ???
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
        def getContentType                          : String = ???
        def getInputStream                          : InputStream = ???
        def getProtocol                             : String = ???
        def getRemoteHost                           : String = dom.window.document.location.hostname
        def getRemoteAddr                           : String = ???
        def getScheme                               : String = "http"
        def getMethod                               : HttpMethod = HttpMethod.GET
        def getServerName                           : String = dom.window.document.location.hostname
        def getServerPort                           : Int = dom.window.document.location.port.toInt
        def getSession(create: Boolean)             : ExternalContext.Session = selfExternalContext.getSession(create)
        def sessionInvalidate()                     : Unit = ???
        def isRequestedSessionIdValid               : Boolean = ???
        def getRequestedSessionId                   : String = ???
        def getAuthType                             : String = ???
        def isSecure                                : Boolean = dom.window.document.location.protocol == "https:/"
        def credentials                             : Option[Credentials] = None
        def isUserInRole(role: String)              : Boolean = false
        def getLocale                               : Locale = ???
        def getLocales                              : util.Enumeration[_] = ???
        def getPathTranslated                       : String = ???
        def getQueryString                          : String = ???
        def getRequestURI                           : String = ???
        def getRequestURL                           : String = ???

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

        def rewriteActionURL  (urlString: String): String                                           = urlString // TODO: CHECK
        def rewriteRenderURL  (urlString: String): String                                           = urlString // TODO: CHECK
        def rewriteActionURL  (urlString: String, portletMode: String, windowState: String): String = urlString // TODO: CHECK
        def rewriteRenderURL  (urlString: String, portletMode: String, windowState: String): String = urlString // TODO: CHECK
        def rewriteResourceURL(urlString: String, rewriteMode: Int): String                         = urlString // TODO: CHECK

        def getNamespacePrefix: String = ???
      }

      def getStartLoggerString: String = ""
      def getEndLoggerString: String = ""
    }
}