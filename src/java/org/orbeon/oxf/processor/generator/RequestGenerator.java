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

import org.apache.commons.fileupload.*;
import org.dom4j.*;
import org.dom4j.io.DocumentSource;
import org.orbeon.oxf.cache.OutputCacheKey;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.ProcessorImpl;
import org.orbeon.oxf.processor.ProcessorInputOutputInfo;
import org.orbeon.oxf.processor.ProcessorOutput;
import org.orbeon.oxf.processor.XMLConstants;
import org.orbeon.oxf.util.HttpServletRequestStub;
import org.orbeon.oxf.util.NetUtils;
import org.orbeon.oxf.util.SystemUtils;
import org.orbeon.oxf.xml.ForwardingContentHandler;
import org.orbeon.oxf.xml.TransformerUtils;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.oxf.xml.XPathUtils;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.sax.SAXResult;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

/**
 * The request generator works like this:
 *
 * o Read config DOM (or obtain it from state cache)
 * o Obtain request DOM
 *   o Read whole request as DOM (or obtain it from context cache)
 *     o If content-type is multipart/form-data, special handling takes place.
 *       o File items are read with commons upload, either in memory if small or on disk if large
 *       o Only references to the file items are stored in the DOM
 *   o Filter the DOM request based on config
 *   o Serialize the DOM into SAX
 *     o References to file items are treated specially during the serialization process
 *       o Small files are serialized, and the xs:base64Binary type is set
 *       o Large files are referenced, and the xs:anyURI type is set
 *
 *   o FIXME: Request body processing is badly implemented!!!
 *   o FIXME: The upload code checks that the total request body is not larger than the maximum
 *     upload size specified. But If many small files are uploaded, can we potentially use a lot
 *     of memory?
 *
 *   o NOTE: In theory, it should be possible to stream without going through
 *     the disk, but it would be harder to implement (especially dealing with XPath
 *     expressions parameter selection, if any).
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

    private static final Long VALIDITY = new Long(0);

    private static final String INCLUDE_ELEMENT = "include";
    private static final String EXCLUDE_ELEMENT = "exclude";

    private static final String FILE_ITEM_ELEMENT = "request:file-item";
    private static final String PARAMETER_NAME_ATTRIBUTE = "parameter-name";

    private static final Map prefixes = new HashMap();

    {
        prefixes.put("request", REQUEST_PRIVATE_NAMESPACE_URI);
    }

    //private static final String FILE_UPLOAD_TYPE_ANYURI = "xs:anyURI";
    //private static final String DEFAULT_FILE_UPLOAD_TYPE = FILE_UPLOAD_TYPE_ANYURI;

    public RequestGenerator() {
        addInputInfo(new ProcessorInputOutputInfo(INPUT_CONFIG, REQUEST_CONFIG_NAMESPACE_URI));
        addOutputInfo(new ProcessorInputOutputInfo(OUTPUT_DATA));
    }

    public ProcessorOutput createOutput(String name) {
        ProcessorOutput output = new ProcessorImpl.ProcessorOutputImpl(getClass(), name) {
            public void readImpl(final PipelineContext pipelineContext, ContentHandler contentHandler) {
                try {
                    final State state = getFilledOutState(pipelineContext);
                    final Context context = getContext(pipelineContext);
                    // Transform the resulting document into SAX
                    Transformer identity = TransformerUtils.getIdentityTransformer();
                    identity.transform(new DocumentSource(state.requestDocument), new SAXResult(new ForwardingContentHandler(contentHandler) {
                        public void startElement(String uri, String localname, String qName, Attributes attributes) throws SAXException {
                            if (REQUEST_PRIVATE_NAMESPACE_URI.equals(uri)) {
                                // Special treatment for this element
                                if (FILE_ITEM_ELEMENT.equals(qName)) {
                                    String parameterName = attributes.getValue(PARAMETER_NAME_ATTRIBUTE);
                                    FileItem fileItem = (FileItem) context.parameters.get(parameterName);
                                    if (fileItem.getSize() > 0) {
                                        if (fileItem.isInMemory()) {
                                            // The content of the file is streamed to the output (xs:base64Binary)
                                            InputStream fileItemInputStream = null;
                                            try {
                                                fileItemInputStream = fileItem.getInputStream();
                                                XMLUtils.inputStreamToBase64Characters(fileItemInputStream, getContentHandler());
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
                                            // File must exist on disk since isInMemory() returns false
                                            File file = defaultFileItem.getStoreLocation();
                                            try {
                                                URL fileURL = file.toURL();
                                                char[] chars = fileURL.toExternalForm().toCharArray();
                                                getContentHandler().characters(chars, 0, chars.length);
                                            } catch (MalformedURLException e) {
                                                throw new OXFException(e);
                                            }
                                        }
                                    }
                                }
                            } else {
                                super.startElement(uri, localname, qName, attributes);
                            }
                        }
                        public void endElement(String uri, String localname, String qName) throws SAXException {
                            if (REQUEST_PRIVATE_NAMESPACE_URI.equals(uri)) {
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

            protected Object getValidityImpl(PipelineContext context) {
                return VALIDITY;
            }

            protected OutputCacheKey getKeyImpl(PipelineContext context) {
                if (isInputInCache(context, INPUT_CONFIG)) {
                    return getFilledOutState(context).outputCacheKey;
                } else {
                    return null;
                }
            }

            /*
             * This is called from both readImpl and getKeyImpl. Based on the assumption that a
             * getKeyImpl will be followed soon by a readImpl if it fails, we compute the key here
             * and cache the config input and its derivatives.
             */
            private State getFilledOutState(PipelineContext pipelineContext) {
                State state = (State) getState(pipelineContext);

                // Set request document
                if (state.requestDocument == null) {
                    Document config = readCacheInputAsDOM4J(pipelineContext, INPUT_CONFIG);
                    //String fileUploadType = XPathUtils.selectStringValue(config, "/config/file-upload/type");
                    //state.fileUploadType = (fileUploadType == null) ? DEFAULT_FILE_UPLOAD_TYPE : fileUploadType;
                    state.requestDocument = readRequestAsDOM4J(pipelineContext, config);
                }

                // Set cache key, unless there is at least one upload
                Context context = getContext(pipelineContext);
                if (state.outputCacheKey == null && !context.hasUpload) {
                    // NOTE: In theory, we could cache by digesting the uploaded files as well
                    state.outputCacheKey = new OutputCacheKey(getOutputByName(OUTPUT_DATA), new String(XMLUtils.getDigest(state.requestDocument)));
                }
                return state;
            }
        };
        addOutput(name, output);
        return output;
    }

    private Document readRequestAsDOM4J(PipelineContext pipelineContext, Node config) {
        try {
            // Get complete request document from pipeline context, or create it if not there
            Context context = getContext(pipelineContext);
            if (context.wholeRequest == null)
                context.wholeRequest = readWholeRequestAsDOM4J(pipelineContext);
            Document result = (Document) context.wholeRequest.clone();

            // Filter the request based on the config input
            filterRequestDocument(result, config);

            // Read and parse body and append the body content as XML under /request/body

            // FIXME: This is quite a hack! We only handle XML. We don't handle encodings. Also,
            // if the body is large, we need to stream. Also, this will conflict with reading
            // parameters as only request.getInputStream() or request.getReader() may be called, and
            // only once.
            if (config.selectSingleNode("/config/include[string(.) = '/request/body']") != null) {
                ExternalContext.Request request = getRequest(pipelineContext);
                Reader reader = request.getReader();
                // Read in String as SAX API only take an InputStream
                // (we don't want to look charset information)
                StringBuffer bodyString = new StringBuffer();
                char[] buffer = new char[1024];
                while (true) {
                    int length = reader.read(buffer);
                    if (length == -1) break;
                    bodyString.append(buffer, 0, length);
                }
                Element bodyElement = result.getRootElement().addElement("body");
                try {
                    Document body = DocumentHelper.parseText(bodyString.toString());
                    bodyElement.add(body.getRootElement());
                } catch (DocumentException e) {
                    bodyElement.addAttribute(new QName("nil", new Namespace(XMLUtils.XSI_PREFIX, XMLUtils.XSI_NAMESPACE)), "true");
                }
            }

            return result;
        } catch (IOException e) {
            throw new OXFException(e);
        }
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

        Document document = XMLUtils.createDOM4JDocument();
        Element requestElement = document.addElement("request");
        if (all || XPathUtils.selectSingleNode(config, "/config/container-type") != null)
            addTextElement(requestElement, "container-type", request.getContainerType());
        if (all || XPathUtils.selectSingleNode(config, "/config/character-encoding") != null)
            addTextElement(requestElement, "character-encoding", request.getCharacterEncoding());
        if (all || XPathUtils.selectSingleNode(config, "/config/content-length") != null)
            addTextElement(requestElement, "content-length", Integer.toString(request.getContentLength()));
        if (all || XPathUtils.selectSingleNode(config, "/config/parameters") != null)
            addParameters(context, requestElement, request);
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
        try {
            Context context = getContext(pipelineContext);
            Map parametersMap = context.parameters;
            if (parametersMap == null) {
                // No two Request generators can work on the same request at the same time
                // We don't really have concurrency within the same pipeline, but it may be coming soon
                synchronized (RequestGenerator.class) {
                    if (parametersMap == null) { // need to recheck within synchronized block

                        if (request.getContentType() != null && request.getContentType().startsWith("multipart/form-data")) {
                            // Special handling for multipart/form-data

                            // Setup commons upload
                            DiskFileUpload upload = new DiskFileUpload();
                            Integer maxSizeProperty = getPropertySet().getInteger(MAX_UPLOAD_SIZE_PROPERTY);
                            int maxSize = (maxSizeProperty != null) ? maxSizeProperty.intValue() : DEFAULT_MAX_UPLOAD_SIZE;

                            Integer maxMemorySizeProperty = getPropertySet().getInteger(MAX_UPLOAD_MEMORY_SIZE_PROPERTY);
                            int maxMemorySize = (maxMemorySizeProperty != null) ? maxMemorySizeProperty.intValue() : DEFAULT_MAX_UPLOAD_MEMORY_SIZE;

                            parametersMap = new HashMap();
                            final Map _parametersMap = parametersMap;

                            // Add a listener to destroy file items when the pipeline context is destroyed
                            pipelineContext.addContextListener(new PipelineContext.ContextListenerAdapter() {
                                public void contextDestroyed(boolean success) {
                                    if (_parametersMap != null) {
                                        for (Iterator i = _parametersMap.keySet().iterator(); i.hasNext();) {
                                            String name = (String) i.next();
                                            Object value = _parametersMap.get(name);
                                            if (value instanceof FileItem) {
                                                FileItem fileItem = (FileItem) value;
                                                fileItem.delete();
                                            }
                                        }
                                    }
                                }
                            });

                            // Wrap and implement just the required methods for the upload code
                            HttpServletRequest wrapper = new HttpServletRequestStub() {

                                public String getHeader(String s) {
                                    return ("content-type".equalsIgnoreCase(s)) ? request.getContentType() : null;
                                }

                                public int getContentLength() {
                                    return request.getContentLength();
                                }

                                public ServletInputStream getInputStream() throws IOException {
                                    // NOTE: The upload code does not actually check that it
                                    // doesn't read more than the content-length sent by the client!
                                    // Maybe here would be a good place to put an interceptor and
                                    // make sure we don't read too much.
                                    final InputStream is = request.getInputStream();
                                    return new ServletInputStream() {
                                        public int read() throws IOException {
                                            return is.read();
                                        }
                                    };
                                }
                            };

                            // Parse the request and add file information
                            try {
                                for (Iterator i = upload.parseRequest(wrapper, maxMemorySize, maxSize, SystemUtils.getTemporaryDirectory().getPath()).iterator(); i.hasNext();) {
                                    FileItem fileItem = (FileItem) i.next();
                                    if (fileItem.isFormField()) {
                                        // Simple form filled: add value to existing values, if any
                                        NetUtils.addValueToStringArrayMap(parametersMap, fileItem.getFieldName(), fileItem.getString());
                                    } else {
                                        // It is a file, store the FileItem object
                                        parametersMap.put(fileItem.getFieldName(), fileItem);
                                        context.hasUpload = true;
                                    }
                                }
                            } catch (FileUploadBase.SizeLimitExceededException e) {
                                // Should we do something smart so we can use the Presentation
                                // Server error page anyway? Right now, this is going to fail
                                // miserably with an error.
                                throw e;
                            }

                        } else {
                            // Just use request parameters
                            parametersMap = request.getParameterMap();
                        }
                        context.parameters = parametersMap;
                    }
                }
            }
            addElements(pipelineContext, requestElement, parametersMap, "parameters", "parameter");
        } catch (FileUploadException e) {
            throw new OXFException(e);
        }
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

                // Create value element with type information
                Element valueElement = parameterElement.addElement("value");
                if (fileItem.getSize() > 0) {
                    valueElement.addNamespace(XMLConstants.XSD_PREFIX, XMLConstants.XSD_URI);
                    valueElement.addAttribute(XMLConstants.XSI_TYPE_QNAME, fileItem.isInMemory() ? XMLConstants.BASE64BINARY_TYPE : XMLConstants.ANYURI_TYPE);
                    Element fileItemElement = valueElement.addElement(FILE_ITEM_ELEMENT, REQUEST_PRIVATE_NAMESPACE_URI);
                    fileItemElement.addAttribute(PARAMETER_NAME_ATTRIBUTE, name);
                }
            } else {
                throw new OXFException("Invalid value type.");
            }
        }
    }

    public void reset(PipelineContext context) {
        setState(context, new State());
    }

    private Context getContext(PipelineContext pipelineContext) {
        Context context = (Context) pipelineContext.getAttribute(PipelineContext.REQUEST_GENERATOR_CONTEXT);
        if (context == null) {
            context = new Context();
            pipelineContext.setAttribute(PipelineContext.REQUEST_GENERATOR_CONTEXT, context);
        }
        return context;
    }

    /**
     * We store in the state the request document (output of this processor) and
     * its key. This information is stored to be reused by readImpl() after a
     * getKeyImpl() in the same pipeline context, or vice versa.
     */
    private static class State {
        public OutputCacheKey outputCacheKey;
        public Document requestDocument;
        //public String fileUploadType;
    }

    private static class Context {
        public Document wholeRequest;
        public Map parameters;
        public boolean hasUpload;
    }
}
