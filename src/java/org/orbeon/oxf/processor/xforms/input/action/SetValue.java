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
import org.orbeon.oxf.processor.xforms.output.InstanceData;
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

        // Fill the instance
        Integer id = new Integer(ref);
        Node node = (Node) ((InstanceData) instance.getRootElement().getData()).getIdToNodeMap().get(id);
        String newValue = value != null ? value
                : content == null ? "" : content;
        XFormsUtils.fillNode(node, newValue);
    }
}
