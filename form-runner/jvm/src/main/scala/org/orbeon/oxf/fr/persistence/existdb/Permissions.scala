/**
 * Copyright (C) 2016 Orbeon, Inc.
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
package org.orbeon.oxf.fr.persistence.existdb

import org.orbeon.oxf.externalcontext.{Organization, UserAndGroup}
import org.orbeon.oxf.fr.permission.Operation.{Create, Delete, Read, Update}
import org.orbeon.oxf.fr.permission.PermissionsAuthorization.CheckWithDataUser
import org.orbeon.oxf.fr.permission._
import org.orbeon.oxf.fr.persistence.relational.RelationalUtils
import org.orbeon.oxf.fr.{AppForm, FormDefinitionVersion, FormRunnerPersistence}
import org.orbeon.oxf.http.{HttpStatusCodeException, StatusCode}
import org.orbeon.oxf.util.{IndentedLogger, LoggerFactory, NetUtils}
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.SimplePath._

import scala.collection.compat._


object Permissions {

  //@XPathFunction
  def checkPermissions(
    app            : String,
    form           : String,
    metadataFromDB : NodeInfo,
    dataExists     : Boolean,
    method         : String
  ): Unit = {

    implicit val Logger = new IndentedLogger(LoggerFactory.createLogger(Permissions.getClass))

    val authorizedOperations = {
      val permissionsElemOpt    = RelationalUtils.readFormPermissions(AppForm(app, form), FormDefinitionVersion.Latest)
      val permissions           = PermissionsXML.parse(permissionsElemOpt)
      val currentCredentialsOpt = PermissionsAuthorization.findCurrentCredentialsFromSession
      val checkWithDataUser     = CheckWithDataUser(
        userAndGroup = UserAndGroup.fromStrings(
          Option(metadataFromDB).map(_.child("username" ).stringValue).getOrElse(""),
          Option(metadataFromDB).map(_.child("groupname").stringValue).getOrElse("")
        ),
        organization = Option(metadataFromDB).map(metadataEl => {
          val levelsEls = metadataEl.child("organization").child("level").map(_.stringValue)
          Organization(levelsEls.to(List))
        })
      )
      PermissionsAuthorization.authorizedOperations(permissions, currentCredentialsOpt, checkWithDataUser)
    }

    val requiredOperation = method match {
      case "GET"                => Read
      case "DELETE"             => Delete
      case "PUT" if dataExists  => Update
      case "PUT" if !dataExists => Create
    }

    val authorized = Operations.allows(authorizedOperations, requiredOperation)
    if (!authorized) throw HttpStatusCodeException(StatusCode.Forbidden)
    def httpResponse = NetUtils.getExternalContext.getResponse
    httpResponse.setHeader(
      FormRunnerPersistence.OrbeonOperations,
      Operations.serialize(authorizedOperations, normalized = true).mkString(" ")
    )
  }
}
