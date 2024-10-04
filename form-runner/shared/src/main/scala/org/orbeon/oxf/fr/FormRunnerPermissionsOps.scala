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
import org.orbeon.oxf.fr.definitions.{ModeType, ModeTypeAndOps}
import org.orbeon.oxf.fr.permission.*
import org.orbeon.oxf.fr.persistence.api.PersistenceApi
import org.orbeon.oxf.fr.process.FormRunnerExternalMode
import org.orbeon.oxf.util.StringUtils.*
import org.orbeon.oxf.util.{CoreCrossPlatformSupport, IndentedLogger}
import org.orbeon.oxf.xforms.action.XFormsAPI.inScopeContainingDocument
import org.orbeon.oxf.xforms.action.XFormsActions
import org.orbeon.oxf.xforms.function.XFormsFunction
import org.orbeon.saxon.om.NodeInfo


trait FormRunnerPermissionsOps extends FormRunnerPlatform {

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
        frc.formRunnerRawProperty("oxf.fr.permissions", appForm) match {
          case Some(p) if p.stringValue.nonAllBlank =>
            p.associatedValue { _ =>
              PermissionsJSON.parseString(p.stringValue).get // will throw if there is an error in the format of the property
            }
          case _ => Permissions.Undefined
        }
    }

  //@XPathFunction
  def possiblyAllowedTokenOperations(permissionsElOrNull: NodeInfo, app: String, form: String, authorizedOperations: String): String =
    Operations.serialize(
      PermissionsAuthorization.possiblyAllowedTokenOperations(
        permissionsFromElemOrProperties(
          Option(permissionsElOrNull),
          AppForm(app, form)
        ),
        Operations.parseFromString(authorizedOperations) collect {
          case SpecificOperations(operations) => operations
        }
      ),
      normalized = true
    ).mkString(" ")

  // 2024-12-02: Used by the Summary page.
  //@XPathFunction
  def authorizedOperationsBasedOnRolesXPath(permissionsElOrNull: NodeInfo, app: String, form: String): List[String] = {
    implicit val logger: IndentedLogger =
      inScopeContainingDocument.getIndentedLogger(XFormsActions.LoggingCategory)
    Operations.serialize(
      PermissionsAuthorization.authorizedOperationsForSummary(
        permissionsFromElemOrProperties(
          Option(permissionsElOrNull),
          AppForm(app, form)
        ),
        CoreCrossPlatformSupport.externalContext.getRequest.credentials
      ),
      normalized = true
    )
  }

  //@XPathFunction
  def autosaveAuthorizedForNew(permissionsElOrNull: NodeInfo, app: String, form: String): Boolean = {
    implicit val logger: IndentedLogger =
      inScopeContainingDocument.getIndentedLogger(XFormsActions.LoggingCategory)
    PermissionsAuthorization.autosaveAuthorizedForNew(
      permissions    = permissionsFromElemOrProperties(Option(permissionsElOrNull), AppForm(app, form)),
      credentialsOpt = PermissionsAuthorization.findCurrentCredentialsFromSession
    )
  }

  //@XPathFunction
  def authorizedOperationsForDetailModeOrThrow(
    operationsFromPersistence         : String,
    encryptedPrivateModeMetadataOrNull: String,
    permissionsElemOrNull             : NodeInfo,
    isSubmit                          : Boolean
  ): String = {

    // Same logger that was used for the `xf:message` action before (could use something else)
    implicit val logger: IndentedLogger =
      inScopeContainingDocument.getIndentedLogger(XFormsActions.LoggingCategory)

    implicit val formRunnerParams = FormRunnerParams()

    // In initial edit/view modes that read data from the database, we use `operationsFromData` which is the result of
    // headers returned by the database `GET`. When we change modes from there, we propagate and use
    // `encryptedOperationsFromData` instead.
    val modeTypeAndOpsOpt =
      formRunnerParams.modeType(frc.customModes) match {
        case modeType: ModeType.ForExistingData =>
          val opsOpt =
            Operations.parseFromString(operationsFromPersistence)
              .orElse(
                if (isSubmit)
                  decryptPrivateModeOperations(encryptedPrivateModeMetadataOrNull.trimAllToOpt)
                else
                  None
              )
          opsOpt.map(ModeTypeAndOps(modeType, _))
        case ModeType.Creation =>
          None
      }

    Operations.serialize(
      modeTypeAndOpsOpt match {
        case Some(modeTypeAndOps) =>
          PermissionsAuthorization.authorizedOperationsForDetailModeOrThrow(
            modeTypeAndOps = modeTypeAndOps,
            isSubmit       = isSubmit
          )
        case None =>
          // This means that we found operations associated with data neither from persistence nor from mode change.
          // The scenario is an external `POST` to `edit`/`view`. In that case, we fall back to permissions without
          // checking the data.
          PermissionsAuthorization.authorizedOperationsForNoDataOrThrow(
            permissions    = permissionsFromElemOrProperties(Option(permissionsElemOrNull), formRunnerParams.appForm),
            credentialsOpt = PermissionsAuthorization.findCurrentCredentialsFromSession,
          )
      },
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

  //@XPathFunction
  def encryptParameterIfNeeded(parameterValue: String): String =
    parameterValue.trimAllToOpt.map(FormRunnerOperationsEncryption.encryptString).getOrElse("")

  //@XPathFunction
  def createInternalAdminTokenParam(): String =
    PersistenceApi.createInternalAdminUserToken(XFormsFunction.context.containingDocument.getRequestPath).orNull

  //@XPathFunction
  def decryptParameterIfNeeded(parameterValue: String): String =
    parameterValue.trimAllToOpt.flatMap(FormRunnerOperationsEncryption.decryptString).getOrElse("")
}
