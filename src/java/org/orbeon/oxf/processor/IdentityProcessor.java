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
package org.orbeon.oxf.processor;

import org.orbeon.oxf.cache.OutputCacheKey;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.xml.sax.ContentHandler;

/**
 * The Identity processor produces on its data output the document received on the data input.
 */
public class IdentityProcessor extends ProcessorImpl {

    public IdentityProcessor() {
        addInputInfo(new ProcessorInputOutputInfo(INPUT_DATA));
        addOutputInfo(new ProcessorInputOutputInfo(OUTPUT_DATA));
    }

    public ProcessorOutput createOutput(String name) {
        ProcessorOutput output = new ProcessorImpl.ProcessorOutputImpl(getClass(), name) {
            public void readImpl(PipelineContext context, ContentHandler contentHandler) {
                readInputAsSAX(context, INPUT_DATA, contentHandler);
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
