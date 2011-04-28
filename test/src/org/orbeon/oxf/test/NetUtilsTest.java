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
package org.orbeon.oxf.test;

import org.dom4j.Document;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.orbeon.oxf.externalcontext.RequestAdapter;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.ProcessorUtils;
import org.orbeon.oxf.processor.test.TestExternalContext;
import org.orbeon.oxf.util.ISODateUtils;
import org.orbeon.oxf.util.NetUtils;
import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.XFormsUtils;
import org.orbeon.oxf.xml.XMLConstants;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class NetUtilsTest extends ResourceManagerTestBase {

    private PipelineContext pipelineContext;

    @Before
    public void setup() throws Exception {

        pipelineContext = new PipelineContext();

        final Document requestDocument = ProcessorUtils.createDocumentFromURL("oxf:/org/orbeon/oxf/test/if-modified-since-request.xml", null);
        final ExternalContext externalContext = new TestExternalContext(pipelineContext, requestDocument);
        pipelineContext.setAttribute(PipelineContext.EXTERNAL_CONTEXT, externalContext);
    }

    @After
    public void tearDown() {
        pipelineContext.destroy(true);
    }

    @Test
    public void testCheckIfModifiedSince() {

        // Get long value for If-Modified-Since present in request
        final String ifModifiedHeaderString = "Thu, 28 Jun 2007 14:17:36 GMT";
        final long ifModifiedHeaderLong = ISODateUtils.parseRFC1123Date(ifModifiedHeaderString);

        final ExternalContext.Request request = new RequestAdapter() {
            @Override
            public String getMethod() {
                return "GET";
            }

            private final Map<String, String[]> headers = new HashMap<String, String[]>();
            {
                headers.put("if-modified-since", new String[] { ifModifiedHeaderString });
            }

            @Override
            public Map<String, String[]> getHeaderValuesMap() {
                return headers;
            }
        };

        assertEquals(NetUtils.checkIfModifiedSince(request, ifModifiedHeaderLong -1), false);
        assertEquals(NetUtils.checkIfModifiedSince(request, ifModifiedHeaderLong), false);
        // For some reason the code checks that there is more than one second of difference
        assertEquals(NetUtils.checkIfModifiedSince(request, ifModifiedHeaderLong + 1001), true);
    }

    @Test
    public void testConvertUploadTypes() {

        final String testDataURI = "oxf:/org/orbeon/oxf/test/anyuri-test-content.xml";
        final String testDataString = "You have to let people challenge your ideas.";
        final String testDataBase64 = "WW91IGhhdmUgdG8gbGV0IHBlb3BsZSBjaGFsbGVuZ2UgeW91ciBpZGVhcy4=";

        // anyURI -> base64
        assertEquals(XFormsUtils.convertUploadTypes(testDataURI,
                XMLConstants.XS_ANYURI_EXPLODED_QNAME, XMLConstants.XS_BASE64BINARY_EXPLODED_QNAME),
                testDataBase64);

        assertEquals(XFormsUtils.convertUploadTypes(testDataURI,
                XMLConstants.XS_ANYURI_EXPLODED_QNAME, XFormsConstants.XFORMS_BASE64BINARY_EXPLODED_QNAME),
                testDataBase64);

        assertEquals(XFormsUtils.convertUploadTypes(testDataURI,
                XFormsConstants.XFORMS_ANYURI_EXPLODED_QNAME, XMLConstants.XS_BASE64BINARY_EXPLODED_QNAME),
                testDataBase64);

        assertEquals(XFormsUtils.convertUploadTypes(testDataURI,
                XFormsConstants.XFORMS_ANYURI_EXPLODED_QNAME, XFormsConstants.XFORMS_BASE64BINARY_EXPLODED_QNAME),
                testDataBase64);

        // base64 -> anyURI
        String result = XFormsUtils.convertUploadTypes(testDataBase64,
                XMLConstants.XS_BASE64BINARY_EXPLODED_QNAME, XMLConstants.XS_ANYURI_EXPLODED_QNAME);
        assertTrue(result.startsWith("file:/"));
        assertEquals(testDataBase64, NetUtils.anyURIToBase64Binary(result));

        result = XFormsUtils.convertUploadTypes(testDataBase64,
                XMLConstants.XS_BASE64BINARY_EXPLODED_QNAME, XFormsConstants.XFORMS_ANYURI_EXPLODED_QNAME);
        assertTrue(result.startsWith("file:/"));
        assertEquals(testDataBase64, NetUtils.anyURIToBase64Binary(result));

        result = XFormsUtils.convertUploadTypes(testDataBase64,
                XFormsConstants.XFORMS_BASE64BINARY_EXPLODED_QNAME, XMLConstants.XS_ANYURI_EXPLODED_QNAME);
        assertTrue(result.startsWith("file:/"));
        assertEquals(testDataBase64, NetUtils.anyURIToBase64Binary(result));

        result = XFormsUtils.convertUploadTypes(testDataBase64,
                XFormsConstants.XFORMS_BASE64BINARY_EXPLODED_QNAME, XFormsConstants.XFORMS_ANYURI_EXPLODED_QNAME);
        assertTrue(result.startsWith("file:/"));
        assertEquals(testDataBase64, NetUtils.anyURIToBase64Binary(result));

        // All the following tests must not change the input
        assertEquals(XFormsUtils.convertUploadTypes(testDataString,
                XMLConstants.XS_ANYURI_EXPLODED_QNAME, XFormsConstants.XFORMS_ANYURI_EXPLODED_QNAME),
                testDataString);

        assertEquals(XFormsUtils.convertUploadTypes(testDataString,
                XFormsConstants.XFORMS_ANYURI_EXPLODED_QNAME, XMLConstants.XS_ANYURI_EXPLODED_QNAME),
                testDataString);

        assertEquals(XFormsUtils.convertUploadTypes(testDataBase64,
                XMLConstants.XS_BASE64BINARY_EXPLODED_QNAME, XFormsConstants.XFORMS_BASE64BINARY_EXPLODED_QNAME),
                testDataBase64);

        assertEquals(XFormsUtils.convertUploadTypes(testDataBase64,
                XFormsConstants.XFORMS_BASE64BINARY_EXPLODED_QNAME, XMLConstants.XS_BASE64BINARY_EXPLODED_QNAME),
                testDataBase64);
    }
}
