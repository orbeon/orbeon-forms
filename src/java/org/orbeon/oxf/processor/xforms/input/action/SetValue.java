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
package org.orbeon.oxf.processor.xforms.input.action;

import org.dom4j.*;
import org.jaxen.FunctionContext;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.processor.xforms.XFormsUtils;
import org.orbeon.oxf.xml.XPathUtils;
import org.orbeon.oxf.pipeline.api.PipelineContext;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class SetValue implements Action {

    private String ref;
    private String value;
    private String content;

    public void setParameters(Map parameters) {
        ref = (String) parameters.get("ref");
        value = (String) parameters.get("value");
        content = (String) parameters.get("content");
    }

    public void run(PipelineContext context, FunctionContext functionContext, Document instance) {

        // Compute value
        String newValue;
        if (value == null) {
            newValue = content == null ? "" : content;
        } else {
            XPath valueXPath = XPathUtils.xpathWithFullURI(context, "string(" + value + ")");
            valueXPath.setFunctionContext(functionContext);
            newValue = (String) valueXPath.evaluate(instance);
        }

        // Get referenced part of the instance
        XPath refXPath = XPathUtils.xpathWithFullURI(context, ref);
        refXPath.setFunctionContext(functionContext);
        Object refObject = refXPath.evaluate(instance);

        // Fill the instance
        fill(refObject, newValue);
    }

    private void fill(Object refObject, String newValue) {

        if (refObject instanceof Element || refObject instanceof Attribute) {
            XFormsUtils.fillNode((Node) refObject, newValue);
        } else  if (refObject instanceof List) {
            for (Iterator i = ((List) refObject).iterator(); i.hasNext();) {
                Object child = i.next();
                fill(child, newValue);
            }
        } else {
            throw new OXFException("XPath expression '" + ref
                    + "' must reference an element or an attribute in the instance");
        }
    }
}
