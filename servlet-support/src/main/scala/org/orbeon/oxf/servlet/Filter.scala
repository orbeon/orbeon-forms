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

trait Filter {
  def init(filterConfig: FilterConfig): Unit
  def doFilter(request: ServletRequest, response: ServletResponse, chain: FilterChain): Unit
  def destroy(): Unit
}

trait JavaxOrJakartaFilter

class JavaxFilter(filter: Filter) extends javax.servlet.Filter with JavaxOrJakartaFilter {
  override def init(filterConfig: javax.servlet.FilterConfig): Unit =
    filter.init(FilterConfig(filterConfig))

  override def doFilter(request: javax.servlet.ServletRequest, response: javax.servlet.ServletResponse, chain: javax.servlet.FilterChain): Unit =
    filter.doFilter(ServletRequest(request), ServletResponse(response), FilterChain(chain))

  override def destroy(): Unit =
    filter.destroy()
}

class JakartaFilter(filter: Filter) extends jakarta.servlet.Filter with JavaxOrJakartaFilter {
  override def init(filterConfig: jakarta.servlet.FilterConfig): Unit =
    filter.init(FilterConfig(filterConfig))

  override def doFilter(request: jakarta.servlet.ServletRequest, response: jakarta.servlet.ServletResponse, chain: jakarta.servlet.FilterChain): Unit =
    filter.doFilter(ServletRequest(request), ServletResponse(response), FilterChain(chain))

  override def destroy(): Unit =
    filter.destroy()
}
