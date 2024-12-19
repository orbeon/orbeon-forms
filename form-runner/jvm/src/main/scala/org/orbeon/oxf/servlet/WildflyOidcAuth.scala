/**
 * Copyright (C) 2024 Orbeon, Inc.
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

import org.orbeon.oxf.externalcontext.{Credentials, SimpleRole, UserAndGroup}
import org.wildfly.security.http.oidc.OidcSecurityContext

import scala.jdk.CollectionConverters.*

object WildflyOidcAuth {

  private val OidcSecurityContextClassName = "org.wildfly.security.http.oidc.OidcSecurityContext"
  private val ObjectIDClaimName            = "oid"

  def hasWildflyOidcAuth(servletRequest: HttpServletRequest): Boolean =
    Option(servletRequest.getAttribute(OidcSecurityContextClassName)).isDefined

  def credentialsOpt(servletRequest: HttpServletRequest): Option[Credentials] =
    Option(servletRequest.getAttribute(OidcSecurityContextClassName)) collect {
      case oidcSecurityContext: OidcSecurityContext =>

        val objectIdOpt = Option(oidcSecurityContext.getToken.getClaimValue(ObjectIDClaimName)).map(_.toString)
        val objectId    = objectIdOpt.getOrElse(throw new RuntimeException(s"Claim '$ObjectIDClaimName' not found in OIDC token"))
        val roleIds     = oidcSecurityContext.getToken.getRolesClaim.asScala.toList

        Credentials(
          userAndGroup  = UserAndGroup(username = objectId, groupname = None),
          roles         = roleIds.map(SimpleRole.apply),
          organizations = Nil
        )
    }
}
