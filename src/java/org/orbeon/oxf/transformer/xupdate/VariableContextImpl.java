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
package org.orbeon.oxf.transformer.xupdate;

import org.dom4j.Namespace;
import org.dom4j.QName;
import org.jaxen.UnresolvableException;
import org.jaxen.VariableContext;
import org.orbeon.oxf.transformer.xupdate.statement.Utils;

public class VariableContextImpl implements VariableContext {

    private VariableContextImpl parentContext;
    private QName qname;
    private Object value;

    public VariableContextImpl() {}

    public VariableContextImpl(VariableContextImpl parentContext, QName qname, Object value) {
        this.parentContext = parentContext;
        this.qname = qname;
        this.value = value;
    }

    public Object getVariableValue(String namespaceURI, String prefix, String localName)
            throws UnresolvableException {
        if (this.qname == null) {
            throw new UnresolvableException("Variable '" +
                    Utils.qualifiedName(prefix, localName) + "' is not defined");
        } else {
            QName qname = new QName(localName, new Namespace(prefix, namespaceURI));
            return this.qname.equals(qname) ? value
                    : parentContext.getVariableValue(namespaceURI, prefix, localName);
        }
    }

    public void assign(QName qname, Object value) throws UnresolvableException {
        if (this.qname == null) {
            throw new UnresolvableException("Variable '" + qname.getQualifiedName()
                     + "' is not defined");
        } else {
            if (this.qname.equals(qname)) this.value = value;
            else parentContext.assign(qname, value);
        }
    }
}
