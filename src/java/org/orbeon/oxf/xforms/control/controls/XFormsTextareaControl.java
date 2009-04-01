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
import org.dom4j.QName;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.XFormsContainer;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.control.XFormsValueControl;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;

/**
 * Represents an xforms:textarea control.
 */
public class XFormsTextareaControl extends XFormsValueControl {

    private static final String AUTOSIZE_APPEARANCE = Dom4jUtils.qNameToExplodedQName(XFormsConstants.XXFORMS_AUTOSIZE_APPEARANCE_QNAME);

    // List of attributes to handle as AVTs
    private static final QName[] EXTENSION_ATTRIBUTES = {
            XFormsConstants.XXFORMS_MAXLENGTH_QNAME,
            XFormsConstants.XXFORMS_COLS_QNAME,
            XFormsConstants.XXFORMS_ROWS_QNAME
    };

    public XFormsTextareaControl(XFormsContainer container, XFormsControl parent, Element element, String name, String id) {
        super(container, parent, element, name, id);
    }

    protected QName[] getExtensionAttributes() {
        return EXTENSION_ATTRIBUTES;
    }

    public boolean hasJavaScriptInitialization() {
        return "text/html".equals(getMediatype()) || AUTOSIZE_APPEARANCE.equals(getAppearance());
    }

    protected void evaluate(PipelineContext pipelineContext) {
        super.evaluate(pipelineContext);
    }

    // NOTE: textarea doesn't support maxlength natively (this is added in HTML 5), but this can be implemented in JavaScript
    public String getMaxlength() {
        return getExtensionAttributeValue(XFormsConstants.XXFORMS_MAXLENGTH_QNAME);
    }
}
