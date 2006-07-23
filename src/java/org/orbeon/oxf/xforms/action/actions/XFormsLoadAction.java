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
import org.dom4j.Namespace;
import org.dom4j.QName;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.xforms.*;
import org.orbeon.oxf.xforms.action.XFormsAction;
import org.orbeon.oxf.xforms.action.XFormsActionInterpreter;
import org.orbeon.oxf.xforms.event.XFormsEventHandlerContainer;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.saxon.om.NodeInfo;

/**
 * 10.1.8 The load Element
 */
public class XFormsLoadAction extends XFormsAction {
    public void execute(XFormsActionInterpreter actionInterpreter, PipelineContext pipelineContext, String targetId, XFormsEventHandlerContainer eventHandlerContainer, Element actionElement) {

        final XFormsControls xformsControls = actionInterpreter.getXFormsControls();
        final XFormsContainingDocument containingDocument = actionInterpreter.getContainingDocument();

        final String ref = actionElement.attributeValue("ref");
        final String resource = actionElement.attributeValue("resource");
        final String showAttribute;
        {
            final String rawShowAttribute = actionElement.attributeValue("show");
            showAttribute = (rawShowAttribute == null) ? "replace" : rawShowAttribute;
            if (!("replace".equals(showAttribute) || "new".equals(showAttribute)))
                throw new OXFException("Invalid value for 'show' attribute on xforms:load element: " + showAttribute);
        }
        final boolean doReplace = "replace".equals(showAttribute);
        final String target = actionElement.attributeValue(XFormsConstants.XXFORMS_TARGET_QNAME);
        final String urlType = actionElement.attributeValue(new QName("url-type", new Namespace("f", XMLConstants.OPS_FORMATTING_URI)));

        // "If both are present, the action has no effect."
        if (ref != null && resource != null)
            return;

        if (ref != null) {
            // Use single-node binding
            final NodeInfo currentNode = xformsControls.getCurrentSingleNode();
            if (currentNode != null) {
                final String value = XFormsInstance.getValueForNode(currentNode);
                resolveLoadValue(containingDocument, pipelineContext, actionElement, doReplace, value, target, urlType);
            } else {
                // The action is a NOP if it's not bound to a node
                return;
            }
            // NOTE: We are supposed to throw an xforms-link-error in case of failure. Can we do it?
        } else if (resource != null) {
            // Use linking attribute
            resolveLoadValue(containingDocument, pipelineContext, actionElement, doReplace, resource, target, urlType);
            // NOTE: We are supposed to throw an xforms-link-error in case of failure. Can we do it?
        } else {
            // "Either the single node binding attributes, pointing to a URI in the instance
            // data, or the linking attributes are required."
            throw new OXFException("Missing 'resource' or 'ref' attribute on xforms:load element.");
        }
    }

    public static String resolveLoadValue(XFormsContainingDocument containingDocument, PipelineContext pipelineContext, Element currentElement, boolean doReplace, String value, String target, String urlType) {

        final boolean isPortletLoad = "portlet".equals(containingDocument.getContainerType());
        final String externalURL = XFormsUtils.resolveURL(containingDocument, pipelineContext, currentElement, (!isPortletLoad) ? doReplace : (doReplace && !"resource".equals(urlType)), value);
        containingDocument.addLoadToRun(externalURL, target, urlType, doReplace, isPortletLoad);
        return externalURL;
    }
}
