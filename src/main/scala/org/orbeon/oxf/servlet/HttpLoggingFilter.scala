package org.orbeon.oxf.servlet

import org.orbeon.io.CharsetNames
import org.orbeon.oxf.util.NetUtils
import org.orbeon.oxf.webapp.ServletSupport
import org.slf4j.{Logger, LoggerFactory}

import java.io.{ByteArrayInputStream, InputStream}
import scala.jdk.CollectionConverters._


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

// For backward compatibility
trait HttpLoggingFilter extends JavaxHttpLoggingFilter

class JavaxHttpLoggingFilter   extends JavaxFilter  (new HttpLoggingFilterImpl)
class JakartaHttpLoggingFilter extends JakartaFilter(new HttpLoggingFilterImpl)

class HttpLoggingFilterImpl extends Filter {

  val Logger: Logger = LoggerFactory.getLogger("org.orbeon.filter.http-logging")

  override def doFilter(
    servletRequest  : ServletRequest,
    servletResponse : ServletResponse,
    chain           : FilterChain
  ): Unit = {

    val httpRequest        = servletRequest.asInstanceOf[HttpServletRequest]
    val byteArray          = NetUtils.inputStreamToByteArray(httpRequest.getInputStream)
    val requestPath        = ServletSupport.getRequestPathInfo(httpRequest)
    val requestBody        = new String(byteArray, CharsetNames.Utf8)
    val inputStream        = new ByteArrayInputStream(byteArray)
    val servletInputStream = new HttpLoggingFilter.ByteArrayServletInputStream(inputStream)
    val wrappedRequest     = new HttpLoggingFilter.LoggerRequestWrapper(httpRequest, servletInputStream)

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
  override def init(config: FilterConfig): Unit = ()
  override def destroy(): Unit = ()
}

private object HttpLoggingFilter {

  class LoggerRequestWrapper(httpServletRequest: HttpServletRequest, servletInputStream: InputStream)
      extends HttpServletRequestWrapper(httpServletRequest) {
    override def getInputStream: InputStream = servletInputStream
  }

  class ByteArrayServletInputStream(byteArrayInputStream: ByteArrayInputStream)
      extends InputStream {
    def read(): Int = byteArrayInputStream.read()
  }
}
