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

import org.dom4j.Node;
import org.jaxen.FunctionContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.util.SecureUtils;
import org.orbeon.oxf.xforms.InstanceData;
import org.orbeon.oxf.xforms.XFormsProperties;
import org.orbeon.saxon.om.DocumentInfo;

import java.util.Map;

public class Delete implements Action {

    private String nodeset;
    private String at;

    public void setParameters(Map parameters) {
        nodeset = (String) parameters.get(NODESET_ATTRIBUTE_NAME);
        at = (String) parameters.get(AT_ATTRIBUTE_NAME);
    }

    public void run(PipelineContext context, FunctionContext functionContext, String encryptionPassword, DocumentInfo instanceDocumentInfo) {
        final String[] ids = nodeset.split(" ");
        if (XFormsProperties.isNameEncryptionEnabled())
            at = SecureUtils.decryptAsString(context, encryptionPassword, at);
        String id = ids[Integer.parseInt(at) - 1];
        if (XFormsProperties.isNameEncryptionEnabled())
            id = SecureUtils.decryptAsString(context, encryptionPassword, id);

        final Node nodeToRemove = (Node) InstanceData.getIdToNodeMap(instanceDocumentInfo).get(new Integer(id));
        nodeToRemove.getParent().remove(nodeToRemove);
    }
}
