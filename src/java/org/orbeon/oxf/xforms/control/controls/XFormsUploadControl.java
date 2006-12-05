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
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.control.XFormsValueControl;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.saxon.om.NodeInfo;

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
        final String value = XFormsInstance.getValueForNodeInfo(currentSingleNode);
        xformsControls.popBinding();
        return value;
    }

    public void setMediatype(PipelineContext pipelineContext, String mediatype) {
        setInfoValue(pipelineContext, mediatypeElement, mediatype);
    }

    public void setFilename(PipelineContext pipelineContext, String filename) {
        setInfoValue(pipelineContext, filenameElement, filename);
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
        XFormsInstance.setValueForNodeInfo(pipelineContext, currentSingleNode, value, null);
        xformsControls.popBinding();
    }
}
