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
package org.orbeon.oxf.xforms.control.controls;

import org.dom4j.QName;
import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.XFormsUtils;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.control.XFormsPseudoControl;
import org.orbeon.oxf.xforms.control.XFormsSingleNodeContainerControl;
import org.orbeon.oxf.xforms.event.XFormsEvents;
import org.orbeon.oxf.xforms.xbl.XBLContainer;
import org.orbeon.oxf.xml.ContentHandlerHelper;
import org.xml.sax.helpers.AttributesImpl;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Represents xforms:repeat iteration information.
 *
 * This is not really a control, but an abstraction for xforms:repeat branches.
 *
 * TODO: Use inheritance to make this a single-node control that doesn't hold a value.
 */
public class XFormsRepeatIterationControl extends XFormsSingleNodeContainerControl implements XFormsPseudoControl {
    private int iterationIndex;
    public XFormsRepeatIterationControl(XBLContainer container, XFormsRepeatControl parent, int iterationIndex) {
        // NOTE: Associate this control with the repeat element. This is so that even targets get a proper id
        // NOTE: Effective id of an iteration is parentRepeatId·iteration
        super(container, parent, parent.getControlElement(), "xxforms-repeat-iteration", XFormsUtils.getIterationEffectiveId(parent.getEffectiveId(), iterationIndex));
        this.iterationIndex = iterationIndex;
    }

    public int getIterationIndex() {
        return iterationIndex;
    }

    /**
     * Set a new iteration index. This will cause the nested effective ids to update.
     *
     * This is used to "shuffle" around repeat iterations when repeat nodesets change.
     *
     * @param iterationIndex    new iteration index
     */
    public void setIterationIndex(int iterationIndex) {
        if (this.iterationIndex != iterationIndex) {
            this.iterationIndex = iterationIndex;
            updateEffectiveId();
        }
    }

    @Override
    public boolean supportsRefreshEvents() {
        // Des not support refresh events for now (could make sense though)
        return false;
    }

    @Override
    public String getLabel() {
        // Don't bother letting superclass handle this
        return null;
    }

    @Override
    public String getHelp() {
        // Don't bother letting superclass handle this
        return null;
    }

    @Override
    public String getHint() {
        // Don't bother letting superclass handle this
        return null;
    }

    @Override
    public String getAlert() {
        // Don't bother letting superclass handle this
        return null;
    }

    @Override
    public boolean isStaticReadonly() {
        return false;
    }

    @Override
    public QName getType() {
        return null;
    }

    /**
     * Update this control's effective id and its descendants based on the parent's effective id.
     */
    @Override
    public void updateEffectiveId() {

        // Update this iteration's effective id
        setEffectiveId(XFormsUtils.getIterationEffectiveId(getParent().getEffectiveId(), iterationIndex));

        // Update children
        final List<XFormsControl> children = getChildren();
        if (children != null && children.size() > 0) {
            for (XFormsControl currentControl: children) {
                currentControl.updateEffectiveId();
            }
        }
    }

    private static final Set<String> ALLOWED_EXTERNAL_EVENTS = new HashSet<String>();
    static {
        ALLOWED_EXTERNAL_EVENTS.add(XFormsEvents.DOM_FOCUS_IN);
    }

    @Override
    protected Set<String> getAllowedExternalEvents() {
        return ALLOWED_EXTERNAL_EVENTS;
    }

    @Override
    public boolean equalsExternal(XFormsControl other) {

        if (other == null || !(other instanceof XFormsRepeatIterationControl))
            return false;

        if (this == other)
            return true;

        final XFormsRepeatIterationControl otherRepeatIterationControl = (XFormsRepeatIterationControl) other;

        // Ad-hoc comparison, because we basically only care about relevance changes
        return !mustSendIterationUpdate(otherRepeatIterationControl);
    }

    private boolean mustSendIterationUpdate(XFormsRepeatIterationControl other) {

        // NOTE: We only care about relevance changes. We should care about moving iterations around, but that's not
        // handled that way yet!

        // NOTE: We output if we are NOT relevant as the client must mark non-relevant elements. Ideally, we should not
        // have non-relevant iterations actually present on the client.
        return (other == null && !isRelevant()
                //|| XFormsSingleNodeControl.isRelevant(xformsSingleNodeControl1) != XFormsSingleNodeControl.isRelevant(xformsSingleNodeControl2)) {
                || other != null && other.isRelevant() != isRelevant());//TODO: not sure why the above alternative fails tests. Which is more correct?
    }

    @Override
    public void outputAjaxDiff(ContentHandlerHelper ch, XFormsControl other, AttributesImpl attributesImpl, boolean isNewlyVisibleSubtree) {

        assert attributesImpl.getLength() == 0;

        final XFormsRepeatIterationControl repeatIterationControl1 = (XFormsRepeatIterationControl) other;
        if (mustSendIterationUpdate(repeatIterationControl1)) {
            // Use the effective id of the parent repeat
            attributesImpl.addAttribute("", "id", "id", ContentHandlerHelper.CDATA, getParent().getEffectiveId());

            // Relevance
            attributesImpl.addAttribute("", XFormsConstants.RELEVANT_ATTRIBUTE_NAME,
                    XFormsConstants.RELEVANT_ATTRIBUTE_NAME,
                    ContentHandlerHelper.CDATA, Boolean.toString(isRelevant()));

            attributesImpl.addAttribute("", "iteration", "iteration", ContentHandlerHelper.CDATA, Integer.toString(getIterationIndex()));
            ch.element("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "repeat-iteration", attributesImpl);
        }

        // NOTE: in this case, don't do the regular Ajax output (maybe in the future we should to be more consistent?)
    }

    @Override
    public boolean supportFullAjaxUpdates() {
        return false;
    }
}
