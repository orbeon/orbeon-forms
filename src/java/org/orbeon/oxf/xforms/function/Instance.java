/**
 *  Copyright (C) 2005 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.function;

import org.dom4j.Document;
import org.orbeon.oxf.xforms.XFormsInstance;
import org.orbeon.oxf.xforms.XFormsModel;
import org.orbeon.oxf.xforms.XFormsUtils;
import org.orbeon.saxon.dom4j.DocumentWrapper;
import org.orbeon.saxon.expr.Expression;
import org.orbeon.saxon.expr.StaticContext;
import org.orbeon.saxon.expr.XPathContext;
import org.orbeon.saxon.om.ListIterator;
import org.orbeon.saxon.om.SequenceIterator;
import org.orbeon.saxon.trans.XPathException;
import org.orbeon.saxon.Configuration;

import java.util.Collections;

/**
 * XForms instance() function.
 *
 * 7.11.1 The instance() Function
 */
public class Instance extends XFormsFunction {

    /**
    * preEvaluate: this method suppresses compile-time evaluation by doing nothing
    * (because the value of the expression depends on the runtime context)
    */
    public Expression preEvaluate(StaticContext env) {
        return this;
    }

    public SequenceIterator iterate(XPathContext xpathContext) throws XPathException {
        // Get instance id
        final Expression instanceIdExpression = argument[0];
        final String instanceId = XFormsUtils.namespaceId(getXFormsContainingDocument(), instanceIdExpression.evaluateAsString(xpathContext));

        // Get model and instance with given id for that model only
        
        // "If a match is located, and the matching instance data is associated with the same XForms Model as the
        // current context node, this function returns a node-set containing just the root element node (also called the
        // document element node) of the referenced instance data. In all other cases, an empty node-set is returned."

        final XFormsModel model = (getXFormsModel() != null) ? getXFormsModel() : getXFormsControls().getCurrentModel();
        final XFormsInstance instance = model.getInstance(instanceId);


        // Return instance document
        if (instance != null) {
            final Document instanceDocument = instance.getInstanceDocument();
            // "this function returns a node-set containing just the root element node"
            return new ListIterator(Collections.singletonList(new DocumentWrapper(instanceDocument, null, new Configuration()).wrap(instanceDocument.getRootElement())));
        } else {
            // "an empty node-set is returned"
            return new ListIterator(Collections.EMPTY_LIST);
        }
    }
}
