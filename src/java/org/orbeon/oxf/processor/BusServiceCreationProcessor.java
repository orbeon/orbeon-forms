/*
 * Copyright 1998-2002, Orbeon, Inc.
 * All Rights Reserved
 *
 * This is unpublished proprietary source code of Orbeon, Inc. The
 * copyright notice above does not evidence any actual or intended
 * publication of such source code.
 */
package org.orbeon.oxf.processor;

/*
import org.apache.axis.AxisFault;
import org.apache.axis.message.SOAPBodyElement;
import org.apache.axis.message.SOAPEnvelope;
import org.dom4j.Document;
import org.dom4j.XPath;
import org.dom4j.DocumentHelper;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.generator.DOMGenerator;
import org.orbeon.oxf.processor.pipeline.PipelineProcessor;
import org.orbeon.oxf.processor.pipeline.PipelineReader;
import org.orbeon.oxf.util.JMSUtils;
import org.orbeon.oxf.util.PipelineUtils;
import org.orbeon.oxf.util.XPathCache;
import org.orbeon.oxf.xml.XPathUtils;
import org.orbeon.oxf.cache.OutputCacheKey;
import org.jaxen.SimpleNamespaceContext;
import org.jaxen.NamespaceContext;
import org.xml.sax.ContentHandler;

import javax.jms.*;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Vector;
*/

public class BusServiceCreationProcessor {}
/*
public class BusServiceCreationProcessor extends ProcessorImpl {

    private static final String INPUT_PIPELINE = "pipeline";

    // XPath expressions used to extract information from config
    private static final XPath nameXPath;
    static {
        Map namespaceContextMap = new HashMap();
        namespaceContextMap.put("x", "http://www.orbeon.com/oxf/xml-server");
        NamespaceContext namespaceContext = new SimpleNamespaceContext(namespaceContextMap);

        nameXPath = DocumentHelper.createXPath("string(/x:service/x:binding[@type = 'bus']/@name)");
        nameXPath.setNamespaceContext(namespaceContext);
    }

    public BusServiceCreationProcessor() {
        addInputInfo(new ProcessorInputOutputInfo(INPUT_CONFIG));
        addInputInfo(new ProcessorInputOutputInfo(INPUT_PIPELINE));
    }

    public void start(PipelineContext context) {

        try {
            Document config = readInputAsDOM4J(context, INPUT_DATA);

            // Read pipeline from data input
            PipelineReader pipelineReader = new PipelineReader();
            ProcessorInput pipelineReaderInput = pipelineReader.createInput("pipeline");
            pipelineReaderInput.setOutput(new ProcessorImpl.ProcessorOutputImpl(getClass(), "dummy") {
                public void readImpl(PipelineContext context, ContentHandler contentHandler) {
                    BusServiceCreationProcessor.this.readInputAsSAX(context, INPUT_PIPELINE, contentHandler);
                }

                public OutputCacheKey getKeyImpl(PipelineContext context) {
                    return getInputKey(context, INPUT_PIPELINE);
                }

                public Object getValidityImpl(PipelineContext context) {
                    return getInputValidity(context, _configInput);
                }

            });

            // Create queue for service
            String queueJNDIName = JMSUtils.JNDI_SERVICE_PREFIX + nameXPath.evaluate(config);
            Queue queue = JMSUtils.createQueue(queueJNDIName);


            // Create connection and session
            QueueConnection queueConnection = JMSUtils.getQueueConnection();
            QueueSession queueSession = queueConnection.createQueueSession(false, Session.AUTO_ACKNOWLEDGE);;
            QueueReceiver queueReceiver = queueSession.createReceiver(queue);
            queueReceiver.setMessageListener(new BusServiceMessageListener());
        } catch (JMSException e) {
            throw new OXFException(e);
        }
    }

    private static class BusServiceMessageListener implements MessageListener {

        private

        public void onMessage(Message message) {
            try {
                // Create pipeline and connect config
                Processor pipelineSource = PipelineUtils.createDOMGenerator()URLGenerator(service.getImplementation());
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
                    DOMGenerator domGenerator = new DOMGenerator(soapBodyElement.getAsDocument(), new Long(0));
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
    }
}
*/