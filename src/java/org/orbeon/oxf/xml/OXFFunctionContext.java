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

import org.jaxen.*;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;

import java.util.List;

/**
 * Declares functions available in XPL
 */
public class OXFFunctionContext implements FunctionContext {

    public Function getFunction(String namespaceURI, String prefix, String localName) throws UnresolvableException {

        // identity(x) returns x. This is use in XPath expressions modified with
        // the Jaxen API to replace a function call by some other expression.
        if ("identity".equals(localName) && (namespaceURI == null || namespaceURI.equals(""))) {
            return new Function() {
                public Object call(Context context, List args) throws FunctionCallException {
                    if (args.size() != 1)
                        throw new OXFException("Function identity expects one argument");
                    return args.get(0);
                }
            };
        } else if ("if".equals(localName) && (namespaceURI == null || namespaceURI.equals(""))) {
            return new Function() {
                public Object call(Context context, List args) throws FunctionCallException {
                    if (args.size() != 3)
                        throw new OXFException("if function expects 3 arguments");
                    return (((Boolean) Dom4jUtils.createXPath("boolean(.)").evaluate(args.get(0))).booleanValue())
                        ? args.get(1) : args.get(2);
                }
            };
        } else {
            return XPathFunctionContext.getInstance().getFunction(namespaceURI, prefix, localName);
        }
    }
}
