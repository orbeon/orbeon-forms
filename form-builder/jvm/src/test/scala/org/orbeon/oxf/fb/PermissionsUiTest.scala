package org.orbeon.oxf.fb

import org.orbeon.oxf.fr.permission._
import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport}
import org.orbeon.saxon.om
import org.orbeon.scaxon.NodeConversions._
import org.scalatest.funspec.AnyFunSpecLike


class PermissionsUiTest extends DocumentTestBase
  with ResourceManagerSupport
  with AnyFunSpecLike
  with FormBuilderSupport {

  describe("Conversion from UI format to form definition format") {

    val uiAnyoneCanReadEl: om.NodeInfo =
      <permissions>
        <permission>
          <operations>read</operations>
        </permission>
        <permission for="owner">
          <operations/>
        </permission>
        <permission for="group-member">
          <operations/>
        </permission>
      </permissions>

    val fdAnyoneCanReadEl: om.NodeInfo =
      <permissions>
        <permission operations="read -list"/>
      </permissions>

    val adtAnyoneCanRead =
      DefinedPermissions(
        List(
          Permission(
            Nil,
            SpecificOperations(Set(Operation.Read))
          )
        )
      )

    val uiAnyoneCanReadListEl: om.NodeInfo =
      <permissions>
        <permission>
          <operations>read list</operations>
        </permission>
        <permission for="owner">
          <operations/>
        </permission>
        <permission for="group-member">
          <operations/>
        </permission>
      </permissions>

    val fdAnyoneCanReadListEl: om.NodeInfo =
      <permissions>
        <permission operations="read"/>
      </permissions>

    val adtAnyoneCanReadList =
      DefinedPermissions(
        List(
          Permission(
            Nil,
            SpecificOperations(Set(Operation.Read, Operation.List))
          )
        )
      )

    val uiAnyoneCanReadRoleCanListEl: om.NodeInfo =
      <permissions>
        <permission>
          <operations>read</operations>
        </permission>
        <permission for="owner">
          <operations/>
        </permission>
        <permission for="group-member">
          <operations/>
        </permission>
        <permission>
          <operations>list</operations>
          <role>admin</role>
        </permission>
      </permissions>

    val fdAnyoneCanReadRoleCanListEl: om.NodeInfo =
      <permissions>
        <permission operations="read -list"/>
        <permission operations="">
          <user-role any-of="admin"/>
        </permission>
      </permissions>

    val adtAnyoneCanReadRoleCanList =
      DefinedPermissions(
        List(
          Permission(
            Nil,
            SpecificOperations(Set(Operation.Read))
          ),
          Permission(
            List(RolesAnyOf(List("admin"))),
            SpecificOperations(Set(Operation.List)) // 2022-10-03: decided that `list` does not imply `read`
          )
        )
      )

    val uiMixedPermissionsEl: om.NodeInfo =
      <permissions>
        <permission>
          <operations/>
        </permission>
        <permission for="owner">
          <operations>update delete</operations>
        </permission>
        <permission for="group-member">
          <operations>read</operations>
        </permission>
        <permission>
          <role>orbeon-admin</role>
          <operations>create update list delete</operations>
        </permission>
        <permission>
          <role>orbeon-user</role>
          <operations>create update list</operations>
        </permission>
      </permissions>

    val fdMixedPermissionsEl: om.NodeInfo =
      <permissions>
        <permission operations="read update delete -list">
          <owner/>
        </permission>
        <permission operations="read -list">
          <group-member/>
        </permission>
        <permission operations="create read update">
          <user-role any-of="orbeon-admin orbeon-user"/>
        </permission>
        <permission operations="delete -list">
          <user-role any-of="orbeon-admin"/>
        </permission>
      </permissions>

    val adtMixedPermissionsEl =
      DefinedPermissions(
        List(
          Permission(
            List(Owner),
            SpecificOperations(Set(Operation.Update, Operation.Delete, Operation.Read))
          ),
          Permission(
            List(Group),
            SpecificOperations(Set(Operation.Read))
          ),
          Permission(
            List(RolesAnyOf(List("orbeon-admin", "orbeon-user"))),
            SpecificOperations(Set(Operation.Create, Operation.Update, Operation.Read, Operation.List))
          ),
          Permission(
            List(RolesAnyOf(List("orbeon-admin"))),
            SpecificOperations(Set(Operation.Delete)))
        )
      )

    val Expected = List(
      ("Anyone can read",                uiAnyoneCanReadEl,            fdAnyoneCanReadEl,            adtAnyoneCanRead),
      ("Anyone can read and list",       uiAnyoneCanReadListEl,        fdAnyoneCanReadListEl,        adtAnyoneCanReadList),
      ("Anyone can read, role can list", uiAnyoneCanReadRoleCanListEl, fdAnyoneCanReadRoleCanListEl, adtAnyoneCanReadRoleCanList),
      ("Mixed permissions",              uiMixedPermissionsEl,         fdMixedPermissionsEl,         adtMixedPermissionsEl),
    )

    for ((desc, uiEl, expectedFdEl, expectedAdt) <- Expected) {
      it(s"must pass $desc") {

        val computedFdEl   = FormBuilderXPathApi.convertPermissionsFromUiToFormDefinitionFormat(uiEl)
        val computedAdt    = PermissionsXML.parse(Some(computedFdEl))
        val serializedFdEl = PermissionsXML.serialize(computedAdt, normalized = false)

//        println(s"computedFdEl:\n${StaticXPath.tinyTreeToString(computedFdEl)}")
//        println(s"computedAdt:\n$computedAdt")
//        println(s"serializedFdEl:\n${serializedFdEl.toString}")

        assertXMLElementsIgnoreNamespacesInScope(expectedFdEl, computedFdEl)
        assert(expectedAdt == computedAdt)
        assertXMLElementsIgnoreNamespacesInScope(expectedFdEl, serializedFdEl.get: om.NodeInfo)
      }
    }
  }
}
