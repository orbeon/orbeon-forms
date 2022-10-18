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

import org.orbeon.builder.rpc.FormBuilderRpcApiImpl
import org.orbeon.oxf.fb.FormBuilder._
import org.orbeon.oxf.fr.FormRunner._
import org.orbeon.oxf.fr.{AppForm, FormRunner, FormRunnerParams}
import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport}
import org.orbeon.scaxon.SimplePath._
import org.scalatest.funspec.AnyFunSpecLike

import scala.collection.compat._


// These functions run on a simplified "Form Builder" which loads a source form and goes through annotation.
class ClipboardTest
  extends DocumentTestBase
     with ResourceManagerSupport
     with AnyFunSpecLike
     with FormBuilderSupport {

  val SectionsRepeatsDoc      = "oxf:/org/orbeon/oxf/fb/template-with-sections-repeats.xhtml"
  val SectionsGridsRepeatsDoc = "oxf:/org/orbeon/oxf/fb/template-with-sections-grids-repeats.xhtml"
  val RepeatedSectionDoc      = "oxf:/org/orbeon/oxf/fb/template-with-repeated-section.xhtml"

  val Control1   = "control-1"
  val Control2   = "control-2"
  val Control3   = "control-3"
  val Control4   = "control-4"
  val Section1   = "section-1"
  val Section2   = "section-2"

  describe("Clipboard cut and paste") {

    it("Simple cut/paste must remove and restore the control, bind, holders, and resources") {
      withActionAndFBDoc(SectionsRepeatsDoc) { implicit ctx =>

        implicit val formRunnerParams: FormRunnerParams =
          FormRunnerParams(AppForm.FormBuilder.app, AppForm.FormBuilder.form, 1, None, None, "new")

        val selectedCell = FormBuilder.findSelectedCell.get

        def assertPresent() = {
          assert(Control1 === FormRunner.getControlName(selectedCell / * head))
          assert(FormRunner.findControlByName(Control1).nonEmpty)
          assert(FormRunner.findBindByName(Control1).nonEmpty)
          assert(FormRunner.findDataHolders(Control1).nonEmpty)
          assert(FormBuilder.findCurrentResourceHolder(Control1).nonEmpty)
        }

        assertPresent()

        ToolboxOps.cutToClipboard(selectedCell)

        // Selected cell hasn't changed
        assert(FormBuilder.findSelectedCell contains selectedCell)

        assert(FormRunner.findControlByName(Control1).isEmpty)
        assert(FormRunner.findBindByName(Control1).isEmpty)
        assert(FormRunner.findDataHolders(Control1).isEmpty)
        assert(FormBuilder.findCurrentResourceHolder(Control1).isEmpty)

        ToolboxOps.pasteFromClipboardImpl()

        assertPresent()
      }
    }

    val containerIds =
      withActionAndFBDoc(SectionsGridsRepeatsDoc) { implicit ctx =>
        findNestedContainers(ctx.bodyElem).to(List) map (_.id)
      }

    containerIds foreach { containerId =>
      it(s"Must be able to cut and paste container `$containerId`") {
        withActionAndFBDoc(SectionsGridsRepeatsDoc) { implicit ctx =>

          val doc = ctx.formDefinitionRootElem

          implicit val formRunnerParams: FormRunnerParams =
            FormRunnerParams(AppForm.FormBuilder.app, AppForm.FormBuilder.form, 1, None, None, "new")

          def countContainers = FormBuilderXPathApi.countAllContainers(doc)

          ToolboxOps.selectFirstCellInContainer(containerById(containerId))

          val nestedControlName =
            FormBuilder.findContainerById(containerId).toList flatMap findNestedControls flatMap getControlNameOpt head

          val initialContainerCount = countContainers

          // Before and after cut
          locally {
            assert(FormBuilder.findContainerById(containerId).nonEmpty)
            assert(FormRunner.findControlByName(nestedControlName).nonEmpty)

            FormBuilderRpcApiImpl.containerCut(containerId)

            assert(FormBuilder.findContainerById(containerId).isEmpty)
            assert(FormRunner.findControlByName(nestedControlName).isEmpty)

            assert(initialContainerCount > countContainers)
          }

          val cutContainersCount = initialContainerCount - countContainers

          // Paste without prefix/suffix (shouldn't conflict)
          locally {
            ToolboxOps.pasteFromClipboardImpl()
            assert(FormBuilder.findContainerById(containerId).nonEmpty)
            assert(FormRunner.findControlByName(nestedControlName).nonEmpty)
            assert(initialContainerCount === countContainers)
          }

          // Paste with prefix
          locally {
            val Prefix = "my-"
            FormBuilderXPathApi.pasteSectionGridFromClipboardImpl(Prefix, "")
            assert(FormBuilder.findContainerById(Prefix + containerId).nonEmpty)
            assert(FormRunner.findControlByName(Prefix + nestedControlName).nonEmpty)
            assert(initialContainerCount + cutContainersCount === countContainers)
          }

          // Paste with suffix
          locally {
            val Suffix = "-nice"
            FormBuilderXPathApi.pasteSectionGridFromClipboardImpl("", Suffix)
            assert(findControlByName(controlNameFromId(containerId) + Suffix).nonEmpty)
            assert(FormRunner.findControlByName(nestedControlName + Suffix).nonEmpty)
            assert(initialContainerCount + cutContainersCount * 2 === countContainers)
          }

          // Paste without prefix/suffix (should create automatic ids and not crash)
          locally {
            FormBuilderXPathApi.pasteSectionGridFromClipboardImpl("", "")
            assert(initialContainerCount + cutContainersCount * 3 === countContainers)
          }
        }
      }
    }

    it("must cut and paste repeated controls and keep values (#3781)") {

      val RepeatedControl1 = "control-111"
      val RepeatedControl2 = "control-121"
      val SingleControl    = "control-21"

      withActionAndFBDoc(RepeatedSectionDoc) { implicit ctx =>

        implicit val formRunnerParams: FormRunnerParams =
          FormRunnerParams(AppForm.FormBuilder.app, AppForm.FormBuilder.form, 1, None, None, "new")

        def findValues(controlName: String) =
          ctx.dataRootElem descendant controlName map (_.stringValue)

        def assertCutPasteRepeatToRepeat(sourceControlName: String, newControlName: String, expected: List[String]): Unit = {

          assert(expected == findValues(sourceControlName))

          ToolboxOps.cutToClipboard(findControlByName(sourceControlName).get.parentUnsafe)
          ToolboxOps.pasteFromClipboardImpl()
          assert(expected == findValues(sourceControlName))

          ToolboxOps.pasteFromClipboardImpl()
          assert(expected == findValues(sourceControlName))
          assert(expected == findValues(newControlName))
        }

        assertCutPasteRepeatToRepeat(RepeatedControl1, Control1, List("value111⊙1", "value111⊙2"))
        assertCutPasteRepeatToRepeat(RepeatedControl2, Control2, List("value121⊙1", "value121⊙2"))

        def assertCutPasteSingleToRepeat(sourceControlName: String, newControlName: String, expected: String): Unit = {

          assert(List(expected) == findValues(sourceControlName))

          ToolboxOps.copyToClipboard(findControlByName(sourceControlName).get.parentUnsafe)

          FormBuilder.selectCell(findControlByName(RepeatedControl1).get.parentUnsafe)
          ToolboxOps.pasteFromClipboardImpl()
          assert(List(expected)           == findValues(sourceControlName))
          assert(List(expected, expected) == findValues(newControlName))
        }

        assertCutPasteSingleToRepeat(SingleControl, Control3, "value21")

        def assertCutPasteRepeatToSingle(sourceControlName: String, newControlName: String, expected: List[String]): Unit = {

          assert(expected == findValues(sourceControlName))
          ToolboxOps.copyToClipboard(findControlByName(sourceControlName).get.parentUnsafe)

          FormBuilder.selectCell(findControlByName(SingleControl).get.parentUnsafe)
          ToolboxOps.pasteFromClipboardImpl()
          assert(expected           == findValues(sourceControlName))
          assert(List(expected.head) == findValues(newControlName))
        }

        assertCutPasteRepeatToSingle(RepeatedControl1, Control4, List("value111⊙1", "value111⊙2"))
      }
    }

    it("must cut and paste nested grid (#3781)") {

      val RepeatedControl111   = "control-111"
      val RepeatedControl112   = "control-112"
      val RepeatedControl121   = "control-121"
      val RepeatedControl122   = "control-122"

      val NestedGridId         = "grid-1-grid"
      val NestedRepeatedGridId = "grid12-grid"

      withActionAndFBDoc(RepeatedSectionDoc) { implicit ctx =>

        implicit val formRunnerParams: FormRunnerParams =
          FormRunnerParams(AppForm.FormBuilder.app, AppForm.FormBuilder.form, 1, None, None, "new")

        def findValues(controlName: String) =
          ctx.dataRootElem descendant controlName map (_.stringValue)

        def assertContainerCutPaste(gridId: String, controlNames: List[String], expected: List[String]): Unit = {

          assert(expected == (controlNames flatMap findValues))

          FormBuilderRpcApiImpl.containerCut(gridId)
          assert(Nil == (controlNames flatMap findValues))

          ToolboxOps.pasteFromClipboardImpl()
          assert(expected == (controlNames flatMap findValues))
        }

        assertContainerCutPaste(NestedGridId,         List(RepeatedControl111, RepeatedControl112), List("value111⊙1", "value111⊙2", "value112⊙1", "value112⊙2"))
        assertContainerCutPaste(NestedRepeatedGridId, List(RepeatedControl121, RepeatedControl122), List("value121⊙1", "value121⊙2", "value122⊙1", "value122⊙2"))
      }
    }
  }
}