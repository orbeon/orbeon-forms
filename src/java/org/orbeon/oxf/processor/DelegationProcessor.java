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

import org.apache.axis.client.Call;
import org.apache.axis.client.Service;
import org.apache.axis.message.MessageElement;
import org.apache.axis.message.PrefixedQName;
import org.apache.axis.message.SOAPBodyElement;
import org.apache.axis.message.SOAPEnvelope;
import org.apache.axis.soap.SOAPConstants;
import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.Text;
import org.dom4j.io.DOMReader;
import org.dom4j.io.DOMWriter;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.processor.generator.SAXStoreGenerator;
import org.orbeon.oxf.servicedirectory.ServiceDirectory;
import org.orbeon.oxf.util.JMSUtils;
import org.orbeon.oxf.util.PipelineUtils;
import org.orbeon.oxf.util.PooledXPathExpression;
import org.orbeon.oxf.util.XPathCache;
import org.orbeon.oxf.xml.ForwardingContentHandler;
import org.orbeon.oxf.xml.SAXStore;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.oxf.xml.XPathUtils;
import org.orbeon.oxf.xml.dom4j.*;
import org.orbeon.saxon.dom4j.DocumentWrapper;
import org.w3c.dom.Node;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;

import javax.naming.Context;
import javax.naming.InitialContext;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;

public class DelegationProcessor extends ProcessorImpl {

    public static final String DELEGATION_NAMESPACE_URI = "http://orbeon.org/oxf/xml/delegation";

    private static final org.dom4j.QName xsiType = new org.dom4j.QName
            ("type", new org.dom4j.Namespace("xsi", "http://www.w3.org/1999/XMLSchema-instance"));
    private static final String DEFAULT_SELECT_WEB_SERVICE_RPC = "/SOAP-ENV:Envelope/SOAP-ENV:Body/*[1]/node()";
    private static final String DEFAULT_SELECT_WEB_SERVICE_DOCUMENT = "/SOAP-ENV:Envelope/SOAP-ENV:Body/node()";
    private static final String DEFAULT_SELECT_BUS = "/SOAP-ENV:Envelope/SOAP-ENV:Body/*";

    public final String INPUT_INTERFACE = "interface";
    public final String INPUT_CALL = "call";

    public DelegationProcessor() {
        addInputInfo(new ProcessorInputOutputInfo(INPUT_INTERFACE, DELEGATION_NAMESPACE_URI));
        addInputInfo(new ProcessorInputOutputInfo(INPUT_CALL));
        addOutputInfo(new ProcessorInputOutputInfo(OUTPUT_DATA));
    }

    public ProcessorOutput createOutput(String name) {
        ProcessorOutput output = new ProcessorImpl.ProcessorOutputImpl(getClass(), name) {
            public void readImpl(final org.orbeon.oxf.pipeline.api.PipelineContext context, final ContentHandler contentHandler) {
                final java.util.List services = readServices(readInputAsDOM4J(context, INPUT_INTERFACE));

                readInputAsSAX(context, INPUT_CALL, new ForwardingContentHandler(contentHandler) {

                    Locator locator;
                    String operationName;
                    Integer operationTimeout;
                    ServiceDefinition service;
                    OperationDefinition operation;
                    SAXStore parameters;

                    public void startElement(String uri, String localname, String qName, Attributes attributes) throws SAXException {
                        if (uri.equals(DELEGATION_NAMESPACE_URI) && localname.equals("execute")) {

                            // Find service
                            service = null;
                            String serviceId = attributes.getValue("service");
                            for ( java.util.Iterator i = services.iterator(); i.hasNext();) {
                                ServiceDefinition candidateService = (ServiceDefinition) i.next();
                                if (candidateService.id.equals(serviceId)) {
                                    service = candidateService;
                                    break;
                                }
                            }
                            if (service == null)
                                throw new OXFException("Cannot find service with id \"" + serviceId + "\"");

                            operation = null;
                            operationName = attributes.getValue("operation");

                            // Find operation for Web service
                            if (service.type == ServiceDefinition.WEB_SERVICE_TYPE && operationName != null) {
                                for (java.util.Iterator i = service.operations.iterator(); i.hasNext();) {
                                    OperationDefinition candidateOperation = (OperationDefinition) i.next();
                                    if (candidateOperation.name.equals(operationName)) {
                                        operation = candidateOperation;
                                        break;
                                    }
                                }
                                if (operation == null)
                                    throw new ValidationException("No operation '" + operationName + "' declared", new LocationData(locator));
                            }

                            // Get timeout if any
                            {
                                final String timeoutAttribute = attributes.getValue("timeout");
                                if (timeoutAttribute != null) {
                                    try {
                                        operationTimeout = new Integer(timeoutAttribute);
                                    } catch (NumberFormatException e) {
                                        throw new ValidationException("Invalid timeout specified: " + timeoutAttribute, new LocationData(locator));
                                    }
                                    if (operationTimeout.intValue() < 0)
                                        throw new ValidationException("Invalid timeout specified: " + operationTimeout, new LocationData(locator));
                                }
                            }

                            parameters = new SAXStore();

                        } else {
                            // Store values if we are inside a <delegation:execute>
                            if (parameters == null) {
                                super.startElement(uri, localname, qName, attributes);
                            } else {
                                parameters.startElement(uri, localname, qName, attributes);
                            }
                        }
                    }

                    public void endElement(String uri, String localname, String qName) {
                        try {
                            if (uri.equals(DELEGATION_NAMESPACE_URI)) {
                                if (localname.equals("execute")) {

                                    if (service.type == ServiceDefinition.WEB_SERVICE_TYPE
                                            || service.type == ServiceDefinition.BUS_SERVICE_TYPE) {

                                        // Call Web service
                                        Service axisService = new Service();
                                        Call call = (Call) axisService.createCall();
                                        if (operationTimeout != null)
                                            call.setTimeout(operationTimeout);

                                        // Read all parameters in root node
                                        final Node rootNode;
                                        {
                                            // Read in DOM4j content handler
                                            final NonLazySAXContentHandler dom4jContentHandler 
                                                = new NonLazySAXContentHandler();
                                            dom4jContentHandler.startDocument();
                                            dom4jContentHandler.startElement("", "dummy", "dummy", XMLUtils.EMPTY_ATTRIBUTES);
                                            parameters.replay(dom4jContentHandler);
                                            dom4jContentHandler.endElement("", "dummy", "dummy");
                                            dom4jContentHandler.endDocument();

                                            // Convert to DOM
                                            rootNode = new DOMWriter().write
                                                    (dom4jContentHandler.getDocument()).getDocumentElement();
                                        }

                                        // Populate envelope
                                        SOAPEnvelope requestEnvelope =
                                                service.soapVersion != null && service.soapVersion.equals("1.2")
                                                ? new SOAPEnvelope(SOAPConstants.SOAP12_CONSTANTS)
                                                : new SOAPEnvelope();
                                        SOAPConstants soapConstants  = requestEnvelope.getSOAPConstants();
                                        final java.util.Map selectNamespaceContext = new java.util.HashMap();
                                        selectNamespaceContext.put("SOAP-ENV", soapConstants.getEnvelopeURI());
                                        if (service.type == ServiceDefinition.BUS_SERVICE_TYPE || "document".equals(service.style)) {
                                            // Add elements to directly to body
                                            for (int i = 0; i < rootNode.getChildNodes().getLength(); i++) {
                                                Node child = rootNode.getChildNodes().item(i);
                                                if (child instanceof org.w3c.dom.Element)
                                                    requestEnvelope.addBodyElement(new SOAPBodyElement((org.w3c.dom.Element) child));
                                            }
                                        } else {
                                            // Create body element with operation name, and add elements as children
                                            final SOAPBodyElement requestBody = new SOAPBodyElement(new PrefixedQName(operation.nsuri, operation.name, "m"));
                                            for (int i = 0; i < rootNode.getChildNodes().getLength(); i++) {
                                                Node child = rootNode.getChildNodes().item(i);
                                                if (child instanceof org.w3c.dom.Element) {
                                                    requestBody.addChild(new MessageElement((org.w3c.dom.Element) child));
                                                } else if (child instanceof org.w3c.dom.Text) {
                                                    requestBody.addTextNode(((org.w3c.dom.Text) child).toString());
                                                } else {
                                                    throw new OXFException("Unsupported node type: " + child.getClass().getName());
                                                }
                                            }
                                            requestEnvelope.addBodyElement(requestBody);
                                        }

                                        // Call service
                                        SOAPEnvelope resultEnvelope = null;
                                        if (service.type == ServiceDefinition.WEB_SERVICE_TYPE) {
                                            // Call Web service
                                            parameters = null;
                                            call.setTargetEndpointAddress(new URL(service.endpoint));
                                            if (operation != null && operation.soapAction != null) {
                                                call.setUseSOAPAction(true);
                                                call.setSOAPActionURI(operation.soapAction);
                                            }
                                            call.setReturnClass(javax.xml.soap.SOAPMessage.class);
                                            resultEnvelope = call.invoke(requestEnvelope);
                                        } else {
                                            // Call bus service
                                            javax.jms.QueueConnection requestQueueConnection = null;
                                            javax.jms.QueueSession requestQueueSession = null;
                                            javax.jms.QueueSender queueSender = null;
                                            try {
                                                requestQueueConnection = JMSUtils.getQueueConnection();
                                                requestQueueSession = requestQueueConnection.createQueueSession(false, javax.jms.Session.AUTO_ACKNOWLEDGE);
                                                queueSender = requestQueueSession.createSender
                                                        ((javax.jms.Queue) new InitialContext().lookup(JMSUtils.JNDI_SERVICE_PREFIX + service.name));
                                                javax.jms.ObjectMessage responseMessage = requestQueueSession.createObjectMessage();
                                                responseMessage.setObject(requestEnvelope);

                                                // Send message
                                                if (ServiceDirectory.instance().getServiceByName(service.name).hasOutputs()) {
                                                    // Response expected
                                                    javax.jms.QueueConnection responseQueueConnection = null;
                                                    javax.jms.QueueSession responseQueueSession = null;
                                                    javax.jms.QueueReceiver queueReceiver = null;
                                                    try {
                                                        responseQueueConnection = JMSUtils.getQueueConnection();
                                                        responseQueueSession = responseQueueConnection.createQueueSession(false, javax.jms.Session.AUTO_ACKNOWLEDGE);
                                                        javax.jms.Queue temporaryQueue = responseQueueSession.createTemporaryQueue();
                                                        queueReceiver = responseQueueSession.createReceiver(temporaryQueue);
                                                        responseMessage.setJMSReplyTo(temporaryQueue);
                                                        responseQueueConnection.start();
                                                        queueSender.send(responseMessage);
                                                        javax.jms.Message message = queueReceiver.receive();
                                                        resultEnvelope = (SOAPEnvelope) ((javax.jms.ObjectMessage) message).getObject();
                                                    } finally{
                                                        if (queueReceiver != null) queueReceiver.close();
                                                        if (responseQueueSession != null) responseQueueSession.close();
                                                        if (responseQueueConnection != null) responseQueueConnection.close();
                                                    }

                                                } else {
                                                    // No response expected
                                                    queueSender.send(responseMessage);
                                                }
                                            } finally {
                                                if (queueSender != null) queueSender.close();
                                                if (requestQueueSession != null) requestQueueSession.close();
                                                if (requestQueueConnection != null) requestQueueConnection.close();
                                            }

                                        }

                                        // Handle result
                                        if (resultEnvelope != null) {

                                            // Throw exception if a fault is returned
                                            if (resultEnvelope.getBody().getFault() != null) {
                                                throw new OXFException("SOAP Fault. Request:\n"
                                                        + XMLUtils.domToString(requestEnvelope.getAsDocument())
                                                        + "\n\nResponse:\n"
                                                        + XMLUtils.domToString(resultEnvelope.getAsDocument()));
                                            }

                                            // Send body from result envelope
                                            LocationSAXWriter locationSAXWriter = new LocationSAXWriter();
                                            locationSAXWriter.setContentHandler(contentHandler);
                                            final NonLazyUserDataDocumentFactory fctry 
                                                = NonLazyUserDataDocumentFactory
                                                      .getInstance( null );
                                            Document resultEnvelopeDOM4j = new DOMReader( fctry ).read(resultEnvelope.getAsDocument());

                                            String xpath =
                                                    operation != null && operation.select != null
                                                    ? operation.select
                                                    : service.type == ServiceDefinition.WEB_SERVICE_TYPE
                                                    ? ("document".equals(service.style) ? DEFAULT_SELECT_WEB_SERVICE_DOCUMENT : DEFAULT_SELECT_WEB_SERVICE_RPC)
                                                    : DEFAULT_SELECT_BUS;
                                            PooledXPathExpression expr = XPathCache.getXPathExpression(context,
                                                    new DocumentWrapper(resultEnvelopeDOM4j, null),
                                                    xpath,
                                                    operation != null && operation.select != null
                                                            ? operation.selectNamespaceContext : selectNamespaceContext);
                                            for (java.util.Iterator i = expr.evaluate().iterator(); i.hasNext();) {

                                                // Create document with node from SOAP envelope
                                                Object result = i.next();
                                                if (result instanceof Element) {
                                                    locationSAXWriter.write((Element) result);
                                                } else if (result instanceof Document) {
                                                    locationSAXWriter.write(((Document) result).getRootElement());
                                                } else if (result instanceof Text) {
                                                    locationSAXWriter.write((Text) result);
                                                } else {
                                                    throw new OXFException("Unsupported result from select expression: '" + result.getClass() + "'");
                                                }
                                            }
                                        }

                                    } else if (service.type == ServiceDefinition.STATELESS_EJB_TYPE
                                            || service.type == ServiceDefinition.JAVABEAN_TYPE) {

                                        // Create SAXStore with "real" document
                                        SAXStore parametersWellFormed = new SAXStore();
                                        parametersWellFormed.startDocument();
                                        parametersWellFormed.startElement("", "parameters", "parameters", XMLUtils.EMPTY_ATTRIBUTES);
                                        parameters.replay(parametersWellFormed);
                                        parametersWellFormed.endElement("", "parameters", "parameters");
                                        parametersWellFormed.endDocument();
                                        parameters = null;

                                        // Put parameters in DOM
                                        SAXStoreGenerator saxGenerator = new SAXStoreGenerator(parametersWellFormed);
                                        DOMSerializer domSerializer = new DOMSerializer();
                                        PipelineUtils.connect(saxGenerator, "data", domSerializer, "data");
                                        org.orbeon.oxf.pipeline.api.PipelineContext context = new org.orbeon.oxf.pipeline.api.PipelineContext();
                                        domSerializer.start(context);
                                        org.dom4j.Document parametersDocument = domSerializer.getDocument(context);
                                        // Get parameter values and types
                                        java.util.List parameterTypes = new java.util.ArrayList();
                                        java.util.List parameterValues = new java.util.ArrayList();

                                        // Go throught elements
                                        for (java.util.Iterator i = parametersDocument.selectNodes("/parameters/*").iterator(); i.hasNext();) {
                                            org.dom4j.Element parameterElement = (org.dom4j.Element) i.next();
                                            String parameterValue = parameterElement.getText();
                                            String type = parameterElement.attributeValue(xsiType);
                                            if (type == null || "xsd:string".equals(type)) {
                                                parameterTypes.add(String.class);
                                                parameterValues.add(parameterValue);
                                            } else if ("xsd:double".equals(type)) {
                                                parameterTypes.add(Double.TYPE);
                                                parameterValues.add(new Double(parameterValue));
                                            }
                                        }

                                        if (service.type == ServiceDefinition.STATELESS_EJB_TYPE) {
                                            // Call EJB method
                                            Context jndiContext = (Context) context.getAttribute(org.orbeon.oxf.pipeline.api.PipelineContext.JNDI_CONTEXT);
                                            Object home = jndiContext.lookup(service.jndiName);
                                            Method create = home.getClass().getDeclaredMethod("create", new Class[]{});
                                            Object instance = create.invoke(home, new Object[]{});
                                            String result = callMethod(instance.getClass(), operationName,
                                                    parameterTypes, instance, parameterValues);
                                            super.characters(result.toCharArray(), 0, result.length());
                                        } else if (service.type == ServiceDefinition.JAVABEAN_TYPE) {
                                            // Call JavaBean method
                                            Class clazz = Class.forName(service.clazz);
                                            Object instance = clazz.newInstance();
                                            String result = callMethod(clazz, operationName, parameterTypes,
                                                    instance, parameterValues);
                                            super.characters(result.toCharArray(), 0, result.length());
                                        }
                                    }
                                }
                            } else {
                                // Store values if we are inside a <delegation:execute>
                                if (parameters == null) {
                                    super.endElement(uri, localname, qName);
                                } else {
                                    parameters.endElement(uri, localname, qName);
                                }
                            }
                        } catch (Exception e) {
                            throw new OXFException(e);
                        }
                    }

                    public void characters(char[] chars, int start, int length) throws SAXException {
                        // Store values if we are inside a <delegation:execute>
                        if (parameters == null) {
                            super.characters(chars, start, length);
                        } else {
                            parameters.characters(chars, start, length);
                        }
                    }

                    public void setDocumentLocator(Locator locator) {
                        this.locator = locator;
                    }
                });
            };
        };
        addOutput(name, output);
        return output;
    }

    /**
     * Calls a method on an object with the reflexion API.
     */
    private String callMethod(Class clazz, String methodName, java.util.List parameterTypes,
                              Object instance, java.util.List parameterValues)
            throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        String result;
        Class[] parameterClasses = new Class[parameterTypes.size()];
        parameterTypes.toArray(parameterClasses);
        Method method = clazz.getDeclaredMethod(methodName, parameterClasses);
        result = method.invoke(instance, parameterValues.toArray()).toString();
        return result;
    }

    /**
     * Returns a list of AbstractSercice objects.
     */
    private java.util.List readServices(Document interfaceDocument) {

        java.util.List services = new java.util.ArrayList();
        for (java.util.Iterator i = interfaceDocument.getRootElement().elements("service").iterator(); i.hasNext();) {
            Element serviceElement = (Element) i.next();

            // Create Service Definition
            ServiceDefinition service = new ServiceDefinition();
            services.add(service);
            String serviceType = serviceElement.attributeValue("type");
            service.type =
                    "webservice".equals(serviceType) ? ServiceDefinition.WEB_SERVICE_TYPE :
                    "stateless-ejb".equals(serviceType) ? ServiceDefinition.STATELESS_EJB_TYPE :
                    "javabean".equals(serviceType) ? ServiceDefinition.JAVABEAN_TYPE :
                    "bus".equals(serviceType) ? ServiceDefinition.BUS_SERVICE_TYPE:
                    -1;
            service.id = serviceElement.attributeValue("id");
            service.endpoint = serviceElement.attributeValue("endpoint");
            service.jndiName = serviceElement.attributeValue("uri");
            service.name = serviceElement.attributeValue("name");
            service.clazz = serviceElement.attributeValue("class");
            service.style = serviceElement.attributeValue("style");
            service.soapVersion = serviceElement.attributeValue("soap-version");

            // Create operations
            for (java.util.Iterator j = XPathUtils.selectIterator(serviceElement, "operation"); j.hasNext();) {
                Element operationElement = (Element) j.next();
                OperationDefinition operation = new OperationDefinition();
                operation.service = service;
                service.operations.add(operation);
                operation.name = operationElement.attributeValue("name");
                operation.nsuri = operationElement.attributeValue("nsuri");
                operation.soapAction = operationElement.attributeValue("soap-action");
                operation.encodingStyle = operationElement.attributeValue("encodingStyle");
                String select = operationElement.attributeValue("select");
                if (select != null) {
                    operation.select = select;
                    operation.selectNamespaceContext = Dom4jUtils.getNamespaceContext(operationElement);
                }
            }
        }

        return services;
    }

    private static class ServiceDefinition {
        public final static int WEB_SERVICE_TYPE = 1;
        public final static int STATELESS_EJB_TYPE = 2;
        public final static int JAVABEAN_TYPE = 3;
        public final static int BUS_SERVICE_TYPE = 4;

        public int type;
        public String id;
        public String endpoint;
        public String jndiName;
        public String name;
        public String clazz;
        public String style;
        public String soapVersion;
        public java.util.List operations = new java.util.ArrayList();
    }

    private static class OperationDefinition {
        public ServiceDefinition service;
        public String name;
        public String nsuri;
        public String soapAction;
        public String encodingStyle;
        public String select;
        public java.util.Map selectNamespaceContext;
    }
}
