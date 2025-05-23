/**
 * Copyright (C) 2013 Orbeon, Inc.
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

import org.orbeon.oxf.fb.FormBuilderDocContext.{BindingIndexKey, FormBuilderNs}
import org.orbeon.oxf.fr.{FormRunnerDocContext, Names}
import org.orbeon.oxf.xforms.action.XFormsAPI.*
import org.orbeon.oxf.xforms.model.XFormsModel
import org.orbeon.oxf.xforms.xbl.{BindingDescriptor, BindingIndex}
import org.orbeon.oxf.xforms.xbl.BindingDescriptor.{buildIndexFromBindingDescriptors, getAllRelevantDescriptors}
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.Implicits.*
import org.orbeon.scaxon.SimplePath.*


case class FormBuilderDocContext(
  explicitFormDefinitionInstance : Option[NodeInfo],   // for annotating the form definition outside of an instance
  formBuilderModel               : Option[XFormsModel] // always present at runtime, but missing for annotation tests
) extends
  FormRunnerDocContext {

  lazy val formDefinitionInstance = formBuilderModel flatMap (_.findInstance("fb-form-instance"))
  lazy val undoInstance           = formBuilderModel flatMap (_.findInstance("fb-undo-instance"))
  lazy val userAgentInstance      = formBuilderModel flatMap (_.findInstance("fb-user-agent-instance"))

  lazy val formDefinitionRootElem = explicitFormDefinitionInstance getOrElse formDefinitionInstance.get.rootElement

  lazy val undoRootElem = undoInstance.get.rootElement

  // We cache the index against the form definition document
  lazy implicit val bindingIndex: BindingIndex[BindingDescriptor] =
    formBuilderModel
      .flatMap(_.containingDocument.getAttribute(FormBuilderNs, BindingIndexKey))
      .map(_.asInstanceOf[BindingIndex[BindingDescriptor]])
      .getOrElse {
        val index = buildIndexFromBindingDescriptors(getAllRelevantDescriptors(componentBindings))
        formBuilderModel.foreach(_.containingDocument.setAttribute(FormBuilderNs, BindingIndexKey, index))
        index
      }

  private def componentBindings: Seq[NodeInfo] =
    asScalaSeq(formBuilderModel.get.getVariable("component-bindings")).asInstanceOf[Seq[NodeInfo]]
}

object FormBuilderDocContext {

  private val FormBuilderNs   = "fb"
  private val BindingIndexKey = "binding-index"

  // Create with a specific form definition document, but still pass a model to provide access to variables
  def apply(inDoc: NodeInfo): FormBuilderDocContext =
    FormBuilderDocContext(Some(inDoc.rootElement), topLevelModel(Names.FormModel))

  def apply(formBuilderModel: XFormsModel): FormBuilderDocContext =
    FormBuilderDocContext(None, Some(formBuilderModel))

  def apply(): FormBuilderDocContext =
    FormBuilderDocContext(topLevelModel(Names.FormModel) getOrElse (throw new IllegalStateException))
}
