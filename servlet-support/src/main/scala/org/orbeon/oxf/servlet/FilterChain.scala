/**
 * Copyright (C) 2023 Orbeon, Inc.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version
 * 2.1 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.servlet

object FilterChain {
  def apply(filterChain: javax.servlet.FilterChain): JavaxFilterChain     = new JavaxFilterChain(filterChain)
  def apply(filterChain: jakarta.servlet.FilterChain): JakartaFilterChain = new JakartaFilterChain(filterChain)
}

trait FilterChain {
  def doFilter(request: ServletRequest, response: ServletResponse): Unit
}

class JavaxFilterChain(filterChain: javax.servlet.FilterChain) extends FilterChain {
  override def doFilter(request: ServletRequest, response: ServletResponse): Unit =
    filterChain.doFilter(
      request.getNativeServletRequest.asInstanceOf[javax.servlet.ServletRequest],
      response.getNativeServletResponse.asInstanceOf[javax.servlet.ServletResponse]
    )
}

class JakartaFilterChain(filterChain: jakarta.servlet.FilterChain) extends FilterChain {
  override def doFilter(request: ServletRequest, response: ServletResponse): Unit =
    filterChain.doFilter(
      request.getNativeServletRequest.asInstanceOf[jakarta.servlet.ServletRequest],
      response.getNativeServletResponse.asInstanceOf[jakarta.servlet.ServletResponse]
    )
}
