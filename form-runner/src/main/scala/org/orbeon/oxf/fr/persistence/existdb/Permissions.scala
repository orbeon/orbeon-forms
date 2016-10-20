package org.orbeon.oxf.fr.persistence.existdb

import org.orbeon.oxf.fr.FormRunner
import org.orbeon.oxf.fr.permission.PermissionsAuthorization.CheckWithDataUser
import org.orbeon.oxf.fr.permission._
import org.orbeon.oxf.fr.persistence.relational.crud.Organization
import org.orbeon.oxf.util.{IndentedLogger, LoggerFactory, NetUtils}
import org.orbeon.oxf.webapp.HttpStatusCodeException
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.XML._

object Permissions {

  def checkPermissions(
    app            : String,
    form           : String,
    metadataFromDB : NodeInfo,
    dataExists     : Boolean,
    method         : String
  ): Unit = {

    implicit val Logger = new IndentedLogger(LoggerFactory.createLogger(Permissions.getClass))

    val authorizedOperations = {
      val formMetadata = FormRunner.readFormMetadata(app, form).ensuring(_.isDefined, "can't find form metadata for data").get
      val permissionsElOrNull = (formMetadata / "forms" / "form" / "permissions").headOption.orNull
      val permissions = PermissionsXML.parse(permissionsElOrNull)
      val currentUser = PermissionsAuthorization.currentUserFromSession
      val checkWithDataUser = CheckWithDataUser(
        username     = Option(metadataFromDB).map(_.child("username" ).stringValue),
        groupname    = Option(metadataFromDB).map(_.child("groupname").stringValue),
        organization = Option(metadataFromDB).map(metadataEl ⇒ {
          val levelsEls = metadataEl.child("organization").child("level").map(_.stringValue)
          Organization(levelsEls.to[List])
        })
      )
      PermissionsAuthorization.authorizedOperations(permissions, currentUser, checkWithDataUser)
    }

    val requiredOperation = method match {
      case "GET"                ⇒ Read
      case "DELETE"             ⇒ Delete
      case "PUT" if dataExists  ⇒ Update
      case "PUT" if !dataExists ⇒ Create
    }

    val authorized = Operations.allows(authorizedOperations, requiredOperation)
    if (!authorized) throw HttpStatusCodeException(403)
    def httpResponse = NetUtils.getExternalContext.getResponse
    httpResponse.setHeader("Orbeon-Operations", Operations.serialize(authorizedOperations).mkString(" "))
  }

}
