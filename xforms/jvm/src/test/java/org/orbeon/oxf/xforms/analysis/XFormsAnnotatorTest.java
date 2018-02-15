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

import org.junit.Test;
import org.orbeon.dom.Document;
import org.orbeon.dom.Element;
import org.orbeon.dom.saxon.DocumentWrapper;
import org.orbeon.dom.saxon.NodeWrapper;
import org.orbeon.oxf.test.ResourceManagerTestBase;
import org.orbeon.oxf.util.IndentedLogger;
import org.orbeon.oxf.util.StringUtils;
import org.orbeon.oxf.util.XPath;
import org.orbeon.oxf.util.XPathCache;
import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.XFormsStaticStateImpl;
import org.orbeon.oxf.xforms.XFormsUtils;
import org.orbeon.oxf.xforms.processor.XFormsServer;
import org.orbeon.oxf.xforms.xbl.XBLBindings;
import org.orbeon.oxf.xml.XMLParsing;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;

import java.util.List;

import static org.junit.Assert.*;

public class XFormsAnnotatorTest extends ResourceManagerTestBase {

    @Test
    public void formNamespaceElements() {

        final Metadata metadata = new Metadata();
        final XFormsAnnotator ch = new XFormsAnnotator(metadata);
        XMLParsing.urlToSAX("oxf:/org/orbeon/oxf/xforms/forms/annotator-test.xhtml", ch, XMLParsing.ParserConfiguration.PLAIN, false);

        // Test that ns information is provided for those elements
        assertNotNull(metadata.getNamespaceMapping("output-in-title").mapping());
        assertNotNull(metadata.getNamespaceMapping("html").mapping());
        assertNotNull(metadata.getNamespaceMapping("main-instance").mapping());
        assertNotNull(metadata.getNamespaceMapping("dateTime-component").mapping());
        assertNotNull(metadata.getNamespaceMapping("dateTime1-control").mapping());
        assertNotNull(metadata.getNamespaceMapping("value1-control").mapping());
        assertNotNull(metadata.getNamespaceMapping("output-in-label").mapping());
        assertNotNull(metadata.getNamespaceMapping("img-in-label").mapping());
        assertNotNull(metadata.getNamespaceMapping("span").mapping());

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

    // Test that xxf:attribute elements with @id and @for were created for
    @Test
    public void xxformsAttribute() {

        final Document document = Dom4jUtils.readFromURL("oxf:/org/orbeon/oxf/xforms/forms/annotator-test.xhtml", XMLParsing.ParserConfiguration.PLAIN);
        final Metadata metadata = new Metadata();
        final Document annotatedDocument =
            new XBLBindings(new IndentedLogger(XFormsServer.logger), null, metadata).annotateShadowTree(document, "");
        final DocumentWrapper documentWrapper =
            new DocumentWrapper(annotatedDocument, null, XPath.GlobalConfiguration());

        // Check there is an xxf:attribute for "html" with correct name
        List<Object> result =
            XPathCache.evaluate(
                documentWrapper,
                "//xxf:attribute[@for = 'html']",
                XFormsStaticStateImpl.BASIC_NAMESPACE_MAPPING(),
                null,
                null,
                null,
                null,
                null,
                null
            );

        assertNotNull(result);
        assertEquals(1, result.size());
        Element resultElement = (Element) ((NodeWrapper) result.get(0)).getUnderlyingNode();
        assertTrue(StringUtils.trimAllToEmpty(XFormsUtils.getElementId(resultElement)).length() > 0);
        assertEquals("lang", resultElement.attributeValue(XFormsConstants.NAME_QNAME));

        // Check there is an xxf:attribute for "span" with correct name
        result =
            XPathCache.evaluate(
                documentWrapper, "//xxf:attribute[@for = 'span']",
                XFormsStaticStateImpl.BASIC_NAMESPACE_MAPPING(),
                null,
                null,
                null,
                null,
                null,
                null
            );

        assertNotNull(result);
        assertEquals(1, result.size());
        resultElement = (Element) ((NodeWrapper) result.get(0)).getUnderlyingNode();
        assertTrue(StringUtils.trimAllToEmpty(XFormsUtils.getElementId(resultElement)).length() > 0);
        assertEquals("style", resultElement.attributeValue(XFormsConstants.NAME_QNAME));
    }
}
