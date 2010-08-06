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

import org.orbeon.oxf.xforms.XFormsUtils;
import org.orbeon.oxf.xforms.control.controls.XFormsSwitchControl;
import org.orbeon.oxf.xforms.function.XFormsFunction;
import org.orbeon.saxon.expr.XPathContext;
import org.orbeon.saxon.om.Item;
import org.orbeon.saxon.trans.XPathException;
import org.orbeon.saxon.value.StringValue;

/**
 * Extension xxforms:case($switch-id as xs:string) as xs:string? function.
 */
public class XXFormsCase  extends XFormsFunction {

    public Item evaluateItem(XPathContext xpathContext) throws XPathException {

        final String switchStaticId = argument[0].evaluateAsString(xpathContext).toString();

        final Object switchControl = getXBLContainer(xpathContext).resolveObjectByIdInScope(getSourceEffectiveId(xpathContext), switchStaticId, null);

        if (switchControl instanceof XFormsSwitchControl && ((XFormsSwitchControl) switchControl).isRelevant()) {
            // NOTE: Return the static id, not the effective id
            return StringValue.makeStringValue(XFormsUtils.getStaticIdFromId(((XFormsSwitchControl) switchControl).getSelectedCaseEffectiveId()));
        } else {
            return null;
        }
    }
}
