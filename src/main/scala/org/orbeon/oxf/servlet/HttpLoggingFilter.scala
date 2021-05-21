package org.orbeon.oxf.servlet

import org.orbeon.io.CharsetNames
import org.orbeon.oxf.util.NetUtils
import org.slf4j.{Logger, LoggerFactory}

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, InputStream}
import javax.servlet.http.{HttpServletRequest, HttpServletRequestWrapper}
import javax.servlet.{Filter, FilterChain, FilterConfig, ReadListener, ServletInputStream, ServletRequest, ServletResponse}
import scala.collection.JavaConverters._

// To enable, add the following to your `web.xml`, before all the other filters.
//
// <filter>
//     <filter-name>orbeon-http-logging-filter</filter-name>
//     <filter-class>org.orbeon.oxf.servlet.HttpLoggingFilter</filter-class>
// </filter>
// <filter-mapping>
//     <filter-name>orbeon-http-logging-filter</filter-name>
//     <url-pattern>/xforms-server</url-pattern>
//     <dispatcher>REQUEST</dispatcher>
// </filter-mapping>

class HttpLoggingFilter extends Filter {

  val Logger: Logger = LoggerFactory.getLogger("org.orbeon.filter.http-logging")

  override def doFilter(
    servletRequest  : ServletRequest,
    servletResponse : ServletResponse,
    chain           : FilterChain
  ): Unit = {

    val httpRequest         = servletRequest.asInstanceOf[HttpServletRequest]
    val byteArray           = NetUtils.inputStreamToByteArray(httpRequest.getInputStream)
    val requestPath         = NetUtils.getRequestPathInfo(httpRequest)
    val requestBody         = new String(byteArray, CharsetNames.Utf8)
    val inputStream         = new ByteArrayInputStream(byteArray)
    val servletInputStream  = new Private.ByteArrayServletInputStream(inputStream)
    val wrappedRequest      = new Private.LoggerRequestWrapper(httpRequest, servletInputStream)

    // Log path, headers, and body
    Logger.info(s"request path `$requestPath`")
    httpRequest.getHeaderNames.asScala.foreach { headerName =>
      val headerValue = httpRequest.getHeader(headerName)
      Logger.info(s"request header `$headerName: $headerValue`")
    }
    Logger.info(s"request body `$requestBody`")

    chain.doFilter(wrappedRequest, servletResponse)
  }

  // We need to provide those implementation of `init` and `destroy` as long as we support Servlet 3.1 / Tomcat 8.5
  override def init(config: FilterConfig): Unit = {}
  override def destroy(): Unit = {}
}

private object Private {

  class LoggerRequestWrapper(httpServletRequest: HttpServletRequest, servletInputStream: ServletInputStream)
      extends HttpServletRequestWrapper(httpServletRequest) {
    override def getInputStream: ServletInputStream = servletInputStream
  }

  class ByteArrayServletInputStream(byteArrayInputStream: ByteArrayInputStream)
      extends ServletInputStream {
    override def isFinished: Boolean                               = byteArrayInputStream.available() == 0
    override def isReady: Boolean                                  = true
    override def setReadListener(readListener: ReadListener): Unit = {}
    override def read(): Int                                       = byteArrayInputStream.read()
  }
}
