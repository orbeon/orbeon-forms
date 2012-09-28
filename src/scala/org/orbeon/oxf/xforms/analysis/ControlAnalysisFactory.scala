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

import org.orbeon.oxf.xforms.analysis.controls._
import model.{Instance, Model, Submission}
import org.orbeon.oxf.xforms.XFormsConstants._
import org.orbeon.oxf.xforms.XFormsUtils
import org.dom4j.{QName, Element}
import org.orbeon.oxf.xforms.action.XFormsActions
import org.orbeon.oxf.xforms.xbl.Scope
import org.orbeon.oxf.xforms.event.XFormsEvents._

object ControlAnalysisFactory {

    // Control factories
    type ControlFactory = (StaticStateContext, Element,  Option[ElementAnalysis], Option[ElementAnalysis], Scope) ⇒ ElementAnalysis

    private val TriggerExternalEvents = Set(XFORMS_FOCUS, XFORMS_HELP, DOM_ACTIVATE, XXFORMS_VALUE_OR_ACTIVATE)
    private val ValueExternalEvents   = TriggerExternalEvents + XXFORMS_VALUE
    // NOTE: xxforms-upload-done is a trusted server event so doesn't need to be listed here
    private val UploadExternalEvents  = Set(XFORMS_SELECT, XXFORMS_UPLOAD_START, XXFORMS_UPLOAD_CANCEL, XXFORMS_UPLOAD_PROGRESS)

    abstract class ValueControl(staticStateContext: StaticStateContext, element: Element, parent: Option[ElementAnalysis], preceding: Option[ElementAnalysis], scope: Scope)
            extends CoreControl(staticStateContext, element, parent, preceding, scope)
            with ValueTrait
            with ChildrenBuilderTrait
            with ChildrenLHHAAndActionsTrait
            with FormatTrait {
    }

    class InputValueControl(staticStateContext: StaticStateContext, element: Element, parent: Option[ElementAnalysis], preceding: Option[ElementAnalysis], scope: Scope)
            extends ValueControl(staticStateContext, element, parent, preceding, scope)
            with RequiredSingleNode {
        override protected def externalEventsDef = super.externalEventsDef ++ ValueExternalEvents
        override val externalEvents = externalEventsDef
    }

    class SelectionControl(staticStateContext: StaticStateContext, element: Element, parent: Option[ElementAnalysis], preceding: Option[ElementAnalysis], scope: Scope)
            extends InputValueControl(staticStateContext, element, parent, preceding, scope)
            with SelectionControlTrait

    class TriggerControl(staticStateContext: StaticStateContext, element: Element, parent: Option[ElementAnalysis], preceding: Option[ElementAnalysis], scope: Scope)
            extends CoreControl(staticStateContext, element, parent, preceding, scope)
            with OptionalSingleNode
            with TriggerAppearanceTrait
            with ChildrenBuilderTrait
            with ChildrenLHHAAndActionsTrait {
        override protected def externalEventsDef = super.externalEventsDef ++ TriggerExternalEvents
        override val externalEvents              = externalEventsDef
    }

    class UploadControl(staticStateContext: StaticStateContext, element: Element, parent: Option[ElementAnalysis], preceding: Option[ElementAnalysis], scope: Scope)
            extends InputValueControl(staticStateContext, element, parent, preceding, scope) {
        override protected def externalEventsDef = super.externalEventsDef ++ UploadExternalEvents
        override val externalEvents = externalEventsDef
    }

    private val VariableControlFactory: ControlFactory = (new VariableControl(_, _, _, _, _) with ChildrenActionsTrait)
    private val LHHAControlFactory    : ControlFactory = (new LHHAAnalysis(_, _, _, _, _))
    private val ValueControlFactory   : ControlFactory = (new InputValueControl(_, _, _, _, _))
    private val SwitchControlFactory  : ControlFactory = (new ContainerControl(_, _, _, _, _) with OptionalSingleNode with LHHATrait with ChildrenBuilderTrait)
    private val CaseControlFactory    : ControlFactory = (new ContainerControl(_, _, _, _, _) with OptionalSingleNode with LHHATrait with ChildrenBuilderTrait)

    private val GroupControlFactory: ControlFactory =
        (new ContainerControl(_, _, _, _, _) with OptionalSingleNode with LHHATrait with ChildrenBuilderTrait {
            override val externalEvents = super.externalEvents + DOM_ACTIVATE // allow DOMActivate
        })

    private val DialogControlFactory: ControlFactory =
        (new ContainerControl(_, _, _, _, _) with OptionalSingleNode with LHHATrait with ChildrenBuilderTrait {
            override val externalEvents = super.externalEvents + XXFORMS_DIALOG_CLOSE // allow xxforms-dialog-close
        })

    // Variable factories indexed by QName
    // NOTE: We have all these QNames for historical reasons (XForms 2 is picking <xforms:var>)
    private val variableFactory =
        Seq(XXFORMS_VARIABLE_QNAME, XXFORMS_VAR_QNAME, XFORMS_VARIABLE_QNAME, XFORMS_VAR_QNAME, EXFORMS_VARIABLE_QNAME) map
            (qName ⇒ qName → VariableControlFactory) toMap

    // Other factories indexed by QName
    private val byQNameFactory = Map[QName, ControlFactory](
        XBL_TEMPLATE_QNAME            → (new ContainerControl(_, _, _, _, _) with ChildrenBuilderTrait),
        // Core value controls
        XFORMS_INPUT_QNAME            → ValueControlFactory,
        XFORMS_SECRET_QNAME           → ValueControlFactory,
        XFORMS_TEXTAREA_QNAME         → ValueControlFactory,
        XFORMS_UPLOAD_QNAME           → (new UploadControl(_, _, _, _, _)),
        XFORMS_RANGE_QNAME            → ValueControlFactory,
        XXFORMS_TEXT_QNAME            → (new OutputControl(_, _, _, _, _)),// TODO: don't accept any external events
        XFORMS_OUTPUT_QNAME           → (new OutputControl(_, _, _, _, _)),
        // Core controls
        XFORMS_TRIGGER_QNAME          → (new TriggerControl(_, _, _, _, _)),
        XFORMS_SUBMIT_QNAME           → (new TriggerControl(_, _, _, _, _)),
        // Selection controls
        XFORMS_SELECT_QNAME           → (new SelectionControl(_, _, _, _, _)),
        XFORMS_SELECT1_QNAME          → (new SelectionControl(_, _, _, _, _)),
        // Attributes
        XXFORMS_ATTRIBUTE_QNAME       → (new AttributeControl(_, _, _, _, _)),
        // Container controls
        XFORMS_GROUP_QNAME            → GroupControlFactory,
        XFORMS_SWITCH_QNAME           → SwitchControlFactory,
        XFORMS_CASE_QNAME             → CaseControlFactory,
        XXFORMS_DIALOG_QNAME          → DialogControlFactory,
        // Dynamic control
        XXFORMS_DYNAMIC_QNAME         → (new ContainerControl(_, _, _, _, _) with RequiredSingleNode),
        // Repeat control
        XFORMS_REPEAT_QNAME           → (new RepeatControl(_, _, _, _, _)),
        XFORMS_REPEAT_ITERATION_QNAME → (new RepeatIterationControl(_, _, _, _, _)),
        // LHHA
        LABEL_QNAME                   → LHHAControlFactory,
        HELP_QNAME                    → LHHAControlFactory,
        HINT_QNAME                    → LHHAControlFactory,
        ALERT_QNAME                   → LHHAControlFactory,
        // Model
        XFORMS_MODEL_QNAME            → (new Model(_, _, _, _, _)),
        XFORMS_SUBMISSION_QNAME       → (new Submission(_, _, _, _, _)),
        XFORMS_INSTANCE_QNAME         → (new Instance(_, _, _, _, _))
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
        val binding = context.partAnalysis.getBinding(scope.prefixedIdForStaticId(XFormsUtils.getElementId(controlElement)))

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