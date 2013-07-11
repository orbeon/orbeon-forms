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

import org.dom4j.Document
import org.junit.Test
import org.orbeon.oxf.fb.FormBuilder._
import org.orbeon.oxf.test.DocumentTestBase
import org.orbeon.oxf.xml.TransformerUtils
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.XML._
import org.scalatest.junit.AssertionsForJUnit
import scala.xml.Elem

class AlertsAndConstraintsTest extends DocumentTestBase with FormBuilderSupport with AssertionsForJUnit {

    val AlertsDoc = "oxf:/org/orbeon/oxf/fb/template-with-alerts.xhtml"
    
    private val Control1 = "control-1"

    @Test def initialAlert() =
        withActionAndDoc(AlertsDoc) { doc ⇒

            // Read initial alert
            val alertDetails = AlertDetails.fromForm(doc, Control1)
            assert(List(AlertDetails(None, List("en" → "Alert for en", "fr" → "Alert for fr"), global = false)) === alertDetails)

            // Read initial alert as XML
            val expected =
                <alert message="Alert for en" global="false">
                    <message lang="fr" value="Alert for fr"/>
                </alert>

            assertAlertsXML(Array(expected), alertDetails map (a ⇒ a.toXML(currentLang): NodeInfo) toArray)
        }

    @Test def warningConstraintAutomaticId() =
        withActionAndDoc(AlertsDoc) { doc ⇒
            val newValidation =
                <validation type="constraint" id="" level="warning" default-alert="false">
                    <constraint expression="string-length() gt 10"/>
                    <alert message="Length must be greater than 10" global="false">
                        <message lang="fr" value="Longueur doit être plus grande que 10"/>
                    </alert>
                </validation>
                
            writeAlertsAndValidationsAsXML(doc, Control1, globalAlertAsXML, Array(newValidation))

            val expected =
                <validation type="constraint" id="constraint-3-constraint" level="warning" default-alert="false">
                    <constraint expression="string-length() gt 10"/>
                    <alert message="Length must be greater than 10" global="false">
                        <message lang="fr" value="Longueur doit être plus grande que 10"/>
                    </alert>
                </validation>

            assertAlertsXML(Array(expected), readConstraintValidationsAsXML(doc, Control1))
        }

    @Test def warningConstraintSpecifyId() =
        withActionAndDoc(AlertsDoc) { doc ⇒
            val newValidation =
                <validation type="constraint" id="length-constraint" level="warning" default-alert="false">
                    <constraint expression="string-length() gt 10"/>
                    <alert message="Length must be greater than 10" global="false">
                        <message lang="fr" value="Longueur doit être plus grande que 10"/>
                    </alert>
                </validation>

            writeAlertsAndValidationsAsXML(doc, Control1, globalAlertAsXML, Array(newValidation))
            assertAlertsXML(Array(newValidation), readConstraintValidationsAsXML(doc, Control1))
        }

    @Test def multipleValidations() =
        withActionAndDoc(AlertsDoc) { doc ⇒
                
            val newValidations = Array(
                <validation type="constraint" id="length5-constraint" level="error" default-alert="false">
                    <constraint expression="string-length() gt 5"/>
                    <alert message="Length must be greater than 5" global="false">
                        <message lang="fr" value="Longueur doit être plus grande que 5"/>
                    </alert>
                </validation>,
                <validation type="constraint" id="length10-constraint" level="warning" default-alert="false">
                    <constraint expression="string-length() gt 10"/>
                    <alert message="Length must be greater than 10" global="false">
                        <message lang="fr" value="Longueur doit être plus grande que 10"/>
                    </alert>
                </validation>
            )

            writeAlertsAndValidationsAsXML(doc, Control1, globalAlertAsXML, newValidations map elemToNodeInfo)
            assertAlertsXML(newValidations, readConstraintValidationsAsXML(doc, Control1))
        }

    @Test def removeAlertInMiddle() =
        withActionAndDoc(AlertsDoc) { doc ⇒

            val defaultAlertAsXML = AlertDetails.fromForm(doc, Control1).head.toXML(currentLang)

            locally {

                val twoValidations = Array(
                    <validation type="constraint" id="length5-constraint" level="error" default-alert="false">
                        <constraint expression="string-length() gt 5"/>
                        <alert message="Length must be greater than 5" global="false">
                            <message lang="fr" value="Longueur doit être plus grande que 5"/>
                        </alert>
                    </validation>,
                    <validation type="constraint" id="length10-constraint" level="warning" default-alert="false">
                        <constraint expression="string-length() gt 10"/>
                        <alert message="Length must be greater than 10" global="false">
                            <message lang="fr" value="Longueur doit être plus grande que 10"/>
                        </alert>
                    </validation>
                )

                writeAlertsAndValidationsAsXML(doc, Control1, defaultAlertAsXML, twoValidations map elemToNodeInfo)
                assertAlertsXML(twoValidations, readConstraintValidationsAsXML(doc, Control1))

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
                    </resources>

                assertXMLDocuments(expectedResources, TransformerUtils.tinyTreeToDom4j(currentResources parent * head))
            }

            locally {
                val oneValidation = Array(
                    <validation type="constraint" id="length10-constraint" level="warning" default-alert="false">
                        <constraint expression="string-length() gt 10"/>
                        <alert message="Length must be greater than 10" global="false">
                            <message lang="fr" value="Longueur doit être plus grande que 10"/>
                        </alert>
                    </validation>
                )

                writeAlertsAndValidationsAsXML(doc, Control1, defaultAlertAsXML, oneValidation map elemToNodeInfo)
                assertAlertsXML(oneValidation, readConstraintValidationsAsXML(doc, Control1))

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
                    </resources>

                assertXMLDocuments(expectedResources, TransformerUtils.tinyTreeToDom4j(currentResources parent * head))
            }
        }

    @Test def defaultAlert() =
        withActionAndDoc(AlertsDoc) { doc ⇒

            val defaultAlertAsXML = AlertDetails.fromForm(doc, Control1).head.toXML(currentLang)

            // Local default alert
            locally {
                writeAlertsAndValidationsAsXML(doc, Control1, defaultAlertAsXML, Array())
                assert("$form-resources/control-1/alert" === (getControlLHHA(doc, Control1, "alert") att "ref" stringValue))
            }

            // Global default alert
            locally {
                writeAlertsAndValidationsAsXML(doc, Control1, globalAlertAsXML, Array())
                assert("$fr-resources/detail/labels/alert" === (getControlLHHA(doc, Control1, "alert") att "ref" stringValue))
            }
        }

    @Test def singleConstraintHasBindId() =
        withActionAndDoc(AlertsDoc) { doc ⇒

            val newValidation =
                <validation type="constraint" id="length5-constraint" level="error" default-alert="false">
                    <constraint expression="string-length() gt 5"/>
                    <alert message="Length must be greater than 5" global="false">
                        <message lang="fr" value="Longueur doit être plus grande que 5"/>
                    </alert>
                </validation>

            writeAlertsAndValidationsAsXML(doc, Control1, globalAlertAsXML, Array(newValidation))

            val expected =
                <validation type="constraint" id="" level="error" default-alert="false">
                    <constraint expression="string-length() gt 5"/>
                    <alert message="Length must be greater than 5" global="false">
                        <message lang="fr" value="Longueur doit être plus grande que 5"/>
                    </alert>
                </validation>

            assertAlertsXML(Array(expected), readConstraintValidationsAsXML(doc, Control1))

            // No elements inserted under the bind
            val bind = findBindByName(doc, Control1).toList
            assert(bind child * isEmpty)
        }

    @Test def requiredAndDatatypeValidations() =
        withActionAndDoc(AlertsDoc) { doc ⇒

            val bind = findBindByName(doc, Control1).toList

            locally {
                val newValidations = Array(
                    <validation type="required" level="error">
                        <required>true</required>
                    </validation>,
                    <validation type="datatype" level="error">
                        <builtin-type>string</builtin-type>
                        <schema-type/>
                        <required>false</required>
                    </validation>
                )

                writeAlertsAndValidationsAsXML(doc, Control1, globalAlertAsXML, newValidations map elemToNodeInfo)
                assertAlertsXML(newValidations, readValidationsAsXML(doc, Control1))

                assert("true()" === (bind att "required" stringValue))
                assert(bind att "type" isEmpty)
            }

            locally {
                val newValidations = Array(
                    <validation type="required" level="error">
                        <required>false</required>
                    </validation>,
                    <validation type="datatype" level="error">
                        <builtin-type>decimal</builtin-type>
                        <schema-type/>
                        <required>false</required>
                    </validation>
                )

                writeAlertsAndValidationsAsXML(doc, Control1, globalAlertAsXML, newValidations map elemToNodeInfo)
                assertAlertsXML(newValidations, readValidationsAsXML(doc, Control1))

                assert(bind att "required" isEmpty)
                assert("xf:decimal" === (bind att "type" stringValue))
            }

            locally {
                val newValidations = Array(
                    <validation type="required" level="error">
                        <required>true</required>
                    </validation>,
                    <validation type="datatype" level="error">
                        <builtin-type>decimal</builtin-type>
                        <schema-type/>
                        <required>true</required>
                    </validation>
                )

                writeAlertsAndValidationsAsXML(doc, Control1, globalAlertAsXML, newValidations map elemToNodeInfo)
                assertAlertsXML(newValidations, readValidationsAsXML(doc, Control1))

                assert("true()"     === (bind att "required" stringValue))
                assert("xs:decimal" === (bind att "type" stringValue))
            }
        }

    @Test def schemaType() =
        withActionAndDoc(AlertsDoc) { doc ⇒

            val bind = findBindByName(doc, Control1).toList

            // See https://github.com/orbeon/orbeon-forms/issues/1113
            val newValidations = Array(
                <validation type="required" level="error">
                    <required>true</required>
                </validation>,
                <validation type="datatype" level="error">
                    <builtin-type/>
                    <schema-type>fr:bar</schema-type>
                    <required>false</required>
                </validation>
            )

            writeAlertsAndValidationsAsXML(doc, Control1, globalAlertAsXML, newValidations map elemToNodeInfo)
            assertAlertsXML(newValidations, readValidationsAsXML(doc, Control1))

            assert("true()"  === (bind att "required" stringValue))
            assert("fr:bar" === (bind att "type" stringValue))
        }
    
    private def globalAlert      = AlertDetails(None, List(currentLang → ""), global = true)
    private def globalAlertAsXML = globalAlert.toXML(currentLang)

    private def readConstraintValidationsAsXML(inDoc: NodeInfo, controlName: String) =
        ConstraintValidation.fromForm(inDoc, controlName) map
        (a ⇒ a.toXML(currentLang): NodeInfo) toArray

    private def assertAlertsXML(left: Array[Elem], right: Array[NodeInfo]): Unit = {

        left zip right foreach {
            case (l, r) ⇒ assertXMLDocuments(elemToDocument(l), TransformerUtils.tinyTreeToDom4j(r))
        }

        assert(left.size === right.size)
    }
}
