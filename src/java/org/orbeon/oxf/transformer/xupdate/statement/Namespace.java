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

import org.jaxen.NamespaceContext;
import org.orbeon.oxf.transformer.xupdate.*;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.oxf.xml.dom4j.LocationData;

import javax.xml.transform.URIResolver;
import java.util.Arrays;

public class Namespace extends Statement {
    private NamespaceContext namespaceContext;
    private String name;
    private String select;
    private Statement[] statements;

    public Namespace(LocationData locationData, String name, String select, NamespaceContext namespaceContext, Statement[] statements) {
        super(locationData);
        this.namespaceContext = namespaceContext;
        this.name = name;
        this.select = select;
        this.statements = statements;
    }

    public Object execute(URIResolver uriResolver, Object context, VariableContextImpl variableContext, DocumentContext documentContext) {
        name = name.trim();
        String prefix = name.startsWith("{")
                ? (String) Utils.evaluate(uriResolver, context, variableContext, documentContext, getLocationData(), "string(" + name.substring(1, name.length() - 1) + ")", namespaceContext)
                : name;
        String uri = select != null
                ? (String) Utils.evaluate(uriResolver, context, variableContext, documentContext, getLocationData(), "string(" + select + ")", namespaceContext)
                : (String) Dom4jUtils.createXPath("string()").evaluate
                    (Utils.execute(uriResolver, context, variableContext, documentContext, statements));
        return Arrays.asList(new Object[] {Dom4jUtils.createNamespace(prefix, uri)});
    }
}
