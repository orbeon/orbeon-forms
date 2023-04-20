package org.orbeon.oxf.fr.permission

import org.scalatest.funspec.AnyFunSpecLike

import scala.util.Success


class PermissionsJSONTest extends AnyFunSpecLike {

  describe("Parsing JSON permissions format") {

    val json1 =
      """{
        |  "anyone":         [ "create" ],
        |  "owner":          [ "read", "update" ],
        |  "group-member":   [ "read", "update" ],
        |  "roles": {
        |    "orbeon-user":  [ "read", "update", "list" ],
        |    "orbeon-admin": [ "read", "update", "delete", "list" ]
        |  }
        |}""".stripMargin

    val adt1 =
      Permissions.Defined(
        List(
          Permission(
            Nil,
            SpecificOperations(Set(Operation.Create))
          ),
          Permission(
            List(Condition.Owner),
            SpecificOperations(Set(Operation.Read, Operation.Update))
          ),
          Permission(
            List(Condition.Group),
            SpecificOperations(Set(Operation.Read, Operation.Update))
          ),
          Permission(
            List(Condition.RolesAnyOf(List("orbeon-user"))),
            SpecificOperations(Set(Operation.Read, Operation.Update, Operation.List))
          ),
          Permission(
            List(Condition.RolesAnyOf(List("orbeon-admin"))),
            SpecificOperations(Set(Operation.Read, Operation.Update, Operation.Delete, Operation.List))
          )
        )
      )

    it (s"must succeed with `$json1`") {
      assert(Success(adt1) == PermissionsJSON.parseString(json1))
    }

    val json2 = "  "

    it(s"must succeed with `$json2`") {
      assert(Success(Permissions.Undefined) == PermissionsJSON.parseString(json2))
    }

    val json3 =
      """
        |{
        |  "anyone":         [ "creat" ]
        |}""".stripMargin

    it(s"must not parse `$json3`") {
      assertThrows[NoSuchElementException](PermissionsJSON.parseString(json3).get)
    }

    val json4 =
      """{
        |  "anyone":         [ ],
        |  "roles": {
        |    "orbeon-user":  [ ],
        |    "orbeon-admin": [ ]
        |  }
        |}""".stripMargin

    val adt4 =
      Permissions.Defined(
        List(
          Permission(Nil, Operations.None),
          Permission(List(Condition.RolesAnyOf(List("orbeon-user"))), Operations.None),
          Permission(List(Condition.RolesAnyOf(List("orbeon-admin"))), Operations.None)
        )
      )

    it(s"must succeed with empty lists of operations `$json4`") {
      assert(Success(adt4) == PermissionsJSON.parseString(json4))
    }

    val json5 =
      """{
        |  "anybody":        [ "create" ],
        |  "owner":          [ "read", "update" ],
        |  "group-member":   [ "read", "update" ],
        |  "roles": {
        |    "orbeon-user":  [ "read", "update", "list" ],
        |    "orbeon-admin": [ "read", "update", "delete", "list" ]
        |  }
        |}""".stripMargin

    it(s"must not parse `$json5`") {
      assertThrows[IllegalArgumentException](PermissionsJSON.parseString(json5).get)
    }
  }
}
