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
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.XFormsContainingDocument;
import org.orbeon.oxf.xforms.XFormsInstance;
import org.orbeon.oxf.xforms.XFormsUtils;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.control.XFormsValueControl;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.saxon.om.NodeInfo;

import java.net.URI;
import java.util.List;

/**
 * Represents an xforms:output control.
 */
public class XFormsOutputControl extends XFormsValueControl {

    // Optional display format
    private String format;

    // Value attribute
    private String valueAttribute;

    // XForms 1.1 draft mediatype attribute
    private String mediaTypeAttribute;

    public XFormsOutputControl(XFormsContainingDocument containingDocument, XFormsControl parent, Element element, String name, String id) {
        super(containingDocument, parent, element, name, id);
        this.format = element.attributeValue(new QName("format", XFormsConstants.XXFORMS_NAMESPACE));
        this.mediaTypeAttribute = element.attributeValue("mediatype");
        this.valueAttribute = element.attributeValue("value");
    }

    public void evaluateValue(PipelineContext pipelineContext) {
        final String rawValue;
        if (valueAttribute == null) {
            // Get value from single-node binding
            final NodeInfo currentSingleNode = bindingContext.getSingleNode();
            if (currentSingleNode != null)
                rawValue = XFormsInstance.getValueForNode(currentSingleNode);
            else
                rawValue = "";
        } else {
            // Value comes from the XPath expression within the value attribute
            final List currentNodeset = bindingContext.getNodeset();
            if (currentNodeset != null && currentNodeset.size() > 0) {
                rawValue = containingDocument.getEvaluator().evaluateAsString(pipelineContext,
                        currentNodeset, bindingContext.getPosition(),
                        valueAttribute, Dom4jUtils.getNamespaceContextNoDefault(getControlElement()), null, containingDocument.getXFormsControls().getFunctionLibrary(), null);
            } else {
                rawValue = "";
            }
        }

        // Handle image mediatype if necessary
        final String updatedValue;
        if (mediaTypeAttribute != null && mediaTypeAttribute.startsWith("image/")) {
            final String type = getType();
            if (type == null || type.equals(XMLUtils.buildExplodedQName(XMLConstants.XSD_URI, "anyURI"))) {
                // Rewrite URI
                final URI resolvedURI = XFormsUtils.resolveURI(getControlElement(), rawValue);
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
        evaluateDisplayValueUseFormat(pipelineContext, format);
    }

    public String getMediaTypeAttribute() {
        return mediaTypeAttribute;
    }

    public String getValueAttribute() {
        return valueAttribute;
    }
}
