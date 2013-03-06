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
package org.orbeon.oxf.transformer.xupdate.statement;

import org.dom4j.QName;
import org.orbeon.oxf.transformer.xupdate.*;
import org.orbeon.oxf.xml.dom4j.LocationData;

import javax.xml.transform.URIResolver;

public class Function extends Statement {

    private QName qname;
    private Statement[] statements;

    public Function(LocationData locationData, QName qname, Statement[] statements) {
        super(locationData);
        this.qname = qname;
        this.statements = statements;
    }

    public Object execute(URIResolver uriResolver, Object context, VariableContextImpl variableContext, DocumentContext documentContext) {
        throw new IllegalStateException("Function statement for '" + qname.toString() + "' cannot be executed");
    }

    public Closure getClosure(URIResolver uriResolver, Object context) {
        return new Closure(uriResolver, context, statements);
    }

    public QName getName() {
        return qname;
    }
}
