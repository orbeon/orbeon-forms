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
package org.orbeon.oxf.processor.generator;

import org.orbeon.oxf.cache.OutputCacheKey;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.pipeline.api.XMLReceiver;
import org.orbeon.oxf.processor.ProcessorImpl;
import org.orbeon.oxf.processor.ProcessorOutput;
import org.orbeon.oxf.processor.impl.ProcessorOutputImpl;
import org.orbeon.oxf.xml.SAXStore;
import org.xml.sax.SAXException;

public class SAXStoreGenerator extends ProcessorImpl {

    private SAXStore saxStore;
    private OutputCacheKey key;
    private Object validity;

    public SAXStoreGenerator(SAXStore saxStore) {
        this.saxStore = saxStore;
    }

    public SAXStoreGenerator(SAXStore saxStore, OutputCacheKey key, Object validity) {
        this(saxStore);
        this.key = key;
        this.validity = validity;
    }

    @Override
    public ProcessorOutput createOutput(String name) {
        final ProcessorOutput output = new ProcessorOutputImpl(SAXStoreGenerator.this, name) {
            public void readImpl(PipelineContext pipelineContext, XMLReceiver xmlReceiver) {
                try {
                    if (saxStore != null) {
                        saxStore.replay(xmlReceiver);
                    } else {
                        throw new OXFException("SAXStore is not set on SAXStoreGenerator");
                    }
                } catch (SAXException e) {
                    throw new OXFException(e);
                }
            }

            @Override
            public OutputCacheKey getKeyImpl(PipelineContext pipelineContext) {
                return key;
            }

            @Override
            public Object getValidityImpl(PipelineContext pipelineContext) {
                return validity;
            }
        };
        addOutput(name, output);
        return output;
    }
}
