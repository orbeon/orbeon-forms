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

import org.dom4j.Attribute;
import org.dom4j.*;
import org.jaxen.NamespaceContext;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.transformer.xupdate.*;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.oxf.xml.dom4j.LocationData;

import javax.xml.transform.URIResolver;
import java.util.Collections;
import java.util.Iterator;

public class Update extends Statement {
    private String select;
    private NamespaceContext namespaceContext;
    private Statement[] statements;

    public Update(LocationData locationData, String select, NamespaceContext namespaceContext, Statement[] statements) {
        super(locationData);
        this.select = select;
        this.namespaceContext = namespaceContext;
        this.statements = statements;
    }

    public Object execute(URIResolver uriResolver, Object context, VariableContextImpl variableContext, DocumentContext documentContext) {
        for (Iterator i = Utils.evaluateToList(uriResolver, context, variableContext, getLocationData(), select, namespaceContext, documentContext).iterator(); i.hasNext();) {
            Object node = i.next();
            Object toInsert = Utils.execute(uriResolver, node, variableContext, documentContext, statements);
            if (node instanceof Element) {
                Element parent = (Element) node;
                Dom4jUtils.clearElementContent(parent);
                Utils.insert(getLocationData(), parent, 0, toInsert);
            } else if (node instanceof Document) {
                Document parent = (Document) node;
                parent.clearContent();
                Utils.insert(getLocationData(), parent, 0, toInsert);
            } else if (node instanceof Attribute) {
                Attribute parent = (Attribute) node;
                parent.setValue("");
                Utils.insert(getLocationData(), parent, 0, toInsert);
            } else if (node instanceof org.dom4j.Text) {
                if (!(toInsert instanceof org.dom4j.Text))
                    throw new ValidationException("A text node can only be updated with text", getLocationData());
                ((org.dom4j.Text) node).setText(((org.dom4j.Text) toInsert).getText());
            } else {
                throw new ValidationException("Cannot update a " + node.getClass().getName(), getLocationData());
            }
        }
        return Collections.EMPTY_LIST;
    }
}
