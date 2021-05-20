/**
  * Copyright (C) 2016 Orbeon, Inc.
  *
  * This program is free software; you can redistribute it and/or modify it under the terms of the
  * GNU Lesser General Public License as published by the Free Software Foundation; either version
  *  2.1 of the License, or (at your option) any later version.
  *
  * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  * See the GNU Lesser General Public License for more details.
  *
  * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
  */
package org.orbeon.oxf.externalcontext

import org.orbeon.oxf.test.XMLSupport
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.NodeConversions._
import org.scalatest.funspec.AnyFunSpec


class CredentialsTest extends AnyFunSpec with XMLSupport {

  val TestCredentials =
    Credentials(
      "employee@orbeon.com",
      Some("employee"),
      List(
        SimpleRole("Administrator"),
        SimpleRole("Power User"),
        ParametrizedRole("Organization Owner", "Orbeon San Mateo"),
        ParametrizedRole("Organization Administrator", "Orbeon San Mateo")
      ),
      List(
        Organization(List("Orbeon, Inc.", "Orbeon California", "Orbeon San Mateo")),
        Organization(List("California Department of Education", "Local School District"))
      )
    )

  describe("JSON serialization and deserialization") {
    for {
      encode <- List(false, true)
      serialized   = CredentialsSupport.serializeCredentials(TestCredentials, encode)
      deserialized = CredentialsSupport.parseCredentials(serialized, encode)
    } locally {
      it (s"must pass with `encode` set to `$encode`") {
        assert(deserialized === Some(TestCredentials))
      }
    }
  }

  // Here, rather than comparing with the XML representation, we should either:
  //
  // - check against the actual JSON result
  // - or use a schema to check a series of input `Credentials`, in which case
  //   it would make sense to work on the XML representation of the JSON and use
  //   for example RNG compact as schema
  //
  describe("External JSON format") {

    import org.orbeon.oxf.json.Converter
    import org.orbeon.scaxon.SimplePath._

    val rootElem =
      Converter.jsonStringToXmlDoc(CredentialsSupport.serializeCredentials(TestCredentials, encodeForHeader = false)).rootElement

    val expectedXml: NodeInfo =
      <json type="object">
        <username>employee@orbeon.com</username>
        <groups type="array">
            <_>employee</_>
        </groups>
        <roles type="array">
            <_ type="object">
                <name>Administrator</name>
            </_>
            <_ type="object">
                <name>Power User</name>
            </_>
            <_ type="object">
                <name>Organization Owner</name>
                <organization>Orbeon San Mateo</organization>
            </_>
            <_ type="object">
                <name>Organization Administrator</name>
                <organization>Orbeon San Mateo</organization>
            </_>
        </roles>
        <organizations type="array">
            <_ type="array">
                <_>Orbeon, Inc.</_>
                <_>Orbeon California</_>
                <_>Orbeon San Mateo</_>
            </_>
            <_ type="array">
                <_>California Department of Education</_>
                <_>Local School District</_>
            </_>
        </organizations>
    </json>

    it ("must match the expected serialization") {
      assertXMLDocumentsIgnoreNamespacesInScope(expectedXml.root, rootElem.root)
    }

    val expectedPassing = List(
      (
        "full input",
        """
          {
            "username": "employee@orbeon.com",
            "groups": [
              "employee"
            ],
            "roles": [
              {
                "name": "Administrator"
              },
              {
                "name": "Power User"
              },
              {
                "name": "Organization Owner",
                "organization": "Orbeon San Mateo"
              },
              {
                "name": "Organization Administrator",
                "organization": "Orbeon San Mateo"
              }
            ],
            "organizations": [
              [
                "Orbeon, Inc.",
                "Orbeon California",
                "Orbeon San Mateo"
              ],
              [
                "California Department of Education",
                "Local School District"
              ]
            ]
          }
        """,
        TestCredentials
      ),
      (
        "missing organizations",
        """
          {
            "username": "employee@orbeon.com",
            "groups": [
              "employee"
            ],
            "roles": [
              {
                "name": "Administrator"
              },
              {
                "name": "Power User"
              },
              {
                "name": "Organization Owner",
                "organization": "Orbeon San Mateo"
              },
              {
                "name": "Organization Administrator",
                "organization": "Orbeon San Mateo"
              }
            ]
          }
        """,
          Credentials(
            "employee@orbeon.com",
            Some("employee"),
            List(
              SimpleRole("Administrator"),
              SimpleRole("Power User"),
              ParametrizedRole("Organization Owner", "Orbeon San Mateo"),
              ParametrizedRole("Organization Administrator", "Orbeon San Mateo")
            ),
            Nil
          )
      ),
      (
        "missing roles and organizations",
        """
          {
            "username": "employee@orbeon.com",
            "groups": [
              "employee"
            ]
          }
        """,
          Credentials(
            "employee@orbeon.com",
            Some("employee"),
            Nil,
            Nil
          )
      ),
      (
        "missing groups, roles and organizations",
        """
          {
            "username": "employee@orbeon.com"
          }
        """,
          Credentials(
            "employee@orbeon.com",
            None,
            Nil,
            Nil
          )
        )
    )

    for ((description, json, credentials) <- expectedPassing)
      it (s"must support $description") {
        assert(Some(credentials) === CredentialsSupport.parseCredentials(json, decodeForHeader = false))
      }

    val expectedFailing = List(
      "missing username" ->
      """
        {
          "groups": [
            "employee"
          ]
        }
      """
    )

    for ((description, json) <- expectedFailing)
      it (s"must reject $description") {
        intercept[Throwable] {
          CredentialsSupport.parseCredentials(json, decodeForHeader = false)
        }
      }
  }
}
