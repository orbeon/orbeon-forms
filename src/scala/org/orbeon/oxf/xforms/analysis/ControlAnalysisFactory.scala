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
import model.{Instance, Model, Submission}
import org.orbeon.oxf.xforms.XFormsConstants._
import org.orbeon.oxf.xforms.XFormsUtils
import org.dom4j.{QName, Element}
import org.orbeon.oxf.xforms.action.XFormsActions
import scala.PartialFunction
import org.orbeon.oxf.xforms.xbl.Scope
import org.orbeon.oxf.xforms.event.XFormsEvents._

object ControlAnalysisFactory {

    // Control factories
    type ControlFactory = (StaticStateContext, Element,  Option[ElementAnalysis], Option[ElementAnalysis], Scope) ⇒ ElementAnalysis

    private val VariableControl: ControlFactory = (new VariableControl(_, _, _, _, _) with ChildrenActionsTrait)
    private val LHHAControl: ControlFactory     = (new LHHAAnalysis(_, _, _, _, _))

    private val TriggerControlExternalEvents = Set(XFORMS_FOCUS, XFORMS_HELP, DOM_ACTIVATE, XXFORMS_VALUE_OR_ACTIVATE)
    private val ValueControlExternalEvents   = TriggerControlExternalEvents + XXFORMS_VALUE

    private val ValueControl: ControlFactory =
        (new CoreControl(_, _, _, _, _) with ValueTrait with ChildrenBuilderTrait with ChildrenLHHAAndActionsTrait
            { override val externalEvents = super.externalEvents ++ ValueControlExternalEvents })

    private val SelectionControl: ControlFactory =
        (new CoreControl(_, _, _, _, _) with ValueTrait with SelectionControl with ChildrenBuilderTrait with ChildrenLHHAAndActionsTrait
            { override val externalEvents = super.externalEvents ++ ValueControlExternalEvents })

    private val TriggerControl: ControlFactory =
        (new CoreControl(_, _, _, _, _) with SingleNodeTrait with TriggerAppearanceTrait with ChildrenBuilderTrait with ChildrenLHHAAndActionsTrait
            { override val externalEvents = super.externalEvents ++ TriggerControlExternalEvents })

    // NOTE: xxforms-upload-done is a trusted server event so doesn't need to be listed here
    private val UploadControl: ControlFactory =
        (new CoreControl(_, _, _, _, _) with ValueTrait with ChildrenBuilderTrait with ChildrenLHHAAndActionsTrait
            { override val externalEvents = super.externalEvents ++
                Set(XFORMS_FOCUS,
                    XFORMS_HELP,
                    XFORMS_SELECT,
                    XXFORMS_VALUE,
                    XXFORMS_UPLOAD_START,
                    XXFORMS_UPLOAD_CANCEL,
                    XXFORMS_UPLOAD_PROGRESS) })

    private val GroupControl: ControlFactory =
        (new ContainerControl(_, _, _, _, _) with SingleNodeTrait with LHHATrait with ChildrenBuilderTrait
            { override val externalEvents = super.externalEvents + DOM_ACTIVATE }) // allow DOMActivate on group

    private val DialogControl: ControlFactory =
        (new ContainerControl(_, _, _, _, _) with SingleNodeTrait with LHHATrait with ChildrenBuilderTrait
            { override val externalEvents = super.externalEvents + XXFORMS_DIALOG_CLOSE }) // allow xxforms-dialog-close on dialog

    private val SwitchControl: ControlFactory = (new ContainerControl(_, _, _, _, _) with SingleNodeTrait with LHHATrait with ChildrenBuilderTrait)
    private val CaseControl: ControlFactory   = (new ContainerControl(_, _, _, _, _) with SingleNodeTrait with LHHATrait with ChildrenBuilderTrait)

    // Variable factories indexed by QName
    // NOTE: We have all these QNames for historical reasons (XForms 2 is picking <xforms:var>)
    private val variableFactory =
        Seq(XXFORMS_VARIABLE_QNAME, XXFORMS_VAR_QNAME, XFORMS_VARIABLE_QNAME, XFORMS_VAR_QNAME, EXFORMS_VARIABLE_QNAME) map
            (qName ⇒ qName → VariableControl) toMap

    // Other factories indexed by QName
    private val byQNameFactory = Map[QName, ControlFactory](
        XBL_TEMPLATE_QNAME            → (new ContainerControl(_, _, _, _, _) with ChildrenBuilderTrait),
        // Core value controls
        XFORMS_INPUT_QNAME            → ValueControl,
        XFORMS_SECRET_QNAME           → ValueControl,
        XFORMS_TEXTAREA_QNAME         → ValueControl,
        XFORMS_UPLOAD_QNAME           → UploadControl,
        XFORMS_RANGE_QNAME            → ValueControl,
        XXFORMS_TEXT_QNAME            → ValueControl,
        XFORMS_OUTPUT_QNAME           → (new OutputControl(_, _, _, _, _)),
        // Core controls
        XFORMS_TRIGGER_QNAME          → TriggerControl,
        XFORMS_SUBMIT_QNAME           → TriggerControl,
        // Selection controls
        XFORMS_SELECT_QNAME           → SelectionControl,
        XFORMS_SELECT1_QNAME          → SelectionControl,
        // Attributes
        XXFORMS_ATTRIBUTE_QNAME       → (new AttributeControl(_, _, _, _, _)),
        // Container controls
        XFORMS_GROUP_QNAME            → GroupControl,
        XFORMS_SWITCH_QNAME           → SwitchControl,
        XFORMS_CASE_QNAME             → CaseControl,
        XXFORMS_DIALOG_QNAME          → DialogControl,
        // Dynamic control
        XXFORMS_DYNAMIC_QNAME         → (new ContainerControl(_, _, _, _, _) with SingleNodeTrait),
        // Repeat control
        XFORMS_REPEAT_QNAME           → (new RepeatControl(_, _, _, _, _)),
        XFORMS_REPEAT_ITERATION_QNAME → (new RepeatIterationControl(_, _, _, _, _)),
        // LHHA
        LABEL_QNAME                   → LHHAControl,
        HELP_QNAME                    → LHHAControl,
        HINT_QNAME                    → LHHAControl,
        ALERT_QNAME                   → LHHAControl,
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