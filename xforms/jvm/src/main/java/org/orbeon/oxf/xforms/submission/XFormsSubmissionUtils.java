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
import org.orbeon.dom.Document;
import org.orbeon.dom.Element;
import org.orbeon.dom.QName;
import org.orbeon.dom.VisitorSupport;
import org.orbeon.io.CharsetNames;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.resources.URLFactory;
import org.orbeon.oxf.util.NetUtils;
import org.orbeon.oxf.util.StringUtils;
import org.orbeon.oxf.xforms.XFormsContainingDocument;
import org.orbeon.oxf.xforms.control.controls.XFormsUploadControl;
import org.orbeon.oxf.xforms.model.InstanceData;
import org.orbeon.oxf.xforms.model.XFormsInstance;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.saxon.om.NodeInfo;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.List;

/**
 * Utilities for XForms submission processing.
 */
public class XFormsSubmissionUtils {

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

                                if (InstanceData.getValid(element) && StringUtils.trimAllToEmpty(value).length() > 0) {
                                    // Value is valid as per xs:anyURI
                                    // Don't close the stream here, as it will get read later when the MultipartEntity
                                    // we create here is written to an output stream
                                    addPart(multipartEntity, URLFactory.createURL(value).openStream(), element, value);
                                } else {
                                    // Value is invalid as per xs:anyURI
                                    // Just use the value as is (could also ignore it)
                                    multipartEntity.addPart(localName, new StringBody(value, Charset.forName(CharsetNames.Utf8())));
                                }

                            } else if (XMLConstants.XS_BASE64BINARY_QNAME.equals(nodeType)) {
                                // Interpret value as xs:base64Binary

                                if (InstanceData.getValid(element) && StringUtils.trimAllToEmpty(value).length() > 0) {
                                    // Value is valid as per xs:base64Binary
                                    addPart(multipartEntity, new ByteArrayInputStream(NetUtils.base64StringToByteArray(value)), element, null);
                                } else {
                                    // Value is invalid as per xs:base64Binary
                                    // Just use the value as is (could also ignore it)
                                    multipartEntity.addPart(localName, new StringBody(value, Charset.forName(CharsetNames.Utf8())));
                                }
                            } else {
                                // Just use the value as is
                                multipartEntity.addPart(localName, new StringBody(value, Charset.forName(CharsetNames.Utf8())));
                            }
                        }
                    }
                } catch (IOException e) {
                    throw new OXFException(e);
                }
            }
        });

        return multipartEntity;
    }

    static private void addPart(MultipartEntity multipartEntity, InputStream inputStream, Element element, String url) {
        // Gather mediatype and filename if known
        // NOTE: special MIP-like annotations were added just before re-rooting/pruning element. Those will be
        // removed during the next recalculate.

        // See this WG action item (which was decided but not carried out): "Clarify that upload activation produces
        // content and possibly filename and mediatype info as metadata. If available, filename and mediatype are copied
        // to instance data if upload filename and mediatype elements are specified. At serialization, filename and
        // mediatype from instance data are used if upload filename and mediatype are specified; otherwise, filename and
        // mediatype are drawn from upload metadata, if they were available at time of upload activation"
        //
        // See:
        // http://lists.w3.org/Archives/Public/public-forms/2009May/0052.html
        // http://lists.w3.org/Archives/Public/public-forms/2009Apr/att-0010/2009-04-22.html#ACTION2
        //
        // See also this clarification:
        // http://lists.w3.org/Archives/Public/public-forms/2009May/0053.html
        // http://lists.w3.org/Archives/Public/public-forms/2009Apr/att-0003/2009-04-01.html#ACTION1
        //
        // The bottom line is that if we can find the xf:upload control bound to a node to submit, we try to get
        // metadata from that control. If that fails (which can be because the control is non-relevant, bound to another
        // control, or never had nested xf:filename/xf:mediatype elements), we try URL metadata. URL metadata is only
        // present on nodes written by xf:upload as temporary file: URLs. It is not present if the data is stored as
        // xs:base64Binary. In any case, metadata can be absent.
        //
        // If an xf:upload control saved data to a node as xs:anyURI, has xf:filename/xf:mediatype elements, is still
        // relevant and bound to the original node (as well as its children elements), and if the nodes pointed to by
        // the children elements have not been modified (e.g. by xf:setvalue), then retrieving the metadata via
        // xf:upload should be equivalent to retrieving it via the URL metadata.
        //
        // Benefits of URL metadata: a single xf:upload can be used to save data to multiple nodes over time, and it
        // doesn't have to be relevant and bound upon submission.
        //
        // Benefits of using xf:upload metadata: it is possible to modify the filename and mediatype subsequently.
        //
        // URL metadata was added 2012-05-29.

        // Get mediatype, first via xf:upload control, or, if not found, try URL metadata
        String mediatype = InstanceData.getTransientAnnotation(element, "xxforms-mediatype");
        if (mediatype == null && url != null)
            mediatype = XFormsUploadControl.getParameterOrNull(url, "mediatype");

        // Get filename, first via xf:upload control, or, if not found, try URL metadata
        String filename = InstanceData.getTransientAnnotation(element, "xxforms-filename");
        if (filename == null && url != null)
            filename = XFormsUploadControl.getParameterOrNull(url, "filename");

        final ContentBody contentBody = new InputStreamBody(inputStream, mediatype, filename);
        multipartEntity.addPart(element.getName(), contentBody);
    }

    /**
     * Annotate the DOM with information about file name and mediatype provided by uploads if available.
     *
     * @param containingDocument    current XFormsContainingDocument
     * @param currentInstance       instance containing the nodes to check
     */
    public static void annotateBoundRelevantUploadControls(XFormsContainingDocument containingDocument, XFormsInstance currentInstance) {
        for (XFormsUploadControl currentUploadControl : containingDocument.controls().getCurrentControlTree().getUploadControlsJava()) {
            if (currentUploadControl.isRelevant()) {
                final scala.Option<NodeInfo> controlBoundNodeOpt = currentUploadControl.boundNodeOpt();
                if (controlBoundNodeOpt.isDefined()) {
                    final NodeInfo controlBoundNodeInfo = controlBoundNodeOpt.get();
                    if (currentInstance == currentInstance.model().getInstanceForNode(controlBoundNodeInfo)) {
                        // Found one relevant upload control bound to the instance we are submitting
                        // NOTE: special MIP-like annotations were added just before re-rooting/pruning element. Those
                        // will be removed during the next recalculate.
                        final String fileName = currentUploadControl.boundFilename();
                        if (fileName != null) {
                            InstanceData.setTransientAnnotation(controlBoundNodeInfo, "xxforms-filename", fileName);
                        }
                        final String mediatype = currentUploadControl.boundFileMediatype();
                        if (mediatype != null) {
                            InstanceData.setTransientAnnotation(controlBoundNodeInfo, "xxforms-mediatype", mediatype);
                        }
                    }
                }
            }
        }
    }
}
