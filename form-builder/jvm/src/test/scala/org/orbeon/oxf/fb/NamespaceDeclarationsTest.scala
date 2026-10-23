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

import org.orbeon.oxf.fb.FormBuilder.*
import org.orbeon.oxf.fb.ToolboxOps.*
import org.orbeon.oxf.fr.FormRunner.*
import org.orbeon.oxf.fr.NodeInfoCell
import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport, XMLSupport}
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.NodeInfoConversions.unsafeUnwrapElement
import org.orbeon.scaxon.SimplePath.*
import org.scalatest.funspec.AnyFunSpecLike


// Check that Form Builder doesn't introduce unneeded namespace declarations into the form definition.
class NamespaceDeclarationsTest
  extends DocumentTestBase
     with ResourceManagerSupport
     with AnyFunSpecLike
     with FormBuilderSupport
     with XMLSupport {

  val SectionTemplatesDoc = "oxf:/org/orbeon/oxf/fb/template-with-section-templates.xhtml"
  val ComponentNamespace  = "http://orbeon.org/oxf/xml/form-builder/component/orbeon/library"

  // All the namespace declarations on the element and its descendants
  def declaredNamespaces(elem: NodeInfo): List[(String, String)] =
    (elem descendantOrSelf *).toList flatMap { e =>
      unsafeUnwrapElement(e).declaredNamespacesIterator.map(ns => ns.prefix -> ns.uri)
    }

  def selectFirstCell()(implicit ctx: FormBuilderDocContext): Unit =
    selectCell(ctx.bodyElem descendant NodeInfoCell.GridTest descendant NodeInfoCell.CellTest head)

  def findBinding(attName: String, attValue: String)(implicit ctx: FormBuilderDocContext): NodeInfo =
    ctx.bindingIndex.iterateDescriptors.flatMap(_.binding).find(_.attValueOpt(attName).contains(attValue)).get

  describe("Namespace declarations introduced by Form Builder (#7619)") {

    it("must not add namespace declarations when inserting controls from the toolbox") {
      withActionAndFBDoc(TemplateWithSingleControlDoc) { implicit ctx =>
        for (bindingId <- List("fb-input", "fr-number", "fr-dropdown-select1", "fr-attachment")) {

          selectFirstCell()
          val controlName = insertNewControl(findBinding("id", bindingId)).get

          assert(declaredNamespaces(findControlByName(controlName).get).isEmpty, s"on `$bindingId` control")
          assert(findDataHolders(controlName).flatMap(declaredNamespaces).isEmpty, s"on `$bindingId` data holder")
          assert(
            (allResources(ctx.resourcesRootElem) / controlName).toList.flatMap(declaredNamespaces).isEmpty,
            s"on `$bindingId` resource holders"
          )
        }
      }
    }

    it("must only declare the component namespace when inserting a section template") {
      withActionAndFBDoc(SectionTemplatesDoc) { implicit ctx =>

        selectFirstCell()
        insertNewSectionTemplate(findBinding("element", "component|section-template-2"))

        // The new section is named after the section template as that name is not in use yet
        val sectionElem   = findControlByName("section-template-2").get
        val componentElem = (sectionElem / *).find(_.getURI == ComponentNamespace).get

        assert(declaredNamespaces(componentElem) == List("component" -> ComponentNamespace))
      }
    }

    it("must not add namespace declarations to section template data holders") {
      withActionAndFBDoc(SectionTemplatesDoc) { implicit ctx =>

        // Holders are updated when the form is loaded, but do it again explicitly
        FormBuilderXPathApi.updateSectionTemplateContentHolders(ctx.formDefinitionRootElem)

        for (sectionName <- List("section-1", "section-2", "section-3", "section-4", "section-5")) {
          val holders = findDataHolders(sectionName)
          assert(holders.nonEmpty && (holders / *).nonEmpty, s"content of `$sectionName` data holder")
          assert(holders.flatMap(declaredNamespaces).isEmpty, s"on `$sectionName` data holder")
        }
      }
    }

  }
}
