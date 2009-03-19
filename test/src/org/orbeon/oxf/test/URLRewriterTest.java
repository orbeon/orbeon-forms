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
import org.orbeon.oxf.util.URLRewriterUtils;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

public class URLRewriterTest extends TestCase {

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

        final Document requestDocument = ProcessorUtils.createDocumentFromURL("oxf:/org/orbeon/oxf/test/url-rewriter-test-request.xml", null);
        externalContext = new TestExternalContext(pipelineContext, requestDocument);
        request = externalContext.getRequest();
        response = externalContext.getResponse();
    }

    public void testServiceRewrite() {

        // Test with oxf.url-rewriting.service.base-uri is set to http://example.org/cool/service
        assertEquals("https://foo.com/bar", URLRewriterUtils.rewriteServiceURL(request, "https://foo.com/bar", true));
//        assertEquals("http://example.org/cool/service/relative/sub/path", URLRewriterUtils.rewriteServiceURL(request, "relative/sub/path", true));
        assertEquals("http://example.org/cool/service/bar", URLRewriterUtils.rewriteServiceURL(request, "/bar", true));
        assertEquals("http://example.org/cool/service/bar?a=1&amp;b=2", URLRewriterUtils.rewriteServiceURL(request, "/bar?a=1&amp;b=2", true));
        assertEquals("http://example.org/cool/service/bar?a=1&amp;b=2#there", URLRewriterUtils.rewriteServiceURL(request, "/bar?a=1&amp;b=2#there", true));
        assertEquals("http://example.org/cool/service?a=1&amp;b=2", URLRewriterUtils.rewriteServiceURL(request, "?a=1&amp;b=2", true));
    }

    public void testRewrite() {
        // Test against request
        int mode = ExternalContext.Response.REWRITE_MODE_ABSOLUTE;
        assertEquals("https://foo.com/bar", URLRewriterUtils.rewriteURL(request, "https://foo.com/bar", mode));
//        assertEquals("http://example.org/cool/service/relative/sub/path", URLRewriterUtils.rewriteURL(request, "relative/sub/path", mode));
        assertEquals("http://localhost:8080/orbeon/bar", URLRewriterUtils.rewriteURL(request, "/bar", mode));
        assertEquals("http://localhost:8080/orbeon/bar?a=1&amp;b=2", URLRewriterUtils.rewriteURL(request, "/bar?a=1&amp;b=2", mode));
        assertEquals("http://localhost:8080/orbeon/bar?a=1&amp;b=2#there", URLRewriterUtils.rewriteURL(request, "/bar?a=1&amp;b=2#there", mode));
        assertEquals("http://localhost:8080/orbeon/doc/home-welcome?a=1&amp;b=2", URLRewriterUtils.rewriteURL(request, "?a=1&amp;b=2", mode));

        mode = ExternalContext.Response.REWRITE_MODE_ABSOLUTE_PATH;
        assertEquals("https://foo.com/bar", URLRewriterUtils.rewriteURL(request, "https://foo.com/bar", mode));
//        assertEquals("http://example.org/cool/service/relative/sub/path", URLRewriterUtils.rewriteURL(request, "relative/sub/path", mode));
        assertEquals("/orbeon/bar", URLRewriterUtils.rewriteURL(request, "/bar", mode));
        assertEquals("/orbeon/bar?a=1&amp;b=2", URLRewriterUtils.rewriteURL(request, "/bar?a=1&amp;b=2", mode));
        assertEquals("/orbeon/bar?a=1&amp;b=2#there", URLRewriterUtils.rewriteURL(request, "/bar?a=1&amp;b=2#there", mode));
        assertEquals("/orbeon/doc/home-welcome?a=1&amp;b=2", URLRewriterUtils.rewriteURL(request, "?a=1&amp;b=2", mode));

        mode = ExternalContext.Response.REWRITE_MODE_ABSOLUTE_PATH_NO_CONTEXT;
        assertEquals("https://foo.com/bar", URLRewriterUtils.rewriteURL(request, "https://foo.com/bar", mode));
//        assertEquals("http://example.org/cool/service/relative/sub/path", URLRewriterUtils.rewriteURL(request, "relative/sub/path", mode));
        assertEquals("/bar", URLRewriterUtils.rewriteURL(request, "/bar", mode));
        assertEquals("/bar?a=1&amp;b=2", URLRewriterUtils.rewriteURL(request, "/bar?a=1&amp;b=2", mode));
        assertEquals("/bar?a=1&amp;b=2#there", URLRewriterUtils.rewriteURL(request, "/bar?a=1&amp;b=2#there", mode));
        assertEquals("/doc/home-welcome?a=1&amp;b=2", URLRewriterUtils.rewriteURL(request, "?a=1&amp;b=2", mode));

        mode = ExternalContext.Response.REWRITE_MODE_ABSOLUTE_PATH_OR_RELATIVE;
        assertEquals("https://foo.com/bar", URLRewriterUtils.rewriteURL(request, "https://foo.com/bar", mode));
//        assertEquals("http://example.org/cool/service/relative/sub/path", URLRewriterUtils.rewriteURL(request, "relative/sub/path", mode));
        assertEquals("/orbeon/bar", URLRewriterUtils.rewriteURL(request, "/bar", mode));
        assertEquals("/orbeon/bar?a=1&amp;b=2", URLRewriterUtils.rewriteURL(request, "/bar?a=1&amp;b=2", mode));
        assertEquals("/orbeon/bar?a=1&amp;b=2#there", URLRewriterUtils.rewriteURL(request, "/bar?a=1&amp;b=2#there", mode));
        assertEquals("/orbeon/doc/home-welcome?a=1&amp;b=2", URLRewriterUtils.rewriteURL(request, "?a=1&amp;b=2", mode));
    }
}
