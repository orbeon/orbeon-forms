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

import org.junit.Test
import org.orbeon.dom.saxon
import org.orbeon.dom.saxon.DocumentWrapper
import org.orbeon.oxf.fb.FormBuilder._
import org.orbeon.oxf.fb.ToolboxOps._
import org.orbeon.oxf.fr.FormRunner
import org.orbeon.oxf.fr.XMLNames._
import org.orbeon.oxf.test.DocumentTestBase
import org.orbeon.oxf.util.{IndentedLogger, LoggerFactory}
import org.orbeon.oxf.xforms.action.XFormsAPI._
import org.orbeon.oxf.xforms.library.XFormsFunctionLibrary
import org.orbeon.oxf.xml.Dom4j.elemToDocument
import org.orbeon.oxf.xml.TransformerUtils
import org.orbeon.saxon.om._
import org.orbeon.scaxon.XML._
import org.scalatest.junit.AssertionsForJUnit

class FormBuilderFunctionsTest extends DocumentTestBase with FormBuilderSupport with AssertionsForJUnit {

  val SectionsGridsDoc   = "oxf:/org/orbeon/oxf/fb/template-with-sections-grids.xhtml"
  val SectionsRepeatsDoc = "oxf:/org/orbeon/oxf/fb/template-with-sections-repeats.xhtml"
  val RowspansDoc        = "oxf:/org/orbeon/oxf/fb/template-with-rowspans.xhtml"

  private val Control1 = "control-1"
  private val Control2 = "control-2"
  private val Control3 = "control-3"
  private val Section1 = "section-1"
  private val Section2 = "section-2"

  @Test def modelInstanceBodyElements(): Unit =
    withActionAndFBDoc(TemplateDoc) { doc ⇒
      assert(findModelElement(doc).getDisplayName === "xf:model")
      assert(hasIdValue(findModelElement(doc), "fr-form-model"))

      assert((formInstanceRoot(doc) parent * head).name === "xf:instance")

      assert(findFRBodyElement(doc).uriQualifiedName === (XF → "group"))
    }

  @Test def nameAndId(): Unit =
    withActionAndFBDoc(TemplateDoc) { doc ⇒

      // Basic functions
      assert(controlNameFromId(controlId(Control1)) === Control1)
      assert(controlNameFromId(bindId(Control1)) === Control1)

      // Find control element
      assert(findControlByName(doc, Control1).get.uriQualifiedName === (XF → "input"))
      assert(hasIdValue(findControlByName(doc, Control1).get, controlId(Control1)))
    }

  @Test def controlElements(): Unit =
    withActionAndFBDoc(TemplateDoc) { doc ⇒

      // Find bind element
      assert(findBindByName(doc, Control1).get.uriQualifiedName === (XF → "bind"))
      assert(hasIdValue(findBindByName(doc, Control1).get, bindId(Control1)))

      // Check content of value holder
      assert(findDataHolders(doc, Control1).length == 1)
      assert(findDataHolders(doc, Control1).head.getStringValue === "")

      // TODO
      // controlResourceHolders
    }

  @Test def sectionName(): Unit =
    withActionAndFBDoc(TemplateDoc) { doc ⇒
      assert(findSectionName(doc, Control1).get === Section1)
      assert(getControlNameOpt(doc \\ "*:section" head).get === Section1)
    }

  @Test def newBinds(): Unit =
    withActionAndFBDoc(TemplateDoc) { doc ⇒
      ensureBinds(doc, Seq(Section1, Control2))

      assert(findBindByName(doc, Control2).get.uriQualifiedName === (XF → "bind"))
      assert(hasIdValue(findBindByName(doc, Control2).get, bindId(Control2)))

      ensureBinds(doc, Seq(Section2, "grid-1", Control3))

      assert(findBindByName(doc, Control3).get.uriQualifiedName === (XF → "bind"))
      assert(hasIdValue(findBindByName(doc, Control3).get, bindId(Control3)))
    }

  @Test def findNextId(): Unit =
    withActionAndFBDoc(TemplateDoc) { doc ⇒
      assert(nextId(doc, "control") === "control-3-control")
      assert(nextId(doc, "section") === "section-3-section")

      // TODO: test more collisions
    }

  @Test def containers(): Unit =
    withActionAndFBDoc(TemplateDoc) { doc ⇒
      val firstTd = findFRBodyElement(doc) \\ "*:grid" \\ "*:td" head

      val containers = findAncestorContainers(firstTd)

      assert(containers(0).localname === "grid")
      assert(containers(1).localname === "section")

      assert(findContainerNamesForModel(firstTd) === Seq("section-1"))
    }

  // Select the first grid td (assume there is one)
  def selectFirstTd(doc: NodeInfo): Unit =
    selectTd(findFRBodyElement(doc) \\ "*:grid" \\ "*:td" head)

  @Test def insertControl(): Unit =
    withActionAndFBDoc(TemplateDoc) { doc ⇒

      val binding = <binding element="xf|input" xmlns:xf="http://www.w3.org/2002/xforms"/>

      // Insert a new control into the next empty td
      selectFirstTd(doc)
      val newControlNameOption = insertNewControl(doc, binding)

      // Check the control's name
      assert(newControlNameOption === Some("control-3"))
      val newControlName = newControlNameOption.get

      // Test result
      assert(hasIdValue(findControlByName(doc, newControlName).get, controlId(newControlName)))

      val newlySelectedTd = findSelectedTd(doc)
      assert(newlySelectedTd.isDefined)
      assert(newlySelectedTd.get \ * \@ "id" === controlId(newControlName))

      val containerNames = findContainerNamesForModel(newlySelectedTd.get)
      assert(containerNames == Seq("section-1"))

      // NOTE: We should maybe just compare the XML for holders, binds, and resources
      val dataHolder = assertDataHolder(doc, newControlName)
      assert((dataHolder.head precedingSibling * head).name === "control-1")

      val controlBind = findBindByName(doc, newControlName).get
      assert(hasIdValue(controlBind, bindId(newControlName)))
      assert((controlBind precedingSibling * att "id") === bindId("control-1"))

      assert(formResourcesRoot \ "resource" \ newControlName nonEmpty)
    }

  @Test def insertExplanation(): Unit =
    withActionAndFBDoc(TemplateDoc) { doc ⇒

      // Insert explanation control
      val frExplanation = {
        val selectionControls = TransformerUtils.urlToTinyTree("oxf:/xbl/orbeon/explanation/explanation.xbl")
        val explanationBinding = selectionControls.rootElement.child("binding").head
        ToolboxOps.insertNewControl(doc, explanationBinding)
        doc.descendant("*:explanation").head
      }

      // Check resource holder just contains <text>, taken from the XBL metadata
      locally {
        val explanationResourceHolder = FormBuilder.resourcesRoot.child("resource").child(*).last
        val actual   = <holder> { explanationResourceHolder.child(*) map nodeInfoToElem } </holder>
        val expected = <holder><text/></holder>
        assertXMLDocumentsIgnoreNamespacesInScope(actual, expected)
      }

      // Check that the <fr:text ref=""> points to the corresponding <text> resource
      locally {
        val controlName = FormRunner.controlNameFromId(frExplanation.id)
        val actualRef = frExplanation.child("*:text").head.attValue("ref")
        val expectedRef = "$form-resources/" ++ controlName ++ "/text"
        assert(actualRef === expectedRef)
      }
    }

  @Test def insertRepeat(): Unit =
    withActionAndFBDoc(TemplateDoc) { doc ⇒

      // Insert a new repeated grid after the current grid
      selectFirstTd(doc)
      val newRepeatNameOption = insertNewRepeatedGrid(doc)

      assert(newRepeatNameOption === Some("grid-3"))
      val newRepeatName          = newRepeatNameOption.get
      val newRepeatIterationName = defaultIterationName(newRepeatName)

      locally {

        val newlySelectedTd = findSelectedTd(doc)
        assert(newlySelectedTd.isDefined)
        assert((newlySelectedTd flatMap (_ parent * headOption) flatMap (_ parent * headOption) head) \@ "id" === gridId(newRepeatName))

        val containerNames = findContainerNamesForModel(newlySelectedTd.get)
        assert(containerNames === Seq("section-1", newRepeatName, newRepeatIterationName))

        // NOTE: We should maybe just compare the XML for holders, binds, and resources
        val dataHolder = assertDataHolder(doc, containerNames.init.last)
        assert((dataHolder.head precedingSibling * head).name === "control-1")

        val controlBind = findBindByName(doc, newRepeatName).get
        assert(hasIdValue(controlBind, bindId(newRepeatName)))
        assert((controlBind precedingSibling * att "id") === bindId("control-1"))

        assert(findModelElement(doc) \ "*:instance" exists (hasIdValue(_, "grid-3-template")))
      }

      // Insert a new control
      val binding = <binding element="xf|input" xmlns:xf="http://www.w3.org/2002/xforms"/>
      val newControlNameOption = insertNewControl(doc, binding)

      assert(newControlNameOption === Some("control-5"))
      val newControlName = newControlNameOption.get

      // Test result
      locally {

        val newlySelectedTd = findSelectedTd(doc)
        assert(newlySelectedTd.isDefined)
        assert(newlySelectedTd.get \ * \@ "id" === controlId(newControlName))

        val containerNames = findContainerNamesForModel(newlySelectedTd.get)
        assert(containerNames === Seq("section-1", newRepeatName, newRepeatIterationName))

        assert(hasIdValue(findControlByName(doc, newControlName).get, controlId(newControlName)))

        // NOTE: We should maybe just compare the XML for holders, binds, and resources
        val dataHolder = assertDataHolder(doc, newControlName)
        assert(dataHolder.head precedingSibling * isEmpty)
        assert((dataHolder.head parent * head).name === newRepeatIterationName)

        val controlBind = findBindByName(doc, newControlName).get
        assert(hasIdValue(controlBind, bindId(newControlName)))
        assert(hasIdValue(controlBind parent * head, bindId(newRepeatIterationName)))

        assert(formResourcesRoot \ "resource" \ newControlName nonEmpty)

        val templateHolder = templateRoot(doc, newRepeatName).get \ newControlName headOption

        assert(templateHolder.isDefined)
        assert(templateHolder.get precedingSibling * isEmpty)
        assert((templateHolder.get parent * head).name === newRepeatIterationName)
      }
    }

  def compareExpectedCells(grid: NodeInfo, expected: Seq[Seq[Cell]]): Unit = {
    val trs = grid \ "tr"
    for ((expected, index) ← expected.zipWithIndex)
      yield assert(getRowCells(trs(index)) === expected)
  }

  @Test def rowspanGetRowCells(): Unit =
    withActionAndFBDoc(RowspansDoc) { doc ⇒

      val grid = doc \\ "grid" head

      def td(id: String) = grid \\ * filter (hasIdValue(_, id)) head

      val expected = Seq(
        Seq(Cell(td("11"), 1, false), Cell(td("12"), 2, false), Cell(td("13"), 1, false)),
        Seq(Cell(td("21"), 2, false), Cell(td("12"), 1, true),  Cell(td("23"), 1, false)),
        Seq(Cell(td("21"), 1, true),  Cell(td("32"), 1, false), Cell(td("33"), 1, false))
      )

      compareExpectedCells(grid, expected)
    }

  // See https://github.com/orbeon/orbeon-forms/issues/2803
  def rewrap(node: NodeInfo) = node match {
    case nodeWrapper: saxon.NodeWrapper ⇒ node.root.asInstanceOf[DocumentWrapper].wrap(nodeWrapper.getUnderlyingNode.asInstanceOf[org.orbeon.dom.Node])
    case _ ⇒ node
  }

  @Test def rowspanInsertRowBelow(): Unit =
    withActionAndFBDoc(RowspansDoc) { doc ⇒

      val grid = doc \\ "grid" head

      // Insert one row below each existing row
      for (tr ← grid \ "tr" toList)
        insertRowBelow(rewrap(tr)) // rewrap after mutation (it's dangerous to play with NodeInfo and mutation!)

      def td(id: String) = grid \\ * filter (hasIdValue(_, id)) head

      val expected = Seq(
        Seq(Cell(td("11"),        1, false), Cell(td("12"),        3, false), Cell(td("13"),        1, false)),
        Seq(Cell(td("tmp-2-tmp"), 1, false), Cell(td("12"),        2, true),  Cell(td("tmp-3-tmp"), 1, false)),
        Seq(Cell(td("21"),        3, false), Cell(td("12"),        1, true),  Cell(td("23"),        1, false)),
        Seq(Cell(td("21"),        2, true),  Cell(td("tmp-4-tmp"), 1, false), Cell(td("tmp-5-tmp"), 1, false)),
        Seq(Cell(td("21"),        1, true),  Cell(td("32"),        1, false), Cell(td("33"),        1, false)),
        Seq(Cell(td("tmp-6-tmp"), 1, false), Cell(td("tmp-7-tmp"), 1, false), Cell(td("tmp-8-tmp"), 1, false))
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

  @Test def testPrecedingNameForControl(): Unit = {

    val section = sectionWithGridAndControls

    val controls = section \\ "*:td" \ * filter (_ \@ "id" nonEmpty)

    val expected = Seq(None, Some("grid-2"), Some("grid-2"))
    val actual = controls map precedingControlNameInSectionForControl

    assert(actual === expected)
  }

  @Test def testPrecedingNameForGrid(): Unit = {

    val section = sectionWithGridAndControls

    val grids = section \\ "*:grid"

    val expected = Seq(Some("control-11"), Some("grid-2"), Some("control-31"))
    val actual = grids map (precedingControlNameInSectionForGrid(_, includeSelf = true))

    assert(actual === expected)
  }

  def assertSelectedTdAfterDelete(beforeAfter: Seq[(String, String)])(delete: NodeInfo ⇒ Any): Unit = {

    // For before/after td ids: create a doc, call the delete function, and assert the resulting selected td
    def deleteRowCheckSelectedTd(beforeTdId: String, afterTdId: String) =
      withActionAndFBDoc(SectionsGridsDoc) { doc ⇒

        def getTd(id: String) = doc \\ "*:td" find (hasIdValue(_, id)) head

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

  @Test def selectedTdAfterDeletedRow(): Unit = {

    // A few before/after ids of selected tds
    val beforeAfter = Seq(
      "1111" → "1121",    // first td
      "2222" → "2231",    // middle td
      "3333" → "3323",    // last td
      "2111" → "2121"     // first td of grid/section
    )

    assertSelectedTdAfterDelete(beforeAfter) { td ⇒
      deleteRow(td parent * head)
    }
  }

  @Test def selectedTdAfterDeletedCol(): Unit = {

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

  @Test def selectedTdAfterDeletedGrid(): Unit = {

    // A few before/after ids of selected tds
    val beforeAfter = Seq(
      "1111" → "1211",    // first td
      "2222" → "2311",    // middle td
      "3333" → "3233",    // last td
      "2111" → "2211"     // first td of grid/section
    )

    assertSelectedTdAfterDelete(beforeAfter) { td ⇒
      deleteContainer(getContainingGrid(td))
    }
  }

  @Test def selectedTdAfterDeletedSection(): Unit = {

    // A few before/after ids of selected tds
    val beforeAfter = Seq(
      "1111" → "2111",    // first td
      "2222" → "3111",    // middle td
      "3333" → "2333",    // last td
      "2111" → "3111"     // first td of grid/section
    )

    assertSelectedTdAfterDelete(beforeAfter) { td ⇒
      deleteContainer(findAncestorContainers(getContainingGrid(td)) head)
    }
  }

  @Test def lastGridInSectionAndCanInsert(): Unit =
    withActionAndFBDoc(TemplateDoc) { doc ⇒

      // Initially can insert all
      assert(canInsertSection(doc) === true)
      assert(canInsertGrid(doc)    === true)
      assert(canInsertControl(doc) === true)

      // Remove everything (assume top-level section with a single grid inside)
      childrenContainers(findFRBodyElement(doc)).toList foreach  { section ⇒ // evaluate with toList otherwise the lazy iterator can fail
        assert(isLastGridInSection(childrenGrids(section).head) === true)
        deleteContainer(section)
      }

      // After everything is removed we can only insert a section (later: can also insert grid)
      assert(canInsertSection(doc) === true)
      assert(canInsertGrid(doc)    === false)
      assert(canInsertControl(doc) === false)
    }

  @Test def allowedBindingExpressions(): Unit = {

    val doc = this setupDocument
      <xh:html xmlns:xf="http://www.w3.org/2002/xforms"
           xmlns:xh="http://www.w3.org/1999/xhtml"
           xmlns:xxf="http://orbeon.org/oxf/xml/xforms">
        <xh:head>
          <xf:model xxf:xpath-analysis="true">

            <xf:instance id="fr-form-instance">
              <form>
                <section-1>
                  <control-1/>
                </section-1>
              </form>
            </xf:instance>

            <xf:bind id="fr-form-binds" ref="instance('fr-form-instance')">
              <xf:bind id="section-1-bind" name="section-1" ref="section-1">
                <xf:bind id="control-1-bind" name="control-1" ref="control-1"/>
              </xf:bind>
            </xf:bind>
          </xf:model>
        </xh:head>
        <xh:body>
          <xf:group id="section-1-section" bind="section-1-bind">
            <xf:input id="control-1-control" bind="control-1-bind"/>
          </xf:group>
        </xh:body>
      </xh:html>

    withContainingDocument(doc) {
      val section1 = doc.getControlByEffectiveId("section-1-section")
      val control1 = doc.getControlByEffectiveId("control-1-control")

      assert(true  === DataModel.isAllowedBindingExpression(section1, "section-1")) // existing node
      assert(false === DataModel.isAllowedBindingExpression(section1, "foo/bar"))   // non-existing node
      assert(false === DataModel.isAllowedBindingExpression(section1, "("))         // invalid expression
      assert(true  === DataModel.isAllowedBindingExpression(section1, "/"))         // root node
      assert(true  === DataModel.isAllowedBindingExpression(section1, ".."))        // complex content

      assert(true  === DataModel.isAllowedBindingExpression(control1, "control-1")) // existing node
      assert(false === DataModel.isAllowedBindingExpression(control1, "foo/bar"))   // non-existing node
      assert(false === DataModel.isAllowedBindingExpression(control1, "("))         // invalid expression
      assert(false === DataModel.isAllowedBindingExpression(control1, "/"))         // root node
      assert(false === DataModel.isAllowedBindingExpression(control1, ".."))        // complex content
    }
  }

  @Test def controlEffectiveId(): Unit =
    withActionAndFBDoc(SectionsRepeatsDoc) { doc ⇒

      val expected = Map(
        "|fb≡section-1-section≡tmp-13-tmp≡control-1-control|"                      → "control-1-control",
        "|fb≡section-1-section≡grid-4-grid≡control-5-control⊙1|"                   → "control-5-control",
        "|fb≡section-1-section≡section-3-section≡tmp-14-tmp≡control-6-control|"    → "control-6-control",
        "|fb≡section-1-section≡section-3-section≡grid-7-grid≡control-8-control⊙1|" → "control-8-control"
      )

      for ((expected, id) ← expected)
        assert(expected === buildFormBuilderControlAbsoluteIdOrEmpty(doc, id))
    }

  @Test def testAnalyzeKnownConstraint(): Unit = {

    import org.orbeon.oxf.xforms.function.xxforms.ValidationFunction.analyzeKnownConstraint

    val Library = XFormsFunctionLibrary
    val Logger  = new IndentedLogger(LoggerFactory.createLogger(classOf[FormBuilderFunctionsTest]), true)

    assert(Some("max-length"        → Some("5"))                          === analyzeKnownConstraint("xxf:max-length(5)",                                   Library)(Logger))
    assert(Some("min-length"        → Some("5"))                          === analyzeKnownConstraint("xxf:min-length(5)",                                   Library)(Logger))
    assert(Some("min-length"        → Some("5"))                          === analyzeKnownConstraint("xxf:min-length('5')",                                 Library)(Logger))
    assert(Some("min-length"        → Some("5"))                          === analyzeKnownConstraint("(xxf:min-length(5))",                                 Library)(Logger))
    assert(Some("non-negative"      → None)                               === analyzeKnownConstraint("(xxf:non-negative())",                                Library)(Logger))
    assert(Some("negative"          → None)                               === analyzeKnownConstraint("(xxf:negative())",                                    Library)(Logger))
    assert(Some("non-positive"      → None)                               === analyzeKnownConstraint("(xxf:non-positive())",                                Library)(Logger))
    assert(Some("positive"          → None)                               === analyzeKnownConstraint("(xxf:positive())",                                    Library)(Logger))
    assert(Some("upload-max-size"   → Some("3221225472"))                 === analyzeKnownConstraint("xxf:upload-max-size(3221225472)",                     Library)(Logger))
    assert(Some("upload-mediatypes" → Some("image/jpeg application/pdf")) === analyzeKnownConstraint("xxf:upload-mediatypes('image/jpeg application/pdf')", Library)(Logger))
    assert(None                                                           === analyzeKnownConstraint("xxf:min-length(foo)",                                 Library)(Logger))
    assert(None                                                           === analyzeKnownConstraint("xxf:foobar(5)",                                       Library)(Logger))

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

  private def assertDataHolder(doc: DocumentWrapper, holderName: String) = {
    val dataHolder = findDataHolders(doc, holderName)
    assert(dataHolder.length == 1)
    dataHolder
  }
}