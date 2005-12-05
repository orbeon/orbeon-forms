/*
 * Copyright 1998-2002, Orbeon, Inc.
 * All Rights Reserved
 *
 * This is unpublished proprietary source code of Orbeon, Inc. The
 * copyright notice above does not evidence any actual or intended
 * publication of such source code.
 */
package org.orbeon.oxf.xmlserver;

import org.apache.axis.AxisFault;
import org.apache.axis.message.SOAPBodyElement;
import org.apache.axis.message.SOAPEnvelope;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.DOMSerializer;
import org.orbeon.oxf.processor.Processor;
import org.orbeon.oxf.processor.ProcessorImpl;
import org.orbeon.oxf.processor.generator.DOMGenerator;
import org.orbeon.oxf.processor.pipeline.PipelineProcessor;
import org.orbeon.oxf.util.JMSUtils;
import org.orbeon.oxf.util.PipelineUtils;
import org.w3c.dom.Document;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import javax.jms.*;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

public class ContextListener implements ServletContextListener {

    private static String XML_SERVER_CONFIG = "oxf.xml-server.config";

    public void contextInitialized(ServletContextEvent event) {

        try {
            try {
                // Get list of services (bus service registry)
                final List xmlServerServiceDefinitions;
                {
                    Processor configSource = PipelineUtils.createURLGenerator
                            (event.getServletContext().getInitParameter(XML_SERVER_CONFIG), true);
                    ConfigProcessor serverConfigProcessor = new ConfigProcessor();
                    PipelineUtils.connect(configSource, ProcessorImpl.OUTPUT_DATA,
                            serverConfigProcessor, ProcessorImpl.INPUT_CONFIG);
                    PipelineContext context = new PipelineContext();
                    serverConfigProcessor.reset(context);
                    serverConfigProcessor.start(context);
                    xmlServerServiceDefinitions = serverConfigProcessor.getXMLServerServiceDefinitions(context);
                }

                QueueConnection queueConnection = null;
                QueueSession queueSession = null;
                try {
                    // Create connection and session
                    queueConnection = JMSUtils.getQueueConnection();
                    final QueueConnection finalQueueConnection = queueConnection;
                    queueSession = queueConnection.createQueueSession(false, Session.AUTO_ACKNOWLEDGE);


                    for (Iterator i = xmlServerServiceDefinitions.iterator(); i.hasNext();) {
                        final XMLServerServiceDefinition service = (XMLServerServiceDefinition) i.next();

                        // Create queue for service
                        String queueJNDIName = JMSUtils.JNDI_SERVICE_PREFIX + service.getName();
                        Queue queue = JMSUtils.createQueue(queueJNDIName);

                        // Add listener on queue
                        QueueReceiver queueReceiver = queueSession.createReceiver(queue);
                        queueReceiver.setMessageListener(new MessageListener() {
                            public void onMessage(Message message) {
                                try {
                                    // Create pipeline and connect config
                                    Processor pipelineSource = PipelineUtils.createURLGenerator(service.getImplementation(), true);
                                    PipelineProcessor pipelineProcessor = new PipelineProcessor();
                                    PipelineUtils.connect(pipelineSource, ProcessorImpl.OUTPUT_DATA,
                                            pipelineProcessor, ProcessorImpl.INPUT_CONFIG);

                                    // Connect other inputs from envelope to pipeline
                                    SOAPEnvelope soapEnvelope = (SOAPEnvelope) ((ObjectMessage) message).getObject();
                                    Vector bodyElements = soapEnvelope.getBodyElements();
                                    if (bodyElements.size() != service.getInputNames().size())
                                        throw new OXFException("Service expects " + service.getInputNames().size()
                                                + " input documents, while " + bodyElements.size() + " have been provided");
                                    for (int j = 0; j < bodyElements.size(); j++) {
                                        SOAPBodyElement soapBodyElement = (SOAPBodyElement) bodyElements.elementAt(j);
                                        final Document d = soapBodyElement.getAsDocument();
                                        final DOMGenerator domGenerator = new DOMGenerator
                                            ( d, "soap body", DOMGenerator.ZeroValidity
                                              , DOMGenerator.DefaultContext ) ;
                                        PipelineUtils.connect(domGenerator, ProcessorImpl.OUTPUT_DATA,
                                                pipelineProcessor, (String) service.getInputNames().get(j));
                                    }
                                    PipelineContext pipelineContext = new PipelineContext();
                                    pipelineProcessor.reset(pipelineContext);

                                    // Connect outputs to DOM serializers and start serializers
                                    Map outputSerializers = new HashMap();
                                    for (Iterator j = service.getOutputNames().iterator(); j.hasNext();) {
                                        String outputName = (String) j.next();
                                        DOMSerializer domSerializer = new DOMSerializer();
                                        PipelineUtils.connect(pipelineProcessor, outputName, domSerializer, ProcessorImpl.OUTPUT_DATA);
                                        outputSerializers.put(outputName, domSerializer);
                                        domSerializer.start(pipelineContext);
                                    }

                                    // Run pipeline if no serializer
                                    if (service.getOutputNames().isEmpty())
                                        pipelineProcessor.start(pipelineContext);

                                    // Send back reponse
                                    if (message.getJMSReplyTo() != null && !service.getOutputNames().isEmpty()) {

                                        // Create reponse envelope
                                        SOAPEnvelope responseEnvelope = new SOAPEnvelope();
                                        for (Iterator j = service.getOutputNames().iterator(); j.hasNext();) {
                                            String outputName = (String) j.next();
                                            DOMSerializer domSerializer = (DOMSerializer) outputSerializers.get(outputName);
                                            SOAPBodyElement soapBodyElement = new SOAPBodyElement
                                                    (domSerializer.getW3CDocument(pipelineContext).getDocumentElement());
                                            responseEnvelope.addBodyElement(soapBodyElement);
                                        }

                                        // Send it to JMSReployTo destination
                                        QueueSession queueSession = null;
                                        QueueSender queueSender = null;
                                        try {
                                            queueSession = finalQueueConnection.createQueueSession(false, Session.AUTO_ACKNOWLEDGE);
                                            queueSender = queueSession.createSender((Queue) message.getJMSReplyTo());
                                            ObjectMessage responseMessage = queueSession.createObjectMessage();
                                            responseMessage.setObject(responseEnvelope);
                                            queueSender.send(responseMessage);
                                        } finally {
                                            if (queueSender != null) queueSender.close();
                                            if (queueSession != null) queueSession.close();
                                        }
                                    }
                                } catch (JMSException e) {
                                    throw new OXFException(e);
                                } catch (AxisFault axisFault) {
                                    throw new OXFException(axisFault);
                                } catch (Exception e) {
                                    throw new OXFException(e);
                                }
                            }
                        });
                    }
                    queueConnection.start();
                } finally {
//                    if (queueSession != null) queueSession.close();
//                    if (queueConnection != null) queueConnection.close();
                }
            } catch (JMSException e) {
                throw new OXFException(e);
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new OXFException(e);
        }
    }

    public void contextDestroyed(ServletContextEvent event) {
    }
}
