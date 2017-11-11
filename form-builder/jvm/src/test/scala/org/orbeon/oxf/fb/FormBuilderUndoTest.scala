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

import org.orbeon.builder.rpc.FormBuilderRpcApiImpl
import org.orbeon.oxf.fb.FormBuilder._
import org.orbeon.oxf.fr.FormRunner
import org.orbeon.oxf.fr.FormRunner._
import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport}
import org.orbeon.scaxon.SimplePath._
import org.scalatest.FunSpecLike

class FormBuilderUndoTest
  extends DocumentTestBase
     with ResourceManagerSupport
     with FunSpecLike
     with FormBuilderSupport {

  val SectionsGridsRepeatsDoc = "oxf:/org/orbeon/oxf/fb/template-with-sections-grids-repeats.xhtml"

  describe("Undo delete section, grid and control") {

    val containerDetails =
      withActionAndFBDoc(SectionsGridsRepeatsDoc) { implicit ctx ⇒
        findNestedContainers(ctx.bodyElem) map  { container ⇒
          (container.id, IsGrid(container), findNestedControls(container) map (_.id) head)
        } toList
      }

    containerDetails foreach { case (containerId, isGrid, nestedControlId) ⇒
      it(s"Must be able to undo delete of ${if (isGrid) "grid" else "section"} `$containerId` and nested control `$nestedControlId`") {
        withActionAndFBDoc(SectionsGridsRepeatsDoc) { implicit ctx ⇒

          val doc = ctx.formDefinitionRootElem

          def countContainers    = FormBuilderXPathApi.countAllContainers(doc)
          def countNonContainers = FormBuilderXPathApi.countAllNonContainers(doc)

          val nestedControlName = controlNameFromId(nestedControlId)

          val initialContainerCount    = countContainers
          val initialNonContainerCount = countNonContainers

          // Before and after delete container
          locally {
            assert(FormBuilder.findContainerById(containerId).nonEmpty)
            assert(FormRunner.findControlByName(doc, nestedControlName).nonEmpty)

            if (isGrid)
              FormBuilder.deleteGridById(containerId) foreach Undo.pushUndoAction
            else
              FormBuilder.deleteSectionById(containerId) foreach Undo.pushUndoAction

            assert(FormBuilder.findContainerById(containerId).isEmpty)
            assert(FormRunner.findControlByName(doc, nestedControlName).isEmpty)

            assert(initialContainerCount    > countContainers)
            assert(initialNonContainerCount > countNonContainers)
          }

          // Undo delete container
          locally {
            FormBuilderXPathApi.undoAction()

            assert(FormBuilder.findContainerById(containerId).nonEmpty)
            assert(FormRunner.findControlByName(doc, nestedControlName).nonEmpty)

            assert(initialContainerCount    === countContainers)
            assert(initialNonContainerCount === countNonContainers)

            // TODO: compare more: documents should be ideally identical before/after except for `tmp-` ids
            // but order can currently change in binds and/or holders
          }

          // Delete nested control
          {
            FormBuilderRpcApiImpl.controlDelete(nestedControlId)

            assert(FormRunner.findControlByName(doc, nestedControlName).isEmpty)

            assert(initialContainerCount        === countContainers)
            assert(initialNonContainerCount - 1 === countNonContainers)
          }

          // Undo delete nested control
          locally {
            FormBuilderXPathApi.undoAction()

            assert(FormRunner.findControlByName(doc, nestedControlName).nonEmpty)

            assert(initialContainerCount    === countContainers)
            assert(initialNonContainerCount === countNonContainers)
          }
        }
      }
    }
  }
}
