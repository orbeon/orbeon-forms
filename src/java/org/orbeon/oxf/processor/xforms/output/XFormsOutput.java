/**
 *  Copyright (C) 2004 Orbeon, Inc.
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
package org.orbeon.oxf.processor.xforms.output;

import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.*;
import org.orbeon.oxf.processor.xforms.Constants;
import org.orbeon.oxf.processor.xforms.Model;
import org.orbeon.oxf.processor.xforms.XFormsUtils;
import org.orbeon.oxf.processor.xforms.output.element.*;
import org.orbeon.oxf.xml.ForwardingContentHandler;
import org.orbeon.oxf.xml.SAXStore;
import org.orbeon.oxf.xml.XPathUtils;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;

public class XFormsOutput extends ProcessorImpl {

    private static final String INPUT_MODEL = "model";
    private static final String INPUT_INSTANCE = "instance";

    public XFormsOutput() {
        addInputInfo(new ProcessorInputOutputInfo(INPUT_MODEL));
        addInputInfo(new ProcessorInputOutputInfo(INPUT_INSTANCE));
        addInputInfo(new ProcessorInputOutputInfo(INPUT_DATA, Constants.XFORMS_NAMESPACE_URI + "/controls"));
        addInputInfo(new ProcessorInputOutputInfo(INPUT_CONFIG));
        addOutputInfo(new ProcessorInputOutputInfo(OUTPUT_DATA));
    }

    public ProcessorOutput createOutput(String name) {
        ProcessorOutput output = new ProcessorImpl.CacheableTransformerOutputImpl(getClass(), name) {
            public void readImpl(PipelineContext context, ContentHandler contentHandler) {
                
                // Extract info from model
                final Model model = (Model) readCacheInputAsObject(context,  getInputByName(INPUT_MODEL), new CacheableInputReader(){
                    public Object read(PipelineContext context, ProcessorInput input) {
                        Model model = new Model(context);
                        readInputAsSAX(context, input, model.getContentHandlerForModel());
                        return model;
                    }
                });

                // Obsolete: used to come from config will be removed for 2.5
                final XFormsOutputConfig xformsOutputConfig = new XFormsOutputConfig("d", "http://orbeon.org/oxf/xml/document");

                // Read instance data and annotate
                final Document instance = DocumentHelper.createDocument
                        (readCacheInputAsDOM4J(context, INPUT_INSTANCE).getRootElement().createCopy());
                XFormsUtils.setInitialDecoration(instance.getDocument());
                model.applyInputOutputBinds(instance);

                // Create evaluation context
                final XFormsElementContext elementContext = new XFormsElementContext(context, model, instance, 
                        xformsOutputConfig, contentHandler);

                readInputAsSAX(context, INPUT_DATA, new ForwardingContentHandler(contentHandler) {

                    SAXStore repeatSAXStore = new SAXStore();
                    int repeatElementDepth = 0;
                    int elementDepth = 0;
                    boolean recordMode = false;

                    public void setDocumentLocator(Locator locator) {
                        elementContext.setLocator(locator);
                    }

                    public void startPrefixMapping(String prefix, String uri) throws SAXException {
                        if (recordMode) {
                            repeatSAXStore.startPrefixMapping(prefix, uri);
                        } else {
                            elementContext.getNamespaceSupport().declarePrefix(prefix, uri);
                            super.startPrefixMapping(prefix, uri);
                        }
                    }

                    public void endPrefixMapping(String prefix) throws SAXException {
                        if (recordMode) {
                            repeatSAXStore.endPrefixMapping(prefix);
                        } else {
                            super.endPrefixMapping(prefix);
                        }
                    }

                    public void startElement(String uri, String localname, String qname, Attributes attributes) throws SAXException {
                        elementContext.getNamespaceSupport().pushContext();
                        if (elementDepth == 0) {
                            super.startPrefixMapping(xformsOutputConfig.getNamespacePrefix(), xformsOutputConfig.getNamespaceURI());
                        }
                        if (recordMode) {
                            // Record event
                            repeatElementDepth++;
                            repeatSAXStore.startElement(uri, localname, qname, attributes);
                        } else if (Constants.XFORMS_NAMESPACE_URI.equals(uri) || Constants.XXFORMS_NAMESPACE_URI.equals(uri)) {

                            // Get ref
                            String ref = attributes.getValue("ref");
                            if (ref == null) ref = attributes.getValue("nodeset");

                            // New method where relative XPath is pushed
                            elementContext.pushRelativeXPath(ref, attributes.getValue("ref") != null);

                            // Old method where expanded XPath is pushed
                            if (ref != null) {
                                // Concatenate with existing ref
                                ref = XPathUtils.putNamespacesInPath(elementContext.getNamespaceSupport(), ref);
                                if (!ref.startsWith("/"))
                                    ref = elementContext.getRefXPath() + "/" + ref;
                                // Replace "." and ".."
                                ref = XFormsUtils.canonicalizeRef(ref);

                                elementContext.pushGroupRef(ref);
                            } else {
                                // Ref does not change
                                elementContext.pushGroupRef(elementContext.getRefXPath());
                            }

                            // Invoke element
                            XFormsElement element = "group".equals(localname) ? new Group()
                                    : "repeat".equals(localname) ? new Repeat()
                                    : "itemset".equals(localname) ? new Itemset()
                                    : new XFormsElement();
                            elementContext.pushElement(element);
                            element.start(elementContext, uri, localname, qname, attributes);

                            // If this is a repeat element, record children events
                            if (element.repeatChildren()) {
                                recordMode = true;
                                repeatSAXStore = new SAXStore();
                                repeatElementDepth = 0;
                            }

                        } else {
                            super.startElement(uri, localname, qname, attributes);
                        }
                        elementDepth++;
                    }

                    public void endElement(String uri, String localname, String qname) throws SAXException {
                        elementContext.getNamespaceSupport().popContext();
                        elementDepth--;
                        if (recordMode) {
                            if (repeatElementDepth == 0) {
                                // We are back to the element that requested the repeat
                                recordMode = false;
                                XFormsElement repeatElement = elementContext.peekElement();
                                SAXStore currentSAXStore = repeatSAXStore;
                                while (repeatElement.nextChildren(elementContext))
                                    currentSAXStore.replay(this);
                            } else {
                                // Record event
                                repeatElementDepth--;
                                repeatSAXStore.endElement(uri, localname, qname);
                            }
                        }

                        if (!recordMode) {
                            if (Constants.XFORMS_NAMESPACE_URI.equals(uri) || Constants.XXFORMS_NAMESPACE_URI.equals(uri)) {
                                XFormsElement element = elementContext.peekElement();
                                element.end(elementContext, uri, localname, qname);
                                elementContext.popElement();
                                elementContext.popGroupRef();
                                elementContext.popRelativeXPath();
                            } else {
                                super.endElement(uri, localname, qname);
                            }
                        }
                        if (elementDepth == 0) {
                            super.endPrefixMapping(xformsOutputConfig.getNamespacePrefix());
                        }
                    }

                    public void characters(char[] chars, int start, int length) throws SAXException {
                        if (recordMode == true) {
                            repeatSAXStore.characters(chars, start,  length);
                        } else  {
                            super.characters(chars, start, length);
                        }
                    }
                });
            }
        };
        addOutput(name, output);
        return output;
    }
}
