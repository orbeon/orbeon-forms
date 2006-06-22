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
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.processor.ProcessorImpl;
import org.orbeon.oxf.processor.ProcessorInputOutputInfo;
import org.orbeon.oxf.processor.ProcessorOutput;
import org.orbeon.oxf.xml.ContentHandlerHelper;
import org.orbeon.oxf.xml.ForwardingContentHandler;
import org.orbeon.oxf.xml.dom4j.ExtendedLocationData;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.common.ValidationException;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.Locator;
import org.xml.sax.helpers.AttributesImpl;

import java.util.HashMap;
import java.util.Map;

/**
 * This processor adds ids on all the XForms elements which don't have any, and adds xforms:alert
 * elements within relevant XForms element which don't have any.
 *
 * TODO: We shouldn't have to add those xforms:alert elements. Is this a legacy from the XSLT version of XFormsToXHTML?
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
        addInputInfo(new ProcessorInputOutputInfo("namespace")); // This input ensures that we depend on a portlet namespace
        addOutputInfo(new ProcessorInputOutputInfo(OUTPUT_DATA));
    }

    public ProcessorOutput createOutput(String name) {
        ProcessorOutput output = new ProcessorImpl.CacheableTransformerOutputImpl(getClass(), name) {
            public void readImpl(PipelineContext pipelineContext, ContentHandler contentHandler) {

                final ExternalContext externalContext = ((ExternalContext) pipelineContext.getAttribute(PipelineContext.EXTERNAL_CONTEXT));
                final boolean isPortlet = "portlet".equals(externalContext.getRequest().getContainerType());
                final String containerNamespace = externalContext.getRequest().getContainerNamespace();

                final Map ids = new HashMap();

                readInputAsSAX(pipelineContext, INPUT_DATA, new ForwardingContentHandler(contentHandler) {

                    private int currentId = 1;
                    private String alertParentIdAttribute;
                    private boolean hasAlert;
                    private Locator documentLocator;

                    public void startElement(String uri, String localname, String qName, Attributes attributes) throws SAXException {

                        if (XFormsConstants.XFORMS_NAMESPACE_URI.equals(uri)) {
                            // This is an XForms element

                            final int idIndex = attributes.getIndex("id");
                            final String newIdAttributeUnprefixed;
                            final String newIdAttribute;
                            if (idIndex == -1) {
                                // Create a new "id" attribute, prefixing if needed
                                final AttributesImpl newAttributes = new AttributesImpl(attributes);
                                newIdAttributeUnprefixed = "xforms-element-" + currentId;
                                newIdAttribute = containerNamespace + newIdAttributeUnprefixed;
                                newAttributes.addAttribute("", "id", "id", ContentHandlerHelper.CDATA, newIdAttribute);
                                attributes = newAttributes;
                            } else if (isPortlet) {
                                // Then we must prefix the existing id
                                final AttributesImpl newAttributes = new AttributesImpl(attributes);
                                newIdAttributeUnprefixed = newAttributes.getValue(idIndex);
                                newIdAttribute = containerNamespace + newIdAttributeUnprefixed;
                                newAttributes.setValue(idIndex, newIdAttribute);
                                attributes = newAttributes;
                            } else {
                                // Keep existing id
                                newIdAttributeUnprefixed = newIdAttribute = attributes.getValue(idIndex);
                            }

                            // Check for duplicate ids
                            if (ids.get(newIdAttribute) != null) // TODO: create Element to provide more info?
                                throw new ValidationException("Duplicate id for XForms element: " + newIdAttributeUnprefixed,
                                        new ExtendedLocationData(new LocationData(getDocumentLocator()), "analyzing control element", new String[] { "id", newIdAttributeUnprefixed }, false));

                            // Remember that this id was used
                            ids.put(newIdAttribute, "");

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

                    public void setDocumentLocator(Locator locator) {
                        this.documentLocator = locator;
                        super.setDocumentLocator(locator);
                    }

                    public Locator getDocumentLocator() {
                        return documentLocator;
                    }
                });
            }
        };
        addOutput(name, output);
        return output;
    }
}