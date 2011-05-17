/**
 * Copyright (C) 2011 Orbeon, Inc.
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

import collection.JavaConversions._
import javax.servlet.http.{HttpServletRequestWrapper, HttpServletRequest}
import org.orbeon.oxf.fr.FormRunner
import java.util.{Enumeration => JEnumeration}

/**
 * This filter adds the Orbeon-Username and Orbeon-Roles to the request headers.
 */
class FormRunnerRequestFilter extends RequestFilter {
    def amendRequest(servletRequest: HttpServletRequest) = {

        def getHeader(name: String) = servletRequest.getHeaders(name).asInstanceOf[JEnumeration[String]].toArray match {
            case Array() => None
            case array => Some(array)
        }

        val headers = FormRunner.getUserRolesAsHeaders(servletRequest, getHeader)

        trait CustomHeaders extends HttpServletRequestWrapper {
            override def getHeaderNames =
                headers.keysIterator ++ super.getHeaderNames
            override def getHeader(name: String) =
                headers.get(name) map (_.head) getOrElse super.getHeader(name)
            override def getHeaders(name: String): JEnumeration[_] =
                headers.get(name) map (n => asJavaEnumeration(n.iterator)) getOrElse super.getHeaders(name)
        }

        new HttpServletRequestWrapper(servletRequest) with CustomHeaders
    }
}