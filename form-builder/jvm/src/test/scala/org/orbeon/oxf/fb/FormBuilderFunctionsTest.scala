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

import org.orbeon.dom.saxon.DocumentWrapper
import org.orbeon.oxf.fb.FormBuilder._
import org.orbeon.oxf.fb.ToolboxOps._
import org.orbeon.oxf.fr.FormRunner.findInViewTryIndex
import org.orbeon.oxf.fr.NodeInfoCell.NodeInfoCellOps
import org.orbeon.oxf.fr.XMLNames._
import org.orbeon.oxf.fr.{Cell, FormRunner, NodeInfoCell}
import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport}
import org.orbeon.oxf.util.{IndentedLogger, LoggerFactory}
import org.orbeon.oxf.xforms.action.XFormsAPI._
import org.orbeon.oxf.xforms.library.XFormsFunctionLibrary
import org.orbeon.oxf.xml.Dom4j.elemToDocument
import org.orbeon.oxf.xml.TransformerUtils
import org.orbeon.saxon.om._
import org.orbeon.scaxon.NodeConversions._
import org.orbeon.scaxon.SimplePath._
import org.scalatest.FunSpecLike

// These functions run on a simplified "Form Builder" which loads a source form and goes through annotation.
class FormBuilderFunctionsTest
  extends DocumentTestBase
     with ResourceManagerSupport
     with FunSpecLike
     with FormBuilderSupport {

  val SectionsGridsDoc   = "oxf:/org/orbeon/oxf/fb/template-with-sections-grids.xhtml"
  val SectionsRepeatsDoc = "oxf:/org/orbeon/oxf/fb/template-with-sections-repeats.xhtml"
  val RowspansDoc        = "oxf:/org/orbeon/oxf/fb/template-with-rowspans.xhtml"

  val Control1 = "control-1"
  val Control2 = "control-2"
  val Control3 = "control-3"
  val Section1 = "section-1"
  val Section2 = "section-2"

  describe("Model instance body elements") {
    withTestExternalContext { _ ⇒
      withActionAndFBDoc(TemplateDoc) { doc ⇒

        it("must find the model") {
          assert(findModelElement(doc).getDisplayName === "xf:model")
          assert(findModelElement(doc).hasIdValue("fr-form-model"))
        }

        it("must find the instance") {
          assert((formInstanceRoot(doc) parent * head).name === "xf:instance")
        }

        it("must find the body group") {
          assert(findFRBodyElement(doc).uriQualifiedName === URIQualifiedName(XF, "group"))
        }
      }
    }
  }

  describe("Name and id") {
    withTestExternalContext { _ ⇒
      withActionAndFBDoc(TemplateDoc) { doc ⇒

        it("must return the control names") {
          assert(controlNameFromId(controlId(Control1)) === Control1)
          assert(controlNameFromId(bindId(Control1))    === Control1)
        }

        it("must find the control element") {
          assert(findControlByName(doc, Control1).get.uriQualifiedName === URIQualifiedName(XF, "input"))
          assert(findControlByName(doc, Control1).get.hasIdValue(controlId(Control1)))
        }
      }
    }
  }

  describe("Control elements") {
    withTestExternalContext { _ ⇒
      withActionAndFBDoc(TemplateDoc) { doc ⇒

        it("must find the bind element") {
          assert(findBindByName(doc, Control1).get.uriQualifiedName === URIQualifiedName(XF, "bind"))
          assert(findBindByName(doc, Control1).get.hasIdValue(bindId(Control1)))
        }

        it("must check the content of the value holder") {
          assert(findDataHolders(doc, Control1).length == 1)
          assert(findDataHolders(doc, Control1).head.getStringValue === "")
        }

        // TODO
        // controlResourceHolders
      }
    }
  }

  describe("Section name") {
    withTestExternalContext { _ ⇒
      withActionAndFBDoc(TemplateDoc) { doc ⇒
        it("must find the section name") {
          assert(findSectionName(doc, Control1).get === Section1)
          assert(getControlNameOpt(doc descendant "*:section" head).get === Section1)
        }
      }
    }
  }

  describe("New binds") {
    it("must find the newly-created binds") {
      withTestExternalContext { _ ⇒
        withActionAndFBDoc(TemplateDoc) { doc ⇒
          ensureBinds(doc, List(Section1, Control2))

          assert(findBindByName(doc, Control2).get.uriQualifiedName === URIQualifiedName(XF, "bind"))
          assert(findBindByName(doc, Control2).get.hasIdValue(bindId(Control2)))

          ensureBinds(doc, List(Section2, "grid-1", Control3))

          assert(findBindByName(doc, Control3).get.uriQualifiedName === URIQualifiedName(XF, "bind"))
          assert(findBindByName(doc, Control3).get.hasIdValue(bindId(Control3)))
        }
      }
    }
  }

  describe("Find the next id") {
    it("must find ids without collisions") {
      withTestExternalContext { _ ⇒
        withActionAndFBDoc(TemplateDoc) { doc ⇒
          assert(nextId(doc, "control") === "control-3-control")
          assert(nextId(doc, "section") === "section-3-section")
        }
        // TODO: test more collisions
      }
    }
  }

  describe("Containers") {
    withTestExternalContext { _ ⇒
      withActionAndFBDoc(TemplateDoc) { doc ⇒
        val firstTd = findFRBodyElement(doc) descendant NodeInfoCell.GridTest descendant NodeInfoCell.CellTest head

        val containers = findAncestorContainersLeafToRoot(firstTd)

        it("must find the containers") {
          assert(containers(0).localname === "grid")
          assert(containers(1).localname === "section")

          assert(findContainerNamesForModel(firstTd) === List("section-1"))
        }
      }
    }
  }

  // Select the first grid cell (assume there is one)
  def selectFirstCell(doc: NodeInfo): Unit =
    selectCell(findFRBodyElement(doc) descendant NodeInfoCell.GridTest descendant NodeInfoCell.CellTest head)

  describe("Insert `xf:input` control") {
    it("must insert all elements in the right places") {
      withTestExternalContext { _ ⇒
        withActionAndFBDoc(TemplateDoc) { doc ⇒

          // Insert a new control into the next empty td
          selectFirstCell(doc)
          val newControlNameOption = insertNewControl(doc, <binding element="xf|input" xmlns:xf="http://www.w3.org/2002/xforms"/>)

          // Check the control's name
          assert(newControlNameOption === Some("control-3"))
          val newControlName = newControlNameOption.get

          // Test result
          assert(findControlByName(doc, newControlName).get.hasIdValue(controlId(newControlName)))

          val newlySelectedTd = findSelectedCell(doc)
          assert(newlySelectedTd.isDefined)
          assert(newlySelectedTd.get / * /@ "id" === controlId(newControlName))

          val containerNames = findContainerNamesForModel(newlySelectedTd.get)
          assert(containerNames == List("section-1"))

          // NOTE: We should maybe just compare the XML for holders, binds, and resources
          val dataHolder = assertDataHolder(doc, newControlName)
          assert((dataHolder.head precedingSibling * head).name === "control-1")

          val controlBind = findBindByName(doc, newControlName).get
          assert(controlBind.hasIdValue(bindId(newControlName)))
          assert((controlBind precedingSibling * att "id") === bindId("control-1"))

          assert(formResourcesRoot / "resource" / newControlName nonEmpty)

        }
      }
    }
  }

  describe("Insert `fr:explanation` control") {
    it("must insert all elements in the right places") {
      withTestExternalContext { _ ⇒
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
      }
    }
  }

  describe("Insert repeat") {
    it("must insert all elements in the right places") {
      withTestExternalContext { _ ⇒
        withActionAndFBDoc(TemplateDoc) { doc ⇒

          // Insert a new repeated grid after the current grid
          selectFirstCell(doc)
          val newRepeatNameOption = insertNewRepeatedGrid(doc)

          assert(newRepeatNameOption === Some("grid-3"))
          val newRepeatName          = newRepeatNameOption.get
          val newRepeatIterationName = defaultIterationName(newRepeatName)

          locally {

            val newlySelectedTd = findSelectedCell(doc)
            assert(newlySelectedTd.isDefined)
            assert((newlySelectedTd flatMap (_ parent * headOption) head) /@ "id" === gridId(newRepeatName))

            val containerNames = findContainerNamesForModel(newlySelectedTd.get)
            assert(containerNames === List("section-1", newRepeatName, newRepeatIterationName))

            // NOTE: We should maybe just compare the XML for holders, binds, and resources
            val dataHolder = assertDataHolder(doc, containerNames.init.last)
            assert((dataHolder.head precedingSibling * head).name === "control-1")

            val controlBind = findBindByName(doc, newRepeatName).get
            assert(controlBind.hasIdValue(bindId(newRepeatName)))
            assert((controlBind precedingSibling * att "id") === bindId("control-1"))

            assert(findModelElement(doc) / "*:instance" exists (_.hasIdValue("grid-3-template")))
          }

          // Insert a new control
          val newControlNameOption = insertNewControl(doc, <binding element="xf|input" xmlns:xf="http://www.w3.org/2002/xforms"/>)

          assert(newControlNameOption === Some("control-5"))
          val newControlName = newControlNameOption.get

          // Test result
          locally {

            val newlySelectedTd = findSelectedCell(doc)
            assert(newlySelectedTd.isDefined)
            assert(newlySelectedTd.get / * /@ "id" === controlId(newControlName))

            val containerNames = findContainerNamesForModel(newlySelectedTd.get)
            assert(containerNames === List("section-1", newRepeatName, newRepeatIterationName))

            assert(findControlByName(doc, newControlName).get.hasIdValue(controlId(newControlName)))

            // NOTE: We should maybe just compare the XML for holders, binds, and resources
            val dataHolder = assertDataHolder(doc, newControlName)
            assert(dataHolder.head precedingSibling * isEmpty)
            assert((dataHolder.head parent * head).name === newRepeatIterationName)

            val controlBind = findBindByName(doc, newControlName).get
            assert(controlBind.hasIdValue(bindId(newControlName)))
            assert((controlBind parent * head).hasIdValue(bindId(newRepeatIterationName)))

            assert(formResourcesRoot / "resource" / newControlName nonEmpty)

            val templateHolder = templateRoot(doc, newRepeatName).get / newControlName headOption

            assert(templateHolder.isDefined)
            assert(templateHolder.get precedingSibling * isEmpty)
            assert((templateHolder.get parent * head).name === newRepeatIterationName)
          }
        }
      }
    }
  }

  describe("Row insertion below") {
    it("must insert as expected") {
      withTestExternalContext { _ ⇒
        withActionAndFBDoc(RowspansDoc) { doc ⇒

          def gridNode =
            doc descendant NodeInfoCell.GridTest head

          import NodeInfoCell._

          // Insert one row below each existing row
          for (rowPos ← List(0, 2, 4))
            rowInsertBelow(gridNode.id, rowPos)

          def td(id: String) = gridNode descendant * find (_.hasIdValue(id))

          val FakeOrigin = Some(Cell[NodeInfo](None, None, 1, 1, 1, 1))

          val expected: List[List[Cell[NodeInfo]]] = List(
            List(Cell(td("11"),        None,       1, 1, 1, 1), Cell(td("12"),        None, 2, 1, 3, 1),        Cell(td("13"),        None, 3, 1, 1, 1)),
            List(Cell(None,            None,       1, 2, 1, 1), Cell(td("12"),        FakeOrigin, 2, 2, 2, 1),  Cell(None,            None, 3, 2, 1, 1)),
            List(Cell(td("21"),        None,       1, 3, 3, 1), Cell(td("12"),        FakeOrigin, 2, 3, 1, 1),  Cell(td("23"),        None, 3, 3, 1, 1)),
            List(Cell(td("21"),        FakeOrigin, 1, 4, 2, 1), Cell(None,            None, 2, 4, 1, 2),        Cell(None,            FakeOrigin, 3, 4, 1, 1)),
            List(Cell(td("21"),        FakeOrigin, 1, 5, 1, 1), Cell(td("32"),        None, 2, 5, 1, 1),        Cell(td("33"),        None, 3, 5, 1, 1)),
            List(Cell(td("tmp-2-tmp"), None,       1, 6, 1, 3), Cell(td("tmp-2-tmp"), FakeOrigin, 2, 6, 1, 2),  Cell(td("tmp-2-tmp"), FakeOrigin, 3, 6, 1, 1))
          )

          val actual = Cell.analyze12ColumnGridAndFillHoles(gridNode, simplify = true)

          // Compare all except `origin` as it's a pain to setup `expected` above with that
          def compareCells(c1: Cell[NodeInfo], c2: Cell[NodeInfo]): Boolean =
            c1.u == c2.u && c1.x == c2.x && c1.y == c2.y && c1.w == c2.w && c1.h == c2.h && c1.missing == c2.missing

          assert(expected.flatten.zipAll(actual.flatten, null, null) forall (compareCells _).tupled)
        }
      }
    }
  }

  def sectionWithGridAndControls: NodeInfo =
    <fr:section xmlns:fr="http://orbeon.org/oxf/xml/form-runner"
          xmlns:xh="http://www.w3.org/1999/xhtml"
          xmlns:xf="http://www.w3.org/2002/xforms">
      <fr:grid>
        <fr:c><xf:input id="control-11-control"/></fr:c>
      </fr:grid>
      <fr:grid id="grid-2-grid">
        <fr:c><xf:input id="control-21-control"/></fr:c>
      </fr:grid>
      <fr:grid>
        <fr:c><xf:input id="control-31-control"/></fr:c>
      </fr:grid>
    </fr:section>

  describe("Preceding name for control") {
    it("must insert all elements in the right places") {
      withTestExternalContext { _ ⇒

        val controls =
          sectionWithGridAndControls descendant NodeInfoCell.CellTest child * filter (_ /@ "id" nonEmpty)

        val actual = controls map precedingControlNameInSectionForControl

        assert(actual === List(None, Some("grid-2"), Some("grid-2")))
      }
    }}

  describe("Preceding name for grid") {
    it("must insert all elements in the right places") {
      withTestExternalContext { _ ⇒

        val section = sectionWithGridAndControls

        val grids = section descendant NodeInfoCell.GridTest

        val expected = List(Some("control-11"), Some("grid-2"), Some("control-31"))
        val actual = grids map (precedingControlNameInSectionForGrid(_, includeSelf = true))

        assert(actual === expected)
      }
    }
  }

  describe("Delete") {

    def assertSelectedCellAfterDelete(beforeAfter: List[(String, String)])(delete: NodeInfo ⇒ Any): Unit = {

      // For before/after cell ids: create a doc, call the delete function, and assert the resulting selected cell
      def deleteAndCheckSelectedCell(beforeCellId: String, afterCellId: String) =
        withTestExternalContext { _ ⇒
          withActionAndFBDoc(SectionsGridsDoc) { doc ⇒

            findInViewTryIndex(doc, beforeCellId) foreach { beforeCell ⇒
              selectCell(beforeCell)
              delete(beforeCell)
            }

            val actualSelectedCellId = findSelectedCell(doc) map (_.id)

            assert(actualSelectedCellId === Some(afterCellId))
          }
        }

      // Test all
      for ((beforeTdId, afterTdId) ← beforeAfter)
        deleteAndCheckSelectedCell(beforeTdId, afterTdId)
    }

    it("must select the right cell after deleting a row") {

      val beforeAfter = List(
        "1111" → "1121", // first cell
        "2222" → "2231", // middle cell
        "3333" → "3323", // last cell
        "2111" → "2121"  // first cell of grid/section
      )

      assertSelectedCellAfterDelete(beforeAfter) { cell ⇒
        rowDelete(getContainingGrid(cell).id, (NodeInfoCellOps.y(cell) getOrElse 1) - 1)
      }
    }

    it("must select the right cell after deleting a grid") {

      val beforeAfter = List(
        "1111" → "1211", // first cell
        "2222" → "2311", // middle cell
        "3333" → "3233", // last cell
        "2111" → "2211"  // first cell of grid/section
      )

      assertSelectedCellAfterDelete(beforeAfter) { cell ⇒
        deleteContainer(getContainingGrid(cell))
      }
    }

    it("must select the right cell after deleting a section") {

      val beforeAfter = List(
        "1111" → "2111", // first cell
        "2222" → "3111", // middle cell
        "3333" → "2333", // last cell
        "2111" → "3111"  // first cell of grid/section
      )
      assertSelectedCellAfterDelete(beforeAfter) { cell ⇒
        deleteContainer(findAncestorContainersLeafToRoot(getContainingGrid(cell)).head)
      }
    }
  }

  describe("Last grid in section") {
    it("must allow inserting a new grid") {
      withTestExternalContext { _ ⇒
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
      }
    }
  }

  describe("Allowed binding expression") {
    it("must insert all elements in the right places") {
      withTestExternalContext { _ ⇒

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
    }
  }

  describe("Control effective id") {
    it("must return the expected statis ids") {
      withTestExternalContext { _ ⇒
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
      }
    }
  }

  describe("Analyze known constraint") {

    import org.orbeon.oxf.xforms.function.xxforms.ValidationFunction.analyzeKnownConstraint

    val Library = XFormsFunctionLibrary
    val Logger  = new IndentedLogger(LoggerFactory.createLogger(classOf[FormBuilderFunctionsTest]), true)

    it("must pass all common constraints") {
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
  }

  private def assertDataHolder(doc: DocumentWrapper, holderName: String) = {
    val dataHolder = findDataHolders(doc, holderName)
    assert(dataHolder.length == 1)
    dataHolder
  }
}