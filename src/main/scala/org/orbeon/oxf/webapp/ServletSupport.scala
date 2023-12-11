package org.orbeon.oxf.webapp

import org.orbeon.oxf.servlet.HttpServletRequest
import org.orbeon.oxf.util.PathUtils._


object ServletSupport {

  /**
   * Return a request path info that looks like what one would expect. The path starts with a "/", relative to the
   * servlet context. If the servlet was included or forwarded to, return the path by which the *current* servlet was
   * invoked, NOT the path of the calling servlet.
   *
   * Request path = servlet path + path info.
   *
   * @param request servlet HTTP request
   * @return path
   */
  def getRequestPathInfo(request: HttpServletRequest): String = {

    // NOTE: Servlet 2.4 spec says: "These attributes [javax.servlet.include.*] are accessible from the included
    // servlet via the getAttribute method on the request object and their values must be equal to the request URI,
    // context path, servlet path, path info, and query string of the included servlet, respectively."
    // NOTE: This is very different from the similarly-named forward attributes, which reflect the values of the
    // first servlet in the chain!

    val servletPath =
      Option(request.getAttribute("javax.servlet.include.servlet_path").asInstanceOf[String])
        .orElse(Option(request.getServletPath))
        .getOrElse("")

    val pathInfo =
      Option(request.getAttribute("javax.servlet.include.path_info").asInstanceOf[String])
        .orElse(Option(request.getPathInfo))
        .getOrElse("")

    val requestPath =
      if (servletPath.endsWith("/") && pathInfo.startsWith("/"))
        servletPath + pathInfo.dropStartingSlash
      else
        servletPath + pathInfo

    requestPath.prependSlash
  }
}
