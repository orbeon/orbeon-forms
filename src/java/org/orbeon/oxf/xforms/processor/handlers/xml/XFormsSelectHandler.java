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

import org.orbeon.oxf.pipeline.api.XMLReceiver;
import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.control.controls.XFormsSelect1Control;
import org.orbeon.oxf.xforms.itemset.Item;
import org.orbeon.oxf.xforms.itemset.Itemset;
import org.orbeon.oxf.xforms.itemset.ItemsetListener;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * Handle xforms:select and xforms:select1.
 */
public class XFormsSelectHandler extends XFormsControlLifecyleHandlerXML {
	
	public XFormsSelectHandler() {
		super(false);
	}
	
	@Override
	protected void handleControlEnd(String uri, String localname, String qName, Attributes attributes, String staticId, String effectiveId, XFormsControl control) throws SAXException {
		final XFormsSelect1Control select1Control = (XFormsSelect1Control) control;
		final XMLReceiver xmlReceiver = handlerContext.getController().getOutput();
		
    	final Itemset itemset = XFormsSelect1Control.getInitialItemset(containingDocument, select1Control, getPrefixedId());
    	if (itemset != null) { // can be null if the control is non-relevant
    		reusableAttributes.clear();
			xmlReceiver.startElement(XFormsConstants.XXFORMS_NAMESPACE_URI, "items", XFormsConstants.XXFORMS_PREFIX + ":items", reusableAttributes);
			
    		itemset.visit(xmlReceiver, new ItemsetListener() {

				public void startLevel(ContentHandler contentHandler, Item item) throws SAXException {
					// We don't have to do anything here. We handle depth by startItem and endItem
				}

				public void endLevel(ContentHandler contentHandler) throws SAXException {
					// We don't have to do anything here. We handle depth by startItem and endItem
				}

				public void startItem(ContentHandler contentHandler, Item item, boolean first) throws SAXException {
					reusableAttributes.clear();
					assert !item.getLabel().isHTML(); // TODO Handle rich content in select labels
					reusableAttributes.addAttribute(XFormsConstants.XXFORMS_NAMESPACE_URI, "label", XFormsConstants.XXFORMS_PREFIX + ":label", "CDATA", item.getLabel().getLabel());
					reusableAttributes.addAttribute(XFormsConstants.XXFORMS_NAMESPACE_URI, "value", XFormsConstants.XXFORMS_PREFIX + ":value", "CDATA", item.getValue());
					xmlReceiver.startElement(XFormsConstants.XXFORMS_NAMESPACE_URI, "item", XFormsConstants.XXFORMS_PREFIX + ":item", reusableAttributes);
				}

				 public void endItem(ContentHandler contentHandler, Item item) throws SAXException {
					xmlReceiver.endElement(XFormsConstants.XXFORMS_NAMESPACE_URI, "item", XFormsConstants.XXFORMS_PREFIX + ":item");
				}
    			
    		});
    		
    		xmlReceiver.endElement(XFormsConstants.XXFORMS_NAMESPACE_URI, "items", XFormsConstants.XXFORMS_PREFIX + ":items");
    	}
        
        super.handleControlEnd(uri, localname, qName, attributes, staticId, effectiveId, control);
    }
}
