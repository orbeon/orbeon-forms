/**
 *  Copyright (C) 2005 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.event.events;

import org.orbeon.oxf.xforms.event.XFormsEvent;
import org.orbeon.oxf.xforms.event.XFormsEventTarget;
import org.orbeon.oxf.xforms.event.XFormsEvents;
import org.orbeon.oxf.xforms.function.xxforms.XXFormsElement;
import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.saxon.om.*;
import org.dom4j.Element;
import org.dom4j.Document;

import java.util.Collections;

/**
 * 4.1.1 The xforms-submit-serialize Event
 *
 * Target: submission / Bubbles: Yes / Cancelable: No / Context Info: A node
 * into which data to be submitted can be placed.
 *
 * The default action for this event is to perform the normal XForms submission
 * serialization if the event context node's content is empty. The content of
 * the event context node is the data sent by the XForms submission.
 */
public class XFormsSubmitSerializeEvent extends XFormsEvent {

    private static final String XXFORMS_BINDING_ATTRIBUTE = XMLUtils.buildExplodedQName(XFormsConstants.XXFORMS_NAMESPACE_URI, "binding");

    private NodeInfo boundNode;
    private Element submissionBodyElement;

    public XFormsSubmitSerializeEvent(final XFormsEventTarget targetObject) {
        super(XFormsEvents.XFORMS_SUBMIT_SERIALIZE, targetObject, true, false);
    }

    public XFormsSubmitSerializeEvent(final XFormsEventTarget targetObject, NodeInfo boundNode) {
        super(XFormsEvents.XFORMS_SUBMIT_SERIALIZE, targetObject, true, false);
        this.boundNode = boundNode;
    }

    public SequenceIterator getAttribute(String name) {
        if ("submission-body".equals(name)) {

            if (submissionBodyElement == null) {
                // Create a document and root element
                final Document document = Dom4jUtils.createDocument();
                submissionBodyElement = Dom4jUtils.createElement("submission-body");
                document.setRootElement(submissionBodyElement);
            }

            // Return document element
            final Item item = XXFormsElement.DOCUMENT_WRAPPER.wrap(submissionBodyElement);
            return new ListIterator(Collections.singletonList(item));
        } else if (XXFORMS_BINDING_ATTRIBUTE.equals(name)) {
            // Return the node to which the submission is bound if any
            if (boundNode != null)
                return new ListIterator(Collections.singletonList(boundNode));
            else
                return EmptyIterator.getInstance();
        } else {
            return super.getAttribute(name);
        }
    }

    public String getSerializedData() {
        return (submissionBodyElement == null) ? "" : submissionBodyElement.getStringValue();
    }
}
