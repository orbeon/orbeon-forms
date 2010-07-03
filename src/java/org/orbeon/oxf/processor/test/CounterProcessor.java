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

import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.pipeline.api.XMLReceiver;
import org.orbeon.oxf.processor.*;
import org.orbeon.oxf.processor.impl.CacheableTransformerOutputImpl;
import org.orbeon.oxf.xml.XMLUtils;
import org.xml.sax.SAXException;

/**
 * This processor returns a new value each time it its output is being read. This processor is
 * designed to be used in tests that want to get that an input is read only once.
 */
public class CounterProcessor extends ProcessorImpl {

    private int counter = 0;

    public CounterProcessor() {
        addOutputInfo(new ProcessorInputOutputInfo(OUTPUT_DATA));
    }

    @Override
    public ProcessorOutput createOutput(String name) {
        final ProcessorOutput output = new CacheableTransformerOutputImpl(CounterProcessor.this, name) {
            public void readImpl(PipelineContext context, XMLReceiver xmlReceiver) {
                try {
                    counter++;
                    xmlReceiver.startDocument();
                    xmlReceiver.startElement("", "counter", "counter", XMLUtils.EMPTY_ATTRIBUTES);
                    final String counterString = Integer.toString(counter);
                    xmlReceiver.characters(counterString.toCharArray(), 0, counterString.length());
                    xmlReceiver.endElement("", "counter", "counter");
                    xmlReceiver.endDocument();
                } catch (SAXException e) {
                    throw new OXFException(e);
                }
            }
        };
        addOutput(name, output);
        return output;
    }
}
