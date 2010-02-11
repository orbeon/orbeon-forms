/**
 * Copyright (C) 2010 Orbeon, Inc.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version
 * 2.1 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.xforms.action.actions;

import org.dom4j.Element;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.util.PropertyContext;
import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.XFormsContainingDocument;
import org.orbeon.oxf.xforms.XFormsContextStack;
import org.orbeon.oxf.xforms.XFormsUtils;
import org.orbeon.oxf.xforms.action.XFormsAction;
import org.orbeon.oxf.xforms.action.XFormsActionInterpreter;
import org.orbeon.oxf.xforms.event.XFormsEvent;
import org.orbeon.oxf.xforms.event.XFormsEventObserver;
import org.orbeon.oxf.xforms.xbl.XBLBindings;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.saxon.om.Item;

/**
 * 10.1.8 The load Element
 */
public class XFormsLoadAction extends XFormsAction {
    public void execute(XFormsActionInterpreter actionInterpreter, PropertyContext propertyContext, XFormsEvent event,
                        XFormsEventObserver eventObserver, Element actionElement,
                        XBLBindings.Scope actionScope, boolean hasOverriddenContext, Item overriddenContext) {

        final XFormsContainingDocument containingDocument = actionInterpreter.getContainingDocument();

        final String resourceAttributeValue = actionElement.attributeValue("resource");

        final String showAttribute;
        {
            final String rawShowAttribute = actionInterpreter.resolveAVT(propertyContext, actionElement, "show", false);
            showAttribute = (rawShowAttribute == null) ? "replace" : rawShowAttribute;
            if (!("replace".equals(showAttribute) || "new".equals(showAttribute)))
                throw new OXFException("Invalid value for 'show' attribute on xforms:load element: " + showAttribute);
        }
        final boolean doReplace = "replace".equals(showAttribute);
        final String target = actionInterpreter.resolveAVT(propertyContext, actionElement, XFormsConstants.XXFORMS_TARGET_QNAME, false);
        final String urlType = actionInterpreter.resolveAVT(propertyContext, actionElement, XMLConstants.FORMATTING_URL_TYPE_QNAME, false);
        final boolean urlNorewrite = XFormsUtils.resolveUrlNorewrite(actionElement);
        final boolean isShowProgress = !"false".equals(actionInterpreter.resolveAVT(propertyContext, actionElement, XFormsConstants.XXFORMS_SHOW_PROGRESS_QNAME, false));

        // "If both are present, the action has no effect."
        final XFormsContextStack.BindingContext bindingContext = actionInterpreter.getContextStack().getCurrentBindingContext();
        if (bindingContext.isNewBind() && resourceAttributeValue != null)
            return;

        if (bindingContext.isNewBind()) {
            // Use single-node binding
            final String tempValue = XFormsUtils.getBoundItemValue(bindingContext.getSingleItem());
            if (tempValue != null) {
                final String encodedValue = XFormsUtils.encodeHRRI(tempValue, true);
                resolveStoreLoadValue(containingDocument, propertyContext, actionElement, doReplace, encodedValue, target, urlType, urlNorewrite, isShowProgress);
            } else {
                // The action is a NOP if it's not bound to a node
            }
            // NOTE: We are supposed to throw an xforms-link-error in case of failure. Can we do it?
        } else if (resourceAttributeValue != null) {
            // Use resource attribute

            // NOP if there is an AVT but no context node
            if (bindingContext.getSingleItem() == null && resourceAttributeValue.indexOf('{') != -1)
                return;

            // Resolve AVT
            final String resolvedResource = actionInterpreter.resolveAVT(propertyContext, actionElement, "resource", false);
            final String encodedResource = XFormsUtils.encodeHRRI(resolvedResource, true);
            resolveStoreLoadValue(containingDocument, propertyContext, actionElement, doReplace, encodedResource, target, urlType, urlNorewrite, isShowProgress);
            // NOTE: We are supposed to throw an xforms-link-error in case of failure. Can we do it?
        } else {
            // "Either the single node binding attributes, pointing to a URI in the instance
            // data, or the linking attributes are required."
            throw new OXFException("Missing 'resource' or 'ref' attribute on xforms:load element.");
        }
    }

    public static String resolveStoreLoadValue(XFormsContainingDocument containingDocument, PropertyContext propertyContext,
                                               Element currentElement, boolean doReplace, String value, String target,
                                               String urlType, boolean urlNorewrite, boolean isShowProgress) {

        final boolean isPortlet = "portlet".equals(containingDocument.getContainerType());
        final String externalURL;
        if (value.startsWith("#") || urlNorewrite) {
            // Keep value unchanged if it's just a fragment or if we are explicitly disabling rewriting
            // TODO: Not clear what happens in portlet mode: does norewrite make any sense?
            externalURL = value;
        } else {
            // URL must be resolved
            if ("resource".equals(urlType) || isPortlet && !doReplace) {
                // Load as resource URL
                // In a portlet, there is not much sense in opening a new portlet "window", so in this case we open as a resource URL
                externalURL = XFormsUtils.resolveResourceURL(propertyContext, currentElement, value,
                        ExternalContext.Response.REWRITE_MODE_ABSOLUTE_PATH_OR_RELATIVE);
            } else {
                // Load as render URL
                externalURL = XFormsUtils.resolveRenderURL(isPortlet, propertyContext, currentElement, value);
            }
        }
        containingDocument.addLoadToRun(externalURL, target, urlType, doReplace, isPortlet, isShowProgress);
        return externalURL;
    }
}
