/**
 *  Copyright (C) 2006 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.control;

import org.orbeon.oxf.xforms.XFormsContainingDocument;
import org.orbeon.oxf.xforms.control.controls.*;
import org.orbeon.oxf.common.OXFException;
import org.dom4j.Element;

import java.util.Map;
import java.util.HashMap;

/**
 * Factory for all existing XForms controls.
 */
public class XFormsControlFactory {

    private static Map nameToClassMap = new HashMap();

    static {
        nameToClassMap.put("case", new Factory() {
            public XFormsControl createXFormsControl(XFormsContainingDocument containingDocument, XFormsControl parent, Element element, String name, String effectiveId) {
                return new XFormsCaseControl(containingDocument, parent, element, name, effectiveId);
            }
        });
        nameToClassMap.put("group", new Factory() {
            public XFormsControl createXFormsControl(XFormsContainingDocument containingDocument, XFormsControl parent, Element element, String name, String effectiveId) {
                return new XFormsGroupControl(containingDocument, parent, element, name, effectiveId);
            }
        });
        nameToClassMap.put("input", new Factory() {
            public XFormsControl createXFormsControl(XFormsContainingDocument containingDocument, XFormsControl parent, Element element, String name, String effectiveId) {
                return new XFormsInputControl(containingDocument, parent, element, name, effectiveId);
            }
        });
        nameToClassMap.put("output", new Factory() {
            public XFormsControl createXFormsControl(XFormsContainingDocument containingDocument, XFormsControl parent, Element element, String name, String effectiveId) {
                return new XFormsOutputControl(containingDocument, parent, element, name, effectiveId);
            }
        });
        nameToClassMap.put("range", new Factory() {
            public XFormsControl createXFormsControl(XFormsContainingDocument containingDocument, XFormsControl parent, Element element, String name, String effectiveId) {
                return new XFormsRangeControl(containingDocument, parent, element, name, effectiveId);
            }
        });
        nameToClassMap.put("repeat", new Factory() {
            public XFormsControl createXFormsControl(XFormsContainingDocument containingDocument, XFormsControl parent, Element element, String name, String effectiveId) {
                return new XFormsRepeatControl(containingDocument, parent, element, name, effectiveId);
            }
        });
        nameToClassMap.put("secret", new Factory() {
            public XFormsControl createXFormsControl(XFormsContainingDocument containingDocument, XFormsControl parent, Element element, String name, String effectiveId) {
                return new XFormsSecretControl(containingDocument, parent, element, name, effectiveId);
            }
        });
        nameToClassMap.put("select1", new Factory() {
            public XFormsControl createXFormsControl(XFormsContainingDocument containingDocument, XFormsControl parent, Element element, String name, String effectiveId) {
                return new XFormsSelect1Control(containingDocument, parent, element, name, effectiveId);
            }
        });
        nameToClassMap.put("select", new Factory() {
            public XFormsControl createXFormsControl(XFormsContainingDocument containingDocument, XFormsControl parent, Element element, String name, String effectiveId) {
                return new XFormsSelectControl(containingDocument, parent, element, name, effectiveId);
            }
        });
        nameToClassMap.put("submit", new Factory() {
            public XFormsControl createXFormsControl(XFormsContainingDocument containingDocument, XFormsControl parent, Element element, String name, String effectiveId) {
                return new XFormsSubmitControl(containingDocument, parent, element, name, effectiveId);
            }
        });
        nameToClassMap.put("switch", new Factory() {
            public XFormsControl createXFormsControl(XFormsContainingDocument containingDocument, XFormsControl parent, Element element, String name, String effectiveId) {
                return new XFormsSwitchControl(containingDocument, parent, element, name, effectiveId);
            }
        });
        nameToClassMap.put("textarea", new Factory() {
            public XFormsControl createXFormsControl(XFormsContainingDocument containingDocument, XFormsControl parent, Element element, String name, String effectiveId) {
                return new XFormsTextAreaControl(containingDocument, parent, element, name, effectiveId);
            }
        });
        nameToClassMap.put("trigger", new Factory() {
            public XFormsControl createXFormsControl(XFormsContainingDocument containingDocument, XFormsControl parent, Element element, String name, String effectiveId) {
                return new XFormsTriggerControl(containingDocument, parent, element, name, effectiveId);
            }
        });
        nameToClassMap.put("upload", new Factory() {
            public XFormsControl createXFormsControl(XFormsContainingDocument containingDocument, XFormsControl parent, Element element, String name, String effectiveId) {
                return new XFormsUploadControl(containingDocument, parent, element, name, effectiveId);
            }
        });
    }

    public static XFormsControl createXFormsControl(XFormsContainingDocument containingDocument, XFormsControl parent, Element element, String name, String effectiveId) {

        final Factory factory = (Factory) nameToClassMap.get(name);
        if (factory == null)
            throw new OXFException("Invalid control name: " + name);

        return factory.createXFormsControl(containingDocument, parent, element, name, effectiveId);
    }

    private static abstract class Factory {
        public abstract XFormsControl createXFormsControl(XFormsContainingDocument containingDocument, XFormsControl parent, Element element, String name, String effectiveId);
    }
}
