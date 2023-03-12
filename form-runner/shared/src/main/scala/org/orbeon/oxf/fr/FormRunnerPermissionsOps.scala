/**
 * Copyright (C) 2013 Orbeon, Inc.
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
package org.orbeon.oxf.fr

import org.orbeon.oxf.fr.FormRunnerCommon.frc
import org.orbeon.oxf.fr.permission._
import org.orbeon.oxf.util.{CoreCrossPlatformSupport, IndentedLogger}
import org.orbeon.oxf.xforms.action.XFormsAPI.inScopeContainingDocument
import org.orbeon.oxf.xforms.action.XFormsActions
import org.orbeon.saxon.om.{NodeInfo, SequenceIterator}
import org.orbeon.scaxon.Implicits._


trait FormRunnerPermissionsOps {

  def permissionsFromElemOrProperties(
    permissionsElemOpt: Option[NodeInfo],
    appForm           : AppForm
  ): Permissions =
    permissionsElemOpt match {
      case some @ Some(_) =>
        // If the element is defined, even if nothing else is present, properties will *not* be used
        PermissionsXML.parse(some)
      case None =>
        // Try app/form properties
        frc.formRunnerProperty("oxf.fr.permissions", appForm) match {
          case Some(value) => PermissionsJSON.parseString(value).get // will throw if there is an error in the format of the property
          case None        => UndefinedPermissions
        }
    }

  // 2023-03-08: Used by Summary and legacy eXist code
  //@XPathFunction
  def authorizedOperationsBasedOnRolesXPath(permissionsElOrNull: NodeInfo, app: String, form: String): List[String] =
    Operations.serialize(
      PermissionsAuthorization.authorizedOperations(
        permissionsFromElemOrProperties(
          Option(permissionsElOrNull),
          AppForm(app, form)
        ),
        CoreCrossPlatformSupport.externalContext.getRequest.credentials,
        PermissionsAuthorization.CheckWithoutDataUserPessimistic
      ),
      normalized = true
    )

  //@XPathFunction
  def autosaveAuthorizedForNew(permissionsElOrNull: NodeInfo, app: String, form: String): Boolean =
    PermissionsAuthorization.autosaveAuthorizedForNew(
      permissions    = permissionsFromElemOrProperties(Option(permissionsElOrNull), AppForm(app, form)),
      credentialsOpt = PermissionsAuthorization.findCurrentCredentialsFromSession
    )

  //@XPathFunction
  def authorizedOperationsForDetailModeOrThrow(operationsFromData: String, permissionsElemOrNull: NodeInfo, isSubmit: Boolean): String = {

    // Same logger that was used for the `xf:message` action before (could use something else)
    implicit val logger: IndentedLogger =
      inScopeContainingDocument.getIndentedLogger(XFormsActions.LoggingCategory)

    val FormRunnerParams(app, form, _, _, _, mode) = FormRunnerParams()

    Operations.serialize(
      PermissionsAuthorization.authorizedOperationsForDetailModeOrThrow(
        mode                  = mode,
        permissions           = permissionsFromElemOrProperties(Option(permissionsElemOrNull), AppForm(app, form)),
        operationsFromDataOpt = Operations.parseFromString(operationsFromData),
        credentialsOpt        = PermissionsAuthorization.findCurrentCredentialsFromSession,
        isSubmit              = isSubmit
      ),
      normalized = true
    ).mkString(" ")
  }

  def orbeonRolesFromCurrentRequest: Set[String] =
    CoreCrossPlatformSupport.externalContext.getRequest.credentials.toList.flatMap(_.roles).map(_.roleName).toSet

  //@XPathFunction
  def redirectToHomePageIfLoggedIn(): Unit = {
    val request  = CoreCrossPlatformSupport.externalContext.getRequest
    val response = CoreCrossPlatformSupport.externalContext.getResponse
    if (request.credentials.isDefined) {
      response.sendRedirect(
        response.rewriteRenderURL("/fr/"),
        isServerSide = false,
        isExitPortal = false
      )
    }
  }
}

object FormRunnerPermissionsOps extends FormRunnerPermissionsOps