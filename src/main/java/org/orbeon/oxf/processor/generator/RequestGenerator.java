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
package org.orbeon.oxf.processor.generator;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.disk.DiskFileItem;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.dom4j.*;
import org.dom4j.io.DocumentSource;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.pipeline.api.XMLReceiver;
import org.orbeon.oxf.processor.ProcessorImpl;
import org.orbeon.oxf.processor.ProcessorInputOutputInfo;
import org.orbeon.oxf.processor.ProcessorOutput;
import org.orbeon.oxf.processor.impl.DigestState;
import org.orbeon.oxf.processor.impl.DigestTransformerOutputImpl;
import org.orbeon.oxf.properties.Properties;
import org.orbeon.oxf.properties.PropertySet;
import org.orbeon.oxf.util.NetUtils;
import org.orbeon.oxf.util.SystemUtils;
import org.orbeon.oxf.xml.*;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.orbeon.oxf.xml.dom4j.NonLazyUserDataDocument;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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

    public static final String REQUEST_CONFIG_NAMESPACE_URI = "http://orbeon.org/oxf/xml/request-config";
    private static final String REQUEST_PRIVATE_NAMESPACE_URI = "http://orbeon.org/oxf/xml/request-private";

    // Maximum upload size
    private static final int DEFAULT_MAX_UPLOAD_SIZE = 1024 * 1024;
    private static final String MAX_UPLOAD_SIZE_PROPERTY = "max-upload-size";

    // Maximum size kept in memory
    private static final int DEFAULT_MAX_UPLOAD_MEMORY_SIZE = 10 * 1024;
    private static final String MAX_UPLOAD_MEMORY_SIZE_PROPERTY = "max-upload-memory-size";

    private static final String INCLUDE_ELEMENT = "include";
    private static final String EXCLUDE_ELEMENT = "exclude";

    private static final String FILE_ITEM_ELEMENT = "request:file-item";
    private static final String PARAMETER_NAME_ATTRIBUTE = "parameter-name";
    private static final String PARAMETER_POSITION_ATTRIBUTE = "parameter-position";
    private static final String REQUEST_GENERATOR_CONTEXT = "request-generator-context";

    private static final String BODY_REQUEST_ATTRIBUTE = "orbeon.request.body.url";

    public RequestGenerator() {
        addInputInfo(new ProcessorInputOutputInfo(INPUT_CONFIG, REQUEST_CONFIG_NAMESPACE_URI));
        addOutputInfo(new ProcessorInputOutputInfo(OUTPUT_DATA));
    }

    @Override
    public ProcessorOutput createOutput(String name) {
        final ProcessorOutput output = new DigestTransformerOutputImpl(RequestGenerator.this, name) {
            public void readImpl(final PipelineContext pipelineContext, XMLReceiver xmlReceiver) {
                final State state = (State) getFilledOutState(pipelineContext);
                // Transform the resulting document into SAX

                TransformerUtils.sourceToSAX(new DocumentSource(state.requestDocument), new ForwardingXMLReceiver(xmlReceiver) {
                    @Override
                    public void startElement(String uri, String localname, String qName, Attributes attributes) throws SAXException {
                        try {
                            if (REQUEST_PRIVATE_NAMESPACE_URI.equals(uri)) {
                                // Special treatment for this element
                                if (FILE_ITEM_ELEMENT.equals(qName)) {
                                    // Marker for file item

                                    final String parameterName = attributes.getValue(PARAMETER_NAME_ATTRIBUTE);
                                    final int parameterPosition = Integer.parseInt(attributes.getValue(PARAMETER_POSITION_ATTRIBUTE));
                                    final FileItem fileItem = (FileItem) ((Object[]) getRequest(pipelineContext).getParameterMap().get(parameterName))[parameterPosition];

                                    final AttributesImpl newAttributes = new AttributesImpl();
                                    super.startPrefixMapping(XMLConstants.XSI_PREFIX, XMLConstants.XSI_URI);
                                    super.startPrefixMapping(XMLConstants.XSD_PREFIX, XMLConstants.XSD_URI);
                                    newAttributes.addAttribute(XMLConstants.XSI_URI, "type", "xsi:type", "CDATA",
                                            useBase64(pipelineContext, fileItem) ? XMLConstants.XS_BASE64BINARY_QNAME.getQualifiedName(): XMLConstants.XS_ANYURI_QNAME.getQualifiedName());
                                    super.startElement("", "value", "value", newAttributes);
                                    writeFileItem(pipelineContext, fileItem, state.isSessionScope, getXMLReceiver());
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
                                final Context context = getContext(pipelineContext);
                                if (context.bodyFileItem != null || getRequest(pipelineContext).getInputStream() != null) {

                                    final ExternalContext.Request request = getRequest(pipelineContext);

                                    if (context.bodyFileItem == null) {
                                        final FileItem fileItem = new DiskFileItemFactory(getMaxMemorySizeProperty(), SystemUtils.getTemporaryDirectory()).createItem("dummy", "dummy", false, null);
                                        pipelineContext.addContextListener(new PipelineContext.ContextListenerAdapter() {
                                            public void contextDestroyed(boolean success) {
                                                fileItem.delete();
                                            }
                                        });
                                        final OutputStream outputStream = fileItem.getOutputStream();
                                        NetUtils.copyStream(request.getInputStream(), outputStream);
                                        outputStream.close();
                                        context.bodyFileItem = fileItem;
                                    }
                                    // Serialize the stream into the body element
                                    final AttributesImpl newAttributes = new AttributesImpl();
                                    super.startPrefixMapping(XMLConstants.XSI_PREFIX, XMLConstants.XSI_URI);
                                    super.startPrefixMapping(XMLConstants.XSD_PREFIX, XMLConstants.XSD_URI);
                                    newAttributes.addAttribute(XMLConstants.XSI_URI, "type", "xsi:type", "CDATA",
                                            useBase64(pipelineContext, context.bodyFileItem) ? XMLConstants.XS_BASE64BINARY_QNAME.getQualifiedName(): XMLConstants.XS_ANYURI_QNAME.getQualifiedName());
                                    super.startElement(uri, localname, qName, newAttributes);
                                    final String uriOrNull = writeFileItem(pipelineContext, context.bodyFileItem, state.isSessionScope, getXMLReceiver());
                                    super.endElement(uri, localname, qName);
                                    super.endPrefixMapping(XMLConstants.XSD_PREFIX);
                                    super.endPrefixMapping(XMLConstants.XSI_PREFIX);

                                    // If the body is available as a URL, store it into the request. This is done so that
                                    // native code can access the body even if it has been read already. Possibly, this
                                    // could be handled more transparently by ExternalContext, so that
                                    // Request.getInputStream() works even upon multiple reads.
                                    if (uriOrNull != null)
                                        request.getAttributesMap().put(BODY_REQUEST_ATTRIBUTE, uriOrNull);
                                }
                            } else {
                                super.startElement(uri, localname, qName, attributes);
                            }
                        } catch (IOException e) {
                            throw new OXFException(e);
                        }
                    }
                    @Override
                    public void endElement(String uri, String localname, String qName) throws SAXException {
                        if (REQUEST_PRIVATE_NAMESPACE_URI.equals(uri) || localname.equals("body") && uri.equals("")) {
                            // Ignore end element
                        } else {
                            super.endElement(uri, localname, qName);
                        }
                    }
                });
            }

            protected boolean fillOutState(PipelineContext pipelineContext, DigestState digestState) {
                final State state = (State) digestState;
                if (state.requestDocument == null) {
                    // Read config document
                    final Document config = readCacheInputAsDOM4J(pipelineContext, INPUT_CONFIG);

                    // Try to find stream-type attribute
                    final QName streamTypeQName = Dom4jUtils.extractAttributeValueQName(config.getRootElement(), "stream-type");
                    if (streamTypeQName != null && !(streamTypeQName.equals(XMLConstants.XS_BASE64BINARY_QNAME) || streamTypeQName.equals(XMLConstants.XS_ANYURI_QNAME)))
                        throw new OXFException("Invalid value for stream-type attribute: " + streamTypeQName.getQualifiedName());
                    state.requestedStreamType = streamTypeQName;
                    state.isSessionScope = "session".equals(config.getRootElement().attributeValue("stream-scope"));

                    // Read and store request
                    state.requestDocument = readRequestAsDOM4J(pipelineContext, config);

                    // Check if the body was requested
                    state.bodyRequested = XPathUtils.selectSingleNode(state.requestDocument, "/*/body") != null;
                }
                final Context context = getContext(pipelineContext);
                return !context.hasUpload && !state.bodyRequested;
            }

            protected byte[] computeDigest(PipelineContext pipelineContext, DigestState digestState) {
                final State state = (State) digestState;
                return XMLUtils.getDigest(new DocumentSource(state.requestDocument));
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

    private String writeFileItem(PipelineContext pipelineContext, FileItem fileItem, boolean isSessionScope, ContentHandler contentHandler) throws SAXException {
        if (!isFileItemEmpty(fileItem)) {
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
                final DiskFileItem diskFileItem = (DiskFileItem) fileItem;
                final String uriExpiringWithRequest;
                if (!fileItem.isInMemory()) {
                    // File must exist on disk since isInMemory() returns false
                    final File file = diskFileItem.getStoreLocation();
                    uriExpiringWithRequest = file.toURI().toString();
                } else {
                    // File does not exist on disk, must convert
                    // NOTE: Conversion occurs every time this method is called. Not optimal.
                    try {
                        uriExpiringWithRequest = NetUtils.inputStreamToAnyURI(fileItem.getInputStream(), NetUtils.REQUEST_SCOPE);
                    } catch (IOException e) {
                        throw new OXFException(e);
                    }
                }

                // If the content is meant to expire with the session, and we haven't yet renamed the file, then do this here.
                final String uriExpiringWithScope;
                if (isSessionScope) {
                    final String tempSessionURI = getContext(pipelineContext).getSessionURIForRequestURI(uriExpiringWithRequest);
                    if (tempSessionURI == null) {
                        uriExpiringWithScope = NetUtils.renameAndExpireWithSession(uriExpiringWithRequest, logger).toURI().toString();
                        getContext(pipelineContext).putSessionURIForRequestURI(uriExpiringWithRequest, uriExpiringWithScope);
                    } else
                        uriExpiringWithScope = tempSessionURI;
                } else
                    uriExpiringWithScope = uriExpiringWithRequest;

                final char[] chars = uriExpiringWithScope.toCharArray();
                contentHandler.characters(chars, 0, chars.length);

                return uriExpiringWithRequest;
            }
        }

        return null;
    }

    private Document readRequestAsDOM4J(PipelineContext pipelineContext, Node config) {
        // Get complete request document from pipeline context, or create it if not there
        final Context context = getContext(pipelineContext);
        if (context.wholeRequest == null)
            context.wholeRequest = readWholeRequestAsDOM4J(getRequest(pipelineContext), context);
        final Document result = (Document) context.wholeRequest.clone();

        // Filter the request based on the config input
        filterRequestDocument(result, config);

        return result;
    }

    private static void addTextElement(Element element, String elementName, String text) {
        if (text != null)
            element.addElement(elementName).addText(text);
    }

    public static Document readWholeRequestAsDOM4J(final ExternalContext.Request request, final Context context) {

        final Document document = new NonLazyUserDataDocument();
        final Element requestElement = document.addElement("request");

        addTextElement(requestElement, "container-type", request.getContainerType());
        addTextElement(requestElement, "container-namespace", request.getContainerNamespace());
        addTextElement(requestElement, "character-encoding", request.getCharacterEncoding());
        addTextElement(requestElement, "content-length", Integer.toString(request.getContentLength()));
        {
            final String contentType = request.getContentType();
            addTextElement(requestElement, "content-type", (contentType == null) ? "" : contentType);
        }
        addParameters(context, requestElement, request);
        addBody(requestElement);
        addTextElement(requestElement, "protocol", request.getProtocol());
        addTextElement(requestElement, "remote-addr", request.getRemoteAddr());
        // TODO: Handle this differently as it can take a long time to work
        addTextElement(requestElement, "remote-host", request.getRemoteHost());
        addTextElement(requestElement, "scheme", request.getScheme());
        addTextElement(requestElement, "server-name", request.getServerName());
        addTextElement(requestElement, "server-port", Integer.toString(request.getServerPort()));
        addTextElement(requestElement, "is-secure", Boolean.toString(request.isSecure()));
        addTextElement(requestElement, "auth-type", request.getAuthType());
        addTextElement(requestElement, "context-path", request.getContextPath());
        addHeaders(requestElement, request);
        addAttributes(requestElement, request);
        addTextElement(requestElement, "method", request.getMethod());
        addTextElement(requestElement, "path-info", request.getPathInfo());
        addTextElement(requestElement, "path-translated", request.getPathTranslated());
        addTextElement(requestElement, "query-string", request.getQueryString());
        addTextElement(requestElement, "remote-user", request.getRemoteUser());
        addTextElement(requestElement, "requested-session-id", request.getRequestedSessionId());
        addTextElement(requestElement, "request-uri", request.getRequestURI());
        addTextElement(requestElement, "request-url", request.getRequestURL());
        addTextElement(requestElement, "servlet-path", request.getServletPath());
        addTextElement(requestElement, "request-path", request.getRequestPath());

        // Portlet-specific features
        if (request.getWindowState() != null)
            addTextElement(requestElement, "window-state", request.getWindowState());
        if (request.getPortletMode() != null)
            addTextElement(requestElement, "portlet-mode", request.getPortletMode());

        return document;
    }

    private ExternalContext.Request getRequest(PipelineContext context) {
        ExternalContext externalContext = (ExternalContext) context.getAttribute(PipelineContext.EXTERNAL_CONTEXT);
        if (externalContext == null)
            throw new OXFException("Missing external context");
        return externalContext.getRequest();
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
        Attribute attribute = element.attribute(MARK_ATTRIBUTE);
        if (attribute == null) {
            element.detach();
        } else {
            element.remove(attribute);
            final List<Element> elementsToRemove = new ArrayList<Element>();
            for (Iterator i = element.elementIterator(); i.hasNext();) {
                final Element e = (Element) i.next();
                attribute = e.attribute(MARK_ATTRIBUTE);
                if (attribute == null)
                    elementsToRemove.add(e);
                else
                    prune(e);
            }
            for (final Element elementToRemove : elementsToRemove)
                elementToRemove.detach();
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
     * Add parameters to the request element. The parameters are also all stored in the pipeline context if they are not
     * already present. The parameter map supports Object[], which can contain String but also FileItem objects.
     */
    protected static void addParameters(Context context, Element requestElement, final ExternalContext.Request request) {
        // Obtain parameters from external context
        final Map<String, Object[]> parametersMap = request.getParameterMap();
        // Check if there is at least one file upload and set this information in the pipeline context
        if (context != null) {
            for (final Object[] values : parametersMap.values()) {
                for (Object value : values) {
                    if (value instanceof FileItem) {
                        context.hasUpload = true;
                        break;
                    }
                }
            }
        }
        // Add parameters elements
        addElements(requestElement, parametersMap, "parameters", "parameter");
    }

    protected static void addAttributes(Element requestElement, final ExternalContext.Request request) {
        // Add attributes elements
        addElements(requestElement, request.getAttributesMap(), "attributes", "attribute");
    }

    protected static void addHeaders(Element requestElement, ExternalContext.Request request) {
        addElements(requestElement, request.getHeaderValuesMap(), "headers", "header");
    }

    protected static void addElements(Element requestElement, Map<String, ?> map, String name1, String name2) {
        if (map.size() >= 0) {
            final Element parametersElement = requestElement.addElement(name1);
            for (final String name : map.keySet()) {

                final Element parameterElement = parametersElement.addElement(name2);
                // Always create the name element
                parameterElement.addElement("name").addText(name);

                final Object entryValue = map.get(name);
                final Object[] values;
                if (entryValue instanceof Object[]) {
                    values = (Object[]) entryValue;
                } else {
                    values = new Object[] { entryValue };
                }

                for (int j = 0; j < values.length; j++) {
                    final Object value = values[j];

                    if (value instanceof String) {
                        // Simple String parameter
                        parameterElement.addElement("value").addText((String) value);
                    } else if (value instanceof FileItem) {
                        // Retrieve the FileItem (only for parameters)
                        final FileItem fileItem = (FileItem) value;

                        // Set meta-information element
                        if (fileItem.getName() != null)
                            parameterElement.addElement("filename").addText(fileItem.getName());
                        if (fileItem.getContentType() != null)
                            parameterElement.addElement("content-type").addText(fileItem.getContentType());
                        parameterElement.addElement("content-length").addText(Long.toString(fileItem.getSize()));

                        if (!isFileItemEmpty(fileItem)) {
                            // Create private placeholder element with parameter name as attribute
                            final Element fileItemElement = parameterElement.addElement(FILE_ITEM_ELEMENT, REQUEST_PRIVATE_NAMESPACE_URI);
                            fileItemElement.addAttribute(PARAMETER_NAME_ATTRIBUTE, name);
                            fileItemElement.addAttribute(PARAMETER_POSITION_ATTRIBUTE, Integer.toString(j));
                        } else {
                            // Just generate an empty "value" element
                            parameterElement.addElement("value");
                        }
                    } else {
                        // ignore (needed in case of attributes, which can be any Java object)
                    }
                }
            }
        }
    }

    private static boolean isFileItemEmpty(FileItem fileItem) {
        return fileItem.getSize() <= 0 && (fileItem.getName() == null || fileItem.getName().trim().equals(""));
    }

    protected static void addBody(Element requestElement) {
        // This just adds a placeholder element
        requestElement.addElement("body");
    }

    @Override
    public void reset(PipelineContext context) {
        setState(context, new State());
    }

    private static class State extends DigestState {
        public QName requestedStreamType;
        public boolean bodyRequested;
        public Document requestDocument;
        public boolean isSessionScope;
    }

    private static Context getContext(PipelineContext pipelineContext) {
        Context context = (Context) pipelineContext.getAttribute(REQUEST_GENERATOR_CONTEXT);
        if (context == null) {
            context = new Context();
            pipelineContext.setAttribute(REQUEST_GENERATOR_CONTEXT, context);
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

        // Hold mapping from URI expiring w/ request (provided by Request object) to URI expiring w/ session (created here)
        private Map<String, String> uriMap;

        public String getSessionURIForRequestURI(String requestURI) {
            return (uriMap == null) ? null : uriMap.get(requestURI);
        }

        public void putSessionURIForRequestURI(String requestURI, String sessionURI) {
            if (uriMap == null)
                uriMap = new HashMap<String, String>();

            uriMap.put(requestURI, sessionURI);
        }
    }

    public static int getMaxSizeProperty() {
        PropertySet propertySet = Properties.instance().getPropertySet(XMLConstants.REQUEST_PROCESSOR_QNAME);
        Integer maxSizeProperty = propertySet.getInteger(RequestGenerator.MAX_UPLOAD_SIZE_PROPERTY);
        return (maxSizeProperty != null) ? maxSizeProperty.intValue() : RequestGenerator.DEFAULT_MAX_UPLOAD_SIZE;
    }

    public static int getMaxMemorySizeProperty() {
        PropertySet propertySet = org.orbeon.oxf.properties.Properties.instance().getPropertySet(XMLConstants.REQUEST_PROCESSOR_QNAME);
        Integer maxMemorySizeProperty = propertySet.getInteger(RequestGenerator.MAX_UPLOAD_MEMORY_SIZE_PROPERTY);
        return (maxMemorySizeProperty != null) ? maxMemorySizeProperty.intValue() : RequestGenerator.DEFAULT_MAX_UPLOAD_MEMORY_SIZE;
    }

    public static String getRequestBody(ExternalContext.Request request) {
        final Object result = request.getAttributesMap().get(BODY_REQUEST_ATTRIBUTE);
        return (result instanceof String) ? ((String) result) : null;
    }
}
