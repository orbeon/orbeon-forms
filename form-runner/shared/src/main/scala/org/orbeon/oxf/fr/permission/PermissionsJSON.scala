package org.orbeon.oxf.fr.permission

import io.circe.*
import io.circe.parser.*
import org.orbeon.oxf.util.StringUtils.*

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

        def keyToSimpleConditions(key: String): List[Condition] =
          Try(Condition.parseSimpleCondition(key).toList).getOrElse(error())

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
                    keyToSimpleConditions(key),
                    jsonToOperations(jsonArray)
                  )
                }

            def findRolePermissions: Option[List[Permission]] =
              topLevelMap.get("roles") map { jsonObject =>
                jsonObject.asObject.getOrElse(error()).toList map { case (role, jsonArray) =>
                  Permission(
                    List(Condition.RolesAnyOf(List(role))),
                    jsonToOperations(jsonArray)
                  )
                }
              }

            Permissions.Defined(
              findSimplePermission("anyone").toList       :::
              findSimplePermission("owner").toList        :::
              findSimplePermission("group-member").toList :::
              findRolePermissions.toList.flatten
            )
          }
        }
      case None =>
        Success(Permissions.Undefined)
    }
}
