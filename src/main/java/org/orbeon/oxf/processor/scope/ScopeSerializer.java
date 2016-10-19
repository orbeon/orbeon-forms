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

import org.orbeon.oxf.webapp.ExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.xml.XMLReceiver;
import org.orbeon.oxf.processor.CacheableInputReader;
import org.orbeon.oxf.processor.ProcessorInput;
import org.orbeon.oxf.processor.ProcessorInputOutputInfo;
import org.orbeon.oxf.xml.SAXStore;
import org.orbeon.oxf.xml.SimpleForwardingXMLReceiver;
import org.orbeon.oxf.xml.XMLConstants;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

import java.util.Map;

public class ScopeSerializer extends ScopeProcessorBase {

    private boolean isNull = false;

    public ScopeSerializer() {
        addInputInfo(new ProcessorInputOutputInfo(INPUT_CONFIG, SCOPE_CONFIG_NAMESPACE_URI));
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

                    public void startElement(String uri, String localname, String qName, Attributes attributes) throws SAXException {
                        super.startElement(uri, localname, qName, attributes);
                        if (root) {
                            isNull = uri.equals("") && localname.equals("null") && "true".equals(attributes.getValue(XMLConstants.XSI_URI, "nil"));
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
        Map<String, Object> map = null;
        if (config.getContextType() == ScopeProcessorBase.REQUEST_CONTEXT) {
            map = externalContext.getRequest().getAttributesMap();
        } else if (config.getContextType() == ScopeProcessorBase.SESSION_CONTEXT) {
            if (config.getSessionScope() == null)
                map = externalContext.getSession(true).getAttributesMap();
            else
                map = externalContext.getSession(true).getAttributesMap(config.getSessionScope());
        } else if (config.getContextType() == ScopeProcessorBase.APPLICATION_CONTEXT) {
            map = externalContext.getWebAppContext().getAttributesMap();
        }
        // Delete when null, otherwise store...
        if (isNull) {
            map.remove(config.getKey());
        } else {
            map.put(config.getKey(), store);
        }

    }
}
