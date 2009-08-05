/**
 * Copyright (C) 2009 Orbeon, Inc.
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

import org.orbeon.oxf.properties.Properties;
import org.orbeon.oxf.xforms.XFormsUtils;
import org.orbeon.oxf.xforms.function.XFormsFunction;
import org.orbeon.saxon.expr.Expression;
import org.orbeon.saxon.expr.XPathContext;
import org.orbeon.saxon.om.ListIterator;
import org.orbeon.saxon.om.SequenceIterator;
import org.orbeon.saxon.trans.XPathException;
import org.orbeon.saxon.value.AtomicValue;

import java.util.ArrayList;
import java.util.List;

/**
 * xxforms:properties-starts-with() function.
 *
 * Return the name of all the properties from properties.xml that start with the given name.
 */
public class XXFormsPropertiesStartsWith extends XFormsFunction {

    public SequenceIterator iterate(XPathContext xpathContext) throws XPathException {

        // Get property name
        final Expression propertyNameExpression = argument[0];
        final String propertyName = propertyNameExpression.evaluateAsString(xpathContext);

        // Get property value
        return new ListIterator(propertiesStartsWith(propertyName));
    }

    public static List<AtomicValue> propertiesStartsWith(String propertyName) {
        final List<String> stringList = Properties.instance().getPropertySet().getPropertiesStartsWith(propertyName);
        final List<AtomicValue> atomicValueList = new ArrayList<AtomicValue>();
        for (String property: stringList) {
            if (property.toLowerCase().indexOf("password") == -1)
                atomicValueList.add((AtomicValue) XFormsUtils.convertJavaObjectToSaxonObject(property));
        }
        return atomicValueList;
    }
}
