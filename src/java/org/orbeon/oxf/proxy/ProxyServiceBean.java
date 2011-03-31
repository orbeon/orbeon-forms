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
package org.orbeon.oxf.proxy;

import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.*;
import org.orbeon.oxf.processor.generator.SAXStoreGenerator;
import org.orbeon.oxf.util.PipelineUtils;
import org.orbeon.oxf.webapp.ProcessorService;
import org.orbeon.oxf.xml.SAXStore;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import java.util.*;

public class ProxyServiceBean extends SessionBeanAdapter {

    private String jndiName;
    private Map inputs;

    public void setJNDIName(String jndiName) {
        this.jndiName = jndiName;
    }

    public void setInputs(Map inputs) {
        this.inputs = inputs;
    }

    public Map getOutputs() {

        try {
            // Create processor
            final Context jndiContext = new InitialContext();
            final PipelineContext pipelineContext = new PipelineContext();
            boolean success = false;
            try {
                pipelineContext.setAttribute(ProcessorService.JNDI_CONTEXT, jndiContext);
                final Processor processor = ProcessorFactoryRegistry.lookup(jndiName).createInstance();

                // Connect inputs
                for (Iterator i = inputs.keySet().iterator(); i.hasNext();) {
                    final String inputName = (String) i.next();
                    final List inputsForName = (List) inputs.get(inputName);
                    for (Iterator j = inputsForName.iterator(); j.hasNext();) {
                        final SAXStore saxStore = (SAXStore) j.next();
                        final SAXStoreGenerator generator = new SAXStoreGenerator(saxStore);
                        PipelineUtils.connect(generator, ProcessorImpl.INPUT_DATA, processor, inputName);
                    }
                }

                // Connect outputs
                final Map outputs = new HashMap();
                final List serializers = new ArrayList();
                for (Iterator i = processor.getOutputsInfo().iterator(); i.hasNext();) {
                    final ProcessorInputOutputInfo outputInfo = (ProcessorInputOutputInfo) i.next();

                    // Save SAX Store in outputs
                    final SAXStoreSerializer saxStoreSerializer = new SAXStoreSerializer();
                    List outputsForName = (List) outputs.get(outputInfo.getName());
                    if (outputsForName == null) {
                        outputsForName = new ArrayList();
                        outputs.put(outputInfo.getName(), outputsForName);
                    }
                    outputsForName.add(saxStoreSerializer.getSAXStore());

                    // Connect serializer
                    PipelineUtils.connect(processor, outputInfo.getName(), saxStoreSerializer, ProcessorImpl.INPUT_DATA);
                    serializers.add(saxStoreSerializer);
                }

                // Run serializers or start processor
                if (serializers.size() > 0) {
                    for (Iterator i = serializers.iterator(); i.hasNext();) {
                        final SAXStoreSerializer serializer = (SAXStoreSerializer) i.next();
                        serializer.start(pipelineContext);
                    }
                } else {
                    processor.start(pipelineContext);
                }
                success = true;

                return outputs;
            } finally {
                pipelineContext.destroy(success);
            }

        } catch (NamingException e) {
            throw new OXFException(e);
        }
    }
}
