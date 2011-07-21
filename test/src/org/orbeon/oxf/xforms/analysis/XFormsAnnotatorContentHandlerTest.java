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
package org.orbeon.oxf.xforms.analysis;

import org.dom4j.Document;
import org.dom4j.Element;
import org.junit.Test;
import org.orbeon.oxf.test.ResourceManagerTestBase;
import org.orbeon.oxf.util.IndentedLogger;
import org.orbeon.oxf.util.XPathCache;
import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.XFormsStaticStateImpl;
import org.orbeon.oxf.xforms.processor.XFormsServer;
import org.orbeon.oxf.xforms.xbl.XBLBindings;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.saxon.dom4j.DocumentWrapper;
import org.orbeon.saxon.dom4j.NodeWrapper;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import static junit.framework.Assert.*;

public class XFormsAnnotatorContentHandlerTest extends ResourceManagerTestBase {

    public static final HashMap<String, String> BASIC_NAMESPACE_MAPPINGS = new HashMap<String, String>();
    static {
        BASIC_NAMESPACE_MAPPINGS.put(XFormsConstants.XFORMS_PREFIX, XFormsConstants.XFORMS_NAMESPACE_URI);
        BASIC_NAMESPACE_MAPPINGS.put(XFormsConstants.XXFORMS_PREFIX, XFormsConstants.XXFORMS_NAMESPACE_URI);
        BASIC_NAMESPACE_MAPPINGS.put(XFormsConstants.XML_EVENTS_PREFIX, XFormsConstants.XML_EVENTS_NAMESPACE_URI);
        BASIC_NAMESPACE_MAPPINGS.put(XMLConstants.XHTML_PREFIX, XMLConstants.XHTML_NAMESPACE_URI);
    }

    @Test
    public void formNamespaceElements() {

        final Metadata metadata = new Metadata();
        final XFormsAnnotatorContentHandler ch = new XFormsAnnotatorContentHandler(metadata);
        XMLUtils.urlToSAX("oxf:/org/orbeon/oxf/xforms/processor/test-form.xml", ch, XMLUtils.ParserConfiguration.PLAIN, false);

        // Test that ns information is provided for those elements
        assertNotNull(metadata.getNamespaceMapping("output-in-title").mapping);
        assertNotNull(metadata.getNamespaceMapping("html").mapping);
        assertNotNull(metadata.getNamespaceMapping("main-instance").mapping);
        assertNotNull(metadata.getNamespaceMapping("dateTime-component").mapping);
        assertNotNull(metadata.getNamespaceMapping("dateTime1-control").mapping);
        assertNotNull(metadata.getNamespaceMapping("value1-control").mapping);
        assertNotNull(metadata.getNamespaceMapping("output-in-label").mapping);
        assertNotNull(metadata.getNamespaceMapping("img-in-label").mapping);
        assertNotNull(metadata.getNamespaceMapping("span").mapping);

        // Test that ns information is NOT provided for those elements (because processed as part of shadow tree processing)
        assertNull(metadata.getNamespaceMapping("instance-in-xbl"));
        assertNull(metadata.getNamespaceMapping("div-in-xbl"));

        // Test that ns information is NOT provided for those elements (because in instances or schemas)
        assertNull(metadata.getNamespaceMapping("instance-root"));
        assertNull(metadata.getNamespaceMapping("instance-value"));
        assertNull(metadata.getNamespaceMapping("xbl-instance-root"));
        assertNull(metadata.getNamespaceMapping("xbl-instance-value"));
        assertNull(metadata.getNamespaceMapping("schema-element"));
    }

    // Test that xxforms:attribute elements with @id and @for were created for
    @Test
    public void xxformsAttribute() {

        final Document document = Dom4jUtils.readFromURL("oxf:/org/orbeon/oxf/xforms/processor/test-form.xml", XMLUtils.ParserConfiguration.PLAIN);
        final Metadata metadata = new Metadata();
        final Document annotatedDocument = new XBLBindings(new IndentedLogger(XFormsServer.getLogger(), ""), null, metadata, Collections.<Document>emptyList())
                .annotateShadowTree(document, "", false);
        final DocumentWrapper documentWrapper = new DocumentWrapper(annotatedDocument, null, XPathCache.getGlobalConfiguration());

        // Check there is an xxforms:attribute for "html" with correct name
        List result = XPathCache.evaluate(documentWrapper, "//xxforms:attribute[@for = 'html']", XFormsStaticStateImpl.BASIC_NAMESPACE_MAPPING(), null, null, null, null, null);

        assertNotNull(result);
        assertEquals(1, result.size());
        Element resultElement = (Element) ((NodeWrapper) result.get(0)).getUnderlyingNode();
        assertTrue(resultElement.attributeValue(XFormsConstants.ID_QNAME).trim().length() > 0);
        assertEquals("lang", resultElement.attributeValue(XFormsConstants.NAME_QNAME));

        // Check there is an xxforms:attribute for "span" with correct name
        result = XPathCache.evaluate(documentWrapper, "//xxforms:attribute[@for = 'span']", XFormsStaticStateImpl.BASIC_NAMESPACE_MAPPING(), null, null, null, null, null);

        assertNotNull(result);
        assertEquals(1, result.size());
        resultElement = (Element) ((NodeWrapper) result.get(0)).getUnderlyingNode();
        assertTrue(resultElement.attributeValue(XFormsConstants.ID_QNAME).trim().length() > 0);
        assertEquals("style", resultElement.attributeValue(XFormsConstants.NAME_QNAME));
    }
}
