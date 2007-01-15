/**
 *  Copyright (C) 2006 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.action.actions;

import org.dom4j.Element;
import org.dom4j.QName;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.xforms.XFormsContainingDocument;
import org.orbeon.oxf.xforms.XFormsUtils;
import org.orbeon.oxf.xforms.action.XFormsAction;
import org.orbeon.oxf.xforms.action.XFormsActionInterpreter;
import org.orbeon.oxf.xforms.event.XFormsEventHandlerContainer;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;

/**
 * 10.1.12 The message Element
 */
public class XFormsMessageAction extends XFormsAction {
    public void execute(XFormsActionInterpreter actionInterpreter, PipelineContext pipelineContext, String targetId, XFormsEventHandlerContainer eventHandlerContainer, Element actionElement) {

        final XFormsContainingDocument containingDocument = actionInterpreter.getContainingDocument();

        final String level;
        {
            final String levelAttribute = actionElement.attributeValue("level");;
            if (levelAttribute == null)
                throw new OXFException("xforms:message element is missing mandatory 'level' attribute.");
            final QName levelQName = Dom4jUtils.extractAttributeValueQName(actionElement, "level");
            if (levelQName.getNamespacePrefix().equals("")) {
                if (!("ephemeral".equals(levelAttribute) || "modeless".equals(levelAttribute) || "modal".equals(levelAttribute))) {
                    throw new OXFException("xforms:message element's 'level' attribute must have value: 'ephemeral'|'modeless'|'modal'|QName-but-not-NCName.");
                }
                level = levelAttribute;
            } else {
                level = "{" + levelQName.getNamespaceURI() + "}" + levelQName.getName();
            }
        }

        // Get message value
        // TODO: In the future, we should support HTML
        final String messageValue = XFormsUtils.getChildElementValue(pipelineContext, containingDocument, actionElement, false);
        if (messageValue != null) {
            // Store message for sending to client
            containingDocument.addMessageToRun(messageValue, level);

            // NOTE: In the future, we *may* want to save and resume the event state before and
            // after displaying a message, in order to possibly provide a behavior which is more
            // consistent with what users may expect regarding actions executed after
            // xforms:message.
        }
    }
}
