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

import org.dom4j.Node;
import org.jaxen.NamespaceContext;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.transformer.xupdate.Closure;
import org.orbeon.oxf.transformer.xupdate.Statement;
import org.orbeon.oxf.transformer.xupdate.VariableContextImpl;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.oxf.xml.dom4j.LocationData;

import javax.xml.transform.URIResolver;
import java.util.*;

public class CopyOf extends Statement {
    private String select;
    private NamespaceContext namespaceContext;

    public CopyOf(LocationData locationData, String select, NamespaceContext namespaceContext) {
        super(locationData);
        this.select = select;
        this.namespaceContext = namespaceContext;
    }

    public Object execute(final URIResolver uriResolver, Object context, VariableContextImpl variableContext) {
        Object selected = Utils.evaluate(uriResolver, context, variableContext, getLocationData(), select, namespaceContext);
        if (selected == null) {
            return Collections.EMPTY_LIST;
        } else if (selected instanceof String || selected instanceof Number) {
            org.dom4j.Text textNode = Dom4jUtils.createText(selected.toString());
            return Arrays.asList(new org.dom4j.Text[] {textNode});
        } else if (selected instanceof Node) {
            return Arrays.asList(new Node[]{ ( Node )( ( Node )selected ).clone()  });
        } else if (selected instanceof List) {
            List result = new ArrayList();
            for (Iterator i = ((List) selected).iterator(); i.hasNext();)
                result.add( ( ( Node )i.next() ).clone() );
            return result;
        } else if (selected instanceof Closure) {
            return Arrays.asList(new Closure[] {(Closure) selected});
        } else {
            throw new ValidationException("Unsupported type: " + selected.getClass().getName(), getLocationData());
        }
    }
}
