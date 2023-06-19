package org.orbeon.oxf.fr.persistence.proxy

import org.orbeon.oxf.externalcontext.{Credentials, Organization, UserAndGroup}
import org.orbeon.oxf.fr.{FormDefinitionVersion, FormRunner, FormRunnerAccessToken}
import org.orbeon.oxf.fr.FormRunnerParams.AppFormVersion
import org.orbeon.oxf.fr.permission.PermissionsAuthorization._
import org.orbeon.oxf.fr.permission._
import org.orbeon.oxf.fr.persistence.PersistenceMetadataSupport
import org.orbeon.oxf.fr.persistence.relational.{StageHeader, Version}
import org.orbeon.oxf.http.{Headers, HttpMethod, HttpStatusCodeException, StatusCode}
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.util.IndentedLogger
import org.orbeon.oxf.util.Logging.debug
import shapeless.syntax.typeable.typeableOps


object PersistenceProxyPermissions {

  case class ResponseHeaders(
    createdBy    : Option[UserAndGroup],
    organization : Option[(Int, Organization)],
    formVersion  : Option[Int],
    stage        : Option[String]
  )

  def permissionCheck(
    appFormVersion    : AppFormVersion,
    method            : HttpMethod.CrudMethod,
    documentId        : String,
    incomingTokenOpt  : Option[String],
    responseHeadersOpt: Option[ResponseHeaders])(implicit
    logger            : IndentedLogger
  ): Operations = {

    // TODO: Check possible optimization above to avoid retrieving form permissions twice.
    val formPermissions =
      FormRunner.permissionsFromElemOrProperties(
        PersistenceMetadataSupport.readFormPermissions(appFormVersion._1, FormDefinitionVersion.Specific(appFormVersion._2)),
        appFormVersion._1
      ) |!>
        (formPermissions => debug("CRUD: form permissions", List("permissions" -> formPermissions.toString)))

    PersistenceProxyPermissions.findAuthorizedOperationsOrThrow(
      formPermissions,
      findCurrentCredentialsFromSession,
      method,
      appFormVersion,
      documentId,
      incomingTokenOpt,
      responseHeadersOpt,
    )
  }

  private def findAuthorizedOperationsOrThrow(
    formPermissions   : Permissions,
    credentialsOpt    : Option[Credentials],
    crudMethod        : HttpMethod.CrudMethod,
    appFormVersion    : AppFormVersion,
    documentId        : String,
    incomingTokenOpt  : Option[String],
    responseHeadersOpt: Option[ResponseHeaders])(implicit
    logger            : IndentedLogger
  ): Operations = {

    val authorizedOpsOpt = {

      val authorizedOps =
        responseHeadersOpt match {
          case Some(responseHeaders) =>
            // Existing data

            val operationsFromData: Operations =
              authorizedOperations(
                formPermissions,
                credentialsOpt,
                CheckWithDataUser(
                  responseHeaders.createdBy,
                  responseHeaders.organization.map(_._2)
                )
              )

            // We might have additional operations from a token
            val operationsFromTokenOpt: Option[Operations] =
              for {
                token              <- incomingTokenOpt
                definedPermissions <- formPermissions.narrowTo[Permissions.Defined]
              } yield
                operationsFromToken(
                  definedPermissions = definedPermissions,
                  token              = token,
                  tokenHmac          = FormRunnerAccessToken.TokenHmac(appFormVersion._1.app, appFormVersion._1.form, appFormVersion._2, Some(documentId))
                ).getOrElse(throw HttpStatusCodeException(StatusCode.Forbidden))

            // Operations obtained without consideration for the data
            val otherOperations: Operations =
             authorizedOperations(
                formPermissions,
                credentialsOpt,
                CheckWithoutDataUserPessimistic
              )

            // Those are combined
            // For example: the data might grand only `Read` but the token might grant `Update` in addition
            Operations.combine(operationsFromData :: otherOperations :: operationsFromTokenOpt.toList)

          case None =>
            // Non-existing data
            if (crudMethod == HttpMethod.DELETE)
              // For deletes, if there is no data to delete, it unclear we think a 404 is clearer than a 403, so
              // let this check pass, and some code later will return a 404
              SpecificOperations(Set(Operation.Delete))
            else {
              // `PUT` and no data, so this is a creation
              authorizedOperationsForNoData(
                formPermissions,
                credentialsOpt
              )
            }
        }

      val requestedOp =
        crudMethod match {
          case HttpMethod.GET | HttpMethod.HEAD             => Operation.Read
          case HttpMethod.PUT if responseHeadersOpt.isEmpty => Operation.Create
          case HttpMethod.PUT                               => Operation.Update
          case HttpMethod.DELETE                            => Operation.Delete
        }

      val authorized =
        Operations.allows(authorizedOps, requestedOp)

      authorized option authorizedOps
    }

    authorizedOpsOpt match {
      case Some(authorizedOps) =>
        debug("CRUD: authorized", List("operations" -> Operations.serialize(authorizedOps, normalized = true).mkString(" ")))
        authorizedOps
      case None =>
        debug("CRUD: not authorized", List("status code" -> StatusCode.Forbidden.toString))
        throw HttpStatusCodeException(StatusCode.Forbidden)
    }
  }

  def extractResponseHeaders(getHeaderIgnoreCase: String => Option[String]): ResponseHeaders = {

    def ownerUserAndGroupFromHeadersOpt: Option[UserAndGroup] =
      getHeaderIgnoreCase(Headers.OrbeonUsername).map { username =>
        UserAndGroup(username, getHeaderIgnoreCase(Headers.OrbeonGroup))
      }

    ResponseHeaders(
      createdBy    = ownerUserAndGroupFromHeadersOpt,
      organization = None, // TODO
      formVersion  = getHeaderIgnoreCase(Version.OrbeonFormDefinitionVersion).map(_.toInt),
      stage        = getHeaderIgnoreCase(StageHeader.HeaderName)
    )
  }
}
