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
package org.orbeon.oxf.xforms.function.xxforms;

import org.dom4j.Attribute;
import org.dom4j.QName;
import org.orbeon.oxf.xforms.function.XFormsFunction;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.saxon.expr.Expression;
import org.orbeon.saxon.expr.XPathContext;
import org.orbeon.saxon.om.Item;
import org.orbeon.saxon.trans.XPathException;

/**
 * xxforms:attribute(xs:string, xs:anyAtomicType?) as attribute()
 *
 * Creates a new XML attribute. The first argument is a string representing a QName representing the name of the
 * attribute to create. If a prefix is present, it is resolved with the namespace mappings in scope where the expression
 * is evaluated. The second attribute is an optional attribute value. The default is the empty string.
 */
public class XXFormsAttribute extends XFormsFunction {

    public Item evaluateItem(XPathContext xpathContext) throws XPathException {

        // Attribute QName
        final Expression qNameExpression = (argument.length < 1) ? null : argument[0];
        final QName qName = getQNameFromExpression(xpathContext, qNameExpression);

        // Attribute value
        final Expression valueExpression = (argument.length < 2) ? null : argument[1];
        final Item item = (valueExpression == null) ? null : valueExpression.evaluateItem(xpathContext);
        final String value = (item != null) ? item.getStringValue() : "";

        final Attribute attribute = Dom4jUtils.createAttribute(qName, value);
        return getContainingDocument(xpathContext).getStaticState().documentWrapper().wrap(attribute);
    }
}
