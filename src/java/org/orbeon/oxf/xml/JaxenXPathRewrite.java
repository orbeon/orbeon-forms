/**
 *  Copyright (C) 2004 Orbeon, Inc.
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
package org.orbeon.oxf.xml;

import org.jaxen.JaxenHandler;
import org.jaxen.expr.Expr;
import org.jaxen.expr.FunctionCallExpr;
import org.jaxen.expr.XPathExpr;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.saxpath.SAXPathException;
import org.saxpath.XPathReader;
import org.saxpath.helpers.XPathReaderFactory;

import java.util.Map;

/**
 * Utility class to rewrite XPath expression, in particular to change function
 * calls.
 */
public class JaxenXPathRewrite {

    public interface Rewriter {
        public void rewrite(FunctionCallExpr expr);
    }

    public static String rewrite(String xpath, final Map namespaceContext,
                          final String functionURI, final String functionName,
                          final int parametersCount, final LocationData locationData,
                          final Rewriter rewriter) {
        try {
            // Create reader and stuff to parse XPath expression
            JaxenHandler handler = new JaxenHandler(); {
                XPathReader reader = XPathReaderFactory.createReader();
                reader.setXPathHandler(handler);
                reader.parse(xpath);
            }
            XPathExpr xpathExpr = handler.getXPathExpr();
            Expr expr = xpathExpr.getRootExpr();

            // Visit XPath and change call to bpws:getVariableData()
            expr.accept(new JaxenSimpleVisitor() {
                public void visit(FunctionCallExpr expr) {

                    // Check prefix
                    String prefix = expr.getPrefix();
                    if (prefix != null && !"".equals(prefix)) {
                        if (namespaceContext == null)
                            throw new OXFException("Function in namespace not supported: '" + expr.getPrefix() + ":"
                                    + expr.getFunctionName() + "'");
                        String uri = (String) namespaceContext.get(prefix);
                        if (functionURI == null || !functionURI.equals(uri))
                            return;
                    }

                    // Check local name
                    if (!expr.getFunctionName().equals(functionName)) return;

                    // Check parameter count
                    if (expr.getParameters().size() != parametersCount)
                        throw new ValidationException(functionName + " expected " + parametersCount + " arguments",
                                locationData);

                    // Everything is ok: run rewriter
                    rewriter.rewrite(expr);
                }
            });
            return expr.getText();

        } catch (SAXPathException e) {
            throw new OXFException(e.getMessage() + " while parsing XPath expression '" + xpath + "'");
        }
    }
}
