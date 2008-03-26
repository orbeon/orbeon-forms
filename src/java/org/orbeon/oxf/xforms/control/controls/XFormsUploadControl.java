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
import org.orbeon.oxf.xforms.*;
import org.orbeon.oxf.xforms.event.events.XFormsDeselectEvent;
import org.orbeon.oxf.xforms.action.actions.XFormsSetvalueAction;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.control.XFormsValueControl;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.util.NetUtils;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.saxon.om.NodeInfo;
import org.apache.commons.lang.StringUtils;

import java.io.File;
import java.io.IOException;
import java.net.URI;

/**
 * Represents an xforms:upload control.
 *
 * @noinspection SimplifiableIfStatement
 */
public class XFormsUploadControl extends XFormsValueControl {

    private Element mediatypeElement;
    private Element filenameElement;
    private Element sizeElement;

    private boolean isStateEvaluated;
    private String state;
    private boolean isMediatypeEvaluated;
    private String mediatype;
    private boolean isSizeEvaluated;
    private String size;
    private boolean isFilenameEvaluated;
    private String filename;


    public XFormsUploadControl(XFormsContainingDocument containingDocument, XFormsControl parent, Element element, String name, String id) {
        super(containingDocument, parent, element, name, id);
        mediatypeElement = element.element(XFormsConstants.XFORMS_MEDIATYPE_ELEMENT_QNAME);
        filenameElement = element.element(XFormsConstants.XFORMS_FILENAME_ELEMENT_QNAME);
        sizeElement = element.element(XFormsConstants.XXFORMS_SIZE_ELEMENT_QNAME);
    }


    protected void evaluate(PipelineContext pipelineContext) {
        super.evaluate(pipelineContext);

        getState(pipelineContext);
        getMediatype(pipelineContext);
        getFilename(pipelineContext);
        getSize(pipelineContext);
    }

    public void storeExternalValue(PipelineContext pipelineContext, String value, String type) {

        // Set value and handle temporary files
        setExternalValue(pipelineContext, value, type, true);

        // If the value is being cleared, also clear the metadata
        if (value.equals("")) {
            setFilename(pipelineContext, "");
            setMediatype(pipelineContext, "");
            setSize(pipelineContext, "");
        }
    }

    public void setExternalValue(PipelineContext pipelineContext, String value, String type, boolean handleTemporaryFiles){

        final String oldValue = getValue(pipelineContext);

        if ((value == null || value.trim().equals("")) && !(oldValue == null || oldValue.trim().equals(""))) {
            // Consider that file got "deselected" in the UI
            containingDocument.dispatchEvent(pipelineContext, new XFormsDeselectEvent(this));
        }

        try {
            final String newValue;
            if (handleTemporaryFiles) {
                // Try to delete temporary file if old value was temp URI and new value is different
                if (oldValue != null && NetUtils.urlHasProtocol(oldValue) && !oldValue.equals(value)) {
                    final File file = new File(new URI(oldValue));
                    if (file.exists()) {
                        final boolean success = file.delete();
                        try {
                            final String message = success ? "deleted temporary file upon upload" : "could not delete temporary file upon upload";
                            containingDocument.logDebug("upload", message, new String[] { "path", file.getCanonicalPath() });
                        } catch (IOException e) {
                        }
                    }
                }

                if (XMLConstants.XS_ANYURI_EXPLODED_QNAME.equals(type) && value != null && NetUtils.urlHasProtocol(value)) {
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
                        final String message = success ? "renamed temporary file upon upload" : "could not rename temporary file upon upload";
                        containingDocument.logDebug("upload", message, new String[] { "from", oldFile.getCanonicalPath(), "to", newFile.getCanonicalPath() });
                    } catch (IOException e) {
                    }
                    // Try to delete the file on exit and on session termination
                    {
                        newFile.deleteOnExit();
                        final ExternalContext externalContext = (ExternalContext) pipelineContext.getAttribute(PipelineContext.EXTERNAL_CONTEXT);
                        final ExternalContext.Session session = externalContext.getSession(false);
                        if (session != null) {
                            session.addListener(new ExternalContext.Session.SessionListener() {
                                public void sessionDestroyed() {
                                    final boolean success = newFile.delete();
                                    try {
                                        final String message = success ? "deleted temporary file upon session destruction" : "could not delete temporary file upon session destruction";
                                        containingDocument.logDebug("upload", message, new String[] { "file", newFile.getCanonicalPath() });
                                    } catch (IOException e) {
                                    }
                                }
                            });
                        } else {
                            containingDocument.logDebug("upload", "no existing session found so cannot register temporary file deletion upon session destruction",
                                    new String[] { "file", newFile.getCanonicalPath() });
                        }
                    }
                    newValue = newFile.toURI().toString();
                } else {
                    newValue = value;
                }
            } else {
                newValue = value;
            }

            // Call the super method
            super.storeExternalValue(pipelineContext, newValue, type);
        } catch (Exception e) {
            throw new OXFException(e);
        }
    }

    public String getState(PipelineContext pipelineContext) {
        if (!isStateEvaluated) {
            final boolean isEmpty = getValue(pipelineContext) == null || getValue(pipelineContext).length() == 0;
            state = isEmpty ? "empty" : "file";
            isStateEvaluated = true;
        }
        return state;
    }

    public String getMediatype(PipelineContext pipelineContext) {
        if (!isMediatypeEvaluated) {
            mediatype = (mediatypeElement == null) ? null : getInfoValue(pipelineContext, mediatypeElement);
            isMediatypeEvaluated = true;
        }
        return mediatype;
    }

    public String getFilename(PipelineContext pipelineContext) {
        if (!isFilenameEvaluated) {
            filename = (filenameElement == null) ? null : getInfoValue(pipelineContext, filenameElement);
            isFilenameEvaluated = true;
        }
        return filename;
    }

    public String getSize(PipelineContext pipelineContext) {
        if (!isSizeEvaluated) {
            size = (sizeElement == null) ? null : getInfoValue(pipelineContext, sizeElement);
            isSizeEvaluated = true;
        }
        return size;
    }

    private String getInfoValue(PipelineContext pipelineContext, Element element) {
        final XFormsContextStack contextStack = getContextStack();
        contextStack.setBinding(this);
        contextStack.pushBinding(pipelineContext, element);
        final NodeInfo currentSingleNode = contextStack.getCurrentSingleNode();
        if (currentSingleNode == null) {
            return null;
        } else {
            final String value = XFormsInstance.getValueForNodeInfo(currentSingleNode);
            contextStack.popBinding();
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

        final XFormsContextStack contextStack = getContextStack();
        contextStack.setBinding(this);
        contextStack.pushBinding(pipelineContext, element);
        final NodeInfo currentSingleNode = contextStack.getCurrentSingleNode();
        if (currentSingleNode != null) {
            XFormsSetvalueAction.doSetValue(pipelineContext, containingDocument, this, currentSingleNode, value, null, false);
            contextStack.popBinding();
        }
    }

    public boolean equalsExternal(PipelineContext pipelineContext, XFormsControl obj) {

        if (obj == null || !(obj instanceof XFormsUploadControl))
            return false;

        if (this == obj)
            return true;

        final XFormsUploadControl other = (XFormsUploadControl) obj;

        if (!compareStrings(getState(pipelineContext), other.getState(pipelineContext)))
            return false;
        if (!compareStrings(getMediatype(pipelineContext), other.getMediatype(pipelineContext)))
            return false;
        if (!compareStrings(getSize(pipelineContext), other.getSize(pipelineContext)))
            return false;
        if (!compareStrings(getFilename(pipelineContext), other.getFilename(pipelineContext)))
            return false;

        return super.equalsExternal(pipelineContext, obj);
    }
}
