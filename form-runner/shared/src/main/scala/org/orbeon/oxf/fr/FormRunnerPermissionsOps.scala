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
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.util.{CoreCrossPlatformSupport, IndentedLogger}
import org.orbeon.oxf.xforms.action.XFormsAPI.inScopeContainingDocument
import org.orbeon.oxf.xforms.action.XFormsActions
import org.orbeon.saxon.om.NodeInfo


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
  def authorizedOperationsForDetailModeOrThrow(
    operationsFromPersistence              : String,
    encryptedOperationsFromModeChangeOrNull: String,
    permissionsElemOrNull                  : NodeInfo,
    isSubmit                               : Boolean
  ): String = {

    // Same logger that was used for the `xf:message` action before (could use something else)
    implicit val logger: IndentedLogger =
      inScopeContainingDocument.getIndentedLogger(XFormsActions.LoggingCategory)

    val formRunnerParams = FormRunnerParams()

    // In initial edit/view modes that read data from the database, we use `operationsFromData` which is the result of
    // headers returned by the the database `GET`. When we change modes from there, we propagate and use
    // `encryptedOperationsFromData` instead.
    val modeTypeAndOps =
      formRunnerParams.modeType match {
        case modeType @ (ModeType.Edition | ModeType.Readonly) =>
          val ops =
            Operations.parseFromString(operationsFromPersistence)
              .orElse(
                if (isSubmit)
                  encryptedOperationsFromModeChangeOrNull.trimAllToOpt match {
                    case Some(encrypted) =>
                      FormRunnerOperationsEncryption.decryptOperations(encrypted)
                    case None if modeType == ModeType.Readonly =>
                      Some(SpecificOperations(Set(Operation.Read))) // case of a `POST` to `view` mode!
                    case _ =>
                      None
                  }
                else
                  None
              )
          ModeTypeAndOps.Other(modeType, ops.getOrElse(throw new IllegalStateException))
        case ModeType.Creation =>
          ModeTypeAndOps.Creation
      }

    Operations.serialize(
      PermissionsAuthorization.authorizedOperationsForDetailModeOrThrow(
        modeTypeAndOps        = modeTypeAndOps,
        permissions           = permissionsFromElemOrProperties(Option(permissionsElemOrNull), formRunnerParams.appForm),
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

  //@XPathFunction
  def encryptParameterIfNeeded(parameterValue: String): String =
    parameterValue.trimAllToOpt.map(FormRunnerOperationsEncryption.encryptString).getOrElse("")

  //@XPathFunction
  def decryptParameterIfNeeded(parameterValue: String): String =
    parameterValue.trimAllToOpt.flatMap(FormRunnerOperationsEncryption.decryptString).getOrElse("")
}

object FormRunnerPermissionsOps extends FormRunnerPermissionsOps