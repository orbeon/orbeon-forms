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

import org.orbeon.oxf.fr.{Credentials, Organization, UserRole}
import org.orbeon.oxf.http.Headers
import org.orbeon.oxf.util.URLRewriterUtils


// Implementations shared between ServletExternalContext and Portlet2ExternalContext.
trait ServletPortletRequest extends ExternalContext.Request {

  protected def headerValuesMap: Map[String, Array[String]]

  lazy val credentials: Option[Credentials] =
    Headers.firstHeaderIgnoreCase(headerValuesMap, Headers.OrbeonUsername) map { username ⇒

      val roles =
        Headers.nonEmptyHeaderIgnoreCase(headerValuesMap, Headers.OrbeonRoles).to[List] flatMap
          (_ map UserRole.parse)

      val organization =
        Headers.nonEmptyHeaderIgnoreCase(headerValuesMap, Headers.OrbeonOrganization) filter
          (_.nonEmpty) map
          (_.to[List]) map
          Organization.apply

      Credentials(
        username     = username,
        roles        = roles,
        group        = Headers.firstHeaderIgnoreCase(headerValuesMap, Headers.OrbeonGroup),
        organization = organization
      )
    }

  def isUserInRole(role: String): Boolean =
    credentials exists (_.roles exists (_.roleName == role))

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
