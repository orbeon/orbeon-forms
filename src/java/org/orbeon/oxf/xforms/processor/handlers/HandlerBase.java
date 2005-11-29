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
import org.orbeon.oxf.xforms.XFormsControls;
import org.orbeon.oxf.xml.*;
import org.xml.sax.Attributes;
import org.xml.sax.helpers.AttributesImpl;

/**
 *
 */
public abstract class HandlerBase extends ElementHandlerNew {

    // NOTE: the XForms schema seems to indicates that "style", "onchange", and others
    // cannot be used; those should probably be in the XHTML namespace
    private static final String[] ATTRIBUTES_TO_COPY = {"accesskey", "tabindex", "style", "onchange"};

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

    public static void handleReadOnlyAttribute(AttributesImpl newAttributes, XFormsControls.ControlInfo controlInfo) {
        if (controlInfo.isReadonly()) {
            // @disabled="disabled"
            newAttributes.addAttribute("", "disabled", "disabled", ContentHandlerHelper.CDATA, "disabled");
        }
    }

    public static void handleRelevantClass(StringBuffer sb, XFormsControls.ControlInfo controlInfo) {
        if (controlInfo != null && !controlInfo.isRelevant()) {// TEMP, controlInfo should not be null
            if (sb.length() > 0)
                sb.append(' ');
            sb.append("xforms-disabled");
        }
    }

    public static void handleReadOnlyClass(StringBuffer sb, XFormsControls.ControlInfo controlInfo) {
        if (controlInfo != null && controlInfo.isReadonly()) {// TEMP, controlInfo should not be null
            if (sb.length() > 0)
                sb.append(' ');
            sb.append("xforms-readonly");
        }
    }

    public static boolean isDateOrTime(String type) {
        return "{http://www.w3.org/2001/XMLSchema}date".equals(type)
                || "{http://www.w3.org/2001/XMLSchema}dateTime".equals(type)
                || "{http://www.w3.org/2001/XMLSchema}time".equals(type);
    }

    protected AttributesImpl getAttributes(Attributes elementAttributes, String classes, String id) {
        reusableAttributes.clear();

        // Copy "id"
        if (id != null) {
            reusableAttributes.addAttribute("", "id", "id", ContentHandlerHelper.CDATA, id);
        }
        // Copy common attributes
        for (int i = 0; i < ATTRIBUTES_TO_COPY.length; i++) {
            final String name = ATTRIBUTES_TO_COPY[i];
            final String value = elementAttributes.getValue(name);
            if (value != null)
                reusableAttributes.addAttribute("", name, name, ContentHandlerHelper.CDATA, value);
        }
        // Copy "navindex" into "tabindex"
        {
            final String value = elementAttributes.getValue("navindex");
            if (value != null)
                reusableAttributes.addAttribute("", "tabindex", "tabindex", ContentHandlerHelper.CDATA, value);
        }
        // Create "class" attribute if necessary
        {
            final StringBuffer sb = new StringBuffer((classes != null) ? classes : "");
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
