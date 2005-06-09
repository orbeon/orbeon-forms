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

import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.Node;
import org.jaxen.NamespaceContext;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.transformer.xupdate.Statement;
import org.orbeon.oxf.transformer.xupdate.VariableContextImpl;
import org.orbeon.oxf.transformer.xupdate.DocumentContext;
import org.orbeon.oxf.xml.dom4j.LocationData;

import javax.xml.transform.URIResolver;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class Append extends Statement {
    private NamespaceContext namespaceContext;
    private String select;
    private String child;
    private Statement[] statements;

    public Append(LocationData locationData, String select, NamespaceContext namespaceContext, String child, Statement[] statements) {
        super(locationData);
        this.select = select;
        this.namespaceContext = namespaceContext;
        this.child = child;
        this.statements = statements;
    }

    public Object execute(URIResolver uriResolver, Object context, VariableContextImpl variableContext, DocumentContext documentContext) {
        Object parentNode = Utils.evaluate(uriResolver, context, variableContext, documentContext, getLocationData(), select, namespaceContext);
        if (parentNode == null)
            throw new ValidationException("Cannot find '" + select + "' in XUpdate append operation", getLocationData());
        if (parentNode instanceof List) {
            List list = (List) parentNode;
            if (list.isEmpty())
                throw new ValidationException("Cannot insert in an empty list", getLocationData());
            for (Iterator i = list.iterator(); i.hasNext();)
                insert(i.next(), uriResolver, context, variableContext, documentContext);
        } else {
            insert(parentNode, uriResolver, context, variableContext, documentContext);
        }
        return Collections.EMPTY_LIST;
    }

    private void insert(Object parentNode, URIResolver uriResolver, Object context, VariableContextImpl variableContext, DocumentContext documentContext) {
        if (parentNode instanceof Document) {
            if (((Document) parentNode).getRootElement() != null)
                throw new ValidationException("Document already has a root element", getLocationData());
            Utils.insert(getLocationData(), (Document) parentNode, 0,
                    Utils.execute(uriResolver, context, variableContext, documentContext, statements));
        } else if (parentNode instanceof Element) {
            Element parentElement = (Element) parentNode;
            List children = parentElement.content();
            int position = child == null ? parentElement.content().size()
                    : parentElement.content().size() == 0 ? 0
                    : ((Number) ((Node) children.get(0)).createXPath(child).evaluate(children)).intValue();
            Utils.insert(getLocationData(), parentElement, position,
                    Utils.execute(uriResolver, context, variableContext, documentContext, statements));
        } else {
            throw new ValidationException("Cannot append in a: " + parentNode.getClass().getName(), getLocationData());
        }
    }
}
