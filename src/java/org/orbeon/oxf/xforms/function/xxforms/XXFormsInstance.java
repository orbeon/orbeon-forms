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

import org.orbeon.oxf.xforms.*;
import org.orbeon.oxf.xforms.function.XFormsFunction;
import org.orbeon.oxf.xforms.xbl.XBLContainer;
import org.orbeon.saxon.expr.*;
import org.orbeon.saxon.om.*;
import org.orbeon.saxon.trans.XPathException;

/**
 * xxforms:instance() function. This function operates like the standard instance() function, except that it looks for
 * instances globally instead of using the current model.
 */
public class XXFormsInstance extends XFormsFunction {

    @Override
    public SequenceIterator iterate(XPathContext xpathContext) throws XPathException {

        final XFormsContainingDocument containingDocument = getContainingDocument(xpathContext);

        // Get instance id
        final Expression instanceIdExpression = argument[0];
        final String instanceId = instanceIdExpression.evaluateAsString(xpathContext).toString();

        XFormsInstance instance = null;
        if (argument.length > 1 && argument[1].effectiveBooleanValue(xpathContext)) {
            // Argument is effective id
            final Object o = containingDocument.getObjectByEffectiveId(instanceId);
            instance = (o instanceof XFormsInstance) ? ((XFormsInstance) o) : null;
        } else {
            // Search ancestor-or-self containers as suggested here: http://wiki.orbeon.com/forms/projects/xforms-model-scoping-rules
            {
                XBLContainer currentContainer = getXBLContainer(xpathContext);
                while (currentContainer != null) {
                    instance = currentContainer.findInstance(instanceId);
                    if (instance != null)
                        break;
                    currentContainer = currentContainer.getParentXBLContainer();
                }
            }
        }

        // Return instance document if found
        if (instance != null) {
            // "this function returns a node-set containing just the root element node"
            return SingletonIterator.makeIterator(instance.getInstanceRootElementInfo());
        } else {
            // "an empty node-set is returned"
            getContainingDocument(xpathContext).getIndentedLogger(XFormsModel.LOGGING_CATEGORY).logWarning("xxforms:instance()", "instance not found", "instance id", instanceId);
            return EmptyIterator.getInstance();
        }
    }

    @Override
    public PathMap.PathMapNodeSet addToPathMap(PathMap pathMap, PathMap.PathMapNodeSet pathMapNodeSet) {
        // TODO: if argument[1] is true, must search globally
        argument[0].addToPathMap(pathMap, pathMapNodeSet);
        return new PathMap.PathMapNodeSet(pathMap.makeNewRoot(this));
    }
}
