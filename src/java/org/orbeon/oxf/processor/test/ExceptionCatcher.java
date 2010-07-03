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
import org.orbeon.oxf.processor.generator.ExceptionGenerator;
import org.orbeon.oxf.processor.impl.ProcessorOutputImpl;
import org.orbeon.oxf.xml.ContentHandlerHelper;
import org.orbeon.oxf.xml.SAXStore;

/**
 * This processor has a data input and data output and behaves like the identity processor except if an exception is
 * thrown while reading its input, in which case it outputs the exception like the ExceptionGenerator does.
 */
public class ExceptionCatcher extends ProcessorImpl {

    public ExceptionCatcher() {
        addInputInfo(new ProcessorInputOutputInfo(INPUT_DATA));
        addOutputInfo(new ProcessorInputOutputInfo(OUTPUT_DATA));
    }

    @Override
    public ProcessorOutput createOutput(String name) {
        ProcessorOutput output = new ProcessorOutputImpl(ExceptionCatcher.this, name) {
            public void readImpl(PipelineContext context, XMLReceiver xmlReceiver) {
                try {
                    // Try to read input in SAX store
                    SAXStore dataInput = new SAXStore();
                    readInputAsSAX(context, getInputByName(INPUT_DATA), dataInput);
                    // No exception: output what was read
                    dataInput.replay(xmlReceiver);
                } catch (Throwable e) {
                    // Exception was thrown while reading input: generate a document with that exception
                    ContentHandlerHelper helper = new ContentHandlerHelper(xmlReceiver);
                    helper.startDocument();
                    String rootElementName = "exceptions";
                    helper.startElement(rootElementName);

                    // Find the root throwable
                    Throwable innerMostThrowable = e; {
                        while (true) {
                            Throwable candidate = OXFException.getNestedException(innerMostThrowable);
                            if (candidate == null) break;
                            else innerMostThrowable = candidate;
                        }
                    }

                    ExceptionGenerator.addThrowable(helper, innerMostThrowable);
                    helper.endElement();
                    helper.endDocument();
                }
            }
        };
        addOutput(name, output);
        return output;
    }
}
