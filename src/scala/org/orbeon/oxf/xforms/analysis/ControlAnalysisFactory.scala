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
    private val valueControlFactory: ControlFactory =     (new CoreControl(_, _, _, _, _) with ValueTrait with ActionChildrenBuilder)
    private val triggerControlFactory: ControlFactory =   (new CoreControl(_, _, _, _, _) with TriggerAppearanceTrait with ActionChildrenBuilder)
    private val selectionControlFactory: ControlFactory = (new CoreControl(_, _, _, _, _) with ValueTrait with SelectionControl with ActionChildrenBuilder)
    private val variableControlFactory: ControlFactory =  (new VariableControl(_, _, _, _, _) with ActionChildrenBuilder)
    private val containerControlFactory: ControlFactory = (new ContainerControl(_, _, _, _, _) with ContainerLHHATrait with ContainerChildrenBuilder) // NOTE: no LHHA in spec yet, but will make sense
    private val lhhaControlFactory: ControlFactory =      (new ExternalLHHAAnalysis(_, _, _, _, _))

    // Variable factories indexed by QName
    // NOTE: We have all these QNames for historical reasons
    private val variableFactory =
        Seq(XXFORMS_VARIABLE_QNAME, XXFORMS_VAR_QNAME, XFORMS_VARIABLE_QNAME, XFORMS_VAR_QNAME, EXFORMS_VARIABLE_QNAME) map
            (qName ⇒ qName → variableControlFactory) toMap

    // Other factories indexed by QName
    private val byQNameFactory = Map[QName, ControlFactory](
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
        XFORMS_REPEAT_QNAME           → (new RepeatControl(_, _, _, _, _) with ContainerChildrenBuilder),
        XFORMS_REPEAT_ITERATION_QNAME → (new RepeatIterationControl(_, _, _, _, _) with ContainerChildrenBuilder),
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

        // Tell whether the current element has an XBL binding
        def hasXBLBinding(e: Element) = context.partAnalysis.xblBindings.hasBinding(scope.prefixedIdForStaticId(XFormsUtils.getElementStaticId(e)))

        // Not all factories are simply indexed by QName, so compose those with factories for components and actions
        val componentFactory: PartialFunction[Element, ControlFactory] =
            { case e if hasXBLBinding(e) ⇒ (new ComponentControl(_, _, _, _, _) with ShadowChildrenBuilder) }

        val f = controlFactory orElse componentFactory orElse XFormsActions.factory

        // Create the ElementAnalysis if possible
        f.lift(controlElement) map
            (_(context, controlElement, parent, preceding, scope))
    }

    def isVariable(qName: QName) = variableFactory.contains(qName)

    /*
        The hierarchy looks like this:

        // Leaf / container axis
        trait LeafTrait
        trait ContainerTrait

        // Other traits
        trait LHHATrait
        trait ContainerLHHATrait extends LHHATrait
        trait ValueTrait

        // The root does not have an element
        class RootControl extends ContainerTrait

        class CoreControl extends SimpleElementAnalysis with LeafTrait with LHHATrait
        class ValueCoreControl extends CoreControl with ValueTrait
        class ContainerControl extends SimpleElementAnalysis with ContainerTrait
        class LHHAContainerControl extends ContainerControl with ContainerLHHATrait

        class InputControl extends ValueCoreControl
        class SecretControl extends ValueCoreControl
        class TextareaControl extends ValueCoreControl
        class OutputControl extends ValueCoreControl
        class UploadControl extends ValueCoreControl
        class RangeControl extends ValueCoreControl
        class TriggerControl extends CoreControl
        class SubmitControl extends CoreControl

        trait SelectionControl
        class SelectControl extends ValueCoreControl with SelectionControl
        class Select1Control extends ValueCoreControl with SelectionControl

        class VariableControl extends ValueCoreControl
        class AttributeControl extends ValueCoreControl
        class TextControl extends ValueCoreControl

        class GroupControl extends LHHAContainerControl
        class SwitchControl extends LHHAContainerControl
        class CaseControl extends LHHAContainerControl
        class RepeatControl extends ContainerControl

        class ComponentControl extends ContainerControl
        class DialogControl extends LHHAContainerControl

    */
}