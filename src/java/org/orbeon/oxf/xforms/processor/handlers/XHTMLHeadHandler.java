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
package org.orbeon.oxf.xforms.processor.handlers;

import org.orbeon.oxf.xml.ContentHandlerHelper;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.oxf.xml.XMLUtils;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * Handle xhtml:head.
 */
public class XHTMLHeadHandler extends HandlerBase {

    private static final String[] stylesheets = {
            // Calendar stylesheets
            "/config/theme/jscalendar/calendar-blue.css",
            // Other standard stylesheets
            "/config/theme/xforms.css"
    };

    private static final String[] scripts = {
            // Calendar scripts
            "/config/theme/jscalendar/calendar.js",
            "/config/theme/jscalendar/lang/calendar-en.js",
            "/config/theme/jscalendar/calendar-setup.js",
            // Other standard scripts
            "/config/theme/javascript/xforms-style.js",
            "/ops/javascript/wz_tooltip.js",
            "/ops/javascript/overlib_mini.js",
            "/ops/javascript/time-utils.js",
            "/ops/javascript/sarissa.js",
            "/ops/javascript/xforms.js"
    };

    public XHTMLHeadHandler() {
        super(false, true);
    }

    public void start(String uri, String localname, String qName, Attributes attributes) throws SAXException {

        final ContentHandler contentHandler = handlerContext.getController().getOutput();

        // Open head element
        contentHandler.startElement(uri, localname, qName, attributes);

        final ContentHandlerHelper helper = new ContentHandlerHelper(contentHandler);
        final String prefix = XMLUtils.prefixFromQName(qName); // current prefix for XHTML

        // Stylesheets
        for (int i = 0; i < stylesheets.length; i++) {
            helper.element(prefix, XMLConstants.XHTML_NAMESPACE_URI, "link", new String[] {
                    "rel", "stylesheet", "href", stylesheets[i], "type", "text/css"});
        }

        // Scripts
        for (int i = 0; i < scripts.length; i++) {
            helper.element(prefix, XMLConstants.XHTML_NAMESPACE_URI, "script", new String[] {
                "type", "text/javascript", "src", scripts[i]});
        }
    }

    public void end(String uri, String localname, String qName) throws SAXException {

        // Close head element
        final ContentHandler contentHandler = handlerContext.getController().getOutput();
        contentHandler.endElement(uri, localname, qName);
    }
}
