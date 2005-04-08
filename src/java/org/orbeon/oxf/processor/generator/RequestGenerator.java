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
package org.orbeon.oxf.processor.generator;

import org.apache.commons.fileupload.DefaultFileItem;
import org.apache.commons.fileupload.DefaultFileItemFactory;
import org.apache.commons.fileupload.FileItem;
import org.apache.log4j.Logger;
import org.dom4j.*;
import org.dom4j.io.DocumentSource;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.ProcessorImpl;
import org.orbeon.oxf.processor.ProcessorInputOutputInfo;
import org.orbeon.oxf.processor.ProcessorOutput;
import org.orbeon.oxf.resources.OXFProperties;
import org.orbeon.oxf.util.LoggerFactory;
import org.orbeon.oxf.util.NetUtils;
import org.orbeon.oxf.util.SystemUtils;
import org.orbeon.oxf.xml.ForwardingContentHandler;
import org.orbeon.oxf.xml.TransformerUtils;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.oxf.xml.XPathUtils;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.orbeon.oxf.xml.dom4j.NonLazyUserDataDocument;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.sax.SAXResult;
import java.io.*;
import java.net.MalformedURLException;
import java.util.*;

/**
 * The Request generator works like this:
 *
 * o Read config DOM (or obtain it from state cache)
 * o Obtain request DOM
 *   o Read whole request as DOM (or obtain it from context cache)
 *     o If content-type is multipart/form-data, special handling takes place.
 *       o File items are read with commons upload, either in memory if small or on disk if large
 *       o Only references to the file items are stored in the DOM
 *     o A special marker is put in place for the request body
 *   o Filter the DOM request based on config
 *   o Serialize the DOM into SAX
 *     o References to file items are treated specially during the serialization process
 *       o Small files are serialized, and the xs:base64Binary type is set
 *       o Large files are referenced, and the xs:anyURI type is set
 *     o The body marker, if not filtered, causes the body of the request to be read
 *       o Small files are serialized, and the xs:base64Binary type is set
 *       o Large files are referenced, and the xs:anyURI type is set
 *
 *   NOTE: The Request generator attempts to cache based on the content of the request, but it
 *   never caches if there is an upload or if a request body is requested.
 *
 *   FIXME: The upload code checks that the total request body is not larger than the maximum
 *   upload size specified. But If many small files are uploaded, can we potentially use a lot of
 *   memory?
 */
public class RequestGenerator extends ProcessorImpl {

    static Logger logger = LoggerFactory.createLogger(RequestGenerator.class);

    public static final String REQUEST_CONFIG_NAMESPACE_URI = "http://orbeon.org/oxf/xml/request-config";
    private static final String REQUEST_PRIVATE_NAMESPACE_URI = "http://orbeon.org/oxf/xml/request-private";

    // Maximum upload size
    private static final int DEFAULT_MAX_UPLOAD_SIZE = 1024 * 1024;
    private static final String MAX_UPLOAD_SIZE_PROPERTY = "max-upload-size";

    // Maximum size kept in memory
    private static final int DEFAULT_MAX_UPLOAD_MEMORY_SIZE = 10 * 1024;
    private static final String MAX_UPLOAD_MEMORY_SIZE_PROPERTY = "max-upload-memory-size";

    // Enable saving input stream
//    private static final boolean DEFAULT_ENABLE_INPUT_STREAM_SAVING = true;
//    private static final String ENABLE_INPUT_STREAM_SAVING_PROPERTY = "enable-input-stream-saving";

    private static final String INCLUDE_ELEMENT = "include";
    private static final String EXCLUDE_ELEMENT = "exclude";

    private static final String FILE_ITEM_ELEMENT = "request:file-item";
    private static final String PARAMETER_NAME_ATTRIBUTE = "parameter-name";

    private static final Map prefixes = new HashMap();

    {
        prefixes.put("request", REQUEST_PRIVATE_NAMESPACE_URI);
    }

    public RequestGenerator() {
        addInputInfo(new ProcessorInputOutputInfo(INPUT_CONFIG, REQUEST_CONFIG_NAMESPACE_URI));
        addOutputInfo(new ProcessorInputOutputInfo(OUTPUT_DATA));
    }

    public ProcessorOutput createOutput(String name) {
        ProcessorOutput output = new ProcessorImpl.DigestTransformerOutputImpl(getClass(), name) {
            public void readImpl(final PipelineContext pipelineContext, ContentHandler contentHandler) {
                try {
                    final State state = (State) getFilledOutState(pipelineContext);
                    // Transform the resulting document into SAX
                    Transformer identity = TransformerUtils.getIdentityTransformer();
                    identity.transform(new DocumentSource(state.requestDocument), new SAXResult(new ForwardingContentHandler(contentHandler) {
                        public void startElement(String uri, String localname, String qName, Attributes attributes) throws SAXException {
                            if (REQUEST_PRIVATE_NAMESPACE_URI.equals(uri)) {
                                // Special treatment for this element
                                if (FILE_ITEM_ELEMENT.equals(qName)) {
                                    // Marker for file item

                                    String parameterName = attributes.getValue(PARAMETER_NAME_ATTRIBUTE);
                                    FileItem fileItem = (FileItem) getRequest(pipelineContext).getParameterMap().get(parameterName);

                                    AttributesImpl newAttributes = new AttributesImpl();
                                    super.startPrefixMapping(XMLConstants.XSI_PREFIX, XMLConstants.XSI_URI);
                                    super.startPrefixMapping(XMLConstants.XSD_PREFIX, XMLConstants.XSD_URI);
                                    newAttributes.addAttribute(XMLConstants.XSI_URI, "type", "xsi:type", "CDATA",
                                            useBase64(pipelineContext, fileItem) ? XMLConstants.XS_BASE64BINARY_QNAME.getQualifiedName(): XMLConstants.XS_ANYURI_QNAME.getQualifiedName());
                                    super.startElement("", "value", "value", newAttributes);
                                    writeFileItem(pipelineContext, fileItem, getContentHandler());
                                    super.endElement("", "value", "value");
                                    super.endPrefixMapping(XMLConstants.XSD_PREFIX);
                                    super.endPrefixMapping(XMLConstants.XSI_PREFIX);
                                }
                            } else if (localname.equals("body") && uri.equals("")) {
                                // Marker for request body

                                // Read InputStream into FileItem object, if not already present

                                // We do this so we can read the body multiple times, if needed.
                                // For large files, there will be a performance hit. If we knew
                                // we didn't need to read it multiple times, we could avoid
                                // saving the stream, but practically, it can happen, and it is
                                // convenient.
                                Context context = getContext(pipelineContext);
                                if (context.bodyFileItem == null) {
                                    final FileItem fileItem = new DefaultFileItemFactory(getMaxMemorySizeProperty(), SystemUtils.getTemporaryDirectory()).createItem("dummy", "dummy", false, null);
                                    pipelineContext.addContextListener(new PipelineContext.ContextListenerAdapter() {
                                        public void contextDestroyed(boolean success) {
                                            fileItem.delete();
                                        }
                                    });
                                    try {
                                        OutputStream outputStream = fileItem.getOutputStream();
                                        NetUtils.copyStream(getRequest(pipelineContext).getInputStream(), outputStream);
                                        outputStream.close();
                                    } catch (IOException e) {
                                        throw new OXFException(e);
                                    }
                                    context.bodyFileItem = fileItem;
                                }
                                // Serialize the stream into the body element
                                AttributesImpl newAttributes = new AttributesImpl();
                                super.startPrefixMapping(XMLConstants.XSI_PREFIX, XMLConstants.XSI_URI);
                                super.startPrefixMapping(XMLConstants.XSD_PREFIX, XMLConstants.XSD_URI);
                                newAttributes.addAttribute(XMLConstants.XSI_URI, "type", "xsi:type", "CDATA",
                                        useBase64(pipelineContext, context.bodyFileItem) ? XMLConstants.XS_BASE64BINARY_QNAME.getQualifiedName(): XMLConstants.XS_ANYURI_QNAME.getQualifiedName());
                                super.startElement(uri, localname, qName, newAttributes);
                                writeFileItem(pipelineContext, context.bodyFileItem, getContentHandler());
                                super.endElement(uri, localname, qName);
                                super.endPrefixMapping(XMLConstants.XSD_PREFIX);
                                super.endPrefixMapping(XMLConstants.XSI_PREFIX);
                            } else {
                                super.startElement(uri, localname, qName, attributes);
                            }
                        }
                        public void endElement(String uri, String localname, String qName) throws SAXException {
                            if (REQUEST_PRIVATE_NAMESPACE_URI.equals(uri) || localname.equals("body") && uri.equals("")) {
                                // Ignore end element
                            } else {
                                super.endElement(uri, localname, qName);
                            }
                        }
                    }));
                } catch (TransformerException e) {
                    throw new OXFException(e);
                }
            }

            protected boolean fillOutState(PipelineContext pipelineContext, DigestState digestState) {
                State state = (State) digestState;
                if (state.requestDocument == null) {
                    // Read config document
                    Document config = readCacheInputAsDOM4J(pipelineContext, INPUT_CONFIG);

                    // Try to find stream-type attribute
                    QName streamTypeQName = Dom4jUtils.extractAttributeValueQName(config.getRootElement(), "stream-type");
                    if (streamTypeQName != null && !(streamTypeQName.equals(XMLConstants.XS_BASE64BINARY_QNAME) || streamTypeQName.equals(XMLConstants.XS_ANYURI_QNAME)))
                        throw new OXFException("Invalid value for stream-type attribute: " + streamTypeQName.getQualifiedName());
                    state.requestedStreamType = streamTypeQName;

                    // Read and store request
                    state.requestDocument = readRequestAsDOM4J(pipelineContext, config);

                    // Check if the body was requestd
                    state.bodyRequested = XPathUtils.selectSingleNode(state.requestDocument, "/*/body") != null;
                }
                Context context = getContext(pipelineContext);
                return !context.hasUpload && !state.bodyRequested;
            }

            protected byte[] computeDigest(PipelineContext pipelineContext, DigestState digestState) {
                State state = (State) digestState;
                XMLUtils.DigestContentHandler dch = new XMLUtils.DigestContentHandler("MD5");
                try {
                    Transformer identityTransformer = TransformerUtils.getIdentityTransformer();
                    identityTransformer.transform(new DocumentSource(state.requestDocument), new SAXResult(dch));
                } catch (TransformerException e) {
                    throw new OXFException(e);
                }
                return dch.getResult();
            }
        };
        addOutput(name, output);
        return output;
    }

    /**
     * Check whether a specific FileItem must be generated as Base64.
     */
    private boolean useBase64(PipelineContext pipelineContext, FileItem fileItem) {
        State state = (State) getState(pipelineContext);

        return (state.requestedStreamType == null && fileItem.isInMemory())
                || (state.requestedStreamType != null && state.requestedStreamType.equals(XMLConstants.XS_BASE64BINARY_QNAME));
    }

    private void writeFileItem(PipelineContext pipelineContext, FileItem fileItem, ContentHandler contentHandler) throws SAXException {
        if (fileItem.getSize() > 0) {
            if (useBase64(pipelineContext, fileItem)) {
                // The content of the file is streamed to the output (xs:base64Binary)
                InputStream fileItemInputStream = null;
                try {
                    fileItemInputStream = fileItem.getInputStream();
                    XMLUtils.inputStreamToBase64Characters(fileItemInputStream, contentHandler);
                } catch (IOException e) {
                    throw new OXFException(e);
                } finally {
                    if (fileItemInputStream != null) {
                        try {
                            fileItemInputStream.close();
                        } catch (IOException e) {
                            throw new OXFException(e);
                        }
                    }
                }
            } else {
                // Only a reference to the file is output (xs:anyURI)
                DefaultFileItem defaultFileItem = (DefaultFileItem) fileItem;
                String uri;
                if (!fileItem.isInMemory()) {
                    // File must exist on disk since isInMemory() returns false
                    File file = defaultFileItem.getStoreLocation();
                    try {
                        uri = file.toURL().toExternalForm();
                    } catch (MalformedURLException e) {
                        throw new OXFException(e);
                    }
                } else {
                    // File does not exist on disk, must convert
                    try {
                        uri = XMLUtils.inputStreamToAnyURI(pipelineContext, fileItem.getInputStream());
                    } catch (IOException e) {
                        throw new OXFException(e);
                    }
                }
                char[] chars = uri.toCharArray();
                contentHandler.characters(chars, 0, chars.length);
            }
        }
    }

    private Document readRequestAsDOM4J(PipelineContext pipelineContext, Node config) {
        // Get complete request document from pipeline context, or create it if not there
        Context context = getContext(pipelineContext);
        if (context.wholeRequest == null)
            context.wholeRequest = readWholeRequestAsDOM4J(pipelineContext);
        Document result = (Document) context.wholeRequest.clone();

        // Filter the request based on the config input
        filterRequestDocument(result, config);

        return result;
    }

    private Document readWholeRequestAsDOM4J(PipelineContext context) {
        return readRequestAsDOM4J(context, null, true);
    }

    private void addTextElement(Element element, String elementName, String text) {
        if (text != null)
            element.addElement(elementName).addText(text);
    }

    private Document readRequestAsDOM4J(PipelineContext context, Node config, boolean all) {
        ExternalContext.Request request = getRequest(context);

        Document document = new NonLazyUserDataDocument();
        Element requestElement = document.addElement("request");
        if (all || XPathUtils.selectSingleNode(config, "/config/container-type") != null)
            addTextElement(requestElement, "container-type", request.getContainerType());
        if (all || XPathUtils.selectSingleNode(config, "/config/character-encoding") != null)
            addTextElement(requestElement, "character-encoding", request.getCharacterEncoding());
        if (all || XPathUtils.selectSingleNode(config, "/config/content-length") != null)
            addTextElement(requestElement, "content-length", Integer.toString(request.getContentLength()));
        if (all || XPathUtils.selectSingleNode(config, "/config/parameters") != null)
            addParameters(context, requestElement, request);
        if (all || XPathUtils.selectSingleNode(config, "/config/body") != null)
            addBody(context, requestElement);
        if (all || XPathUtils.selectSingleNode(config, "/config/protocol") != null)
            addTextElement(requestElement, "protocol", request.getProtocol());
        if (all || XPathUtils.selectSingleNode(config, "/config/remote-addr") != null)
            addTextElement(requestElement, "remote-addr", request.getRemoteAddr());
        if (all || XPathUtils.selectSingleNode(config, "/config/remote-host") != null)
            addTextElement(requestElement, "remote-host", request.getRemoteHost());
        if (all || XPathUtils.selectSingleNode(config, "/config/scheme") != null)
            addTextElement(requestElement, "scheme", request.getScheme());
        if (all || XPathUtils.selectSingleNode(config, "/config/server-name") != null)
            addTextElement(requestElement, "server-name", request.getServerName());
        if (all || XPathUtils.selectSingleNode(config, "/config/server-port") != null)
            addTextElement(requestElement, "server-port", Integer.toString(request.getServerPort()));
        if (all || XPathUtils.selectSingleNode(config, "/config/is-secure") != null)
            addTextElement(requestElement, "is-secure", new Boolean(request.isSecure()).toString());
        if (all || XPathUtils.selectSingleNode(config, "/config/auth-type") != null)
            addTextElement(requestElement, "auth-type", request.getAuthType());
        if (all || XPathUtils.selectSingleNode(config, "/config/context-path") != null)
            addTextElement(requestElement, "context-path", request.getContextPath());
        if (all || XPathUtils.selectSingleNode(config, "/config/headers") != null)
            addHeaders(context, requestElement, request);
        if (all || XPathUtils.selectSingleNode(config, "/config/method") != null)
            addTextElement(requestElement, "method", request.getMethod());
        if (all || XPathUtils.selectSingleNode(config, "/config/path-info") != null)
            addTextElement(requestElement, "path-info", request.getPathInfo());
        if (all || XPathUtils.selectSingleNode(config, "/config/path-translated") != null)
            addTextElement(requestElement, "path-translated", request.getPathTranslated());
        if (all || XPathUtils.selectSingleNode(config, "/config/query-string") != null)
            addTextElement(requestElement, "query-string", request.getQueryString());
        if (all || XPathUtils.selectSingleNode(config, "/config/remote-user") != null)
            addTextElement(requestElement, "remote-user", request.getRemoteUser());
        if (all || XPathUtils.selectSingleNode(config, "/config/requested-session-id") != null)
            addTextElement(requestElement, "requested-session-id", request.getRequestedSessionId());
        if (all || XPathUtils.selectSingleNode(config, "/config/request-uri") != null)
            addTextElement(requestElement, "request-uri", request.getRequestURI());
        if (all || XPathUtils.selectSingleNode(config, "/config/servlet-path") != null)
            addTextElement(requestElement, "servlet-path", request.getServletPath());
        if (all || XPathUtils.selectSingleNode(config, "/config/request-path") != null) {
            addTextElement(requestElement, "request-path", request.getRequestPath());
        }
        return document;
    }

    private ExternalContext.Request getRequest(PipelineContext context) {
        ExternalContext externalContext = (ExternalContext) context.getAttribute(PipelineContext.EXTERNAL_CONTEXT);
        if (externalContext == null)
            throw new OXFException("Missing external context");
        ExternalContext.Request request = externalContext.getRequest();
        return request;
    }

    private String MARK_ATTRIBUTE = "mark";
    private String MARK_ATTRIBUTE_VALUE = "true";

    private void markAncestors(Element element) {
        if (element.attribute(MARK_ATTRIBUTE) == null) {
            element.addAttribute(MARK_ATTRIBUTE, MARK_ATTRIBUTE_VALUE);
            Element parent = element.getParent();
            if (parent != null)
                markAncestors(parent);
        }
    }

    private void markDescendants(Element element) {
        for (Iterator i = element.elementIterator(); i.hasNext();) {
            Element e = (Element) i.next();
            if (e.attribute(MARK_ATTRIBUTE) == null)
                e.addAttribute(MARK_ATTRIBUTE, MARK_ATTRIBUTE_VALUE);
            markDescendants(e);
        }
    }

    private void prune(Element element) {
        Attribute a = element.attribute(MARK_ATTRIBUTE);
        if (a == null) {
            element.detach();
        } else {
            element.remove(a);
            List remove = new ArrayList();
            for (Iterator i = element.elementIterator(); i.hasNext();) {
                Element e = (Element) i.next();
                a = e.attribute(MARK_ATTRIBUTE);
                if (a == null)
                    remove.add(e);
                else
                    prune(e);
            }
            for (Iterator i = remove.iterator(); i.hasNext();)
                ((Element) i.next()).detach();
        }
    }


    private Document filterRequestDocument(Document requestDocument, Node config) {

        // We always want to keep the root element (even if nothing is selected)
        requestDocument.getRootElement().addAttribute(MARK_ATTRIBUTE, MARK_ATTRIBUTE_VALUE);

        for (Iterator i = XPathUtils.selectIterator(config, "/config/*"); i.hasNext();) {
            Element includeExcludeElement = (Element) i.next();
            LocationData locationData = (LocationData) includeExcludeElement.getData();
            String includeExcludeXPath = includeExcludeElement.getText();
            if (includeExcludeElement.getName().equals(INCLUDE_ELEMENT)) {
                for (Iterator j = XPathUtils.selectIterator(requestDocument, includeExcludeXPath); j.hasNext();) {
                    Element e = referencedElement((Node) j.next(), locationData, includeExcludeXPath);
                    markAncestors(e);
                    markDescendants(e);
                }
            } else if (includeExcludeElement.getName().equals(EXCLUDE_ELEMENT)) {
                for (Iterator j = XPathUtils.selectIterator(requestDocument, includeExcludeXPath); j.hasNext();) {
                    Element e = referencedElement((Node) j.next(), locationData, includeExcludeXPath);
                    e.detach();
                }
            }
        }

        // Prune unnecessary nodes
        prune(requestDocument.getRootElement());

        return requestDocument;
    }

    private Element referencedElement(Node node, LocationData locationData, String includeExcludeXPath) {
        if (node instanceof Element) {
            return (Element) node;
        } else if (node instanceof Document) {
            return ((Document) node).getRootElement();
        } else {
            throw new ValidationException("XPath expression '" + includeExcludeXPath + "' cannot reference a '"
                    + node.getClass().getName() + "'", locationData);
        }
    }

    /**
     * Add parameters to the request element. The parameters are also all stored in the pipeline
     * context if they are not already present. The parameter map supports the regular String[], but
     * also FileItem objects.
     */
    protected void addParameters(PipelineContext pipelineContext, Element requestElement, final ExternalContext.Request request) {
        // Obtain parameters from external context
        Map parametersMap = request.getParameterMap();
        // Check if there is at least one file upload and set this information in the pipeline context
        for (Iterator i = parametersMap.values().iterator(); i.hasNext();) {
            if (i.next() instanceof FileItem) {
                getContext(pipelineContext).hasUpload = true;
                break;
            }
        }
        // Add parameters elements
        addElements(pipelineContext, requestElement, parametersMap, "parameters", "parameter");
    }

    protected void addHeaders(PipelineContext pipelineContext, Element requestElement, ExternalContext.Request request) {
        addElements(pipelineContext, requestElement, request.getHeaderValuesMap(), "headers", "header");
    }

    protected void addElements(PipelineContext pipelineContext, Element requestElement, Map map, String name1, String name2) {
        Element parametersElement = requestElement.addElement(name1);
        for (Iterator i = map.keySet().iterator(); i.hasNext();) {
            Element parameterElement = parametersElement.addElement(name2);
            // Always create the name element
            String name = (String) i.next();
            parameterElement.addElement("name").addText(name);

            Object value = map.get(name);
            if (value instanceof String[]) {
                // Simple parameter
                String[] values = (String[]) value;
                for (int j = 0; j < values.length; j++) {
                    parameterElement.addElement("value").addText(values[j]);
                }
            } else if (value instanceof FileItem) {
                // Retrieve the FileItem (only for parameters)
                FileItem fileItem = (FileItem) value;

                // Set meta-information element
                if (fileItem.getName() != null)
                    parameterElement.addElement("filename").addText(fileItem.getName());
                if (fileItem.getContentType() != null)
                    parameterElement.addElement("content-type").addText(fileItem.getContentType());
                parameterElement.addElement("content-length").addText(Long.toString(fileItem.getSize()));

                if (fileItem.getSize() > 0) {
                    // Create private placeholder element with parameter name as attribute
                    Element fileItemElement = parameterElement.addElement(FILE_ITEM_ELEMENT, REQUEST_PRIVATE_NAMESPACE_URI);
                    fileItemElement.addAttribute(PARAMETER_NAME_ATTRIBUTE, name);
                } else {
                    // Just generate an empty "value" element
                    parameterElement.addElement("value");
                }
            } else {
                throw new OXFException("Invalid value type.");
            }
        }
    }

    protected void addBody(PipelineContext pipelineContext, Element requestElement) {
        // This just adds a placeholder element
        requestElement.addElement("body");
    }

    public void reset(PipelineContext context) {
        setState(context, new State());
    }

    private static class State extends DigestState {
        public QName requestedStreamType;
        public boolean bodyRequested;
        public Document requestDocument;
    }

    private static Context getContext(PipelineContext pipelineContext) {
        Context context = (Context) pipelineContext.getAttribute(PipelineContext.REQUEST_GENERATOR_CONTEXT);
        if (context == null) {
            context = new Context();
            pipelineContext.setAttribute(PipelineContext.REQUEST_GENERATOR_CONTEXT, context);
        }
        return context;
    }

    /**
     * This context is kept in the PipelineContext so that if multiple Request generators are run,
     * common information is reused.
     */
    private static class Context {
        public Document wholeRequest;
        public boolean hasUpload;
        public FileItem bodyFileItem;
    }

    public static int getMaxSizeProperty() {
        OXFProperties.PropertySet propertySet = OXFProperties.instance().getPropertySet(XMLConstants.REQUEST_PROCESSOR_QNAME);
        Integer maxSizeProperty = propertySet.getInteger(RequestGenerator.MAX_UPLOAD_SIZE_PROPERTY);
        int maxSize = (maxSizeProperty != null) ? maxSizeProperty.intValue() : RequestGenerator.DEFAULT_MAX_UPLOAD_SIZE;
        return maxSize;
    }

    public static int getMaxMemorySizeProperty() {
        OXFProperties.PropertySet propertySet = OXFProperties.instance().getPropertySet(XMLConstants.REQUEST_PROCESSOR_QNAME);
        Integer maxMemorySizeProperty = propertySet.getInteger(RequestGenerator.MAX_UPLOAD_MEMORY_SIZE_PROPERTY);
        int maxMemorySize = (maxMemorySizeProperty != null) ? maxMemorySizeProperty.intValue() : RequestGenerator.DEFAULT_MAX_UPLOAD_MEMORY_SIZE;
        return maxMemorySize;
    }
}
