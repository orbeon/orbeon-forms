/**
 *  Copyright (C) 2005 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.processor.handlers;

import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.xforms.XFormsContainingDocument;
import org.orbeon.oxf.xforms.controls.ControlInfo;
import org.orbeon.oxf.xml.*;
import org.xml.sax.Attributes;
import org.xml.sax.helpers.AttributesImpl;

/**
 *
 */
public abstract class HandlerBase extends ElementHandlerNew {

    // NOTE: the XForms schema seems to indicates that "style", "onchange", and others
    // cannot be used; those should probably be in the XHTML namespace
    private static final String[] XHTML_ATTRIBUTES_TO_COPY = { "style", "onchange" };

    private boolean repeating;
    private boolean forwarding;

    protected HandlerContext handlerContext;
    protected PipelineContext pipelineContext;
    protected XFormsContainingDocument containingDocument;
    protected ExternalContext externalContext;

    protected AttributesImpl reusableAttributes = new AttributesImpl();

    protected HandlerBase(boolean repeating, boolean forwarding) {
        this.repeating = repeating;
        this.forwarding = forwarding;
    }

    public void setContext(Object context) {
        this.handlerContext = (HandlerContext) context;

        this.pipelineContext = handlerContext.getPipelineContext();
        this.containingDocument = handlerContext.getContainingDocument();
        this.externalContext = handlerContext.getExternalContext();

        super.setContext(context);
    }

    public boolean isRepeating() {
        return repeating;
    }

    public boolean isForwarding() {
        return forwarding;
    }

    public static void handleReadOnlyAttribute(AttributesImpl newAttributes, ControlInfo controlInfo) {
        if (controlInfo != null && controlInfo.isReadonly()) {
            // @disabled="disabled"
            newAttributes.addAttribute("", "disabled", "disabled", ContentHandlerHelper.CDATA, "disabled");
        }
    }

    public static void handleMIPClasses(StringBuffer sb, ControlInfo controlInfo) {
        if (controlInfo != null) {// TEMP, controlInfo should not be null
            if (!controlInfo.isRelevant()) {
                if (sb.length() > 0)
                    sb.append(' ');
                sb.append("xforms-disabled");
            }
            if (!controlInfo.isValid()) {
                if (sb.length() > 0)
                    sb.append(' ');
                sb.append("xforms-invalid");
            }
            if (controlInfo != null && controlInfo.isReadonly()) {
                if (sb.length() > 0)
                    sb.append(' ');
                sb.append("xforms-readonly");
            }
            if (controlInfo != null && controlInfo.isRequired()) {
                if (sb.length() > 0)
                    sb.append(' ');
                sb.append("xforms-required");
                if ("".equals(controlInfo.getValue()))
                    sb.append(" xforms-required-empty");
                else
                    sb.append(" xforms-required-filled");

            }
        }
    }

    public static boolean isDateOrTime(String type) {
        return isDate(type)
                || "{http://www.w3.org/2001/XMLSchema}dateTime".equals(type)
                || "{http://www.w3.org/2001/XMLSchema}time".equals(type);
    }

    public static boolean isDate(String type) {
        return "{http://www.w3.org/2001/XMLSchema}date".equals(type);
    }

    protected void handleAccessibilityAttributes(Attributes srcAttributes, AttributesImpl destAttributes) {
        // Handle "tabindex"
        {
            String value = srcAttributes.getValue("navindex");// This is the standard XForms attribute
            if (value == null)
                value = srcAttributes.getValue("tabindex");// This is the XHTML attribute
            if (value != null)
                destAttributes.addAttribute("", "tabindex", "tabindex", ContentHandlerHelper.CDATA, value);
        }
        // Handle "accesskey"
        {
            final String value = srcAttributes.getValue("accesskey");
            if (value != null)
                destAttributes.addAttribute("", "accesskey", "accesskey", ContentHandlerHelper.CDATA, value);
        }
    }

    protected AttributesImpl getAttributes(Attributes elementAttributes, String classes, String id) {
        reusableAttributes.clear();

        // Copy "id"
        if (id != null) {
            reusableAttributes.addAttribute("", "id", "id", ContentHandlerHelper.CDATA, id);
        }
        // Copy common attributes
        for (int i = 0; i < XHTML_ATTRIBUTES_TO_COPY.length; i++) {
            final String name = XHTML_ATTRIBUTES_TO_COPY[i];
            final String value = elementAttributes.getValue(name);
            if (value != null)
                reusableAttributes.addAttribute("", name, name, ContentHandlerHelper.CDATA, value);
        }
        // Create "class" attribute if necessary
        {
            final StringBuffer sb = new StringBuffer();
            // User-defined classes go first
            {
                final String value = elementAttributes.getValue("class");
                if (value != null) {
                    if (sb.length() > 0)
                        sb.append(' ');
                    sb.append(value);
                }
            }
            {
                final String value = elementAttributes.getValue(XMLConstants.XHTML_NAMESPACE_URI, "class");
                if (value != null) {
                    if (sb.length() > 0)
                        sb.append(' ');
                    sb.append(value);
                }
            }
            // XForms engine classes go next
            {
                if (classes != null) {
                    if (sb.length() > 0)
                        sb.append(' ');
                    sb.append(classes);
                }
            }
            {
                final String value = elementAttributes.getValue("incremental");
                if ("true".equals(value)) {
                    if (sb.length() > 0)
                        sb.append(' ');
                    sb.append("xforms-incremental");
                }
            }
            if (sb.length() > 0) {
                reusableAttributes.addAttribute("", "class", "class", ContentHandlerHelper.CDATA, sb.toString());
            }
        }
        // Copy attributes in the xhtml namespace to no namespace
        for (int i = 0; i < elementAttributes.getLength(); i++) {
            if (XMLConstants.XHTML_NAMESPACE_URI.equals(elementAttributes.getURI(i))) {
                final String name = elementAttributes.getLocalName(i);
                if (!"class".equals(name)) {
                    reusableAttributes.addAttribute("", name, name, ContentHandlerHelper.CDATA, elementAttributes.getValue(i));
                }
            }
        }

        return reusableAttributes;
    }

    protected String uriFromQName(String qName) {
        return XMLUtils.uriFromQName(qName, handlerContext.getController().getNamespaceSupport());
    }
}
