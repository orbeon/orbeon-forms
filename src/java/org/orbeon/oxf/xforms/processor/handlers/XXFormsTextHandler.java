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
package org.orbeon.oxf.xforms.processor.handlers;

import org.orbeon.oxf.xforms.control.controls.XXFormsTextControl;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

public class XXFormsTextHandler extends XFormsBaseHandler {

    public XXFormsTextHandler() {
        super(false, false);
    }

    public void start(String uri, String localname, String qName, Attributes attributes) throws SAXException {

        final String effectiveId = handlerContext.getEffectiveId(attributes);
        final XXFormsTextControl textControl = (XXFormsTextControl) containingDocument.getObjectByEffectiveId(effectiveId);

        final ContentHandler contentHandler = handlerContext.getController().getOutput();
        final boolean isConcreteControl = textControl != null;

        if (isConcreteControl) {
            final String externalValue = textControl.getExternalValue();
            if (externalValue != null && externalValue.length() > 0)
                contentHandler.characters(externalValue.toCharArray(), 0, externalValue.length());
        }
    }
}
