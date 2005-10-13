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
package org.orbeon.oxf.xforms.processor;

import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.ProcessorImpl;
import org.orbeon.oxf.processor.ProcessorInputOutputInfo;
import org.orbeon.oxf.processor.ProcessorOutput;
import org.orbeon.oxf.xml.ContentHandlerHelper;
import org.orbeon.oxf.xml.ForwardingContentHandler;
import org.orbeon.oxf.xforms.XFormsConstants;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import java.util.HashMap;
import java.util.Map;

/**
 * This processor adds ids on all the XForms elements which don't have any, and adds xforms:alert
 * elements within relevant XForms element which don't have any.
 */
public class XFormsDocumentAnnotator extends ProcessorImpl {

    private static final Map alertElements = new HashMap();

    static {
        alertElements.put("input", "");
        alertElements.put("secret", "");
        alertElements.put("textarea", "");
        alertElements.put("select", "");
        alertElements.put("select1", "");
        alertElements.put("output", "");
    }

    public XFormsDocumentAnnotator() {
        addInputInfo(new ProcessorInputOutputInfo(INPUT_DATA));
        addOutputInfo(new ProcessorInputOutputInfo(OUTPUT_DATA));
    }

    public ProcessorOutput createOutput(String name) {
        ProcessorOutput output = new ProcessorImpl.CacheableTransformerOutputImpl(getClass(), name) {
            public void readImpl(PipelineContext pipelineContext, ContentHandler contentHandler) {

                readInputAsSAX(pipelineContext, INPUT_DATA, new ForwardingContentHandler(contentHandler) {

                    private int currentId = 1;
                    private String alertParentIdAttribute;
                    private boolean hasAlert;

                    public void startElement(String uri, String localname, String qName, Attributes attributes) throws SAXException {

                        if (XFormsConstants.XFORMS_NAMESPACE_URI.equals(uri)) {
                            // This is an XForms element

                            final String initialIdAttribute = attributes.getValue("id");
                            final String newIdAttribute;
                            if (initialIdAttribute == null) {
                                // Create a new "id" attribute
                                final AttributesImpl newAttributes = new AttributesImpl(attributes);
                                final String newId = "xforms-element-" + currentId;
                                newAttributes.addAttribute("", "id", "id", ContentHandlerHelper.CDATA, newId);
                                attributes = newAttributes;

                                newIdAttribute = newId;
                            } else {
                                newIdAttribute = initialIdAttribute;
                            }

                            if (alertElements.get(localname) != null) {
                                // Control may have an alert
                                hasAlert = false;
                                alertParentIdAttribute = newIdAttribute;
                            } else if (localname.equals("alert")) {
                                hasAlert = true;
                            }

                            currentId++;
                        }

                        super.startElement(uri, localname, qName, attributes);
                    }

                    public void endElement(String uri, String localname, String qName) throws SAXException {

                        if (XFormsConstants.XFORMS_NAMESPACE_URI.equals(uri)) {
                            // This is an XForms element

                            if (alertElements.get(localname) != null) {
                                // Control may have an alert

                                if (!hasAlert) {
                                    // Create an alert element

                                    final int colonIndex = qName.indexOf(':');
                                    final String prefix = (colonIndex == -1) ? null : qName.substring(0, colonIndex);
                                    final String newLocalname = "alert";
                                    final String newQName = (prefix == null) ? newLocalname : prefix + ":" + newLocalname; // if parent doesn't have a prefix, then we can also not use a prefix

                                    final AttributesImpl newAttributes = new AttributesImpl();
                                    newAttributes.addAttribute("", "id", "id", ContentHandlerHelper.CDATA, alertParentIdAttribute + "-alert");

                                    super.startElement(XFormsConstants.XFORMS_NAMESPACE_URI, newLocalname, newQName, newAttributes);
                                    super.endElement(XFormsConstants.XFORMS_NAMESPACE_URI, newLocalname, newQName);
                                }
                            }
                        }

                        super.endElement(uri, localname, qName);
                    }
                });
            }
        };
        addOutput(name, output);
        return output;
    }
}