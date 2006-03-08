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
package org.orbeon.oxf.xforms.controls;

import org.dom4j.Element;
import org.dom4j.QName;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.XFormsContainingDocument;
import org.orbeon.oxf.xforms.XFormsInstance;
import org.orbeon.oxf.xforms.XFormsUtils;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;

import java.net.URI;

/**
 * Represents an xforms:output control.
 */
public class OutputControlInfo extends ControlInfo {

    // Optional display format
    private String format;

    // Value attribute
    private String valueAttribute;

    // XForms 1.1 draft mediatype attribute
    private String mediaTypeAttribute;

    public OutputControlInfo(XFormsContainingDocument containingDocument, ControlInfo parent, Element element, String name, String id) {
        super(containingDocument, parent, element, name, id);
        this.format = element.attributeValue(new QName("format", XFormsConstants.XXFORMS_NAMESPACE));
        this.mediaTypeAttribute = element.attributeValue("mediatype");
        this.valueAttribute = element.attributeValue("value");
    }

    public void evaluateValue(PipelineContext pipelineContext) {
        final String rawValue;
        if (valueAttribute == null) {
            // Get value from single-node binding
            rawValue = XFormsInstance.getValueForNode(currentBindingContext.getSingleNode());
        } else {
            // Value comes from the XPath expression within the value attribute
            rawValue = currentBindingContext.getModel().getDefaultInstance().evaluateXPathAsString(pipelineContext,
                    currentBindingContext.getNodeset(), currentBindingContext.getPosition(),
                    "string(" + valueAttribute + ")", Dom4jUtils.getNamespaceContextNoDefault(getElement()), null, containingDocument.getXFormsControls().getFunctionLibrary(), null);
        }

        // Handle mediatype if necessary
        final String updatedValue;
        if (mediaTypeAttribute != null && mediaTypeAttribute.startsWith("image/")) {
            final String type = getType();
            if (type == null || type.equals(XMLUtils.buildExplodedQName(XMLConstants.XSD_URI, "anyURI"))) {
                // Rewrite URI
                final URI resolvedURI = XFormsUtils.resolveURI(getElement(), rawValue);
                final ExternalContext externalContext = (ExternalContext) pipelineContext.getAttribute(PipelineContext.EXTERNAL_CONTEXT);
                updatedValue = externalContext.getResponse().rewriteResourceURL(resolvedURI.toString(), false);
            } else {
                updatedValue = rawValue;
            }
        } else {
            updatedValue = rawValue;
        }

        super.setValue(updatedValue);
    }

    public void evaluateDisplayValue(PipelineContext pipelineContext) {
        evaluateDisplayValue(pipelineContext, format);
    }

    public String getMediaTypeAttribute() {
        return mediaTypeAttribute;
    }

    public String getValueAttribute() {
        return valueAttribute;
    }
}
