/**
 * Copyright (C) 2009 Orbeon, Inc.
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

import org.orbeon.oxf.util.IndentedLogger;
import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.XFormsContainingDocument;
import org.orbeon.oxf.xforms.XFormsUtils;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.control.XFormsSingleNodeControl;
import org.orbeon.oxf.xforms.event.XFormsEvent;
import org.orbeon.oxf.xforms.event.XFormsEventTarget;
import org.orbeon.oxf.xforms.event.XFormsEvents;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.saxon.om.EmptyIterator;
import org.orbeon.saxon.om.ListIterator;
import org.orbeon.saxon.om.SequenceIterator;
import org.orbeon.saxon.value.StringValue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Base class for UI events.
 */
public abstract class XFormsUIEvent extends XFormsEvent {

    private static final String XXFORMS_BINDING_ATTRIBUTE = XMLUtils.buildExplodedQName(XFormsConstants.XXFORMS_NAMESPACE_URI, "binding");
    private static final String XXFORMS_ALERT_ATTRIBUTE = XMLUtils.buildExplodedQName(XFormsConstants.XXFORMS_NAMESPACE_URI, "alert");
    private static final String XXFORMS_LABEL_ATTRIBUTE = XMLUtils.buildExplodedQName(XFormsConstants.XXFORMS_NAMESPACE_URI, "label");
    private static final String XXFORMS_HINT_ATTRIBUTE = XMLUtils.buildExplodedQName(XFormsConstants.XXFORMS_NAMESPACE_URI, "hint");
    private static final String XXFORMS_HELP_ATTRIBUTE = XMLUtils.buildExplodedQName(XFormsConstants.XXFORMS_NAMESPACE_URI, "help");
    private static final String XXFORMS_REPEAT_INDEXES_ATTRIBUTE = XMLUtils.buildExplodedQName(XFormsConstants.XXFORMS_NAMESPACE_URI, "repeat-indexes");
    private static final String XXFORMS_TARGET_PREFIXES_ATTRIBUTE = XMLUtils.buildExplodedQName(XFormsConstants.XXFORMS_NAMESPACE_URI, "target-prefixes");

    private XFormsControl targetXFormsControl;

    public XFormsUIEvent(XFormsContainingDocument containingDocument, String eventName, XFormsControl targetObject) {
        super(containingDocument, eventName, targetObject, true, false);
        this.targetXFormsControl = targetObject;
    }

    protected XFormsUIEvent(XFormsContainingDocument containingDocument, String eventName, XFormsControl targetObject, boolean bubbles, boolean cancelable) {
        super(containingDocument, eventName, targetObject, bubbles, cancelable);
        this.targetXFormsControl = targetObject;
    }

    public SequenceIterator getAttribute(String name) {
        if ("target-ref".equals(name) || XXFORMS_BINDING_ATTRIBUTE.equals(name)) {

            if ("target-ref".equals(name)) {
                getIndentedLogger().logWarning("", "event('target-ref') is deprecated. Use event('xxforms:binding') instead.");
            }

            // Return the node to which the control is bound
            if (targetXFormsControl instanceof XFormsSingleNodeControl) {
                return new ListIterator(Collections.singletonList(((XFormsSingleNodeControl) targetXFormsControl).getBoundNode()));
            } else {
                return EmptyIterator.getInstance();
            }
        } else if ("alert".equals(name) || XXFORMS_ALERT_ATTRIBUTE.equals(name)) {

            if ("alert".equals(name)) {
                getIndentedLogger().logWarning("", "event('alert') is deprecated. Use event('xxforms:alert') instead.");
            }

            final String alert = targetXFormsControl.getAlert(getPipelineContext());
            if (alert != null)
                return new ListIterator(Collections.singletonList(new StringValue(alert)));
            else
                return EmptyIterator.getInstance();
        } else if ("label".equals(name) || XXFORMS_LABEL_ATTRIBUTE.equals(name)) {

            if ("label".equals(name)) {
                getIndentedLogger().logWarning("", "event('label') is deprecated. Use event('xxforms:label') instead.");
            }

            final String label = targetXFormsControl.getLabel(getPipelineContext());
            if (label != null)
                return new ListIterator(Collections.singletonList(new StringValue(label)));
            else
                return EmptyIterator.getInstance();
        } else if ("hint".equals(name) || XXFORMS_HINT_ATTRIBUTE.equals(name)) {

            if ("hint".equals(name)) {
                getIndentedLogger().logWarning("", "event('hint') is deprecated. Use event('xxforms:hint') instead.");
            }

            final String hint = targetXFormsControl.getHint(getPipelineContext());
            if (hint != null)
                return new ListIterator(Collections.singletonList(new StringValue(hint)));
            else
                return EmptyIterator.getInstance();
        } else if ("help".equals(name) || XXFORMS_HELP_ATTRIBUTE.equals(name)) {

            if ("help".equals(name)) {
                getIndentedLogger().logWarning("", "event('help') is deprecated. Use event('xxforms:help') instead.");
            }

            final String help = targetXFormsControl.getHelp(getPipelineContext());
            if (help != null)
                return new ListIterator(Collections.singletonList(new StringValue(help)));
            else
                return EmptyIterator.getInstance();
        } else if ("repeat-indexes".equals(name) || XXFORMS_REPEAT_INDEXES_ATTRIBUTE.equals(name)) {

            if ("repeat-indexes".equals(name)) {
                getIndentedLogger().logWarning("", "event('repeat-indexes') is deprecated. Use event('xxforms:repeat-indexes') instead.");
            }

            final String effectiveTargetId = targetXFormsControl.getEffectiveId();
            final Integer[] parts = XFormsUtils.getEffectiveIdSuffixParts(effectiveTargetId);

            if (parts.length > 0) {
                final List<StringValue> tokens = new ArrayList<StringValue>(parts.length);
                for (final Integer currentIndex: parts) {
                    tokens.add(new StringValue(currentIndex.toString()));
                }
                return new ListIterator(tokens);
            } else {
                return EmptyIterator.getInstance();
            }
        } else if (XXFORMS_TARGET_PREFIXES_ATTRIBUTE.equals(name)) {

            final String effectiveTargetId = targetXFormsControl.getEffectiveId();
            final String[] parts = XFormsUtils.getEffectiveIdPrefixParts(effectiveTargetId);

            if (parts.length > 0) {
                final List<StringValue> tokens = new ArrayList<StringValue>(parts.length);
                for (final String currentPart: parts) {
                    tokens.add(new StringValue(currentPart));
                }
                return new ListIterator(tokens);
            } else {
                return EmptyIterator.getInstance();
            }
        } else {
            return super.getAttribute(name);
        }
    }

    public XFormsEvent retarget(XFormsEventTarget newTargetObject) {
        final XFormsUIEvent newEvent = (XFormsUIEvent) super.retarget(newTargetObject);
        newEvent.targetXFormsControl = (XFormsControl) newTargetObject;
        return newEvent;
    }

    private IndentedLogger getIndentedLogger() {
        return getContainingDocument().getIndentedLogger(XFormsEvents.logger);
    }

    // TODO: Need this at XFormsEvent level
    private XFormsContainingDocument getContainingDocument() {
        final XFormsEventTarget targetObject = getTargetObject();
        if (targetObject instanceof XFormsControl)
            return ((XFormsControl) targetObject).getXBLContainer().getContainingDocument();
        else
            return null;
    }
}
