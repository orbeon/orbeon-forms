package org.orbeon.oxf.fr.permission

import io.circe.*
import io.circe.parser.*
import org.orbeon.oxf.util.StringUtils.*

import scala.util.{Success, Try}


object PermissionsJSON {

  import Private.*

  // Example JSON:
  // ```json
  // {
  //   "anyone":                 [ "create" ],
  //   "anyone-with-token":      [ "read", "update" ],
  //   "any-authenticated-user": [ "read" ],
  //   "owner":                  [ "read", "update" ],
  //   "group-member":           [ "read", "update" ],
  //   "roles": {
  //     "orbeon-user":  [ "read", "update", "list" ],
  //     "orbeon-admin": [ "read", "update", "delete", "list" ]
  //   }
  // }```

  implicit val encodeDefinedPermissions: Encoder[Permissions.Defined] =
    Encoder.instance(definedToJson)

  def serializeToJson(permissions: Permissions): Option[Json] =
    permissions match {
      case Permissions.Undefined        => None
      case defined: Permissions.Defined => Some(definedToJson(defined))
    }

  def formatToString(permissions: Permissions): String =
    serializeToJson(permissions) match {
      case None       => ""
      case Some(json) => prettyPrintJson(json)
    }

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
                val roleEntries = jsonObject.asObject.getOrElse(error()).toList

                if (roleEntries.exists(_._1.isAllBlank) || roleEntries.map(_._1).distinct.size != roleEntries.size)
                  error()

                roleEntries map { case (role, jsonArray) =>
                  Permission(
                    List(Condition.RolesAnyOf(List(role))),
                    jsonToOperations(jsonArray)
                  )
                }
              }

            Permissions.Defined(
              OrderedSimpleConditionKeys.flatMap(findSimplePermission) :::
              findRolePermissions.toList.flatten
            )
          }
        }
      case None =>
        Success(Permissions.Undefined)
    }

  private object Private {

    val OrderedSimpleConditionKeys: List[String] = List(
      "anyone",
      "anyone-with-token",
      "any-authenticated-user",
      "owner",
      "group-member"
    )

    val AllowedTopLevelKeys: Set[String] =
      OrderedSimpleConditionKeys.toSet + "roles"

    def definedToJson(defined: Permissions.Defined): Json = {

      val simpleFields: List[(String, Json)] =
        for {
          key      <- OrderedSimpleConditionKeys
          condList = Condition.parseSimpleCondition(key).toList
          matching = defined.permissionsList.collect {
            case Permission(`condList`, SpecificOperations(ops)) => ops
          }
          if matching.nonEmpty
          orderedOps = Operations.inDefinitionOrder(matching.flatten)
        } yield
          key -> Json.fromValues(orderedOps.map(op => Json.fromString(op.entryName)))

      val rolePairs: List[(String, Set[Operation])] =
        defined.permissionsList.collect {
          case Permission(List(Condition.RolesAnyOf(roles)), SpecificOperations(ops)) =>
            roles.map(_ -> ops)
        }.flatten

      val distinctRoles = rolePairs.map(_._1).distinct

      val rolesFieldOpt: Option[(String, Json)] =
        if (distinctRoles.isEmpty)
          None
        else {
          val roleFields =
            distinctRoles.map { role =>
              val orderedOps = Operations.inDefinitionOrder(rolePairs.collect { case (`role`, ops) => ops }.flatten)
              role -> Json.fromValues(orderedOps.map(op => Json.fromString(op.entryName)))
            }
          Some("roles" -> Json.fromJsonObject(JsonObject.fromIterable(roleFields)))
        }

      Json.fromJsonObject(JsonObject.fromIterable(simpleFields ++ rolesFieldOpt.toList))
    }

    def prettyPrintJson(json: Json, indent: Int = 0): String =
      json.fold(
        jsonNull    = "null",
        jsonBoolean = _.toString,
        jsonNumber  = _.toString,
        jsonString  = s => Json.fromString(s).noSpaces,
        jsonArray   = values =>
          if (values.isEmpty)
            "[]"
          else
            s"[ ${values.map(v => prettyPrintJson(v, indent)).mkString(", ")} ]",
        jsonObject  = obj => {
          val fields = obj.toList
          if (fields.isEmpty)
            "{}"
          else {
            val pad      = " " * indent
            val innerPad = " " * (indent + 2)
            fields
              .map { case (k, v) => s"$innerPad${Json.fromString(k).noSpaces}: ${prettyPrintJson(v, indent + 2)}" }
              .mkString("{\n", ",\n", s"\n$pad}")
          }
        }
      )
  }
}
