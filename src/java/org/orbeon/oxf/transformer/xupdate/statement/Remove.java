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

import org.dom4j.Node;
import org.jaxen.NamespaceContext;
import org.orbeon.oxf.transformer.xupdate.*;
import org.orbeon.oxf.xml.dom4j.LocationData;

import javax.xml.transform.URIResolver;
import java.util.Collections;
import java.util.Iterator;

public class Remove extends Statement {
    private String select;
    private NamespaceContext namespaceContext;

    public Remove(LocationData locationData, String select, NamespaceContext namespaceContext) {
        super(locationData);
        this.select = select;
        this.namespaceContext = namespaceContext;
    }

    public Object execute(URIResolver uriResolver, Object context, VariableContextImpl variableContext, DocumentContext documentContext) {
        for (Iterator i = Utils.evaluateToList(uriResolver, context, variableContext, getLocationData(), select, namespaceContext, documentContext)
                .iterator(); i.hasNext();) {
            Node node = (Node) i.next();
            node.detach();
        }
        return Collections.EMPTY_LIST;
    }
}
