package org.orbeon.oxf.fr.existdb

import org.orbeon.oxf.fr.FormRunner
import org.orbeon.oxf.util.{NetUtils, IndentedLogger, LoggerFactory}
import org.orbeon.oxf.webapp.HttpStatusCodeException
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.XML._

object Permissions {

    def checkPermissions(app: String, form: String, metadataFromDB: NodeInfo, dataExists: Boolean, method: String): Unit = {

        implicit val Logger = new IndentedLogger(LoggerFactory.createLogger(Permissions.getClass), "")

        val authorizedOperations = {

            // TODO: this code is in part duplicated from authorizedOperations.authorizedOperations(); fix this when we rewrite eXist's crud.xpl in Scala
            val formMetadata = FormRunner.readFormMetadata(app, form).ensuring(_.isDefined, "can't find form metadata for data").get
            val formPermissions = (formMetadata / "forms" / "form" / "permissions").headOption
            formPermissions match {
                case None ⇒ Set("create", "read", "update", "delete")
                case Some(permissionsEl) ⇒
                    if (metadataFromDB != null) {
                        def dataUserGroupName(elName: String): Option[String] = {
                            val elStringValue = metadataFromDB.child(elName) .stringValue
                            if (elStringValue == "") None else Some(elStringValue)
                        }
                        val dataUsername  = dataUserGroupName("username")
                        val dataGroupname = dataUserGroupName("groupname")
                        FormRunner.allAuthorizedOperations(permissionsEl, dataUsername, dataGroupname).toSet
                    } else {
                        FormRunner.authorizedOperationsBasedOnRoles(permissionsEl).toSet
                    }
            }
        }

        val authorized = method match {
            case "GET"                    ⇒ authorizedOperations("read")
            case "DELETE"                 ⇒ authorizedOperations("delete")
            case "PUT"    if dataExists   ⇒ authorizedOperations("update")
            case "PUT"    if ! dataExists ⇒ authorizedOperations("create")
        }

        if (! authorized) throw HttpStatusCodeException(403)
        def httpResponse = NetUtils.getExternalContext.getResponse
        httpResponse.setHeader("Orbeon-Operations", authorizedOperations.mkString(" "))
    }

}
