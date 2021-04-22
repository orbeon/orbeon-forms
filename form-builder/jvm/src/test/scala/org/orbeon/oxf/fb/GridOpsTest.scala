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

import org.orbeon.oxf.fb.FormBuilder._
import org.orbeon.oxf.fb.ToolboxOps._
import org.orbeon.oxf.fr.FormRunner._
import org.orbeon.oxf.fr.NodeInfoCell.NodeInfoCellOps
import org.orbeon.oxf.fr.{Cell, NodeInfoCell}
import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport}
import org.orbeon.oxf.xforms.NodeInfoFactory.attributeInfo
import org.orbeon.oxf.xforms.action.XFormsAPI
import org.orbeon.oxf.xml.TransformerUtils
import org.orbeon.saxon.om._
import org.orbeon.scaxon.NodeConversions._
import org.orbeon.scaxon.SimplePath._
import org.scalatest.funspec.AnyFunSpecLike

// These functions run on a simplified "Form Builder" which loads a source form and goes through annotation.
class GridOpsTest
  extends DocumentTestBase
     with ResourceManagerSupport
     with AnyFunSpecLike
     with FormBuilderSupport {

  val SectionsGridsDoc   = "oxf:/org/orbeon/oxf/fb/template-with-sections-grids.xhtml"
  val RowspansDoc        = "oxf:/org/orbeon/oxf/fb/template-with-rowspans.xhtml"

  def createAndAssertInitialGrid(gridElem: NodeInfo)(implicit ctx: FormBuilderDocContext): Map[NodeInfo, Char] = {

    val expected =
      """
        |ABC
        |DbE
        |dFG
      """.stripMargin.trim

    val (actual, newMapping) = Cell.makeASCII(Cell.analyze12ColumnGridAndFillHoles(gridElem, simplify = true))
    assert(expected === actual)
    newMapping
  }

  describe("Row insertion below") {
    it("must insert as expected") {
      withActionAndFBDoc(RowspansDoc) { implicit ctx =>

        val gridElem =
          ctx.bodyElem descendant NodeInfoCell.GridTest head

        // Keep updating mapping so that initial cells keep their letter names
        var mapping = createAndAssertInitialGrid(gridElem)

        // Insert one row below each existing row
        for (rowPos <- List(0, 2, 4)) {
          rowInsertBelow(gridElem, rowPos)
          val (_, newMapping) = Cell.makeASCII(Cell.analyze12ColumnGridAndFillHoles(gridElem, simplify = true), mapping)
          mapping = newMapping
        }

        val after =
          """
            |ABC
            |HbI
            |DbE
            |dJK
            |dFG
            |LMN
          """.stripMargin.trim

       assert(after === Cell.makeASCII(Cell.analyze12ColumnGridAndFillHoles(gridElem, simplify = true), mapping)._1)
      }
    }
  }

  describe("Row insertion above") {
    it("must insert as expected") {
      withActionAndFBDoc(RowspansDoc) { implicit ctx =>

        val gridElem =
          ctx.bodyElem descendant NodeInfoCell.GridTest head

        // Keep updating mapping so that initial cells keep their letter names
        var mapping = createAndAssertInitialGrid(gridElem)

        // Insert one row above each existing row
        for (rowPos <- List(0, 2, 4)) {
          rowInsertAbove(gridElem, rowPos)
          val (_, newMapping) = Cell.makeASCII(Cell.analyze12ColumnGridAndFillHoles(gridElem, simplify = true), mapping)
          mapping = newMapping
        }

        val after =
          """
            |HIJ
            |ABC
            |KbL
            |DbE
            |dMN
            |dFG
          """.stripMargin.trim

       assert(after === Cell.makeASCII(Cell.analyze12ColumnGridAndFillHoles(gridElem, simplify = true), mapping)._1)
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
      <fr:grid bind="grid-2-bind" id="grid-2-grid">
        <fr:c><xf:input id="control-21-control"/></fr:c>
      </fr:grid>
      <fr:grid>
        <fr:c><xf:input id="control-31-control"/></fr:c>
      </fr:grid>
    </fr:section>

  describe("Preceding name for control") {
    it("must insert all elements in the right places") {
      withTestExternalContext { _ =>

        val controls =
          sectionWithGridAndControls descendant NodeInfoCell.CellTest child * filter (_.idOpt.nonEmpty)

        val actual = controls map precedingBoundControlNameInSectionForControl

        assert(actual === List(None, None, Some("grid-2")))
      }
    }}

  describe("Preceding name for grid") {
    it("must insert all elements in the right places") {
      withTestExternalContext { _ =>

        val section = sectionWithGridAndControls

        val grids = section descendant NodeInfoCell.GridTest

        val expected = List(Some("control-11"), Some("grid-2"), Some("control-31"))
        val actual = grids map (precedingBoundControlNameInSectionForGrid(_, includeSelf = true))

        assert(actual === expected)
      }
    }
  }

  describe("Delete") {

    def assertSelectedCellAfterDelete(beforeAfter: List[(String, String)])(delete: NodeInfo => Any): Unit = {

      // For before/after cell ids: create a doc, call the delete function, and assert the resulting selected cell
      def deleteAndCheckSelectedCell(beforeCellId: String, afterCellId: String) =
        withActionAndFBDoc(SectionsGridsDoc) { implicit ctx =>

          val doc = ctx.formDefinitionRootElem

          findInViewTryIndex(doc, beforeCellId) foreach { beforeCell =>
            selectCell(beforeCell)
            delete(beforeCell)
          }

          val actualSelectedCellId = findSelectedCell map (_.id)

          assert(actualSelectedCellId === Some(afterCellId))
        }

      // Test all
      for ((beforeTdId, afterTdId) <- beforeAfter)
        deleteAndCheckSelectedCell(beforeTdId, afterTdId)
    }

    it("must select the right cell after deleting a row") {

      val beforeAfter = List(
        "1111" -> "1121", // first cell
        "2222" -> "2231", // middle cell
        "3333" -> "3323", // last cell
        "2111" -> "2121"  // first cell of grid/section
      )

      assertSelectedCellAfterDelete(beforeAfter) { cell =>
        implicit val ctx = FormBuilderDocContext()
        rowDelete(getContainingGrid(cell).id, (NodeInfoCellOps.y(cell) getOrElse 1) - 1)
      }
    }

    it("must select the right cell after deleting a grid") {

      val beforeAfter = List(
        "1111" -> "1211", // first cell
        "2222" -> "2311", // middle cell
        "3333" -> "3233", // last cell
        "2111" -> "2211"  // first cell of grid/section
      )

      assertSelectedCellAfterDelete(beforeAfter) { cell =>
        deleteContainer(getContainingGrid(cell))
      }
    }

    it("must select the right cell after deleting a section") {

      val beforeAfter = List(
        "1111" -> "2111", // first cell
        "2222" -> "3111", // middle cell
        "3333" -> "2333", // last cell
        "2111" -> "3111"  // first cell of grid/section
      )
      assertSelectedCellAfterDelete(beforeAfter) { cell =>
        deleteContainer(findAncestorContainersLeafToRoot(getContainingGrid(cell)).head)
      }
    }
  }

  describe("Last grid in section") {
    it("must allow inserting a new grid") {
      withActionAndFBDoc(TemplateDoc) { implicit ctx =>

        val doc = ctx.formDefinitionRootElem

        // Initially can insert all
        assert(canInsertSection(doc) === true)
        assert(canInsertGrid(doc)    === true)
        assert(canInsertControl(doc) === true)

        // Remove everything (assume top-level section with a single grid inside)
        childrenContainers(ctx.bodyElem).toList foreach  { section => // evaluate with toList otherwise the lazy iterator can fail
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

  describe("#4134: 24-column conversion") {

    val grid12: NodeInfo =
      <fr:grid
        xmlns:fr="http://orbeon.org/oxf/xml/form-runner"
        id="my-grid-grid" bind="my-grid-bind">
        <fr:c x="1" y="1" w="6"/><fr:c x="7" y="1" w="6"/>
      </fr:grid>

    val grid24Even: NodeInfo =
      <fr:grid
        xmlns:fr="http://orbeon.org/oxf/xml/form-runner"
        columns="24"
        id="my-grid-grid" bind="my-grid-bind">
        <fr:c x="1" y="1" w="12"/><fr:c x="13" y="1" w="12"/>
      </fr:grid>

    val grid24Odd: NodeInfo =
      <fr:grid
        xmlns:fr="http://orbeon.org/oxf/xml/form-runner"
        columns="24"
        id="my-grid-grid" bind="my-grid-bind">
        <fr:c x="1" y="1" w="13"/><fr:c x="14" y="1" w="11"/>
      </fr:grid>

    import org.orbeon.scaxon.Implicits._

    it("must convert from 12 to 24 columns") {
      val result = TransformerUtils.extractAsMutableDocument(grid12).rootElement
      FormBuilder.migrateGridColumns(result, from = 12, to = 24)
      XFormsAPI.insert(into = result, origin = attributeInfo("columns", "24") )
      assertXMLElementsIgnoreNamespacesInScope(grid24Even, result)
    }

    it("must convert from 24 to 12 columns") {
      val result = TransformerUtils.extractAsMutableDocument(grid24Even).rootElement
      FormBuilder.migrateGridColumns(result, from = 24, to = 12)
      XFormsAPI.delete(result /@ "columns")
      assertXMLElementsIgnoreNamespacesInScope(grid12, result)
    }

    it("must report success to convert from 24 to 12 columns if cells are evenly aligned") {
      assert(FormBuilder.findGridColumnMigrationType(grid24Even, from = 24, to = 12).contains(FormBuilder.To12ColumnMigrationType))
    }

    it("must report failure to convert from 24 to 12 columns if cells are oddly aligned") {
      assert(FormBuilder.findGridColumnMigrationType(grid24Odd, from = 24, to = 12).isEmpty)
    }
  }
}