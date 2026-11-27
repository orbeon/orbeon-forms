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
package org.orbeon.oxf.fr

import org.orbeon.oxf.test.ResourceManagerSupport
import org.orbeon.scaxon.SimplePath.*
import org.scalatest.funspec.AnyFunSpec


class RemoteServersEditorUITest
  extends AnyFunSpec
     with ResourceManagerSupport {

  describe("RemoteServersEditorUI validation") {

    it("accepts blank, empty array, and valid remote server entries") {
      assert(RemoteServersEditorUI.isValidRemoteServersJson(""))
      assert(RemoteServersEditorUI.isValidRemoteServersJson("   "))
      assert(RemoteServersEditorUI.isValidRemoteServersJson("[]"))
      assert(RemoteServersEditorUI.isValidRemoteServersJson(
        """[{"name": "staging", "label": "Staging", "url": "https://staging.example.org/orbeon"}]"""
      ))
      assert(RemoteServersEditorUI.isValidRemoteServersJson(
        """[{"url": "https://prod.example.org/orbeon", "ui-access": false, "api-access": true}]"""
      ))
    }

    it("rejects malformed JSON, non-arrays, missing url, wrong field types, and duplicate names") {
      assert(! RemoteServersEditorUI.isValidRemoteServersJson("[{broken"))
      assert(! RemoteServersEditorUI.isValidRemoteServersJson("""{"url": "https://a.example.org"}"""))
      assert(! RemoteServersEditorUI.isValidRemoteServersJson("""[{"name": "staging"}]"""))
      assert(! RemoteServersEditorUI.isValidRemoteServersJson("""[{"url": "   "}]"""))
      assert(! RemoteServersEditorUI.isValidRemoteServersJson("""[{"url": "https://a.example.org", "ui-access": "yes"}]"""))
      assert(! RemoteServersEditorUI.isValidRemoteServersJson("""[{"url": "https://a.example.org", "api-access": 1}]"""))
      assert(! RemoteServersEditorUI.isValidRemoteServersJson(
        """[
          |  {"name": "staging", "url": "https://a.example.org"},
          |  {"name": "staging", "url": "https://b.example.org"}
          |]""".stripMargin
      ))
    }
  }

  describe("RemoteServersEditorUI JSON <-> UI XML round-trip") {

    it("converts JSON to UI XML with expected defaults and serializes concisely back to JSON") {
      val input =
        """[
          |  {"name": "staging", "label": "Staging", "url": "https://staging.example.org/orbeon"},
          |  {"name": "ci", "url": "https://ci.example.org/orbeon", "ui-access": false, "api-access": true}
          |]""".stripMargin

      val xml        = RemoteServersEditorUI.jsonToUiXml(input)
      val serversEl  = (xml / "servers").head
      val servers    = (serversEl / "server").toList
      assert(servers.size == 2)

      assert(servers(0).attValue("name")       == "staging")
      assert(servers(0).attValue("label")      == "Staging")
      assert(servers(0).attValue("url")        == "https://staging.example.org/orbeon")
      assert(servers(0).attValue("ui-access")  == "true")
      assert(servers(0).attValue("api-access") == "false")

      assert(servers(1).attValue("name")       == "ci")
      assert(servers(1).attValue("label")      == "")
      assert(servers(1).attValue("url")        == "https://ci.example.org/orbeon")
      assert(servers(1).attValue("ui-access")  == "false")
      assert(servers(1).attValue("api-access") == "true")

      val serialized = RemoteServersEditorUI.uiXmlToJson(serversEl)
      assert(RemoteServersEditorUI.isValidRemoteServersJson(serialized))

      // Default ui-access=true and api-access=false are omitted for clean JSON
      assert(! serialized.contains(""""ui-access" : true"""))
      assert(! serialized.contains(""""api-access" : false"""))
      assert(serialized.contains(""""ui-access" : false"""))
      assert(serialized.contains(""""api-access" : true"""))

      // Compatible with FormRunnerHome.tryRemoteServersFromString
      val parsedByHome = FormRunnerHome.tryRemoteServersFromString(serialized).get
      assert(parsedByHome.size == 2)
      assert(parsedByHome(0) == FormRunnerHome.RemoteServer(Some("staging"), Some("Staging"), "https://staging.example.org/orbeon", uiAccess = true))
      assert(parsedByHome(1) == FormRunnerHome.RemoteServer(Some("ci"), None, "https://ci.example.org/orbeon", uiAccess = false))
    }

    it("serializes empty servers element to []") {
      val xml = RemoteServersEditorUI.jsonToUiXml("[]")
      assert(RemoteServersEditorUI.uiXmlToJson((xml / "servers").head) == "[]")
    }
  }
}
