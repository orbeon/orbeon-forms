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
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.transformer.xupdate.*;
import org.orbeon.oxf.xml.dom4j.LocationData;

import javax.xml.transform.URIResolver;
import java.util.*;

public class NodeSet extends Statement {
    private String select;
    private NamespaceContext namespaceContext;

    public NodeSet(LocationData locationData, String select, NamespaceContext namespaceContext) {
        super(locationData);
        this.select = select;
        this.namespaceContext = namespaceContext;
    }

    public Object execute(final URIResolver uriResolver, Object context, VariableContextImpl variableContext, DocumentContext documentContext) {
        final Object selected = Utils.evaluate(uriResolver, context, variableContext, documentContext, getLocationData(), select, namespaceContext);

        if (selected == null) {
            return Collections.EMPTY_LIST;
        } else if (selected instanceof Node) {
            return Arrays.asList(new Node[]{ (Node) selected });
        } else if (selected instanceof List) {
//            List result = new ArrayList(((List) selected).size());
            for (Iterator i = ((List) selected).iterator(); i.hasNext();) {
                final Object o = i.next();
                if (!(o instanceof Node || o instanceof List))
                    throw new ValidationException("Unsupported type: " + o.getClass().getName(), getLocationData());

//                result.add(o);
            }
            return selected;
//            return result;
        } else {
            throw new ValidationException("Unsupported type: " + selected.getClass().getName(), getLocationData());
        }
    }
}
