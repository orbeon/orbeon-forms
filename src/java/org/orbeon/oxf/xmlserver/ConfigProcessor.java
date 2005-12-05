/*
 * Copyright 1998-2002, Orbeon, Inc.
 * All Rights Reserved
 *
 * This is unpublished proprietary source code of Orbeon, Inc. The
 * copyright notice above does not evidence any actual or intended
 * publication of such source code.
 */
package org.orbeon.oxf.xmlserver;

import org.dom4j.Document;
import org.dom4j.Element;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.DOMSerializer;
import org.orbeon.oxf.processor.Processor;
import org.orbeon.oxf.processor.ProcessorImpl;
import org.orbeon.oxf.resources.URLFactory;
import org.orbeon.oxf.servicedirectory.ServiceDefinition;
import org.orbeon.oxf.servicedirectory.ServiceDirectory;
import org.orbeon.oxf.util.PipelineUtils;
import org.orbeon.oxf.xml.dom4j.LocationData;

import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class ConfigProcessor extends ProcessorImpl {

    public void start(PipelineContext context) {

        try {
            final Document xmlServerConfigDocument = readInputAsDOM4J(context, INPUT_CONFIG);
            final LocationData locationData = (LocationData) xmlServerConfigDocument.getRootElement().getData();
            final String xmlServerConfigContext = locationData == null ? null : locationData.getSystemID();
            final State state = (State) getState(context);

            // Go through all services
            for (Iterator i = xmlServerConfigDocument.getRootElement().elements("service").iterator(); i.hasNext();) {
                final Element serviceElement = (Element) i.next();
                final String implementation = serviceElement.attributeValue("implementation");

                // Read pipeline into document
                final Document pipelineDocument;
                {
                    Processor pipelineURLGenerator = PipelineUtils.createURLGenerator
                            (URLFactory.createURL(xmlServerConfigContext, implementation).toExternalForm(), true);
                    DOMSerializer pipelineDOMSerializer = new DOMSerializer();
                    PipelineUtils.connect(pipelineURLGenerator, OUTPUT_DATA, pipelineDOMSerializer, INPUT_DATA);
                    pipelineDOMSerializer.start(context);
                    pipelineDocument = pipelineDOMSerializer.getDocument(context);
                }

                // Retrieve input and output names
                final List inputNames = new ArrayList();
                final List outputNames = new ArrayList();
                for (Iterator j = pipelineDocument.getRootElement().elements("param").iterator(); j.hasNext();) {
                    Element paramElement = (Element) j.next();
                    ("input".equals(paramElement.attributeValue("type")) ? inputNames : outputNames).add
                            (paramElement.attributeValue("name"));
                }

                // Go though all bindings for current service
                for (Iterator j = serviceElement.elements("binding").iterator(); j.hasNext();) {
                    Element bindingElement = (Element) j.next();
                    if ("bus".equals(bindingElement.attributeValue("type"))) {
                        String name = bindingElement.attributeValue("name");
                        state.xmlServerServiceDefinitions.add(new XMLServerServiceDefinition
                                (name, implementation, inputNames, outputNames));
                        ServiceDirectory.instance().addServiceDefinition
                                (new ServiceDefinition(name, !outputNames.isEmpty()));
                    }
                }
            }
        } catch (MalformedURLException e) {
            throw new OXFException(e);
        }
    }

    public List getXMLServerServiceDefinitions(PipelineContext context) {
        return ((State) getState(context)).xmlServerServiceDefinitions;
    }

    public void reset(final PipelineContext context) {
        setState(context, new State());
    }

    private static class State {
        public List xmlServerServiceDefinitions = new ArrayList();
    }
}
