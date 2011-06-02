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
package org.orbeon.oxf.xforms.action;

import org.dom4j.Element;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.util.XPathCache;
import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.XFormsContainingDocument;
import org.orbeon.oxf.xforms.XFormsContextStack;
import org.orbeon.oxf.xforms.analysis.VariableAnalysis;
import org.orbeon.oxf.xforms.event.XFormsEvent;
import org.orbeon.oxf.xforms.event.XFormsEventObserver;
import org.orbeon.oxf.xforms.xbl.XBLBindings;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.orbeon.saxon.om.Item;
import org.orbeon.saxon.value.SequenceExtent;
import org.orbeon.saxon.value.StringValue;

/**
 * Base class for all actions.
 */
public abstract class XFormsAction {
    public abstract void execute(XFormsActionInterpreter actionInterpreter,
                                 XFormsEvent event, XFormsEventObserver eventObserver, Element actionElement,
                                 XBLBindings.Scope actionScope, boolean hasOverriddenContext, Item overriddenContext);

    /**
     * Add event context attributes based on nested xxforms:context elements.
     *
     * @param actionInterpreter current XFormsActionInterpreter
     * @param actionElement     action element
     * @param event             event to add context information to
     */
    protected void addContextAttributes(XFormsActionInterpreter actionInterpreter, Element actionElement, XFormsEvent event) {
        // Check if there are parameters specified

        final XFormsContextStack contextStack = actionInterpreter.getContextStack();

        for (final Element currentContextInfo : Dom4jUtils.elements(actionElement, XFormsConstants.XXFORMS_CONTEXT_QNAME)) {

            // Get and check attributes
            final String name = Dom4jUtils.qNameToExplodedQName(Dom4jUtils.extractAttributeValueQName(currentContextInfo, XFormsConstants.NAME_QNAME));
            if (name == null)
                throw new OXFException(XFormsConstants.XXFORMS_CONTEXT_QNAME + " element must have a \"name\" attribute.");

            final String valueOrSelect = VariableAnalysis.valueOrSelectAttribute(currentContextInfo);

            final SequenceExtent value;
            if (valueOrSelect == null) {
                // Literal text
                value = new SequenceExtent(new Item[] { StringValue.makeStringValue(currentContextInfo.getStringValue()) });
            } else {
                // XPath expression

                // Set context on context element
                final XBLBindings.Scope currentActionScope = actionInterpreter.getActionScope(currentContextInfo);
                contextStack.pushBinding(currentContextInfo, actionInterpreter.getSourceEffectiveId(currentContextInfo), currentActionScope);

                // Evaluate context parameter
                value = XPathCache.evaluateAsExtent(
                        actionInterpreter.getContextStack().getCurrentNodeset(), actionInterpreter.getContextStack().getCurrentPosition(),
                        valueOrSelect, actionInterpreter.getNamespaceMappings(currentContextInfo),
                        contextStack.getCurrentVariables(), XFormsContainingDocument.getFunctionLibrary(),
                        contextStack.getFunctionContext(actionInterpreter.getSourceEffectiveId(currentContextInfo)), null,
                        (LocationData) currentContextInfo.getData());

                contextStack.returnFunctionContext();

                // Restore context
                contextStack.popBinding();
            }

            event.setAttribute(name, value);
        }
    }

    // TODO: use this class as parameter to XFormsAction.execute() and convenience methods; what about XFormsActionInterpreter?
//    public static class ActionContext {
//        public final PropertyContext propertyContext;
//        public final XFormsActionInterpreter actionInterpreter;
//        public final XFormsEventObserver eventObserver;
//        public final Element actionElement;
//        public final String targetId;
//        public final boolean hasOverriddenContext;
//        public final Item overriddenContext;
//
//        public ActionContext(PropertyContext propertyContext, XFormsActionInterpreter actionInterpreter, XFormsEventObserver eventObserver,
//                             Element actionElement, String targetId, boolean hasOverriddenContext, Item overriddenContext) {
//            this.propertyContext = propertyContext;
//            this.actionInterpreter = actionInterpreter;
//            this.eventObserver = eventObserver;
//            this.actionElement = actionElement;
//            this.targetId = targetId;
//            this.hasOverriddenContext = hasOverriddenContext;
//            this.overriddenContext = overriddenContext;
//        }
//    }
}
