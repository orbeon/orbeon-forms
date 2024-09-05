package org.orbeon.oxf.servlet

import org.orbeon.io.CharsetNames
import org.orbeon.oxf.util.{NetUtils, PathUtils}
import org.slf4j.{Logger, LoggerFactory}

import java.io.{ByteArrayInputStream, InputStream}


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
class HttpLoggingFilter extends JavaxHttpLoggingFilter

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

    val pathQuery =
      PathUtils.appendQueryString(
        httpRequest.getRequestPathInfo,
        httpRequest.getQueryString
      )

    val requestBody        = new String(byteArray, CharsetNames.Utf8)
    val inputStream        = new ByteArrayInputStream(byteArray)
    val servletInputStream = new HttpLoggingFilter.ByteArrayServletInputStream(inputStream)
    val wrappedRequest     = new HttpLoggingFilter.LoggerRequestWrapper(httpRequest, servletInputStream)

    Logger.info(s"request path and query: `$pathQuery`")
    Logger.info(s"request headers:\n${httpRequest.headersAsString}")
    Logger.info(s"request body:\n`$requestBody`")

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
