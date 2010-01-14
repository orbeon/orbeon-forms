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
package org.orbeon.oxf.xforms.function;

import org.apache.commons.lang.StringUtils;
import org.orbeon.oxf.xforms.XFormsInstance;
import org.orbeon.oxf.xforms.XFormsModel;
import org.orbeon.oxf.xforms.XFormsUtils;
import org.orbeon.saxon.expr.Expression;
import org.orbeon.saxon.expr.PathMap;
import org.orbeon.saxon.expr.XPathContext;
import org.orbeon.saxon.om.EmptyIterator;
import org.orbeon.saxon.om.SequenceIterator;
import org.orbeon.saxon.om.SingletonIterator;
import org.orbeon.saxon.trans.XPathException;

/**
 * XForms instance() function.
 *
 * 7.11.1 The instance() Function
 */
public class Instance extends XFormsFunction {

    public SequenceIterator iterate(XPathContext xpathContext) throws XPathException {
        // Get instance id
        
        // "If the argument is omitted or is equal to the empty string, then the root element node (also called the
        // document element node) is returned for the default instance in the model that contains the current context
        // node."
        final Expression instanceIdExpression = (argument.length > 0) ? argument[0] : null;
        final String instanceId;
        {
            if (instanceIdExpression != null) {
                final String tempId = instanceIdExpression.evaluateAsString(xpathContext).trim();
                instanceId = (StringUtils.isNotBlank(tempId)) ? XFormsUtils.namespaceId(getContainingDocument(xpathContext), tempId) : null;
            } else {
                instanceId = null;
            }
        }

        // Get model and instance with given id for that model only
        
        // "If a match is located, and the matching instance data is associated with the same XForms Model as the
        // current context node, this function returns a node-set containing just the root element node (also called
        // the document element node) of the referenced instance data. In all other cases, an empty node-set is
        // returned."

        // NOTE: Model can be null when there is no model in scope at all
        final XFormsModel model = getModel(xpathContext);
        final XFormsInstance instance = (model != null) ? (instanceId != null) ?  model.getInstance(instanceId) : model.getDefaultInstance() : null;

        // Return instance document if found
        if (instance != null) {
            // "this function returns a node-set containing just the root element node"
            return SingletonIterator.makeIterator(instance.getInstanceRootElementInfo());
        } else {
            // "an empty node-set is returned"
            getContainingDocument(xpathContext).getIndentedLogger(XFormsModel.LOGGING_CATEGORY).logWarning("instance()", "instance not found", "instance id", instanceId);
            return EmptyIterator.getInstance();
        }
    }

    public PathMap.PathMapNode addToPathMap(PathMap pathMap, PathMap.PathMapNode pathMapNode) {
        return addDocToPathMap(pathMap, pathMapNode);
    }
}
