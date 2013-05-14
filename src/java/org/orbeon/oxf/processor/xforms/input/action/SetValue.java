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

import org.jaxen.FunctionContext;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.util.SecureUtils;
import org.orbeon.oxf.xforms.XFormsInstance;
import org.orbeon.oxf.xforms.InstanceData;
import org.orbeon.oxf.xforms.XFormsProperties;
import org.orbeon.saxon.om.DocumentInfo;
import org.dom4j.Node;

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

    public void run(PipelineContext pipelineContext, FunctionContext functionContext, String encryptionPassword, DocumentInfo instanceDocumentInfo) {

        // Fill the instance
        final String[] ids = nodeset.split(" ");
        try {
            String id = ids[0];
            if (XFormsProperties.isNameEncryptionEnabled())
                id = SecureUtils.decryptAsString(pipelineContext, encryptionPassword, id);
            final Integer idInteger = new Integer(Integer.parseInt(id));

            final Node node = (Node) InstanceData.getIdToNodeMap(instanceDocumentInfo).get(idInteger);
            final String newValue = value != null ? value : content == null ? "" : content;

            XFormsInstance.setValueForNode(pipelineContext, node, newValue, null);
        } catch (NumberFormatException e) {
            throw new OXFException("Invalid node-id in setvalue action", e);
        }
    }
}
