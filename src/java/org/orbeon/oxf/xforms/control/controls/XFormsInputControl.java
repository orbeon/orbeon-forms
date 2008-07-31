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
package org.orbeon.oxf.xforms.control.controls;

import org.dom4j.Element;
import org.dom4j.QName;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.XFormsContainingDocument;
import org.orbeon.oxf.xforms.XFormsItemUtils;
import org.orbeon.oxf.xforms.XFormsProperties;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.control.XFormsValueControl;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.oxf.util.XPathCache;
import org.orbeon.saxon.om.NodeInfo;

import java.util.Map;
import java.util.HashMap;

/**
 * Represents an xforms:input control.
 */
public class XFormsInputControl extends XFormsValueControl {

    // Optional display format
    private String format;

    public XFormsInputControl(XFormsContainingDocument containingDocument, XFormsControl parent, Element element, String name, String id) {
        super(containingDocument, parent, element, name, id);
        this.format = element.attributeValue(new QName("format", XFormsConstants.XXFORMS_NAMESPACE));
    }

    public String getFormat() {
        return format;
    }

//    public void evaluateDisplayValue(PipelineContext pipelineContext) {
//        getValueUseFormat(pipelineContext, format);
//    }

    protected void evaluateExternalValue(PipelineContext pipelineContext) {

        final String internalValue = getValue(pipelineContext);
        final String updatedValue;

        final String typeName = getBuiltinTypeName();
        if (typeName != null) {
            if (typeName.equals("boolean")) {
                // xs:boolean

                if (internalValue != null && !internalValue.equals("true")) {
                    // This so we don't send "false" to the client but ""
                    updatedValue = "";
                } else {
                    if (XFormsProperties.isEncryptItemValues(containingDocument)) {
                        // Encrypt outgoing value if needed
                        updatedValue = XFormsItemUtils.encryptValue(pipelineContext, internalValue);
                    } else {
                        // For open selection, values sent to client are the internal values
                        updatedValue = internalValue;
                    }
                }
            } else {
                // Other types
            updatedValue = internalValue;
            }
        } else {
            // No type
            updatedValue = internalValue;
        }

        setExternalValue(updatedValue);
    }

    public void storeExternalValue(PipelineContext pipelineContext, String value, String type) {
        super.storeExternalValue(pipelineContext, convertFromExternalValue(pipelineContext, value), type);
    }

    private String convertFromExternalValue(PipelineContext pipelineContext, String externalValue) {
        final String typeName = getBuiltinTypeName();
        if (typeName != null && typeName.equals("boolean")) {
            // xs:boolean input

            // Decrypt incoming value if needed. With open selection, values are sent to the client.
            if (XFormsProperties.isEncryptItemValues(containingDocument))
                externalValue = XFormsItemUtils.decryptValue(pipelineContext, externalValue);

            // Anything but "true" is "false"
            if (!externalValue.equals("true"))
                externalValue = "false";
        }

        return externalValue;
    }

    /**
     * Convenience method for handler: return the value of the first input field.
     *
     * @param pipelineContext   pipeline context
     * @return                  value to store in the first input field
     */
    public String getFirstValueUseFormat(PipelineContext pipelineContext) {
        final String result;

        final String typeName = getBuiltinTypeName();
        if ("date".equals(typeName) || "time".equals(typeName) || "dateTime".equals(typeName)) {
            // Format value specially

            // Assume xs: prefix for default formats
            final Map prefixToURIMap = new HashMap();
            prefixToURIMap.put(XMLConstants.XSD_PREFIX, XMLConstants.XSD_URI);

            final NodeInfo boundNode = getBoundNode();
            if (boundNode == null) {
                result = null;
            } else {
                result = XPathCache.evaluateAsString(pipelineContext, boundNode,
                        XFormsProperties.getTypeInputFormat(containingDocument, "time".equals(typeName) ? "time" : "date"),
                        prefixToURIMap, getContextStack().getCurrentVariables(),
                        XFormsContainingDocument.getFunctionLibrary(),
                        getContextStack().getFunctionContext(), null, getLocationData());
            }

        } else {
            // Regular case, use external value
            result = getExternalValue(pipelineContext);
        }

        return (result != null) ? result : "";
    }

    /**
     * Convenience method for handler: return the value of the second input field.
     *
     * @param pipelineContext   pipeline context
     * @return                  value to store in the second input field
     */
    public String getSecondValueUseFormat(PipelineContext pipelineContext) {
        final String result;

        final String typeName = getBuiltinTypeName();
        if ("dateTime".equals(typeName)) {
            // Format value specially

            // Assume xs: prefix for default formats
            final Map prefixToURIMap = new HashMap();
            prefixToURIMap.put(XMLConstants.XSD_PREFIX, XMLConstants.XSD_URI);

            final NodeInfo boundNode = getBoundNode();
            if (boundNode == null) {
                result = null;
            } else {
                result = XPathCache.evaluateAsString(pipelineContext, boundNode,
                        XFormsProperties.getTypeInputFormat(containingDocument, "time"),
                        prefixToURIMap, getContextStack().getCurrentVariables(),
                        XFormsContainingDocument.getFunctionLibrary(),
                        getContextStack().getFunctionContext(), null, getLocationData());
            }

        } else {
            // N/A
            result = null;
        }

        return (result != null) ? result : "";
    }
}
