/**
 * Copyright (C) 2010 Orbeon, Inc.
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
import org.dom4j.Namespace;
import org.dom4j.QName;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.action.XFormsActions;
import org.orbeon.oxf.xforms.control.controls.*;
import org.orbeon.oxf.xforms.xbl.XBLContainer;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Factory for all existing XForms controls.
 */
public class XFormsControlFactory {

    private static Map<QName, Factory> nameToClassMap = new HashMap<QName, Factory>();

    // TODO: fix terminology which is not consistent with class hierarchy
    private static final Set<QName> CONTAINER_CONTROLS = new HashSet<QName>();
    private static final Set<QName> CORE_VALUE_CONTROLS = new HashSet<QName>();
    private static final Set<QName> CORE_CONTROLS = new HashSet<QName>();
    private static final Set<QName> BUILTIN_CONTROLS = new HashSet<QName>();

    public static final Set<QName> MANDATORY_SINGLE_NODE_CONTROLS = new HashSet<QName>();
    public static final Set<QName> OPTIONAL_SINGLE_NODE_CONTROLS = new HashSet<QName>();
    public static final Set<QName> NO_SINGLE_NODE_CONTROLS = new HashSet<QName>();
    public static final Set<QName> MANDATORY_NODESET_CONTROLS = new HashSet<QName>();
    public static final Set<QName> NO_NODESET_CONTROLS = new HashSet<QName>();
    public static final Set<QName> SINGLE_NODE_OR_VALUE_CONTROLS = new HashSet<QName>();

    static {
        // TODO: standardize on QName?

        // Standard controls
        CONTAINER_CONTROLS.add(XFormsConstants.GROUP_QNAME);
        CONTAINER_CONTROLS.add(XFormsConstants.REPEAT_QNAME);
        CONTAINER_CONTROLS.add(XFormsConstants.SWITCH_QNAME);
        CONTAINER_CONTROLS.add(XFormsConstants.CASE_QNAME);

        CORE_VALUE_CONTROLS.add(XFormsConstants.INPUT_QNAME);
        CORE_VALUE_CONTROLS.add(XFormsConstants.SECRET_QNAME);
        CORE_VALUE_CONTROLS.add(XFormsConstants.TEXTAREA_QNAME);
        CORE_VALUE_CONTROLS.add(XFormsConstants.OUTPUT_QNAME);
        CORE_VALUE_CONTROLS.add(XFormsConstants.UPLOAD_QNAME);
        CORE_VALUE_CONTROLS.add(XFormsConstants.RANGE_QNAME);
        CORE_VALUE_CONTROLS.add(XFormsConstants.SELECT_QNAME);
        CORE_VALUE_CONTROLS.add(XFormsConstants.SELECT1_QNAME);

        final Set<QName> coreNoValueControls = new HashSet<QName>();
        coreNoValueControls.add(XFormsConstants.SUBMIT_QNAME);
        coreNoValueControls.add(XFormsConstants.TRIGGER_QNAME);

        // Extension controls
        CONTAINER_CONTROLS.add(XFormsConstants.XXFORMS_DIALOG_QNAME);
        CORE_VALUE_CONTROLS.add(XFormsConstants.XXFORMS_ATTRIBUTE_QNAME);
        CORE_VALUE_CONTROLS.add(XFormsConstants.XXFORMS_TEXT_QNAME);

        CORE_CONTROLS.addAll(CORE_VALUE_CONTROLS);
        CORE_CONTROLS.addAll(coreNoValueControls);

        BUILTIN_CONTROLS.addAll(CONTAINER_CONTROLS);
        BUILTIN_CONTROLS.addAll(CORE_CONTROLS);

        BUILTIN_CONTROLS.add(XFormsConstants.XXFORMS_VARIABLE_QNAME);
        BUILTIN_CONTROLS.add(XFormsConstants.EXFORMS_VARIABLE_QNAME);

        MANDATORY_SINGLE_NODE_CONTROLS.addAll(CORE_VALUE_CONTROLS);
        MANDATORY_SINGLE_NODE_CONTROLS.remove(XFormsConstants.UPLOAD_QNAME);
        MANDATORY_SINGLE_NODE_CONTROLS.add(XFormsConstants.FILENAME_QNAME);
        MANDATORY_SINGLE_NODE_CONTROLS.add(XFormsConstants.MEDIATYPE_QNAME);
        MANDATORY_SINGLE_NODE_CONTROLS.add(XFormsActions.XFORMS_SETVALUE_ACTION_QNAME);

        SINGLE_NODE_OR_VALUE_CONTROLS.add(XFormsConstants.OUTPUT_QNAME);

        // TODO: some of those are not controls at all, must review this
        OPTIONAL_SINGLE_NODE_CONTROLS.addAll(coreNoValueControls);
        OPTIONAL_SINGLE_NODE_CONTROLS.add(XFormsConstants.UPLOAD_QNAME);  // can have @value attribute
        OPTIONAL_SINGLE_NODE_CONTROLS.add(XFormsConstants.VALUE_QNAME);   // can have inline text
        OPTIONAL_SINGLE_NODE_CONTROLS.add(XFormsConstants.LABEL_QNAME);   // can have linking or inline text
        OPTIONAL_SINGLE_NODE_CONTROLS.add(XFormsConstants.HELP_QNAME);    // can have linking or inline text
        OPTIONAL_SINGLE_NODE_CONTROLS.add(XFormsConstants.HINT_QNAME);    // can have linking or inline text
        OPTIONAL_SINGLE_NODE_CONTROLS.add(XFormsConstants.ALERT_QNAME);   // can have linking or inline text
        OPTIONAL_SINGLE_NODE_CONTROLS.add(XFormsConstants.COPY_QNAME);
        OPTIONAL_SINGLE_NODE_CONTROLS.add(XFormsConstants.LOAD_QNAME);    // can have linking
        OPTIONAL_SINGLE_NODE_CONTROLS.add(XFormsActions.XFORMS_MESSAGE_ACTION_QNAME); // can have linking or inline text
        OPTIONAL_SINGLE_NODE_CONTROLS.add(XFormsConstants.GROUP_QNAME);
        OPTIONAL_SINGLE_NODE_CONTROLS.add(XFormsConstants.SWITCH_QNAME);

        NO_SINGLE_NODE_CONTROLS.add(XFormsConstants.CHOICES_QNAME);
        NO_SINGLE_NODE_CONTROLS.add(XFormsConstants.ITEM_QNAME);
        NO_SINGLE_NODE_CONTROLS.add(XFormsConstants.CASE_QNAME);
        NO_SINGLE_NODE_CONTROLS.add(XFormsActions.XFORMS_TOGGLE_ACTION_QNAME);

        MANDATORY_NODESET_CONTROLS.add(XFormsConstants.REPEAT_QNAME);
        MANDATORY_NODESET_CONTROLS.add(XFormsConstants.ITEMSET_QNAME);
        MANDATORY_NODESET_CONTROLS.add(XFormsActions.XFORMS_DELETE_ACTION_QNAME);

        NO_NODESET_CONTROLS.addAll(MANDATORY_SINGLE_NODE_CONTROLS);
        NO_NODESET_CONTROLS.addAll(OPTIONAL_SINGLE_NODE_CONTROLS);
        NO_NODESET_CONTROLS.addAll(NO_SINGLE_NODE_CONTROLS);
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
        nameToClassMap.put(XFormsConstants.XXFORMS_VARIABLE_QNAME, new Factory() {
            public XFormsControl createXFormsControl(XBLContainer container, XFormsControl parent, Element element, String name, String effectiveId) {
                return new XXFormsVariableControl(container, parent, element, name, effectiveId);
            }
        });
        nameToClassMap.put(XFormsConstants.EXFORMS_VARIABLE_QNAME, new Factory() {
            public XFormsControl createXFormsControl(XBLContainer container, XFormsControl parent, Element element, String name, String effectiveId) {
                return new XXFormsVariableControl(container, parent, element, name, effectiveId);
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
        return CORE_VALUE_CONTROLS.contains(getQName(controlURI, controlName));
    }

    public static boolean isContainerControl(String controlURI, String controlName) {
        return CONTAINER_CONTROLS.contains(getQName(controlURI, controlName));
    }

    public static boolean isCoreControl(String controlURI, String controlName) {
        return CORE_CONTROLS.contains(getQName(controlURI, controlName));
    }

    public static boolean isBuiltinControl(String controlURI, String controlName) {
        return BUILTIN_CONTROLS.contains(getQName(controlURI, controlName));
    }

    public static boolean isLHHA(String controlURI, String controlName) {
        return XFormsConstants.LABEL_HINT_HELP_ALERT_ELEMENT.contains(controlName) && XFormsConstants.XFORMS_NAMESPACE_URI.equals(controlURI);
    }

    private static QName getQName(String controlURI, String controlName) {
        return QName.get(controlName, Namespace.get("", controlURI));
    }

    public static abstract class Factory {
        public abstract XFormsControl createXFormsControl(XBLContainer container, XFormsControl parent, Element element, String name, String effectiveId);
    }

    private XFormsControlFactory() {}
}
