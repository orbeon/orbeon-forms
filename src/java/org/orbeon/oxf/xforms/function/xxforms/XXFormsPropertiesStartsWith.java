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
import java.util.Iterator;
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

    public static List propertiesStartsWith(String propertyName) {
        final List stringList = Properties.instance().getPropertySet().getPropertiesStartsWith(propertyName);
        final List atomicValueList = new ArrayList();
        for (Iterator propertyIterator = stringList.iterator(); propertyIterator.hasNext();) {
            String property = (String) propertyIterator.next();
            if (property.toLowerCase().indexOf("password") == -1)
                atomicValueList.add((AtomicValue) XFormsUtils.convertJavaObjectToSaxonObject(property));
        }
        return atomicValueList;
    }
}
