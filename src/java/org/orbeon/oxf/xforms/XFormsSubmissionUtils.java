/**
 *  Copyright (C) 2005 Orbeon, Inc.
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
package org.orbeon.oxf.xforms;

import org.apache.commons.fileupload.disk.DiskFileItem;
import org.apache.commons.httpclient.methods.multipart.*;
import org.apache.commons.httpclient.params.HttpMethodParams;
import org.dom4j.*;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.externalcontext.ForwardExternalContextRequestWrapper;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.util.ConnectionResult;
import org.orbeon.oxf.util.NetUtils;
import org.orbeon.oxf.xforms.control.controls.XFormsUploadControl;
import org.orbeon.oxf.xforms.event.events.XFormsSubmitDoneEvent;
import org.orbeon.oxf.xforms.processor.XFormsServer;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.saxon.om.FastStringBuffer;
import org.orbeon.saxon.om.NodeInfo;

import java.io.*;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Utilities for XForms submission processing.
 */
public class XFormsSubmissionUtils {

    // TODO: harmonize with regular HTTP submission headers configuration (property)
    public static String[] MINIMAL_HEADERS_TO_FORWARD = { "cookie", "authorization" };
    public static String[] STANDARD_HEADERS_TO_FORWARD = { "cookie", "authorization", "user-agent"};

    /**
     * Perform an optimized local connection using the Servlet API instead of using a URLConnection.
     */
    public static ConnectionResult openOptimizedConnection(PipelineContext pipelineContext, ExternalContext externalContext,
                                                           XFormsContainingDocument containingDocument,
                                                           XFormsModelSubmission xformsModelSubmission,
                                                           String httpMethod, final String action, boolean isNorewrite, String mediatype,
                                                           byte[] messageBody, String queryString,
                                                           boolean isReplaceAll, String[] headerNames) {

        // NOTE: This code does custom rewriting of the path on the action, taking into account whether
        // the page was produced through a filter in separate deployment or not.
        final boolean isContextRelative;
        final String effectiveAction;
        if (!isNorewrite) {
            // Must rewrite
            if (!containingDocument.getStaticState().isSeparateDeployment()) {
                // We are not in separate deployment, so keep path relative to the current servlet context
                isContextRelative = true;
                effectiveAction = action;
            } else {
                // We are in separate deployment, so prepend request context path and mark path as not relative to the current context`
                final String contextPath = containingDocument.getStaticState().getRequestContextPath();
                isContextRelative = false;
                effectiveAction = contextPath + action;
            }
        } else {
            // Must not rewrite anyway, so mark path as not relative to the current context
            isContextRelative = false;
            effectiveAction = action;
        }

        return XFormsSubmissionUtils.openOptimizedConnection(pipelineContext, externalContext, containingDocument.getResponse(),
                                xformsModelSubmission, httpMethod, effectiveAction, isContextRelative, mediatype,
                                messageBody, queryString, isReplaceAll, headerNames);
    }

    /**
     * Perform an optimized local connection using the Servlet API instead of using a URLConnection.
     */
    public static ConnectionResult openOptimizedConnection(PipelineContext pipelineContext, ExternalContext externalContext,
                                                           ExternalContext.Response response,
                                                           XFormsModelSubmission xformsModelSubmission,
                                                           String httpMethod, final String action, boolean isContextRelative, String mediatype,
                                                           byte[] messageBody, String queryString,
                                                           boolean isReplaceAll, String[] headerNames) {

        // Action must be an absolute path
        if (!action.startsWith("/"))
            throw new OXFException("Action does not start with a '/': " + action);

        final XFormsContainingDocument containingDocument = (xformsModelSubmission != null) ? xformsModelSubmission.getContainingDocument() : null;
        try {

            // Get dispatcher
            final ExternalContext.RequestDispatcher requestDispatcher = externalContext.getRequestDispatcher(action, isContextRelative);
            final boolean isDefaultContext = requestDispatcher.isDefaultContext();

            // Case of empty body
            if (messageBody == null)
                messageBody = new byte[0];

            // Destination context path is the context path of the current request, or the context path implied by the new URI
            final String destinationContextPath = isDefaultContext ? "" : isContextRelative ? externalContext.getRequest().getContextPath() : NetUtils.getFirstPathElement(action);

            // Create requestAdapter depending on method
            final ForwardExternalContextRequestWrapper requestAdapter;
            final String effectiveResourceURI;
            final String rootAdjustedResourceURI;
            {
                if (httpMethod.equals("POST") || httpMethod.equals("PUT")) {
                    // Simulate a POST or PUT
                    effectiveResourceURI = action;

                    if (XFormsServer.logger.isDebugEnabled())
                        XFormsContainingDocument.logDebugStatic(containingDocument, "submission", "setting request body",
                            new String[] { "body", new String(messageBody, "UTF-8") });

                    rootAdjustedResourceURI = isDefaultContext || isContextRelative ? effectiveResourceURI : NetUtils.removeFirstPathElement(effectiveResourceURI);
                    if (rootAdjustedResourceURI == null)
                        throw new OXFException("Action must start with a servlet context path: " + action);

                    requestAdapter = new ForwardExternalContextRequestWrapper(externalContext.getRequest(), destinationContextPath,
                            rootAdjustedResourceURI, httpMethod, (mediatype != null) ? mediatype : XMLUtils.XML_CONTENT_TYPE, messageBody, headerNames);
                } else {
                    // Simulate a GET or DELETE
                    {
                        final StringBuffer updatedActionStringBuffer = new StringBuffer(action);
                        if (queryString != null) {
                            if (action.indexOf('?') == -1)
                                updatedActionStringBuffer.append('?');
                            else
                                updatedActionStringBuffer.append('&');
                            updatedActionStringBuffer.append(queryString);
                        }
                        effectiveResourceURI = updatedActionStringBuffer.toString();
                    }

                    rootAdjustedResourceURI = isDefaultContext || isContextRelative ? effectiveResourceURI : NetUtils.removeFirstPathElement(effectiveResourceURI);
                    if (rootAdjustedResourceURI == null)
                        throw new OXFException("Action must start with a servlet context path: " + action);

                    requestAdapter = new ForwardExternalContextRequestWrapper(externalContext.getRequest(), destinationContextPath,
                            rootAdjustedResourceURI, httpMethod, headerNames);
                }
            }

            if (XFormsServer.logger.isDebugEnabled())
                XFormsContainingDocument.logDebugStatic(containingDocument, "submission", "dispatching request",
                            new String[] {
                                    "method", httpMethod,
                                    "mediatype", mediatype,
                                    "context path", destinationContextPath,
                                    "effective resource URI (original)", effectiveResourceURI,
                                    "effective resource URI (relative to servlet root)", rootAdjustedResourceURI
                            });

            final ConnectionResult connectionResult = new ConnectionResult(effectiveResourceURI) {
                public void close() {
                    if (getResponseInputStream() != null) {
                        try {
                            getResponseInputStream().close();
                        } catch (IOException e) {
                            throw new OXFException("Exception while closing input stream for action: " + action);
                        }
                    }
                }
            };
            if (isReplaceAll) {
                // "the event xforms-submit-done is dispatched"
                if (xformsModelSubmission != null)
                    xformsModelSubmission.getContainer(containingDocument).dispatchEvent(pipelineContext,
                            new XFormsSubmitDoneEvent(xformsModelSubmission, connectionResult.resourceURI, connectionResult.statusCode));
                // Just forward the reply
                // Reason we use a Response passed is for the case of replace="all" when XFormsContainingDocument provides a Response
                requestDispatcher.forward(requestAdapter, response != null ? response : externalContext.getResponse());
                connectionResult.dontHandleResponse = true;
            } else {
                // We must intercept the reply
                final ResponseAdapter responseAdapter = new ResponseAdapter(externalContext.getNativeResponse());
                requestDispatcher.include(requestAdapter, responseAdapter);

                // Get response information that needs to be forwarded

                // NOTE: Here, the resultCode is not propagated from the included resource
                // when including Servlets. Similarly, it is not possible to obtain the
                // included resource's content type or headers. Because of this we should not
                // use an optimized submission from within a servlet.
                connectionResult.statusCode = responseAdapter.getResponseCode();
                connectionResult.setResponseContentType(XMLUtils.XML_CONTENT_TYPE);
                connectionResult.setResponseInputStream(responseAdapter.getInputStream());
                connectionResult.responseHeaders = new HashMap();
                connectionResult.setLastModified(null);
            }

            return connectionResult;
        } catch (IOException e) {
            throw new OXFException(e);
        }
    }

    public static boolean isGet(String method) {
        return method.equals("get") || method.equals(XMLUtils.buildExplodedQName(XFormsConstants.XXFORMS_NAMESPACE_URI, "get"));
    }

    public static boolean isPost(String method) {
        return method.equals("post") || method.endsWith("-post") || method.equals(XMLUtils.buildExplodedQName(XFormsConstants.XXFORMS_NAMESPACE_URI, "post"));
    }

    public static boolean isPut(String method) {
        return method.equals("put") || method.equals(XMLUtils.buildExplodedQName(XFormsConstants.XXFORMS_NAMESPACE_URI, "put"));
    }

    public static boolean isDelete(String method) {
        return method.equals("delete") || method.equals(XMLUtils.buildExplodedQName(XFormsConstants.XXFORMS_NAMESPACE_URI, "delete"));
    }

    /**
     * Check whether an XML sub-tree satifies validity and required MIPs.
     *
     * @param containingDocument    current containing document (for logging)
     * @param startNode             node to recursively check
     * @param checkValid            whether to check validity
     * @param checkRequired         whether to check required
     * @return                      true iif the sub-tree passes the checks
     */
    public static boolean isSatisfiesValidRequired(final XFormsContainingDocument containingDocument, final Node startNode, boolean recurse, final boolean checkValid, final boolean checkRequired) {

        if (recurse) {
            // Recurse into attributes and descendant nodes
            final boolean[] instanceSatisfiesValidRequired = new boolean[]{true};
            startNode.accept(new VisitorSupport() {

                public final void visit(Element element) {
                    final boolean valid = checkInstanceData(element);

                    instanceSatisfiesValidRequired[0] &= valid;

                    if (!valid && XFormsServer.logger.isDebugEnabled()) {
                        containingDocument.logDebug("submission", "found invalid element",
                            new String[] { "element name", Dom4jUtils.elementToString(element) });
                    }
                }

                public final void visit(Attribute attribute) {
                    final boolean valid = checkInstanceData(attribute);

                    instanceSatisfiesValidRequired[0] &= valid;

                    if (!valid && XFormsServer.logger.isDebugEnabled()) {
                        containingDocument.logDebug("submission", "found invalid attribute",
                            new String[] { "attribute name", Dom4jUtils.attributeToString(attribute), "parent element", Dom4jUtils.elementToString(attribute.getParent()) });
                    }
                }

                private final boolean checkInstanceData(Node node) {
                    // Check "valid" MIP
                    if (checkValid && !InstanceData.getValid(node)) return false;
                    // Check "required" MIP
                    if (checkRequired) {
                        final boolean isRequired = InstanceData.getRequired(node);
                        if (isRequired) {
                            final String value = XFormsInstance.getValueForNode(node);
                            if (value.length() == 0) {
                                // Required and empty
                                return false;
                            }
                        }
                    }
                    return true;
                }
            });
            return instanceSatisfiesValidRequired[0];
        } else {
            // Just check the current node
            // Check "valid" MIP
            if (checkValid && !InstanceData.getValid(startNode)) return false;
            // Check "required" MIP
            if (checkRequired) {
                final boolean isRequired = InstanceData.getRequired(startNode);
                if (isRequired) {
                    final String value = XFormsInstance.getValueForNode(startNode);
                    if (value.length() == 0) {
                        // Required and empty
                        return false;
                    }
                }
            }
            return true;
        }
    }

    public static boolean isSatisfiesValidRequired(NodeInfo nodeInfo, boolean checkValid, boolean checkRequired) {
        // Check "valid" MIP
        if (checkValid && !InstanceData.getValid(nodeInfo)) return false;
        // Check "required" MIP
        if (checkRequired) {
            final boolean isRequired = InstanceData.getRequired(nodeInfo);
            if (isRequired) {
                final String value = XFormsInstance.getValueForNodeInfo(nodeInfo);
                if (value.length() == 0) {
                    // Required and empty
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Create an application/x-www-form-urlencoded string, encoded in UTF-8, based on the elements and text content
     * present in an XML document.
     *
     * @param document      document to analyze
     * @param separator     separator character
     * @return              application/x-www-form-urlencoded string
     */
    public static String createWwwFormUrlEncoded(final Document document, final String separator) {

        final FastStringBuffer sb = new FastStringBuffer(100);
        document.accept(new VisitorSupport() {
            public final void visit(Element element) {
                // We only care about elements

                final List children = element.elements();
                if (children == null || children.size() == 0) {
                    // Only consider leaves
                    final String text = element.getText();
                    if (text != null && text.length() > 0) {
                        // Got one!
                        final String localName = element.getName();

                        if (sb.length() > 0)
                            sb.append(separator);

                        try {
                            sb.append(URLEncoder.encode(localName, "UTF-8"));
                            sb.append('=');
                            sb.append(URLEncoder.encode(text, "UTF-8"));
                            // TODO: check if line breaks will be correcly encoded as "%0D%0A"
                        } catch (UnsupportedEncodingException e) {
                            // Should not happen: UTF-8 must be supported
                            throw new OXFException(e);
                        }
                    }
                }
            }
        });

        return sb.toString();
    }

    /**
     * Implement support for XForms 1.1 section "11.9.7 Serialization as multipart/form-data".
     *
     * @param pipelineContext   used only to access the request to remove temporary files
     * @param document          XML document to submit
     * @return                  MultipartRequestEntity
     * @throws IOException
     */
    public static MultipartRequestEntity createMultipartFormData(final PipelineContext pipelineContext, final XFormsContainingDocument containingDocument,
                                                                 final Document document) throws IOException {

        final List params = new ArrayList();

        // Visit document
        document.accept(new VisitorSupport() {
            public final void visit(Element element) {
                // Only care about elements

                // Only consider leaves i.e. elements without children elements
                final List children = element.elements();
                if (children == null || children.size() == 0) {

                    final String value = element.getText();
                    {
                        // Got one!
                        final String localName = element.getName();
                        final String nodeType = InstanceData.getType(element);

                        if (XMLConstants.XS_ANYURI_EXPLODED_QNAME.equals(nodeType)) {
                            // Interpret value as xs:anyURI

                            if (InstanceData.getValid(element) && value.trim().length() > 0) {
                                // Value is valid as per xs:anyURI
                                final DiskFileItem fileItem = (DiskFileItem) NetUtils.anyURIToFileItem(pipelineContext, value, NetUtils.REQUEST_SCOPE);
                                addFilePart(element, fileItem, params);
                            } else {
                                // Value is invalid as per xs:anyURI
                                // Just use the value as is (could also ignore it)
                                params.add(new StringPart(localName, value, "UTF-8"));
                            }

                        } else if (XMLConstants.XS_BASE64BINARY_EXPLODED_QNAME.equals(nodeType)) {
                            // Interpret value as xs:base64Binary

                            if (InstanceData.getValid(element) && value.trim().length() > 0) {
                                // Value is valid as per xs:base64Binary
                                final String localURI = NetUtils.base64BinaryToAnyURI(pipelineContext, value, NetUtils.REQUEST_SCOPE);
                                final DiskFileItem fileItem = (DiskFileItem) NetUtils.anyURIToFileItem(pipelineContext, localURI, NetUtils.REQUEST_SCOPE);
                                addFilePart(element, fileItem, params);
                            } else {
                                // Value is invalid as per xs:base64Binary
                                // Just use the value as is (could also ignore it)
                                params.add(new StringPart(localName, value, "UTF-8"));
                            }
                        } else {
                            // Just use the value as is
                            params.add(new StringPart(localName, value, "UTF-8"));
                        }
                    }
                }
            }
        });

        // Build multipart object
        final Part[] partsArray = new Part[params.size()];
        params.toArray(partsArray);
        return new MultipartRequestEntity(partsArray, new HttpMethodParams());
    }

    private static void addFilePart(Element element, DiskFileItem fileItem, List params) {
        try {
            // Gather mediatype and filename if known
            // NOTE: special MIP-like annotations were added just before re-rooting/pruning element. Those will be
            // removed during the next recalculate.
            final String mediatype = InstanceData.getCustom(element, "xxforms-mediatype");
            final String filename = InstanceData.getCustom(element, "xxforms-filename");

            // TODO: if filename == null, then name of fileItem is used, which is probably not what we want
            final FilePart filePart = new FilePart(element.getName(), new FilePartSource(filename, fileItem.getStoreLocation()), mediatype, null);
            if (mediatype == null || !mediatype.startsWith("text/")) {
                // Stupid Apache implementation sets a charset for all mediatypes, not only text types, even if we pass null above 
                filePart.setCharSet(null);
            }
            params.add(filePart);
        } catch (FileNotFoundException e) {
            throw new OXFException(e);
        }
    }
}

class ResponseAdapter implements ExternalContext.Response {

    private Object nativeResponse;

    private int status = 200;
    private String contentType;

    private StringWriter stringWriter;
    private PrintWriter printWriter;
    private LocalByteArrayOutputStream byteStream;

    private InputStream inputStream;

    public ResponseAdapter(Object nativeResponse) {
        this.nativeResponse = nativeResponse;
    }

    public int getResponseCode() {
        return status;
    }

    public String getContentType() {
        return contentType;
    }

    public Map getHeaders() {
        return null;
    }

    public InputStream getInputStream() {
        if (inputStream == null) {
            if (stringWriter != null) {
                final byte[] bytes;
                try {
                    bytes = stringWriter.getBuffer().toString().getBytes("utf-8");
                } catch (UnsupportedEncodingException e) {
                    throw new OXFException(e); // should not happen
                }
                inputStream = new ByteArrayInputStream(bytes, 0, bytes.length);
//                throw new OXFException("ResponseAdapter.getInputStream() does not yet support content written with getWriter().");
            } else if (byteStream != null) {
                inputStream = new ByteArrayInputStream(byteStream.getByteArray(), 0, byteStream.size());
            }
        }

        return inputStream;
    }

    public void addHeader(String name, String value) {
    }

    public boolean checkIfModifiedSince(long lastModified, boolean allowOverride) {
        return true;
    }

    public String getCharacterEncoding() {
        return null;
    }

    public String getNamespacePrefix() {
        return null;
    }

    public OutputStream getOutputStream() throws IOException {
        if (byteStream == null)
            byteStream = new LocalByteArrayOutputStream();
        return byteStream;
    }

    public PrintWriter getWriter() throws IOException {
        if (stringWriter == null) {
            stringWriter = new StringWriter();
            printWriter = new PrintWriter(stringWriter);
        }
        return printWriter;
    }

    public boolean isCommitted() {
        return false;
    }

    public void reset() {
    }

    public String rewriteActionURL(String urlString) {
        return null;
    }

    public String rewriteRenderURL(String urlString) {
        return null;
    }

    public String rewriteActionURL(String urlString, String portletMode, String windowState) {
        return null;
    }

    public String rewriteRenderURL(String urlString, String portletMode, String windowState) {
        return null;
    }

    public String rewriteResourceURL(String urlString, boolean absolute) {
        return null;
    }

    public String rewriteResourceURL(String urlString, int rewriteMode) {
        return null;
    }

    public void sendError(int sc) throws IOException {
        this.status = sc;
    }

    public void sendRedirect(String pathInfo, Map parameters, boolean isServerSide, boolean isExitPortal, boolean isNoRewrite) throws IOException {
    }

    public void setCaching(long lastModified, boolean revalidate, boolean allowOverride) {
    }

    public void setContentLength(int len) {
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public void setHeader(String name, String value) {
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public void setTitle(String title) {
    }

    private static class LocalByteArrayOutputStream extends ByteArrayOutputStream {
        public byte[] getByteArray() {
            return buf;
        }
    }

    public Object getNativeResponse() {
        return nativeResponse;
    }
}
