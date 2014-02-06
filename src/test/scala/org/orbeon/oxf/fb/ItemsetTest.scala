/**
 * Copyright (C) 2014 Orbeon, Inc.
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

import collection.JavaConverters._
import org.junit.Test
import org.orbeon.oxf.test.DocumentTestBase
import org.orbeon.oxf.xml.Dom4j.elemToDocument
import org.orbeon.oxf.xml.TransformerUtils
import org.orbeon.scaxon.XML._
import org.scalatest.junit.AssertionsForJUnit
import scala.xml.Elem

class ItemsetTest extends DocumentTestBase with FormBuilderSupport with AssertionsForJUnit {

    val ItemsetsDoc = "oxf:/org/orbeon/oxf/fb/form-with-itemsets.xhtml"

    @Test def readAndWriteControlItems(): Unit =
        withActionAndFBDoc(ItemsetsDoc) { doc ⇒

            def assertControl(controlName: String, expectedItems: Seq[Elem]): Unit = {

                val actualItems =
                    FormBuilder.getControlItemsGroupedByValue(controlName).asScala map TransformerUtils.tinyTreeToDom4j

                for ((expected, actual) ← expectedItems.zipAll(actualItems, null, null))
                    assertXMLDocuments(expected, actual)
            }

            // Read itemsets
            locally {
                val expectedDropdownItems = List(
                    <item>
                        <label lang="en">First choice</label>
                        <label lang="fr">Premier choix</label>
                        <value>one</value>
                    </item>,
                    <item>
                        <label lang="en">Second choice</label>
                        <label lang="fr">Second choix</label>
                        <value>two</value>
                    </item>,
                    <item>
                        <label lang="en">Third choice</label>
                        <label lang="fr">Troisième choix</label>
                        <value>three</value>
                    </item>
                )

                val expectedRadioItems = List(
                    <item>
                        <label lang="en">First choice</label>
                        <hint  lang="en">Hint 1</hint>
                        <label lang="fr">Premier choix</label>
                        <hint  lang="fr">Suggestion 1</hint>
                        <value>one</value>
                    </item>,
                    <item>
                        <label lang="en">Second choice</label>
                        <hint  lang="en">Hint 2</hint>
                        <label lang="fr">Second choix</label>
                        <hint  lang="fr">Suggestion 2</hint>
                        <value>two</value>
                    </item>,
                    <item>
                        <label lang="en">Third choice</label>
                        <hint  lang="en">Hint 3</hint>
                        <label lang="fr">Troisième choix</label>
                        <hint  lang="fr">Suggestion 3</hint>
                        <value>three</value>
                    </item>
                )

                val controlNameExpected = List(
                    "dropdown" → expectedDropdownItems,
                    "radios"   → expectedRadioItems
                )

                for ((controlName, expected) ← controlNameExpected)
                    assertControl(controlName, expected)
            }

            // Update itemsets
            locally {
                val newDropdownItems =
                    <items>
                        <item>
                            <label lang="en">Third choice</label>
                            <label lang="fr">Troisième choix</label>
                            <value>three</value>
                        </item>
                        <item>
                            <label lang="en">First choice</label>
                            <label lang="fr">Premier choix</label>
                            <value>one</value>
                        </item>
                        <item>
                            <label lang="en">Fourth choice</label>
                            <label lang="fr">Quatrième choix</label>
                            <value>four</value>
                        </item>
                    </items>

                val newRadioItems =
                    <items>
                        <item>
                            <label lang="en">Third choice</label>
                            <hint  lang="en">Hint 3</hint>
                            <label lang="fr">Troisième choix</label>
                            <hint  lang="fr">Suggestion 3</hint>
                            <value>three</value>
                        </item>
                        <item>
                            <label lang="en">First choice</label>
                            <hint  lang="en">Hint 1</hint>
                            <label lang="fr">Premier choix</label>
                            <hint  lang="fr">Suggestion 1</hint>
                            <value>one</value>
                        </item>
                        <item>
                            <label lang="en">Fourth choice</label>
                            <hint  lang="en">Hint 4</hint>
                            <label lang="fr">Quatrième choix</label>
                            <hint  lang="fr">Suggestion 4</hint>
                            <value>four</value>
                        </item>
                    </items>

                val controlNameExpected = List(
                    "dropdown" → newDropdownItems,
                    "radios"   → newRadioItems
                )

                for ((controlName, expected) ← controlNameExpected) {
                    FormBuilder.setControlItems(controlName, expected)
                    assertControl(controlName, (expected \ "item").toSeq collect { case e: scala.xml.Elem ⇒ e })
                }
            }
        }
}
