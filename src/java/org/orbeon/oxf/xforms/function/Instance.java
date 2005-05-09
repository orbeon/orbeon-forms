package org.orbeon.oxf.xforms.function;

import org.orbeon.oxf.xforms.XFormsInstance;
import org.orbeon.oxf.xforms.XFormsModel;
import org.orbeon.saxon.dom4j.DocumentWrapper;
import org.orbeon.saxon.expr.Expression;
import org.orbeon.saxon.expr.XPathContext;
import org.orbeon.saxon.om.Item;
import org.orbeon.saxon.xpath.XPathException;

/**
 * XForms instance() function.
 *
 * 7.11.1 The instance() Function
 */
public class Instance extends XFormsFunction {

    public Item evaluateItem(XPathContext c) throws XPathException {

        // Get model id
        Expression instanceIdExpression = argument[0];
        String instanceId = instanceIdExpression.evaluateAsString(c);

        // Get model and instance with given id for that model
        XFormsModel model = (getXFormsControls() != null) ? getXFormsControls().getCurrentModel() : getXFormsModel();
        XFormsInstance instance = model.getInstance(instanceId);

        // Return instance document
        return new DocumentWrapper(instance.getDocument(), null);
    }
}
