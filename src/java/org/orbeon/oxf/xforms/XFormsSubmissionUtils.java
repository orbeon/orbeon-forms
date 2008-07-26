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

import org.dom4j.*;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.externalcontext.ForwardExternalContextRequestWrapper;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.util.ConnectionResult;
import org.orbeon.oxf.xforms.event.events.XFormsSubmitDoneEvent;
import org.orbeon.oxf.xforms.processor.XFormsServer;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.saxon.om.NodeInfo;

import java.io.*;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Utilities for XForms submission processing.
 */
public class XFormsSubmissionUtils {

    /**
     * Perform an optimized local connection using the Servlet API instead of using a URLConnection.
     */
    public static ConnectionResult openOptimizedConnection(PipelineContext pipelineContext, ExternalContext externalContext,
                                                                     ExternalContext.Response response,
                                                                     XFormsModelSubmission xformsModelSubmission,
                                                                     String httpMethod, final String action, String mediatype, boolean doReplace,
                                                                     byte[] messageBody, String queryString) {

        final XFormsContainingDocument containingDocument = (xformsModelSubmission != null) ? xformsModelSubmission.getContainingDocument() : null;
        try {
            // Case of empty body
            if (messageBody == null)
                messageBody = new byte[0];

            // Create requestAdapter depending on method
            final ForwardExternalContextRequestWrapper requestAdapter;
            final String effectiveResourceURI;
            {
                if (httpMethod.equals("POST") || httpMethod.equals("PUT")) {
                    // Simulate a POST or PUT
                    effectiveResourceURI = action;

                    if (XFormsServer.logger.isDebugEnabled())
                        XFormsContainingDocument.logDebugStatic(containingDocument, "submission", "setting request body",
                            new String[] { "body", new String(messageBody, "UTF-8") });

                    requestAdapter = new ForwardExternalContextRequestWrapper(externalContext.getRequest(),
                            effectiveResourceURI, httpMethod, (mediatype != null) ? mediatype : XMLUtils.XML_CONTENT_TYPE, messageBody);
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
                    requestAdapter = new ForwardExternalContextRequestWrapper(externalContext.getRequest(),
                            effectiveResourceURI, httpMethod);
                }
            }

            if (XFormsServer.logger.isDebugEnabled())
                XFormsContainingDocument.logDebugStatic(containingDocument, "submission", "dispatching request",
                            new String[] { "effective resource URI (relative to servlet context)", effectiveResourceURI });

            final ExternalContext.RequestDispatcher requestDispatcher = externalContext.getRequestDispatcher(action);
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
            if (doReplace) {
                // "the event xforms-submit-done is dispatched"
                if (xformsModelSubmission != null)
                    xformsModelSubmission.getContainingDocument().dispatchEvent(pipelineContext, new XFormsSubmitDoneEvent(xformsModelSubmission, connectionResult.resourceURI, connectionResult.statusCode));
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

        final StringBuffer sb = new StringBuffer();
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

    public void sendRedirect(String pathInfo, Map parameters, boolean isServerSide, boolean isExitPortal) throws IOException {
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
