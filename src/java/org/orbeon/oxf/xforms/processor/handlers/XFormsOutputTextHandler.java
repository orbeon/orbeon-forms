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
package org.orbeon.oxf.xforms.processor.handlers;

import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.control.controls.XFormsOutputControl;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * Handler for xforms:output[@appearance = 'xxforms:text'].
 */
public class XFormsOutputTextHandler extends XFormsOutputHandler {

    @Override
    protected void handleControlStart(String uri, String localname, String qName, Attributes attributes, String staticId, String effectiveId, XFormsControl control) throws SAXException {

        // Just output value for "text" appearance
//        if (isImageMediatype || isHTMLMediaType) {
//            throw new ValidationException("Cannot use mediatype value for \"xxforms:text\" appearance: " + mediatypeValue, handlerContext.getLocationData());
//        }

        final XFormsOutputControl outputControl = (XFormsOutputControl) control;
        final boolean isConcreteControl = outputControl != null;
        final ContentHandler contentHandler = handlerContext.getController().getOutput();

        if (isConcreteControl) {
            final String externalValue = outputControl.getExternalValue();
            if (externalValue != null && externalValue.length() > 0)
                contentHandler.characters(externalValue.toCharArray(), 0, externalValue.length());
        }
    }
}
