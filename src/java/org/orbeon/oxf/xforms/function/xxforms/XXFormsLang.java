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
package org.orbeon.oxf.xforms.function.xxforms;

import org.dom4j.Element;
import org.orbeon.oxf.xforms.XFormsContainingDocument;
import org.orbeon.oxf.xforms.XFormsUtils;
import org.orbeon.oxf.xforms.function.XFormsFunction;
import org.orbeon.oxf.xforms.xbl.XBLBindings;
import org.orbeon.saxon.expr.XPathContext;
import org.orbeon.saxon.om.Item;
import org.orbeon.saxon.trans.XPathException;
import org.orbeon.saxon.value.StringValue;

public class XXFormsLang extends XFormsFunction {
    @Override
    public Item evaluateItem(XPathContext xpathContext) throws XPathException {

        final String elementId = (argument.length > 0) ? argument[0].evaluateAsString(xpathContext).toString() : null;

        final XFormsContainingDocument containingDocument = getContainingDocument(xpathContext);

        final Element element;
        if (elementId == null) {
            // Get element on which the expression is used
            element = getSourceElement(xpathContext);
        } else {
            // Do a bit more work to find current scope first
            final XBLBindings.Scope scope = containingDocument.getStaticState().getXBLBindings().getResolutionScopeByPrefixedId(XFormsUtils.getPrefixedId(getSourceEffectiveId(xpathContext)));
            final String elementPrefixedId = scope.getPrefixedIdForStaticId(elementId);

            element = containingDocument.getStaticState().getControlElement(elementPrefixedId);
        }

        final String lang = XFormsUtils.resolveXMLangHandleAVTs(getOrCreatePipelineContext(), containingDocument, element);

        return (lang == null) ? null : StringValue.makeStringValue(lang);
    }
}
