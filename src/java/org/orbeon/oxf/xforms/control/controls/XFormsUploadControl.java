/**
 *  Copyright (C) 2006 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.control.controls;

import org.dom4j.Element;
import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.XFormsContainingDocument;
import org.orbeon.oxf.xforms.XFormsInstance;
import org.orbeon.oxf.xforms.XFormsControls;
import org.orbeon.oxf.xforms.processor.XFormsServer;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.control.XFormsValueControl;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.util.NetUtils;
import org.orbeon.oxf.processor.ProcessorUtils;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.saxon.om.NodeInfo;
import org.apache.commons.lang.StringUtils;

import java.io.File;
import java.io.IOException;
import java.net.URI;

/**
 * Represents an xforms:upload control.
 */
public class XFormsUploadControl extends XFormsValueControl {

    private Element mediatypeElement;
    private Element filenameElement;
    private Element sizeElement;

    private String state;
    private String mediatype;
    private String size;
    private String filename;


    public XFormsUploadControl(XFormsContainingDocument containingDocument, XFormsControl parent, Element element, String name, String id) {
        super(containingDocument, parent, element, name, id);
        mediatypeElement = element.element(XFormsConstants.XFORMS_MEDIATYPE_ELEMENT_QNAME);
        filenameElement = element.element(XFormsConstants.XFORMS_FILENAME_ELEMENT_QNAME);
        sizeElement = element.element(XFormsConstants.XXFORMS_SIZE_ELEMENT_QNAME);
    }


    public void evaluate(PipelineContext pipelineContext) {
        super.evaluate(pipelineContext);

        this.state = getState(pipelineContext);
        this.mediatype = getMediatype(pipelineContext);
        this.size = getSize(pipelineContext);
        this.filename  = getFilename(pipelineContext);
    }

    public void setExternalValue(PipelineContext pipelineContext, String value, String type, boolean handleTemporaryFiles){

        try {
            final String newValue;
            if (handleTemporaryFiles) {
                final String oldValue = getValue();
                // Try to delete temporary file if old value was temp URI and new value is different
                if (oldValue != null && NetUtils.urlHasProtocol(oldValue) && !oldValue.equals(value)) {
                    final File file = new File(new URI(oldValue));
                    if (file.exists()) {
                        final boolean success = file.delete();
                        try {
                            if (!success)
                                XFormsServer.logger.debug("XForms - cannot delete temporary file upon upload: " + file.getCanonicalPath());
                            else
                                XFormsServer.logger.debug("XForms - deleted temporary file upon upload: " + file.getCanonicalPath());
                        } catch (IOException e) {
                        }
                    }
                }

                if (ProcessorUtils.XS_ANYURI_EXPLODED_QNAME.equals(type) && value != null && NetUtils.urlHasProtocol(value)) {
                    // If we upload a new URI in the background, then don't delete the temporary file
                    final String newPath;
                    {
                        final File newFile = File.createTempFile("xforms_upload_", null);
                        newPath = newFile.getCanonicalPath();
                        newFile.delete();
                    }
                    final File oldFile = new File(new URI(value));
                    final File newFile = new File(newPath);
                    final boolean success = oldFile.renameTo(newFile);
                    try {
                        if (!success)
                            XFormsServer.logger.debug("XForms - cannot rename temporary file upon upload: " + oldFile.getCanonicalPath() + " to " + newFile.getCanonicalPath());
                        else
                            XFormsServer.logger.debug("XForms - renamed temporary file upon upload: " + oldFile.getCanonicalPath() + " to " + newFile.getCanonicalPath());
                    } catch (IOException e) {
                    }
                    // Try to delete the file on exit and on session termination
                    {
                        newFile.deleteOnExit();
                        final ExternalContext externalContext = (ExternalContext) pipelineContext.getAttribute(PipelineContext.EXTERNAL_CONTEXT);
                        final ExternalContext.Session session = externalContext.getSession(false);
                        session.addListener(new ExternalContext.Session.SessionListener() {
                            public void sessionDestroyed() {
                                final boolean success = newFile.delete();
                                try {
                                    if (!success)
                                        XFormsServer.logger.debug("XForms - cannot delete temporary file upon session destruction: " + newFile.getCanonicalPath());
                                    else
                                        XFormsServer.logger.debug("XForms - deleted temporary file upon session destruction: " + newFile.getCanonicalPath());
                                } catch (IOException e) {
                                }
                            }
                        });
                    }
                    newValue = newFile.toURL().toExternalForm();
                } else {
                    newValue = value;
                }
            } else {
                newValue = value;
            }

            super.setExternalValue(pipelineContext, newValue, type);
        } catch (Exception e) {
            throw new OXFException(e);
        }
    }

    public String getState() {
        return state;
    }

    public String getMediatype() {
        return mediatype;
    }

    public String getSize() {
        return size;
    }

    public String getFilename() {
        return filename;
    }

    public String getState(PipelineContext pipelineContext) {
        final boolean isEmpty = getValue() == null || getValue().length() == 0;
        return isEmpty ? "empty" : "file";
    }

    public String getMediatype(PipelineContext pipelineContext) {
        if (mediatypeElement == null)
            return null;
        else
            return getInfoValue(pipelineContext, mediatypeElement);
    }

    public String getFilename(PipelineContext pipelineContext) {
        if (filenameElement == null)
            return null;
        else
            return getInfoValue(pipelineContext, filenameElement);
    }

    public String getSize(PipelineContext pipelineContext) {
        if (sizeElement == null)
            return null;
        else
            return getInfoValue(pipelineContext, sizeElement);
    }

    private String getInfoValue(PipelineContext pipelineContext, Element element) {
        final XFormsControls xformsControls = containingDocument.getXFormsControls();
        xformsControls.setBinding(pipelineContext, this);
        xformsControls.pushBinding(pipelineContext, element);
        final NodeInfo currentSingleNode = xformsControls.getCurrentSingleNode();
        if (currentSingleNode == null) {
            return null;
        } else {
            final String value = XFormsInstance.getValueForNodeInfo(currentSingleNode);
            xformsControls.popBinding();
            return value;
        }
    }

    public void setMediatype(PipelineContext pipelineContext, String mediatype) {
        setInfoValue(pipelineContext, mediatypeElement, mediatype);
    }

    public void setFilename(PipelineContext pipelineContext, String filename) {
        // Depending on web browsers, the filename may contain a path or not.

        // Normalize below to just the file name.
        final String normalized = StringUtils.replace(filename, "\\", "/");
        final int index = normalized.lastIndexOf('/');
        final String justFileName = (index == -1) ? normalized : normalized.substring(index + 1);

        setInfoValue(pipelineContext, filenameElement, justFileName);
    }

    public void setSize(PipelineContext pipelineContext, String size) {
        setInfoValue(pipelineContext, sizeElement, size);
    }

    private void setInfoValue(PipelineContext pipelineContext, Element element, String value) {
        if (element == null || value == null)
            return;

        final XFormsControls xformsControls = containingDocument.getXFormsControls();
        xformsControls.setBinding(pipelineContext, this);
        xformsControls.pushBinding(pipelineContext, element);
        final NodeInfo currentSingleNode = xformsControls.getCurrentSingleNode();
        if (currentSingleNode != null) {
            XFormsInstance.setValueForNodeInfo(pipelineContext, currentSingleNode, value, null);
            xformsControls.popBinding();
        }
    }
}
