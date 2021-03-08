/**
 * Copyright (C) 2013 Orbeon, Inc.
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

import org.orbeon.dom.Document
import org.orbeon.oxf.fb.FormBuilder._
import org.orbeon.oxf.fr.FormRunner._
import org.orbeon.oxf.fr.SchemaOps.findSchemaPrefix
import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport}
import org.orbeon.oxf.xml.dom.Converter._
import org.orbeon.oxf.xml.{TransformerUtils, XMLConstants}
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.NodeConversions._
import org.orbeon.scaxon.SimplePath._
import org.scalatest.funspec.AnyFunSpecLike

import scala.{xml => sx}


class AlertsAndConstraintsTest
  extends DocumentTestBase
     with ResourceManagerSupport
     with AnyFunSpecLike
     with FormBuilderSupport {

  val AlertsDoc            = "oxf:/org/orbeon/oxf/fb/template-with-alerts.xhtml"
  val SchemaDoc            = "oxf:/org/orbeon/oxf/fb/template-with-schema.xhtml"
  val SchemaNoNamespaceDoc = "oxf:/org/orbeon/oxf/fb/template-with-schema-nonamespace.xhtml"

  private val Control1 = "control-1"

  describe("Initial alert") {
    it("must read initial alert") {
      withActionAndFBDoc(AlertsDoc) { implicit ctx =>

        val alertDetails = AlertDetails.fromForm(Control1)
        assert(List(AlertDetails(None, List("en" -> "Alert for en", "fr" -> "Alert for fr"), global = false)) === alertDetails)

        val expected =
          <alert message="Alert for en" global="false">
            <message lang="fr" value="Alert for fr"/>
          </alert>

        assertAlertsXML(List(expected), alertDetails map (a => a.toXML(FormBuilder.currentLang): NodeInfo))
      }
    }
  }

  describe("Warning constraint automatic id") {
    it("must add an automatic id") {
      withActionAndFBDoc(AlertsDoc) { implicit ctx =>

        val newValidation =
          <validation type="formula" id="" level="warning" default-alert="false">
            <constraint expression="string-length() gt 10" argument=""/>
            <alert message="Length must be greater than 10" global="false">
              <message lang="fr" value="Longueur doit être plus grande que 10"/>
            </alert>
          </validation>

        writeAlertsAndValidationsAsXML(Control1, "", globalAlertAsXML, List(newValidation))

        val expected =
          <validation type="formula" id="validation-1-validation" level="warning" default-alert="false">
            <constraint expression="string-length() gt 10" argument=""/>
            <alert message="Length must be greater than 10" global="false">
              <message lang="fr" value="Longueur doit être plus grande que 10"/>
            </alert>
          </validation>

        assertAlertsXML(List(expected), readConstraintValidationsAsXML(Control1))
      }
    }
  }

  describe("Warning constraint specify id") {
    it("must read back the constraint with the given id") {
      withActionAndFBDoc(AlertsDoc) { implicit ctx =>

        val newValidation =
          <validation type="formula" id="length-constraint" level="warning" default-alert="false">
            <constraint expression="string-length() gt 10" argument=""/>
            <alert message="Length must be greater than 10" global="false">
              <message lang="fr" value="Longueur doit être plus grande que 10"/>
            </alert>
          </validation>

        writeAlertsAndValidationsAsXML(Control1, "", globalAlertAsXML, List(newValidation))
        assertAlertsXML(List(newValidation), readConstraintValidationsAsXML(Control1))
      }
    }
  }

  describe("Multiple validations") {
    it("must read back multiple validations") {
      withActionAndFBDoc(AlertsDoc) { implicit ctx =>

        val newValidations = List(
          <validation type="formula" id="length5-constraint" level="error" default-alert="false">
            <constraint expression="string-length() gt 5" argument=""/>
            <alert message="Length must be greater than 5" global="false">
              <message lang="fr" value="Longueur doit être plus grande que 5"/>
            </alert>
          </validation>,
          <validation type="formula" id="length10-constraint" level="warning" default-alert="false">
            <constraint expression="string-length() gt 10" argument=""/>
            <alert message="Length must be greater than 10" global="false">
              <message lang="fr" value="Longueur doit être plus grande que 10"/>
            </alert>
          </validation>
        )

        writeAlertsAndValidationsAsXML(Control1, "", globalAlertAsXML, newValidations map elemToNodeInfo)
        assertAlertsXML(newValidations, readConstraintValidationsAsXML(Control1))
      }
    }
  }

  describe("Remove alert in middle") {
    it("two validations and one validation") {
      withActionAndFBDoc(AlertsDoc) { implicit ctx =>

        val defaultAlertAsXML = AlertDetails.fromForm(Control1).head.toXML(FormBuilder.currentLang)

        locally {

          val twoValidations = List(
            <validation type="formula" id="length5-constraint" level="error" default-alert="false">
              <constraint expression="string-length() gt 5" argument=""/>
              <alert message="Length must be greater than 5" global="false">
                <message lang="fr" value="Longueur doit être plus grande que 5"/>
              </alert>
            </validation>,
            <validation type="formula" id="length10-constraint" level="warning" default-alert="false">
              <constraint expression="string-length() gt 10" argument=""/>
              <alert message="Length must be greater than 10" global="false">
                <message lang="fr" value="Longueur doit être plus grande que 10"/>
              </alert>
            </validation>
          )

          writeAlertsAndValidationsAsXML(Control1, "", defaultAlertAsXML, twoValidations map elemToNodeInfo)
          assertAlertsXML(twoValidations, readConstraintValidationsAsXML(Control1))

          val expectedResources: Document =
            <resources>
              <resource xml:lang="en">
                <section-1>
                  <label/>
                </section-1>
                <control-1>
                  <label/>
                  <hint/>
                  <alert>Length must be greater than 5</alert>
                  <alert>Length must be greater than 10</alert>
                  <alert>Alert for en</alert>
                </control-1>
              </resource>
              <resource xml:lang="fr">
                <section-1>
                  <label/>
                </section-1>
                <control-1>
                  <label/>
                  <hint/>
                  <alert>Longueur doit être plus grande que 5</alert>
                  <alert>Longueur doit être plus grande que 10</alert>
                  <alert>Alert for fr</alert>
                </control-1>
              </resource>
            </resources>.toDocument

          assertXMLDocumentsIgnoreNamespacesInScope(expectedResources, TransformerUtils.tinyTreeToDom4j(currentResources parent * head))
        }

        locally {
          val oneValidation = List(
            <validation type="formula" id="length10-constraint" level="warning" default-alert="false">
              <constraint expression="string-length() gt 10" argument=""/>
              <alert message="Length must be greater than 10" global="false">
                <message lang="fr" value="Longueur doit être plus grande que 10"/>
              </alert>
            </validation>
          )

          writeAlertsAndValidationsAsXML(Control1, "", defaultAlertAsXML, oneValidation map elemToNodeInfo)
          assertAlertsXML(oneValidation, readConstraintValidationsAsXML(Control1))

          val expectedResources: Document =
            <resources>
              <resource xml:lang="en">
                <section-1>
                  <label/>
                </section-1>
                <control-1>
                  <label/>
                  <hint/>
                  <alert>Length must be greater than 10</alert>
                  <alert>Alert for en</alert>
                </control-1>
              </resource>
              <resource xml:lang="fr">
                <section-1>
                  <label/>
                </section-1>
                <control-1>
                  <label/>
                  <hint/>
                  <alert>Longueur doit être plus grande que 10</alert>
                  <alert>Alert for fr</alert>
                </control-1>
              </resource>
            </resources>.toDocument

          assertXMLDocumentsIgnoreNamespacesInScope(expectedResources, TransformerUtils.tinyTreeToDom4j(currentResources parent * head))
        }
      }
    }
  }

  describe("Default alert") {
    it("must use local default alert") {
      withActionAndFBDoc(AlertsDoc) { implicit ctx =>

        val defaultAlertAsXML = AlertDetails.fromForm(Control1).head.toXML(FormBuilder.currentLang)

        writeAlertsAndValidationsAsXML(Control1, "", defaultAlertAsXML, Nil)
        assert("$form-resources/control-1/alert" === (getControlLhhat(Control1, "alert") att "ref" stringValue))
      }
    }

    it("must use global default alert") {
      withActionAndFBDoc(AlertsDoc) { implicit ctx =>

        val defaultAlertAsXML = AlertDetails.fromForm(Control1).head.toXML(FormBuilder.currentLang)

        writeAlertsAndValidationsAsXML(Control1, "", globalAlertAsXML, Nil)
        assert("$fr-resources/detail/labels/alert" === (getControlLhhat(Control1, "alert") att "ref" stringValue))
      }
    }
  }

  describe("Single constraint without custom alert") {
    it("must read back the constraint") {
      withActionAndFBDoc(AlertsDoc) { implicit ctx =>

        val newValidation =
          <validation type="formula" id="length5-constraint" level="error" default-alert="true">
            <constraint expression="string-length() gt 5" argument=""/>
            <alert message="" global="false"/>
          </validation>

        writeAlertsAndValidationsAsXML(Control1, "", globalAlertAsXML, List(newValidation))

        val expected =
          <validation type="formula" id="" level="error" default-alert="true">
            <constraint expression="string-length() gt 5" argument=""/>
            <alert message="" global="false"/>
          </validation>

        assertAlertsXML(List(expected), readConstraintValidationsAsXML(Control1))

        // No elements inserted under the bind
        val bind = findBindByName(ctx.formDefinitionRootElem, Control1).toList
        assert(bind child * isEmpty)
      }
    }
  }

  describe("Single constraint with custom alert") {
    it("must read back the constraint") {
      withActionAndFBDoc(AlertsDoc) { implicit ctx =>

        val newValidation =
          <validation type="formula" id="length5-constraint" level="error" default-alert="false">
            <constraint expression="string-length() gt 5" argument=""/>
            <alert message="Length must be greater than 5" global="false">
              <message lang="fr" value="Longueur doit être plus grande que 5"/>
            </alert>
          </validation>

        writeAlertsAndValidationsAsXML(Control1, "", globalAlertAsXML, List(newValidation))

        val expected =
          <validation type="formula" id="length5-constraint" level="error" default-alert="false">
            <constraint expression="string-length() gt 5" argument=""/>
            <alert message="Length must be greater than 5" global="false">
              <message lang="fr" value="Longueur doit être plus grande que 5"/>
            </alert>
          </validation>

        assertAlertsXML(List(expected), readConstraintValidationsAsXML(Control1))

        // One element inserted under the bind
        val bind = findBindByName(ctx.formDefinitionRootElem, Control1).toList
        assert(1 ===(bind child * size))
      }
    }
  }

  describe("Required and datatype validations") {
    it("required string") {
      withActionAndFBDoc(AlertsDoc) { implicit ctx =>

        val bind = findBindByName(ctx.formDefinitionRootElem, Control1).toList

        val newValidations = List(
          <validation type="required" level="error" default-alert="true">
            <required>true()</required>
            <alert message="" global="false"/>
          </validation>,
          <validation type="datatype" level="error" default-alert="true">
            <builtin-type>string</builtin-type>
            <builtin-type-required>false</builtin-type-required>
            <schema-type/>
            <alert message="" global="false"/>
          </validation>
        )

        writeAlertsAndValidationsAsXML(Control1, "", globalAlertAsXML, newValidations map elemToNodeInfo)

        assertAlertsXML(newValidations, readValidationsAsXML(Control1))

        assert("true()" === (bind att "required" stringValue))
        assert(bind att "type" isEmpty)

        assert(RequiredValidation(None, Left(true), None) === RequiredValidation.fromForm(Control1))
      }
    }

    it("optional decimal") {
      withActionAndFBDoc(AlertsDoc) { implicit ctx =>

        val bind = findBindByName(ctx.formDefinitionRootElem, Control1).toList

        val newValidations = List(
          <validation type="required" level="error" default-alert="true">
            <required>false()</required>
            <alert message="" global="false"/>
          </validation>,
          <validation type="datatype" level="error" default-alert="true">
            <builtin-type>decimal</builtin-type>
            <builtin-type-required>false</builtin-type-required>
            <schema-type/>
            <alert message="" global="false"/>
          </validation>
        )

        writeAlertsAndValidationsAsXML(Control1, "", globalAlertAsXML, newValidations map elemToNodeInfo)
        assertAlertsXML(newValidations, readValidationsAsXML(Control1))

        assert(bind att "required" isEmpty)
        assert("xf:decimal" === (bind att "type" stringValue))

        assert(RequiredValidation(None, Left(false), None) === RequiredValidation.fromForm(Control1))
      }
    }

    it("required decimal") {
      withActionAndFBDoc(AlertsDoc) { implicit ctx =>

        val bind = findBindByName(ctx.formDefinitionRootElem, Control1).toList

        val newValidations = List(
          <validation type="required" level="error" default-alert="true">
            <required>true()</required>
            <alert message="" global="false"/>
          </validation>,
          <validation type="datatype" level="error" default-alert="true">
            <builtin-type>decimal</builtin-type>
            <builtin-type-required>true</builtin-type-required>
            <schema-type/>
            <alert message="" global="false"/>
          </validation>
        )

        writeAlertsAndValidationsAsXML(Control1, "", globalAlertAsXML, newValidations map elemToNodeInfo)
        assertAlertsXML(newValidations, readValidationsAsXML(Control1))

        assert("true()"     === (bind att "required" stringValue))
        assert("xs:decimal" === (bind att "type" stringValue))

        assert(RequiredValidation(None, Left(true), None) === RequiredValidation.fromForm(Control1))
      }
    }

    it("formula for required") {
      withActionAndFBDoc(AlertsDoc) { implicit ctx =>

        val bind = findBindByName(ctx.formDefinitionRootElem, Control1).toList
        val newValidations = List(
          <validation type="required" level="error" default-alert="true">
            <required>../foo = 'bar'</required>
            <alert message="" global="false"/>
          </validation>
        )

        writeAlertsAndValidationsAsXML(Control1, "", globalAlertAsXML, newValidations map elemToNodeInfo)

        assert(RequiredValidation(None, Right("../foo = 'bar'"), None) === RequiredValidation.fromForm(Control1))
      }
    }

    it("required decimal and custom alert") {
      withActionAndFBDoc(AlertsDoc) { implicit ctx =>

        val bind = findBindByName(ctx.formDefinitionRootElem, Control1).toList

        locally {
          val newValidations = List(
            <validation type="required" level="error" default-alert="false">
              <required>true()</required>
              <alert message="This is required!" global="false">
                <message lang="fr" value="Ce champ est requis !"/>
              </alert>
            </validation>,
            <validation id="validation-4-validation" type="datatype" level="error" default-alert="false">
              <builtin-type>decimal</builtin-type>
              <builtin-type-required>true</builtin-type-required>
              <schema-type/>
              <alert message="This must be a decimal!" global="false">
                <message lang="fr" value="Ce champ doit être une valeur décimale !"/>
              </alert>
            </validation>
          )

          writeAlertsAndValidationsAsXML(Control1, "", globalAlertAsXML, newValidations map elemToNodeInfo)

          assertAlertsXML(newValidations, readValidationsAsXML(Control1))

          val expectedRequiredValidation =
            RequiredValidation(
              Some("validation-1-validation"),
              Left(true),
              Some(
                AlertDetails(
                  Some("validation-1-validation"),
                  List("en" -> "This is required!", "fr" -> "Ce champ est requis !"),
                  global = false
                )
              )
            )

          assert(expectedRequiredValidation === RequiredValidation.fromForm(Control1))

          val expectedDatatypeValidation =
            DatatypeValidation(
              Some("validation-4-validation"),
              Left(XMLConstants.XS_DECIMAL_QNAME, true),
              Some(
                AlertDetails(
                  Some("validation-4-validation"),
                  List("en" -> "This must be a decimal!", "fr" -> "Ce champ doit être une valeur décimale !"),
                  global = false
                )
              )
            )

          assert(expectedDatatypeValidation === DatatypeValidation.fromForm(Control1))
        }
      }
    }
  }

  describe("Schema type") {
    it("must read back the XML Schema datatype") {
      withActionAndFBDoc(SchemaDoc) { implicit ctx =>

        val bind = findBindByName(ctx.formDefinitionRootElem, Control1).toList

        val newValidations = List(
          <validation type="required" level="error" default-alert="true">
            <required>true()</required>
            <alert message="" global="false"/>
          </validation>,
          <validation type="datatype" level="error" default-alert="true">
            <builtin-type/>
            <builtin-type-required/>
            <schema-type>foo:email</schema-type>
            <alert message="" global="false"/>
          </validation>
        )

        writeAlertsAndValidationsAsXML(Control1, "", globalAlertAsXML, newValidations map elemToNodeInfo)
        assertAlertsXML(newValidations, readValidationsAsXML(Control1))

        assert("true()"    === (bind att "required" stringValue))
        assert("foo:email" === (bind att "type" stringValue))
      }
    }
  }

  describe("Schema prefix") {
    it("must find the schema prefix") {
      withActionAndFBDoc(SchemaDoc) { implicit ctx =>
        assert(Some("foo") === findSchemaPrefix(ctx.formDefinitionRootElem))
      }
    }
  }

  describe("Schema type without namespace") {
    it("must set a datatype without prefix") {
      withActionAndFBDoc(SchemaNoNamespaceDoc) { implicit ctx =>

        val bind = findBindByName(ctx.formDefinitionRootElem, Control1).toList

        val newValidations = List(
          <validation type="required" level="error" default-alert="true">
            <required>true()</required>
            <alert message="" global="false"/>
          </validation>,
          <validation type="datatype" level="error" default-alert="true">
            <builtin-type/>
            <builtin-type-required/>
            <schema-type>rating</schema-type>
            <alert message="" global="false"/>
          </validation>
        )

        writeAlertsAndValidationsAsXML(Control1, "", globalAlertAsXML, newValidations map elemToNodeInfo)
        assertAlertsXML(newValidations, readValidationsAsXML(Control1))

        assert("true()" === (bind att "required" stringValue))
        assert("rating" === (bind att "type" stringValue))
      }
    }
  }

  describe("Schema prefix without namespace") {
    it("must not find an XML Schema prefix") {
      withActionAndFBDoc(SchemaNoNamespaceDoc) { implicit ctx =>
        assert(None === findSchemaPrefix(ctx.formDefinitionRootElem))
      }
    }
  }

  private def globalAlert     (implicit ctx: FormBuilderDocContext) = AlertDetails(None, List(FormBuilder.currentLang -> ""), global = true)
  private def globalAlertAsXML(implicit ctx: FormBuilderDocContext) = globalAlert.toXML(FormBuilder.currentLang)

  private def readConstraintValidationsAsXML(controlName: String)(implicit ctx: FormBuilderDocContext) =
    ConstraintValidation.fromForm(controlName) map
    (a => a.toXML(FormBuilder.currentLang): NodeInfo) toArray

  private def assertAlertsXML(left: List[sx.Elem], right: Seq[NodeInfo]): Unit = {

    left zip right foreach {
      case (l, r) => assertXMLDocumentsIgnoreNamespacesInScope(l.toDocument, TransformerUtils.tinyTreeToDom4j(r))
    }

    assert(left.size === right.size)
  }
}
