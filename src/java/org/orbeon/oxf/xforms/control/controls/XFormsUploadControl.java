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

/**
 * Represents an xforms:upload control.
 */
public class XFormsUploadControl extends XFormsControl {

    private Element mediatypeElement;
    private Element filenameElement;
    private Element sizeElement;

    public XFormsUploadControl(XFormsContainingDocument containingDocument, XFormsControl parent, Element element, String name, String id) {
        super(containingDocument, parent, element, name, id);
        mediatypeElement = element.element(XFormsConstants.XFORMS_MEDIATYPE_ELEMENT_QNAME);
        filenameElement = element.element(XFormsConstants.XFORMS_FILENAME_ELEMENT_QNAME);
        sizeElement = element.element(XFormsConstants.XXFORMS_SIZE_ELEMENT_QNAME);
    }

    public Element getMediatypeElement() {
        return mediatypeElement;
    }

    public Element getFilenameElement() {
        return filenameElement;
    }

    public Element getSizeElement() {
        return sizeElement;
    }
}
