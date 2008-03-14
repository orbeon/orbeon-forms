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
package org.orbeon.oxf.xforms.function.xxforms;

import org.orbeon.oxf.xforms.XFormsContainingDocument;
import org.orbeon.oxf.xforms.XFormsInstance;
import org.orbeon.oxf.xforms.XFormsUtils;
import org.orbeon.oxf.xforms.function.XFormsFunction;
import org.orbeon.saxon.expr.Expression;
import org.orbeon.saxon.expr.PathMap;
import org.orbeon.saxon.expr.XPathContext;
import org.orbeon.saxon.om.ListIterator;
import org.orbeon.saxon.om.SequenceIterator;
import org.orbeon.saxon.trans.XPathException;

import java.util.Collections;

/**
 * xxforms:instance() function. This function operates like the standard instance() function, except that it looks for
 * instances globally instead of using the current model.
 */
public class XXFormsInstance extends XFormsFunction {

    public SequenceIterator iterate(XPathContext xpathContext) throws XPathException {

        final XFormsContainingDocument containingDocument = getContainingDocument(xpathContext);

        // Get instance id
        final Expression instanceIdExpression = argument[0];
        final String instanceId = XFormsUtils.namespaceId(containingDocument, instanceIdExpression.evaluateAsString(xpathContext));

        final XFormsInstance instance = containingDocument.findInstance(instanceId);

        // Return instance document if found
        if (instance != null) {
            // "this function returns a node-set containing just the root element node"
            return new ListIterator(Collections.singletonList(instance.getInstanceRootElementInfo()));
        } else {
            // "an empty node-set is returned"
            getContainingDocument(xpathContext).logWarning("function", "Instance not found with xxforms:instance() function",
                    new String[] { "instance id", instanceId });
            return new ListIterator(Collections.EMPTY_LIST);
        }
    }

    public PathMap.PathMapNode addToPathMap(PathMap pathMap, PathMap.PathMapNode pathMapNode) {
        return addDocToPathMap(pathMap, pathMapNode);
    }
}
