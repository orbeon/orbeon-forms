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
package org.orbeon.oxf.xforms.action;

import org.dom4j.Element;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.xforms.event.XFormsEventHandlerContainer;
import org.orbeon.oxf.xforms.event.XFormsEvent;
import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.XFormsContainingDocument;
import org.orbeon.oxf.xforms.XFormsContextStack;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.util.XPathCache;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.orbeon.saxon.om.Item;
import org.orbeon.saxon.value.SequenceExtent;

import java.util.Iterator;

/**
 * Base class for all actions.
 */
public abstract class XFormsAction {
    public abstract void execute(XFormsActionInterpreter actionInterpreter, PipelineContext pipelineContext,
                                 String targetId, XFormsEventHandlerContainer eventHandlerContainer, Element actionElement,
                                 boolean hasOverriddenContext, Item overriddenContext);

    protected void addContextAttributes(XFormsActionInterpreter actionInterpreter, PipelineContext pipelineContext, Element actionElement, XFormsEvent event) {
        // Check if there are parameters specified

        final XFormsContainingDocument containingDocument = actionInterpreter.getContainingDocument();
        final XFormsContextStack contextStack = actionInterpreter.getContextStack();

        for (Iterator i = actionElement.elements(XFormsConstants.XXFORMS_CONTEXT_QNAME).iterator(); i.hasNext();) {
            final Element currentContextInfo = (Element) i.next();

            final String name = currentContextInfo.attributeValue("name");
            if (name == null)
                throw new OXFException(XFormsConstants.XXFORMS_CONTEXT_QNAME + " element must have a \"name\" attribute.");

            final String select = currentContextInfo.attributeValue("select");
            if (select == null)
                throw new OXFException(XFormsConstants.XXFORMS_CONTEXT_QNAME + " element must have a \"select\" attribute.");

            // Evaluate context parameter
            final SequenceExtent value = XPathCache.evaluateAsExtent(pipelineContext,
                actionInterpreter.getContextStack().getCurrentNodeset(), actionInterpreter.getContextStack().getCurrentPosition(),
                select, containingDocument.getNamespaceMappings(actionElement),
                contextStack.getCurrentVariables(), XFormsContainingDocument.getFunctionLibrary(),
                contextStack.getFunctionContext(), null,
                (LocationData) actionElement.getData());

            event.setAttribute(name, value);
        }
    }
}
