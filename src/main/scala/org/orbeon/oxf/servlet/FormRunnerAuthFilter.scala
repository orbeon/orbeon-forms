/**
 * Copyright (C) 2015 Orbeon, Inc.
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

import javax.servlet._
import javax.servlet.http.{HttpServletRequest, HttpServletRequestWrapper}

import org.orbeon.oxf.fr.FormRunnerAuth

import scala.collection.JavaConversions._

class FormRunnerAuthFilter extends Filter {
    
    def doFilter(req: ServletRequest, res: ServletResponse, chain: FilterChain) =
        chain.doFilter(FormRunnerAuthFilter.amendRequest(req.asInstanceOf[HttpServletRequest]), res)

    def init(filterConfig: FilterConfig) = ()
    def destroy() = ()
}

object FormRunnerAuthFilter {

    import java.util.{Enumeration ⇒ JEnumeration}

    def amendRequest(servletRequest: HttpServletRequest) = {

        def getHeader(name: String) = servletRequest.getHeaders(name).asInstanceOf[JEnumeration[String]].toArray match {
            case Array() ⇒ None
            case array   ⇒ Some(array)
        }

        val headers = FormRunnerAuth.getUserGroupRolesAsHeaders(servletRequest, getHeader).toMap

        trait CustomHeaders extends HttpServletRequestWrapper {
            override def getHeaderNames =
                headers.keysIterator ++ super.getHeaderNames
            override def getHeader(name: String) =
                headers.get(name) map (_.head) getOrElse super.getHeader(name)
            override def getHeaders(name: String): JEnumeration[_] =
                headers.get(name) map (n ⇒ asJavaEnumeration(n.iterator)) getOrElse super.getHeaders(name)
        }

        new HttpServletRequestWrapper(servletRequest) with CustomHeaders
    }
}