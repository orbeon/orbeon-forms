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
package org.orbeon.oxf.fr.auth

import org.orbeon.oxf.externalcontext.SimpleRole
import org.slf4j.LoggerFactory

import scala.jdk.CollectionConverters.*
import scala.util.Try


class WildFlyOidcRolesProvider extends RolesProvider {

  private val logger = LoggerFactory.getLogger(getClass)

  private val OidcSecurityContextClassName = "org.wildfly.security.http.oidc.OidcSecurityContext"

  override def isApplicable: Boolean = {
    val wildFlyClassDetected = Try(Class.forName(OidcSecurityContextClassName)).isSuccess

    if (wildFlyClassDetected) {
      logger.info("WildFly OIDC authentication detected")
    } else {
      logger.debug("WildFly OIDC authentication not available")
    }

    wildFlyClassDetected
  }

  override def getRoles(getAttribute: String => AnyRef): List[SimpleRole] = {
    import org.wildfly.security.http.oidc.OidcSecurityContext

    List(getAttribute(OidcSecurityContextClassName)).collect {
      case oidcSecurityContext: OidcSecurityContext if oidcSecurityContext != null =>

        logger.debug("Found WildFly OIDC security context")

        // Retrieve the OIDC access token
        Option(oidcSecurityContext.getToken).toList.flatMap { accessToken =>

          // Retrieve the roles directly from the roles claim (e.g. Entra ID)
          val rolesFromAccessToken = for {
            rolesClaim <- Option(accessToken.getRolesClaim).toList
            role       <- rolesClaim.asScala.toList
          } yield role

          if (rolesFromAccessToken.nonEmpty) {
            logger.debug(s"Roles from access token: ${rolesFromAccessToken.size}")
          }

          // Retrieve the roles from the realm access claim (e.g. Keycloak)
          val rolesFromRealmAccessClaim = for {
            realmAccessClaim <- Option(accessToken.getRealmAccessClaim).toList
            roles            <- Option(realmAccessClaim.getRoles).toList
            role             <- roles.asScala.toList
          } yield role

          if (rolesFromRealmAccessClaim.nonEmpty) {
            logger.debug(s"Roles from realm access claim: ${rolesFromRealmAccessClaim.size}")
          }

          val allRoles = (rolesFromAccessToken ++ rolesFromRealmAccessClaim).distinct

          logger.debug(s"Total roles found in OIDC tokens: ${allRoles.size}")

          allRoles
        }.map(SimpleRole.apply)
    }.flatten
  }
}
