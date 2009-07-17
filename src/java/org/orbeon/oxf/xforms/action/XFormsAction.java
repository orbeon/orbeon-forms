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

import org.apache.commons.lang.StringUtils;
import org.dom4j.Element;
import org.dom4j.QName;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.util.PropertyContext;
import org.orbeon.oxf.util.XPathCache;
import org.orbeon.oxf.xforms.*;
import org.orbeon.oxf.xforms.event.XFormsEvent;
import org.orbeon.oxf.xforms.event.XFormsEventObserver;
import org.orbeon.oxf.xforms.xbl.XBLContainer;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.orbeon.saxon.om.Item;
import org.orbeon.saxon.value.SequenceExtent;

import java.util.Map;

/**
 * Base class for all actions.
 */
public abstract class XFormsAction {
    public abstract void execute(XFormsActionInterpreter actionInterpreter, PropertyContext propertyContext,
                                 String targetEffectiveId, XFormsEventObserver eventObserver, Element actionElement,
                                 boolean hasOverriddenContext, Item overriddenContext);

    /**
     * Add event context attributes based on nested xxforms:context elements.
     *
     * @param actionInterpreter current XFormsActionInterpreter
     * @param propertyContext
     * @param actionElement     action element
     * @param event             event to add context information to
     */
    protected void addContextAttributes(XFormsActionInterpreter actionInterpreter, PropertyContext propertyContext, Element actionElement, XFormsEvent event) {
        // Check if there are parameters specified

        final XFormsContextStack contextStack = actionInterpreter.getContextStack();

        for (Object o: actionElement.elements(XFormsConstants.XXFORMS_CONTEXT_QNAME)) {
            final Element currentContextInfo = (Element) o;

            final String name = Dom4jUtils.qNameToExplodedQName(Dom4jUtils.extractAttributeValueQName(currentContextInfo, "name"));
            if (name == null)
                throw new OXFException(XFormsConstants.XXFORMS_CONTEXT_QNAME + " element must have a \"name\" attribute.");

            final String select = currentContextInfo.attributeValue("select");
            if (select == null)
                throw new OXFException(XFormsConstants.XXFORMS_CONTEXT_QNAME + " element must have a \"select\" attribute.");

            // Evaluate context parameter
            final SequenceExtent value = XPathCache.evaluateAsExtent(propertyContext,
                    actionInterpreter.getContextStack().getCurrentNodeset(), actionInterpreter.getContextStack().getCurrentPosition(),
                    select, actionInterpreter.getNamespaceMappings(actionElement),
                    contextStack.getCurrentVariables(), XFormsContainingDocument.getFunctionLibrary(),
                    contextStack.getFunctionContext(), null,
                    (LocationData) actionElement.getData());

            event.setAttribute(name, value);
        }
    }

    /**
     * Resolve the value of an attribute which may be an AVT.
     *
     * @param actionInterpreter current XFormsActionInterpreter
     * @param propertyContext
     * @param actionElement     action element
     * @param attributeName     name of the attribute containing the value
     * @param isNamespace       whether to namespace the resulting value
     * @return                  resolved attribute value
     */
    protected String resolveAVT(XFormsActionInterpreter actionInterpreter, PropertyContext propertyContext, Element actionElement, String attributeName, boolean isNamespace) {
        // Get raw attribute value
        final String attributeValue = actionElement.attributeValue(attributeName);
        if (attributeValue == null)
            return null;

        return resolveAVTProvideValue(actionInterpreter, propertyContext, actionElement, attributeValue, isNamespace);
    }

    /**
     * Resolve the value of an attribute which may be an AVT.
     *
     * @param actionInterpreter current XFormsActionInterpreter
     * @param propertyContext
     * @param actionElement     action element
     * @param attributeName     QName of the attribute containing the value
     * @param isNamespace       whether to namespace the resulting value
     * @return                  resolved attribute value
     */
    protected String resolveAVT(XFormsActionInterpreter actionInterpreter, PropertyContext propertyContext, Element actionElement, QName attributeName, boolean isNamespace) {
        // Get raw attribute value
        final String attributeValue = actionElement.attributeValue(attributeName);
        if (attributeValue == null)
            return null;

        return resolveAVTProvideValue(actionInterpreter, propertyContext, actionElement, attributeValue, isNamespace);
    }

    /**
     * Resolve a value which may be an AVT.
     *
     * @param actionInterpreter current XFormsActionInterpreter
     * @param propertyContext
     * @param actionElement     action element
     * @param attributeValue    raw value to resolve
     * @param isNamespace       whether to namespace the resulting value
     * @return                  resolved attribute value
     */
    protected String resolveAVTProvideValue(XFormsActionInterpreter actionInterpreter, PropertyContext propertyContext, Element actionElement, String attributeValue, boolean isNamespace) {

        if (attributeValue == null)
            return null;

        // Whether this can't be an AVT
        final boolean maybeAvt = attributeValue.indexOf('{') != -1;

        final XFormsContainingDocument containingDocument = actionInterpreter.getContainingDocument();
        final String resolvedAVTValue;
        if (maybeAvt) {
            // We have to go through AVT evaluation
            final XFormsContextStack contextStack = actionInterpreter.getContextStack();
            final XFormsContextStack.BindingContext bindingContext = contextStack.getCurrentBindingContext();

            // We don't have an evaluation context so return
            // TODO: In the future we want to allow an empty evaluation context so do we really want this check?
            if (bindingContext.getSingleNode() == null)
                return null;

            final Map<String, String> prefixToURIMap = actionInterpreter.getNamespaceMappings(actionElement);
            final LocationData locationData = (LocationData) actionElement.getData();

            resolvedAVTValue = XFormsUtils.resolveAttributeValueTemplates(propertyContext, bindingContext.getNodeset(),
                        bindingContext.getPosition(), contextStack.getCurrentVariables(), XFormsContainingDocument.getFunctionLibrary(),
                        actionInterpreter.getFunctionContext(), prefixToURIMap, locationData, attributeValue);
        } else {
            // We optimize as this doesn't need AVT evaluation
            resolvedAVTValue = attributeValue;
        }

        return isNamespace ? XFormsUtils.namespaceId(containingDocument, resolvedAVTValue) : resolvedAVTValue;
    }

    /**
     * Find an effective object based on either the xxforms:repeat-indexes attribute, or on the current repeat indexes.
     *
     * @param actionInterpreter current XFormsActionInterpreter
     * @param propertyContext
     * @param sourceEffectiveId effective id of the source action
     * @param targetStaticId    target to resolve
     * @param actionElement     current action element
     * @return                  effective control if found
     */
    protected Object resolveEffectiveControl(XFormsActionInterpreter actionInterpreter, PropertyContext propertyContext, String sourceEffectiveId, String targetStaticId, Element actionElement) {

        final XFormsControls controls = actionInterpreter.getXFormsControls();

        // Get indexes as space-separated list
        final String repeatindexes = resolveAVT(actionInterpreter, propertyContext, actionElement, XFormsConstants.XXFORMS_REPEAT_INDEXES_QNAME, false);
        if (repeatindexes != null && !"".equals(repeatindexes.trim())) {
            // Effective id is provided, modify appropriately
            return controls.getObjectByEffectiveId(actionInterpreter.getXBLContainer().getFullPrefix() + targetStaticId + XFormsConstants.REPEAT_HIERARCHY_SEPARATOR_1 + StringUtils.join(StringUtils.split(repeatindexes), XFormsConstants.REPEAT_HIERARCHY_SEPARATOR_2));
        } else {
            // Figure out effective id
            return actionInterpreter.getXBLContainer().resolveObjectById(sourceEffectiveId, targetStaticId);
        }
    }

    /**
     * Resolve an object by passing the
     *
     * @param actionInterpreter current XFormsActionInterpreter
     * @param propertyContext
     * @param eventObserver     event observer
     * @param objectStaticId    target to resolve
     * @param actionElement     current action element
     * @return                  effective control if found
     */
    protected Object resolveEffectiveObject(XFormsActionInterpreter actionInterpreter, PropertyContext propertyContext, XFormsEventObserver eventObserver, String objectStaticId, Element actionElement) {
        // First try controls as we want to check on explicit repeat indexes first
        final Object tempXFormsEventTarget = resolveEffectiveControl(actionInterpreter, propertyContext, eventObserver.getEffectiveId(), objectStaticId, actionElement);
        if (tempXFormsEventTarget != null) {
            // Object with this id exists
            return tempXFormsEventTarget;
        } else {
            // Otherwise, try container
            final XBLContainer container = actionInterpreter.getXBLContainer();
            return container.resolveObjectById(null, objectStaticId);
        }
    }
}
