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
package org.orbeon.oxf.processor.xforms.input.action;

import org.jaxen.Function;
import org.jaxen.FunctionContext;
import org.jaxen.UnresolvableException;
import org.orbeon.oxf.xml.OXFFunctionContext;

public class ActionFunctionContext implements FunctionContext {

    private static OXFFunctionContext oxfFunctionContext = new OXFFunctionContext();

/*
    private Map indexValues;

    public ActionFunctionContext() {
        this.indexValues = indexValues;
    }
*/

    public Function getFunction(String namespaceURI, String prefix, String localName) throws UnresolvableException {
//        if ("index".equals(localName) && (namespaceURI == null || namespaceURI.equals(""))) {
//            return new Function() {
//                public Object call(Context context, List args) throws FunctionCallException {
//                    if (args.size() != 1)
//                        throw new OXFException("Function index expects one argument");
//                    if (!(args.get(0) instanceof String))
//                        throw new OXFException("The parameter of the index function must be a string");
//                    String id = (String) args.get(0);
//                    if (!indexValues.containsKey(id))
//                        throw new OXFException("Unknown repeat id '" + id + "' used in index() function");
//                    return indexValues.get(id);
//                }
//            };
//        } else {
            return oxfFunctionContext.getFunction(namespaceURI, prefix, localName);
//        }
    }
}
