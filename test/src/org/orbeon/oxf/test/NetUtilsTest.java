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

import junit.framework.TestCase;
import org.dom4j.Document;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.ProcessorUtils;
import org.orbeon.oxf.processor.test.TestExternalContext;
import org.orbeon.oxf.resources.ResourceManagerWrapper;
import org.orbeon.oxf.util.NetUtils;
import org.orbeon.oxf.util.ISODateUtils;
import org.orbeon.oxf.util.HttpServletRequestStub;
import org.orbeon.oxf.externalcontext.ExternalContextToHttpServletRequestWrapper;

import javax.servlet.http.HttpServletRequest;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

public class NetUtilsTest extends TestCase {

    private PipelineContext pipelineContext;
    private ExternalContext externalContext;
    private ExternalContext.Request request;
    private ExternalContext.Response response;

    // TODO - need to clean this up
	// Copied from ProcessorTest
    static {
		// Setup resource manager
		final Map props = new HashMap();
		final java.util.Properties properties = System.getProperties();
		for (Enumeration e = properties.propertyNames(); e.hasMoreElements();) {
			final String name = (String) e.nextElement();
			if (name.startsWith("oxf.resources."))
				props.put(name, properties.getProperty(name));
		}
		ResourceManagerWrapper.init(props);
		org.orbeon.oxf.properties.Properties.init("oxf:/ops/unit-tests/properties.xml");
	}

    protected void setUp() throws Exception {

        pipelineContext = new PipelineContext();

        final Document requestDocument = ProcessorUtils.createDocumentFromURL("oxf:/org/orbeon/oxf/test/if-modified-since-request.xml", null);
        externalContext = new TestExternalContext(pipelineContext, requestDocument);
        pipelineContext.setAttribute(PipelineContext.EXTERNAL_CONTEXT, externalContext);
        request = externalContext.getRequest();
        response = externalContext.getResponse();
    }

    public void testCheckIfModifiedSince() {

        // Get long value for If-Modified-Since present in request
        final String ifModifiedHeaderString = "Thu, 28 Jun 2007 14:17:36 GMT";
        final long ifModifiedHeaderLong = ISODateUtils.parseRFC1123Date(ifModifiedHeaderString);

        final HttpServletRequest httpServletRequest = new HttpServletRequestStub() {
            public String getMethod() {
                return "GET";
            }

            public String getHeader(String s) {
                if (s.equalsIgnoreCase("If-Modified-Since")) {
                    return ifModifiedHeaderString;
                } else {
                    return null;
                }
            }
        };

        assertEquals(NetUtils.checkIfModifiedSince(httpServletRequest, ifModifiedHeaderLong -1), false);
        assertEquals(NetUtils.checkIfModifiedSince(httpServletRequest, ifModifiedHeaderLong), false);
        // For some reason the code checks that there is more than one second of difference
        assertEquals(NetUtils.checkIfModifiedSince(httpServletRequest, ifModifiedHeaderLong + 1001), true);
    }

    public void testProxyURI() {
        assertEquals("/xforms-server/dynamic/87c938edbc170d5038192ca5ab9add97", NetUtils.proxyURI(pipelineContext, "/foo/bar.png", null, null, -1));
        assertEquals("/xforms-server/dynamic/674c2ff956348155ff60c01c0c0ec2e0", NetUtils.proxyURI(pipelineContext, "http://example.org/foo/bar.png", null, null, -1));
    }
}
