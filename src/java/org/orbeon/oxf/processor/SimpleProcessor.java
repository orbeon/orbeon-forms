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
package org.orbeon.oxf.processor;

import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.pipeline.api.XMLReceiver;
import org.orbeon.oxf.processor.impl.ProcessorOutputImpl;
import org.orbeon.oxf.xml.NamingConvention;
import org.xml.sax.ContentHandler;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * This is the class that must be extended to create a custom processor. See the <a
 * href="http://www.orbeon.com/ops/doc/reference-processor-api">Processor API section</a>  for more information.
 */
public abstract class SimpleProcessor extends ProcessorImpl {

    private static final String GENERATE = "generate";

    private Map<String, Method> outputToMethod = new HashMap<String, Method>();

    /**
     * This constructor must be called by subclasses.
     *
     * Iterate through the generateXxx() methods and stores a reference to the method in outputToMethod.
     *
     * This checks for the following signatures:
     *
     *  generateXxx(PipelineContext context, XMLReceiver xmlReceiver)
     *  generateXxx(PipelineContext context, ContentHandler contentHandler)
     *
     * For a given "Xxx", only one of the two methods should be implemented. If both are present, the ContentHandler
     * version is ignored.
     */
    public SimpleProcessor() {
        final Method[] methods = getClass().getDeclaredMethods();
        for (final Method method : methods) {
            final Class[] parameterTypes = method.getParameterTypes();
            if (method.getName().startsWith(GENERATE) && parameterTypes.length == 2
                    && parameterTypes[0].equals(PipelineContext.class)
                    && (parameterTypes[1].equals(XMLReceiver.class) || parameterTypes[1].equals(ContentHandler.class))) {
                final String javaName = method.getName().substring(GENERATE.length());
                outputToMethod.put(NamingConvention.javaToXMLName(javaName), method);
            }
        }
    }

    /**
     * Create an output. Delegate processing to the appropriate generateXXX() method.
     */
    @Override
    public ProcessorOutput createOutput(final String name) {
        final ProcessorOutput output = new ProcessorOutputImpl(SimpleProcessor.this, name) {
            public void readImpl(PipelineContext context, XMLReceiver xmlReceiver) {
                try {
                    final Method method = outputToMethod.get(name);
                    if (method == null)
                        throw new OXFException("Cannot find \"generate\" method for output \"" + name
                                + "\" in class \"" + SimpleProcessor.this.getClass().getName() + "\"");
                    method.invoke(SimpleProcessor.this, context, xmlReceiver);
                } catch (Exception e) {
                    throw new OXFException(e);
                }
            }
        };
        addOutput(name, output);
        return output;
    }
}
