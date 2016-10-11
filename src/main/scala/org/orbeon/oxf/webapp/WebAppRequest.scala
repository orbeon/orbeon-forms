/**
  * Copyright (C) 2016 Orbeon, Inc.
  *
  * This program is free software; you can redistribute it and/or modify it under the terms of the
  * GNU Lesser General Public License as published by the Free Software Foundation; either version
  *  2.1 of the License, or (at your option) any later version.
  *
  * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  * See the GNU Lesser General Public License for more details.
  *
  * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
  */
package org.orbeon.oxf.webapp

import org.orbeon.oxf.fr.UserRole
import org.orbeon.oxf.http.Headers
import org.orbeon.oxf.pipeline.api.ExternalContext
import org.orbeon.oxf.util.URLRewriterUtils


// Implementations shared between ServletExternalContext and Portlet2ExternalContext.
trait WebAppRequest extends ExternalContext.Request {

  protected def headerValuesMap: Map[String, Array[String]]

  def getUsername  : String = Headers.firstHeaderIgnoreCase(headerValuesMap, Headers.OrbeonUsername).orNull
  def getUserGroup : String = Headers.firstHeaderIgnoreCase(headerValuesMap, Headers.OrbeonGroup).orNull
  def getUserOrganization   = null

  lazy val getUserRoles: Array[UserRole] =
    Headers.nonEmptyHeaderIgnoreCase(headerValuesMap, Headers.OrbeonRoles) match {
      case Some(headers) ⇒ headers map UserRole.parse
      case None          ⇒ Array.empty[UserRole]
    }

  def isUserInRole(role: String): Boolean =
    getUserRoles exists (_.roleName == role)

  def getClientContextPath(urlString: String): String =
    if (URLRewriterUtils.isPlatformPath(urlString))
      platformClientContextPath
    else
      applicationClientContextPath

  private lazy val platformClientContextPath: String =
    URLRewriterUtils.getClientContextPath(this, true)

  private lazy val applicationClientContextPath: String =
    URLRewriterUtils.getClientContextPath(this, false)
}
