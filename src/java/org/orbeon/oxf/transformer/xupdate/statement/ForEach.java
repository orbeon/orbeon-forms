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

import org.jaxen.Context;
import org.jaxen.NamespaceContext;
import org.orbeon.oxf.transformer.xupdate.*;
import org.orbeon.oxf.xml.dom4j.LocationData;

import javax.xml.transform.URIResolver;
import java.util.*;

public class ForEach extends Statement {

    private String select;
    private NamespaceContext namespaceContext;
    private Statement[] statements;

    public ForEach(LocationData locationData, String select, NamespaceContext namespaceContext, Statement[] statements) {
        super(locationData);
        this.select = select;
        this.namespaceContext = namespaceContext;
        this.statements = statements;
    }

    public Object execute(URIResolver uriResolver, Object context, VariableContextImpl variableContext, DocumentContext documentContext) {
        List result = new ArrayList();
        List selected = Utils.evaluateToList(uriResolver, context, variableContext, getLocationData(), select, namespaceContext, documentContext);
        Context jaxenContext = new Context(null);
        jaxenContext.setSize(selected.size());
        for (int i = 0; i < selected.size(); i++) {
            jaxenContext.setPosition(i + 1);
            jaxenContext.setNodeSet(Arrays.asList(new Object[]{selected.get(i)}));
            Object statementsResult = Utils.execute(uriResolver, jaxenContext, variableContext, documentContext, statements);
            if (statementsResult instanceof List) {
                result.addAll((List) statementsResult);
            } else {
                result.add(statementsResult);
            }
        }
        return result.size() == 1 ? result.get(0) : result;
    }
}
