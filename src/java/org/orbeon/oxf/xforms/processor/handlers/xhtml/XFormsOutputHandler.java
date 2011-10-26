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
package org.orbeon.oxf.xforms.processor.handlers.xhtml;

import org.orbeon.oxf.xforms.control.XFormsSingleNodeControl;
import org.orbeon.oxf.xml.ContentHandlerHelper;
import org.xml.sax.Attributes;
import org.xml.sax.helpers.AttributesImpl;

/**
 * Base xforms:output handler.
 */
public abstract class XFormsOutputHandler extends XFormsControlLifecyleHandler {

    public XFormsOutputHandler() {
        super(false);
    }

    protected AttributesImpl getContainerAttributes(String uri, String localname, Attributes attributes, String effectiveId, XFormsSingleNodeControl outputControl) {

        final AttributesImpl containerAttributes = super.getContainerAttributes(uri, localname, attributes, effectiveId, outputControl, true);
        if (handlerContext.isSpanHTMLLayout()) {
            // Add custom class
            containerAttributes.addAttribute("", "class", "class", ContentHandlerHelper.CDATA, "xforms-output-output");
        }

        return containerAttributes;
    }
}
