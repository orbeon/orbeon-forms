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
import org.orbeon.saxon.dom4j.{NodeWrapper, DocumentWrapper}
import org.orbeon.saxon.om.{SequenceIterator, EmptyIterator, NodeInfo}

class FormBuilderFunctionsTest extends DocumentTestBase with AssertionsForJUnit with MockitoSugar {

    val TemplateDoc = "oxf:/forms/orbeon/builder/form/template.xml"
    val SectionsGridsDoc = "oxf:/org/orbeon/oxf/fb/template-with-sections-grids.xhtml"
    
    def getDocument(documentURL: String) = ProcessorUtils.createDocumentFromURL(documentURL, null)

    def getNewDoc(url: String = TemplateDoc) = {
        // Make a copy because the document is cached behind the scene, and we don't want the original to be mutated
        val template = Dom4jUtils.createDocumentCopyElement(getDocument(url).getRootElement)
        new DocumentWrapper(template, null, XPathCache.getGlobalConfiguration)
    }

    private val control1 = "control-1"
    private val control2 = "control-2"
    private val control3 = "control-3"
    private val section1 = "section-1"
    private val section2 = "section-2"

    @Test def modelInstanceBodyElements() {
        val doc = getNewDoc()

        assert(findModelElement(doc).getDisplayName === "xforms:model")
        assert(hasId(findModelElement(doc), "fr-form-model"))

        assert(name(formInstanceRoot(doc).parent.get) === "xforms:instance")

        assert(qname(findFRBodyElement(doc)) === (FR → "body"))
    }

    @Test def nameAndId() {

        val doc = getNewDoc()

        // Basic functions
        assert(controlName(controlId(control1)) === control1)
        assert(controlName(bindId(control1)) === control1)

        // Find control element
        assert(qname(findControlByName(doc, control1).get) === (XF → "input"))
        assert(hasId(findControlByName(doc, control1).get, controlId(control1)))
    }

    @Test def controlElements() {
        val doc = getNewDoc()

        // Find bind element
        assert(qname(findBindByName(doc, control1).get) === (XF → "bind"))
        assert(hasId(findBindByName(doc, control1).get, bindId(control1)))

        // Check content of value holder
        assert(findDataHolder(doc, control1).isDefined)
        assert(findDataHolder(doc, control1).get.getStringValue === "")

        // TODO
        // controlResourceHolders
    }

    @Test def sectionName() {
        val doc = getNewDoc()

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
        val resources = inlineInstanceRootElement(doc, "fr-form-resources").get
        Mockito when model.getVariable("resources") thenReturn new SequenceIterator {
            def next() = resources
            def current() = resources
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
        Mockito when actionInterpreter.containingDocument thenReturn document
        Mockito when actionInterpreter.container thenReturn xblContainer
        Mockito when actionInterpreter.indentedLogger thenReturn new IndentedLogger(XFormsServer.getLogger, "action")

        actionInterpreter
    }

    def withActionAndDoc(doc: DocumentWrapper)(body: NodeInfo ⇒ Any) {
        val actionInterpreter = mockActionInterpreter(doc)
        withScalaAction(actionInterpreter) {
            withContainingDocument(actionInterpreter.containingDocument) {
                initializeGrids(doc)
                body(doc)
            }
        }
    }

    @Test def newBinds() {
        withActionAndDoc(getNewDoc()) { doc ⇒
            ensureBinds(doc, Seq(section1, control2), false)

            assert(qname(findBindByName(doc, control2).get) === (XF → "bind"))
            assert(hasId(findBindByName(doc, control2).get, bindId(control2)))

            ensureBinds(doc, Seq(section2, "grid-1", control3), false)

            assert(qname(findBindByName(doc, control3).get) === (XF → "bind"))
            assert(hasId(findBindByName(doc, control3).get, bindId(control3)))
        }
    }

    @Test def findNextId() {
        val doc = getNewDoc()

        assert(nextId(doc, "control") === "control-3-control")
        assert(nextId(doc, "section") === "section-3-section")

        // TODO: test more collisions
    }

    @Test def containers() {

        val doc = getNewDoc()

        val firstTd = findFRBodyElement(doc) \\ "*:grid" \\ "*:td" head

        val containers = findAncestorContainers(firstTd)

        assert(localname(containers(0)) === "grid")
        assert(localname(containers(1)) === "section")

        assert(findContainerNames(firstTd) === Seq("section-1"))

    }

    // Select the first grid td (assume there is one)
    def selectFirstTd(doc: NodeInfo) {
        selectTd(findFRBodyElement(doc) \\ "*:grid" \\ "*:td" head)
    }

    @Test def insertControl() {
        withActionAndDoc(getNewDoc()) { doc ⇒

            val binding = <binding element="xforms|input" xmlns:xforms="http://www.w3.org/2002/xforms"/>

            // Insert a new control into the next empty td
            selectFirstTd(doc)
            insertNewControl(doc, binding)

            // Test result
            assert(hasId(findControlByName(doc, "control-3").get, controlId("control-3")))

            val newlySelectedTd = findSelectedTd(doc)
            assert(newlySelectedTd.isDefined)
            assert(newlySelectedTd.get \ * \@ "id" === controlId("control-3"))

            val containerNames = findContainerNames(newlySelectedTd.get)
            assert(containerNames == Seq("section-1"))

            // NOTE: We should maybe just compare the XML for holders, binds, and resources
            val dataHolder = findDataHolder(doc, "control-3")
            assert(dataHolder.isDefined)
            assert(name(dataHolder.get precedingSibling * head) === "control-1")

            val controlBind = findBindByName(doc, "control-3").get
            assert(hasId(controlBind, bindId("control-3")))
            assert((controlBind precedingSibling * att "id") === bindId("control-1"))
            
            assert(formResourcesRoot \ "resource" \ "control-3" nonEmpty)

//            println(TransformerUtils.tinyTreeToString(doc))
        }
    }

    @Test def insertRepeat() {
        withActionAndDoc(getNewDoc()) { doc ⇒

            // Insert a new repeated grid after the current grid
            selectFirstTd(doc)
            insertNewRepeat(doc)

            def assertNewRepeat() {

                val newlySelectedTd = findSelectedTd(doc)
                assert(newlySelectedTd.isDefined)
                assert((newlySelectedTd flatMap (_.parent) flatMap (_.parent) get) \@ "id" === gridId("grid-3"))

                val containerNames = findContainerNames(newlySelectedTd.get)
                assert(containerNames === Seq("section-1", "grid-3"))

                // NOTE: We should maybe just compare the XML for holders, binds, and resources
                val dataHolder = findDataHolder(doc, containerNames.last)
                assert(dataHolder.isDefined)
                assert(name(dataHolder.get precedingSibling * head) === "control-1")

                val controlBind = findBindByName(doc, "grid-3").get
                assert(hasId(controlBind, bindId("grid-3")))
                assert((controlBind precedingSibling * att "id") === bindId("control-1"))

                assert(findModelElement(doc) \ "*:instance" filter(hasId(_, "grid-3-template")) nonEmpty)
            }
            assertNewRepeat()

            // Insert a new control
            val binding = <binding element="xforms|input" xmlns:xforms="http://www.w3.org/2002/xforms"/>
            insertNewControl(doc, binding)

            // Test result
            def assertNewControl() {

                val newlySelectedTd = findSelectedTd(doc)
                assert(newlySelectedTd.isDefined)
                assert(newlySelectedTd.get \ * \@ "id" === controlId("control-4"))

                val containerNames = findContainerNames(newlySelectedTd.get)
                assert(containerNames === Seq("section-1", "grid-3"))

                assert(hasId(findControlByName(doc, "control-4").get, controlId("control-4")))

                // NOTE: We should maybe just compare the XML for holders, binds, and resources
                val dataHolder = findDataHolder(doc, "control-4")
                assert(dataHolder.isDefined)
                assert(dataHolder.get precedingSibling * isEmpty)
                assert(name(dataHolder.get.parent.get) === "grid-3")

                val controlBind = findBindByName(doc, "control-4").get
                assert(hasId(controlBind, bindId("control-4")))
                assert(hasId(controlBind.parent.get, bindId("grid-3")))

                assert(formResourcesRoot \ "resource" \ "control-4" nonEmpty)

                val templateHolder = templateRoot(doc, "grid-3").get \ "control-4" headOption
                
                assert(templateHolder.isDefined)
                assert(templateHolder.get precedingSibling * isEmpty)
                assert(name(templateHolder.get.parent.get) === "grid-3")
            }
            assertNewControl()

            println(TransformerUtils.tinyTreeToString(doc))
        }
    }

    // Return a grid with some rowspans. The tree returned is mutable.
    // Include model and instance, because the code that looks for next ids relies on their presence.
    def gridWithRowspan: NodeInfo = {
        val doc = elemToDocumentInfo(
            <xh:html xmlns:xh="http://www.w3.org/1999/xhtml">
                <xh:head>
                    <xforms:model id="fr-form-model" xmlns:xforms="http://www.w3.org/2002/xforms">
                        <xforms:instance id="fr-form-instance"><form/></xforms:instance>
                    </xforms:model>
                </xh:head>
                <xh:body>
                    <fr:body xmlns:fr="http://orbeon.org/oxf/xml/form-runner">
                        <fr:grid>
                            <xh:tr><xh:td id="11"/><xh:td id="12" rowspan="2"/><xh:td id="13"/></xh:tr>
                            <xh:tr><xh:td id="21" rowspan="2"/><xh:td id="23"/></xh:tr>
                            <xh:tr><xh:td id="32"/><xh:td id="33"/></xh:tr>
                        </fr:grid>
                    </fr:body>
                </xh:body>
            </xh:html>, false)

        doc \\ "grid" head
    }

    def compareExpectedCells(grid: NodeInfo, expected: Seq[Seq[Cell]]) {
        val trs = grid \ "tr"
        for ((expected, index) ← expected.zipWithIndex)
            yield assert(getRowCells(trs(index)) === expected)
    }

    @Test def rowspanGetRowCells() {

        val grid = gridWithRowspan

        def td(id: String) = grid \\ * filter (hasId(_, id)) head

        val expected = Seq(
            Seq(Cell(td("11"), 1, false), Cell(td("12"), 2, false), Cell(td("13"), 1, false)),
            Seq(Cell(td("21"), 2, false), Cell(td("12"), 1, true),  Cell(td("23"), 1, false)),
            Seq(Cell(td("21"), 1, true),  Cell(td("32"), 1, false), Cell(td("33"), 1, false))
        )

        compareExpectedCells(grid, expected)
    }

    def rewrap(node: NodeInfo) = node match {
        case nodeWrapper: NodeWrapper ⇒ node.root.asInstanceOf[DocumentWrapper].wrap(nodeWrapper.getUnderlyingNode)
        case _ ⇒ node
    }

    @Test def rowspanInsertRowBelow() {

        val grid = gridWithRowspan

        // Insert one row below each existing row
        for (tr ← grid \ "tr" force)
            insertRowBelow(rewrap(tr)) // rewrap after mutation (it's dangerous to play with NodeInfo and mutation!)

        def td(id: String) = grid \\ * filter (hasId(_, id)) head

        val expected = Seq(
            Seq(Cell(td("11"),      1, false), Cell(td("12"),      3, false), Cell(td("13"),      1, false)),
            Seq(Cell(td("td-1-td"), 1, false), Cell(td("12"),      2, true),  Cell(td("td-2-td"), 1, false)),
            Seq(Cell(td("21"),      3, false), Cell(td("12"),      1, true),  Cell(td("23"),      1, false)),
            Seq(Cell(td("21"),      2, true),  Cell(td("td-3-td"), 1, false), Cell(td("td-4-td"), 1, false)),
            Seq(Cell(td("21"),      1, true),  Cell(td("32"),      1, false), Cell(td("33"),      1, false)),
            Seq(Cell(td("td-5-td"), 1, false), Cell(td("td-6-td"), 1, false), Cell(td("td-7-td"), 1, false))
        )

        compareExpectedCells(grid, expected)
    }

    def sectionWithGridAndControls: NodeInfo =
        <fr:section xmlns:fr="http://orbeon.org/oxf/xml/form-runner"
                    xmlns:xh="http://www.w3.org/1999/xhtml"
                    xmlns:xf="http://www.w3.org/2002/xforms">
            <fr:grid>
                <xh:tr><xh:td><xf:input id="control-11-control"/></xh:td></xh:tr>
            </fr:grid>
            <fr:grid id="grid-2-grid">
                <xh:tr><xh:td><xf:input id="control-21-control"/></xh:td></xh:tr>
            </fr:grid>
            <fr:grid>
                <xh:tr><xh:td><xf:input id="control-31-control"/></xh:td></xh:tr>
            </fr:grid>
        </fr:section>

    @Test def testPrecedingNameForControl() {

        val section = sectionWithGridAndControls

        val controls = section \\ "*:td" \ * filter (_ \@ "id" nonEmpty)

        val expected = Seq(None, Some("grid-2"), Some("grid-2"))
        val actual = controls map (precedingControlNameInSectionForControl(_))

        assert(actual === expected)
    }

    @Test def testPrecedingNameForGrid() {

        val section = sectionWithGridAndControls

        val grids = section \\ "*:grid"

        val expected = Seq(Some("control-11"), Some("grid-2"), Some("control-31"))
        val actual = grids map (precedingControlNameInSectionForGrid(_, true))

        assert(actual === expected)
    }
    
    def assertSelectedTdAfterDelete(beforeAfter: Seq[(String, String)])(delete: NodeInfo ⇒ Any) {

        // For before/after td ids: create a doc, call the delete function, and assert the resulting selected td
        def deleteRowCheckSelectedTd(beforeTdId: String, afterTdId: String) =
            withActionAndDoc(getNewDoc(SectionsGridsDoc)) { doc ⇒

                def getTd(id: String) = doc \\ "*:td" find (hasId(_, id)) head

                val beforeTd = getTd(beforeTdId)
                selectTd(beforeTd)
                delete(beforeTd)

                val actualSelectedId = findSelectedTd(doc) map (_ \@ "id" stringValue)
                
                assert(actualSelectedId === Some(afterTdId))
            }

        // Test all
        for ((beforeTdId, afterTdId) ← beforeAfter)
            deleteRowCheckSelectedTd(beforeTdId, afterTdId)
    }
    
    @Test def selectedTdAfterDeletedRow() = {

        // A few before/after ids of selected tds
        val beforeAfter = Seq(
            "1111" → "1121",    // first td
            "2222" → "2231",    // middle td
            "3333" → "3323",    // last td
            "2111" → "2121"     // first td of grid/section
        )

        assertSelectedTdAfterDelete(beforeAfter) { td ⇒
            deleteRow(td.parent.get)
        }
    }

    @Test def selectedTdAfterDeletedCol() = {
        
        // A few before/after ids of selected tds
        val beforeAfter = Seq(
            "1111" → "1112",    // first td
            "2222" → "2223",    // middle td
            "3333" → "3332",    // last td
            "2111" → "2112"     // first td of grid/section
        )

        assertSelectedTdAfterDelete(beforeAfter) { td ⇒
            deleteCol(getColTds(td) head)
        }
    }

    @Test def selectedTdAfterDeletedGrid() = {

        // A few before/after ids of selected tds
        val beforeAfter = Seq(
            "1111" → "1211",    // first td
            "2222" → "2311",    // middle td
            "3333" → "3233",    // last td
            "2111" → "2211"     // first td of grid/section
        )

        assertSelectedTdAfterDelete(beforeAfter) { td ⇒
            deleteGrid(getContainingGridOrRepeat(td))
        }
    }

    @Test def selectedTdAfterDeletedSection() = {

        // A few before/after ids of selected tds
        val beforeAfter = Seq(
            "1111" → "2111",    // first td
            "2222" → "3111",    // middle td
            "3333" → "2333",    // last td
            "2111" → "3111"     // first td of grid/section
        )

        assertSelectedTdAfterDelete(beforeAfter) { td ⇒
            deleteSection(findAncestorContainers(getContainingGridOrRepeat(td)) head)
        }
    }

    @Test def lastGridInSectionAndCanInsert() {
        withActionAndDoc(getNewDoc(TemplateDoc)) { doc ⇒

            // Initially can insert all
            assert(canInsertSection(doc) === true)
            assert(canInsertGrid(doc) === true)
            assert(canInsertControl(doc) === true)

            // Remove everything (assume top-level section with a single grid inside)
            childrenContainers(findFRBodyElement(doc)) foreach  { section ⇒
                assert(isLastGridInSection(childrenGrids(section).head) === true)
                deleteContainer(section)
            }

            // After everything is removed we can only insert a section (later: can also insert grid)
            assert(canInsertSection(doc) === true)
            assert(canInsertGrid(doc) === false)
            assert(canInsertControl(doc) === false)
        }
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