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
import org.orbeon.oxf.fr.FormRunner.findInViewTryIndex
import org.orbeon.oxf.fr.NodeInfoCell.NodeInfoCellOps
import org.orbeon.oxf.fr.{Cell, NodeInfoCell}
import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport}
import org.orbeon.saxon.om._
import org.orbeon.scaxon.NodeConversions._
import org.orbeon.scaxon.SimplePath._
import org.scalatest.FunSpecLike

// These functions run on a simplified "Form Builder" which loads a source form and goes through annotation.
class GridOpsTest
  extends DocumentTestBase
     with ResourceManagerSupport
     with FunSpecLike
     with FormBuilderSupport {

  val SectionsGridsDoc   = "oxf:/org/orbeon/oxf/fb/template-with-sections-grids.xhtml"
  val RowspansDoc        = "oxf:/org/orbeon/oxf/fb/template-with-rowspans.xhtml"



  describe("Row insertion below") {
    it("must insert as expected") {
      withTestExternalContext { _ ⇒
        withActionAndFBDoc(RowspansDoc) { doc ⇒

          def gridNode =
            doc descendant NodeInfoCell.GridTest head

          import NodeInfoCell._

          // Keep updating mapping so that initial cells keep their letter names
          var mapping = {
            val expected =
              """
                |ABC
                |DbE
                |dFG
              """.stripMargin.trim

            val (actual, newMapping) = Cell.makeASCII(Cell.analyze12ColumnGridAndFillHoles(gridNode, simplify = true))
            assert(expected === actual)
            newMapping
          }

          // Insert one row below each existing row

          for (rowPos ← List(0, 2, 4)) {
            rowInsertBelow(gridNode.id, rowPos)
            val (_, newMapping) = Cell.makeASCII(Cell.analyze12ColumnGridAndFillHoles(gridNode, simplify = true), mapping)
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

         assert(after === Cell.makeASCII(Cell.analyze12ColumnGridAndFillHoles(gridNode, simplify = true), mapping)._1)
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
}