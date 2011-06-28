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
package org.orbeon.oxf.xforms.control.controls;

import org.apache.commons.lang.StringUtils;
import org.dom4j.Element;
import org.dom4j.QName;
import org.orbeon.oxf.externalcontext.ServletURLRewriter;
import org.orbeon.oxf.externalcontext.URLRewriter;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.util.NetUtils;
import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.XFormsUtils;
import org.orbeon.oxf.xforms.analysis.XPathDependencies;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.control.XFormsValueControl;
import org.orbeon.oxf.xforms.event.XFormsEvents;
import org.orbeon.oxf.xforms.processor.XFormsResourceServer;
import org.orbeon.oxf.xforms.submission.Headers;
import org.orbeon.oxf.xforms.xbl.XBLContainer;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.xml.sax.helpers.AttributesImpl;

import java.net.URI;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

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
    private final String format;

    // Value attribute
    private String valueAttribute;

    // XForms 1.1 mediatype attribute
    private String mediatypeAttribute;

    // For xxforms:download appearance
    private FileInfo fileInfo;

    private boolean urlNorewrite;

    public XFormsOutputControl(XBLContainer container, XFormsControl parent, Element element, String name, String id, Map<String, Element> state) {
        super(container, parent, element, name, id);
        this.format = element.attributeValue(new QName("format", XFormsConstants.XXFORMS_NAMESPACE));
        this.mediatypeAttribute = element.attributeValue(XFormsConstants.MEDIATYPE_QNAME);
        this.valueAttribute = element.attributeValue(XFormsConstants.VALUE_QNAME);

        // TODO: must be resolved statically
        this.urlNorewrite = XFormsUtils.resolveUrlNorewrite(element);

        fileInfo = new FileInfo(this, getContextStack(), element);
    }

    @Override
    protected QName[] getExtensionAttributes() {
        if (DOWNLOAD_APPEARANCE.equals(getAppearance()))
            return DOWNLOAD_APPEARANCE_EXTENSION_ATTRIBUTES;
        else
            return null;
    }

    @Override
    protected void evaluateImpl() {
        super.evaluateImpl();

        getState();
        getFileMediatype();
        getFileName();
        getFileSize();
    }

    @Override
    protected void markDirtyImpl(XPathDependencies xpathDependencies) {
        super.markDirtyImpl(xpathDependencies);
        fileInfo.markDirty();
    }

    @Override
    protected void evaluateValue() {
        final String value;
        if (valueAttribute == null) {
            // Get value from single-node binding
            final String tempValue = XFormsUtils.getBoundItemValue(bindingContext.getSingleItem());
            value = (tempValue != null) ? tempValue : "";
        } else {
            // Value comes from the XPath expression within the value attribute
            value = evaluateAsString(valueAttribute, bindingContext.getNodeset(), bindingContext.getPosition());
        }
        setValue((value != null) ? value : "");
    }

    @Override
    protected void evaluateExternalValue() {

        assert isRelevant();

        final String internalValue = getValue();
        assert internalValue != null;

        final String updatedValue;
        if (DOWNLOAD_APPEARANCE.equals(getAppearance())) {
            // Download appearance
            final String dynamicMediatype = fileInfo.getFileMediatype();
            // NOTE: Never put timestamp for downloads otherwise browsers may cache the file to download which is not
            updatedValue = proxyValueIfNeeded(internalValue, "", (dynamicMediatype != null) ? dynamicMediatype : mediatypeAttribute);
        } else if (mediatypeAttribute != null && mediatypeAttribute.startsWith("image/")) {
            // Image mediatype
            // Use dummy image as default value so that client always has something to load
            updatedValue = proxyValueIfNeeded(internalValue, XFormsConstants.DUMMY_IMAGE_URI, mediatypeAttribute);
        } else if (mediatypeAttribute != null && mediatypeAttribute.equals("text/html")) {
            // HTML mediatype
            updatedValue = internalValue;
        } else {
            // Other mediatypes
            if (valueAttribute == null) {
                // There is a single-node binding, so the format may be used
                final String formattedValue = getValueUseFormat(format);
                updatedValue = (formattedValue != null) ? formattedValue : internalValue;
            } else {
                // There is a @value attribute, don't use format
                updatedValue = internalValue;
            }
        }

        setExternalValue(updatedValue);
    }

    // For unit tests
    public Map<String, String[]> testEvaluateHeaders() {
        return evaluateHeaders();
    }

    private Map<String, String[]> evaluateHeaders() {
        getContextStack().setBinding(this); // evaluateHeaders() requires this (bad, but do this until we can pass BindingContext directly)
        return Headers.evaluateHeaders(getXBLContainer(), getContextStack(),
                getEffectiveId(), getElementAnalysis().element());
    }

    private String proxyValueIfNeeded(String internalValue, String defaultValue, String mediatype) {
        String updatedValue;
        final String typeName = getBuiltinTypeName();
        if (internalValue != null && internalValue.length() > 0 && internalValue.trim().length() > 0) {
            if (typeName == null || "anyURI".equals(typeName)) {// we picked xs:anyURI as default
                // xs:anyURI type
                if (!urlNorewrite) {
                    // Resolve xml:base and try to obtain a path which is an absolute path without the context
                    final URI rebasedURI = XFormsUtils.resolveXMLBase(containingDocument, getControlElement(), internalValue);
                    final URLRewriter servletRewriter = new ServletURLRewriter(NetUtils.getExternalContext().getRequest());
                    final String resolvedURI = servletRewriter.rewriteResourceURL(rebasedURI.toString(), ExternalContext.Response.REWRITE_MODE_ABSOLUTE_PATH_NO_CONTEXT);

                    final long lastModified = NetUtils.getLastModifiedIfFast(resolvedURI);
                    updatedValue = XFormsResourceServer.proxyURI(resolvedURI, fileInfo.getFileName(), mediatype, lastModified, evaluateHeaders());
                } else {
                    // Otherwise we leave the value as is
                    updatedValue = internalValue;
                }
            } else if ("base64Binary".equals(typeName)) {
                // xs:base64Binary type
                // TODO: avoid cast to PipelineContext
                final String uri = NetUtils.base64BinaryToAnyURI(internalValue, NetUtils.SESSION_SCOPE);
                // Value of -1 for lastModified will cause XFormsResourceServer to set Last-Modified and Expires properly to "now".
                updatedValue = XFormsResourceServer.proxyURI(uri, fileInfo.getFileName(), mediatype, -1, evaluateHeaders());

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

    @Override
    public String getEscapedExternalValue() {
        if (DOWNLOAD_APPEARANCE.equals(getAppearance()) || mediatypeAttribute != null && mediatypeAttribute.startsWith("image/")) {
            final String externalValue = getExternalValue();
            if (StringUtils.isNotBlank(externalValue)) {
                // External value is not blank, rewrite as absolute path. Two cases:
                // o URL is proxied:        /xforms-server/dynamic/27bf...  => [/context]/xforms-server/dynamic/27bf...
                // o URL is default value:  /ops/images/xforms/spacer.gif   => [/context][/version]/ops/images/xforms/spacer.gif
                return XFormsUtils.resolveResourceURL(containingDocument, getControlElement(), externalValue, ExternalContext.Response.REWRITE_MODE_ABSOLUTE_PATH);
            } else {
                // Empty value, return as is
                return externalValue;
            }
        } else if (mediatypeAttribute != null && mediatypeAttribute.equals("text/html")) {
            // Rewrite the HTML value with resolved @href and @src attributes
            return XFormsControl.getEscapedHTMLValue(getLocationData(), getExternalValue());
        } else {
            // Return external value as is
            return getExternalValue();
        }
    }

    @Override
    public String getNonRelevantEscapedExternalValue() {
        if (mediatypeAttribute != null && mediatypeAttribute.startsWith("image/")) {
            // Return rewritten URL of dummy image URL
            return XFormsUtils.resolveResourceURL(containingDocument, getControlElement(), XFormsConstants.DUMMY_IMAGE_URI,
                    ExternalContext.Response.REWRITE_MODE_ABSOLUTE_PATH);
        } else {
            return super.getNonRelevantEscapedExternalValue();
        }
    }

    public String getMediatypeAttribute() {
        return mediatypeAttribute;
    }

    public String getValueAttribute() {
        return valueAttribute;
    }

    @Override
    public QName getType() {
        // No type information is returned when there is a value attribute

        // Question: what if we have both @ref and @value? Should a type still be provided? This is not supported in
        // XForms 1.1 but we do support it, with the idea that the bound node does not provide the value but provides
        // mips. Not sure if the code below makes sense after all then.
        return (valueAttribute == null) ? super.getType() : null;
    }

    public String getState() {
        return fileInfo.getState();
    }

    public String getFileMediatype() {
        return fileInfo.getFileMediatype();
    }

    public String getFileName() {
        return fileInfo.getFileName();
    }

    public String getFileSize() {
        return fileInfo.getFileSize();
    }

    public void setMediatype(String mediatype) {
        fileInfo.setMediatype(mediatype);
    }

    public void setFilename(String filename) {
        fileInfo.setFilename(filename);
    }

    public void setSize(String size) {
        fileInfo.setSize(size);
    }

    public static String getExternalValue(XFormsOutputControl control, String mediatypeValue) {
        if (control != null) {
            // Get control value
            return control.getExternalValue();
        } else if (mediatypeValue != null && mediatypeValue.startsWith("image/")) {
            // Return dummy image
            return XFormsConstants.DUMMY_IMAGE_URI;
        } else {
            // Provide default for other types
            return null;
        }
    }

    @Override
    public boolean setFocus() {
        // It usually doesn't make sense to focus on xf:output, at least not in the sense "focus to enter data". So we
        // disallow this for now.
        return false;
    }

    @Override
    protected boolean addAjaxCustomAttributes(AttributesImpl attributesImpl, boolean isNewRepeatIteration, XFormsControl other) {

        final XFormsOutputControl outputControl1 = (XFormsOutputControl) other;
        final XFormsOutputControl outputControl2 = this;

        // Mediatype
        final String mediatypeValue1 = (outputControl1 == null) ? null : outputControl1.getMediatypeAttribute();
        final String mediatypeValue2 = outputControl2.getMediatypeAttribute();

        boolean added = false;
        if (!((mediatypeValue1 == null && mediatypeValue2 == null) || (mediatypeValue1 != null && mediatypeValue2 != null && mediatypeValue1.equals(mediatypeValue2)))) {
            final String attributeValue = mediatypeValue2 != null ? mediatypeValue2 : "";
            added |= addAttributeIfNeeded(attributesImpl, "mediatype", attributeValue, isNewRepeatIteration, attributeValue.equals(""));
        }

        return added;
    }

    @Override
    public Object getBackCopy() {
        final XFormsOutputControl cloned = (XFormsOutputControl) super.getBackCopy();
        cloned.fileInfo = (FileInfo) fileInfo.getBackCopy();
        return cloned;
    }

    private static final Set<String> IGNORED_EXTERNAL_EVENTS = new HashSet<String>();
    private static final Set<String> ALLOWED_EXTERNAL_EVENTS = new HashSet<String>();
    static {
        IGNORED_EXTERNAL_EVENTS.add(XFormsEvents.DOM_FOCUS_IN);
        IGNORED_EXTERNAL_EVENTS.add(XFormsEvents.DOM_FOCUS_OUT);

        ALLOWED_EXTERNAL_EVENTS.addAll(IGNORED_EXTERNAL_EVENTS);
        ALLOWED_EXTERNAL_EVENTS.add(XFormsEvents.XFORMS_HELP);
    }

    @Override
    protected Set<String> getAllowedExternalEvents() {
        return ALLOWED_EXTERNAL_EVENTS;
    }

    public boolean isIgnoredExternalEvent(String eventName) {
        // Mmh, this looks like some legacy stuff?
        return IGNORED_EXTERNAL_EVENTS.contains(eventName);
    }
}
