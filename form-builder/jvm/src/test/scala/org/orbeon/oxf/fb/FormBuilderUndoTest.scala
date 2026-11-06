/**
  * Copyright (C) 2017 Orbeon, Inc.
  *
  * This program is free software; you can redistribute it and/or modify it under the terms of the
  * GNU Lesser General Public License as published by the Free Software Foundation; either version
  *  2.1 of the License, or (at your option) any later version.
  *
  * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  * See the GNU Lesser General Public License for more details.
  *
  * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
  */
package org.orbeon.oxf.fb

import cats.syntax.option.*
import org.orbeon.builder.rpc.FormBuilderRpcApiImpl
import org.orbeon.builder.rpc.FormBuilderRpcApiImpl.resolveId
import org.orbeon.datatypes.Direction
import org.orbeon.oxf.fb.FormBuilder.*
import org.orbeon.oxf.fr.FormRunner.*
import org.orbeon.oxf.fr.NodeInfoCell.NodeInfoCellOps
import org.orbeon.oxf.fr.{AppForm, FormRunner, FormRunnerParams, NodeInfoCell}
import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport}
import org.orbeon.scaxon.SimplePath.*
import org.scalatest.funspec.AnyFunSpecLike


class FormBuilderUndoTest
  extends DocumentTestBase
     with ResourceManagerSupport
     with AnyFunSpecLike
     with FormBuilderSupport {

  val SectionsGridsRepeatsDoc = "oxf:/org/orbeon/oxf/fb/template-with-sections-grids-repeats.xhtml"

  describe("Undo delete section, grid and control") {

    val containerDetails =
      withActionAndFBDoc(SectionsGridsRepeatsDoc) { implicit ctx =>
        findNestedContainers(ctx.bodyElem) map  { container =>
          (
            container.id,
            IsGrid(container),
            container descendant NodeInfoCell.CellTest map (_.id) head,
            findNestedControls(container) map (_.id) head
          )
        } toList
      }

    containerDetails foreach { case (containerId, isGrid, _, nestedControlId) =>
      it(s"Must be able to undo delete of ${if (isGrid) "grid" else "section"} `$containerId` and nested control `$nestedControlId`") {
        withActionAndFBDoc(SectionsGridsRepeatsDoc) { implicit ctx =>

          val doc = ctx.formDefinitionRootElem

          implicit val formRunnerParams: FormRunnerParams =
            FormRunnerParams(AppForm.FormBuilder.app, AppForm.FormBuilder.form, 1.some, None, None, "new")

          def countContainers    = FormBuilderXPathApi.countAllContainers(doc)
          def countNonContainers = FormBuilderXPathApi.countAllNonContainers(doc)

          val nestedControlName = controlNameFromId(nestedControlId)

          val initialContainerCount    = countContainers
          val initialNonContainerCount = countNonContainers

          // Before and after delete container
          locally {
            assert(FormBuilder.findContainerById(containerId).nonEmpty)
            assert(FormRunner.findControlByName(nestedControlName).nonEmpty)

            if (isGrid)
              FormBuilder.deleteGridByIdIfPossible(containerId) foreach Undo.pushUserUndoAction
            else
              FormBuilder.deleteSectionByIdIfPossible(containerId) foreach Undo.pushUserUndoAction

            assert(FormBuilder.findContainerById(containerId).isEmpty)
            assert(FormRunner.findControlByName(nestedControlName).isEmpty)

            assert(initialContainerCount    > countContainers)
            assert(initialNonContainerCount > countNonContainers)
          }

          // Undo delete container
          locally {
            FormBuilderXPathApi.undoActionImpl()

            assert(FormBuilder.findContainerById(containerId).nonEmpty)
            assert(FormRunner.findControlByName(nestedControlName).nonEmpty)

            assert(initialContainerCount    === countContainers)
            assert(initialNonContainerCount === countNonContainers)

            // TODO: compare more: documents should be ideally identical before/after except for `tmp-` ids
            // but order can currently change in binds and/or holders
          }

          // Delete nested control
          locally {
            FormBuilderRpcApiImpl.controlDelete(nestedControlId)

            assert(FormRunner.findControlByName(nestedControlName).isEmpty)

            assert(initialContainerCount        === countContainers)
            assert(initialNonContainerCount - 1 === countNonContainers)
          }

          // Undo delete nested control
          locally {
            FormBuilderXPathApi.undoActionImpl()

            assert(FormRunner.findControlByName(nestedControlName).nonEmpty)

            assert(initialContainerCount    === countContainers)
            assert(initialNonContainerCount === countNonContainers)
          }
        }
      }
    }

    containerDetails filter (_._2) foreach { case (containerId, _, firstCellId, _) =>
      it(s"Must be able to move cell walls, merge and split cells of grid `$containerId`") {
        withActionAndFBDoc(SectionsGridsRepeatsDoc) { implicit ctx =>

          implicit val formRunnerParams: FormRunnerParams =
            FormRunnerParams(AppForm.FormBuilder.app, AppForm.FormBuilder.form, 1.some, None, None, "new")

          // Move grid wall
          locally {
            val cell = resolveId(firstCellId).get
            FormBuilderRpcApiImpl.moveWall(firstCellId, Direction.Right, 7)
            assert(Some(7) === NodeInfoCellOps.w(cell))
          }

          // Undo move wall
          locally {
            FormBuilderXPathApi.undoActionImpl()
            val cell = resolveId(firstCellId).get
            assert(Some(6) === NodeInfoCellOps.w(cell))
          }

          // Merge cell
          locally {
            val cell = resolveId(firstCellId).get

            FormBuilderRpcApiImpl.moveWall(firstCellId, Direction.Right, 8)
            assert(Some(8) === NodeInfoCellOps.w(cell))

            FormBuilderRpcApiImpl.mergeRight(firstCellId)
            assert(Some(12) === NodeInfoCellOps.w(cell))
          }

          // Undo merge cell
          locally {
            FormBuilderXPathApi.undoActionImpl()
            val cell = resolveId(firstCellId).get
            assert(Some(8) === NodeInfoCellOps.w(cell))
          }

          // Split cell
          locally {
            val cell = resolveId(firstCellId).get

            FormBuilderRpcApiImpl.moveWall(firstCellId, Direction.Right, 8)
            assert(Some(8) === NodeInfoCellOps.w(cell))

            FormBuilderRpcApiImpl.splitX(firstCellId)
            assert(Some(4) === NodeInfoCellOps.w(cell))
          }

          // Undo split cell
          locally {
            FormBuilderXPathApi.undoActionImpl()
            val cell = resolveId(firstCellId).get
            assert(Some(8) === NodeInfoCellOps.w(cell))
          }
        }
      }
    }
  }

  describe("Move sections and grids with containerMove and undo/redo") {

    val RepeatedSectionsDoc = "oxf:/org/orbeon/oxf/fr/template-for-repeated-sections.xhtml"

    it("must move sections and grids and support undo/redo") {
      withActionAndFBDoc(RepeatedSectionsDoc) { implicit ctx =>

        implicit val formRunnerParams: FormRunnerParams =
          FormRunnerParams(AppForm.FormBuilder.app, AppForm.FormBuilder.form, 1.some, None, None, "new")

        // Initial structure of `template-for-repeated-sections.xhtml`:
        // - my-section (contains grid-1, my-grid)
        // - other-section (contains grid-2)
        val mySectionId    = findControlByName("my-section").get.id
        val otherSectionId = findControlByName("other-section").get.id
        val grid1Id        = findControlByName("grid-1").get.id
        val myGridId       = findControlByName("my-grid").get.id
        val grid2Id        = findControlByName("grid-2").get.id

        assert(FormBuilder.containerPosition(mySectionId)    === ContainerPosition(None, None))
        assert(FormBuilder.containerPosition(otherSectionId) === ContainerPosition(None, Some("my-section")))
        assert(FormBuilder.containerPosition(grid1Id)        === ContainerPosition(Some("my-section"), None))
        assert(FormBuilder.containerPosition(myGridId)       === ContainerPosition(Some("my-section"), Some("grid-1")))
        assert(FormBuilder.containerPosition(grid2Id)        === ContainerPosition(Some("other-section"), None))

        // 1. Reorder `other-section` before `my-section` at top-level
        FormBuilderRpcApiImpl.containerMove(otherSectionId, None, None)
        assert(FormBuilder.containerPosition(otherSectionId) === ContainerPosition(None, None))
        assert(FormBuilder.containerPosition(mySectionId)    === ContainerPosition(None, Some("other-section")))

        // Undo and redo
        FormBuilderXPathApi.undoActionImpl()
        assert(FormBuilder.containerPosition(mySectionId)    === ContainerPosition(None, None))
        assert(FormBuilder.containerPosition(otherSectionId) === ContainerPosition(None, Some("my-section")))

        FormBuilderXPathApi.redoActionImpl()
        assert(FormBuilder.containerPosition(otherSectionId) === ContainerPosition(None, None))
        assert(FormBuilder.containerPosition(mySectionId)    === ContainerPosition(None, Some("other-section")))

        // Restore initial order
        FormBuilderXPathApi.undoActionImpl()

        // 2. Move `other-section` into `my-section` between `grid-1` and `my-grid`
        FormBuilderRpcApiImpl.containerMove(otherSectionId, Some(mySectionId), Some(grid1Id))
        assert(FormBuilder.containerPosition(otherSectionId) === ContainerPosition(Some("my-section"), Some("grid-1")))
        assert(FormBuilder.containerPosition(myGridId)       === ContainerPosition(Some("my-section"), Some("other-section")))

        // Disallow moving `grid-2` out of nested `other-section` when it is the sole grid in `other-section`
        FormBuilderRpcApiImpl.containerMove(grid2Id, Some(mySectionId), Some(grid1Id))
        assert(FormBuilder.containerPosition(grid2Id) === ContainerPosition(Some("other-section"), None))

        // Undo must restore `other-section` to top-level after `my-section`
        FormBuilderXPathApi.undoActionImpl()
        assert(FormBuilder.containerPosition(otherSectionId) === ContainerPosition(None, Some("my-section")))

        // 3. Move `grid-1` into `other-section` as first child (before `grid-2`)
        FormBuilderRpcApiImpl.containerMove(grid1Id, Some(otherSectionId), None)
        assert(FormBuilder.containerPosition(grid1Id)  === ContainerPosition(Some("other-section"), None))
        assert(FormBuilder.containerPosition(grid2Id)  === ContainerPosition(Some("other-section"), Some("grid-1")))
        assert(FormBuilder.containerPosition(myGridId) === ContainerPosition(Some("my-section"), None))

        // 4. Disallow moving `my-grid` out of `my-section` when it is now the sole remaining container in `my-section`
        FormBuilderRpcApiImpl.containerMove(myGridId, Some(otherSectionId), Some(grid2Id))
        assert(FormBuilder.containerPosition(myGridId) === ContainerPosition(Some("my-section"), None))

        // 5. Disallow moving a grid to top-level (`intoSectionIdOpt = None`)
        FormBuilderRpcApiImpl.containerMove(grid1Id, None, None)
        assert(FormBuilder.containerPosition(grid1Id) === ContainerPosition(Some("other-section"), None))

        // Undo moving `grid-1` into `other-section`
        FormBuilderXPathApi.undoActionImpl()
        assert(FormBuilder.containerPosition(grid1Id)  === ContainerPosition(Some("my-section"), None))
        assert(FormBuilder.containerPosition(myGridId) === ContainerPosition(Some("my-section"), Some("grid-1")))
        assert(FormBuilder.containerPosition(grid2Id)  === ContainerPosition(Some("other-section"), None))
      }
    }
  }

  describe("FormBuilderPreferences cookie serialization and initialization") {

    it("must round-trip preferences via cookie value and header") {
      import org.orbeon.builder.FormBuilderPreferences
      import org.orbeon.oxf.util.JvmUrlEncoderDecoder

      val prefs = FormBuilderPreferences(
        controlSettingsToolbarVisible = false,
        showCellBadges                = true,
        outlineVisible                = false
      )
      val cookieValue = FormBuilderPreferences.toCookieValue(prefs)
      assert(FormBuilderPreferences.fromCookieValue(cookieValue).contains(prefs))

      val cookieHeader = s"other-cookie=123; ${FormBuilderPreferences.PreferencesCookieName}=$cookieValue; theme=dark"
      assert(FormBuilderPreferences.fromCookieHeader(cookieHeader).contains(prefs))
    }

    it("must default missing keys and initialize fb-toolbar-instance from request cookie") {
      import org.orbeon.builder.FormBuilderPreferences
      import org.orbeon.oxf.util.JvmUrlEncoderDecoder

      // Partial JSON (only `outline-visible` specified)
      val partialEncoded = JvmUrlEncoderDecoder.encode("""{"outline-visible":false}""")
      assert(
        FormBuilderPreferences.fromCookieValue(partialEncoded).contains(
          FormBuilderPreferences(
            controlSettingsToolbarVisible = true,
            showCellBadges                = true,
            outlineVisible                = false
          )
        )
      )

      withActionAndFBDoc(SectionsGridsRepeatsDoc) { implicit ctx =>
        val model = ctx.formBuilderModel.get
        val doc   = model.containingDocument
        val cookieValue = FormBuilderPreferences.toCookieValue(
          FormBuilderPreferences(
            controlSettingsToolbarVisible = false,
            showCellBadges                = false,
            outlineVisible                = false
          )
        )
        doc.setRequestInformation(
          doc.getRequestInformation.copy(
            requestHeaders = Map("cookie" -> List(s"${FormBuilderPreferences.PreferencesCookieName}=$cookieValue"))
          )
        )

        val toolbarRoot = model.findInstance("fb-toolbar-instance").get.rootElement
        FormBuilderXPathApi.initializeToolbarFromPreferences(toolbarRoot)

        assert(toolbarRoot.attValue("visible")          === "false")
        assert(toolbarRoot.attValue("show-cell-badges") === "false")
        assert(toolbarRoot.attValue("outline-visible")  === "false")
      }
    }
  }
}
