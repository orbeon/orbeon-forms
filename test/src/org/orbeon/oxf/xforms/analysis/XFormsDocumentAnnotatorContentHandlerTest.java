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
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.test.ResourceManagerTestBase;
import org.orbeon.oxf.util.IndentedLogger;
import org.orbeon.oxf.util.XPathCache;
import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.processor.XFormsServer;
import org.orbeon.oxf.xforms.xbl.XBLBindings;
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

    public static final HashMap<String, String> BASIC_NAMESPACE_MAPPINGS = new HashMap<String, String>();
    static {
        BASIC_NAMESPACE_MAPPINGS.put(XFormsConstants.XFORMS_PREFIX, XFormsConstants.XFORMS_NAMESPACE_URI);
        BASIC_NAMESPACE_MAPPINGS.put(XFormsConstants.XXFORMS_PREFIX, XFormsConstants.XXFORMS_NAMESPACE_URI);
        BASIC_NAMESPACE_MAPPINGS.put(XFormsConstants.XML_EVENTS_PREFIX, XFormsConstants.XML_EVENTS_NAMESPACE_URI);
        BASIC_NAMESPACE_MAPPINGS.put(XMLConstants.XHTML_PREFIX, XMLConstants.XHTML_NAMESPACE_URI);
    }

    public void testFormNamespaceElements() {

        final Map<String, Map<String, String>> mappings = new HashMap<String, Map<String, String>>();
        final XFormsAnnotatorContentHandler ch = new XFormsAnnotatorContentHandler(new XFormsAnnotatorContentHandler.Metadata(new IdGenerator(), mappings));
        XMLUtils.urlToSAX("oxf:/org/orbeon/oxf/xforms/processor/test-form.xml", ch, false, false);

        // Test that ns information is provided for those elements
        Map<String, String> result = mappings.get("output-in-title");
        assertNotNull(result);

        result = mappings.get("html");
        assertNotNull(result);
        
        result = mappings.get("main-instance");
        assertNotNull(result);

        result = mappings.get("dateTime-component");
        assertNotNull(result);

        result = mappings.get("dateTime1-control");
        assertNotNull(result);

        result = mappings.get("value1-control");
        assertNotNull(result);

        result = mappings.get("output-in-label");
        assertNotNull(result);

        result = mappings.get("img-in-label");
        assertNotNull(result);

        result = mappings.get("span");
        assertNotNull(result);

        // Test that ns information is NOT provided for those elements (because processed as part of shadow tree processing)
        result = mappings.get("instance-in-xbl");
        assertNull(result);

        result = mappings.get("div-in-xbl");
        assertNull(result);

        // Test that ns information is NOT provided for those elements (because in instances or schemas)
        result = mappings.get("instance-root");
        assertNull(result);

        result = mappings.get("instance-value");
        assertNull(result);

        result = mappings.get("xbl-instance-root");
        assertNull(result);

        result = mappings.get("xbl-instance-value");
        assertNull(result);

        result = mappings.get("schema-element");
        assertNull(result);
    }

    // Test that xxforms:attribute elements with @id and @for were created for
    public void testXXFormsAttribute() {

        final Map<String, Map<String, String>> mappings = new HashMap<String, Map<String, String>>();
        final Document document = Dom4jUtils.readFromURL("oxf:/org/orbeon/oxf/xforms/processor/test-form.xml", false, false);
        final Document annotatedDocument = new XBLBindings(new IndentedLogger(XFormsServer.getLogger(), ""), null, null, mappings, null)
                .annotateShadowTree(document, "", new XFormsAnnotatorContentHandler.Metadata(new IdGenerator(), mappings));
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
