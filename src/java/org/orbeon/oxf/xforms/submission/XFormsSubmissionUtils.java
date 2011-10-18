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
package org.orbeon.oxf.xforms.submission;

import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.ContentBody;
import org.apache.http.entity.mime.content.InputStreamBody;
import org.apache.http.entity.mime.content.StringBody;
import org.dom4j.*;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.resources.URLFactory;
import org.orbeon.oxf.util.IndentedLogger;
import org.orbeon.oxf.util.NetUtils;
import org.orbeon.oxf.util.StringBuilderWriter;
import org.orbeon.oxf.xforms.*;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.control.controls.XFormsUploadControl;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.saxon.om.Item;
import org.orbeon.saxon.om.NodeInfo;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;

/**
 * Utilities for XForms submission processing.
 */
public class XFormsSubmissionUtils {

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

    public static boolean isSuccessCode(int code) {
        // Accept any success code (in particular "201 Resource Created")
        return code >= 200 && code < 300;
    }

    /**
     * Check whether an XML sub-tree satisfies validity and required MIPs.
     *
     * @param indentedLogger        logger
     * @param startNode             node to check
     * @param recurse               whether to recurse into attributes and descendant nodes
     * @return                      true iif the sub-tree passes the checks
     */
    public static boolean isSatisfiesValid(final IndentedLogger indentedLogger, final Node startNode, boolean recurse) {

        if (recurse) {
            // Recurse into attributes and descendant nodes
            final boolean[] instanceSatisfiesValidRequired = new boolean[]{true};
            startNode.accept(new VisitorSupport() {

                public final void visit(Element element) {
                    final boolean valid = checkInstanceData(element);

                    instanceSatisfiesValidRequired[0] &= valid;

                    if (!valid && indentedLogger.isDebugEnabled()) {
                        indentedLogger.logDebug("", "found invalid element",
                            "element name", Dom4jUtils.elementToDebugString(element));
                    }
                }

                public final void visit(Attribute attribute) {
                    final boolean valid = checkInstanceData(attribute);

                    instanceSatisfiesValidRequired[0] &= valid;

                    if (!valid && indentedLogger.isDebugEnabled()) {
                        indentedLogger.logDebug("", "found invalid attribute",
                            "attribute name", Dom4jUtils.attributeToDebugString(attribute), "parent element", Dom4jUtils.elementToDebugString(attribute.getParent()));
                    }
                }

                private boolean checkInstanceData(Node node) {
                    return InstanceData.getValid(node);
                }
            });
            return instanceSatisfiesValidRequired[0];
        } else {
            // Just check the current node
            return InstanceData.getValid(startNode);
        }
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

        final StringBuilder sb = new StringBuilder(100);
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
     * @param document          XML document to submit
     * @return                  MultipartRequestEntity
     */
    public static MultipartEntity createMultipartFormData(final Document document) throws IOException {

        // Visit document
        final MultipartEntity multipartEntity = new MultipartEntity();
        document.accept(new VisitorSupport() {
            public final void visit(Element element) {
                try {
                    // Only care about elements

                    // Only consider leaves i.e. elements without children elements
                    final List children = element.elements();
                    if (children == null || children.size() == 0) {

                        final String value = element.getText();
                        {
                            // Got one!
                            final String localName = element.getName();
                            final QName nodeType = InstanceData.getType(element);

                            if (XMLConstants.XS_ANYURI_QNAME.equals(nodeType)) {
                                // Interpret value as xs:anyURI

                                if (InstanceData.getValid(element) && value.trim().length() > 0) {
                                    // Value is valid as per xs:anyURI
                                    // Don't close the stream here, as it will get read later when the MultipartEntity
                                    // we create here is written to an output stream
                                    addPart(multipartEntity, URLFactory.createURL(value).openStream(), element);
                                } else {
                                    // Value is invalid as per xs:anyURI
                                    // Just use the value as is (could also ignore it)
                                    multipartEntity.addPart(localName, new StringBody(value, Charset.forName("UTF-8")));
                                }

                            } else if (XMLConstants.XS_BASE64BINARY_QNAME.equals(nodeType)) {
                                // Interpret value as xs:base64Binary

                                if (InstanceData.getValid(element) && value.trim().length() > 0) {
                                    // Value is valid as per xs:base64Binary
                                    addPart(multipartEntity, new ByteArrayInputStream(NetUtils.base64StringToByteArray(value)), element);
                                } else {
                                    // Value is invalid as per xs:base64Binary
                                    // Just use the value as is (could also ignore it)
                                    multipartEntity.addPart(localName, new StringBody(value, Charset.forName("UTF-8")));
                                }
                            } else {
                                // Just use the value as is
                                multipartEntity.addPart(localName, new StringBody(value, Charset.forName("UTF-8")));
                            }
                        }
                    }
                } catch (UnsupportedEncodingException e) {
                    throw new OXFException(e);
                } catch (MalformedURLException e) {
                    throw new OXFException(e);
                } catch (IOException e) {
                    throw new OXFException(e);
                }
            }
        });

        return multipartEntity;
    }

    static private void addPart(MultipartEntity multipartEntity, InputStream inputStream, Element element) {
        // Gather mediatype and filename if known
        // NOTE: special MIP-like annotations were added just before re-rooting/pruning element. Those will be
        // removed during the next recalculate.
        final String mediatype = InstanceData.getTransientAnnotation(element, "xxforms-mediatype");
        final String filename = InstanceData.getTransientAnnotation(element, "xxforms-filename");
        ContentBody contentBody = new InputStreamBody(inputStream, mediatype, filename);
        multipartEntity.addPart(element.getName(), contentBody);

    }

    /**
     * Annotate the DOM with information about file name and mediatype provided by uploads if available.
     *
     * @param containingDocument    current XFormsContainingDocument
     * @param currentInstance       instance containing the nodes to check
     */
    public static void annotateBoundRelevantUploadControls(XFormsContainingDocument containingDocument, XFormsInstance currentInstance) {
        final XFormsControls xformsControls = containingDocument.getControls();
        final Map<String, XFormsControl> uploadControls = xformsControls.getCurrentControlTree().getUploadControls();
        if (uploadControls != null) {
            for (Object o: uploadControls.values()) {
                final XFormsUploadControl currentControl = (XFormsUploadControl) o;
                if (currentControl.isRelevant()) {
                    final Item controlBoundItem = currentControl.getBoundItem();
                    if (controlBoundItem instanceof NodeInfo) {
                        final NodeInfo controlBoundNodeInfo = (NodeInfo) controlBoundItem;
                        if (currentInstance == currentInstance.getModel(containingDocument).getInstanceForNode(controlBoundNodeInfo)) {
                            // Found one relevant upload control bound to the instance we are submitting
                            // NOTE: special MIP-like annotations were added just before re-rooting/pruning element. Those
                            // will be removed during the next recalculate.
                            final String fileName = currentControl.getFileName();
                            if (fileName != null) {
                                InstanceData.setTransientAnnotation(controlBoundNodeInfo, "xxforms-filename", fileName);
                            }
                            final String mediatype = currentControl.getFileMediatype();
                            if (mediatype != null) {
                                InstanceData.setTransientAnnotation(controlBoundNodeInfo, "xxforms-mediatype", mediatype);
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Returns whether there is at least one relevant upload control with pending upload bound to any node of the given instance.
     *
     * @param containingDocument    current XFormsContainingDocument
     * @param currentInstance       instance to check
     * @return                      true iif condition is satisfied
     */
    public static boolean hasBoundRelevantPendingUploadControls(XFormsContainingDocument containingDocument, XFormsInstance currentInstance) {
        if (containingDocument.countPendingUploads() > 0) { // don't bother if there is no pending upload
            final XFormsControls xformsControls = containingDocument.getControls();
            final Map uploadControls = xformsControls.getCurrentControlTree().getUploadControls();
            if (uploadControls != null) {
                for (Object o: uploadControls.values()) {
                    final XFormsUploadControl currentControl = (XFormsUploadControl) o;
                    if (currentControl.isRelevant() && containingDocument.isUploadPendingFor(currentControl)) {
                        final Item controlBoundItem = currentControl.getBoundItem();
                        if (controlBoundItem instanceof NodeInfo && currentInstance == currentInstance.getModel(containingDocument).getInstanceForNode((NodeInfo) controlBoundItem)) {
                            // Found one relevant upload control with pending upload bound to the instance we are submitting
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }
}

class ResponseAdapter implements ExternalContext.Response {

    private Object nativeResponse;
    private ExternalContext.Response response;

    private int status = 200;
    private String contentType;

    private StringBuilderWriter stringWriter;
    private PrintWriter printWriter;
    private ResponseAdapter.LocalByteArrayOutputStream byteStream;

    private InputStream inputStream;

    public ResponseAdapter(Object nativeResponse, ExternalContext.Response response) {
        this.nativeResponse = nativeResponse;
        this.response = response;
    }

    public int getResponseCode() {
        return status;
    }

    public String getContentType() {
        return contentType;
    }

    public InputStream getInputStream() {
        if (inputStream == null) {
            if (stringWriter != null) {
                final byte[] bytes;
                try {
                    bytes = stringWriter.getBuilder().toString().getBytes("utf-8");
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
            byteStream = new ResponseAdapter.LocalByteArrayOutputStream();
        return byteStream;
    }

    public PrintWriter getWriter() throws IOException {
        if (stringWriter == null) {
            stringWriter = new StringBuilderWriter();
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
        return response.rewriteActionURL(urlString);
    }

    public String rewriteRenderURL(String urlString) {
        return response.rewriteRenderURL(urlString);
    }

    public String rewriteActionURL(String urlString, String portletMode, String windowState) {
        return response.rewriteActionURL(urlString,  portletMode, windowState);
    }

    public String rewriteRenderURL(String urlString, String portletMode, String windowState) {
        return response.rewriteRenderURL(urlString, portletMode, windowState);
    }

    public String rewriteResourceURL(String urlString, int rewriteMode) {
        return response.rewriteResourceURL(urlString, rewriteMode);
    }

    public void sendError(int sc) throws IOException {
        this.status = sc;
    }

    public void sendRedirect(String pathInfo, Map parameters, boolean isServerSide, boolean isExitPortal) throws IOException {
    }

    public void setCaching(long lastModified, boolean revalidate, boolean allowOverride) {
    }

    public void setResourceCaching(long lastModified, long expires) {
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