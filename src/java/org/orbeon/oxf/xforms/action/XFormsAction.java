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
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.util.XPathCache;
import org.orbeon.oxf.xforms.*;
import org.orbeon.oxf.xforms.event.XFormsEvent;
import org.orbeon.oxf.xforms.event.XFormsEventObserver;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.orbeon.saxon.om.Item;
import org.orbeon.saxon.value.SequenceExtent;

import java.util.Iterator;
import java.util.Map;

/**
 * Base class for all actions.
 */
public abstract class XFormsAction {
    public abstract void execute(XFormsActionInterpreter actionInterpreter, PipelineContext pipelineContext,
                                 String targetId, XFormsEventObserver eventObserver, Element actionElement,
                                 boolean hasOverriddenContext, Item overriddenContext);

    /**
     * Add event context attributes based on nested xxforms:context elements.
     *
     * @param actionInterpreter current XFormsActionInterpreter
     * @param pipelineContext   current PipelineContext
     * @param actionElement     action element
     * @param event             event to add context information to
     */
    protected void addContextAttributes(XFormsActionInterpreter actionInterpreter, PipelineContext pipelineContext, Element actionElement, XFormsEvent event) {
        // Check if there are parameters specified

        final XFormsContainingDocument containingDocument = actionInterpreter.getContainingDocument();
        final XFormsContextStack contextStack = actionInterpreter.getContextStack();

        for (Iterator i = actionElement.elements(XFormsConstants.XXFORMS_CONTEXT_QNAME).iterator(); i.hasNext();) {
            final Element currentContextInfo = (Element) i.next();

            final String name = Dom4jUtils.qNameToexplodedQName(Dom4jUtils.extractAttributeValueQName(currentContextInfo, "name"));
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

    /**
     * Resolve the value of an attribute which may be an AVT.
     *
     * @param actionInterpreter current XFormsActionInterpreter
     * @param pipelineContext   current PipelineContext
     * @param actionElement     action element
     * @param attributeName     name of the attribute containing the value
     * @param isNamespace       whether to namespace the resulting value
     * @return                  resolved attribute value
     */
    protected String resolveAVT(XFormsActionInterpreter actionInterpreter, PipelineContext pipelineContext, Element actionElement, String attributeName, boolean isNamespace) {
        // Get raw attribute value
        final String attributeValue = actionElement.attributeValue(attributeName);
        if (attributeValue == null)
            return null;

        return resolveAVTProvideValue(actionInterpreter, pipelineContext, actionElement, attributeValue, isNamespace);
    }

    /**
     * Resolve the value of an attribute which may be an AVT.
     *
     * @param actionInterpreter current XFormsActionInterpreter
     * @param pipelineContext   current PipelineContext
     * @param actionElement     action element
     * @param attributeName     QName of the attribute containing the value
     * @param isNamespace       whether to namespace the resulting value
     * @return                  resolved attribute value
     */
    protected String resolveAVT(XFormsActionInterpreter actionInterpreter, PipelineContext pipelineContext, Element actionElement, QName attributeName, boolean isNamespace) {
        // Get raw attribute value
        final String attributeValue = actionElement.attributeValue(attributeName);
        if (attributeValue == null)
            return null;

        return resolveAVTProvideValue(actionInterpreter, pipelineContext, actionElement, attributeValue, isNamespace);
    }

    /**
     * Resolve a value which may be an AVT.
     *
     * @param actionInterpreter current XFormsActionInterpreter
     * @param pipelineContext   current PipelineContext
     * @param actionElement     action element
     * @param attributeValue    raw value to resolve
     * @param isNamespace       whether to namespace the resulting value
     * @return                  resolved attribute value
     */
    protected String resolveAVTProvideValue(XFormsActionInterpreter actionInterpreter, PipelineContext pipelineContext, Element actionElement, String attributeValue, boolean isNamespace) {

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

            final Map prefixToURIMap = containingDocument.getStaticState().getNamespaceMappings(actionElement);
            final LocationData locationData = (LocationData) actionElement.getData();

            resolvedAVTValue = XFormsUtils.resolveAttributeValueTemplates(pipelineContext, bindingContext.getNodeset(),
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
     * @param pipelineContext   current PipelineContext
     * @param sourceEffectiveId effective id of the source action
     * @param targetId          target to resolve
     * @param actionElement     current action element
     * @return                  effective control id if possible
     */
    protected Object resolveEffectiveControl(XFormsActionInterpreter actionInterpreter, PipelineContext pipelineContext, String sourceEffectiveId, String targetId, Element actionElement) {

        final XFormsControls controls = actionInterpreter.getXFormsControls();

        // Get indexes as space-separated list
        final String repeatindexes = resolveAVT(actionInterpreter, pipelineContext, actionElement, XFormsConstants.XXFORMS_REPEAT_INDEXES_QNAME, false);
        if (repeatindexes != null && !"".equals(repeatindexes.trim())) {
            // Effective id is provided, modify appropriately
            // TODO: provided really, but what about prefix for components?
            return controls.getObjectByEffectiveId(targetId + XFormsConstants.REPEAT_HIERARCHY_SEPARATOR_1 + StringUtils.join(StringUtils.split(repeatindexes), XFormsConstants.REPEAT_HIERARCHY_SEPARATOR_2));
        } else {
            // Figure out effective id
            return controls.resolveObjectById(sourceEffectiveId, targetId);
        }
    }
}
