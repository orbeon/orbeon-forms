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

    val jsonTokenAndAuthenticated =
      """{
        |  "anyone":                 [ "create" ],
        |  "anyone-with-token":      [ "read", "update" ],
        |  "any-authenticated-user": [ "read" ],
        |  "owner":                  [ "read", "update", "delete" ],
        |  "group-member":           [ "read" ],
        |  "roles": {
        |    "Power User": [ "create", "read", "update", "list" ],
        |    "admin":      [ "delete", "list" ]
        |  }
        |}""".stripMargin

    val adtTokenAndAuthenticated =
      Permissions.Defined(
        List(
          Permission(Nil,                                        SpecificOperations(Set(Operation.Create))),
          Permission(List(Condition.AnyoneWithToken),            SpecificOperations(Set(Operation.Read, Operation.Update))),
          Permission(List(Condition.AnyAuthenticatedUser),       SpecificOperations(Set(Operation.Read))),
          Permission(List(Condition.Owner),                      SpecificOperations(Set(Operation.Read, Operation.Update, Operation.Delete))),
          Permission(List(Condition.Group),                      SpecificOperations(Set(Operation.Read))),
          Permission(List(Condition.RolesAnyOf(List("Power User"))), SpecificOperations(Set(Operation.Create, Operation.Read, Operation.Update, Operation.List))),
          Permission(List(Condition.RolesAnyOf(List("admin"))),      SpecificOperations(Set(Operation.Delete, Operation.List)))
        )
      )

    it("must parse `anyone-with-token`, `any-authenticated-user`, and role names with spaces") {
      assert(Success(adtTokenAndAuthenticated) == PermissionsJSON.parseString(jsonTokenAndAuthenticated))
    }

    it("must parse `{}` as `Permissions.Defined(Nil)`") {
      assert(Success(Permissions.Defined(Nil)) == PermissionsJSON.parseString("{}"))
    }

    it("must reject blank role names") {
      assert(PermissionsJSON.parseString("""{ "roles": { "": [ "read" ] } }""").isFailure)
      assert(PermissionsJSON.parseString("""{ "roles": { "   ": [ "read" ] } }""").isFailure)
    }
  }

  describe("Serializing JSON permissions format") {

    it("must format `Permissions.Undefined` as empty string and `Permissions.Defined(Nil)` as `{}`") {
      assert(PermissionsJSON.formatToString(Permissions.Undefined)    == "")
      assert(PermissionsJSON.formatToString(Permissions.Defined(Nil)) == "{}")
    }

    it("must expand grouped `RolesAnyOf` permissions per role and round-trip cleanly") {
      val groupedRolesAdt =
        Permissions.Defined(
          List(
            Permission(List(Condition.AnyoneWithToken),                              SpecificOperations(Set(Operation.Read, Operation.Update))),
            Permission(List(Condition.AnyAuthenticatedUser),                         SpecificOperations(Set(Operation.Create))),
            Permission(List(Condition.Owner),                                        SpecificOperations(Set(Operation.Read, Operation.Update))),
            Permission(List(Condition.RolesAnyOf(List("orbeon-admin", "orbeon-user"))), SpecificOperations(Set(Operation.Read, Operation.Update, Operation.List))),
            Permission(List(Condition.RolesAnyOf(List("orbeon-admin"))),             SpecificOperations(Set(Operation.Delete)))
          )
        )

      val expectedFormatted =
        """{
          |  "anyone-with-token": [ "read", "update" ],
          |  "any-authenticated-user": [ "create" ],
          |  "owner": [ "read", "update" ],
          |  "roles": {
          |    "orbeon-admin": [ "read", "update", "delete", "list" ],
          |    "orbeon-user": [ "read", "update", "list" ]
          |  }
          |}""".stripMargin

      val formatted = PermissionsJSON.formatToString(groupedRolesAdt)
      assert(formatted == expectedFormatted)

      val reparsed = PermissionsJSON.parseString(formatted).get
      assert(PermissionsJSON.formatToString(reparsed) == expectedFormatted)
    }
  }
}
