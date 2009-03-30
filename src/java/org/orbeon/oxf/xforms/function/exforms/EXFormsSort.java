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
package org.orbeon.oxf.xforms.function.exforms;

import org.orbeon.oxf.xforms.function.xxforms.XXFormsSort;
import org.orbeon.saxon.expr.*;
import org.orbeon.saxon.om.NamespaceResolver;
import org.orbeon.saxon.om.SequenceIterator;
import org.orbeon.saxon.trans.IndependentContext;
import org.orbeon.saxon.trans.XPathException;

import java.util.Iterator;

/**
 * exforms:sort() function
 */
public class EXFormsSort extends XXFormsSort {

    // See comments in Saxon Evaluate.java
    private IndependentContext staticContext;

    public SequenceIterator iterate(XPathContext xpathContext) throws XPathException {

        final Expression sequenceToSortExpression = argument[0];
        final Expression sortKeyExpression;
        {
            final Expression selectExpression = argument[1];
            sortKeyExpression = ExpressionTool.make(selectExpression.evaluateAsString(xpathContext),
                staticContext, 0, Token.EOF, getLineNumber());
        }

        return sort(xpathContext, sequenceToSortExpression, sortKeyExpression);
    }

    // The following copies all the StaticContext information into a new StaticContext
    public void checkArguments(StaticContext env) throws XPathException {
        // See same method in Saxon Evaluate.java
        if (staticContext == null) { // only do this once
            super.checkArguments(env);

            final NamespaceResolver namespaceResolver = env.getNamespaceResolver();
            staticContext = new IndependentContext(env.getConfiguration());
            staticContext.setBaseURI(env.getBaseURI());
            staticContext.setImportedSchemaNamespaces(env.getImportedSchemaNamespaces());
            staticContext.setDefaultFunctionNamespace(env.getDefaultFunctionNamespace());
            staticContext.setDefaultElementNamespace(env.getNamePool().getURIFromURICode(env.getDefaultElementNamespace()));

            for (Iterator iterator = namespaceResolver.iteratePrefixes(); iterator.hasNext();) {
                final String prefix = (String) iterator.next();
                if (!"".equals(prefix)) {
                    final String uri = namespaceResolver.getURIForPrefix(prefix, true);
                    staticContext.declareNamespace(prefix, uri);
                }
            }
        }
    }
}
