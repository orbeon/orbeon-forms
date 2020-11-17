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
package org.orbeon.oxf.processor.scope;

import org.orbeon.oxf.externalcontext.ExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.CacheableInputReader;
import org.orbeon.oxf.processor.ProcessorInput;
import org.orbeon.oxf.processor.ProcessorInputOutputInfo;
import org.orbeon.oxf.xml.SAXStore;
import org.orbeon.oxf.xml.SimpleForwardingXMLReceiver;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.oxf.xml.XMLReceiver;
import org.xml.sax.Attributes;

import java.util.Map;

public class ScopeSerializer extends ScopeProcessorBase {

    private boolean isNull = false;// TODO: Why is this here? Should be in `ContextConfig`, right?

    public ScopeSerializer() {
        addInputInfo(new ProcessorInputOutputInfo(INPUT_CONFIG, ScopeConfigNamespaceUri()));
        addInputInfo(new ProcessorInputOutputInfo(INPUT_DATA));
    }

    public void start(PipelineContext context) {
        // Read data input into a ScopeStore
        final ScopeStore store = readCacheInputAsObject(context, getInputByName(INPUT_DATA), new CacheableInputReader<ScopeStore>() {
            public ScopeStore read(PipelineContext context, ProcessorInput input) {
                final SAXStore saxStore = new SAXStore();
                // Output filter to check if this is the null document
                final XMLReceiver filter = new SimpleForwardingXMLReceiver(saxStore) {
                    private boolean root = true;

                    public void startElement(String uri, String localname, String qName, Attributes attributes) {
                        super.startElement(uri, localname, qName, attributes);
                        if (root) {
                            isNull = uri.equals("") && localname.equals("null") && "true".equals(attributes.getValue(XMLConstants.XSI_URI(), "nil"));
                            root = false;
                        }
                    }

                };

                readInputAsSAX(context, input, filter);
                return new ScopeStore(saxStore, getInputKey(context, input), getInputValidity(context, input));
            }
        });

        // Read config
        final ContextConfig config = readConfig(context);

        final ExternalContext externalContext = (ExternalContext) context.getAttribute(PipelineContext.EXTERNAL_CONTEXT);
        // Find the map for the scope
        if (config.javaIsRequestScope()) {
            putOrRemove(externalContext.getRequest().getAttributesMap(), config, store);
        } else if (config.javaIsSessionScope()) {
            final ExternalContext.Session session = externalContext.getSession(true);
            if (isNull) {
                session.removeAttribute(config.key(), config.sessionScope());
            } else {
                session.setAttribute(config.key(), store, config.sessionScope());
            }
        } else if (config.javaIsApplicationScope()) {
            putOrRemove(externalContext.getWebAppContext().getAttributesMap(), config, store);
        }
    }

    private void putOrRemove(Map<String, Object> map, ContextConfig config, ScopeStore store) {
        if (isNull) {
            map.remove(config.key());
        } else {
            map.put(config.key(), store);
        }
    }
}
