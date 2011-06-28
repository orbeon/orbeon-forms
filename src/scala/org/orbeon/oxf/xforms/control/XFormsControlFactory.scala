/**
 * Copyright (C) 2011 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.control

import controls._
import org.dom4j._
import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.xforms.XFormsConstants._
import org.orbeon.oxf.xforms.xbl.XBLContainer
import org.orbeon.oxf.xml.dom4j.Dom4jUtils
import java.util.{Map => JMap}

/**
 * Factory for all existing XForms controls including built-in controls and XBL controls.
 */
object XFormsControlFactory {

    // TODO: fix terminology which is not consistent with class hierarchy
    private val containerControls = Set(
        // Standard controls
        XFORMS_GROUP_QNAME,
        XFORMS_REPEAT_QNAME,
        XFORMS_SWITCH_QNAME,
        XFORMS_CASE_QNAME,
        // Extension controls
        XXFORMS_DIALOG_QNAME,
        XXFORMS_DYNAMIC_QNAME
    )
    
    private val coreValueControls = Set(
        // Standard controls
        XFORMS_INPUT_QNAME,
        XFORMS_SECRET_QNAME,
        XFORMS_TEXTAREA_QNAME,
        XFORMS_OUTPUT_QNAME,
        XFORMS_UPLOAD_QNAME,
        XFORMS_RANGE_QNAME,
        XFORMS_SELECT_QNAME,
        XFORMS_SELECT1_QNAME,
        // Extension controls
        XXFORMS_ATTRIBUTE_QNAME,
        XXFORMS_TEXT_QNAME
    )
    
    private val coreControls = coreValueControls + XFORMS_SUBMIT_QNAME + XFORMS_TRIGGER_QNAME

    private val builtinControls = containerControls ++
        coreControls + XXFORMS_VARIABLE_QNAME + XXFORMS_VAR_QNAME +
        XFORMS_VARIABLE_QNAME + XFORMS_VAR_QNAME + EXFORMS_VARIABLE_QNAME

    private val factories = Map[QName, (XBLContainer, XFormsControl, Element, String, String, JMap[String, Element]) => XFormsControl](
        // Built-in standard controls
        XFORMS_CASE_QNAME -> (new XFormsCaseControl(_, _, _, _, _, _)),
        XFORMS_GROUP_QNAME -> (new XFormsGroupControl(_, _, _, _, _, _)),
        XFORMS_INPUT_QNAME -> (new XFormsInputControl(_, _, _, _, _, _)),
        XFORMS_OUTPUT_QNAME -> (new XFormsOutputControl(_, _, _, _, _, _)),
        XFORMS_RANGE_QNAME -> (new XFormsRangeControl(_, _, _, _, _, _)),
        XFORMS_REPEAT_QNAME -> (new XFormsRepeatControl(_, _, _, _, _, _)),
        XFORMS_SECRET_QNAME -> (new XFormsSecretControl(_, _, _, _, _, _)),
        XFORMS_SELECT1_QNAME -> (new XFormsSelect1Control(_, _, _, _, _, _)),
        XFORMS_SELECT_QNAME -> (new XFormsSelectControl(_, _, _, _, _, _)),
        XFORMS_SUBMIT_QNAME -> (new XFormsSubmitControl(_, _, _, _, _, _)),
        XFORMS_SWITCH_QNAME -> (new XFormsSwitchControl(_, _, _, _, _, _)),
        XFORMS_TEXTAREA_QNAME -> (new XFormsTextareaControl(_, _, _, _, _, _)),
        XFORMS_TRIGGER_QNAME -> (new XFormsTriggerControl(_, _, _, _, _, _)),
        XFORMS_UPLOAD_QNAME -> (new XFormsUploadControl(_, _, _, _, _, _)),
        // Built-in extension controls
        XXFORMS_DIALOG_QNAME -> (new XXFormsDialogControl(_, _, _, _, _, _)),
        XXFORMS_ATTRIBUTE_QNAME -> (new XXFormsAttributeControl(_, _, _, _, _, _)),
        XXFORMS_TEXT_QNAME -> (new XXFormsTextControl(_, _, _, _, _, _)),
        XXFORMS_DYNAMIC_QNAME -> (new XXFormsDynamicControl(_, _, _, _, _, _))
    ) ++
        // Multiple QNames are allowed for variables
        (Set(XXFORMS_VARIABLE_QNAME, XXFORMS_VAR_QNAME, XFORMS_VARIABLE_QNAME, XFORMS_VAR_QNAME, EXFORMS_VARIABLE_QNAME) map
            (_ -> (new XXFormsVariableControl(_, _, _, _, _, _))))

    /**
     * Create a new XForms control. The control returned may be a built-in standard control, a built-in extension
     * control, or a custom component.
     *
     * @param container             container
     * @param parent                parent control, null if none
     * @param element               element associated with the control
     * @param effectiveId           effective id of the control
     * @param state                 initial state if needed, or null
     * @return                      control
     */
    def createXFormsControl(container: XBLContainer, parent: XFormsControl, element: Element, effectiveId: String, state: JMap[String, Element]) = {

        // First try built-in controls, then XBL controls
        // NOTE: Do global search for factory as a part might use an ancestor's XBL
        val factory = factories.get(element.getQName) orElse
            (Option(container.getContainingDocument.getStaticOps.getComponentFactory(element.getQName)) map
                (f => f.createXFormsControl _))

        if (!factory.isDefined)
            throw new OXFException("Invalid control name: " + Dom4jUtils.qNameToExplodedQName(element.getQName))

        // Create and return the control
        factory.get.apply(container, parent, element, element.getName, effectiveId, state)
    }

    def isValueControl(controlURI: String, controlName: String) = coreValueControls(getQName(controlURI, controlName))
    def isContainerControl(controlURI: String, controlName: String) = containerControls(getQName(controlURI, controlName))
    def isCoreControl(controlURI: String, controlName: String) = coreControls.contains(getQName(controlURI, controlName))
    def isBuiltinControl(controlURI: String, controlName: String) = builtinControls.contains(getQName(controlURI, controlName))

    // TODO: move this, it doesn't really belong here
    def isLHHA(controlURI: String, controlName: String) = LABEL_HINT_HELP_ALERT_ELEMENT.contains(controlName) && XFORMS_NAMESPACE_URI == controlURI

    private def getQName(controlURI: String, controlName: String) = QName.get(controlName, Namespace.get("", controlURI))

    // For use by Java code
    abstract class Factory {
        def createXFormsControl(container: XBLContainer, parent: XFormsControl, element: Element, name: String, effectiveId: String, state: JMap[String, Element]): XFormsControl
    }
}
