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

import org.orbeon.datatypes.LocationData;
import org.orbeon.errorified.Exceptions;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.common.OrbeonLocationException;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.ProcessorImpl;
import org.orbeon.oxf.processor.ProcessorInputOutputInfo;
import org.orbeon.oxf.processor.ProcessorOutput;
import org.orbeon.oxf.webapp.ProcessorService;
import org.orbeon.oxf.xml.XMLReceiver;
import org.orbeon.oxf.xml.XMLReceiverHelper;
import org.orbeon.datatypes.ExtendedLocationData;

import java.util.Iterator;
import java.util.List;

/**
 * ExceptionGenerator produces a structured XML document containing information about the
 * throwable stored into the PipelineContext.
 */
public class ExceptionGenerator extends ProcessorImpl {

    public ExceptionGenerator() {
        addOutputInfo(new ProcessorInputOutputInfo(OUTPUT_DATA));
    }

    @Override
    public ProcessorOutput createOutput(String name) {
        final ProcessorOutput output = new ProcessorOutputImpl(ExceptionGenerator.this, name) {
            public void readImpl(PipelineContext context, XMLReceiver xmlReceiver) {
                // Get top throwable
                Throwable throwable = (Throwable) context.getAttribute(ProcessorService.Throwable());

                final XMLReceiverHelper helper = new XMLReceiverHelper(xmlReceiver);
                helper.startDocument();
                helper.startElement("exceptions");

                // Write out document
                try {
                    while (throwable != null) {
                        addThrowable(helper, throwable, true);
                        throwable = Exceptions.getNestedThrowableOrNull(throwable);
                    }
                } catch (Exception e) {
                    throw new OXFException(e);
                }

                helper.endElement();
                helper.endDocument();
            }
        };
        addOutput(name, output);
        return output;
    }

    public static void addThrowable(XMLReceiverHelper helper, Throwable throwable, boolean stackTrace) {
        helper.startElement("exception");

        helper.element("type", throwable.getClass().getName());
        helper.element("message", throwable instanceof ValidationException
                ? ((ValidationException) throwable).message()
                : throwable.getMessage());

        addLocationData(helper, OrbeonLocationException.jGetAllLocationData(throwable));

        if (stackTrace) {
            final StackTraceElement[] elements = throwable.getStackTrace();

            // We were able to get a structured stack trace
            helper.startElement("stack-trace-elements");
            for (int i = 0; i < elements.length; i++) {
                final StackTraceElement element = elements[i];
                helper.startElement("element");
                helper.element("class-name", element.getClassName());
                helper.element("method-name", element.getMethodName());
                helper.element("file-name", element.getFileName());
                helper.element("line-number", element.getLineNumber());
                helper.endElement();
            }
            helper.endElement();
        }

        helper.endElement();
    }

    public static void addLocationData(XMLReceiverHelper helper, List locationDataList) {
        if (locationDataList != null) {
            for (Iterator i = locationDataList.iterator(); i.hasNext();) {
                final LocationData locationData = (LocationData) i.next();
                helper.startElement("location");
                helper.element("system-id", locationData.file());
                helper.element("line", Integer.toString(locationData.line()));
                helper.element("column", Integer.toString(locationData.col()));

                if (locationData instanceof ExtendedLocationData) {
                    final ExtendedLocationData extendedLocationData = (ExtendedLocationData) locationData;

                    final String description = extendedLocationData.getDescription();
                    if (description != null)
                        helper.element("description", description);

                    String elementString = extendedLocationData.getElementDebugString();
                    final String[] parameters = extendedLocationData.getParameters();
                    if (parameters.length > 0) {
                        helper.startElement("parameters");
                        for (int j = 0; j < parameters.length; j += 2) {
                            final String paramName = parameters[j];
                            final String paramValue = parameters[j + 1];

                            if (elementString == null && paramName.equals("element")) {
                                // Use "element" parameter as element string if present and not already set
                                elementString = paramValue;
                            } else {
                                // Just output the parameter
                                helper.startElement("parameter");
                                helper.element("name", paramName);
                                helper.element("value", paramValue);
                                helper.endElement();
                            }
                        }
                        helper.endElement();
                    }
                    // Output element string if set
                    if (elementString != null)
                        helper.element("element", elementString);
                }

                helper.endElement();
            }
        }
    }
}
