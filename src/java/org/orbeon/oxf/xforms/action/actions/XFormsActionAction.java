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
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.xforms.action.XFormsAction;
import org.orbeon.oxf.xforms.action.XFormsActionInterpreter;
import org.orbeon.oxf.xforms.event.XFormsEventHandlerContainer;
import org.orbeon.oxf.xforms.XFormsContextStack;
import org.orbeon.saxon.om.Item;

import java.util.Iterator;

/**
 * 10.1.1 The action Element
 */
public class XFormsActionAction extends XFormsAction {
    public void execute(XFormsActionInterpreter actionInterpreter, PipelineContext pipelineContext, String targetId,
                        XFormsEventHandlerContainer eventHandlerContainer, Element actionElement,
                        boolean hasOverriddenContext, Item overriddenContext) {
        // Iterate over child actions
        final XFormsContextStack contextStack = actionInterpreter.getContextStack();
        for (Iterator i = actionElement.elementIterator(); i.hasNext();) {
            final Element currentActionElement = (Element) i.next();

            // Set context on action element
            contextStack.pushBinding(pipelineContext, currentActionElement);

            // Run action
            actionInterpreter.runAction(pipelineContext, targetId, eventHandlerContainer, currentActionElement);

            // Restore context
            contextStack.popBinding();
        }
    }
}
