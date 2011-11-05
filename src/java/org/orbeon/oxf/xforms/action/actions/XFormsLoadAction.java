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
import org.orbeon.oxf.util.NetUtils;
import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.XFormsContainingDocument;
import org.orbeon.oxf.xforms.XFormsContextStack;
import org.orbeon.oxf.xforms.XFormsUtils;
import org.orbeon.oxf.xforms.action.XFormsAction;
import org.orbeon.oxf.xforms.action.XFormsActionInterpreter;
import org.orbeon.oxf.xforms.event.XFormsEvent;
import org.orbeon.oxf.xforms.event.XFormsEventObserver;
import org.orbeon.oxf.xforms.model.DataModel;
import org.orbeon.oxf.xforms.xbl.XBLBindingsBase;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.saxon.om.Item;

/**
 * 10.1.8 The load Element
 */
public class XFormsLoadAction extends XFormsAction {
    public void execute(XFormsActionInterpreter actionInterpreter, XFormsEvent event,
                        XFormsEventObserver eventObserver, Element actionElement,
                        XBLBindingsBase.Scope actionScope, boolean hasOverriddenContext, Item overriddenContext) {

        final XFormsContainingDocument containingDocument = actionInterpreter.getContainingDocument();

        final String resourceAttributeValue = actionElement.attributeValue(XFormsConstants.RESOURCE_QNAME);

        final String showAttribute;
        {
            final String rawShowAttribute = actionInterpreter.resolveAVT(actionElement, "show");
            showAttribute = (rawShowAttribute == null) ? "replace" : rawShowAttribute;
            if (!("replace".equals(showAttribute) || "new".equals(showAttribute)))
                throw new OXFException("Invalid value for 'show' attribute on xforms:load element: " + showAttribute);
        }
        final boolean doReplace = "replace".equals(showAttribute);
        final String target = actionInterpreter.resolveAVT(actionElement, XFormsConstants.XXFORMS_TARGET_QNAME);
        final String urlType = actionInterpreter.resolveAVT(actionElement, XMLConstants.FORMATTING_URL_TYPE_QNAME);
        final boolean urlNorewrite = XFormsUtils.resolveUrlNorewrite(actionElement);
        final boolean isShowProgress = !"false".equals(actionInterpreter.resolveAVT(actionElement, XFormsConstants.XXFORMS_SHOW_PROGRESS_QNAME));

        // "If both are present, the action has no effect."
        final XFormsContextStack.BindingContext bindingContext = actionInterpreter.getContextStack().getCurrentBindingContext();
        if (bindingContext.isNewBind() && resourceAttributeValue != null)
            return;

        if (bindingContext.isNewBind()) {
            // Use single-node binding
            final String tempValue = DataModel.getValue(bindingContext.getSingleItem());
            if (tempValue != null) {
                final String encodedValue = NetUtils.encodeHRRI(tempValue, true);
                resolveStoreLoadValue(containingDocument, actionElement, doReplace, encodedValue, target, urlType, urlNorewrite, isShowProgress);
            } else {
                // The action is a NOP if it's not bound to a node
            }
            // NOTE: We are supposed to throw an xforms-link-error in case of failure. Can we do it?
        } else if (resourceAttributeValue != null) {
            // Use resource attribute

            // NOP if there is an AVT but no context node
            if (bindingContext.getSingleItem() == null && XFormsUtils.maybeAVT(resourceAttributeValue))
                return;

            // Resolve AVT
            final String resolvedResource = actionInterpreter.resolveAVT(actionElement, "resource");
            final String encodedResource = NetUtils.encodeHRRI(resolvedResource, true);
            resolveStoreLoadValue(containingDocument, actionElement, doReplace, encodedResource, target, urlType, urlNorewrite, isShowProgress);
            // NOTE: We are supposed to throw an xforms-link-error in case of failure. Can we do it?
        } else {
            // "Either the single node binding attributes, pointing to a URI in the instance
            // data, or the linking attributes are required."
            throw new OXFException("Missing 'resource' or 'ref' attribute on xforms:load element.");
        }
    }

    public static void resolveStoreLoadValue(XFormsContainingDocument containingDocument,
                                             Element currentElement, boolean doReplace, String value, String target,
                                             String urlType, boolean urlNorewrite, boolean isShowProgress) {
        final String externalURL;
        if (value.startsWith("#") || urlNorewrite) {
            // Keep value unchanged if it's just a fragment or if we are explicitly disabling rewriting
            // TODO: Not clear what happens in portlet mode: does norewrite make any sense?
            externalURL = value;
        } else {
            // URL must be resolved
            if ("resource".equals(urlType)) {
                // Load as resource URL
                externalURL = XFormsUtils.resolveResourceURL(containingDocument, currentElement, value,
                        ExternalContext.Response.REWRITE_MODE_ABSOLUTE_PATH_OR_RELATIVE);
            } else {
                // Load as render URL
                // NOTE: Skip URL rewriting step in portlet mode, because this will trigger a two-pass load, and during
                // the second pass we call sendRedirect(), which will actually rewrite the URL.
                externalURL = XFormsUtils.resolveRenderURL(containingDocument, currentElement, value, containingDocument.isPortletContainer());
            }
        }

        // Force no progress indication if this is a JavaScript URL
        if (externalURL.startsWith("javascript:"))
            isShowProgress = false;

        containingDocument.addLoadToRun(externalURL, target, urlType, doReplace, isShowProgress);
    }
}
