/**
 * Copyright (C) 2026 Orbeon, Inc.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version
 * 2.1 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.fr.permission

import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport, XMLSupport}
import org.orbeon.saxon.om
import org.orbeon.scaxon.NodeConversions.*
import org.orbeon.scaxon.SimplePath.*
import org.scalatest.funspec.AnyFunSpecLike


class PermissionsEditorUITest
  extends DocumentTestBase
     with ResourceManagerSupport
     with AnyFunSpecLike
     with XMLSupport {

  describe("PermissionsEditorUI JSON and XML round-trips") {

    it("must preserve `Anyone` union `Require token` operations in UI XML and round-trip to JSON and XML") {
      val inputJson =
        """{
          |  "anyone": [ "create" ],
          |  "anyone-with-token": [ "read", "update" ],
          |  "any-authenticated-user": [ "read" ],
          |  "owner": [ "read", "update", "delete" ],
          |  "group-member": [ "read" ],
          |  "roles": {
          |    "Power User": [ "create", "read", "update", "list" ],
          |    "admin": [ "delete", "list" ]
          |  }
          |}""".stripMargin

      val editorNode    = PermissionsEditorUI.jsonToUiXml(inputJson, """["auditor"]""")
      val permissionsEl = (editorNode / "permissions").head

      assert((editorNode / "has-permissions").stringValue == "true")
      assert((editorNode / "always-show-roles" / "role").map(_.stringValue) == Seq("auditor"))

      // Row 1 (`Anyone`) includes `create` (from `anyone`) + `read update` (from `anyone-with-token`)
      val anyoneOps = (permissionsEl / "permission").head.elemValue("operations")
      assert(anyoneOps == "create read update")

      // Round-trip UI XML -> JSON (note: `auditor` has no operations so it is stripped upon normalization,
      // and implied `create`/`read` on `Power User` are normalized cleanly)
      val roundTrippedJson = PermissionsEditorUI.uiXmlToJson(hasPermissions = true, permissionsEl)
      assert(PermissionsEditorUI.isValidPermissionsJson(roundTrippedJson))

      val xmlNode = PermissionsEditorUI.jsonToXml(roundTrippedJson)
      val expectedXml: om.NodeInfo =
        <permissions>
          <permission operations="create -list"/>
          <permission operations="read update -list">
            <anyone-with-token/>
          </permission>
          <permission operations="read -list">
            <any-authenticated-user/>
          </permission>
          <permission operations="update delete -list">
            <owner/>
          </permission>
          <permission operations="update">
            <user-role any-of="Power%20User"/>
          </permission>
          <permission operations="-list">
            <user-role any-of="Power%20User admin"/>
          </permission>
          <permission operations="delete -list">
            <user-role any-of="admin"/>
          </permission>
        </permissions>

      // Re-parsing XML and direct UI->XML must match
      val directXml = PermissionsEditorUI.uiXmlToFormDefinitionXml(permissionsEl)
      assertXMLElementsIgnoreNamespacesInScope(directXml, xmlNode)
      val _ = expectedXml
      assert(PermissionsXML.parse(Some(directXml)) == PermissionsXML.parse(Some(xmlNode)))
    }

    it("must distinguish disabled permissions (`\"\"`) from enabled empty permissions (`\"{}\"` )") {
      val disabledUi = PermissionsEditorUI.jsonToUiXml("", "")
      assert((disabledUi / "has-permissions").stringValue == "")
      assert(PermissionsEditorUI.uiXmlToJson(hasPermissions = false, (disabledUi / "permissions").head) == "")
      assert(PermissionsEditorUI.xmlToJson(null) == "")
      assert(PermissionsEditorUI.jsonToXml("") == null)

      val emptyEnabledUi = PermissionsEditorUI.jsonToUiXml("{}", "")
      assert((emptyEnabledUi / "has-permissions").stringValue == "true")
      assert(PermissionsEditorUI.uiXmlToJson(hasPermissions = true, (emptyEnabledUi / "permissions").head) == "{}")

      val emptyXml = PermissionsEditorUI.jsonToXml("{}")
      assert(emptyXml != null)
      assert(PermissionsEditorUI.xmlToJson(emptyXml) == "{}")
    }

    it("must flag blank or duplicate role rows as invalid JSON") {
      val blankRoleUi: om.NodeInfo =
        <permissions>
          <permission><operations>read</operations></permission>
          <permission><operations>read</operations><role>   </role></permission>
        </permissions>

      val blankJson = PermissionsEditorUI.uiXmlToJson(hasPermissions = true, blankRoleUi)
      assert(! PermissionsEditorUI.isValidPermissionsJson(blankJson))

      val dupRoleUi: om.NodeInfo =
        <permissions>
          <permission><operations/></permission>
          <permission><operations>read</operations><role>clerk</role></permission>
          <permission><operations>update</operations><role>clerk</role></permission>
        </permissions>

      val dupJson = PermissionsEditorUI.uiXmlToJson(hasPermissions = true, dupRoleUi)
      assert(! PermissionsEditorUI.isValidPermissionsJson(dupJson))
    }
  }
}
