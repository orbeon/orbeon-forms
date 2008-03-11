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
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.orbeon.saxon.om.NodeInfo;
import org.orbeon.saxon.om.Item;

import java.util.Map;

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
            final String rawShowAttribute = actionElement.attributeValue("show");
            showAttribute = (rawShowAttribute == null) ? "replace" : rawShowAttribute;
            if (!("replace".equals(showAttribute) || "new".equals(showAttribute)))
                throw new OXFException("Invalid value for 'show' attribute on xforms:load element: " + showAttribute);
        }
        final boolean doReplace = "replace".equals(showAttribute);
        final String target = actionElement.attributeValue(XFormsConstants.XXFORMS_TARGET_QNAME);
        final String urlType = actionElement.attributeValue(XMLConstants.FORMATTING_URL_TYPE_QNAME);
        final boolean urlNorewrite = XFormsUtils.resolveUrlNorewrite(actionElement);
        final boolean isShowProgress = !"false".equals(actionElement.attributeValue(XFormsConstants.XXFORMS_SHOW_PROGRESS_QNAME));

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
            // Use linking attribute

            // NOP if there is an AVT but no context node
            if (bindingContext.getSingleNode() == null && resourceAttributeValue.indexOf('{') != -1)
                return;

            // Resolve AVT
            final Map prefixToURIMap = containingDocument.getStaticState().getNamespaceMappings(actionElement.attributeValue("id"));
            final LocationData locationData = (LocationData) actionElement.getData();
            final XFormsContextStack contextStack = actionInterpreter.getContextStack();
            final String resolvedResource = XFormsUtils.resolveAttributeValueTemplates(pipelineContext, bindingContext.getNodeset(), bindingContext.getPosition(),
                    contextStack.getCurrentVariables(), XFormsContainingDocument.getFunctionLibrary(), actionInterpreter.getFunctionContext(), prefixToURIMap, locationData, resourceAttributeValue);
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
            if ((!isPortletLoad) ? doReplace : (doReplace && !"resource".equals(urlType))) {
                externalURL = XFormsUtils.resolveURLDoReplace(containingDocument, pipelineContext, currentElement, value);
            } else {
                // Just a resource URL
                externalURL = XFormsUtils.resolveResourceURL(pipelineContext, currentElement, value);
            }
        }
        containingDocument.addLoadToRun(externalURL, target, urlType, doReplace, isPortletLoad, isShowProgress);
        return externalURL;
    }
}
