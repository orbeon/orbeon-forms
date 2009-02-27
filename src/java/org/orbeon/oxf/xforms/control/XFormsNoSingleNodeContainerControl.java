/**
 *  Copyright (C) 2008 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.control;

import org.dom4j.Element;
import org.orbeon.oxf.xforms.XFormsContainer;
import org.orbeon.oxf.pipeline.api.PipelineContext;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public abstract class XFormsNoSingleNodeContainerControl extends XFormsControl implements XFormsContainerControl {

    private List children;

    public XFormsNoSingleNodeContainerControl(XFormsContainer container, XFormsControl parent, Element element, String name, String effectiveId) {
        super(container, parent, element, name, effectiveId);
    }

    public void addChild(XFormsControl XFormsControl) {
        if (children == null)
            children = new ArrayList();
        children.add(XFormsControl);
    }

    public List getChildren() {
        return children;
    }

    public int getSize() {
        return (children != null) ? children.size() : 0;
    }

    protected void setChildren(List children) {
        this.children = children;
    }

    /**
     * Update this control's effective id and its descendants based on the parent's effective id.
     */
    public void updateEffectiveId() {
        super.updateEffectiveId();
        final List children = getChildren();
        if (children != null && children.size() > 0) {
            for (Iterator i = children.iterator(); i.hasNext();) {
                final XFormsControl currentControl = (XFormsControl) i.next();
                currentControl.updateEffectiveId();
            }
        }
    }

    public Object clone() {

        // Clone this
        final XFormsNoSingleNodeContainerControl cloned = (XFormsNoSingleNodeContainerControl) super.clone();

        // Clone children if any
        if (children != null) {
            cloned.children = new ArrayList(children.size());
            for (Iterator i = children.iterator(); i.hasNext();) {
                final XFormsControl currentChildControl = (XFormsControl) i.next();
                final XFormsControl currentChildClone = (XFormsControl) currentChildControl.clone();
                currentChildClone.setParent(cloned);
                cloned.children.add(currentChildClone);
            }
        }

        return cloned;
    }

    public void childrenAdded() {
        // For subclasses
    }

    public void iterationRemoved(PipelineContext pipelineContext) {
        final List children = getChildren();
        if (children != null && children.size() > 0) {
            for (Iterator i = children.iterator(); i.hasNext();) {
                final XFormsControl currentControl = (XFormsControl) i.next();
                currentControl.iterationRemoved(pipelineContext);
            }
        }
    }
}
