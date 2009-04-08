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
import org.orbeon.oxf.util.NetUtils;
import org.orbeon.oxf.util.XPathCache;
import org.orbeon.oxf.xforms.*;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.control.XFormsValueControl;
import org.orbeon.oxf.xforms.control.XFormsSingleNodeControl;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.saxon.om.NodeInfo;
import org.xml.sax.helpers.AttributesImpl;

import java.util.List;

/**
 * Represents an xforms:output control.
 */
public class XFormsOutputControl extends XFormsValueControl {

    // List of attributes to handle as AVTs
    private static final QName[] DOWNLOAD_APPEARANCE_EXTENSION_ATTRIBUTES = {
            XFormsConstants.XXFORMS_TARGET_QNAME
    };

    private static final String DOWNLOAD_APPEARANCE = Dom4jUtils.qNameToExplodedQName(XFormsConstants.XXFORMS_DOWNLOAD_APPEARANCE_QNAME);

    // Optional display format
    private String format;

    // Value attribute
    private String valueAttribute;

    // XForms 1.1 mediatype attribute
    private String mediatypeAttribute;

    // For xxforms:download appearance
    private FileInfo fileInfo;

    private boolean urlNorewrite;

    public XFormsOutputControl(XFormsContainer container, XFormsControl parent, Element element, String name, String id) {
        super(container, parent, element, name, id);
        this.format = element.attributeValue(new QName("format", XFormsConstants.XXFORMS_NAMESPACE));
        this.mediatypeAttribute = element.attributeValue("mediatype");
        this.valueAttribute = element.attributeValue("value");
        this.urlNorewrite = XFormsUtils.resolveUrlNorewrite(element);

        fileInfo = new FileInfo(this, getContextStack(), element);
    }

    protected QName[] getExtensionAttributes() {
        if (DOWNLOAD_APPEARANCE.equals(getAppearance()))
            return DOWNLOAD_APPEARANCE_EXTENSION_ATTRIBUTES;
        else
            return null;
    }

    protected void evaluate(PipelineContext pipelineContext) {
        super.evaluate(pipelineContext);

        getState(pipelineContext);
        getFileMediatype(pipelineContext);
        getFileName(pipelineContext);
        getFileSize(pipelineContext);
    }

    public void markDirty() {
        super.markDirty();
        fileInfo.markDirty();
    }

    protected void evaluateValue(PipelineContext pipelineContext) {
        final String value;
        if (valueAttribute == null) {
            // Get value from single-node binding
            final NodeInfo currentSingleNode = bindingContext.getSingleNode();
            if (currentSingleNode != null)
                value = XFormsInstance.getValueForNodeInfo(currentSingleNode);
            else
                value = "";
        } else {
            // Value comes from the XPath expression within the value attribute
            final List currentNodeset = bindingContext.getNodeset();
            if (currentNodeset != null && currentNodeset.size() > 0) {

                // Need to ensure the binding on the context stack is correct before evaluating XPath expressions
                getContextStack().setBinding(this);

                value = XPathCache.evaluateAsString(pipelineContext,
                        currentNodeset, bindingContext.getPosition(),
                        valueAttribute, containingDocument.getNamespaceMappings(getControlElement()), bindingContext.getInScopeVariables(),
                        XFormsContainingDocument.getFunctionLibrary(), getContextStack().getFunctionContext(), null, getLocationData());
            } else {
                value = "";
            }
        }
        setValue(value);
    }

    protected void evaluateExternalValue(PipelineContext pipelineContext) {

        final String internalValue = getValue(pipelineContext);
        final String updatedValue;
        if (DOWNLOAD_APPEARANCE.equals(getAppearance())) {
            // Download appearance
            final String dynamicMediatype = fileInfo.getFileMediatype(pipelineContext);
            // NOTE: Never put timestamp for downloads otherwise browsers may cache the file to download which is not desirable in most cases
            updatedValue = proxyValueIfNeeded(pipelineContext, internalValue, "", (dynamicMediatype != null) ? dynamicMediatype : mediatypeAttribute, 0);
        } else if (mediatypeAttribute != null && mediatypeAttribute.startsWith("image/")) {
            // Image mediatype
            updatedValue = proxyValueIfNeeded(pipelineContext, internalValue, XFormsConstants.DUMMY_IMAGE_URI, mediatypeAttribute, System.currentTimeMillis());// use dummy image so that client always has something to load
        } else if (mediatypeAttribute != null && mediatypeAttribute.equals("text/html")) {
            // HTML mediatype
            updatedValue = internalValue;
        } else {
            // Other mediatypes
            if (valueAttribute == null) {
                // There is a single-node binding, so the format may be used
                final String formattedValue = getValueUseFormat(pipelineContext, format);
                updatedValue = (formattedValue != null) ? formattedValue : internalValue;
            } else {
                // There is a @value attribute, don't use format
                updatedValue = internalValue;
            }
        }

        setExternalValue(updatedValue);
    }

    private String proxyValueIfNeeded(PipelineContext pipelineContext, String internalValue, String defaultValue, String mediatype, long lastModified) {
        String updatedValue;
        final String typeName = getBuiltinTypeName();
        if (internalValue != null && internalValue.length() > 0 && internalValue.trim().length() > 0) {
            if (typeName == null || "anyURI".equals(typeName)) {// we picked xs:anyURI as default
                // xs:anyURI type
                if (!urlNorewrite) {
                    // Resolve xml:base and try to obtain a path which is an absolute path without the context
                    final String resolvedURI = XFormsUtils.resolveResourceURL(pipelineContext, getControlElement(), internalValue, ExternalContext.Response.REWRITE_MODE_ABSOLUTE_PATH_NO_CONTEXT);
                    updatedValue = NetUtils.proxyURI(pipelineContext, resolvedURI, fileInfo.getFileName(pipelineContext), mediatype, lastModified);
                } else {
                    // Otherwise we leave the value as is
                    updatedValue = internalValue;
                }
            } else if ("base64Binary".equals(typeName)) {
                // xs:base64Binary type

                final String uri = NetUtils.base64BinaryToAnyURI(pipelineContext, internalValue, NetUtils.SESSION_SCOPE);
                updatedValue = NetUtils.proxyURI(pipelineContext, uri, fileInfo.getFileName(pipelineContext), mediatype, lastModified);

            } else {
                // Return dummy image
                updatedValue = defaultValue;
            }
        } else {
            // Return dummy image
            updatedValue = defaultValue;
        }
        return updatedValue;
    }

    public String getEscapedExternalValue(PipelineContext pipelineContext) {
        if (DOWNLOAD_APPEARANCE.equals(getAppearance()) || mediatypeAttribute != null && mediatypeAttribute.startsWith("image/")) {
            // We just need to prepend the context because the URL is proxied
            final String externalValue = getExternalValue(pipelineContext);
            if (externalValue != null && !externalValue.trim().equals("")) {
                final ExternalContext externalContext = (ExternalContext) pipelineContext.getAttribute(PipelineContext.EXTERNAL_CONTEXT);
                return externalContext.getRequest().getContextPath() + externalValue;
            } else {
                return externalValue;
            }
        } else if (mediatypeAttribute != null && mediatypeAttribute.equals("text/html")) {
            // Rewrite the HTML value
            return getEscapedHTMLValue(pipelineContext, getExternalValue(pipelineContext));
        } else {
            return getExternalValue(pipelineContext);
        }
    }

    public String getMediatypeAttribute() {
        return mediatypeAttribute;
    }

    public String getValueAttribute() {
        return valueAttribute;
    }

    public String getType() {
        // No type information is returned when there is a value attribute

        // Question: what if we have both @ref and @value? Should a type still be provided? This is not supported in
        // XForms 1.1 but we do support it, with the idea that the bound node does not provide the value but provides
        // mips. Not sure if the code below makes sense after all then.
        return (valueAttribute == null) ? super.getType() : null;
    }

    public String getState(PipelineContext pipelineContext) {
        return fileInfo.getState(pipelineContext);
    }

    public String getFileMediatype(PipelineContext pipelineContext) {
        return fileInfo.getFileMediatype(pipelineContext);
    }

    public String getFileName(PipelineContext pipelineContext) {
        return fileInfo.getFileName(pipelineContext);
    }

    public String getFileSize(PipelineContext pipelineContext) {
        return fileInfo.getFileSize(pipelineContext);
    }

    public void setMediatype(PipelineContext pipelineContext, String mediatype) {
        fileInfo.setMediatype(pipelineContext, mediatype);
    }

    public void setFilename(PipelineContext pipelineContext, String filename) {
        fileInfo.setFilename(pipelineContext, filename);
    }

    public void setSize(PipelineContext pipelineContext, String size) {
        fileInfo.setSize(pipelineContext, size);
    }

    public static String getExternalValue(PipelineContext pipelineContext, XFormsOutputControl control, String mediatypeValue) {
        if (control != null) {
            // Get control value
            return control.getExternalValue(pipelineContext);
        } else if (mediatypeValue != null && mediatypeValue.startsWith("image/")) {
            // Return dummy image
            return XFormsConstants.DUMMY_IMAGE_URI;
        } else {
            // Provide default for other types
            return null;
        }
    }

    public boolean addAttributesDiffs(PipelineContext pipelineContext, XFormsSingleNodeControl other, AttributesImpl attributesImpl, boolean isNewRepeatIteration) {
        final XFormsOutputControl outputControlInfo1 = (XFormsOutputControl) other;
        final XFormsOutputControl outputControlInfo2 = this;

        // Mediatype
        final String mediatypeValue1 = (outputControlInfo1 == null) ? null : outputControlInfo1.getMediatypeAttribute();
        final String mediatypeValue2 = outputControlInfo2.getMediatypeAttribute();

        boolean added = false;
        if (!((mediatypeValue1 == null && mediatypeValue2 == null) || (mediatypeValue1 != null && mediatypeValue2 != null && mediatypeValue1.equals(mediatypeValue2)))) {
            final String attributeValue = mediatypeValue2 != null ? mediatypeValue2 : "";
            added |= addAttributeIfNeeded(attributesImpl, "mediatype", attributeValue, isNewRepeatIteration, attributeValue.equals(""));
        }

        return added;
    }

    public Object clone() {
        final XFormsOutputControl cloned = (XFormsOutputControl) super.clone();
        // NOTE: this keeps old refs to control/contextStack, is it ok?
        cloned.fileInfo = (FileInfo) fileInfo.clone();
        return cloned;
    }
}
