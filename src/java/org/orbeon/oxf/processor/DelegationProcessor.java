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
import org.apache.axis.message.PrefixedQName;
import org.apache.axis.message.SOAPBodyElement;
import org.apache.axis.message.SOAPEnvelope;
import org.apache.axis.message.MessageElement;
import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.Text;
import org.dom4j.XPath;
import org.jaxen.SimpleNamespaceContext;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.processor.generator.SAXStoreGenerator;
import org.orbeon.oxf.util.PipelineUtils;
import org.orbeon.oxf.util.XPathCache;
import org.orbeon.oxf.xml.*;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.orbeon.oxf.xml.dom4j.LocationSAXContentHandler;
import org.orbeon.oxf.xml.dom4j.LocationSAXWriter;
import org.w3c.dom.Node;
import org.xml.sax.*;
import org.xml.sax.helpers.XMLFilterImpl;

import javax.naming.Context;
import javax.xml.transform.Transformer;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.sax.SAXSource;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;

public class DelegationProcessor extends ProcessorImpl {

    public static final String DELEGATION_NAMESPACE_URI = "http://orbeon.org/oxf/xml/delegation";

    private static final org.dom4j.QName xsiType = new org.dom4j.QName
            ("type", new org.dom4j.Namespace("xsi", "http://www.w3.org/1999/XMLSchema-instance"));
    private static final String DEFAULT_SELECT = "/SOAP-ENV:Envelope/SOAP-ENV:Body/*[1]/node()";
    private static final Map DEFAULT_SELECT_NAMESPACE_CONTEXT = new HashMap();
    static {
        DEFAULT_SELECT_NAMESPACE_CONTEXT.put("SOAP-ENV", "http://schemas.xmlsoap.org/soap/envelope/");
    }


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
                final org.orbeon.oxf.pipeline.api.PipelineContext _context = context;
                final List services = readServices(readInputAsDOM4J(context, INPUT_INTERFACE));

                readInputAsSAX(context, INPUT_CALL, new ForwardingContentHandler(contentHandler) {

                    Locator locator;
                    String operationName;
                    ServiceDefinition service;
                    OperationDefinition operation;
                    SAXStore parameters;

                    public void startElement(String uri, String localname, String qName, Attributes attributes) throws SAXException {
                        if (uri.equals(DELEGATION_NAMESPACE_URI) && localname.equals("execute")) {

                            // Find service
                            service = null;
                            String serviceId = attributes.getValue("service");
                            for (Iterator i = services.iterator(); i.hasNext();) {
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
                                for (Iterator i = service.operations.iterator(); i.hasNext();) {
                                    OperationDefinition candidateOperation = (OperationDefinition) i.next();
                                    if (candidateOperation.name.equals(operationName)) {
                                        operation = candidateOperation;
                                        break;
                                    }
                                }
                                if (operation == null)
                                    throw new ValidationException("No operation '" + operationName + "' declared",
                                            new LocationData(locator));
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

                                    if (service.type == ServiceDefinition.WEB_SERVICE_TYPE) {

                                        // Call Web service
                                        Service axisService = new Service();
                                        Call call = (Call) axisService.createCall();
                                        SOAPEnvelope requestEnvelope = new SOAPEnvelope();

                                        // Read all parameters in root node
                                        final Node rootNode;
                                        {
                                            SAXSource saxSource = new SAXSource(new XMLFilterImpl() {
                                                ContentHandler contentHandler;
                                                public void setContentHandler(ContentHandler handler) {
                                                    super.setContentHandler(handler);
                                                    contentHandler = handler;
                                                }

                                                public void parse(InputSource input) throws SAXException {
                                                    contentHandler.startDocument();
                                                    contentHandler.startElement("", "dummy", "dummy", XMLUtils.EMPTY_ATTRIBUTES);
                                                    parameters.replay(contentHandler);
                                                    contentHandler.endElement("", "dummy", "dummy");
                                                    contentHandler.endDocument();
                                                }

                                                public void setFeature(String name, boolean state) throws SAXNotRecognizedException, SAXNotSupportedException {
                                                    // We allow these two features
                                                    if (name.equals("http://xml.org/sax/features/namespaces") && state)
                                                        return;
                                                    if (name.equals("http://xml.org/sax/features/namespace-prefixes") && !state)
                                                        return;

                                                    // Otherwise we throw
                                                    throw new SAXNotRecognizedException("Feature: " + name);
                                                }
                                            }, null);
                                            DOMResult domResult = new DOMResult();
                                            Transformer identityTransformer = TransformerUtils.getIdentityTransformer();
                                            identityTransformer.transform(saxSource, domResult);
                                            rootNode = domResult.getNode().getFirstChild();
                                        }

                                        // Populate envelope
                                        if ("document".equals(service.style)) {
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
                                                    requestEnvelope.addBodyElement(new SOAPBodyElement((org.w3c.dom.Element) child));
                                                } else {
                                                    throw new OXFException("Unsupported node type: " + child.getClass().getName());
                                                }
                                            }
                                            requestEnvelope.addBodyElement(requestBody);
                                        }
                                        
                                        parameters = null;
                                        call.setTargetEndpointAddress(new URL(service.endpoint));
                                        if (operation != null && operation.soapAction != null) {
                                            call.setUseSOAPAction(true);
                                            call.setSOAPActionURI(operation.soapAction);
                                        }
                                        call.setReturnClass(javax.xml.soap.SOAPMessage.class);
                                        SOAPEnvelope resultEnvelope = call.invoke(requestEnvelope);

                                        // Throw exception if a fault is returned
                                        if (resultEnvelope.getBody().getFault() != null) {
                                            throw new OXFException("SOAP Fault. Request:\n"
                                                    + XMLUtils.domToString(requestEnvelope.getAsDocument())
                                                    + "\n\nResponse:\n"
                                                    + XMLUtils.domToString(resultEnvelope.getAsDocument()));
                                        }

                                        // Get body from result envelope
                                        LocationSAXContentHandler domContentHandler = new LocationSAXContentHandler();
                                        resultEnvelope.publishToHandler(domContentHandler);
                                        XPath xpath = XPathCache.createCacheXPath(context,
                                                operation != null && operation.select != null
                                                ? operation.select : DEFAULT_SELECT);
                                        xpath.setNamespaceContext(new SimpleNamespaceContext(
                                                operation != null && operation.select != null
                                                ? operation.selectNamespaceContext : DEFAULT_SELECT_NAMESPACE_CONTEXT));
                                        LocationSAXWriter locationSAXWriter = new LocationSAXWriter();
                                        locationSAXWriter.setContentHandler(contentHandler);
                                        for (Iterator i = xpath.selectNodes(domContentHandler.getDocument()).iterator(); i.hasNext();) {

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

                                    } else if (service.type == ServiceDefinition.STATELESS_EJB_TYPE
                                            || service.type == ServiceDefinition.JAVABEAN_TYPE) {

                                        // Create SAXStore with "real" document
                                        SAXStore parametersWellFormed = new SAXStore();
                                        parametersWellFormed.startDocument();
                                        parametersWellFormed.startElement("", "parameters", "parameters", XMLUtils.EMPTY_ATTRIBUTES);
                                        parameters.replay(parametersWellFormed);
                                        parametersWellFormed.endElement("", "parameters", "parameters");
                                        parametersWellFormed.endDocument();

                                        // Put parameters in DOM
                                        SAXStoreGenerator saxGenerator = new SAXStoreGenerator(parametersWellFormed);
                                        DOMSerializer domSerializer = new DOMSerializer();
                                        PipelineUtils.connect(saxGenerator, "data", domSerializer, "data");
                                        org.orbeon.oxf.pipeline.api.PipelineContext context = new org.orbeon.oxf.pipeline.api.PipelineContext();
                                        domSerializer.start(context);
                                        org.dom4j.Document parametersDocument = domSerializer.getNode(context);
                                        // Get parameter values and types
                                        List parameterTypes = new ArrayList();
                                        List parameterValues = new ArrayList();

                                        // Go throught elements
                                        for (Iterator i = parametersDocument.selectNodes("/parameters/*").iterator(); i.hasNext();) {
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
                                            Context jndiContext = (Context) _context.getAttribute(org.orbeon.oxf.pipeline.api.PipelineContext.JNDI_CONTEXT);
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
    private String callMethod(Class clazz, String methodName, List parameterTypes,
                              Object instance, List parameterValues)
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
    private List readServices(Document interfaceDocument) {

        List services = new ArrayList();
        for (Iterator i = interfaceDocument.getRootElement().elements("service").iterator(); i.hasNext();) {
            Element serviceElement = (Element) i.next();

            // Create Service Definition
            ServiceDefinition service = new ServiceDefinition();
            services.add(service);
            String serviceType = serviceElement.attributeValue("type");
            service.type =
                    "webservice".equals(serviceType) ? ServiceDefinition.WEB_SERVICE_TYPE :
                    "stateless-ejb".equals(serviceType) ? ServiceDefinition.STATELESS_EJB_TYPE :
                    "javabean".equals(serviceType) ? ServiceDefinition.JAVABEAN_TYPE :
                    -1;
            service.id = serviceElement.attributeValue("id");
            service.endpoint = serviceElement.attributeValue("endpoint");
            service.jndiName = serviceElement.attributeValue("uri");
            service.clazz = serviceElement.attributeValue("class");
            service.style = serviceElement.attributeValue("style");

            // Create operations
            for (Iterator j = XPathUtils.selectIterator(serviceElement, "operation"); j.hasNext();) {
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
                    operation.selectNamespaceContext = XMLUtils.getNamespaceContext(operationElement);
                }
            }
        }

        return services;
    }

    private static class ServiceDefinition {
        public final static int WEB_SERVICE_TYPE = 1;
        public final static int STATELESS_EJB_TYPE = 2;
        public final static int JAVABEAN_TYPE = 3;

        public int type;
        public String id;
        public String endpoint;
        public String jndiName;
        public String clazz;
        public String style;
        public List operations = new ArrayList();
    }

    private static class OperationDefinition {
        public ServiceDefinition service;
        public String name;
        public String nsuri;
        public String soapAction;
        public String encodingStyle;
        public String select;
        public Map selectNamespaceContext;
    }
}
