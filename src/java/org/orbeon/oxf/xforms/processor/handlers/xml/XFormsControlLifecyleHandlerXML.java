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
package org.orbeon.oxf.xforms.processor.handlers.xml;

import org.orbeon.oxf.xforms.StaticStateGlobalOps;
import org.orbeon.oxf.xforms.analysis.controls.LHHAAnalysis;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.processor.handlers.XFormsControlLifecycleHandlerDelegate;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

/**
 * This class is a helper base class which handles the lifecycle of producing markup for a control. The following
 * phases are handled:
 *
 * o Give the handler a chance to do some prep work: prepareHandler()
 * o Check whether the control wants any output at all: isMustOutputControl()
 * o Output label, control, hint, help, and alert in order specified by properties
 *
 * Outputting the control is split into two parts: handleControlStart() and handleControlEnd().
 */
public class XFormsControlLifecyleHandlerXML extends XFormsBaseHandlerXML {

	private XFormsControlLifecycleHandlerDelegate xFormsControlLifecycleHandlerDelegate;
	private Attributes attributes;

    protected XFormsControlLifecyleHandlerXML(boolean repeating) {
        super(repeating, false);
    }

    protected XFormsControlLifecyleHandlerXML(boolean repeating, boolean forwarding) {
        super(repeating, forwarding);
    }

    
	protected boolean isTemplate() {
		return xFormsControlLifecycleHandlerDelegate.isTemplate();
	}

	protected String getPrefixedId() {
		return xFormsControlLifecycleHandlerDelegate.getPrefixedId();
	}

	protected String getEffectiveId() {
		return xFormsControlLifecycleHandlerDelegate.getEffectiveId();
	}

	protected XFormsControl getControl() {
		return xFormsControlLifecycleHandlerDelegate.getControl();
	}
	
	protected String getStaticId() {
		return xFormsControlLifecycleHandlerDelegate.getStaticId();
	}

    @Override
    public void init(String uri, String localname, String qName, Attributes attributes, Object matched) throws SAXException {
        super.init(uri, localname, qName, attributes, matched);
        this.attributes = new AttributesImpl(attributes);
        this.xFormsControlLifecycleHandlerDelegate = new XFormsControlLifecycleHandlerDelegate(handlerContext, containingDocument, attributes);
    }

    @Override
    public final void start(String uri, String localname, String qName, Attributes attributes) throws SAXException {
        if (isMustOutputControl(getControl())) {

            handleControlStart(uri, localname, qName, attributes, getStaticId(), getEffectiveId(), getControl());
            
            // xforms:label
            if (hasLocalLabel())
                handleLabel();
            
            // xforms:help
            if (hasLocalHelp())
                handleHelp();

            // xforms:hint
            if (hasLocalHint())
                handleHint();
            
            // xforms:alert
            if (hasLocalAlert())
                handleAlert();
        }
    }

    @Override
    public final void end(String uri, String localname, String qName) throws SAXException {
        if (isMustOutputControl(getControl())) {
        	handleControlEnd(uri, localname, qName, attributes, getStaticId(), getEffectiveId(), getControl());
        }
    }

    private boolean hasLocalLabel() {
        final StaticStateGlobalOps globalOps = containingDocument.getStaticOps();
        final LHHAAnalysis analysis = globalOps.getLabel(getPrefixedId());
        return analysis != null && analysis.isLocal();
    }

    private boolean hasLocalHint() {
        final StaticStateGlobalOps globalOps = containingDocument.getStaticOps();
        final LHHAAnalysis analysis = globalOps.getHint(getPrefixedId());
        return analysis != null && analysis.isLocal();
    }

    private boolean hasLocalHelp() {
        final StaticStateGlobalOps globalOps = containingDocument.getStaticOps();
        final LHHAAnalysis analysis = globalOps.getHelp(getPrefixedId());
        return analysis != null && analysis.isLocal();
    }

    private boolean hasLocalAlert() {
        final StaticStateGlobalOps globalOps = containingDocument.getStaticOps();
        final LHHAAnalysis analysis = globalOps.getAlert(getPrefixedId());
        return analysis != null && analysis.isLocal();
    }

    protected boolean isMustOutputControl(XFormsControl control) {
        // May be overridden by subclasses
        return true;
    }

    protected void handleLabel() throws SAXException {
        // May be overridden by subclasses
        handleLabelHintHelpAlert(getEffectiveId(), getForEffectiveId(), LHHAC.LABEL, getControl(), isTemplate(), !handlerContext.isSpanHTMLLayout());
    }

    protected void handleAlert() throws SAXException {
        // May be overridden by subclasses
        handleLabelHintHelpAlert(getEffectiveId(), getForEffectiveId(), LHHAC.ALERT, getControl(), isTemplate(), !handlerContext.isSpanHTMLLayout());
    }

    protected void handleHint() throws SAXException {
        // May be overridden by subclasses
        handleLabelHintHelpAlert(getEffectiveId(), getForEffectiveId(), LHHAC.HINT, getControl(), isTemplate(), !handlerContext.isSpanHTMLLayout());
    }

    protected void handleHelp() throws SAXException {
        // May be overridden by subclasses
        handleLabelHintHelpAlert(getEffectiveId(), getForEffectiveId(), LHHAC.HELP, getControl(), isTemplate(), !handlerContext.isSpanHTMLLayout());
    }

    protected void handleControlStart(String uri, String localname, String qName, Attributes attributes, String staticId, String effectiveId, XFormsControl control) throws SAXException {
        final ContentHandler contentHandler = handlerContext.getController().getOutput();
        
        reusableAttributes.clear();
        reusableAttributes.setAttributes(attributes);
		updateID(reusableAttributes, effectiveId, LHHAC.CONTROL);
		handleMIPAttributes(reusableAttributes, getPrefixedId(), control);
        handleValueAttribute(reusableAttributes, getPrefixedId(), control);
        handleExtraAttributesForControlStart(reusableAttributes, effectiveId, control);
        
        
        contentHandler.startElement(uri, localname, qName, reusableAttributes);
    }

	protected void handleExtraAttributesForControlStart(AttributesImpl reusableAttributes, String prefixedId, XFormsControl control) { }

	protected void handleControlEnd(String uri, String localname, String qName, Attributes attributes, String staticId, String effectiveId, XFormsControl control) throws SAXException {
    	 final ContentHandler contentHandler = handlerContext.getController().getOutput();
    	 
    	 contentHandler.endElement(uri, localname, qName);
    }

    /**
     * Return the effective id of the element to which label/@for, etc. must point to.
     *
     * @return                      @for effective id
     */
    public String getForEffectiveId() {
        return getEffectiveId();
    }

}
