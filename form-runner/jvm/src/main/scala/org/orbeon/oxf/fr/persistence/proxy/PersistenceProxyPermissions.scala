package org.orbeon.oxf.fr.persistence.proxy

import org.orbeon.oxf.externalcontext.{Credentials, Organization, UserAndGroup}
import org.orbeon.oxf.fr.FormRunnerParams.AppFormVersion
import org.orbeon.oxf.fr.permission.PermissionsAuthorization.*
import org.orbeon.oxf.fr.permission.*
import org.orbeon.oxf.fr.persistence.relational.StageHeader
import org.orbeon.oxf.fr.{FormRunnerAccessToken, Version}
import org.orbeon.oxf.http.{Headers, HttpMethod, HttpStatusCodeException, StatusCode}
import org.orbeon.oxf.util.CoreUtils.*
import org.orbeon.oxf.util.{DateUtils, IndentedLogger}
import org.orbeon.oxf.util.Logging.*
import shapeless.syntax.typeable.typeableOps

import java.time.Instant
import scala.util.Try


object PersistenceProxyPermissions {

  case class ResponseHeaders(
    createdBy    : Option[UserAndGroup],
    createdTime  : Option[Instant],
    organization : Option[(Int, Organization)],
    formVersion  : Option[Int],
    stage        : Option[String],
    etag         : Option[String]
  )

  object ResponseHeaders {

    def fromHeaders(getHeaderIgnoreCase: String => Option[String]): ResponseHeaders = {

      def ownerUserAndGroupFromHeadersOpt: Option[UserAndGroup] =
        getHeaderIgnoreCase(Headers.OrbeonUsername).map { username =>
          UserAndGroup(username, getHeaderIgnoreCase(Headers.OrbeonGroup))
        }

      ResponseHeaders(
        createdBy    = ownerUserAndGroupFromHeadersOpt,
        createdTime  = getHeaderIgnoreCase(Headers.OrbeonCreated).map(Instant.parse),
        organization = None, // TODO
        formVersion  = getHeaderIgnoreCase(Version.OrbeonFormDefinitionVersion).map(_.toInt),
        stage        = getHeaderIgnoreCase(StageHeader.HeaderName),
        etag         = getHeaderIgnoreCase(Headers.ETag)
      )
    }

    def toHeaders(responseHeaders: ResponseHeaders): List[(String, String)] = {

      def headerValueIgnoreCaseExisting(name: String, value: String): (String, String) =
        s"$name-Existing" -> value

      responseHeaders.createdBy.map(v => headerValueIgnoreCaseExisting(Headers.OrbeonUsername, v.username)).toList            ++
        responseHeaders.createdBy.flatMap(_.groupname).map(v => headerValueIgnoreCaseExisting(Headers.OrbeonGroup, v)).toList ++
        responseHeaders.createdTime.map(v => headerValueIgnoreCaseExisting(Headers.OrbeonCreated, DateUtils.formatIsoDateTimeUtc(v))).toList
        //responseHeaders.organization.map(v => headerValueIgnoreCaseExisting(Headers.OrbeonOrganization, v._1.toString)).toList // TODO
    }
  }

  def findAuthorizedOperationsOrThrow(
    formPermissions   : Permissions,
    credentialsOpt    : Option[Credentials],
    crudMethod        : HttpMethod.CrudMethod,
    appFormVersion    : AppFormVersion,
    documentId        : String,
    incomingTokenOpt  : Option[String],
    responseHeadersOpt: Option[ResponseHeaders],
    isAttachment      : Boolean
  )(implicit
    logger            : IndentedLogger
  ): Operations = {

    // TODO: add some logging in this method, at least in the case where we throw an `HttpStatusCodeException`

    debug(s"findAuthorizedOperationsOrThrow: `$formPermissions`, `$credentialsOpt`, `$crudMethod`, `$appFormVersion`, `$documentId`, `$incomingTokenOpt`, `$responseHeadersOpt`, `$isAttachment`")

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

            debug(s"operations from data: `$operationsFromData`")

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

            debug(s"operations from token: `$operationsFromTokenOpt`")

            // Operations obtained without consideration for the data
            val otherOperations: Operations =
             authorizedOperations(
                formPermissions,
                credentialsOpt,
                CheckWithoutDataUserPessimistic
              )

            debug(s"other operations: `$otherOperations`")

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

      val requestedAnyOfOp =
        crudMethod match {
          case HttpMethod.GET | HttpMethod.HEAD                             => List(Operation.Read)
          case HttpMethod.PUT if responseHeadersOpt.isEmpty && isAttachment => List(Operation.Create, Operation.Update)
          case HttpMethod.PUT if responseHeadersOpt.isEmpty                 => List(Operation.Create)
          case HttpMethod.PUT                                               => List(Operation.Update)
          case HttpMethod.DELETE if isAttachment                            => List(Operation.Delete, Operation.Update)
          case HttpMethod.DELETE                                            => List(Operation.Delete)
        }

      val authorized =
        requestedAnyOfOp.exists(Operations.allows(authorizedOps, _))

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

  // If we call this, it means that we don't have access based on `Anyone`, `AnyAuthenticatedUser`, `Owner`, roles,
  // etc. We are just testing for `AnyoneWithToken`.
  private def operationsFromToken(
    definedPermissions: Permissions.Defined,
    token             : String,
    tokenHmac         : FormRunnerAccessToken.TokenHmac
  ): Try[Operations] =
    FormRunnerAccessToken.decryptTokenPayloadCheckExpiration(tokenHmac, token).map { ops =>
      Operations.combine(
        definedPermissions.permissionsList collect {
          case permission if permission.conditions.contains(Condition.AnyoneWithToken) =>
            SpecificOperations(permission.operations.operations.intersect(ops.toSet))
        }
      )
    }
}
