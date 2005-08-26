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
package org.orbeon.oxf.processor.test;

import org.orbeon.oxf.processor.ProcessorInputOutputInfo;
import org.orbeon.oxf.processor.ProcessorImpl;
import org.orbeon.oxf.processor.ProcessorOutput;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.cache.OutputCacheKey;
import org.orbeon.oxf.common.OXFException;
import org.xml.sax.ContentHandler;
import org.dom4j.Document;

public class SleepProcessor extends ProcessorImpl {

    public SleepProcessor() {
        addInputInfo(new ProcessorInputOutputInfo(INPUT_CONFIG));
        addInputInfo(new ProcessorInputOutputInfo(INPUT_DATA));
        addOutputInfo(new ProcessorInputOutputInfo(OUTPUT_DATA));
    }

    public ProcessorOutput createOutput(String name) {
        ProcessorOutput output = new ProcessorImpl.ProcessorOutputImpl(getClass(), name) {
            public void readImpl(PipelineContext context, ContentHandler contentHandler) {
                try {
                    Document delayDocument = readInputAsDOM4J(context, INPUT_CONFIG);
                    String delayString = (String) delayDocument.selectObject("string()");
                    Thread.sleep(Long.parseLong(delayString));
                    readInputAsSAX(context, INPUT_DATA, contentHandler);
                } catch (InterruptedException e) {
                    throw new OXFException(e);
                }
            }

            public OutputCacheKey getKeyImpl(PipelineContext context) {
                return getInputKey(context, getInputByName(INPUT_DATA));
            }

            public Object getValidityImpl(PipelineContext context) {
                return getInputValidity(context, getInputByName(INPUT_DATA));
            }
        };
        addOutput(name, output);
        return output;
    }
}
