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

public class ExceptionGenerator extends ProcessorImpl {

    public ExceptionGenerator() {
        addOutputInfo(new ProcessorInputOutputInfo(OUTPUT_DATA));
    }

    public ProcessorOutput createOutput(String name) {
        ProcessorOutput output = new ProcessorOutputImpl(getClass(), name) {
            public void readImpl(PipelineContext context, ContentHandler contentHandler) {
                Throwable throwable = (Throwable) context.getAttribute(PipelineContext.THROWABLE);
                LocationData locationData = (LocationData) context.getAttribute(PipelineContext.LOCATION_DATA);
                if (throwable == null)
                    throw new OXFException("Missing throwable object in ExceptionGenerator");
                try {
                    ContentHandlerHelper helper = new ContentHandlerHelper(contentHandler);
                    helper.startDocument();
                    String rootElementName = "exceptions";
                    helper.startElement(rootElementName);
                    addThrowable(helper, throwable, locationData);
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

    protected void addThrowable(ContentHandlerHelper helper, Throwable throwable, LocationData locationData) throws Exception {
        String exceptionElementName = "exception";
        helper.startElement(exceptionElementName);

        helper.element("type", throwable.getClass().toString());
        helper.element("message", throwable instanceof ValidationException
                ? ((ValidationException) throwable).getSimpleMessage()
                : throwable.getMessage());

        if (locationData != null) {
            helper.element("system-id", locationData.getSystemID());
            helper.element("line", Integer.toString(locationData.getLine()));
            helper.element("column", Integer.toString(locationData.getCol()));
        }

        OXFException.StackTraceElement[] elements = OXFException.getStackTraceElements(throwable);

        if (elements != null) {
            // We were able to get a structured stack trace
            helper.startElement("stack-trace-elements");
            for (int i = 0; i < elements.length; i++) {
                OXFException.StackTraceElement element = elements[i];
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
            StringWriter stringWriter = new StringWriter();
            PrintWriter printWriter = new PrintWriter(stringWriter);
            throwable.printStackTrace(printWriter);
            helper.element("stack-trace", stringWriter.toString());
        }

        helper.endElement();
    }
}
