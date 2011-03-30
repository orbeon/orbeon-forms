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
import org.dom4j.Element
import org.orbeon.oxf.xforms.xbl.XBLBindings

import org.orbeon.oxf.xforms.XFormsConstants._
import org.orbeon.oxf.xforms.XFormsUtils
import org.orbeon.oxf.common.OXFException

object ControlAnalysisFactory {

    def create(staticStateContext: StaticStateContext, parent: ContainerTrait, preceding: ElementAnalysis,
               scope: XBLBindings#Scope, controlElement: Element): ElementAnalysis = {

        // Tell whether the current element has an XBL binding
        def hasXBLBinding = staticStateContext.staticState.getXBLBindings.hasBinding(scope.getPrefixedIdForStaticId(XFormsUtils.getElementStaticId(controlElement)))

        // NOTE: Not all controls need separate classes, so for now we use generic ones like e.g. ValueCoreControl instead of InputControl
        controlElement.getQName match {
            case XFORMS_INPUT_QNAME | XFORMS_SECRET_QNAME | XFORMS_TEXTAREA_QNAME | XFORMS_OUTPUT_QNAME | XFORMS_UPLOAD_QNAME | XFORMS_RANGE_QNAME | XXFORMS_TEXT_QNAME =>
                new CoreControl(staticStateContext, controlElement, parent, Option(preceding), scope) with ValueTrait
            case XFORMS_TRIGGER_QNAME | XFORMS_SUBMIT_QNAME =>
                new CoreControl(staticStateContext, controlElement, parent, Option(preceding), scope)
            case XFORMS_SELECT_QNAME | XFORMS_SELECT1_QNAME =>
                new CoreControl(staticStateContext, controlElement, parent, Option(preceding), scope) with ValueTrait with SelectionControl
            case XXFORMS_VARIABLE_QNAME | XXFORMS_VAR_QNAME | XFORMS_VARIABLE_QNAME | XFORMS_VAR_QNAME | EXFORMS_VARIABLE_QNAME =>
                new VariableControl(staticStateContext, controlElement, parent, Option(preceding), scope)
            case XXFORMS_ATTRIBUTE_QNAME =>
                new AttributeControl(staticStateContext, controlElement, parent, Option(preceding), scope)
            case XFORMS_GROUP_QNAME | XFORMS_SWITCH_QNAME | XFORMS_CASE_QNAME | XXFORMS_DIALOG_QNAME => // no LHHA in spec yet, but will make sense
                new ContainerControl(staticStateContext, controlElement, parent, Option(preceding), scope) with ContainerLHHATrait
            case XFORMS_REPEAT_QNAME =>
                new RepeatControl(staticStateContext, controlElement, parent, Option(preceding), scope)
            case qName if hasXBLBinding =>
                new ComponentControl(staticStateContext, controlElement, parent, Option(preceding), scope)
            case LABEL_QNAME | HELP_QNAME | HINT_QNAME | ALERT_QNAME =>
                new ExternalLHHAAnalysis(staticStateContext, controlElement, parent, Option(preceding), scope)
            case qName =>
                throw new OXFException("Invalid control name: " + qName.getQualifiedName)
        }
    }

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