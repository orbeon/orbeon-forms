/**
 * Copyright (C) 2025 Orbeon, Inc.
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

import java.util.{ServiceConfigurationError, ServiceLoader}
import scala.jdk.CollectionConverters.*


object RolesProviderFactory {

  private val logger = LoggerFactory.getLogger(getClass)

  private lazy val providers: List[RolesProvider] = {
    try {
      val loader              = ServiceLoader.load(classOf[RolesProvider])
      val applicableProviders = loader.asScala.toList.filter { provider =>
        // If a roles provider throws an exception, it will be considered non-applicable
        catchAndLogExceptions(provider, ifException = false)(provider.isApplicable)
      }

      logger.debug(
        s"Roles providers loaded (${applicableProviders.size}): ${applicableProviders.map(_.getClass.getName).mkString(", ")}"
      )

      applicableProviders
    } catch {
      case e: ServiceConfigurationError =>
        logger.error(s"Error loading roles providers: ${e.getMessage}")
        List.empty
    }
  }

  def getRoles(getAttribute: String => AnyRef): List[SimpleRole] = {
    val allRoles = providers.flatMap { provider =>
      catchAndLogExceptions(provider, ifException = List.empty[SimpleRole])(provider.getRoles(getAttribute))
    }.distinct

    logger.debug(s"Roles found in roles providers (${allRoles.size}): ${allRoles.map(_.roleName).mkString(", ")}")

    allRoles
  }

  private def catchAndLogExceptions[T](rolesProvider: RolesProvider, ifException: => T)(body: => T): T =
    try {
      body
    } catch {
      case e: NoClassDefFoundError =>
        logger.debug(s"Roles provider ${rolesProvider.getClass.getName} missing dependencies: ${e.getMessage}")
        ifException
      case e: ClassNotFoundException =>
        logger.debug(s"Roles provider ${rolesProvider.getClass.getName} missing classes: ${e.getMessage}")
        ifException
      case e: Exception =>
        logger.error(s"Error in roles provider ${rolesProvider.getClass.getName}: ${e.getMessage}")
        ifException
    }
}