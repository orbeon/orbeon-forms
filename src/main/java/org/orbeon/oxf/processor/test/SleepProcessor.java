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
package org.orbeon.oxf.processor.test;

import org.dom4j.Document;
import org.orbeon.oxf.cache.OutputCacheKey;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.pipeline.api.XMLReceiver;
import org.orbeon.oxf.processor.*;
import org.orbeon.oxf.processor.impl.ProcessorOutputImpl;
import org.orbeon.oxf.xml.SAXStore;

/**
 * This processor reads its data input, sleeps for a delay specified on its config input, and then sends the data input
 * to its data output. It behaves like an identity processor with a delay.
 */
public class SleepProcessor extends ProcessorImpl {

    public SleepProcessor() {
        addInputInfo(new ProcessorInputOutputInfo(INPUT_CONFIG));
        addInputInfo(new ProcessorInputOutputInfo(INPUT_DATA));
        addOutputInfo(new ProcessorInputOutputInfo(OUTPUT_DATA));
    }

    @Override
    public ProcessorOutput createOutput(String name) {
        final ProcessorOutput output = new ProcessorOutputImpl(SleepProcessor.this, name) {
            public void readImpl(PipelineContext context, XMLReceiver xmlReceiver) {
                try {
                    final SAXStore inputStore = new SAXStore();
                    readInputAsSAX(context, INPUT_DATA, inputStore);
                    final Document delayDocument = readInputAsDOM4J(context, INPUT_CONFIG);
                    final String delayString = (String) delayDocument.selectObject("string()");
                    Thread.sleep(Long.parseLong(delayString));
                    inputStore.replay(xmlReceiver);
                } catch (Exception e) {
                    throw new OXFException(e);
                }
            }

            @Override
            public OutputCacheKey getKeyImpl(PipelineContext pipelineContext) {
                return getInputKey(pipelineContext, getInputByName(INPUT_DATA));
            }

            @Override
            public Object getValidityImpl(PipelineContext context) {
                return getInputValidity(context, getInputByName(INPUT_DATA));
            }
        };
        addOutput(name, output);
        return output;
    }
}
