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
package org.orbeon.oxf.fr

import org.dom4j.Document
import org.junit.Test
import org.orbeon.oxf.properties.PropertyStore
import org.orbeon.oxf.test.DocumentTestBase
import org.orbeon.oxf.xml.Dom4j
import org.scalatest.junit.AssertionsForJUnit

class ResourcesPatcherTest extends DocumentTestBase with AssertionsForJUnit {

    @Test def patchingScenarios(): Unit = {

        val propertySet = {
            val properties: Document =
                <properties xmlns:xs="http://www.w3.org/2001/XMLSchema">
                    <property as="xs:string"  name="oxf.fr.resource.*.*.en.detail.buttons.existing" value="Existing"/>
                    <property as="xs:string"  name="oxf.fr.resource.*.*.fr.detail.buttons.existing" value="Existant"/>
                    <property as="xs:string"  name="oxf.fr.resource.*.*.de.detail.buttons.existing" value="Vorhanden"/>
                    <property as="xs:string"  name="oxf.fr.resource.*.*.en.detail.labels.missing"   value="Missing"/>
                    <property as="xs:string"  name="oxf.fr.resource.*.*.fr.detail.labels.missing"   value="Manquant"/>
                    <property as="xs:string"  name="oxf.fr.resource.*.*.de.detail.labels.missing"   value="Vermisst"/>
                    <property as="xs:string"  name="oxf.fr.resource.*.*.*.detail.buttons.acme"      value="Acme Existing"/>
                    <property as="xs:string"  name="oxf.fr.resource.*.*.*.detail.labels.acme"       value="Acme Missing"/>
                </properties>

            new PropertyStore(properties).getGlobalPropertySet
        }

        def newDoc: Document =
            <resources>
                <resource xml:lang="en">
                    <detail>
                        <buttons>
                            <existing>OVERRIDE ME</existing>
                            <acme>OVERRIDE ME</acme>
                        </buttons>
                    </detail>
                </resource>
                <resource xml:lang="fr">
                    <detail>
                        <buttons>
                            <existing>OVERRIDE ME</existing>
                            <acme>OVERRIDE ME</acme>
                        </buttons>
                    </detail>
                </resource>
            </resources>

        val expected: Document =
            <resources>
                <resource xml:lang="en">
                    <detail>
                        <buttons>
                            <existing>Existing</existing>
                            <acme>Acme Existing</acme>
                        </buttons>
                        <labels>
                            <missing>Missing</missing>
                            <acme>Acme Missing</acme>
                        </labels>
                    </detail>
                </resource>
                <resource xml:lang="fr">
                    <detail>
                        <buttons>
                            <existing>Existant</existing>
                            <acme>Acme Existing</acme>
                        </buttons>
                        <labels>
                            <missing>Manquant</missing>
                            <acme>Acme Missing</acme>
                        </labels>
                    </detail>
                </resource>
            </resources>

        val initial = newDoc

        ResourcesPatcher.transform(initial, "*", "*")(propertySet)

        assert(Dom4j.compareDocumentsIgnoreNamespacesInScope(initial, expected))
    }
}
