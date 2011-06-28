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
package org.orbeon.oxf.xforms.event.events;

import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.XFormsContainingDocument;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.control.XFormsSingleNodeControl;
import org.orbeon.oxf.xforms.event.XFormsEvent;
import org.orbeon.oxf.xforms.event.XFormsEventTarget;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.saxon.om.EmptyIterator;
import org.orbeon.saxon.om.SequenceIterator;
import org.orbeon.saxon.om.SingletonIterator;
import org.orbeon.saxon.value.Int64Value;
import org.orbeon.saxon.value.StringValue;

/**
 * Base class for UI events.
 */
public abstract class XFormsUIEvent extends XFormsEvent {

    private static final String XXFORMS_BINDING_ATTRIBUTE = XMLUtils.buildExplodedQName(XFormsConstants.XXFORMS_NAMESPACE_URI, "binding");
    private static final String XXFORMS_ALERT_ATTRIBUTE = XMLUtils.buildExplodedQName(XFormsConstants.XXFORMS_NAMESPACE_URI, "alert");
    private static final String XXFORMS_LABEL_ATTRIBUTE = XMLUtils.buildExplodedQName(XFormsConstants.XXFORMS_NAMESPACE_URI, "label");
    private static final String XXFORMS_HINT_ATTRIBUTE = XMLUtils.buildExplodedQName(XFormsConstants.XXFORMS_NAMESPACE_URI, "hint");
    private static final String XXFORMS_HELP_ATTRIBUTE = XMLUtils.buildExplodedQName(XFormsConstants.XXFORMS_NAMESPACE_URI, "help");
    private static final String XXFORMS_POSITION_ATTRIBUTE = XMLUtils.buildExplodedQName(XFormsConstants.XXFORMS_NAMESPACE_URI, "control-position");

    private XFormsControl targetControl;

    public XFormsUIEvent(XFormsContainingDocument containingDocument, String eventName, XFormsControl targetControl) {
        super(containingDocument, eventName, targetControl, true, false);
        this.targetControl = targetControl;
    }

    protected XFormsUIEvent(XFormsContainingDocument containingDocument, String eventName, XFormsControl targetControl, boolean bubbles, boolean cancelable) {
        super(containingDocument, eventName, targetControl, bubbles, cancelable);
        this.targetControl = targetControl;
    }

    public SequenceIterator getAttribute(String name) {
        if ("target-ref".equals(name) || XXFORMS_BINDING_ATTRIBUTE.equals(name)) {

            if ("target-ref".equals(name)) {
                getIndentedLogger().logWarning("", "event('target-ref') is deprecated. Use event('xxforms:binding') instead.");
            }

            // Return the node to which the control is bound
            if (targetControl instanceof XFormsSingleNodeControl) {
                return SingletonIterator.makeIterator(((XFormsSingleNodeControl) targetControl).getBoundItem());
            } else {
                return EmptyIterator.getInstance();
            }
        } else if ("alert".equals(name) || XXFORMS_ALERT_ATTRIBUTE.equals(name)) {

            if ("alert".equals(name)) {
                getIndentedLogger().logWarning("", "event('alert') is deprecated. Use event('xxforms:alert') instead.");
            }

            final String alert = targetControl.getAlert();
            if (alert != null)
                return SingletonIterator.makeIterator(new StringValue(alert));
            else
                return EmptyIterator.getInstance();
        } else if ("label".equals(name) || XXFORMS_LABEL_ATTRIBUTE.equals(name)) {

            if ("label".equals(name)) {
                getIndentedLogger().logWarning("", "event('label') is deprecated. Use event('xxforms:label') instead.");
            }

            final String label = targetControl.getLabel();
            if (label != null)
                return SingletonIterator.makeIterator(new StringValue(label));
            else
                return EmptyIterator.getInstance();
        } else if ("hint".equals(name) || XXFORMS_HINT_ATTRIBUTE.equals(name)) {

            if ("hint".equals(name)) {
                getIndentedLogger().logWarning("", "event('hint') is deprecated. Use event('xxforms:hint') instead.");
            }

            final String hint = targetControl.getHint();
            if (hint != null)
                return SingletonIterator.makeIterator(new StringValue(hint));
            else
                return EmptyIterator.getInstance();
        } else if ("help".equals(name) || XXFORMS_HELP_ATTRIBUTE.equals(name)) {

            if ("help".equals(name)) {
                getIndentedLogger().logWarning("", "event('help') is deprecated. Use event('xxforms:help') instead.");
            }

            final String help = targetControl.getHelp();
            if (help != null)
                return SingletonIterator.makeIterator(new StringValue(help));
            else
                return EmptyIterator.getInstance();
        } else if (XXFORMS_POSITION_ATTRIBUTE.equals(name)) {
            // Return the control's static position in the document
            final int controlStaticPosition = targetControl.getXBLContainer().getPartAnalysis().getControlPosition(targetControl.getPrefixedId());
            if (controlStaticPosition >= 0)
                return SingletonIterator.makeIterator(new Int64Value(controlStaticPosition));
            else
                return EmptyIterator.getInstance();
        } else {
            return super.getAttribute(name);
        }
    }

    public XFormsEvent retarget(XFormsEventTarget newTargetObject) {
        final XFormsUIEvent newEvent = (XFormsUIEvent) super.retarget(newTargetObject);
        newEvent.targetControl = (XFormsControl) newTargetObject;
        return newEvent;
    }
}
