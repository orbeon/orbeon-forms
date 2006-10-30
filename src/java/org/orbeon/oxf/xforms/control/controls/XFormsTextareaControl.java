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
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.control.XFormsValueControl;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;

/**
 * Represents an xforms:textarea control.
 */
public class XFormsTextareaControl extends XFormsValueControl {

    private static final String AUTOSIZE_APPEARANCE = Dom4jUtils.qNameToexplodedQName(XFormsConstants.XXFORMS_AUTOSIZE_APPEARANCE_QNAME);

    public XFormsTextareaControl(XFormsContainingDocument containingDocument, XFormsControl parent, Element element, String name, String id) {
        super(containingDocument, parent, element, name, id);
    }

    public boolean hasJavaScriptInitialization() {
        return "text/html".equals(getMediatype()) || AUTOSIZE_APPEARANCE.equals(getAppearance());
    }
}
