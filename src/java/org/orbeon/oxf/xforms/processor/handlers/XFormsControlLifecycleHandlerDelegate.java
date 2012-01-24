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

import org.orbeon.oxf.xforms.XFormsContainingDocument;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

/**
 * This class should have been a mixin for both XFormsControlLifecycleHandlers, but java doesn't supports mixins.
 */
public class XFormsControlLifecycleHandlerDelegate {
	private final boolean isTemplate;

    private final String prefixedId;
    private final String effectiveId;

    private final XFormsControl xformsControl;
    
    public boolean isTemplate() {
    	return isTemplate;
    }
    
    public String getPrefixedId() {
        return prefixedId;
    }

    public String getEffectiveId() {
        return effectiveId;
    }

    public XFormsControl getControl() {
        return xformsControl;
    }

	public XFormsControlLifecycleHandlerDelegate(HandlerContext handlerContext, XFormsContainingDocument containingDocument, Attributes attributes) throws SAXException {

        isTemplate = handlerContext.isTemplate();

        prefixedId = handlerContext.getPrefixedId(attributes);
        effectiveId = handlerContext.getEffectiveId(attributes);

        xformsControl = handlerContext.isTemplate()
                ? null : (XFormsControl) containingDocument.getObjectByEffectiveId(effectiveId);
    }
}
