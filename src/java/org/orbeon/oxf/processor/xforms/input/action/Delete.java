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

import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.Node;
import org.jaxen.FunctionContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.xforms.output.InstanceData;

import java.util.List;
import java.util.Map;

public class Delete implements Action {

    private String nodeset;
    private String at;

    public void setParameters(Map parameters) {
        nodeset = (String) parameters.get(NODESET_ATTRIBUTE_NAME);
        at = (String) parameters.get(AT_ATTRIBUTE_NAME);
    }

    public void run(PipelineContext context, FunctionContext functionContext, Document instance) {
        String[] ids = nodeset.split(" ");
        Integer id = new Integer(ids[Integer.parseInt(at) - 1]);
        Node nodeToRemove = (Node) ((InstanceData) instance.getRootElement().getData()).getIdToNodeMap().get(id);
        nodeToRemove.getParent().remove(nodeToRemove);
    }
}
