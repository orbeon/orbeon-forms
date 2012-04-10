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

import controls._
import model.{Model, Submission}
import org.orbeon.oxf.xforms.XFormsConstants._
import org.orbeon.oxf.xforms.XFormsUtils
import org.dom4j.{QName, Element}
import org.orbeon.oxf.xforms.action.XFormsActions
import scala.PartialFunction
import org.orbeon.oxf.xforms.xbl.Scope

object ControlAnalysisFactory {

    // Control factories
    type ControlFactory = (StaticStateContext, Element,  Option[ElementAnalysis], Option[ElementAnalysis], Scope) ⇒ ElementAnalysis

    // NOTE: Not all controls need separate classes, so for now we use generic ones like e.g. CoreControl instead of InputControl
    private val valueControlFactory: ControlFactory =     (new CoreControl(_, _, _, _, _) with ValueTrait with ChildrenBuilderTrait with ChildrenLHHAAndActionsTrait)
    private val triggerControlFactory: ControlFactory =   (new CoreControl(_, _, _, _, _) with TriggerAppearanceTrait with ChildrenBuilderTrait with ChildrenLHHAAndActionsTrait)
    private val selectionControlFactory: ControlFactory = (new CoreControl(_, _, _, _, _) with ValueTrait with SelectionControl with ChildrenBuilderTrait with ChildrenLHHAAndActionsTrait)
    private val variableControlFactory: ControlFactory =  (new VariableControl(_, _, _, _, _) with ChildrenActionsTrait)
    private val containerControlFactory: ControlFactory = (new ContainerControl(_, _, _, _, _) with LHHATrait with ChildrenBuilderTrait) // NOTE: no LHHA in spec yet, but will make sense
    private val lhhaControlFactory: ControlFactory =      (new LHHAAnalysis(_, _, _, _, _))

    // Variable factories indexed by QName
    // NOTE: We have all these QNames for historical reasons (XForms 2 is picking <xforms:var>)
    private val variableFactory =
        Seq(XXFORMS_VARIABLE_QNAME, XXFORMS_VAR_QNAME, XFORMS_VARIABLE_QNAME, XFORMS_VAR_QNAME, EXFORMS_VARIABLE_QNAME) map
            (qName ⇒ qName → variableControlFactory) toMap

    // Other factories indexed by QName
    private val byQNameFactory = Map[QName, ControlFactory](
        XBL_TEMPLATE_QNAME            → (new ContainerControl(_, _, _, _, _) with ChildrenBuilderTrait),
        // Core value controls
        XFORMS_INPUT_QNAME            → valueControlFactory,
        XFORMS_SECRET_QNAME           → valueControlFactory,
        XFORMS_TEXTAREA_QNAME         → valueControlFactory,
        XFORMS_OUTPUT_QNAME           → valueControlFactory,
        XFORMS_UPLOAD_QNAME           → valueControlFactory,
        XFORMS_RANGE_QNAME            → valueControlFactory,
        XXFORMS_TEXT_QNAME            → valueControlFactory,
        // Core controls
        XFORMS_TRIGGER_QNAME          → triggerControlFactory,
        XFORMS_SUBMIT_QNAME           → triggerControlFactory,
        // Selection controls
        XFORMS_SELECT_QNAME           → selectionControlFactory,
        XFORMS_SELECT1_QNAME          → selectionControlFactory,
        // Attributes
        XXFORMS_ATTRIBUTE_QNAME       → (new AttributeControl(_, _, _, _, _)),
        // Container controls
        XFORMS_GROUP_QNAME            → containerControlFactory,
        XFORMS_SWITCH_QNAME           → containerControlFactory,
        XFORMS_CASE_QNAME             → containerControlFactory,
        XXFORMS_DIALOG_QNAME          → containerControlFactory,
        // Dynamic control
        XXFORMS_DYNAMIC_QNAME         → (new ContainerControl(_, _, _, _, _)),
        // Repeat control
        XFORMS_REPEAT_QNAME           → (new RepeatControl(_, _, _, _, _)),
        XFORMS_REPEAT_ITERATION_QNAME → (new RepeatIterationControl(_, _, _, _, _)),
        // LHHA
        LABEL_QNAME                   → lhhaControlFactory,
        HELP_QNAME                    → lhhaControlFactory,
        HINT_QNAME                    → lhhaControlFactory,
        ALERT_QNAME                   → lhhaControlFactory,
        // Model
        XFORMS_MODEL_QNAME            → (new Model(_, _, _, _, _)),
        XFORMS_SUBMISSION_QNAME       → (new Submission(_, _, _, _, _))
    ) ++ variableFactory

    private val controlFactory: PartialFunction[Element, ControlFactory] =
        { case e: Element if byQNameFactory.isDefinedAt(e.getQName) ⇒ byQNameFactory(e.getQName) }

    def create(
          context: StaticStateContext,
          controlElement: Element,
          parent: Option[ElementAnalysis],
          preceding: Option[ElementAnalysis],
          scope: Scope): Option[ElementAnalysis] = {

        require(controlElement ne null)
        require(scope ne null)

        // XBL binding if any
        val binding = Option(context.partAnalysis.xblBindings.getBinding(scope.prefixedIdForStaticId(XFormsUtils.getElementId(controlElement))))

        // Not all factories are simply indexed by QName, so compose those with factories for components and actions
        val componentFactory: PartialFunction[Element, ControlFactory] = {
            case e if binding.isDefined && binding.get.abstractBinding.modeLHHA ⇒ (new ComponentControl(_, _, _, _, _) with LHHATrait)
            case e if binding.isDefined ⇒ (new ComponentControl(_, _, _, _, _))
        }

        val f = controlFactory orElse componentFactory orElse XFormsActions.factory

        // Create the ElementAnalysis if possible
        f.lift(controlElement) map
            (_(context, controlElement, parent, preceding, scope))
    }

    def isVariable(qName: QName) = variableFactory.contains(qName)
}