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
package org.orbeon.oxf.xforms.event;

import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.StaticExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.util.IndentedLogger;
import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.XFormsContainingDocument;
import org.orbeon.oxf.xforms.XFormsUtils;
import org.orbeon.oxf.xforms.event.events.XFormsUIEvent;
import org.orbeon.oxf.xforms.xbl.XBLContainer;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.orbeon.saxon.om.EmptyIterator;
import org.orbeon.saxon.om.Item;
import org.orbeon.saxon.om.ListIterator;
import org.orbeon.saxon.om.SequenceIterator;
import org.orbeon.saxon.trans.XPathException;
import org.orbeon.saxon.value.BooleanValue;
import org.orbeon.saxon.value.SequenceExtent;
import org.orbeon.saxon.value.StringValue;

import java.util.*;


/**
 * XFormsEvent represents an XForms event passed to all events and actions.
 */
public abstract class XFormsEvent implements Cloneable {

    private static final String XXFORMS_TYPE_ATTRIBUTE = XMLUtils.buildExplodedQName(XFormsConstants.XXFORMS_NAMESPACE_URI, "type");
    private static final String XXFORMS_TARGET_ATTRIBUTE = XMLUtils.buildExplodedQName(XFormsConstants.XXFORMS_NAMESPACE_URI, "target");
    private static final String XXFORMS_TARGETID_ATTRIBUTE = XMLUtils.buildExplodedQName(XFormsConstants.XXFORMS_NAMESPACE_URI, "targetid");
    private static final String XXFORMS_BUBBLES_ATTRIBUTE = XMLUtils.buildExplodedQName(XFormsConstants.XXFORMS_NAMESPACE_URI, "bubbles");
    private static final String XXFORMS_CANCELABLE_ATTRIBUTE = XMLUtils.buildExplodedQName(XFormsConstants.XXFORMS_NAMESPACE_URI, "cancelable");

    private static final String XXFORMS_REPEAT_INDEXES_ATTRIBUTE = XMLUtils.buildExplodedQName(XFormsConstants.XXFORMS_NAMESPACE_URI, "repeat-indexes");
    private static final String XXFORMS_REPEAT_ANCESTORS_ATTRIBUTE = XMLUtils.buildExplodedQName(XFormsConstants.XXFORMS_NAMESPACE_URI, "repeat-ancestors");
    private static final String XXFORMS_TARGET_PREFIXES_ATTRIBUTE = XMLUtils.buildExplodedQName(XFormsConstants.XXFORMS_NAMESPACE_URI, "target-prefixes");

    // Properties that change as the event propagates
    // TODO
//    private static final String XXFORMS_PHASE_ATTRIBUTE = XMLUtils.buildExplodedQName(XFormsConstants.XXFORMS_NAMESPACE_URI, "phase");
//    private static final String XXFORMS_DEFAULT_PREVENTED_ATTRIBUTE = XMLUtils.buildExplodedQName(XFormsConstants.XXFORMS_NAMESPACE_URI, "default-prevented");
//    private static final String XXFORMS_CURRENT_TARGET_ATTRIBUTE = XMLUtils.buildExplodedQName(XFormsConstants.XXFORMS_NAMESPACE_URI, "current-target");// same as observer?
//    private static final String XXFORMS_OBSERVER_ATTRIBUTE = XMLUtils.buildExplodedQName(XFormsConstants.XXFORMS_NAMESPACE_URI, "observer");
//    See DOM 3 events for phase:
//    const unsigned short      CAPTURING_PHASE                = 1;
//    const unsigned short      AT_TARGET                      = 2;
//    const unsigned short      BUBBLING_PHASE                 = 3;

    private XFormsEvent originalEvent;  // original event (for retargeted event)

    private final XBLContainer targetXBLContainer;
    private final String eventName;
    private XFormsEventTarget targetObject;
    private final boolean bubbles;
    private final boolean cancelable;

    private Map<String, SequenceExtent> customAttributes;

    // TODO: Assign for debugging
    private LocationData locationData;

    protected XFormsEvent(XFormsContainingDocument containingDocument, String eventName, XFormsEventTarget targetObject, boolean bubbles, boolean cancelable) {
        this.targetXBLContainer = targetObject.getXBLContainer(containingDocument);
        this.eventName = eventName;
        this.targetObject = targetObject;
        this.bubbles = bubbles;
        this.cancelable = cancelable;
    }

    public XBLContainer getTargetXBLContainer() {
        return targetXBLContainer;
    }
    
    protected XFormsContainingDocument getContainingDocument() {
        return targetXBLContainer.getContainingDocument();
    }

    protected IndentedLogger getIndentedLogger() {
        return getContainingDocument().getIndentedLogger(XFormsEvents.LOGGING_CATEGORY);
    }

    public String getEventName() {
        return eventName;
    }

    public XFormsEventTarget getTargetObject() {
        return targetObject;
    }

    public boolean isBubbles() {
        return bubbles;
    }

    public boolean isCancelable() {
        return cancelable;
    }

    public LocationData getLocationData() {
        return locationData;
    }

    public void setAttribute(String name, SequenceExtent value) {
        if (customAttributes == null)
            customAttributes = new HashMap<String, SequenceExtent>();
        customAttributes.put(name, value);
    }

    public SequenceIterator getAttribute(String name) {
        if ("target".equals(name) || XXFORMS_TARGET_ATTRIBUTE.equals(name) || XXFORMS_TARGETID_ATTRIBUTE.equals(name)) {// first is legacy name
            // Return the target static id

            if ("target".equals(name)) {
                getIndentedLogger().logWarning("", "event('target') is deprecated. Use event('xxforms:targetid') instead.");
            }

            return new ListIterator(Collections.singletonList(new StringValue(targetObject.getId())));
        } else if ("event".equals(name) || XXFORMS_TYPE_ATTRIBUTE.equals(name)) {// first is legacy name
            // Return the event type

            if ("event".equals(name)) {
                getIndentedLogger().logWarning("", "event('event') is deprecated. Use event('xxforms:type') instead.");
            }

            return new ListIterator(Collections.singletonList(new StringValue(eventName)));
        } else if (XXFORMS_BUBBLES_ATTRIBUTE.equals(name)) {
            // Return whether the event bubbles
            return new ListIterator(Collections.singletonList(BooleanValue.get(bubbles)));
        } else if (XXFORMS_CANCELABLE_ATTRIBUTE.equals(name)) {
            // Return whether the event is cancelable
            return new ListIterator(Collections.singletonList(BooleanValue.get(cancelable)));
        } else if (customAttributes != null && customAttributes.get(name) != null) {
            // Return custom attribute if found
            return (customAttributes.get(name)).iterate(null); // NOTE: With Saxon 8, the param is not used, and Saxon 9 has value.iterate()
        } else if (XFormsConstants.NO_INDEX_ADJUSTMENT.equals(name)) {
            // NOP (this is related to a temporary offline performance hack)
            return EmptyIterator.getInstance();
        } else if ("repeat-indexes".equals(name) || XXFORMS_REPEAT_INDEXES_ATTRIBUTE.equals(name)) {

            if ("repeat-indexes".equals(name)) {
                getIndentedLogger().logWarning("", "event('repeat-indexes') is deprecated. Use event('xxforms:repeat-indexes') instead.");
            }

            final String effectiveTargetId = targetObject.getEffectiveId();
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
        } else if (XXFORMS_REPEAT_ANCESTORS_ATTRIBUTE.equals(name)) {
            // Return the target's ancestor repeat static ids if any
            final String effectiveTargetId = targetObject.getEffectiveId();
            if (XFormsUtils.hasEffectiveIdSuffix( effectiveTargetId)) {
                // There is a suffix so compute
                final List<String> ancestorRepeats
                        = getContainingDocument().getStaticState().getAncestorRepeats(XFormsUtils.getPrefixedId(targetObject.getEffectiveId()), null);

                if (ancestorRepeats.size() > 0) {
                    // At least one ancestor repeat
                    final List<StringValue> tokens = new ArrayList<StringValue>(ancestorRepeats.size());
                    for (final String currentRepeat: ancestorRepeats) {
                        tokens.add(new StringValue(XFormsUtils.getStaticIdFromId(currentRepeat)));
                    }
                    return new ListIterator(tokens);
                } else {
                    // No ancestor repeats
                    return EmptyIterator.getInstance();
                }
            } else {
                // No suffix
                return EmptyIterator.getInstance();
            }
        } else if (XXFORMS_TARGET_PREFIXES_ATTRIBUTE.equals(name)) {
            // Return the target's id prefixes if any
            final String effectiveTargetId = targetObject.getEffectiveId();
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
            // "If the event context information does not contain the property indicated by the string argument, then an
            // empty node-set is returned."
            getIndentedLogger().logWarning("", "Unsupported event context information for event('" + name + "').");
            return EmptyIterator.getInstance();
        }
    }

    protected void setAttributeAsString(String name, String value) {
        setAttribute(name, new SequenceExtent(new Item[] { new StringValue(value) } ));
    }

    protected String getAttributeAsString(String name) {
        try {
            final Item item = getAttribute(name).next();
            return (item != null) ? item.getStringValue() : null;
        } catch (XPathException e) {
            throw new OXFException(e);
        }
    }

//    protected void setAttributeAsInteger(String name, int value) {
//        setAttribute(name, new SequenceExtent(new Item[] { new IntegerValue(value) } ));
//    }

    /**
     * Attempts to get the current pipeline context using the static context.
     *
     * @return  PipelineContext, null if not found
     */
    protected PipelineContext getPipelineContext() {
        return StaticExternalContext.getStaticContext().getPipelineContext();
    }

    public XFormsEvent retarget(XFormsEventTarget newTargetObject) {
        final XFormsEvent newEvent;
        try {
            newEvent = (XFormsUIEvent) this.clone();
            newEvent.targetObject = newTargetObject;
            newEvent.originalEvent = this;
        } catch (CloneNotSupportedException e) {
            throw new OXFException(e);// should not happen because we are clonable
        }

        return newEvent;
    }

    public XFormsEvent getOriginalEvent() {
        return originalEvent != null ? originalEvent : this;
    }

    public Object clone() throws CloneNotSupportedException {
        // Default implementation is good enough
        return super.clone();
    }
}
