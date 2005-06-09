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
package org.orbeon.oxf.transformer.xupdate.statement;

import org.jaxen.NamespaceContext;
import org.orbeon.oxf.transformer.xupdate.Statement;
import org.orbeon.oxf.transformer.xupdate.VariableContextImpl;
import org.orbeon.oxf.transformer.xupdate.DocumentContext;
import org.orbeon.oxf.xml.dom4j.LocationData;

import javax.xml.transform.URIResolver;
import java.util.Collections;

public class Choose extends Statement {

    private String[] tests;
    private NamespaceContext[] namespaceContexts;
    private Statement[][] statements;
    private Statement[] otherwise;

    public Choose(LocationData locationData, String[] tests, NamespaceContext[] namespaceContexts,
                  Statement[][] statements, Statement[] otherwise) {
        super(locationData);
        this.tests = tests;
        this.namespaceContexts = namespaceContexts;
        this.statements = statements;
        this.otherwise = otherwise;
    }

    public Object execute(URIResolver uriResolver, Object context, VariableContextImpl variableContext, DocumentContext documentContext) {

        // Evaluate successive tests
        for (int i = 0; i < tests.length; i++) {
            Boolean success = (Boolean) Utils.evaluate(uriResolver, context,
                    variableContext, documentContext, getLocationData(), "boolean(" + tests[i] + ")", namespaceContexts[i]);
            if (success.booleanValue()) {
                return Utils.execute(uriResolver, context, variableContext, documentContext, statements[i]);
            }
        }

        // If everything fails, evaluate otherwise
        if (otherwise != null) {
            return Utils.execute(uriResolver, context, variableContext, documentContext, otherwise);
        } else {
            return Collections.EMPTY_LIST;
        }
    }
}
