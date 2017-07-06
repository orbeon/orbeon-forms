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
import org.orbeon.oxf.fb.ToolboxOps._
import org.orbeon.oxf.test.DocumentTestBase
import org.orbeon.scaxon.NodeConversions._
import org.orbeon.scaxon.XML._
import org.scalatest.junit.AssertionsForJUnit

class RepeatedSectionsTest extends DocumentTestBase with FormBuilderSupport with AssertionsForJUnit {

  val Doc = "oxf:/org/orbeon/oxf/fr/template-for-repeated-sections.xhtml"

  @Test def modelInstanceBodyElements(): Unit =
    withActionAndFBDoc(Doc) { doc ⇒

      // Enable repeat
      locally {
        setRepeatProperties(doc, "my-section", repeat = true, "", "", "", applyDefaults = false, "")

        val expected =
          elemToDom4j(
            <form>
              <my-section>
                <my-section-iteration>
                  <my-input/>
                  <my-grid>
                    <my-grid-iteration>
                      <my-textarea/>
                    </my-grid-iteration>
                    <my-grid-iteration>
                      <my-textarea/>
                    </my-grid-iteration>
                  </my-grid>
                </my-section-iteration>
              </my-section>
              <other-section>
                <other-input/>
              </other-section>
            </form>
          )

        assertXMLElementsIgnoreNamespacesInScopeCollapse(expected.getRootElement, unsafeUnwrapElement(formInstanceRoot(doc)))
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
                    <my-grid-iteration>
                      <my-textarea/>
                    </my-grid-iteration>
                    <my-grid-iteration>
                      <my-textarea/>
                    </my-grid-iteration>
                  </my-grid>
                </foo-iteration>
              </foo>
              <other-section>
                <other-input/>
              </other-section>
            </form>
          )

        assertXMLElementsIgnoreNamespacesInScopeCollapse(expected.getRootElement, unsafeUnwrapElement(formInstanceRoot(doc)))
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
                    <my-grid-iteration>
                      <my-textarea/>
                    </my-grid-iteration>
                    <my-grid-iteration>
                      <my-textarea/>
                    </my-grid-iteration>
                  </my-grid>
                </bar>
              </foo>
              <other-section>
                <other-input/>
              </other-section>
            </form>
          )

        assertXMLElementsIgnoreNamespacesInScopeCollapse(expected.getRootElement, unsafeUnwrapElement(formInstanceRoot(doc)))
      }

      // Change min/max
      locally {
        setRepeatProperties(doc, "foo", repeat = true, "1", "2", "", applyDefaults = false, "")

        val section = findControlByName(doc, "foo").get

        assert("1" === section.attValue("min"))
        assert("2" === section.attValue("max"))

        assert("1" === getNormalizedMin(doc, "foo"))
        assert(Some("2") === getNormalizedMax(doc, "foo"))
      }

      // Change min/max
      locally {
        setRepeatProperties(doc, "foo", repeat = true, "1 + 1", "count(//*[contains(@foo, '{')])", "", applyDefaults = false, "")

        val section = findControlByName(doc, "foo").get

        assert("{1 + 1}" === section.attValue("min"))
        assert("{count(//*[contains(@foo, '{{')])}" === section.attValue("max"))

        assert("1 + 1" === getNormalizedMin(doc, "foo"))
        assert(Some("count(//*[contains(@foo, '{')])") === getNormalizedMax(doc, "foo"))
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
                    <my-grid-iteration>
                      <my-textarea/>
                    </my-grid-iteration>
                    <my-grid-iteration>
                      <my-textarea/>
                    </my-grid-iteration>
                  </my-grid>
                  <other-section>
                    <other-input/>
                  </other-section>
                </bar>
              </foo>
            </form>
          )

        assertXMLElementsIgnoreNamespacesInScopeCollapse(expected.getRootElement, unsafeUnwrapElement(formInstanceRoot(doc)))
      }

      // Disable repeat
      locally {
        setRepeatProperties(doc, "foo", repeat = false, "", "", "", applyDefaults = false, "")

        val expected =
          elemToDom4j(
            <form>
              <foo>
                <my-input/>
                <my-grid>
                  <my-grid-iteration>
                    <my-textarea/>
                  </my-grid-iteration>
                  <my-grid-iteration>
                      <my-textarea/>
                    </my-grid-iteration>
                </my-grid>
                <other-section>
                  <other-input/>
                </other-section>
              </foo>
            </form>
          )

        assertXMLElementsIgnoreNamespacesInScopeCollapse(expected.getRootElement, unsafeUnwrapElement(formInstanceRoot(doc)))

        assert("0" === getNormalizedMin(doc, "foo"))
        assert(None === getNormalizedMax(doc, "foo"))
      }
    }

  @Test def initialIterations(): Unit =
    withActionAndFBDoc(Doc) { doc ⇒

      def templateRootElementFor(name: String) =
        unsafeUnwrapElement(findTemplateInstance(doc, name).get / * head)

      // Enable repeat
      locally {
        setRepeatProperties(doc, "my-section", repeat = true, "", "", "", applyDefaults = false, "")

        // Expect 1 iteration of `my-grid`
        val expected =
          elemToDom4j(
            <my-section-iteration>
              <my-input/>
              <my-grid>
                <my-grid-iteration>
                  <my-textarea/>
                </my-grid-iteration>
              </my-grid>
            </my-section-iteration>
          )

        assertXMLElementsIgnoreNamespacesInScopeCollapse(
          expected.getRootElement,
          templateRootElementFor("my-section")
        )
      }

      // Switch grid to `fb:initial-iterations="first"`
      locally {
        setRepeatProperties(doc, "my-grid", repeat = true, "", "", "", applyDefaults = false, "first")

        // Expect 2 iterations of `my-grid`
        val expected =
          elemToDom4j(
            <my-section-iteration>
              <my-input/>
              <my-grid>
                <my-grid-iteration>
                  <my-textarea/>
                </my-grid-iteration>
                <my-grid-iteration>
                  <my-textarea/>
                </my-grid-iteration>
              </my-grid>
            </my-section-iteration>
          )

        assertXMLElementsIgnoreNamespacesInScopeCollapse(
          expected.getRootElement,
          templateRootElementFor("my-section")
        )
      }

      // Insert control within grid
      locally {

        val myTextareaTd = findControlByName(doc, "my-textarea").get parent * head

        insertColRight(myTextareaTd)
        selectTd(myTextareaTd)

        val binding = <binding element="xf|input" xmlns:xf="http://www.w3.org/2002/xforms"/>

        insertNewControl(doc, binding)

        // Expect new control in 2 iterations in the template
        val expected =
          elemToDom4j(
            <my-section-iteration>
              <my-input/>
              <my-grid>
                <my-grid-iteration>
                  <my-textarea/>
                  <control-9/>
                </my-grid-iteration>
                <my-grid-iteration>
                  <my-textarea/>
                  <control-9/>
                </my-grid-iteration>
              </my-grid>
            </my-section-iteration>
          )

        assertXMLElementsIgnoreNamespacesInScopeCollapse(
          expected.getRootElement,
          templateRootElementFor("my-section")
        )
      }

      // Switch grid back to no `fb:initial-iterations`
      locally {
        setRepeatProperties(doc, "my-grid", repeat = true, "", "", "", applyDefaults = false, "")

        // Expect 1 iteration of `my-grid`
        val expected =
          elemToDom4j(
            <my-section-iteration>
              <my-input/>
              <my-grid>
                <my-grid-iteration>
                  <my-textarea/>
                  <control-9/>
                </my-grid-iteration>
              </my-grid>
            </my-section-iteration>
          )

        assertXMLElementsIgnoreNamespacesInScopeCollapse(
          expected.getRootElement,
          templateRootElementFor("my-section")
        )
      }

      // We could test more, including:
      //
      // - more nesting levels
      // - adding iterations/removing iterations from the grids/sections
    }
}