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
import org.wildfly.security.http.oidc.{JsonWebToken, OidcSecurityContext}

import scala.jdk.CollectionConverters.*

object WildflyOidcAuth {

  private val OidcSecurityContextClassName = "org.wildfly.security.http.oidc.OidcSecurityContext"
  private val ObjectIDClaimName            = "oid"
  private val RolesClaimName               = "roles"

  def hasWildflyOidcAuth(getAttribute: String => AnyRef): Boolean =
    Option(getAttribute(OidcSecurityContextClassName)).isDefined

  def credentialsOpt(getAttribute: String => AnyRef): Option[Credentials] = {

    // Try retrieving claims from both the access and ID tokens. We're using functions instead of methods here to
    // prevent NoClassDefFoundError runtime errors when we're not in a WildFly environment.

    val getClaimValueFromToken = (token: JsonWebToken, claimName: String) =>
      Option(token.getClaimValue(claimName)).map(_.toString)

    val getClaimValue = (oidcSecurityContext: OidcSecurityContext, claimName: String) =>
      Option(oidcSecurityContext.getToken  ).flatMap(getClaimValueFromToken(_, claimName)) orElse
      Option(oidcSecurityContext.getIDToken).flatMap(getClaimValueFromToken(_, claimName))

    val getStringListClaimValueFromToken = (token: JsonWebToken, claimName: String) =>
      Option(token.getStringListClaimValue(claimName)).map(_.asScala.toList)

    val getStringListClaimValue = (oidcSecurityContext: OidcSecurityContext, claimName: String) =>
      Option(oidcSecurityContext.getToken  ).flatMap(getStringListClaimValueFromToken(_, claimName)) orElse
      Option(oidcSecurityContext.getIDToken).flatMap(getStringListClaimValueFromToken(_, claimName))

    Option(getAttribute(OidcSecurityContextClassName)) collect {
      case oidcSecurityContext: OidcSecurityContext =>

        val objectIdOpt = getClaimValue(oidcSecurityContext, ObjectIDClaimName)
        val objectId    = objectIdOpt.getOrElse(throw new RuntimeException(s"Claim '$ObjectIDClaimName' not found in OIDC token"))
        val roleIds     = getStringListClaimValue(oidcSecurityContext, RolesClaimName).getOrElse(Nil)

        Credentials(
          userAndGroup  = UserAndGroup(username = objectId, groupname = None),
          roles         = roleIds.map(SimpleRole.apply),
          organizations = Nil
        )
    }
  }
}
