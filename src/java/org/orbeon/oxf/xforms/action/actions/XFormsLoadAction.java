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
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.xforms.*;
import org.orbeon.oxf.xforms.action.XFormsAction;
import org.orbeon.oxf.xforms.action.XFormsActionInterpreter;
import org.orbeon.oxf.xforms.event.XFormsEventHandlerContainer;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.saxon.om.Item;
import org.orbeon.saxon.om.NodeInfo;

/**
 * 10.1.8 The load Element
 */
public class XFormsLoadAction extends XFormsAction {
    public void execute(XFormsActionInterpreter actionInterpreter, PipelineContext pipelineContext, String targetId,
                        XFormsEventHandlerContainer eventHandlerContainer, Element actionElement,
                        boolean hasOverriddenContext, Item overriddenContext) {

        final XFormsContainingDocument containingDocument = actionInterpreter.getContainingDocument();

        final String resourceAttributeValue = actionElement.attributeValue("resource");

        final String showAttribute;
        {
            final String rawShowAttribute = resolveAVT(actionInterpreter, pipelineContext, actionElement, "show", false);
            showAttribute = (rawShowAttribute == null) ? "replace" : rawShowAttribute;
            if (!("replace".equals(showAttribute) || "new".equals(showAttribute)))
                throw new OXFException("Invalid value for 'show' attribute on xforms:load element: " + showAttribute);
        }
        final boolean doReplace = "replace".equals(showAttribute);
        final String target = resolveAVT(actionInterpreter, pipelineContext, actionElement, XFormsConstants.XXFORMS_TARGET_QNAME, false);
        final String urlType = resolveAVT(actionInterpreter, pipelineContext, actionElement, XMLConstants.FORMATTING_URL_TYPE_QNAME, false);
        final boolean urlNorewrite = XFormsUtils.resolveUrlNorewrite(actionElement);
        final boolean isShowProgress = !"false".equals(resolveAVT(actionInterpreter, pipelineContext, actionElement, XFormsConstants.XXFORMS_SHOW_PROGRESS_QNAME, false));

        // "If both are present, the action has no effect."
        final XFormsContextStack.BindingContext bindingContext = actionInterpreter.getContextStack().getCurrentBindingContext();
        if (bindingContext.isNewBind() && resourceAttributeValue != null)
            return;

        if (bindingContext.isNewBind()) {
            // Use single-node binding
            final NodeInfo currentNode = bindingContext.getSingleNode();
            if (currentNode != null) {
                final String value = XFormsInstance.getValueForNodeInfo(currentNode);
                final String encodedValue = XFormsUtils.encodeHRRI(value, true);
                resolveLoadValue(containingDocument, pipelineContext, actionElement, doReplace, encodedValue, target, urlType, urlNorewrite, isShowProgress);
            } else {
                // The action is a NOP if it's not bound to a node
                return;
            }
            // NOTE: We are supposed to throw an xforms-link-error in case of failure. Can we do it?
        } else if (resourceAttributeValue != null) {
            // Use resource attribute

            // NOP if there is an AVT but no context node
            if (bindingContext.getSingleNode() == null && resourceAttributeValue.indexOf('{') != -1)
                return;

            // Resolve AVT
            final String resolvedResource = resolveAVT(actionInterpreter, pipelineContext, actionElement, "resource", false);
            final String encodedResource = XFormsUtils.encodeHRRI(resolvedResource, true);
            resolveLoadValue(containingDocument, pipelineContext, actionElement, doReplace, encodedResource, target, urlType, urlNorewrite, isShowProgress);
            // NOTE: We are supposed to throw an xforms-link-error in case of failure. Can we do it?
        } else {
            // "Either the single node binding attributes, pointing to a URI in the instance
            // data, or the linking attributes are required."
            throw new OXFException("Missing 'resource' or 'ref' attribute on xforms:load element.");
        }
    }

    public static String resolveLoadValue(XFormsContainingDocument containingDocument, PipelineContext pipelineContext,
                                          Element currentElement, boolean doReplace, String value, String target, String urlType, boolean urlNorewrite, boolean isShowProgress) {

        final boolean isPortletLoad = "portlet".equals(containingDocument.getContainerType());
        final String externalURL;
        if (value.startsWith("#") || urlNorewrite) {
            // Keep value unchanged if it's just a fragment or if we are explicitly disabling rewriting
            externalURL = value;
        } else {
            // In noscript mode, produce an absolute URL because it will be passed to
            // ExternalContext.Response.sendRedirect(), which atm prepends the context path. Possibly, the external
            // URL could be made to not include the context path, but then there may be a need for additional info in
            // Load to differentiate between an absolute path with context and one without context.

            final boolean generateAbsoluteURL = XFormsProperties.isNoscript(containingDocument);
            if ((!isPortletLoad) ? doReplace : (doReplace && !"resource".equals(urlType))) {
                externalURL = XFormsUtils.resolveRenderOrActionURL(isPortletLoad, pipelineContext, currentElement, value, generateAbsoluteURL);
            } else {
                // Just a resource URL
                externalURL = XFormsUtils.resolveResourceURL(pipelineContext, currentElement, value, generateAbsoluteURL);
            }
        }
        containingDocument.addLoadToRun(externalURL, target, urlType, doReplace, isPortletLoad, isShowProgress);
        return externalURL;
    }
}
