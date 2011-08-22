/**
 * Copyright (C) 2011 Orbeon, Inc.
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

import org.scalatest.junit.AssertionsForJUnit
import org.junit.Test
import org.orbeon.oxf.processor.ProcessorUtils
import org.orbeon.oxf.test.DocumentTestBase
import org.orbeon.saxon.dom4j.DocumentWrapper
import org.orbeon.oxf.fb.FormBuilderFunctions._
import org.orbeon.oxf.fb.GridOps._
import org.orbeon.oxf.fb.SectionOps._
import org.orbeon.oxf.fb.ToolboxOps._
import org.orbeon.oxf.fb.ControlOps._
import org.orbeon.oxf.fb.ContainerOps._
import org.scalatest.mock.MockitoSugar
import org.orbeon.oxf.xforms.action.XFormsActionInterpreter
import org.orbeon.oxf.util.{IndentedLogger, XPathCache}
import org.orbeon.oxf.xforms.processor.XFormsServer
import org.mockito.{Matchers, Mockito}
import org.orbeon.oxf.xforms.xbl.XBLContainer
import org.orbeon.scaxon.XML._
import org.orbeon.oxf.xforms.action.XFormsAPI._
import org.orbeon.oxf.xforms.{XFormsStaticState, XFormsModel, XFormsInstance, XFormsContainingDocument}
import org.orbeon.oxf.xml.dom4j.Dom4jUtils
import org.orbeon.oxf.xml.TransformerUtils
import collection.JavaConverters._
import org.orbeon.saxon.om.{SequenceIterator, EmptyIterator, NodeInfo}

class FormBuilderFunctionsTest extends DocumentTestBase with AssertionsForJUnit with MockitoSugar {

    def getDocument(documentURL: String) = ProcessorUtils.createDocumentFromURL(documentURL, null)

    private def getNewDoc = {
        // Make a copy because the document is cached behind the scene, and we don't want the original to be mutated
        val template = Dom4jUtils.createDocumentCopyElement(getDocument("oxf:/forms/orbeon/builder/form/template.xml").getRootElement)
        new DocumentWrapper(template, null, XPathCache.getGlobalConfiguration)
    }

    private val control1 = "control-1"
    private val control2 = "control-2"
    private val control3 = "control-3"
    private val section1 = "section-1"
    private val section2 = "section-2"

    @Test def modelInstanceBodyElements() {
        val doc = getNewDoc

        assert(findModelElement(doc).getDisplayName === "xforms:model")
        assert(hasId(findModelElement(doc), "fr-form-model"))

        assert(name(formInstanceRoot(doc).parent.get) === "xforms:instance")

        assert(findBodyElement(doc).getDisplayName === "xhtml:body")
    }

    @Test def nameAndId() {

        val doc = getNewDoc

        // Basic functions
        assert(controlName(controlId(control1)) === control1)
        assert(controlName(bindId(control1)) === control1)

        // Find control element
        assert(findControlByName(doc, control1).get.getDisplayName === "xforms:input")
        assert(hasId(findControlByName(doc, control1).get, controlId(control1)))
    }

    @Test def controlElements() {
        val doc = getNewDoc

        // Find bind element
        assert(findBindByName(doc, control1).get.getDisplayName === "xforms:bind")
        assert(hasId(findBindByName(doc, control1).get, bindId(control1)))

        // Check content of value holder
        assert(findDataHolder(doc, control1).isDefined)
        assert(findDataHolder(doc, control1).get.getStringValue === "")

        // TODO
        // controlResourceHolders
    }

    @Test def sectionName() {
        val doc = getNewDoc

        assert(findSectionName(doc, control1).get === section1)
        assert(getControlNameOption(doc \\ "*:section" head).get === section1)
    }

    private def mockActionInterpreter(doc: DocumentWrapper) = {

        // This mocks just what's needed so that the tests won't choke

        val model = mock[XFormsModel]
        Mockito when model.getId thenReturn "fr-form-model"
        Mockito when model.getEffectiveId thenReturn "fr-form-model"
        Mockito when model.getVariable("metadata-instance") thenReturn EmptyIterator.getInstance

        // Not sure how to make it so that each call to getVariable returns a new, fresh iterator
        val selectedCellElement = elementInfo("selected-cell")
        Mockito when model.getVariable("selected-cell") thenReturn new SequenceIterator {
            def next() = selectedCellElement
            def current() = selectedCellElement
            def position() = 0
            def close() {}
            def getAnother = this
            def getProperties = 0
        }

        val xblContainer = mock[XBLContainer]

        val instance = mock[XFormsInstance]
        Mockito when instance.getModel(Matchers.any[XFormsContainingDocument]) thenReturn model
        Mockito when instance.getXBLContainer(Matchers.any[XFormsContainingDocument]) thenReturn xblContainer
        Mockito when instance.getDocumentInfo thenReturn doc

        val staticState = mock[XFormsStaticState]
        Mockito when staticState.documentWrapper thenReturn doc

        val document = mock[XFormsContainingDocument]
        Mockito when document.getStaticState thenReturn staticState
        Mockito when document.getInstanceForNode(Matchers.any[NodeInfo]) thenReturn instance
        Mockito when document.getModels thenReturn Seq(model).asJava

        val actionInterpreter = mock[XFormsActionInterpreter]
        Mockito when actionInterpreter.getContainingDocument thenReturn document
        Mockito when actionInterpreter.getXBLContainer thenReturn xblContainer
        Mockito when actionInterpreter.getIndentedLogger thenReturn new IndentedLogger(XFormsServer.getLogger, "action")

        actionInterpreter
    }

    def withActionAndDoc(body: NodeInfo => Any) {
        val doc = getNewDoc
        scalaAction(mockActionInterpreter(doc)) {
            initializeGrids(doc)
            body(doc)
        }
    }

    @Test def newBinds() {
        withActionAndDoc { doc =>
            ensureBinds(doc, Seq(section1, control2), false)

            assert(findBindByName(doc, control2).get.getDisplayName === "xforms:bind")
            assert(hasId(findBindByName(doc, control2).get, bindId(control2)))

            ensureBinds(doc, Seq(section2, "grid-1", control3), false)

            assert(findBindByName(doc, control3).get.getDisplayName === "xforms:bind")
            assert(hasId(findBindByName(doc, control3).get, bindId(control3)))
        }
    }

    @Test def findNextId() {
        val doc = getNewDoc

        assert(nextId(doc, "control") === 2)
        assert(nextId(doc, "section") === 2)

        // TODO: test more collisions
    }

    @Test def containers() {

        val doc = getNewDoc

        val firstTd = findBodyElement(doc) \\ "*:grid" \\ "*:td" head

        val containers = findAncestorContainers(firstTd)

        assert(localname(containers(0)) === "grid")
        assert(localname(containers(1)) === "section")

        assert(findContainerNames(firstTd) === Seq("section-1"))

    }

    // Select the first grid td (assume there is one)
    def selectFirstTd(doc: NodeInfo) {
        selectTd(findBodyElement(doc) \\ "*:grid" \\ "*:td" head)
    }

    @Test def insertControl() {
        withActionAndDoc { doc =>

            val binding = <binding element="xforms|input" xmlns:xforms="http://www.w3.org/2002/xforms"/>

            // Insert a new control into the next empty td
            selectFirstTd(doc)
            insertNewControl(doc, binding)

            // Test result
            assert(hasId(findControlByName(doc, "control-2").get, controlId("control-2")))

            val newlySelectedTd = findSelectedTd(doc)
            assert(newlySelectedTd.isDefined)
            assert(newlySelectedTd.get \ * \@ "id" === controlId("control-2"))

            val containerNames = findContainerNames(newlySelectedTd.get)
            assert(containerNames == Seq("section-1"))

            // NOTE: We should maybe just compare the XML for holders, binds, and resources
            val dataHolder = findDataHolder(doc, "control-2")
            assert(dataHolder.isDefined)
            assert(name(dataHolder.get precedingSibling * head) === "control-1")

            val controlBind = findBindByName(doc, "control-2").get
            assert(hasId(controlBind, bindId("control-2")))
            assert((controlBind precedingSibling * att "id") === bindId("control-1"))
            
            assert(formResourcesRoot(doc) \ "resource" \ "control-2" nonEmpty)

//            println(TransformerUtils.tinyTreeToString(doc))
        }
    }

    @Test def insertRepeat() {
        withActionAndDoc { doc =>

            // Insert a new repeated grid after the current grid
            selectFirstTd(doc)
            insertNewRepeat(doc)

            def assertNewRepeat() {

                val newlySelectedTd = findSelectedTd(doc)
                assert(newlySelectedTd.isDefined)
                assert((newlySelectedTd flatMap (_.parent) flatMap (_.parent) get) \@ "id" === gridId("grid-1"))

                val containerNames = findContainerNames(newlySelectedTd.get)
                assert(containerNames === Seq("section-1", "grid-1"))

                // NOTE: We should maybe just compare the XML for holders, binds, and resources
                val dataHolder = findDataHolder(doc, containerNames.last)
                assert(dataHolder.isDefined)
                assert(name(dataHolder.get precedingSibling * head) === "control-1")

                val controlBind = findBindByName(doc, "grid-1").get
                assert(hasId(controlBind, bindId("grid-1")))
                assert((controlBind precedingSibling * att "id") === bindId("control-1"))

                assert(findModelElement(doc) \ "*:instance" filter(hasId(_, "grid-1-template")) nonEmpty)
            }
            assertNewRepeat()

            // Insert a new control
            val binding = <binding element="xforms|input" xmlns:xforms="http://www.w3.org/2002/xforms"/>
            insertNewControl(doc, binding)

            // Test result
            def assertNewControl() {

                val newlySelectedTd = findSelectedTd(doc)
                assert(newlySelectedTd.isDefined)
                assert(newlySelectedTd.get \ * \@ "id" === controlId("control-2"))

                val containerNames = findContainerNames(newlySelectedTd.get)
                assert(containerNames === Seq("section-1", "grid-1"))

                assert(hasId(findControlByName(doc, "control-2").get, controlId("control-2")))

                // NOTE: We should maybe just compare the XML for holders, binds, and resources
                val dataHolder = findDataHolder(doc, "control-2")
                assert(dataHolder.isDefined)
                assert(dataHolder.get precedingSibling * isEmpty)
                assert(name(dataHolder.get.parent.get) === "grid-1")

                val controlBind = findBindByName(doc, "control-2").get
                assert(hasId(controlBind, bindId("control-2")))
                assert(hasId(controlBind.parent.get, bindId("grid-1")))

                assert(formResourcesRoot(doc) \ "resource" \ "control-2" nonEmpty)

                val templateHolder = templateRoot(doc, "grid-1").get \ "control-2" headOption
                
                assert(templateHolder.isDefined)
                assert(templateHolder.get precedingSibling * isEmpty)
                assert(name(templateHolder.get.parent.get) === "grid-1")
            }
            assertNewControl()

            println(TransformerUtils.tinyTreeToString(doc))
        }
    }

    @Test def rowspans() {

        val grid: NodeInfo =
            <grid>
                <tr><td id="11"/><td id="12" rowspan="2"/><td id="13"/></tr>
                <tr><td id="21" rowspan="2"/><td id="23"/></tr>
                <tr><td id="32"/><td id="33"/></tr>
            </grid>

        def td(id: String) = grid \\ * filter (hasId(_, id)) head

        val expected = Seq(
            Seq(Cell(td("11"), 1, false), Cell(td("12"), 2, false), Cell(td("13"), 1, false)),
            Seq(Cell(td("21"), 2, false), Cell(td("12"), 1, true),  Cell(td("23"), 1, false)),
            Seq(Cell(td("21"), 1, true),  Cell(td("32"), 1, false), Cell(td("33"), 1, false))
        )

        val trs = grid \ "tr"
        for ((expected, index) <- expected.zipWithIndex)
            yield assert(getRowCells(trs(index)) === expected)
    }

    @Test def testPrecedingNameForControl() {

        val section: NodeInfo =
            <section>
                <grid>
                    <tr><td><input id="control-11-control"/></td></tr>
                </grid>
                <grid id="grid-2-grid">
                    <tr><td><input id="control-21-control"/></td></tr>
                </grid>
                <grid>
                    <tr><td><input id="control-31-control"/></td></tr>
                </grid>
            </section>

        val controls = section \\ "*:td" \ * filter (_ \@ "id" nonEmpty)

        val expected = Seq(None, Some("grid-2"), Some("grid-2"))
        val actual = controls map (precedingControlNameInSectionForControl(_))

        assert(actual === expected)
    }

    @Test def testPrecedingNameForGrid() {

        val section: NodeInfo =
            <section>
                <grid>
                    <tr><td><input id="control-11-control"/></td></tr>
                </grid>
                <grid id="grid-2-grid">
                    <tr><td><input id="control-21-control"/></td></tr>
                </grid>
                <grid>
                    <tr><td><input id="control-31-control"/></td></tr>
                </grid>
            </section>

        val grids = section \\ "*:grid"

        val expected = Seq(Some("control-11"), Some("grid-2"), Some("control-31"))
        val actual = grids map (precedingControlNameInSectionForGrid(_, true))

        assert(actual === expected)
    }

//    @Test def insertHolders() {
//
//    }
//
//    @Test def ensureDataHolder() {
//
//    }
//
//    @Test def renameControl() {
//
//    }
//
//    @Test def ensureEmptyTd() {
//
//    }
//
//    @Test def insertRowBelow() {
//
//    }
//
//    @Test def renameBind() {
//
//    }
}