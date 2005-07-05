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

import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.xml.NamingConvention;
import org.xml.sax.ContentHandler;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * This is the class that must be extended to create a custom processor. See the <a
 * href="http://www.orbeon.com/ops/doc/reference-processor-api">Processor API section</a> in the OXF
 * Manual for more information.
 */
public abstract class SimpleProcessor extends ProcessorImpl {

    private final String GENERATE = "generate";

    /**
     * Maps: (String outputName -> Method)
     */
    private Map outputToMethod;

    /**
     * Iterate through the <code>generateXXX</code> methods and stores a reference to the method in
     * <code>outputToMethod</code>. This constructor must be called by subclasses.
     */
    public SimpleProcessor() {
        outputToMethod = new HashMap();
        Method[] methods = getClass().getDeclaredMethods();
        for (int i = 0; i < methods.length; i++) {
            Method method = methods[i];
            Class[] parameterTypes = method.getParameterTypes();
            if (method.getName().startsWith(GENERATE) && parameterTypes.length == 2
                    && parameterTypes[0].equals(org.orbeon.oxf.pipeline.api.PipelineContext.class)
                    && parameterTypes[1].equals(ContentHandler.class)) {
                String javaName = method.getName().substring(GENERATE.length());
                outputToMethod.put(NamingConvention.javaToXMLName(javaName), method);
            }
        }
    }

    /**
     * Delegates processing to the appropriate generateXXX method.
     */
    public ProcessorOutput createOutput(final String name) {
        ProcessorOutput output = new ProcessorImpl.ProcessorOutputImpl(getClass(), name) {
            public void readImpl(org.orbeon.oxf.pipeline.api.PipelineContext context, ContentHandler contentHandler) {
                try {
                    if (outputToMethod == null)
                        throw new OXFException("Subclass of SimpleProcessor has to call superclass constructor");
                    Method method = (Method) outputToMethod.get(name);
                    if (method == null)
                        throw new OXFException("Cannot find \"generate\" method for output \"" + name
                                + "\" in class \"" + SimpleProcessor.this.getClass().getName() + "\"");
                    method.invoke(SimpleProcessor.this, new Object[]{context, contentHandler});
                } catch (Exception e) {
                    throw new OXFException(e);
                }
            }
        };
        addOutput(name, output);
        return output;
    }
}
