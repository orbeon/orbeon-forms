/**
 * Copyright (C) 2009 Orbeon, Inc.
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

import org.dom4j.Element;
import org.dom4j.QName;
import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.control.XFormsValueControl;
import org.orbeon.oxf.xforms.xbl.XBLContainer;

import java.util.Map;

/**
 * Represents an xforms:secret control.
 */
public class XFormsSecretControl extends XFormsValueControl {

    // List of attributes to handle as AVTs
    private static final QName[] EXTENSION_ATTRIBUTES = {
            XFormsConstants.XXFORMS_SIZE_QNAME,
            XFormsConstants.XXFORMS_MAXLENGTH_QNAME,
            XFormsConstants.XXFORMS_AUTOCOMPLETE_QNAME
    };

    public XFormsSecretControl(XBLContainer container, XFormsControl parent, Element element, String name, String id, Map<String, Element> state) {
        super(container, parent, element, name, id);
    }

    @Override
    protected QName[] getExtensionAttributes() {
        return EXTENSION_ATTRIBUTES;
    }
}
