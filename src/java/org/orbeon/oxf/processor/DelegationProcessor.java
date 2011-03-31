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

import org.apache.axis.client.Call;
import org.apache.axis.client.Service;
import org.apache.axis.message.*;
import org.apache.axis.soap.SOAPConstants;
import org.dom4j.*;
import org.dom4j.Text;
import org.dom4j.io.DOMReader;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.pipeline.api.XMLReceiver;
import org.orbeon.oxf.servicedirectory.ServiceDirectory;
import org.orbeon.oxf.util.*;
import org.orbeon.oxf.webapp.ProcessorService;
import org.orbeon.oxf.xml.*;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.oxf.xml.dom4j.*;
import org.orbeon.saxon.dom4j.DocumentWrapper;
import org.orbeon.saxon.om.DocumentInfo;
import org.w3c.dom.Node;
import org.xml.sax.*;

import javax.naming.Context;
import javax.naming.InitialContext;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;

public class DelegationProcessor extends ProcessorImpl {

    public static final String DELEGATION_NAMESPACE_URI = "http://orbeon.org/oxf/xml/delegation";

    private static final String DEFAULT_SELECT_WEB_SERVICE_RPC = "/*:Envelope/*:Body/*[1]/text() | /*:Envelope/*:Body/*[1]/*";
    private static final String DEFAULT_SELECT_WEB_SERVICE_DOCUMENT = "/*:Envelope/*:Body/text() | /*:Envelope/*:Body/*";
    private static final String DEFAULT_SELECT_BUS = "/*:Envelope/*:Body/*";

    public final String INPUT_INTERFACE = "interface";
    public final String INPUT_CALL = "call";

    public DelegationProcessor() {
        addInputInfo(new ProcessorInputOutputInfo(INPUT_INTERFACE, DELEGATION_NAMESPACE_URI));
        addInputInfo(new ProcessorInputOutputInfo(INPUT_CALL));
        addOutputInfo(new ProcessorInputOutputInfo(OUTPUT_DATA));
    }

    public ProcessorOutput createOutput(String name) {
        ProcessorOutput output = new ProcessorOutputImpl(DelegationProcessor.this, name) {
            public void readImpl(final PipelineContext context, final XMLReceiver xmlReceiver) {
                final List<ServiceDefinition> services = readServices(readInputAsDOM4J(context, INPUT_INTERFACE));

                readInputAsSAX(context, INPUT_CALL, new ForwardingXMLReceiver(xmlReceiver) {

                    Locator locator;
                    String operationName;
                    Integer operationTimeout;
                    ServiceDefinition service;
                    OperationDefinition operation;
                    SAXStore parameters;
                    private NamespaceSupport3 namespaceSupport = new NamespaceSupport3();

                    public void startElement(String uri, String localname, String qName, Attributes attributes) throws SAXException {

                        namespaceSupport.startElement();

                        if (uri.equals(DELEGATION_NAMESPACE_URI) && localname.equals("execute")) {

                            // Find service
                            service = null;
                            String serviceId = attributes.getValue("service");
                            for ( Iterator<ServiceDefinition> i = services.iterator(); i.hasNext();) {
                                ServiceDefinition candidateService = i.next();
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
                                for (Iterator<OperationDefinition> i = service.operations.iterator(); i.hasNext();) {
                                    OperationDefinition candidateOperation = i.next();
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

                            // Start building the parameters document
                            {
                                parameters = new SAXStore();
                                parameters.startDocument();

                                {
                                    // Handle namespaces in scope as of delegation:execute element
                                    for (Enumeration e = namespaceSupport.getPrefixes(); e.hasMoreElements();) {
                                        final String namespacePrefix = (String) e.nextElement();
                                        if (!namespacePrefix.startsWith("xml") && !namespacePrefix.equals(""))
                                            parameters.startPrefixMapping(namespacePrefix, namespaceSupport.getURI(namespacePrefix));
                                    }

                                    final String defaultNS = namespaceSupport.getURI("");
                                    if (defaultNS != null && defaultNS.length() > 0)
                                        super.startPrefixMapping("", defaultNS);
                                }

                                parameters.startElement("", "parameters", "parameters", XMLUtils.EMPTY_ATTRIBUTES);
                            }

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

                                    // Complete parameters document
                                    {
                                        parameters.endElement("", "parameters", "parameters");

                                        {
                                            // Handle namespaces in scope as of delegation:execute element
                                            for (Enumeration e = namespaceSupport.getPrefixes(); e.hasMoreElements();) {
                                                final String namespacePrefix = (String) e.nextElement();
                                                if (!namespacePrefix.startsWith("xml") && !namespacePrefix.equals(""))
                                                    parameters.endPrefixMapping(namespacePrefix);
                                            }

                                            final String defaultNS = namespaceSupport.getURI("");
                                            if (defaultNS != null && defaultNS.length() > 0)
                                                super.endPrefixMapping("");
                                        }

                                        parameters.endDocument();
                                    }

                                    if (service.type == ServiceDefinition.WEB_SERVICE_TYPE
                                            || service.type == ServiceDefinition.BUS_SERVICE_TYPE) {

                                        // Call Web service
                                        final Service axisService = new Service();
                                        final Call call = (Call) axisService.createCall();
                                        if (operationTimeout != null)
                                            call.setTimeout(operationTimeout);

                                        // Get document containing the parameters
                                        final org.w3c.dom.Element parametersElement = getParametersDomDocument().getDocumentElement();

                                        // Populate envelope
                                        final SOAPEnvelope requestEnvelope =
                                                service.soapVersion != null && service.soapVersion.equals("1.2")
                                                ? new SOAPEnvelope(SOAPConstants.SOAP12_CONSTANTS)
                                                : new SOAPEnvelope();
                                        if (service.type == ServiceDefinition.BUS_SERVICE_TYPE || "document".equals(service.style)) {
                                            // Add elements to directly to body
                                            for (int i = 0; i < parametersElement.getChildNodes().getLength(); i++) {
                                                final Node child = parametersElement.getChildNodes().item(i);
                                                if (child instanceof org.w3c.dom.Element)
                                                    requestEnvelope.addBodyElement(new SOAPBodyElement((org.w3c.dom.Element) child));
                                            }
                                        } else {
                                            // Create body element with operation name, and add elements as children
                                            final SOAPBodyElement requestBody = new SOAPBodyElement(new PrefixedQName(operation.nsuri, operation.name, "m"));
                                            for (int i = 0; i < parametersElement.getChildNodes().getLength(); i++) {
                                                final Node child = parametersElement.getChildNodes().item(i);
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

                                            // Throw exception if a fault is returned and the user does not want the fault to be returned
                                            if (resultEnvelope.getBody().getFault() != null && !service.returnFault) {
                                                throw new OXFException("SOAP Fault. Request:\n"
                                                        + XMLUtils.domToString(requestEnvelope.getAsDocument())
                                                        + "\n\nResponse:\n"
                                                        + XMLUtils.domToString(resultEnvelope.getAsDocument()));
                                            }

                                            // Send body from result envelope
                                            final LocationSAXWriter locationSAXWriter = new LocationSAXWriter();
                                            locationSAXWriter.setContentHandler(xmlReceiver);
                                            final DocumentFactory factory = NonLazyUserDataDocumentFactory.getInstance();
                                            final Document resultEnvelopeDOM4j = new DOMReader(factory).read(resultEnvelope.getAsDocument());

                                            final String xpathString =
                                                    operation != null && operation.select != null
                                                    ? operation.select
                                                    : service.type == ServiceDefinition.WEB_SERVICE_TYPE
                                                    ? ("document".equals(service.style) ? DEFAULT_SELECT_WEB_SERVICE_DOCUMENT : DEFAULT_SELECT_WEB_SERVICE_RPC)
                                                    : DEFAULT_SELECT_BUS;

                                            final DocumentInfo documentInfo = new DocumentWrapper(resultEnvelopeDOM4j, null, XPathCache.getGlobalConfiguration());
                                            final PooledXPathExpression expr = XPathCache.getXPathExpression(
                                                    documentInfo.getConfiguration(), documentInfo, xpathString,
                                                    operation != null && operation.select != null
                                                            ? operation.selectNamespaceContext : null, getLocationData());

                                            try {
                                                for (Iterator i = expr.evaluate().iterator(); i.hasNext();) {

                                                    // Create document with node from SOAP envelope
                                                    final Object result = i.next();
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
                                            } finally {
                                                if (expr != null) expr.returnToPool();
                                            }
                                        }

                                    } else if (service.type == ServiceDefinition.STATELESS_EJB_TYPE
                                            || service.type == ServiceDefinition.JAVABEAN_TYPE) {

                                        // Get document containing the parameters
                                        final Document parametersDocument = getParametersDocument();

                                        // Get parameter values and types
                                        final List<Class> parameterTypes = new ArrayList<Class>();
                                        final List<Serializable> parameterValues = new ArrayList<Serializable>();

                                        // Go throught elements
                                        for (Iterator i = XPathUtils.selectIterator(parametersDocument, "/*/*"); i.hasNext();) {
                                            final org.dom4j.Element parameterElement = (org.dom4j.Element) i.next();
                                            final String parameterValue = parameterElement.getText();
                                            // TODO: should pass true?
                                            final QName type = Dom4jUtils.extractAttributeValueQName(parameterElement, XMLConstants.XSI_TYPE_QNAME, false);

                                            if (type == null || XMLConstants.XS_STRING_QNAME.equals(type)) {
                                                parameterTypes.add(String.class);
                                                parameterValues.add(parameterValue);
                                            } else if (XMLConstants.XS_DOUBLE_QNAME.equals(type)) {
                                                parameterTypes.add(Double.TYPE);
                                                parameterValues.add(new Double(parameterValue));
                                            } else if (XMLConstants.XS_BOOLEAN_QNAME.equals(type)) {
                                                parameterTypes.add(Boolean.TYPE);
                                                parameterValues.add(new Boolean(parameterValue));
                                            } else if (XMLConstants.XS_INTEGER_QNAME.equals(type)) {
                                                parameterTypes.add(Integer.TYPE);
                                                parameterValues.add(new Integer(parameterValue));
                                            }
                                        }

                                        if (service.type == ServiceDefinition.STATELESS_EJB_TYPE) {
                                            // Call EJB method
                                            final Context jndiContext = (Context) context.getAttribute(ProcessorService.JNDI_CONTEXT);
                                            if (jndiContext == null)
                                                throw new ValidationException("JNDI context not found in pipeline context.", new LocationData(locator));
                                            final Object home = jndiContext.lookup(service.jndiName);
                                            if (home == null)
                                                throw new ValidationException("Home interface not found in JNDI context: " + service.jndiName, new LocationData(locator));
                                            final Method create = home.getClass().getDeclaredMethod("create", new Class[]{});
                                            final Object instance = create.invoke(home);
                                            final String result = callMethod(instance.getClass(), operationName, parameterTypes, instance, parameterValues);

                                            super.characters(result.toCharArray(), 0, result.length());
                                        } else if (service.type == ServiceDefinition.JAVABEAN_TYPE) {
                                            // Call JavaBean method
                                            final Class clazz = Class.forName(service.clazz);
                                            final Object instance = clazz.newInstance();
                                            final String result = callMethod(clazz, operationName, parameterTypes, instance, parameterValues);

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

                        namespaceSupport.endElement();
                    }

                    public void characters(char[] chars, int start, int length) throws SAXException {
                        // Store values if we are inside a <delegation:execute>
                        if (parameters == null) {
                            super.characters(chars, start, length);
                        } else {
                            parameters.characters(chars, start, length);
                        }
                    }

                    public void startPrefixMapping(String prefix, String uri) throws SAXException {
                        namespaceSupport.startPrefixMapping(prefix, uri);
                        if (parameters == null) {
                            super.startPrefixMapping(prefix, uri);
                        } else {
                            parameters.startPrefixMapping(prefix, uri);
                        }
                    }

                    public void endPrefixMapping(String s) throws SAXException {
                        if (parameters == null) {
                            super.endPrefixMapping(s);
                        } else {
                            parameters.endPrefixMapping(s);
                        }
                    }

                    public void setDocumentLocator(Locator locator) {
                        this.locator = locator;
                    }

                    private Document getParametersDocument() throws SAXException {
                        // Create Document
                        final Document result = TransformerUtils.saxStoreToDom4jDocument(parameters);
                        parameters = null;
                        return result;
                    }

                    private org.w3c.dom.Document getParametersDomDocument() throws SAXException {
                        // Create DOM document
                        final org.w3c.dom.Document result = TransformerUtils.saxStoreToDomDocument(parameters);
                        parameters = null;
                        return result;
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
    private String callMethod(Class clazz, String methodName, List<Class> parameterTypes,
                              Object instance, List<Serializable> parameterValues)
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
    private List<ServiceDefinition> readServices(Document interfaceDocument) {

        List<ServiceDefinition> services = new java.util.ArrayList<ServiceDefinition>();
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
            service.returnFault = "true".equals(serviceElement.attributeValue("return-fault"));

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
                    operation.selectNamespaceContext = new NamespaceMapping(Dom4jUtils.getNamespaceContextNoDefault(operationElement));
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
        public boolean returnFault;
        public List<OperationDefinition> operations = new java.util.ArrayList<OperationDefinition>();
    }

    private static class OperationDefinition {
        public ServiceDefinition service;
        public String name;
        public String nsuri;
        public String soapAction;
        public String encodingStyle;
        public String select;
        public NamespaceMapping selectNamespaceContext;
    }
}
