/**
 *  Copyright (C) 2009 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.processor;

import org.dom4j.Document;
import org.dom4j.Element;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.test.ResourceManagerTestBase;
import org.orbeon.oxf.util.XPathCache;
import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.xbl.XBLUtils;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.saxon.Configuration;
import org.orbeon.saxon.dom4j.DocumentWrapper;
import org.orbeon.saxon.dom4j.NodeWrapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class XFormsDocumentAnnotatorContentHandlerTest extends ResourceManagerTestBase {

    public static final HashMap BASIC_NAMESPACE_MAPPINGS = new HashMap();
    static {
        BASIC_NAMESPACE_MAPPINGS.put(XFormsConstants.XFORMS_PREFIX, XFormsConstants.XFORMS_NAMESPACE_URI);
        BASIC_NAMESPACE_MAPPINGS.put(XFormsConstants.XXFORMS_PREFIX, XFormsConstants.XXFORMS_NAMESPACE_URI);
        BASIC_NAMESPACE_MAPPINGS.put(XFormsConstants.XML_EVENTS_PREFIX, XFormsConstants.XML_EVENTS_NAMESPACE_URI);
        BASIC_NAMESPACE_MAPPINGS.put(XMLConstants.XHTML_PREFIX, XMLConstants.XHTML_NAMESPACE_URI);
    }

    public void testFormNamespaceElements() {

        final Map mappings = new HashMap();
        final XFormsDocumentAnnotatorContentHandler ch = new XFormsDocumentAnnotatorContentHandler(mappings);
        XMLUtils.urlToSAX("oxf:/org/orbeon/oxf/xforms/processor/test-form.xml", ch, false, false);

        // Test that ns information is provided for those elements
        Map result = (Map) mappings.get("output-in-title");
        assertNotNull(result);

        result = (Map) mappings.get("html");
        assertNotNull(result);
        
        result = (Map) mappings.get("main-instance");
        assertNotNull(result);

        result = (Map) mappings.get("dateTime-component");
        assertNotNull(result);

        result = (Map) mappings.get("dateTime1-control");
        assertNotNull(result);

        result = (Map) mappings.get("value1-control");
        assertNotNull(result);

        result = (Map) mappings.get("output-in-label");
        assertNotNull(result);

        result = (Map) mappings.get("img-in-label");
        assertNotNull(result);

        result = (Map) mappings.get("span");
        assertNotNull(result);

        // Test that ns information is NOT provided for those elements (because processed as part of shadow tree processing)
        result = (Map) mappings.get("instance-in-xbl");
        assertNull(result);

        result = (Map) mappings.get("div-in-xbl");
        assertNull(result);

        // Test that ns information is NOT provided for those elements (because in instances or schemas)
        result = (Map) mappings.get("instance-root");
        assertNull(result);

        result = (Map) mappings.get("instance-value");
        assertNull(result);

        result = (Map) mappings.get("xbl-instance-root");
        assertNull(result);

        result = (Map) mappings.get("xbl-instance-value");
        assertNull(result);

        result = (Map) mappings.get("schema-element");
        assertNull(result);
    }

    // Test that xxforms:attribute elements with @id and @for were created for
    public void testXXFormsAttribute() {

        final Map mappings = new HashMap();
        final Document document = Dom4jUtils.readFromURL("oxf:/org/orbeon/oxf/xforms/processor/test-form.xml", false, false);
        final Document annotatedDocument = XBLUtils.annotateShadowTree(document, mappings, "");
        final DocumentWrapper documentWrapper = new DocumentWrapper(annotatedDocument, null, new Configuration());

        // Check there is an xxforms:attribute for "html" with correct name
        List result = XPathCache.evaluate(new PipelineContext(), documentWrapper, "//xxforms:attribute[@for = 'html']", BASIC_NAMESPACE_MAPPINGS, null, null, null, null, null);

        assertNotNull(result);
        assertEquals(1, result.size());
        Element resultElement = (Element) ((NodeWrapper) result.get(0)).getUnderlyingNode();
        assertTrue(resultElement.attributeValue("id").trim().length() > 0);
        assertEquals("lang", resultElement.attributeValue("name"));

        // Check there is an xxforms:attribute for "span" with correct name
        result = XPathCache.evaluate(new PipelineContext(), documentWrapper, "//xxforms:attribute[@for = 'span']", BASIC_NAMESPACE_MAPPINGS, null, null, null, null, null);

        assertNotNull(result);
        assertEquals(1, result.size());
        resultElement = (Element) ((NodeWrapper) result.get(0)).getUnderlyingNode();
        assertTrue(resultElement.attributeValue("id").trim().length() > 0);
        assertEquals("style", resultElement.attributeValue("name"));
    }
}
