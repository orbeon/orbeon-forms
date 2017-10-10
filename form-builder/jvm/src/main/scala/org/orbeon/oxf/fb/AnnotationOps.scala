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

import org.orbeon.oxf.fb.FormBuilder.{findSectionsWithTemplates, getControlNameOpt}
import org.orbeon.oxf.fr.XMLNames.{FR, XF}
import org.orbeon.oxf.xforms.action.XFormsAPI.delete
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.SimplePath._


// Functions called from `annotate.xpl` and `model.xml` for annotation, and which require a
// `FormBuilderDocContext` explicitly referring to a given XML document.
trait AnnotationOps extends GridOps with ContainerOps with ResourcesOps {

  // Find all resource holders and elements which are unneeded because the resources are blank
  //@XPathFunction
  def findBlankLHHAHoldersAndElements(inDoc: NodeInfo, lhha: String): Seq[NodeInfo] = {

    implicit val ctx = FormBuilderDocContext(inDoc)

    val allHelpElements =
      ctx.rootElem.root descendant ((if (lhha=="text") FR else XF) → lhha) map
      (lhhaElement ⇒ lhhaElement → lhhaElement.attValue("ref")) collect
      { case (lhhaElement, HelpRefMatcher(controlName)) ⇒ lhhaElement → controlName }

    val allUnneededHolders =
      allHelpElements collect {
        case (lhhaElement, controlName) if hasBlankOrMissingLHHAForAllLangsUseDoc(controlName, lhha) ⇒
           lhhaHoldersForAllLangsUseDoc(controlName, lhha) :+ lhhaElement
      }

    allUnneededHolders.flatten
  }

  // Create template content from a bind name
  //@XPathFunction
  // FIXME: Saxon can pass null as `bindings`.
  def createTemplateContentFromBindNameXPath(inDoc: NodeInfo, name: String, bindings: List[NodeInfo]): Option[NodeInfo] =
    createTemplateContentFromBindName(name, Option(bindings) getOrElse Nil)(FormBuilderDocContext(inDoc))

  //@XPathFunction
  def nextIdsXPath(inDoc: NodeInfo, token: String, count: Int): Seq[String] =
    nextIds(token, count)(FormBuilderDocContext(inDoc))

    // See: https://github.com/orbeon/orbeon-forms/issues/633
  //@XPathFunction
  def deleteSectionTemplateContentHolders(inDoc: NodeInfo): Unit = {

    implicit val ctx = FormBuilderDocContext(inDoc)

    // Find data holders for all section templates
    val holders =
      for {
        section     ← findSectionsWithTemplates(ctx.bodyElem)
        controlName ← getControlNameOpt(section).toList
        holder      ← findDataHolders(controlName)
      } yield
        holder

    // Delete all elements underneath those holders
    holders foreach { holder ⇒
      delete(holder / *)
    }
  }
}
