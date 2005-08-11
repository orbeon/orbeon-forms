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
package org.orbeon.oxf.processor.generator;

import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.ProcessorImpl;
import org.orbeon.oxf.processor.ProcessorInputOutputInfo;
import org.orbeon.oxf.processor.ProcessorOutput;
import org.orbeon.oxf.xml.ContentHandlerHelper;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.xml.sax.ContentHandler;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * ExceptionGenerator produces a structured XML document containing information about the
 * throwable stored into the PipelineContext.
 */
public class ExceptionGenerator extends ProcessorImpl {

    private static final String ROOT_ELEMENT_NAME = "exceptions";
    private static final String LOCATION_DATA_ELEMENT_NAME = "location";
    private static final String EXCEPTION_ELEMENT_NAME = "exception";

    public ExceptionGenerator() {
        addOutputInfo(new ProcessorInputOutputInfo(OUTPUT_DATA));
    }

    public ProcessorOutput createOutput(String name) {
        ProcessorOutput output = new ProcessorOutputImpl(getClass(), name) {
            public void readImpl(PipelineContext context, ContentHandler contentHandler) {
                // Get top throwable
                Throwable throwable = (Throwable) context.getAttribute(PipelineContext.THROWABLE);
                // Throwable is mandatory
                if (throwable == null)
                    throw new OXFException("Missing throwable object in ExceptionGenerator");
                // Write out document
                try {
                    final ContentHandlerHelper helper = new ContentHandlerHelper(contentHandler);
                    helper.startDocument();
                    helper.startElement(ROOT_ELEMENT_NAME);

                    helper.startElement(LOCATION_DATA_ELEMENT_NAME);
                    addLocationData(helper, ValidationException.getRootLocationData(throwable));
                    helper.endElement();

                    while (throwable != null) {
                        addThrowable(helper, throwable);
                        throwable = OXFException.getNestedException(throwable);
                    }

                    helper.endElement();
                    helper.endDocument();
                } catch (Exception e) {
                    throw new OXFException(e);
                }
            }
        };
        addOutput(name, output);
        return output;
    }

    public static void addThrowable(ContentHandlerHelper helper, Throwable throwable) {
        helper.startElement(EXCEPTION_ELEMENT_NAME);

        helper.element("type", throwable.getClass().getName());
        helper.element("message", throwable instanceof ValidationException
                ? ((ValidationException) throwable).getSimpleMessage()
                : throwable.getMessage());

        addLocationData(helper, ValidationException.getLocationData(throwable));

        final OXFException.StackTraceElement[] elements = OXFException.getStackTraceElements(throwable);

        if (elements != null) {
            // We were able to get a structured stack trace
            helper.startElement("stack-trace-elements");
            for (int i = 0; i < elements.length; i++) {
                final OXFException.StackTraceElement element = elements[i];
                helper.startElement("element");
                helper.element("class-name", element.getClassName());
                helper.element("method-name", element.getMethodName());
                helper.element("file-name", element.getFileName());
                helper.element("line-number", element.getLineNumber());
                helper.endElement();
            }
            helper.endElement();
        } else {
            // Just output the String version of the stack trace
            final StringWriter stringWriter = new StringWriter();
            final PrintWriter printWriter = new PrintWriter(stringWriter);
            throwable.printStackTrace(printWriter);
            helper.element("stack-trace", stringWriter.toString());
        }

        helper.endElement();
    }

    public static void addLocationData(ContentHandlerHelper helper, LocationData locationData) {
        if (locationData != null) {
            helper.element("system-id", locationData.getSystemID());
            helper.element("line", Integer.toString(locationData.getLine()));
            helper.element("column", Integer.toString(locationData.getCol()));
        }
    }
}
