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
import org.dom4j.Node;
import org.jaxen.FunctionContext;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.util.SecureUtils;
import org.orbeon.oxf.xforms.InstanceData;
import org.orbeon.oxf.xforms.XFormsUtils;

import java.util.Map;

public class SetValue implements Action {

    private String nodeset;
    private String value;
    private String content;

    public void setParameters(Map parameters) {
        nodeset = (String) parameters.get(NODESET_ATTRIBUTE_NAME);
        value = (String) parameters.get("value");
        content = (String) parameters.get("content");
    }

    public void run(PipelineContext context, FunctionContext functionContext, String encryptionPassword, Document instance) {

        // Fill the instance
        String[] ids = nodeset.split(" ");
        try {
            String id = ids[0];
            if (XFormsUtils.isNameEncryptionEnabled())
                id = SecureUtils.decrypt(context, encryptionPassword, id);
            Integer idInteger = new Integer(Integer.parseInt(id));
            Node node = (Node) ((InstanceData) instance.getRootElement().getData()).getIdToNodeMap().get(idInteger);
            String newValue = value != null ? value : content == null ? "" : content;
            XFormsUtils.fillNode(node, newValue);
        } catch (NumberFormatException e) {
            throw new OXFException("Invalid node-id in setvalue action", e);
        }
    }
}
