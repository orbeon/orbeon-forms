package org.orbeon.oxf.fr.permission

import io.circe._
import io.circe.parser._
import org.orbeon.oxf.util.StringUtils._

import scala.util.{Success, Try}


object PermissionsJSON {

  // Example JSON:
  // ```json
  // {
  //   "anyone":         [ "create" ],
  //   "owner":          [ "read", "update" ],
  //   "group-member":   [ "read", "update" ],
  //   "roles": {
  //     "orbeon-user":  [ "read", "update", "list" ],
  //     "orbeon-admin": [ "read", "update", "delete", "list" ]
  //   }
  // }```

  private val AllowedTopLevelKeys = Set(
    "anyone",
    "owner",
    "group-member",
    "roles"
  )

  def parseString(jsonString: String): Try[Permissions] =
    jsonString.trimAllToOpt match {
      case Some(trimmedJsonString) =>

        def error(): Nothing =
          throw new IllegalArgumentException(trimmedJsonString)

        def keyToConditions(key: String): List[Condition] =
          key match {
            case "anyone"                 => Nil
            case "anyone-with-token"      => List(AnyoneWithToken)      // new with #5437
            case "any-authenticated-user" => List(AnyAuthenticatedUser) // new with #5437
            case "owner"                  => List(Owner)
            case "group-member"           => List(Group)
            case _                        => error()
          }

        def jsonToOperations(jsonArray: Json): SpecificOperations =
          SpecificOperations(jsonArray.asArray.getOrElse(error()).map(_.asString.getOrElse(error())).toSet.map(Operation.withName))

        parse(trimmedJsonString).toTry flatMap { json =>
          Try {
            val topLevelMap = json.asObject.getOrElse(error()).toMap

            if (! topLevelMap.keys.forall(AllowedTopLevelKeys))
              error()

            def findSimplePermission(key: String): Option[Permission] =
                topLevelMap.get(key) map { jsonArray =>
                  Permission(
                    keyToConditions(key),
                    jsonToOperations(jsonArray)
                  )
                }

            def findRolePermissions: Option[List[Permission]] =
              topLevelMap.get("roles") map { jsonObject =>
                jsonObject.asObject.getOrElse(error()).toList map { case (role, jsonArray) =>
                  Permission(
                    List(RolesAnyOf(List(role))),
                    jsonToOperations(jsonArray)
                  )
                }
              }

            DefinedPermissions(
              findSimplePermission("anyone").toList       :::
              findSimplePermission("owner").toList        :::
              findSimplePermission("group-member").toList :::
              findRolePermissions.toList.flatten
            )
          }
        }
      case None =>
        Success(UndefinedPermissions)
    }
}
