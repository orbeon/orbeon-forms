/**
 *  Copyright (C) 2010 Orbeon, Inc.
 *
 *  This program is free software; you can redistribute it and/or modify it under the terms of the
 *  GNU Lesser General Public License as published by the Free Software Foundation; either version
 *  2.1 of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU Lesser General Public License for more details.
 *
 *  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.xforms.analysis

import cats.syntax.option._
import org.orbeon.dom.{Element, QName}
import org.orbeon.oxf.xforms.analysis.EventHandler.{isAction, isContainerAction, isEventHandler}
import org.orbeon.oxf.xforms.analysis.controls._
import org.orbeon.oxf.xforms.analysis.model.{Model, Submission}
import org.orbeon.oxf.xml.dom.Extensions._
import org.orbeon.xforms.XFormsNames.{XXFORMS_VALUE_QNAME, _}
import org.orbeon.xforms.xbl.Scope
import org.orbeon.xml.NamespaceMapping


object ControlAnalysisFactory {

  // Control factories
  type ControlFactory =
    (Int, Element,  Option[ElementAnalysis], Option[ElementAnalysis], String, String, NamespaceMapping, Scope, Scope) => ElementAnalysis

  type ControlFactoryWithPart =
    (PartAnalysisContextForTree, Int, Element,  Option[ElementAnalysis], Option[ElementAnalysis], String, String, NamespaceMapping, Scope, Scope) => ElementAnalysis

  private val VariableControlFactory: ControlFactory         = VariableAnalysisBuilder(_, _, _, _, _, _, _, _, _, forModel = false)
  private val LHHAControlFactory    : ControlFactoryWithPart = LHHAAnalysisBuilder(_, _, _, _, _, _, _, _, _, _)

  // Variable factories indexed by QName
  // NOTE: We have all these QNames for historical reasons (XForms 2 is picking <xf:var>)
  private val VariableFactory =
    VariableAnalysis.VariableQNames.iterator map
      (qName => qName -> VariableControlFactory) toMap

  private val byQNameFactoryWithPart = Map[QName, ControlFactoryWithPart](
    // Core value controls
    XXFORMS_TEXT_QNAME            -> (OutputControlBuilder(_, _, _, _, _, _, _, _, _, _)),// TODO: don't accept any external events
    XFORMS_OUTPUT_QNAME           -> (OutputControlBuilder(_, _, _, _, _, _, _, _, _, _)),
    // Selection controls
    XFORMS_SELECT_QNAME           -> (SelectionControlBuilder(_, _, _, _, _, _, _, _, _, _)),
    XFORMS_SELECT1_QNAME          -> (SelectionControlBuilder(_, _, _, _, _, _, _, _, _, _)),
    // LHHA
    LABEL_QNAME                   -> LHHAControlFactory,
    HELP_QNAME                    -> LHHAControlFactory,
    HINT_QNAME                    -> LHHAControlFactory,
    ALERT_QNAME                   -> LHHAControlFactory,
    // Model
    XFORMS_INSTANCE_QNAME         -> (InstanceBuilder(_, _, _, _, _, _, _, _, _, _)),
    XFORMS_BIND_QNAME             -> (StaticBindBuilder(_, _, _, _, _, _, _, _, _, _)),
    // Container controls
    XFORMS_CASE_QNAME             -> (CaseControlBuilder(_, _, _, _, _, _, _, _, _, _)),
  )

  // Other factories indexed by QName
  private val byQNameFactory = Map[QName, ControlFactory](
    XBL_TEMPLATE_QNAME            -> (new ContainerControl(_, _, _, _, _, _, _, _, _)),
    // Core value controls
    XFORMS_INPUT_QNAME            -> (new InputControl(_, _, _, _, _, _, _, _, _)),
    XFORMS_SECRET_QNAME           -> (new SecretControl(_, _, _, _, _, _, _, _, _)),
    XFORMS_TEXTAREA_QNAME         -> (new TextareaControl(_, _, _, _, _, _, _, _, _)),
    XFORMS_UPLOAD_QNAME           -> (new UploadControl(_, _, _, _, _, _, _, _, _)),
    XFORMS_RANGE_QNAME            -> (new InputValueControl(_, _, _, _, _, _, _, _, _)),
    // Core controls
    XFORMS_TRIGGER_QNAME          -> (new TriggerControl(_, _, _, _, _, _, _, _, _)),
    XFORMS_SUBMIT_QNAME           -> (new TriggerControl(_, _, _, _, _, _, _, _, _)),
    // Attributes
    XXFORMS_ATTRIBUTE_QNAME       -> (new AttributeControl(_, _, _, _, _, _, _, _, _)),
    // Container controls
    XFORMS_GROUP_QNAME            -> (new GroupControl(_, _, _, _, _,  _, _, _, _)),
    XFORMS_SWITCH_QNAME           -> (new SwitchControl(_, _, _, _, _,  _, _, _, _)),
    XXFORMS_DIALOG_QNAME          -> (new DialogControl(_, _, _, _, _, _, _, _, _)),
    // Dynamic control
    XXFORMS_DYNAMIC_QNAME         -> (new ElementAnalysis(_, _, _, _, _, _, _, _, _) with RequiredSingleNode with ViewTrait), // NOTE: No longer `ContainerControl`!
    // Repeat control
    XFORMS_REPEAT_QNAME           -> (new RepeatControl(_, _, _, _, _, _, _, _, _)),
    XFORMS_REPEAT_ITERATION_QNAME -> (new RepeatIterationControl(_, _, _, _, _, _, _, _, _)),
    // Model
    XFORMS_MODEL_QNAME            -> (new Model(_, _, _, _, _, _, _, _, _)),
    XFORMS_SUBMISSION_QNAME       -> (new Submission(_, _, _, _, _, _, _, _, _)),
    XFORMS_HEADER_QNAME           -> (new HeaderControl(_, _, _, _, _, _, _, _, _)),
    XFORMS_NAME_QNAME             -> (NestedNameOrValueControlBuilder(_, _, _, _, _, _, _, _, _)),
    // Itemsets
    XFORMS_CHOICES_QNAME          -> (new ElementAnalysis(_, _, _, _, _, _, _, _, _) with WithChildrenTrait),
    XFORMS_ITEM_QNAME             -> (new ElementAnalysis(_, _, _, _, _, _, _, _, _) with WithChildrenTrait),
    XFORMS_ITEMSET_QNAME          -> (new ElementAnalysis(_, _, _, _, _, _, _, _, _) with WithChildrenTrait),
    XFORMS_VALUE_QNAME            -> (NestedNameOrValueControlBuilder(_, _, _, _, _, _, _, _, _)),
    XFORMS_COPY_QNAME             -> (new ElementAnalysis(_, _, _, _, _, _, _, _, _) with RequiredSingleNode),
    // Variable nested value
    XXFORMS_VALUE_QNAME           -> (new ElementAnalysis(_, _, _, _, _, _, _, _, _) with OptionalSingleNode with VariableValueTrait),
    XXFORMS_SEQUENCE_QNAME        -> (new ElementAnalysis(_, _, _, _, _, _, _, _, _) with OptionalSingleNode with VariableValueTrait),
    // Event property
    XFORMS_PROPERTY_QNAME         -> (new PropertyControl(_, _, _, _, _, _, _, _, _)),
    XXFORMS_CONTEXT_QNAME         -> (new PropertyControl(_, _, _, _, _, _, _, _, _)) // legacy
  ) ++ VariableFactory

  private val ControlFactoryPf: PartialFunction[Element, ControlFactory] =
    { case e: Element if byQNameFactory.isDefinedAt(e.getQName) => byQNameFactory(e.getQName) }

  private val ActionFactoryPf: PartialFunction[Element, ControlFactory] = {
    case e if isContainerAction(e.getQName) && isEventHandler(e) => EventHandlerBuilder(_, _, _, _, _, _, _, _, _, withChildren = true, withExpressionOrConstant = false)
    case e if isContainerAction(e.getQName)                      => new ElementAnalysis(_, _, _, _, _, _, _, _, _) with ActionTrait with WithChildrenTrait
    case e if e.getQName == XFORMS_MESSAGE_QNAME                 => MessageActionBuilder(_, _, _, _, _, _, _, _, _, isEventHandler = isEventHandler(e))
    case e if isAction(e.getQName) && isEventHandler(e)          => EventHandlerBuilder(_, _, _, _, _, _, _, _, _, withChildren = false, withExpressionOrConstant = false)
    case e if isAction(e.getQName)                               => new ElementAnalysis(_, _, _, _, _, _, _, _, _) with ActionTrait
  }

  private val ControlOrActionFactory = ControlFactoryPf orElse ActionFactoryPf lift

  def create(
    partAnalysisCtx   : PartAnalysisContextForTree,
    index             : Int,
    controlElement    : Element,
    parent            : Option[ElementAnalysis],
    preceding         : Option[ElementAnalysis],
    controlStaticId   : String,
    controlPrefixedId : String,
    namespaceMapping  : NamespaceMapping,
    scope             : Scope,
    containerScope    : Scope
  ): Option[ElementAnalysis] = {

    require(controlElement ne null)
    require(scope ne null)

    partAnalysisCtx.metadata.findAbstractBindingByPrefixedId(scope.prefixedIdForStaticId(controlElement.idOrNull)) match {
      case Some(abstractBinding) =>
        ComponentControlBuilder(
          partAnalysisCtx,
          index,
          controlElement,
          parent,
          preceding,
          controlStaticId,
          controlPrefixedId,
          namespaceMapping,
          scope,
          containerScope,
          abstractBinding.modeValue,
          abstractBinding.modeLHHA
        ).some
      case None =>
        if (parent.exists(_.localName == "model") && VariableAnalysis.VariableQNames(controlElement.getQName))
          VariableAnalysisBuilder(
            index,
            controlElement,
            parent,
            preceding,
            controlStaticId,
            controlPrefixedId,
            namespaceMapping,
            scope,
            containerScope,
            forModel = true
          ).some
        else {
          byQNameFactoryWithPart.get(controlElement.getQName) map {
            _.apply(
              partAnalysisCtx,
              index,
              controlElement,
              parent,
              preceding,
              controlStaticId,
              controlPrefixedId,
              namespaceMapping,
              scope,
              containerScope
            )
          } orElse {
            ControlOrActionFactory(controlElement) map {
              _.apply(
                index,
                controlElement,
                parent,
                preceding,
                controlStaticId,
                controlPrefixedId,
                namespaceMapping,
                scope,
                containerScope
              )
            }
          }
        }
    }
  }
}
