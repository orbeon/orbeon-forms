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

import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.CacheableInputReader;
import org.orbeon.oxf.processor.ProcessorInput;
import org.orbeon.oxf.processor.ProcessorInputOutputInfo;
import org.orbeon.oxf.xml.SAXStore;

public class ScopeSerializer extends ScopeProcessorBase {


    public ScopeSerializer() {
        addInputInfo(new ProcessorInputOutputInfo(INPUT_CONFIG, SCOPE_CONFIG_NAMESPACE_URI));
        addInputInfo(new ProcessorInputOutputInfo(INPUT_DATA));
    }

    public void start(org.orbeon.oxf.pipeline.api.PipelineContext context) {
        // Read data input into a SAXStore
        ScopeStore store = (ScopeStore) readCacheInputAsObject(context, getInputByName(INPUT_DATA), new CacheableInputReader() {
            public Object read(PipelineContext context, ProcessorInput input) {
                SAXStore saxStore = new SAXStore();
                readInputAsSAX(context, input, saxStore);
                return new ScopeStore(saxStore, getInputKey(context, input), getInputValidity(context, input));
            }
        });

        // Read config
        ContextConfig config = readConfig(context);

        // Store the SAX store in context
        ExternalContext externalContext = (ExternalContext) context.getAttribute(PipelineContext.EXTERNAL_CONTEXT);
        if (config.getContextType() == ScopeProcessorBase.REQUEST_CONTEXT) {
            externalContext.getRequest().getAttributesMap().put(config.getKey(), store);
        } else if (config.getContextType() == ScopeProcessorBase.SESSION_CONTEXT) {
            externalContext.getSession(true).getAttributesMap().put(config.getKey(), store);
        } else if (config.getContextType() == ScopeProcessorBase.APPLICATION_CONTEXT) {
            externalContext.getAttributesMap().put(config.getKey(), store);
        }
    }
}
