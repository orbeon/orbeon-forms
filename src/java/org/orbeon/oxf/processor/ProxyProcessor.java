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
import org.orbeon.oxf.processor.impl.CacheableTransformerOutputImpl;
import org.orbeon.oxf.proxy.ProxyServiceDelegate;
import org.orbeon.oxf.webapp.ProcessorService;
import org.orbeon.oxf.xml.SAXStore;
import org.xml.sax.SAXException;

import javax.naming.Context;
import java.util.*;

public class ProxyProcessor extends ProcessorImpl implements ProcessorFactory {

    private String jndiName;
    private List inputs;
    private List outputs;

    public void setJNDIName(String jndiName) {
        this.jndiName = jndiName;
    }

    public void setInputs(List inputs) {
        this.inputs = inputs;
    }

    public void setOutputs(List outputs) {
        this.outputs = outputs;
    }


    public Processor createInstance() {
        return new ConcreteProxyProcessor();
    }

    private class ConcreteProxyProcessor extends ProcessorImpl {

        private final String PROXY_SERVICE_JNDI_NAME = "java:comp/env/ejb/oxf/proxy";
        private boolean started;
        // Map: (String name -> List[SAXStore])
        private Map saxStores;

        public ConcreteProxyProcessor() {
            for (int i = 0; i < 2; i++) {
                List list = (i == 0 ? inputs : outputs);

                // Go through list and populate map
                Map map = new HashMap();
                for (Iterator j = list.iterator(); j.hasNext();) {
                    String name = ((org.orbeon.oxf.processor.pipeline.ast.ASTInputOutput) j.next()).getName();
                    Integer count = map.containsKey(name)
                            ? new Integer(((Integer) map.get(name)).intValue() + 1)
                            : new Integer(1);
                    map.put(name, count);
                }

                // Go through map and create sockets
                for (Iterator j = map.keySet().iterator(); j.hasNext();) {
                    String name = (String) j.next();
                    if (i == 0) {
                        addInputInfo(new ProcessorInputOutputInfo(name));
                    } else {
                        addOutputInfo(new ProcessorInputOutputInfo(name));
                    }
                }
            }
        }

        @Override
        public ProcessorOutput createOutput(String name) {
            final String _name = name;
            final ProcessorOutput output = new CacheableTransformerOutputImpl(ProxyProcessor.this, name) {
                public void readImpl(PipelineContext context, XMLReceiver xmlReceiver) {
                    try {
                        if (!started)
                            start(context);
                        if (saxStores.get(_name) == null || ((List) saxStores.get(_name)).isEmpty())
                            throw new OXFException("Not enough or no output with name \"" + _name + "\"");
                        List outputsForName = (List) saxStores.get(_name);
                        SAXStore saxStore = (SAXStore) outputsForName.get(0);
                        saxStore.replay(xmlReceiver);
                        outputsForName.remove(0);
                    } catch (SAXException e) {
                        throw new OXFException(e);
                    }
                }
            };
            addOutput(name, output);
            return output;
        }

        @Override
        public void start(org.orbeon.oxf.pipeline.api.PipelineContext context) {
            if (started)
                throw new OXFException("Concrete ProxyService Processor already started");

            // Serialize all inputs in SAX Stores and populate inputs
            Map inputs = new HashMap();
            for (Iterator i = getInputsInfo().iterator(); i.hasNext();) {
                ProcessorInputOutputInfo inputInfo = (ProcessorInputOutputInfo) i.next();
                List inputsForName = new ArrayList();
                inputs.put(inputInfo.getName(), inputsForName);
                SAXStore saxStore = new SAXStore();
                inputsForName.add(saxStore);
                readInputAsSAX(context, inputInfo.getName(), saxStore);
            }

            // Call EJB to get outputs
            Context jndiContext = (Context) context.getAttribute(ProcessorService.JNDI_CONTEXT);
            ProxyServiceDelegate proxyService = new ProxyServiceDelegate(jndiContext);
            proxyService.setJNDIName(jndiName);
            proxyService.setInputs(inputs);
            saxStores = proxyService.getOutputs();
            started = true;
        }

        @Override
        public void reset(org.orbeon.oxf.pipeline.api.PipelineContext context) {
            started = false;
        }
    }
}
