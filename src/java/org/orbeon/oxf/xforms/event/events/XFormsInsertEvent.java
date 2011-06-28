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

import org.orbeon.oxf.xforms.XFormsContainingDocument;
import org.orbeon.oxf.xforms.event.XFormsEvent;
import org.orbeon.oxf.xforms.event.XFormsEventTarget;
import org.orbeon.oxf.xforms.event.XFormsEvents;
import org.orbeon.saxon.om.*;
import org.orbeon.saxon.value.StringValue;

import java.util.List;


/**
 * 4.4.5 The xforms-insert and xforms-delete Events
 *
 * Target: instance / Bubbles: Yes / Cancelable: No / Context Info: Path expression used for insert/delete (xsd:string).
 * The default action for these events results in the following: None; notification event only.
 */
public class XFormsInsertEvent extends XFormsEvent {

    private List<Item> insertedNodeInfos;
    private List originItems;
    private NodeInfo insertLocationNodeInfo;
    private String position; // "before | after | into" relative to the insert location node ("into" is an Orbeon extension)

    public XFormsInsertEvent(XFormsContainingDocument containingDocument, XFormsEventTarget targetObject) {
        super(containingDocument, XFormsEvents.XFORMS_INSERT, targetObject, true, false);
    }

    public XFormsInsertEvent(XFormsContainingDocument containingDocument, XFormsEventTarget targetObject,
                             List<Item> insertedNodes, List originItems,
                             NodeInfo insertLocationNodeInfo, String position) {
        super(containingDocument, XFormsEvents.XFORMS_INSERT, targetObject, true, false);
        
        this.insertedNodeInfos = insertedNodes;
        this.originItems = originItems;
        this.insertLocationNodeInfo = insertLocationNodeInfo;
        this.position = position;
    }

    public SequenceIterator getAttribute(String name) {
        if ("inserted-nodes".equals(name)) {
            // "The instance data nodes inserted."
            return new ListIterator(insertedNodeInfos);
        } else if ("origin-nodes".equals(name)) {
            // "The instance data nodes referenced by the insert action's origin attribute if present, or the empty nodeset if not present."
            return (originItems == null) ? EmptyIterator.getInstance() : new ListIterator(originItems);
        } else if ("insert-location-node".equals(name)) {
            // "The insert location node as defined by the insert action."
            return SingletonIterator.makeIterator(insertLocationNodeInfo);
        } else if ("position".equals(name)) {
            // "The insert position, before or after."
            return SingletonIterator.makeIterator(new StringValue(position));
        } else {
            return super.getAttribute(name);
        }
    }

    public List<Item> getInsertedNodeInfos() {
        return insertedNodeInfos;
    }

    public List getOriginItems() {
        return originItems;
    }

    public NodeInfo getInsertLocationNodeInfo() {
        return insertLocationNodeInfo;
    }

    public String getPosition() {
        return position;
    }
}
