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

import org.orbeon.oxf.fb.FormBuilder.*
import org.orbeon.oxf.fb.ToolboxOps.*
import org.orbeon.oxf.fr.FormRunner.*
import org.orbeon.oxf.fr.NodeInfoCell.NodeInfoCellOps
import org.orbeon.oxf.fr.{Cell, NodeInfoCell}
import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport}
import org.orbeon.oxf.xforms.NodeInfoFactory.attributeInfo
import org.orbeon.oxf.xforms.action.XFormsAPI
import org.orbeon.oxf.xml.TransformerUtils
import org.orbeon.saxon.om.*
import org.orbeon.scaxon.NodeConversions.*
import org.orbeon.scaxon.SimplePath.*
import org.scalatest.funspec.AnyFunSpecLike

// These functions run on a simplified "Form Builder" which loads a source form and goes through annotation.
class GridOpsTest
  extends DocumentTestBase
     with ResourceManagerSupport
     with AnyFunSpecLike
     with FormBuilderSupport {

  val SectionsGridsDoc   = "oxf:/org/orbeon/oxf/fb/template-with-sections-grids.xhtml"
  val RowspansDoc        = "oxf:/org/orbeon/oxf/fb/template-with-rowspans.xhtml"
  val LinesDoc           = "oxf:/org/orbeon/oxf/fb/template-with-grid-lines.xhtml"

  def createAndAssertInitialGrid(gridElem: NodeInfo)(implicit ctx: FormBuilderDocContext): Map[String, Char] = {

    val expected =
      """
        |ABC
        |DbE
        |dFG
      """.stripMargin.trim

    val (actual, newMapping) = Cell.makeASCII(Cell.analyze12ColumnGridAndFillHoles(gridElem, simplify = true, transpose = false))
    assert(expected == actual)
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
          val (_, newMapping) = Cell.makeASCII(Cell.analyze12ColumnGridAndFillHoles(gridElem, simplify = true, transpose = false), mapping)
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

       assert(after == Cell.makeASCII(Cell.analyze12ColumnGridAndFillHoles(gridElem, simplify = true, transpose = false), mapping)._1)
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
          val (_, newMapping) = Cell.makeASCII(Cell.analyze12ColumnGridAndFillHoles(gridElem, simplify = true, transpose = false), mapping)
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

       assert(after == Cell.makeASCII(Cell.analyze12ColumnGridAndFillHoles(gridElem, simplify = true, transpose = false), mapping)._1)
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

        assert(actual == List(None, None, Some("grid-2")))
      }
    }}

  describe("Preceding name for grid") {
    it("must insert all elements in the right places") {
      withTestExternalContext { _ =>

        val section = sectionWithGridAndControls

        val grids = section descendant NodeInfoCell.GridTest

        val expected = List(Some("control-11"), Some("grid-2"), Some("control-31"))
        val actual = grids map (precedingBoundControlNameInSectionForGrid(_, includeSelf = true))

        assert(actual == expected)
      }
    }
  }

  describe("Delete") {

    def assertSelectedCellAfterDelete(beforeAfter: List[(String, String)])(delete: NodeInfo => Any): Unit = {

      // For before/after cell ids: create a doc, call the delete function, and assert the resulting selected cell
      def deleteAndCheckSelectedCell(beforeCellId: String, afterCellId: String) =
        withActionAndFBDoc(SectionsGridsDoc) { implicit ctx =>

          findInViewTryIndex(beforeCellId) foreach { beforeCell =>
            selectCell(beforeCell)
            delete(beforeCell)
          }

          val actualSelectedCellId = findSelectedCell map (_.id)

          assert(actualSelectedCellId.contains(afterCellId))
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
      withActionAndFBDoc(TemplateWithSingleControlDoc) { implicit ctx =>

        val doc = ctx.formDefinitionRootElem

        // Initially can insert all
        assert(canInsertSection(doc))
        assert(canInsertGrid(doc))
        assert(canInsertControl(doc))

        // Remove everything (assume top-level section with a single grid inside)
        childrenContainers(ctx.bodyElem).toList foreach  { section => // evaluate with toList otherwise the lazy iterator can fail
          assert(isLastGridInSection(childrenGrids(section).head))
          deleteContainer(section)
        }

        // After everything is removed we can only insert a section (later: can also insert grid)
        assert(canInsertSection(doc))
        assert(! canInsertGrid(doc))
        assert(! canInsertControl(doc))
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

    import org.orbeon.scaxon.Implicits.*

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

  describe("#7208: Keyboard shortcuts to move a grid line up/down") {

    // TODO: add more tests
    it("must move rows as expected") {
      withActionAndFBDoc(LinesDoc) { implicit ctx =>

        val gridElem =
          ctx.bodyElem descendant NodeInfoCell.GridTest head

        val (_, mapping) = Cell.makeASCII(Cell.analyze12ColumnGridAndFillHoles(gridElem, simplify = true, transpose = false))

        val beforeS =
          """
            |ABC
            |DEc
            |FGc
            |""".stripMargin.trim

        FormBuilder.rowMove(
          gridId      = gridElem.id,
          fromRowPos0 = 0,
          toRowPos0   = 1
        )

        val afterS1 =
          """
            |DEC
            |ABc
            |FGc
          """.stripMargin.trim

        assert(afterS1 == Cell.makeASCII(Cell.analyze12ColumnGridAndFillHoles(gridElem, simplify = true, transpose = false), mapping)._1)

        FormBuilder.rowMove(
          gridId      = gridElem.id,
          fromRowPos0 = 1,
          toRowPos0   = 2
        )

        val afterS2 =
          """
            |DEC
            |FGc
            |ABc
          """.stripMargin.trim

        assert(afterS2 == Cell.makeASCII(Cell.analyze12ColumnGridAndFillHoles(gridElem, simplify = true, transpose = false), mapping)._1)

        FormBuilder.rowMove(
          gridId      = gridElem.id,
          fromRowPos0 = 2,
          toRowPos0   = 1
        )

        assert(afterS1 == Cell.makeASCII(Cell.analyze12ColumnGridAndFillHoles(gridElem, simplify = true, transpose = false), mapping)._1)

        FormBuilder.rowMove(
          gridId      = gridElem.id,
          fromRowPos0 = 1,
          toRowPos0   = 0
        )

        assert(beforeS == Cell.makeASCII(Cell.analyze12ColumnGridAndFillHoles(gridElem, simplify = true, transpose = false), mapping)._1)
      }
    }

    it("must move rows in repeated grid as expected") {
      withActionAndFBDoc(LinesDoc) { implicit ctx =>

        val gridElem =
          findControlByName("repeated-grid").get

        val (_, mapping) = Cell.makeASCII(Cell.analyze12ColumnGridAndFillHoles(gridElem, simplify = true, transpose = false))

        FormBuilder.rowMove(
          gridId      = gridElem.id,
          fromRowPos0 = 0,
          toRowPos0   = 1
        )

        val afterS1 =
          """
            |CD
            |AB
          """.stripMargin.trim

        assert(afterS1 == Cell.makeASCII(Cell.analyze12ColumnGridAndFillHoles(gridElem, simplify = true, transpose = false), mapping)._1)

        // Check that binds were reordered
        assert(List("my-repeated-text-area", "my-repeated-input") == findBindByName("my-repeated-input").get.getParent.child(*).map(b => controlNameFromId(b.id)))

        // Check holders were reordered
        val holderParents = findDataHolders("my-repeated-input").map(_.getParent)
        assert(holderParents.size == 2)

        holderParents.foreach { holderParent =>
          assert(List("my-repeated-text-area", "my-repeated-input") == holderParent.child(*).map(_.localname))
        }
      }
    }
  }
}