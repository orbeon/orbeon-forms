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

import org.orbeon.oxf.fb.FormBuilder._
import org.orbeon.oxf.fb.ToolboxOps.insertNewControl
import org.orbeon.oxf.fr.FormRunner._
import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport}
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.NodeConversions._
import org.orbeon.scaxon.SimplePath._
import org.scalatest.funspec.AnyFunSpecLike

class RepeatedSectionsTest
  extends DocumentTestBase
     with ResourceManagerSupport
     with AnyFunSpecLike
     with FormBuilderSupport {

  val Doc = "oxf:/org/orbeon/oxf/fr/template-for-repeated-sections.xhtml"

  describe("Model instance body elements") {
    it("must enable repeat") {
      withActionAndFBDoc(Doc) { implicit ctx =>

        val doc = ctx.formDefinitionRootElem

        setRepeatProperties("my-section", repeat = true, userCanAddRemove = true, usePaging = false, "", "", "", "", applyDefaults = false, "")

        val expected =
          elemToDom4j(
            <form>
              <my-section>
                <my-section-iteration>
                  <grid-1>
                    <my-input/>
                  </grid-1>
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
                <grid-2>
                  <other-input/>
                </grid-2>
              </other-section>
            </form>
          )

        assertXMLElementsIgnoreNamespacesInScopeCollapse(expected.getRootElement, unsafeUnwrapElement(ctx.dataRootElem))
      }
    }

    it("must rename section") {
      withActionAndFBDoc(Doc) { implicit ctx =>

        val doc = ctx.formDefinitionRootElem

        setRepeatProperties("my-section", repeat = true, userCanAddRemove = true, usePaging = false, "", "", "", "", applyDefaults = false, "")
        renameControlIterationIfNeeded("my-section", "foo", None, None)
        renameControlIfNeeded("my-section", "foo")

        val expected =
          elemToDom4j(
            <form>
              <foo>
                <foo-iteration>
                  <grid-1>
                    <my-input/>
                  </grid-1>
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
                <grid-2>
                  <other-input/>
                </grid-2>
              </other-section>
            </form>
          )

        assertXMLElementsIgnoreNamespacesInScopeCollapse(expected.getRootElement, unsafeUnwrapElement(ctx.dataRootElem))
      }
    }

    it("must support custom iteration element name") {
      withActionAndFBDoc(Doc) { implicit ctx =>

        val doc = ctx.formDefinitionRootElem

        setRepeatProperties("my-section", repeat = true, userCanAddRemove = true, usePaging = false, "", "", "", "", applyDefaults = false, "")
        renameControlIterationIfNeeded("my-section", "foo", None, Some("bar"))

        val expected =
          elemToDom4j(
            <form>
              <my-section>
                <bar>
                  <grid-1>
                    <my-input/>
                  </grid-1>
                  <my-grid>
                    <my-grid-iteration>
                      <my-textarea/>
                    </my-grid-iteration>
                    <my-grid-iteration>
                      <my-textarea/>
                    </my-grid-iteration>
                  </my-grid>
                </bar>
              </my-section>
              <other-section>
                <grid-2>
                  <other-input/>
                </grid-2>
              </other-section>
            </form>
          )

        assertXMLElementsIgnoreNamespacesInScopeCollapse(expected.getRootElement, unsafeUnwrapElement(ctx.dataRootElem))
      }
    }

    it("must change min/max") {
      withActionAndFBDoc(Doc) { implicit ctx =>

        val doc = ctx.formDefinitionRootElem

        setRepeatProperties("my-section", repeat = true, userCanAddRemove = true, usePaging = false, "1", "2", "", "", applyDefaults = false, "")

        val section = findControlByName(doc, "my-section").get

        assert("1" === section.attValue("min"))
        assert("2" === section.attValue("max"))

        assert("1" === getNormalizedMin(doc, "my-section"))
        assert(Some("2") === getNormalizedMax(doc, "my-section"))
      }
    }

    it("must change calculated min/max") {
      withActionAndFBDoc(Doc) { implicit ctx =>

        val doc = ctx.formDefinitionRootElem

        setRepeatProperties("my-section", repeat = true, userCanAddRemove = true, usePaging = false, "1 + 1", "count(//*[contains(@foo, '{')])", "", "", applyDefaults = false, "")

        val section = findControlByName(doc, "my-section").get

        assert("{1 + 1}" === section.attValue("min"))
        assert("{count(//*[contains(@foo, '{{')])}" === section.attValue("max"))

        assert("1 + 1" === getNormalizedMin(doc, "my-section"))
        assert(Some("count(//*[contains(@foo, '{')])") === getNormalizedMax(doc, "my-section"))
      }
    }

    it("must change freeze") {
      withActionAndFBDoc(Doc) { implicit ctx =>

        val doc = ctx.formDefinitionRootElem

        setRepeatProperties("my-section", repeat = true, userCanAddRemove = true, usePaging = false, "3", "4", "2", "", applyDefaults = false, "")

        val section = findControlByName(doc, "my-section").get

        assert("3" === section.attValue("min"))
        assert("4" === section.attValue("max"))
        assert("2" === section.attValue("freeze"))

        assert("3" === getNormalizedMin(doc, "my-section"))
        assert(Some("4") === getNormalizedMax(doc, "my-section"))
      }
    }

    it("must move section into it") {
      withActionAndFBDoc(Doc) { implicit ctx =>

        val doc = ctx.formDefinitionRootElem

        setRepeatProperties("my-section", repeat = true, userCanAddRemove = true, usePaging = false, "", "", "", "", applyDefaults = false, "")
        moveSectionRight(findControlByName(doc, "other-section").get)

        val expected =
          elemToDom4j(
            <form>
              <my-section>
                <my-section-iteration>
                  <grid-1>
                    <my-input/>
                  </grid-1>
                  <my-grid>
                    <my-grid-iteration>
                      <my-textarea/>
                    </my-grid-iteration>
                    <my-grid-iteration>
                      <my-textarea/>
                    </my-grid-iteration>
                  </my-grid>
                  <other-section>
                    <grid-2>
                  <other-input/>
                </grid-2>
                  </other-section>
                </my-section-iteration>
              </my-section>
            </form>
          )

        assertXMLElementsIgnoreNamespacesInScopeCollapse(expected.getRootElement, unsafeUnwrapElement(ctx.dataRootElem))
      }
    }

    it("must disable repeat") {
      withActionAndFBDoc(Doc) { implicit ctx =>

        val doc = ctx.formDefinitionRootElem

        setRepeatProperties("my-section", repeat = true, userCanAddRemove = true,  usePaging = false, "", "", "", "", applyDefaults = false, "")
        setRepeatProperties("my-section", repeat = false, userCanAddRemove = true, usePaging = false, "", "", "", "", applyDefaults = false, "")

        val expected =
          elemToDom4j(
            <form>
              <my-section>
                <grid-1>
                    <my-input/>
                  </grid-1>
                <my-grid>
                  <my-grid-iteration>
                    <my-textarea/>
                  </my-grid-iteration>
                  <my-grid-iteration>
                      <my-textarea/>
                    </my-grid-iteration>
                </my-grid>
              </my-section>
              <other-section>
                <grid-2>
                  <other-input/>
                </grid-2>
              </other-section>
            </form>
          )

        assertXMLElementsIgnoreNamespacesInScopeCollapse(expected.getRootElement, unsafeUnwrapElement(ctx.dataRootElem))

        assert("0" === getNormalizedMin(doc, "foo"))
        assert(None === getNormalizedMax(doc, "foo"))
      }
    }
  }

  describe("Initial iterations") {

    def templateRootElementFor(doc: NodeInfo, name: String) =
      unsafeUnwrapElement(findTemplateInstance(doc, name).get / * head)

    it("must enable repeat") {
      withActionAndFBDoc(Doc) { implicit ctx =>

        val doc = ctx.formDefinitionRootElem

        setRepeatProperties("my-section", repeat = true, userCanAddRemove = true, usePaging = false, "", "", "", "", applyDefaults = false, "")

        // Expect 1 iteration of `my-grid`
        val expected =
          elemToDom4j(
            <my-section-iteration>
              <grid-1>
                <my-input/>
              </grid-1>
              <my-grid>
                <my-grid-iteration>
                  <my-textarea/>
                </my-grid-iteration>
              </my-grid>
            </my-section-iteration>
          )

        assertXMLElementsIgnoreNamespacesInScopeCollapse(
          expected.getRootElement,
          templateRootElementFor(doc, "my-section")
        )
      }
    }

    it("must switch grid to `fb:initial-iterations=\"first\"`") {
      withActionAndFBDoc(Doc) { implicit ctx =>

        val doc = ctx.formDefinitionRootElem

        setRepeatProperties("my-section", repeat = true, userCanAddRemove = true, usePaging = false, "", "", "", "", applyDefaults = false, "")
        setRepeatProperties("my-grid",    repeat = true, userCanAddRemove = true, usePaging = false, "", "", "", "", applyDefaults = false, "first")

        // Expect 2 iterations of `my-grid`
        val expected =
          elemToDom4j(
            <my-section-iteration>
              <grid-1>
                <my-input/>
              </grid-1>
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
          templateRootElementFor(doc, "my-section")
        )
      }
    }

    it("must insert control within grid") {
      withActionAndFBDoc(Doc) { implicit ctx =>

        val doc = ctx.formDefinitionRootElem

        setRepeatProperties("my-section", repeat = true, userCanAddRemove = true, usePaging = false, "", "", "", "", applyDefaults = false, "")
        setRepeatProperties("my-grid",    repeat = true, userCanAddRemove = true, usePaging = false, "", "", "", "", applyDefaults = false, "first")

        val myTextareaCell = findControlByName(doc, "my-textarea").get.parentUnsafe

        FormBuilder.selectCell(myTextareaCell)

        val binding = <binding element="xf|input" xmlns:xf="http://www.w3.org/2002/xforms"/>

        insertNewControl(binding)

        // Expect new control in 2 iterations in the template
        val expected =
          elemToDom4j(
            <my-section-iteration>
              <grid-1>
                <my-input/>
              </grid-1>
              <my-grid>
                <my-grid-iteration>
                  <my-textarea/>
                  <control-1/>
                </my-grid-iteration>
                <my-grid-iteration>
                  <my-textarea/>
                  <control-1/>
                </my-grid-iteration>
              </my-grid>
            </my-section-iteration>
          )

        assertXMLElementsIgnoreNamespacesInScopeCollapse(
          expected.getRootElement,
          templateRootElementFor(doc, "my-section")
        )
      }
    }

    it("must switch grid back to no `fb:initial-iterations`") {
      withActionAndFBDoc(Doc) { implicit ctx =>

        val doc = ctx.formDefinitionRootElem

        setRepeatProperties("my-section", repeat = true, userCanAddRemove = true, usePaging = false, "", "", "", "", applyDefaults = false, "")
        setRepeatProperties("my-grid",    repeat = true, userCanAddRemove = true, usePaging = false, "", "", "", "", applyDefaults = false, "")

        // Expect 1 iteration of `my-grid`
        val expected =
          elemToDom4j(
            <my-section-iteration>
              <grid-1>
                <my-input/>
              </grid-1>
              <my-grid>
                <my-grid-iteration>
                  <my-textarea/>
                </my-grid-iteration>
              </my-grid>
            </my-section-iteration>
          )

        assertXMLElementsIgnoreNamespacesInScopeCollapse(
          expected.getRootElement,
          templateRootElementFor(doc, "my-section")
        )
      }
    }

    // We could test more, including:
    //
    // - more nesting levels
    // - adding iterations/removing iterations from the grids/sections
  }
}