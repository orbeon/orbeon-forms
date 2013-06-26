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

import org.junit.Test
import org.orbeon.oxf.fb.FormBuilder._
import org.orbeon.oxf.test.DocumentTestBase
import org.orbeon.oxf.xml.dom4j.Dom4jUtils
import org.orbeon.oxf.xml.{Dom4j, TransformerUtils}
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.XML._
import org.scalatest.junit.AssertionsForJUnit
import scala.xml.Elem
import org.dom4j.Document

class AlertsAndConstraintsTest extends DocumentTestBase with FormBuilderSupport with AssertionsForJUnit {

    val AlertsDoc = "oxf:/org/orbeon/oxf/fb/template-with-alerts.xhtml"
    
    private val Control1 = "control-1"

    @Test def initialAlert() =
        withActionAndDoc(AlertsDoc) { doc ⇒

            // Read initial alert
            assert(List(AlertDetails(None, None, List("en" → "Alert for en", "fr" → "Alert for fr"))) === readAlertsAndConstraints(doc, Control1))

            // Read initial alert as XML
            val expected =
                <alert message="Alert for en" level="any" constraint-expression="" constraint-id="">
                    <message lang="fr" value="Alert for fr"/>
                </alert>

            assertAlertsXML(Array(expected), readAlertsAndConstraintsAsXML(doc, Control1))
        }

    @Test def warningConstraintAutomaticId() =
        withActionAndDoc(AlertsDoc) { doc ⇒
            val newAlert =
                <alert message="Length must be greater than 10" level="warning" constraint-expression="string-length() gt 10" constraint-id="">
                    <message lang="fr" value="Longueur doit être plus grande que 10"/>
                </alert>

            writeAlertsAndConstraints(doc, Control1, Array(newAlert))

            val expected =
                <alert message="Length must be greater than 10" level="warning" constraint-expression="string-length() gt 10" constraint-id="constraint-3-constraint">
                    <message lang="fr" value="Longueur doit être plus grande que 10"/>
                </alert>

            assertAlertsXML(Array(expected), readAlertsAndConstraintsAsXML(doc, Control1))
        }

    @Test def warningConstraintSpecifyId() =
        withActionAndDoc(AlertsDoc) { doc ⇒
            val newAlert =
                <alert message="Length must be greater than 10" level="warning" constraint-expression="string-length() gt 10" constraint-id="length-constraint">
                    <message lang="fr" value="Longueur doit être plus grande que 10"/>
                </alert>

            writeAlertsAndConstraints(doc, Control1, Array(newAlert))
            assertAlertsXML(Array(newAlert), readAlertsAndConstraintsAsXML(doc, Control1))
        }

    @Test def multipleAlerts() =
        withActionAndDoc(AlertsDoc) { doc ⇒
            val newAlerts = Array(
                <alert message="Alert for en" level="any" constraint-expression="" constraint-id="">
                    <message lang="fr" value="Alert for fr"/>
                </alert>,
                <alert message="Length must be greater than 10" level="warning" constraint-expression="string-length() gt 10" constraint-id="length-constraint">
                    <message lang="fr" value="Longueur doit être plus grande que 10"/>
                </alert>
            )

            writeAlertsAndConstraints(doc, Control1, newAlerts map elemToNodeInfo)
            assertAlertsXML(newAlerts, readAlertsAndConstraintsAsXML(doc, Control1))
        }

    @Test def removeAlertInMiddle() =
        withActionAndDoc(AlertsDoc) { doc ⇒
            locally {
                val threeAlerts = Array(
                    <alert message="Alert for en" level="any" constraint-expression="" constraint-id="">
                        <message lang="fr" value="Alert for fr"/>
                    </alert>,
                    <alert message="Length must be greater than 5" level="error" constraint-expression="string-length() gt 5" constraint-id="length5-constraint">
                        <message lang="fr" value="Longueur doit être plus grande que 5"/>
                    </alert>,
                    <alert message="Length must be greater than 10" level="warning" constraint-expression="string-length() gt 10" constraint-id="length10-constraint">
                        <message lang="fr" value="Longueur doit être plus grande que 10"/>
                    </alert>
                )

                writeAlertsAndConstraints(doc, Control1, threeAlerts map elemToNodeInfo)
                assertAlertsXML(threeAlerts, readAlertsAndConstraintsAsXML(doc, Control1))

                val expectedResources: Document =
                    <resources>
                        <resource xml:lang="en">
                            <section-1>
                                <label/>
                            </section-1>
                            <control-1>
                                <label/>
                                <hint/>
                                <alert>Alert for en</alert>
                                <alert>Length must be greater than 5</alert>
                                <alert>Length must be greater than 10</alert>
                            </control-1>
                        </resource>
                        <resource xml:lang="fr">
                            <section-1>
                                <label/>
                            </section-1>
                            <control-1>
                                <label/>
                                <hint/>
                                <alert>Alert for fr</alert>
                                <alert>Longueur doit être plus grande que 5</alert>
                                <alert>Longueur doit être plus grande que 10</alert>
                            </control-1>
                        </resource>
                    </resources>

                assertXMLDocuments(expectedResources, TransformerUtils.tinyTreeToDom4j2(currentResources parent * head))
            }

            locally {
                val twoAlerts = Array(
                    <alert message="Alert for en" level="any" constraint-expression="" constraint-id="">
                        <message lang="fr" value="Alert for fr"/>
                    </alert>,
                    <alert message="Length must be greater than 10" level="warning" constraint-expression="string-length() gt 10" constraint-id="length10-constraint">
                        <message lang="fr" value="Longueur doit être plus grande que 10"/>
                    </alert>
                )

                writeAlertsAndConstraints(doc, Control1, twoAlerts map elemToNodeInfo)
                assertAlertsXML(twoAlerts, readAlertsAndConstraintsAsXML(doc, Control1))

                val expectedResources: Document =
                    <resources>
                        <resource xml:lang="en">
                            <section-1>
                                <label/>
                            </section-1>
                            <control-1>
                                <label/>
                                <hint/>
                                <alert>Alert for en</alert>
                                <alert>Length must be greater than 10</alert>
                            </control-1>
                        </resource>
                        <resource xml:lang="fr">
                            <section-1>
                                <label/>
                            </section-1>
                            <control-1>
                                <label/>
                                <hint/>
                                <alert>Alert for fr</alert>
                                <alert>Longueur doit être plus grande que 10</alert>
                            </control-1>
                        </resource>
                    </resources>

                assertXMLDocuments(expectedResources, TransformerUtils.tinyTreeToDom4j2(currentResources parent * head))
            }
        }

    @Test def setSingleAllBlankAlert() =
        withActionAndDoc(AlertsDoc) { doc ⇒
            // Empty alert but not in all languages: must point to form resources
            locally {
                val newAlert =
                    <alert message="" level="warning" constraint-expression="string-length() gt 10" constraint-id="length-constraint">
                        <message lang="fr" value="Longueur doit être plus grande que 10"/>
                    </alert>

                writeAlertsAndConstraints(doc, Control1, Array(newAlert: NodeInfo))

                assertAlertsXML(Array(newAlert), readAlertsAndConstraintsAsXML(doc, Control1))
                assert("$form-resources/control-1/alert" === (getControlLHHA(doc, Control1, "alert") att "ref" stringValue))
            }

            // Empty alert in all languages: must point to global resources
            locally {
                val newAlert =
                    <alert message="" level="warning" constraint-expression="string-length() gt 10" constraint-id="length-constraint">
                        <message lang="fr" value=""/>
                    </alert>

                writeAlertsAndConstraints(doc, Control1, Array(newAlert))

                assertAlertsXML(Array(newAlert), readAlertsAndConstraintsAsXML(doc, Control1))
                assert("$fr-resources/detail/labels/alert" === (getControlLHHA(doc, Control1, "alert") att "ref" stringValue))
            }

            // Empty alert in all languages but more than one alert: must point to form resources
            locally {
                val newAlerts = Array(
                    <alert message="" level="error" constraint-expression="string-length() gt 5" constraint-id="length5-constraint">
                        <message lang="fr" value=""/>
                    </alert>,
                    <alert message="" level="warning" constraint-expression="string-length() gt 10" constraint-id="length10-constraint">
                        <message lang="fr" value=""/>
                    </alert>
                )

                writeAlertsAndConstraints(doc, Control1, newAlerts map elemToNodeInfo)

                assertAlertsXML(newAlerts, readAlertsAndConstraintsAsXML(doc, Control1))
                assert(List("$form-resources/control-1/alert[1]", "$form-resources/control-1/alert[2]") === (getControlLHHA(doc, Control1, "alert") att "ref" map (_.stringValue)))
            }
        }

    @Test def singleConstraintHasBindId() =
        withActionAndDoc(AlertsDoc) { doc ⇒

            val newAlert =
                <alert message="Length must be greater than 5" level="error" constraint-expression="string-length() gt 5" constraint-id="length5-constraint">
                    <message lang="fr" value="Longueur doit être plus grande que 5"/>
                </alert>

            writeAlertsAndConstraints(doc, Control1, Array(newAlert))

            val expected =
                <alert message="Length must be greater than 5" level="error" constraint-expression="string-length() gt 5" constraint-id="control-1-bind">
                    <message lang="fr" value="Longueur doit être plus grande que 5"/>
                </alert>

            assertAlertsXML(Array(expected), readAlertsAndConstraintsAsXML(doc, Control1))

            // No elements inserted under the bind
            val bind = findBindByName(doc, Control1).toList
            assert(bind child * isEmpty)
        }

    private def assertAlertsXML(left: Array[Elem], right: Array[NodeInfo]) {

        left zip right foreach {
            case (l, r) ⇒ assertXMLDocuments(elemToDocument(l), TransformerUtils.tinyTreeToDom4j2(r))
        }

        assert(left.size === right.size)
    }

    private def assertXMLDocuments(left: Document, right: Document) = {
        val result = Dom4j.compareDocumentsIgnoreNamespacesInScope(left, right)

        // So that we get a nicer message
        if (! result)
            assert(Dom4jUtils.domToPrettyString(left) === Dom4jUtils.domToPrettyString(right))
    }
}
