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
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.xforms.XFormsContainingDocument;
import org.orbeon.oxf.xforms.XFormsModel;
import org.orbeon.oxf.xforms.XFormsUtils;
import org.orbeon.oxf.xforms.XFormsContainer;
import org.orbeon.oxf.xforms.action.XFormsAction;
import org.orbeon.oxf.xforms.action.XFormsActionInterpreter;
import org.orbeon.oxf.xforms.event.XFormsEventHandlerContainer;
import org.orbeon.oxf.xforms.event.events.XFormsRebuildEvent;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.orbeon.saxon.om.Item;

/**
 * 10.1.3 The rebuild Element
 */
public class XFormsRebuildAction extends XFormsAction {
    public void execute(XFormsActionInterpreter actionInterpreter, PipelineContext pipelineContext, String targetId,
                        XFormsEventHandlerContainer eventHandlerContainer, Element actionElement,
                        boolean hasOverriddenContext, Item overriddenContext) {

        final XFormsContainer container = actionInterpreter.getContainer();
        final XFormsContainingDocument containingDocument = actionInterpreter.getContainingDocument();

        final String modelId = XFormsUtils.namespaceId(containingDocument, actionElement.attributeValue("model"));
        final XFormsModel model = (modelId != null) ? container.getModelByEffectiveId(modelId) : actionInterpreter.getContextStack().getCurrentModel();// xxx fix not effective

        if (model == null)
            throw new ValidationException("Invalid model id: " + modelId, (LocationData) actionElement.getData());

        container.dispatchEvent(pipelineContext, new XFormsRebuildEvent(model));
    }
}
