/*
 * Copyright 1998-2002, Orbeon, Inc.
 * All Rights Reserved
 *
 * This is unpublished proprietary source code of Orbeon, Inc. The
 * copyright notice above does not evidence any actual or intended
 * publication of such source code.
 */
package org.orbeon.oxf.processor.xforms.output;

import org.orbeon.oxf.processor.xforms.output.element.XFormsElementContext;
import org.orbeon.oxf.processor.xforms.output.function.Index;
import org.orbeon.oxf.processor.xforms.output.function.Last;
import org.orbeon.saxon.expr.Expression;
import org.orbeon.saxon.expr.StaticProperty;
import org.orbeon.saxon.functions.FunctionLibrary;
import org.orbeon.saxon.functions.StandardFunction;
import org.orbeon.saxon.functions.SystemFunction;
import org.orbeon.saxon.om.NamespaceConstant;
import org.orbeon.saxon.type.Type;
import org.orbeon.saxon.xpath.XPathException;

public class XFormsFunctionLibrary implements FunctionLibrary {

    private XFormsElementContext xformsElementContext;

    public XFormsFunctionLibrary(XFormsElementContext xformsElementContext) {
        this.xformsElementContext = xformsElementContext;
    }

    public boolean isAvailable(int fingerprint, String uri, String local, int arity) {
        return "".equals(uri) && "index".equals(local);
    }

    public Expression bind(int nameCode, String uri, String local, Expression[] staticArgs) throws XPathException {

        SystemFunction function = null;
        StandardFunction.Entry entry = null;

        // Create function based on local name
        if (NamespaceConstant.FN.equals(uri) && "index".equals(local)) {
            entry = StandardFunction.makeEntry("index", Index.class, 0, 1, 1,
                    Type.INTEGER_TYPE, StaticProperty.EXACTLY_ONE);
            StandardFunction.arg(entry, 0, Type.STRING_TYPE, StaticProperty.EXACTLY_ONE);
            function = new Index(xformsElementContext);
        } else if (NamespaceConstant.FN.equals(uri) && "last".equals(local)) {
            entry = StandardFunction.makeEntry("last", Last.class, 0, 0, 0,
                    Type.INTEGER_TYPE, StaticProperty.EXACTLY_ONE);
            function = new Last(xformsElementContext);
        }

        // Initialize function
        if (function != null) {
            function.setFunctionNameCode(nameCode);
            function.setArguments(staticArgs);
            function.setDetails(entry);
        }

        return function;
    }
}
