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

import org.junit.Test
import org.orbeon.oxf.fb.FormBuilder._
import org.orbeon.oxf.test.DocumentTestBase
import org.orbeon.scaxon.XML._
import org.scalatest.junit.AssertionsForJUnit

class RepeatedSectionsTest extends DocumentTestBase with FormBuilderSupport with AssertionsForJUnit {

    val Doc = "oxf:/org/orbeon/oxf/fb/template-for-repeated-sections.xhtml"

    @Test def modelInstanceBodyElements(): Unit =
        withActionAndFBDoc(Doc) { doc ⇒

            // Enable repeat
            locally {
                setRepeatProperties(doc, "my-section", repeat = true, "", "", "")
    
                val expected =
                    elemToDom4j(
                        <form>
                            <my-section>
                                <my-section-iteration>
                                    <my-input/>
                                    <my-grid>
                                        <my-textarea/>
                                    </my-grid>
                                </my-section-iteration>
                            </my-section>
                            <other-section>
                                <other-input/>
                            </other-section>
                        </form>
                    )
    
                assertXMLElementsCollapse(expected.getRootElement, unwrapElement(formInstanceRoot(doc)))
            }

            // Rename section
            locally {
                renameControlIterationIfNeeded(doc, "my-section", "foo", "", "")
                renameControlIfNeeded(doc, "my-section", "foo")

                val expected =
                    elemToDom4j(
                        <form>
                            <foo>
                                <foo-iteration>
                                    <my-input/>
                                    <my-grid>
                                        <my-textarea/>
                                    </my-grid>
                                </foo-iteration>
                            </foo>
                            <other-section>
                                <other-input/>
                            </other-section>
                        </form>
                    )

                assertXMLElementsCollapse(expected.getRootElement, unwrapElement(formInstanceRoot(doc)))
            }

            // Custom iteration element name
            locally {
                renameControlIterationIfNeeded(doc, "foo", "", "", "bar")
                
                val expected =
                    elemToDom4j(
                        <form>
                            <foo>
                                <bar>
                                    <my-input/>
                                    <my-grid>
                                        <my-textarea/>
                                    </my-grid>
                                </bar>
                            </foo>
                            <other-section>
                                <other-input/>
                            </other-section>
                        </form>
                    )
                
                assertXMLElementsCollapse(expected.getRootElement, unwrapElement(formInstanceRoot(doc)))
            }

            // Change min/max
            locally {
                setRepeatProperties(doc, "foo", repeat = true, "1", "2", "")

                val section = findControlByName(doc, "foo").get

                assert("1" === section.attValue("min"))
                assert("2" === section.attValue("max"))
            }

            // Change min/max
            locally {
                setRepeatProperties(doc, "foo", repeat = true, "1 + 1", "count(//*[contains(@foo, '{')])", "")

                val section = findControlByName(doc, "foo").get

                assert("{1 + 1}" === section.attValue("min"))
                assert("{count(//*[contains(@foo, '{{')])}" === section.attValue("max"))
            }

            // Move section into it
            locally {
                moveSectionRight(findControlByName(doc, "other-section").get)

                val expected =
                    elemToDom4j(
                        <form>
                            <foo>
                                <bar>
                                    <my-input/>
                                    <my-grid>
                                        <my-textarea/>
                                    </my-grid>
                                    <other-section>
                                        <other-input/>
                                    </other-section>
                                </bar>
                            </foo>
                        </form>
                    )

                assertXMLElementsCollapse(expected.getRootElement, unwrapElement(formInstanceRoot(doc)))
            }

            // Disable repeat
            locally {
                setRepeatProperties(doc, "foo", repeat = false, "", "", "")

                val expected =
                    elemToDom4j(
                        <form>
                            <foo>
                                <my-input/>
                                <my-grid>
                                    <my-textarea/>
                                </my-grid>
                                <other-section>
                                    <other-input/>
                                </other-section>
                            </foo>
                        </form>
                    )
    
                assertXMLElementsCollapse(expected.getRootElement, unwrapElement(formInstanceRoot(doc)))
            }
        }
}