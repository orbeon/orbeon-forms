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
package org.orbeon.oxf.xforms.control.controls;

import org.apache.commons.lang.StringUtils;
import org.dom4j.Element;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.util.Multipart;
import org.orbeon.oxf.util.NetUtils;
import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.XFormsContainingDocument;
import org.orbeon.oxf.xforms.XFormsContextStack;
import org.orbeon.oxf.xforms.XFormsUtils;
import org.orbeon.oxf.xforms.analysis.XPathDependencies;
import org.orbeon.oxf.xforms.control.ExternalCopyable;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.control.XFormsValueControl;
import org.orbeon.oxf.xforms.event.XFormsEvent;
import org.orbeon.oxf.xforms.event.XFormsEvents;
import org.orbeon.oxf.xforms.event.events.XFormsDeselectEvent;
import org.orbeon.oxf.xforms.event.events.XXFormsUploadDoneEvent;
import org.orbeon.oxf.xforms.model.DataModel;
import org.orbeon.oxf.xforms.xbl.XBLContainer;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.saxon.om.Item;
import org.orbeon.saxon.om.NodeInfo;
import org.xml.sax.helpers.AttributesImpl;
import scala.Option;

import java.io.File;
import java.net.URI;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Represents an xforms:upload control.
 */
public class XFormsUploadControl extends XFormsValueControl {

    private FileInfo fileInfo;

    public XFormsUploadControl(XBLContainer container, XFormsControl parent, Element element, String name, String id, Map<String, Element> state) {
        super(container, parent, element, name, id);

        fileInfo = new FileInfo(this, getContextStack(), element);
    }

    @Override
    protected void evaluateImpl() {
        super.evaluateImpl();

        getState();
        getFileMediatype();
        getFileName();
        getFileSize();

        getProgressState();
        getProgressReceived();
        getProgressExpected();
    }

    @Override
    protected void markDirtyImpl(XPathDependencies xpathDependencies) {
        super.markDirtyImpl(xpathDependencies);
        fileInfo.markDirty();
    }

    @Override
    public void performTargetAction(XBLContainer container, XFormsEvent event) {

        // NOTE: Perform all actions at target, so that user event handlers are called after these operations.

        if (XFormsEvents.XXFORMS_UPLOAD_START.equals(event.getName())) {
            // Upload started
            containingDocument.startUpload(getUploadUniqueId());
        } else if (XFormsEvents.XXFORMS_UPLOAD_CANCEL.equals(event.getName())) {
            // Upload canceled
            containingDocument.endUpload(getUploadUniqueId());
            Multipart.removeUploadProgress(NetUtils.getExternalContext().getRequest(), this);
        } else if (XFormsEvents.XXFORMS_UPLOAD_DONE.equals(event.getName())) {
            // Upload done: process upload to this control

            // Notify that the upload has ended
            containingDocument.endUpload(getUploadUniqueId());
            Multipart.removeUploadProgress(NetUtils.getExternalContext().getRequest(), this);

            final XXFormsUploadDoneEvent uploadDoneEvent = (XXFormsUploadDoneEvent) event;
            handleUploadedFile(uploadDoneEvent.file(), uploadDoneEvent.filename(), uploadDoneEvent.mediatype(), uploadDoneEvent.size());

        } else if (XFormsEvents.XXFORMS_UPLOAD_PROGRESS.equals(event.getName())) {
            // NOP: upload progress information will be sent through the diff process
        } else
            super.performTargetAction(container, event);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Make sure to consider any upload associated with this control as ended
        containingDocument.endUpload(getUploadUniqueId());
    }

    public String getUploadUniqueId() {
        // TODO: Need to move to using actual unique ids here, see:
        // http://wiki.orbeon.com/forms/projects/core-xforms-engine-improvements#TOC-Improvement-to-client-side-server-s
        return getEffectiveId();
    }

    /**
     * Handle a construct of the form:
     *
     *   <xxforms:files>
     *     <parameter>
     *       <name>xforms-element-27</name>
     *       <filename>my-filename.jpg</filename>
     *       <content-type>image/jpeg</content-type>
     *       <content-length>33204</content-length>
     *       <value xmlns:request="http://orbeon.org/oxf/xml/request-private" xsi:type="xs:anyURI">file:/temp/upload_432dfead_11f1a983612__8000_00000107.tmp</value>
     *     </parameter>
     *     <parameter>
     *       ...
     *     </parameter>
     *   </xxforms:files>
     *
     * @param containingDocument    document
     * @param filesElement          <xxforms:files> element
     */
    public static void handleUploadedFiles(XFormsContainingDocument containingDocument, Element filesElement) {
        if (filesElement != null) {
            for (final Element parameterElement: Dom4jUtils.elements(filesElement)) {

                final XFormsUploadControl uploadControl; {
                    final String controlEffectiveId = parameterElement.element("name").getTextTrim();
                    uploadControl = (XFormsUploadControl) containingDocument.getObjectByEffectiveId(controlEffectiveId);

                    // In case of xforms:repeat, the name of the template will not match an existing control
                    // In addition, only set value on forControl control if specified
                    if (uploadControl == null)
                        continue;
                }

                final Element valueElement = parameterElement.element("value");
                final String value = valueElement.getTextTrim();

                final String filename;
                {
                    final Element filenameElement = parameterElement.element("filename");
                    filename = (filenameElement != null) ? filenameElement.getTextTrim() : "";
                }
                final String mediatype;
                {
                    final Element mediatypeElement = parameterElement.element("content-type");
                    mediatype = (mediatypeElement != null) ? mediatypeElement.getTextTrim() : "";
                }
                final String size = parameterElement.element("content-length").getTextTrim();

                if (size.equals("0") && filename.equals("")) {
                    // No file was selected in the UI
                } else {
                    // A file was selected in the UI (note that the file may be empty)
                    uploadControl.handleUploadedFile(value, filename, mediatype, size);
                }
            }
        }
    }

    private void handleUploadedFile(String value, String filename, String mediatype, String size) {
        if (!size.equals("0") || !filename.equals("")) {
            // Set value of uploaded file into the instance (will be xs:anyURI or xs:base64Binary)
            doStoreExternalValue(value);

            // Handle filename, mediatype and size if necessary
            setFilename(filename);
            setMediatype(mediatype);
            setSize(size);
        }
    }

    /**
     * Check if an <xxforms:files> element actually contains file uploads to process.
     *
     * @param filesElement  <xxforms:files> element
     * @return              true iif file uploads to process
     */
    public static boolean hasUploadedFiles(Element filesElement) {
        if (filesElement != null) {
            for (final Element parameterElement: Dom4jUtils.elements(filesElement)) {
                final String filename;
                {
                    final Element filenameElement = parameterElement.element("filename");
                    filename = (filenameElement != null) ? filenameElement.getTextTrim() : "";
                }
                final String size = parameterElement.element("content-length").getTextTrim();

                if (size.equals("0") && filename.equals("")) {
                    // No file was selected in the UI
                } else {
                    // A file was selected in the UI (note that the file may be empty)
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public void storeExternalValue(String value) {

        // Set value and handle temporary files
        doStoreExternalValue(value);

        // If the value is being cleared, also clear the metadata
        if (value.equals("")) {
            setFilename("");
            setMediatype("");
            setSize("");
        }
    }

    private boolean isFileURL(String url) {
        return NetUtils.urlHasProtocol(url) && url.startsWith("file:");
    }

    private void deleteFileIfPossible(String url) {
        if (isFileURL(url)) {
            try {
                final File file = new File(new URI(url));
                if (file.exists()) {
                    final boolean success = file.delete();

                    final String message = success ? "deleted temporary file upon upload" : "could not delete temporary file upon upload";
                    getIndentedLogger().logWarning("xforms:upload", message, "path", file.getCanonicalPath());
                }
            } catch (Exception e) {
                getIndentedLogger().logWarning("xforms:upload", "could not delete temporary file upon upload", "path", url);
            }
        }
    }

    private void doStoreExternalValue(String newValue) {

        // Clean values
        newValue = StringUtils.trimToEmpty(newValue);
        final String oldValue = StringUtils.trimToEmpty(getValue());

        try {
            // Only process if the new value is different from the old one
            if (isFileURL(newValue)) {
                // Setting new file

                final String convertedValue;
                final boolean isTargetBase64 = XMLConstants.XS_BASE64BINARY_QNAME.equals(getType()) || XFormsConstants.XFORMS_BASE64BINARY_QNAME.equals(getType());
                if (isTargetBase64) {
                    // Convert value to Base64 and delete incoming file
                    convertedValue = NetUtils.anyURIToBase64Binary(newValue);
                    deleteFileIfPossible(newValue);
                } else {
                    // Leave value as is and make file expire with session
                    final File newFile = NetUtils.renameAndExpireWithSession(newValue, getIndentedLogger().getLogger());
                    convertedValue = newFile.toURI().toString();
                }

                // Store the converted value
                super.storeExternalValue(convertedValue);

                // When a value is set, make sure associated file information is cleared, because even though the control might
                // not be re-evaluated, a submission might attempt to access file name, etc. information for bound upload
                // controls. A little tricky: can we find a better solution?
                fileInfo.markDirty();

            } else if (StringUtils.isEmpty(newValue)) {
                // Setting blank value
                if (StringUtils.isNotEmpty(oldValue)) {
                    // TODO: This should probably take place during refresh instead.
                    getXBLContainer().dispatchEvent(new XFormsDeselectEvent(containingDocument, this));
                }

                // Try to delete temporary file associated with old value if any
                deleteFileIfPossible(oldValue);

                // Store blank value
                super.storeExternalValue("");

                // When a value is set, make sure associated file information is cleared, because even though the control might
                // not be re-evaluated, a submission might attempt to access file name, etc. information for bound upload
                // controls. A little tricky: can we find a better solution?
                fileInfo.markDirty();


            } else {
                // Only accept file or blank
                throw new OXFException("Unexpected incoming value for xforms:upload: " + newValue);
            }
        } catch (Exception e) {
            throw new ValidationException(e, getLocationData());
        }
    }

    public String getState() {
        return fileInfo.getState();
    }

    public String getFileMediatype() {
        return fileInfo.getFileMediatype();
    }

    public String getFileName() {
        return fileInfo.getFileName();
    }

    public String getFileSize() {
        return fileInfo.getFileSize();
    }

    public String getProgressState() {
        return fileInfo.getProgressState();
    }

    public String getProgressReceived() {
        return fileInfo.getProgressCurrent();
    }

    public String getProgressExpected() {
        return fileInfo.getProgressExpected();
    }

    private void setMediatype(String mediatype) {
        fileInfo.setMediatype(mediatype);
    }

    private void setFilename(String filename) {
        fileInfo.setFilename(filename);
    }

    private void setSize(String size) {
        fileInfo.setSize(size);
    }

    @Override
    public boolean equalsExternal(XFormsControl other) {

        if (other == null || !(other instanceof XFormsUploadControl))
            return false;

        if (this == other)
            return true;

        final XFormsUploadControl otherUploadControl = (XFormsUploadControl) other;

        if (!XFormsUtils.compareStrings(getState(), otherUploadControl.getState()))
            return false;
        if (!XFormsUtils.compareStrings(getFileMediatype(), otherUploadControl.getFileMediatype()))
            return false;
        if (!XFormsUtils.compareStrings(getFileSize(), otherUploadControl.getFileSize()))
            return false;
        if (!XFormsUtils.compareStrings(getFileName(), otherUploadControl.getFileName()))
            return false;

        if (!XFormsUtils.compareStrings(getProgressState(), otherUploadControl.getProgressState()))
            return false;
        if (!XFormsUtils.compareStrings(getProgressReceived(), otherUploadControl.getProgressReceived()))
            return false;
        if (!XFormsUtils.compareStrings(getProgressExpected(), otherUploadControl.getProgressExpected()))
            return false;

        return super.equalsExternal(other);
    }

    @Override
    protected boolean addAjaxCustomAttributes(AttributesImpl attributesImpl, boolean isNewRepeatIteration, XFormsControl other) {

        final XFormsUploadControl uploadControl1 = (XFormsUploadControl) other;
        final XFormsUploadControl uploadControl2 = this;

        boolean added = false;
        {
            // State
            final String stateValue1 = (uploadControl1 == null) ? null : uploadControl1.getState();
            final String stateValue2 = uploadControl2.getState();

            if (!XFormsUtils.compareStrings(stateValue1, stateValue2)) {
                final String attributeValue = stateValue2 != null ? stateValue2 : "";
                added |= addAttributeIfNeeded(attributesImpl, "state", attributeValue, isNewRepeatIteration, attributeValue.equals(""));
            }
        }
        {
            // Mediatype
            final String mediatypeValue1 = (uploadControl1 == null) ? null : uploadControl1.getFileMediatype();
            final String mediatypeValue2 = uploadControl2.getFileMediatype();

            if (!XFormsUtils.compareStrings(mediatypeValue1, mediatypeValue2)) {
                final String attributeValue = mediatypeValue2 != null ? mediatypeValue2 : "";
                added |= addAttributeIfNeeded(attributesImpl, "mediatype", attributeValue, isNewRepeatIteration, attributeValue.equals(""));
            }
        }
        {
            // Filename
            final String filenameValue1 = (uploadControl1 == null) ? null : uploadControl1.getFileName();
            final String filenameValue2 = uploadControl2.getFileName();

            if (!XFormsUtils.compareStrings(filenameValue1, filenameValue2)) {
                final String attributeValue = filenameValue2 != null ? filenameValue2 : "";
                added |= addAttributeIfNeeded(attributesImpl, "filename", attributeValue, isNewRepeatIteration, attributeValue.equals(""));
            }
        }
        {
            // Size
            final String sizeValue1 = (uploadControl1 == null) ? null : uploadControl1.getFileSize();
            final String sizeValue2 = uploadControl2.getFileSize();

            if (!XFormsUtils.compareStrings(sizeValue1, sizeValue2)) {
                final String attributeValue = sizeValue2 != null ? sizeValue2 : "";
                added |= addAttributeIfNeeded(attributesImpl, "size", attributeValue, isNewRepeatIteration, attributeValue.equals(""));
            }
        }
        {
            // Progress state
            final String value1 = (uploadControl1 == null) ? null : uploadControl1.getProgressState();
            final String value2 = uploadControl2.getProgressState();

            if (!XFormsUtils.compareStrings(value1, value2)) {
                final String attributeValue = value2 != null ? value2 : "";
                added |= addAttributeIfNeeded(attributesImpl, "progress-state", attributeValue, isNewRepeatIteration, attributeValue.equals(""));
            }
        }
        {
            // Progress current
            final String progressReceivedValue1 = (uploadControl1 == null) ? null : uploadControl1.getProgressReceived();
            final String progressReceivedValue2 = uploadControl2.getProgressReceived();

            if (!XFormsUtils.compareStrings(progressReceivedValue1, progressReceivedValue2)) {
                final String attributeValue = progressReceivedValue2 != null ? progressReceivedValue2 : "";
                added |= addAttributeIfNeeded(attributesImpl, "progress-received", attributeValue, isNewRepeatIteration, attributeValue.equals(""));
            }
        }
        {
            // Progress total
            final String progressExpectedValue1 = (uploadControl1 == null) ? null : uploadControl1.getProgressExpected();
            final String progressExpectedValue2 = uploadControl2.getProgressExpected();

            if (!XFormsUtils.compareStrings(progressExpectedValue1, progressExpectedValue2)) {
                final String attributeValue = progressExpectedValue2 != null ? progressExpectedValue2 : "";
                added |= addAttributeIfNeeded(attributesImpl, "progress-expected", attributeValue, isNewRepeatIteration, attributeValue.equals(""));
            }
        }

        return added;
    }

    @Override
    public Object getBackCopy() {
        final XFormsUploadControl cloned = (XFormsUploadControl) super.getBackCopy();
        cloned.fileInfo = (FileInfo) fileInfo.getBackCopy();
        return cloned;
    }

    private static final Set<String> ALLOWED_EXTERNAL_EVENTS = new HashSet<String>();
    static {
        ALLOWED_EXTERNAL_EVENTS.add(XFormsEvents.DOM_FOCUS_IN);
        ALLOWED_EXTERNAL_EVENTS.add(XFormsEvents.DOM_FOCUS_OUT);
        ALLOWED_EXTERNAL_EVENTS.add(XFormsEvents.XFORMS_HELP);
        ALLOWED_EXTERNAL_EVENTS.add(XFormsEvents.XFORMS_SELECT);
        ALLOWED_EXTERNAL_EVENTS.add(XFormsEvents.XXFORMS_VALUE_CHANGE_WITH_FOCUS_CHANGE);
        // Do we need this?
//        ALLOWED_EXTERNAL_EVENTS.add(XFormsEvents.XXFORMS_VALUE_OR_ACTIVATE);// for noscript mode
        // NOTE: xxforms-upload-done is a trusted server event so doesn't need to be listed here
        ALLOWED_EXTERNAL_EVENTS.add(XFormsEvents.XXFORMS_UPLOAD_START);
        ALLOWED_EXTERNAL_EVENTS.add(XFormsEvents.XXFORMS_UPLOAD_CANCEL);
        ALLOWED_EXTERNAL_EVENTS.add(XFormsEvents.XXFORMS_UPLOAD_PROGRESS);
    }

    @Override
    protected Set<String> getAllowedExternalEvents() {
        return ALLOWED_EXTERNAL_EVENTS;
    }
}

/*
 * File information used e.g. by upload and output controls.
 */
class FileInfo implements ExternalCopyable {

    private XFormsValueControl control;
    private XFormsContextStack contextStack;

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

    private boolean isProgressStateEvaluated;
    private String progressState;
    private boolean isProgressReceivedEvaluated;
    private String progressReceived;
    private boolean isProgressExpectedEvaluated;
    private String progressExpected;

    FileInfo(XFormsValueControl control, XFormsContextStack contextStack, Element element) {
        this.control = control;
        this.contextStack =  contextStack;

        mediatypeElement = element.element(XFormsConstants.XFORMS_MEDIATYPE_QNAME);
        filenameElement = element.element(XFormsConstants.XFORMS_FILENAME_QNAME);
        sizeElement = element.element(XFormsConstants.XXFORMS_SIZE_QNAME);
    }

    public void markDirty() {
        isStateEvaluated = false;
        isMediatypeEvaluated = false;
        isSizeEvaluated = false;
        isFilenameEvaluated = false;

        isProgressReceivedEvaluated = false;
        isProgressExpectedEvaluated = false;
    }

    public String getState() {
        if (!isStateEvaluated) {
            final boolean isEmpty = control.getValue() == null || control.getValue().length() == 0;
            state = isEmpty ? "empty" : "file";
            isStateEvaluated = true;
        }
        return state;
    }

    public String getFileMediatype() {
        if (!isMediatypeEvaluated) {
            mediatype = (mediatypeElement == null) ? null : getInfoValue(mediatypeElement);
            isMediatypeEvaluated = true;
        }
        return mediatype;
    }

    public String getFileName() {
        if (!isFilenameEvaluated) {
            filename = (filenameElement == null) ? null : getInfoValue(filenameElement);
            isFilenameEvaluated = true;
        }
        return filename;
    }

    public String getFileSize() {
        if (!isSizeEvaluated) {
            size = (sizeElement == null) ? null : getInfoValue(sizeElement);
            isSizeEvaluated = true;
        }
        return size;
    }

    public String getProgressState() {
        if (!isProgressStateEvaluated) {

            final Multipart.UploadProgress progress = getProgress();
            if (progress != null)
                progressState = progress.state().toString().toLowerCase();
            else
                progressState = null;

            isProgressStateEvaluated = true;
        }
        return progressState;
    }

    public String getProgressCurrent() {
        if (!isProgressReceivedEvaluated) {

            final Multipart.UploadProgress progress = getProgress();
            if (progress != null)
                progressReceived = Long.toString(progress.receivedSize());
            else
                progressReceived = null;

            isProgressReceivedEvaluated = true;
        }
        return progressReceived;
    }

    public String getProgressExpected() {
        if (!isProgressExpectedEvaluated) {

            final Multipart.UploadProgress progress = getProgress();
            if (progress != null)
                progressExpected = progress.expectedSize().isDefined() ? ((Long) progress.expectedSize().get()).toString() : null;
            else
                progressExpected = null;

            isProgressExpectedEvaluated = true;
        }
        return progressExpected;
    }

    private Multipart.UploadProgress getProgress() {
        final Option option
            = Multipart.getUploadProgress(NetUtils.getExternalContext().getRequest(),
                control.getContainingDocument().getUUID(), control.getEffectiveId());

        final Multipart.UploadProgress progress = option.isEmpty() ? null : (Multipart.UploadProgress) option.get();

        if (progress != null && control.getEffectiveId().equals(progress.fieldName()))
            return progress;
        else
            return null;
    }

    private String getInfoValue(Element element) {
        contextStack.setBinding(control);
        contextStack.pushBinding(element, control.getEffectiveId(), control.getChildElementScope(element));
        final String tempValue = DataModel.getValue(contextStack.getCurrentSingleItem());
        contextStack.popBinding();
        return tempValue;
    }

    public void setMediatype(String mediatype) {
        setInfoValue(mediatypeElement, mediatype);
    }

    public void setFilename(String filename) {
        // Depending on web browsers, the filename may contain a path or not.

        // Normalize below to just the file name.
        final String normalized = StringUtils.replace(filename, "\\", "/");
        final int index = normalized.lastIndexOf('/');
        final String justFileName = (index == -1) ? normalized : normalized.substring(index + 1);

        setInfoValue(filenameElement, justFileName);
    }

    public void setSize(String size) {
        setInfoValue(sizeElement, size);
    }

    private void setInfoValue(Element element, String value) {
        if (element == null || value == null)
            return;

        contextStack.setBinding(control);
        contextStack.pushBinding(element, control.getEffectiveId(), control.getChildElementScope(element));
        final Item currentSingleItem = contextStack.getCurrentSingleItem();
        if (currentSingleItem instanceof NodeInfo) {
            DataModel.jSetValueIfChanged(control.getXBLContainer().getContainingDocument(), control.getIndentedLogger(),
                    control, control.getLocationData(), (NodeInfo) currentSingleItem, value, "fileinfo", false);
            contextStack.popBinding();
        }
    }

    public Object getBackCopy() {
        try {
            final FileInfo cloned = (FileInfo) super.clone();

            // Remove unneeded references
            cloned.control = null;
            cloned.contextStack = null;
            cloned.mediatypeElement = null;
            cloned.filenameElement = null;
            cloned.sizeElement = null;

            // Mark everything as evaluated because comparing must not cause reevaluation
            cloned.isStateEvaluated = true;
            cloned.isMediatypeEvaluated = true;
            cloned.isSizeEvaluated = true;
            cloned.isFilenameEvaluated = true;

            cloned.isProgressReceivedEvaluated = true;
            cloned.isProgressExpectedEvaluated = true;

            return cloned;
        } catch (CloneNotSupportedException e) {
            throw new OXFException(e);
        }
    }
}
