/**
 * Copyright (C) 2009 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.control;

import org.dom4j.Element;
import org.dom4j.QName;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.control.controls.*;
import org.orbeon.oxf.xforms.xbl.XBLContainer;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * Factory for all existing XForms controls.
 */
public class XFormsControlFactory {

    private static Map<QName, Factory> nameToClassMap = new HashMap<QName, Factory>();

    // TODO: fix terminology which is not consistent with class hierarchy
    private static final Map<String, String> CONTAINER_CONTROLS = new HashMap<String, String>();
    private static final Map<String, String> CORE_VALUE_CONTROLS = new HashMap<String, String>();
    private static final Map<String, String> CORE_CONTROLS = new HashMap<String, String>();
    private static final Map<String, String> BUILTIN_CONTROLS = new HashMap<String, String>();

    public static final Map<String, String> MANDATORY_SINGLE_NODE_CONTROLS = new HashMap<String, String>();
    public static final Map<String, String> OPTIONAL_SINGLE_NODE_CONTROLS = new HashMap<String, String>();
    public static final Map<String, String> NO_SINGLE_NODE_CONTROLS = new HashMap<String, String>();
    public static final Map<String, String> MANDATORY_NODESET_CONTROLS = new HashMap<String, String>();
    public static final Map<String, String> NO_NODESET_CONTROLS = new HashMap<String, String>();
    public static final Map<String, String> SINGLE_NODE_OR_VALUE_CONTROLS = new HashMap<String, String>();

    static {
        // TODO: standardize on QName?

        // Standard controls
        CONTAINER_CONTROLS.put(XFormsConstants.GROUP_QNAME.getName(), XFormsConstants.XFORMS_NAMESPACE_URI);
        CONTAINER_CONTROLS.put(XFormsConstants.REPEAT_QNAME.getName(), XFormsConstants.XFORMS_NAMESPACE_URI);
        CONTAINER_CONTROLS.put(XFormsConstants.SWITCH_QNAME.getName(), XFormsConstants.XFORMS_NAMESPACE_URI);
        CONTAINER_CONTROLS.put(XFormsConstants.CASE_QNAME.getName(), XFormsConstants.XFORMS_NAMESPACE_URI);

        CORE_VALUE_CONTROLS.put(XFormsConstants.INPUT_QNAME.getName(), XFormsConstants.XFORMS_NAMESPACE_URI);
        CORE_VALUE_CONTROLS.put(XFormsConstants.SECRET_QNAME.getName(), XFormsConstants.XFORMS_NAMESPACE_URI);
        CORE_VALUE_CONTROLS.put(XFormsConstants.TEXTAREA_QNAME.getName(), XFormsConstants.XFORMS_NAMESPACE_URI);
        CORE_VALUE_CONTROLS.put(XFormsConstants.OUTPUT_QNAME.getName(), XFormsConstants.XFORMS_NAMESPACE_URI);
        CORE_VALUE_CONTROLS.put(XFormsConstants.UPLOAD_QNAME.getName(), XFormsConstants.XFORMS_NAMESPACE_URI);
        CORE_VALUE_CONTROLS.put(XFormsConstants.RANGE_QNAME.getName(), XFormsConstants.XFORMS_NAMESPACE_URI);
        CORE_VALUE_CONTROLS.put(XFormsConstants.SELECT_QNAME.getName(), XFormsConstants.XFORMS_NAMESPACE_URI);
        CORE_VALUE_CONTROLS.put(XFormsConstants.SELECT1_QNAME.getName(), XFormsConstants.XFORMS_NAMESPACE_URI);

        final Map<String, String> coreNoValueControls = new HashMap<String, String>();
        coreNoValueControls.put(XFormsConstants.SUBMIT_QNAME.getName(), XFormsConstants.XFORMS_NAMESPACE_URI);
        coreNoValueControls.put(XFormsConstants.TRIGGER_QNAME.getName(), XFormsConstants.XFORMS_NAMESPACE_URI);

        // Extension controls
        CONTAINER_CONTROLS.put(XFormsConstants.XXFORMS_DIALOG_QNAME.getName(), XFormsConstants.XXFORMS_NAMESPACE_URI);
        CORE_VALUE_CONTROLS.put(XFormsConstants.XXFORMS_ATTRIBUTE_QNAME.getName(), XFormsConstants.XXFORMS_NAMESPACE_URI);
        CORE_VALUE_CONTROLS.put(XFormsConstants.XXFORMS_TEXT_QNAME.getName(), XFormsConstants.XXFORMS_NAMESPACE_URI);

        CORE_CONTROLS.putAll(CORE_VALUE_CONTROLS);
        CORE_CONTROLS.putAll(coreNoValueControls);

        BUILTIN_CONTROLS.putAll(CONTAINER_CONTROLS);
        BUILTIN_CONTROLS.putAll(CORE_CONTROLS);

        MANDATORY_SINGLE_NODE_CONTROLS.putAll(CORE_VALUE_CONTROLS);
        MANDATORY_SINGLE_NODE_CONTROLS.remove("output");
        MANDATORY_SINGLE_NODE_CONTROLS.put("filename", "");
        MANDATORY_SINGLE_NODE_CONTROLS.put("mediatype", "");
        MANDATORY_SINGLE_NODE_CONTROLS.put("setvalue", "");

        SINGLE_NODE_OR_VALUE_CONTROLS.put("output", "");

        // TODO: some of those are not controls at all, must review this
        OPTIONAL_SINGLE_NODE_CONTROLS.putAll(coreNoValueControls);
        OPTIONAL_SINGLE_NODE_CONTROLS.put("output", "");  // can have @value attribute
        OPTIONAL_SINGLE_NODE_CONTROLS.put("value", "");   // can have inline text
        OPTIONAL_SINGLE_NODE_CONTROLS.put("label", "");   // can have linking or inline text
        OPTIONAL_SINGLE_NODE_CONTROLS.put("help", "");    // can have linking or inline text
        OPTIONAL_SINGLE_NODE_CONTROLS.put("hint", "");    // can have linking or inline text
        OPTIONAL_SINGLE_NODE_CONTROLS.put("alert", "");   // can have linking or inline text
        OPTIONAL_SINGLE_NODE_CONTROLS.put("copy", "");
        OPTIONAL_SINGLE_NODE_CONTROLS.put("load", "");    // can have linking
        OPTIONAL_SINGLE_NODE_CONTROLS.put("message", ""); // can have linking or inline text
        OPTIONAL_SINGLE_NODE_CONTROLS.put("group", "");
        OPTIONAL_SINGLE_NODE_CONTROLS.put("switch", "");

        NO_SINGLE_NODE_CONTROLS.put("choices", "");
        NO_SINGLE_NODE_CONTROLS.put("item", "");
        NO_SINGLE_NODE_CONTROLS.put("case", "");
        NO_SINGLE_NODE_CONTROLS.put("toggle", "");

        MANDATORY_NODESET_CONTROLS.put("repeat", "");
        MANDATORY_NODESET_CONTROLS.put("itemset", "");
        MANDATORY_NODESET_CONTROLS.put("delete", "");

        NO_NODESET_CONTROLS.putAll(MANDATORY_SINGLE_NODE_CONTROLS);
        NO_NODESET_CONTROLS.putAll(OPTIONAL_SINGLE_NODE_CONTROLS);
        NO_NODESET_CONTROLS.putAll(NO_SINGLE_NODE_CONTROLS);
    }

    static {
        // Built-in standard controls
        nameToClassMap.put(XFormsConstants.CASE_QNAME, new Factory() {
            public XFormsControl createXFormsControl(XBLContainer container, XFormsControl parent, Element element, String name, String effectiveId) {
                return new XFormsCaseControl(container, parent, element, name, effectiveId);
            }
        });
        nameToClassMap.put(XFormsConstants.GROUP_QNAME, new Factory() {
            public XFormsControl createXFormsControl(XBLContainer container, XFormsControl parent, Element element, String name, String effectiveId) {
                return new XFormsGroupControl(container, parent, element, name, effectiveId);
            }
        });
        nameToClassMap.put(XFormsConstants.INPUT_QNAME, new Factory() {
            public XFormsControl createXFormsControl(XBLContainer container, XFormsControl parent, Element element, String name, String effectiveId) {
                return new XFormsInputControl(container, parent, element, name, effectiveId);
            }
        });
        nameToClassMap.put(XFormsConstants.OUTPUT_QNAME, new Factory() {
            public XFormsControl createXFormsControl(XBLContainer container, XFormsControl parent, Element element, String name, String effectiveId) {
                return new XFormsOutputControl(container, parent, element, name, effectiveId);
            }
        });
        nameToClassMap.put(XFormsConstants.RANGE_QNAME, new Factory() {
            public XFormsControl createXFormsControl(XBLContainer container, XFormsControl parent, Element element, String name, String effectiveId) {
                return new XFormsRangeControl(container, parent, element, name, effectiveId);
            }
        });
        nameToClassMap.put(XFormsConstants.REPEAT_QNAME, new Factory() {
            public XFormsControl createXFormsControl(XBLContainer container, XFormsControl parent, Element element, String name, String effectiveId) {
                return new XFormsRepeatControl(container, parent, element, name, effectiveId);
            }
        });
        nameToClassMap.put(XFormsConstants.SECRET_QNAME, new Factory() {
            public XFormsControl createXFormsControl(XBLContainer container, XFormsControl parent, Element element, String name, String effectiveId) {
                return new XFormsSecretControl(container, parent, element, name, effectiveId);
            }
        });
        nameToClassMap.put(XFormsConstants.SELECT1_QNAME, new Factory() {
            public XFormsControl createXFormsControl(XBLContainer container, XFormsControl parent, Element element, String name, String effectiveId) {
                return new XFormsSelect1Control(container, parent, element, name, effectiveId);
            }
        });
        nameToClassMap.put(XFormsConstants.SELECT_QNAME, new Factory() {
            public XFormsControl createXFormsControl(XBLContainer container, XFormsControl parent, Element element, String name, String effectiveId) {
                return new XFormsSelectControl(container, parent, element, name, effectiveId);
            }
        });
        nameToClassMap.put(XFormsConstants.SUBMIT_QNAME, new Factory() {
            public XFormsControl createXFormsControl(XBLContainer container, XFormsControl parent, Element element, String name, String effectiveId) {
                return new XFormsSubmitControl(container, parent, element, name, effectiveId);
            }
        });
        nameToClassMap.put(XFormsConstants.SWITCH_QNAME, new Factory() {
            public XFormsControl createXFormsControl(XBLContainer container, XFormsControl parent, Element element, String name, String effectiveId) {
                return new XFormsSwitchControl(container, parent, element, name, effectiveId);
            }
        });
        nameToClassMap.put(XFormsConstants.TEXTAREA_QNAME, new Factory() {
            public XFormsControl createXFormsControl(XBLContainer container, XFormsControl parent, Element element, String name, String effectiveId) {
                return new XFormsTextareaControl(container, parent, element, name, effectiveId);
            }
        });
        nameToClassMap.put(XFormsConstants.TRIGGER_QNAME, new Factory() {
            public XFormsControl createXFormsControl(XBLContainer container, XFormsControl parent, Element element, String name, String effectiveId) {
                return new XFormsTriggerControl(container, parent, element, name, effectiveId);
            }
        });
        nameToClassMap.put(XFormsConstants.UPLOAD_QNAME, new Factory() {
            public XFormsControl createXFormsControl(XBLContainer container, XFormsControl parent, Element element, String name, String effectiveId) {
                return new XFormsUploadControl(container, parent, element, name, effectiveId);
            }
        });
        // Built-in extension controls
        nameToClassMap.put(XFormsConstants.XXFORMS_DIALOG_QNAME, new Factory() {
            public XFormsControl createXFormsControl(XBLContainer container, XFormsControl parent, Element element, String name, String effectiveId) {
                return new XXFormsDialogControl(container, parent, element, name, effectiveId);
            }
        });
        nameToClassMap.put(XFormsConstants.XXFORMS_ATTRIBUTE_QNAME, new Factory() {
            public XFormsControl createXFormsControl(XBLContainer container, XFormsControl parent, Element element, String name, String effectiveId) {
                return new XXFormsAttributeControl(container, parent, element, name, effectiveId);
            }
        });
        nameToClassMap.put(XFormsConstants.XXFORMS_TEXT_QNAME, new Factory() {
            public XFormsControl createXFormsControl(XBLContainer container, XFormsControl parent, Element element, String name, String effectiveId) {
                return new XXFormsTextControl(container, parent, element, name, effectiveId);
            }
        });
    }

    /**
     * Create a new XForms control. The control returned may be a built-in standard control, a built-in extension
     * control, or a custom component.
     *
     * @param container             container
     * @param parent                parent control, null if none
     * @param element               element associated with the control
     * @param effectiveId           effective id of the control
     * @return                      control
     */
    public static XFormsControl createXFormsControl(XBLContainer container, XFormsControl parent, Element element, String effectiveId) {

        final String controlName = element.getName();

        // First try built-in controls
        Factory factory = nameToClassMap.get(element.getQName());

        // Then try custom components
        if (factory == null)
            factory = container.getContainingDocument().getStaticState().getXBLBindings().getComponentFactory(element.getQName());

        if (factory == null)
            throw new OXFException("Invalid control name: " + Dom4jUtils.qNameToExplodedQName(element.getQName()));

        // Create and return the control
        return factory.createXFormsControl(container, parent, element, controlName, effectiveId);
    }

    public static boolean isValueControl(String controlURI, String controlName) {
        final String uri = CORE_VALUE_CONTROLS.get(controlName);
        return controlURI.equals(uri);
    }

    public static boolean isContainerControl(String controlURI, String controlName) {
        final String uri = CONTAINER_CONTROLS.get(controlName);
        return controlURI.equals(uri);
    }

    public static boolean isCoreControl(String controlURI, String controlName) {
        final String uri = CORE_CONTROLS.get(controlName);
        return controlURI.equals(uri);
    }

    public static boolean isBuiltinControl(String controlURI, String controlName) {
        final String uri = BUILTIN_CONTROLS.get(controlName);
        return controlURI.equals(uri);
    }

    public static abstract class Factory {
        public abstract XFormsControl createXFormsControl(XBLContainer container, XFormsControl parent, Element element, String name, String effectiveId);
    }

    private XFormsControlFactory() {}
}
