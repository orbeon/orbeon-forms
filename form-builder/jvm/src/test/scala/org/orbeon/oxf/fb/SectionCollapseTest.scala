/**
 * Copyright (C) 2026 Orbeon, Inc.
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
import org.orbeon.dom.Document
import org.orbeon.oxf.fb.FormBuilder.*
import org.orbeon.oxf.fb.ToolboxOps.insertNewControl
import org.orbeon.oxf.fr.FormRunner.*
import org.orbeon.oxf.fr.Names
import org.orbeon.oxf.fr.NodeInfoCell.*
import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport}
import org.orbeon.oxf.xforms.action.XFormsAPI
import org.orbeon.scaxon.NodeConversions
import org.orbeon.scaxon.NodeConversions.*
import org.orbeon.scaxon.SimplePath.*
import org.scalatest.funspec.AnyFunSpecLike


class SectionCollapseTest
  extends DocumentTestBase
     with ResourceManagerSupport
     with AnyFunSpecLike
     with FormBuilderSupport {

  private def fbDocWithDynamic(url: String): Document = {
    val doc  = formBuilderDoc(url)
    val body = doc.getRootElement.element("body")
    val dynamicElem = NodeConversions.elemToOrbeonDom(
      <xxf:dynamic xmlns:xxf="http://orbeon.org/oxf/xml/xforms" id="fb" ref="instance('fb-form-instance')"/>
    ).getRootElement.detach()
    body.add(dynamicElem)
    doc
  }

  private def withDynamicFBDoc[T](url: String)(body: FormBuilderDocContext => T): T =
    withTestExternalContext { _ =>
      val doc = setupDocument(fbDocWithDynamic(url))
      withActionAndDoc(doc) {
        val model = doc.models.find(_.getId == Names.FormModel).get
        body(FormBuilderDocContext(model))
      }
    }

  describe("Section collapse/expand and focus helpers") {

    it("must solo a section on first toggle and expand all sections on second toggle") {
      withDynamicFBDoc(TemplateWithSingleControlDoc) { implicit ctx =>

        // Select first cell in `section-1` and insert `section-2` and `section-3`
        selectCell((ctx.bodyElem descendant GridTest descendant CellTest).head)
        val (section2Elem, _, _) = ToolboxOps.insertNewSection(withGrid = true, suggestedNameOrNull = "section-2").get
        val (section3Elem, _, _) = ToolboxOps.insertNewSection(withGrid = true, suggestedNameOrNull = "section-3").get
        XFormsAPI.refresh("fr-form-model")

        val section1Elem = findControlByName("section-1").get

        assert(isSectionOpen(section1Elem))
        assert(isSectionOpen(section2Elem))
        assert(isSectionOpen(section3Elem))

        // Select cell in `section-1` before soloing `section-2`
        selectCell((section1Elem descendant CellTest).head)
        assert(findSelectedCell.exists(_ ancestor * exists (_ isSameNodeInfo section1Elem)))

        // First toggle on `section-2`: solo `section-2` (collapse `section-1` and `section-3`, select cell in `section-2`)
        toggleOtherSections(section2Elem.id)
        XFormsAPI.refresh("fr-form-model")

        assert(! isSectionOpen(section1Elem))
        assert(  isSectionOpen(section2Elem))
        assert(! isSectionOpen(section3Elem))
        assert(findSelectedCell.exists(_ ancestor * exists (_ isSameNodeInfo section2Elem)))

        // Second toggle on `section-2` while already soloed: restore (expand all sections)
        toggleOtherSections(section2Elem.id)
        XFormsAPI.refresh("fr-form-model")

        assert(isSectionOpen(section1Elem))
        assert(isSectionOpen(section2Elem))
        assert(isSectionOpen(section3Elem))
      }
    }

    it("must keep ancestor and descendant subsections open when soloing and expand closed sections on focus") {
      withDynamicFBDoc(TemplateWithSingleControlDoc) { implicit ctx =>

        selectCell((ctx.bodyElem descendant GridTest descendant CellTest).head)
        val (section2Elem, _, _)    = ToolboxOps.insertNewSection(withGrid = true, suggestedNameOrNull = "section-2").get
        val (subsection2aElem, _, _) = ToolboxOps.insertNewSection(withGrid = true, suggestedNameOrNull = "subsection-2a").get
        moveSectionRight(subsection2aElem)
        val (section3Elem, _, _)    = ToolboxOps.insertNewSection(withGrid = true, suggestedNameOrNull = "section-3").get
        XFormsAPI.refresh("fr-form-model")

        val section1Elem     = findControlByName("section-1").get
        val updatedSub2aElem = findControlByName("subsection-2a").get

        // Solo `subsection-2a`: its parent `section-2` and `subsection-2a` must be open; `section-1` and `section-3` closed
        toggleOtherSections(updatedSub2aElem.id)
        XFormsAPI.refresh("fr-form-model")

        assert(! isSectionOpen(section1Elem))
        assert(  isSectionOpen(section2Elem))
        assert(  isSectionOpen(updatedSub2aElem))
        assert(! isSectionOpen(section3Elem))
        assert(findSelectedCell.exists(_ ancestor * exists (_ isSameNodeInfo updatedSub2aElem)))

        // Focusing on collapsed `section-3` must expand it and select its first cell without collapsing `section-2`
        expandAndSelectSection(section3Elem.id)
        XFormsAPI.refresh("fr-form-model")

        assert(isSectionOpen(section2Elem))
        assert(isSectionOpen(section3Elem))
        assert(findSelectedCell.exists(_ ancestor * exists (_ isSameNodeInfo section3Elem)))

        // If an empty cell is selected in `section-3` while a later cell in `section-3` has a control,
        // `sectionExpandAndSelect` must select the first cell with a control
        val section3Cells = (section3Elem descendant CellTest).toList
        selectCell(section3Cells(1))
        insertNewControl(<binding element="xf|input" xmlns:xf="http://www.w3.org/2002/xforms"/>)
        selectCell(section3Cells.head)
        assert(! findSelectedCell.exists(_.hasChildElement))

        FormBuilderRpcApiImpl.sectionExpandAndSelect(section3Elem.id)
        XFormsAPI.refresh("fr-form-model")

        assert(findSelectedCell.contains(section3Cells(1)))
        assert(findStartingCell.contains(section3Cells(1)))
      }
    }
  }
}
