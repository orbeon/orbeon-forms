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
package org.orbeon.oxf.fb

import org.orbeon.oxf.fb.FormBuilder.*
import org.orbeon.oxf.fr.FormRunner.*
import org.orbeon.oxf.fr.SchemaOps
import org.orbeon.oxf.fr.XMLNames.XS
import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport}
import org.orbeon.oxf.xml.XMLConstants
import org.orbeon.oxf.xml.dom.Converter.*
import org.orbeon.scaxon.NodeConversions.*
import org.orbeon.scaxon.SimplePath.*
import org.orbeon.xforms.XFormsCrossPlatformSupport
import org.scalatest.funspec.AnyFunSpecLike

import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.util.Base64


class SchemaOpsTest
  extends DocumentTestBase
     with ResourceManagerSupport
     with AnyFunSpecLike
     with FormBuilderSupport {

  private val SchemaDoc            = "oxf:/org/orbeon/oxf/fb/template-with-schema.xhtml"
  private val SchemaNoNamespaceDoc = "oxf:/org/orbeon/oxf/fb/template-with-schema-nonamespace.xhtml"

  private def parseString(content: String) =
    FormBuilderXPathApi.parseSchemaFromInputStream(
      new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)),
      "memory:schema.xsd"
    )

  describe("Schema upload parsing and validation") {

    it("must reject malformed XML without throwing") {
      val res = parseString("<xs:schema><unclosed>")
      assert(res.attValue("valid") == "false")
      assert(res.attValue("error-type") == "malformed-xml")
      assert((res / (XS -> "schema")).isEmpty)
    }

    it("must reject well-formed XML whose root element is not xs:schema") {
      val res = parseString("""<book title="Dune"/>""")
      assert(res.attValue("valid") == "false")
      assert(res.attValue("error-type") == "not-a-schema")
    }

    it("must reject an invalid xs:schema definition via MSV validation") {
      val res = parseString(
        """<xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
          |  <xs:simpleType name="broken">
          |    <xs:restriction base="xs:nonExistentBaseType"/>
          |  </xs:simpleType>
          |</xs:schema>""".stripMargin
      )
      assert(res.attValue("valid") == "false")
      assert(res.attValue("error-type") == "invalid-schema")
      assert(res.attValue("error-message").nonEmpty)
    }

    it("must strip xs:import, xs:include, and xs:redefine and flag has-includes") {
      val res = parseString(
        """<xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
          |  <xs:include schemaLocation="http://example.org/other.xsd"/>
          |  <xs:import namespace="http://example.org/ns" schemaLocation="http://example.org/ns.xsd"/>
          |  <xs:simpleType name="rating">
          |    <xs:restriction base="xs:integer">
          |      <xs:minInclusive value="1"/>
          |      <xs:maxInclusive value="5"/>
          |    </xs:restriction>
          |  </xs:simpleType>
          |</xs:schema>""".stripMargin
      )
      assert(res.attValue("valid") == "true")
      assert(res.attValue("has-includes") == "true")
      val schemaElem = (res / (XS -> "schema")).head
      assert((schemaElem / (XS -> "include")).isEmpty)
      assert((schemaElem / (XS -> "import")).isEmpty)
      assert(SchemaOps.findSimpleTypes(schemaElem).map(_.localName) == List("rating"))
    }
  }

  describe("Simple type summaries and schemaInfoAsXml") {

    it("must summarize restrictions, lists, unions, and control usages") {
      val schemaElem = elemToNodeInfo(
        <xs:schema
          xmlns:xs="http://www.w3.org/2001/XMLSchema"
          xmlns:foo="http://orbeon.org/oxf/xml/foo"
          targetNamespace="http://orbeon.org/oxf/xml/foo">
          <xs:simpleType name="rating">
            <xs:restriction base="xs:integer">
              <xs:minInclusive value="1"/>
              <xs:maxInclusive value="5"/>
            </xs:restriction>
          </xs:simpleType>
          <xs:simpleType name="language">
            <xs:restriction base="xs:string">
              <xs:enumeration value="en"/>
              <xs:enumeration value="fr"/>
              <xs:enumeration value="es"/>
            </xs:restriction>
          </xs:simpleType>
          <xs:simpleType name="code">
            <xs:restriction base="xs:string">
              <xs:minLength value="3"/>
              <xs:maxLength value="8"/>
              <xs:pattern value="[A-Z0-9]+"/>
            </xs:restriction>
          </xs:simpleType>
          <xs:simpleType name="tags">
            <xs:list itemType="xs:token"/>
          </xs:simpleType>
          <xs:simpleType name="flex">
            <xs:union memberTypes="xs:integer xs:string"/>
          </xs:simpleType>
        </xs:schema>
      )

      val summaries = SchemaOps.findSimpleTypes(schemaElem)
      assert(summaries.map(s => s.qualifiedName -> s.restrictionSummary) == List(
        "foo:rating"   -> "xs:integer (1..5)",
        "foo:language" -> "xs:string (enum: 3)",
        "foo:code"     -> "xs:string (len 3..8, pattern)",
        "foo:tags"     -> "list (xs:token)",
        "foo:flex"     -> "union (xs:integer xs:string)"
      ))

      val downloadUri = FormBuilderXPathApi.schemaToDownloadUri(schemaElem)
      assert(downloadUri.startsWith("data:application/xml;charset=utf-8;base64,"))
      val decodedBytes = Base64.getDecoder.decode(downloadUri.stripPrefix("data:application/xml;charset=utf-8;base64,"))
      val decodedDom   = XFormsCrossPlatformSupport.readOrbeonDom(new String(decodedBytes, StandardCharsets.UTF_8))
      assert(decodedDom.getRootElement.getQName == XMLConstants.XML_SCHEMA_QNAME)
    }

    it("must track in-use controls, detect orphaned controls on replacement/removal, and reset orphaned types to xs:string") {
      withActionAndFBDoc(SchemaDoc) { implicit ctx =>
        val validations = List(
          <validation type="required" level="error" default-alert="true">
            <required>false()</required>
            <alert global="false"/>
          </validation>,
          <validation type="datatype" level="error" default-alert="true">
            <builtin-type/>
            <builtin-type-required/>
            <schema-type>foo:email</schema-type>
            <alert global="false"/>
          </validation>
        )
        writeAlertsAndValidationsAsXML("control-1", "", <alert global="true"/>, validations.map(elemToNodeInfo))

        val currentSchema = SchemaOps.findSchema(ctx.formDefinitionRootElem).get
        val infoWithCurrent = FormBuilderXPathApi.schemaInfoAsXml(ctx.formDefinitionRootElem, currentSchema)
        assert(infoWithCurrent.attValue("orphaned-controls-count") == "0")

        val emailTypeElem = (infoWithCurrent / "simple-types" / "simple-type").find(_.attValue("name") == "email").get
        assert(emailTypeElem.attValue("used-count") == "1")
        assert(emailTypeElem.attValue("used-by") == "control-1")

        // Simulate removing the schema (passing null)
        val infoWithoutSchema = FormBuilderXPathApi.schemaInfoAsXml(ctx.formDefinitionRootElem, null)
        assert(infoWithoutSchema.attValue("orphaned-controls-count") == "1")
        assert(infoWithoutSchema.attValue("orphaned-summary") == "control-1 (foo:email)")

        // Reset orphaned schema types to xs:string
        val resetCount = FormBuilderXPathApi.resetOrphanedSchemaTypesInDoc(None)
        assert(resetCount == 1)

        val bind = findBindByName("control-1").get
        assert(bind.attValueOpt("type").isEmpty)
        assert(DatatypeValidation.fromForm("control-1").datatype == Left(XMLConstants.XS_STRING_QNAME -> false))
      }
    }

    it("must also detect and reset no-namespace schema types") {
      withActionAndFBDoc(SchemaNoNamespaceDoc) { implicit ctx =>
        val validations = List(
          <validation type="required" level="error" default-alert="true">
            <required>false()</required>
            <alert global="false"/>
          </validation>,
          <validation type="datatype" level="error" default-alert="true">
            <builtin-type/>
            <builtin-type-required/>
            <schema-type>rating</schema-type>
            <alert global="false"/>
          </validation>
        )
        writeAlertsAndValidationsAsXML("control-1", "", <alert global="true"/>, validations.map(elemToNodeInfo))

        assert(findBindByName("control-1").get.attValueOpt("type").contains("rating"))

        val replacementSchema = elemToNodeInfo(
          <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
            <xs:simpleType name="other-type">
              <xs:restriction base="xs:string"/>
            </xs:simpleType>
          </xs:schema>
        )

        val infoReplaced = FormBuilderXPathApi.schemaInfoAsXml(ctx.formDefinitionRootElem, replacementSchema)
        assert(infoReplaced.attValue("orphaned-controls-count") == "1")

        assert(FormBuilderXPathApi.resetOrphanedSchemaTypesInDoc(Some(replacementSchema)) == 1)
        assert(findBindByName("control-1").get.attValueOpt("type").isEmpty)
      }
    }
  }
}
