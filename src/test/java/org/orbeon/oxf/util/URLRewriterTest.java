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
package org.orbeon.oxf.util;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.orbeon.dom.Document;
import org.orbeon.oxf.externalcontext.*;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.ProcessorUtils;
import org.orbeon.oxf.properties.Properties;
import org.orbeon.oxf.test.ResourceManagerTestBase;

import java.util.List;

import static org.junit.Assert.*;

public class URLRewriterTest extends ResourceManagerTestBase {

    private PipelineContext directPipelineContext;
    private PipelineContext forwardPipelineContext;
    private PipelineContext filterPipelineContext;

    private ExternalContext.Request directRequest;
    private ExternalContext.Request forwardRequest;
    private ExternalContext.Request filterRequest;

    @Before
    public void setupResourceManagerTestPipelineContext() {

        {
            directPipelineContext = new PipelineContext();
            final Document requestDocument = ProcessorUtils.createDocumentFromURL("oxf:/org/orbeon/oxf/util/url-rewriter-test-request.xml", null);
            final ExternalContext externalContext = new TestExternalContext(directPipelineContext, requestDocument);
            directRequest = externalContext.getRequest();
            // NOTE: PipelineContext is not really used by TestExternalContext in this test suite
            directPipelineContext.setAttribute(PipelineContext.EXTERNAL_CONTEXT, externalContext);
        }
        {
            forwardPipelineContext = new PipelineContext();
            final Document requestDocument = ProcessorUtils.createDocumentFromURL("oxf:/org/orbeon/oxf/util/url-rewriter-test-request-forward.xml", null);
            final ExternalContext externalContext = new TestExternalContext(forwardPipelineContext, requestDocument);
            forwardRequest = externalContext.getRequest();
            // NOTE: PipelineContext is not really used by TestExternalContext in this test suite
            forwardPipelineContext.setAttribute(PipelineContext.EXTERNAL_CONTEXT, externalContext);
        }
        {
            filterPipelineContext = new PipelineContext();
            final Document requestDocument = ProcessorUtils.createDocumentFromURL("oxf:/org/orbeon/oxf/util/url-rewriter-test-request-filter.xml", null);
            final ExternalContext externalContext = new TestExternalContext(filterPipelineContext, requestDocument);
            filterRequest = externalContext.getRequest();
            // NOTE: PipelineContext is not really used by TestExternalContext in this test suite
            filterPipelineContext.setAttribute(PipelineContext.EXTERNAL_CONTEXT, externalContext);
        }

        // Reinitialize properties to enable versioned resources
        Properties.invalidate();
		org.orbeon.oxf.properties.Properties.init("oxf:/ops/unit-tests/properties-versioned-all.xml");
    }

    @After
    public void tearDownResourceManagerTestPipelineContext() {
        directPipelineContext.destroy(true);
        forwardPipelineContext.destroy(true);
        filterPipelineContext.destroy(true);
    }

    @Test
    public void testServiceRewrite() {

        final String baseURIProperty = URLRewriterUtils.getServiceBaseURI();

        // Test with oxf.url-rewriting.service.base-uri is set to http://example.org/cool/service
        assertEquals("https://foo.com/bar", URLRewriterImpl.rewriteServiceURL(directRequest, "https://foo.com/bar", URLRewriter$.MODULE$.REWRITE_MODE_ABSOLUTE(), baseURIProperty));
//        assertEquals("http://example.org/cool/service/relative/sub/path", URLRewriterImpl.rewriteServiceURL(request, "relative/sub/path", URLRewriter$.MODULE$.REWRITE_MODE_ABSOLUTE()));
        assertEquals("http://example.org/cool/service/bar", URLRewriterImpl.rewriteServiceURL(directRequest, "/bar", URLRewriter$.MODULE$.REWRITE_MODE_ABSOLUTE(), baseURIProperty));
        assertEquals("http://example.org/cool/service/bar?a=1&amp;b=2", URLRewriterImpl.rewriteServiceURL(directRequest, "/bar?a=1&amp;b=2", URLRewriter$.MODULE$.REWRITE_MODE_ABSOLUTE(), baseURIProperty));
        assertEquals("http://example.org/cool/service/bar?a=1&amp;b=2#there", URLRewriterImpl.rewriteServiceURL(directRequest, "/bar?a=1&amp;b=2#there", URLRewriter$.MODULE$.REWRITE_MODE_ABSOLUTE(), baseURIProperty));
        assertEquals("http://example.org/cool/service?a=1&amp;b=2", URLRewriterImpl.rewriteServiceURL(directRequest, "?a=1&amp;b=2", URLRewriter$.MODULE$.REWRITE_MODE_ABSOLUTE(), baseURIProperty));

        assertEquals("https://foo.com/bar", URLRewriterImpl.rewriteServiceURL(directRequest, "https://foo.com/bar", URLRewriter$.MODULE$.REWRITE_MODE_ABSOLUTE_NO_CONTEXT(), baseURIProperty));
//        assertEquals("http://example.org/cool/service/relative/sub/path", URLRewriterImpl.rewriteServiceURL(request, "relative/sub/path", URLRewriter$.MODULE$.REWRITE_MODE_ABSOLUTE_NO_CONTEXT()));
        assertEquals("http://example.org/bar", URLRewriterImpl.rewriteServiceURL(directRequest, "/bar", URLRewriter$.MODULE$.REWRITE_MODE_ABSOLUTE_NO_CONTEXT(), baseURIProperty));
        assertEquals("http://example.org/bar?a=1&amp;b=2", URLRewriterImpl.rewriteServiceURL(directRequest, "/bar?a=1&amp;b=2", URLRewriter$.MODULE$.REWRITE_MODE_ABSOLUTE_NO_CONTEXT(), baseURIProperty));
        assertEquals("http://example.org/bar?a=1&amp;b=2#there", URLRewriterImpl.rewriteServiceURL(directRequest, "/bar?a=1&amp;b=2#there", URLRewriter$.MODULE$.REWRITE_MODE_ABSOLUTE_NO_CONTEXT(), baseURIProperty));
        // NOTE: Ideally should have a "/" between host name and query
        assertEquals("http://example.org?a=1&amp;b=2", URLRewriterImpl.rewriteServiceURL(directRequest, "?a=1&amp;b=2", URLRewriter$.MODULE$.REWRITE_MODE_ABSOLUTE_NO_CONTEXT(), baseURIProperty));

        // TODO: test with oxf.url-rewriting.service.base-uri set to absolute path
        // TODO: test without oxf.url-rewriting.service.base-uri set

    }

    @Test
    public void testRewrite() {
        // Test against request
        int mode = URLRewriter$.MODULE$.REWRITE_MODE_ABSOLUTE();
        assertEquals("https://foo.com/bar", URLRewriterImpl.rewriteURL(directRequest, "https://foo.com/bar", mode));
//        assertEquals("http://example.org/cool/service/relative/sub/path", URLRewriterImpl.rewriteURL(request, "relative/sub/path", mode));
        assertEquals("http://localhost:8080/orbeon/bar", URLRewriterImpl.rewriteURL(directRequest, "/bar", mode));
        assertEquals("http://localhost:8080/orbeon/bar?a=1&amp;b=2", URLRewriterImpl.rewriteURL(directRequest, "/bar?a=1&amp;b=2", mode));
        assertEquals("http://localhost:8080/orbeon/bar?a=1&amp;b=2#there", URLRewriterImpl.rewriteURL(directRequest, "/bar?a=1&amp;b=2#there", mode));
        assertEquals("http://localhost:8080/orbeon/doc/home-welcome?a=1&amp;b=2", URLRewriterImpl.rewriteURL(directRequest, "?a=1&amp;b=2", mode));

        mode = URLRewriter$.MODULE$.REWRITE_MODE_ABSOLUTE_PATH();
        assertEquals("https://foo.com/bar", URLRewriterImpl.rewriteURL(directRequest, "https://foo.com/bar", mode));
//        assertEquals("http://example.org/cool/service/relative/sub/path", URLRewriterImpl.rewriteURL(request, "relative/sub/path", mode));
        assertEquals("/orbeon/bar", URLRewriterImpl.rewriteURL(directRequest, "/bar", mode));
        assertEquals("/orbeon/bar?a=1&amp;b=2", URLRewriterImpl.rewriteURL(directRequest, "/bar?a=1&amp;b=2", mode));
        assertEquals("/orbeon/bar?a=1&amp;b=2#there", URLRewriterImpl.rewriteURL(directRequest, "/bar?a=1&amp;b=2#there", mode));
        assertEquals("/orbeon/doc/home-welcome?a=1&amp;b=2", URLRewriterImpl.rewriteURL(directRequest, "?a=1&amp;b=2", mode));

        mode = URLRewriter$.MODULE$.REWRITE_MODE_ABSOLUTE_PATH_NO_CONTEXT();
        assertEquals("https://foo.com/bar", URLRewriterImpl.rewriteURL(directRequest, "https://foo.com/bar", mode));
//        assertEquals("http://example.org/cool/service/relative/sub/path", URLRewriterImpl.rewriteURL(request, "relative/sub/path", mode));
        assertEquals("/bar", URLRewriterImpl.rewriteURL(directRequest, "/bar", mode));
        assertEquals("/bar?a=1&amp;b=2", URLRewriterImpl.rewriteURL(directRequest, "/bar?a=1&amp;b=2", mode));
        assertEquals("/bar?a=1&amp;b=2#there", URLRewriterImpl.rewriteURL(directRequest, "/bar?a=1&amp;b=2#there", mode));
        assertEquals("/doc/home-welcome?a=1&amp;b=2", URLRewriterImpl.rewriteURL(directRequest, "?a=1&amp;b=2", mode));

        mode = URLRewriter$.MODULE$.REWRITE_MODE_ABSOLUTE_PATH_OR_RELATIVE();
        assertEquals("https://foo.com/bar", URLRewriterImpl.rewriteURL(directRequest, "https://foo.com/bar", mode));
//        assertEquals("http://example.org/cool/service/relative/sub/path", URLRewriterImpl.rewriteURL(request, "relative/sub/path", mode));
        assertEquals("/orbeon/bar", URLRewriterImpl.rewriteURL(directRequest, "/bar", mode));
        assertEquals("/orbeon/bar?a=1&amp;b=2", URLRewriterImpl.rewriteURL(directRequest, "/bar?a=1&amp;b=2", mode));
        assertEquals("/orbeon/bar?a=1&amp;b=2#there", URLRewriterImpl.rewriteURL(directRequest, "/bar?a=1&amp;b=2#there", mode));
        assertEquals("/orbeon/doc/home-welcome?a=1&amp;b=2", URLRewriterImpl.rewriteURL(directRequest, "?a=1&amp;b=2", mode));
    }

    @Test
    public void testResourceRewrite() {

        final List<PathMatcher> pathMatchers = URLRewriterUtils.MATCH_ALL_PATH_MATCHERS;
        final String version = URLRewriterUtils.getOrbeonVersionForClient();

        // Test against request
        int mode = URLRewriter$.MODULE$.REWRITE_MODE_ABSOLUTE();
        assertEquals("https://foo.com/bar", URLRewriterUtils.rewriteResourceURL(directRequest, "https://foo.com/bar", pathMatchers , mode));
//        assertEquals("http://example.org/cool/service/relative/sub/path", URLRewriterUtils.rewriteResourceURL(request, "relative/sub/path", pathMatchers , mode));
        assertEquals("http://localhost:8080/orbeon/42/bar", URLRewriterUtils.rewriteResourceURL(directRequest, "/bar", pathMatchers , mode));
        assertEquals("http://localhost:8080/orbeon/42/bar?a=1&amp;b=2", URLRewriterUtils.rewriteResourceURL(directRequest, "/bar?a=1&amp;b=2", pathMatchers , mode));
        assertEquals("http://localhost:8080/orbeon/42/bar?a=1&amp;b=2#there", URLRewriterUtils.rewriteResourceURL(directRequest, "/bar?a=1&amp;b=2#there", pathMatchers , mode));
        assertEquals("http://localhost:8080/orbeon/42/doc/home-welcome?a=1&amp;b=2", URLRewriterUtils.rewriteResourceURL(directRequest, "?a=1&amp;b=2", pathMatchers , mode));
        assertEquals("http://localhost:8080/orbeon/" + version + "/ops/bar.png", URLRewriterUtils.rewriteResourceURL(directRequest, "/ops/bar.png", pathMatchers , mode));
        assertEquals("http://localhost:8080/orbeon/" + version + "/config/bar.png", URLRewriterUtils.rewriteResourceURL(directRequest, "/config/bar.png", pathMatchers , mode));

        mode = URLRewriter$.MODULE$.REWRITE_MODE_ABSOLUTE_PATH();
        assertEquals("https://foo.com/bar", URLRewriterUtils.rewriteResourceURL(directRequest, "https://foo.com/bar", pathMatchers , mode));
//        assertEquals("http://example.org/cool/service/relative/sub/path", URLRewriterUtils.rewriteResourceURL(request, "relative/sub/path", pathMatchers , mode));
        assertEquals("/orbeon/42/bar", URLRewriterUtils.rewriteResourceURL(directRequest, "/bar", pathMatchers , mode));
        assertEquals("/orbeon/42/bar?a=1&amp;b=2", URLRewriterUtils.rewriteResourceURL(directRequest, "/bar?a=1&amp;b=2", pathMatchers , mode));
        assertEquals("/orbeon/42/bar?a=1&amp;b=2#there", URLRewriterUtils.rewriteResourceURL(directRequest, "/bar?a=1&amp;b=2#there", pathMatchers , mode));
        assertEquals("/orbeon/42/doc/home-welcome?a=1&amp;b=2", URLRewriterUtils.rewriteResourceURL(directRequest, "?a=1&amp;b=2", pathMatchers , mode));
        assertEquals("/orbeon/" + version + "/ops/bar.png", URLRewriterUtils.rewriteResourceURL(directRequest, "/ops/bar.png", pathMatchers , mode));
        assertEquals("/orbeon/" + version + "/config/bar.png", URLRewriterUtils.rewriteResourceURL(directRequest, "/config/bar.png", pathMatchers , mode));

        mode = URLRewriter$.MODULE$.REWRITE_MODE_ABSOLUTE_PATH_NO_CONTEXT();
        assertEquals("https://foo.com/bar", URLRewriterUtils.rewriteResourceURL(directRequest, "https://foo.com/bar", pathMatchers , mode));
//        assertEquals("http://example.org/cool/service/relative/sub/path", URLRewriterUtils.rewriteResourceURL(request, "relative/sub/path", pathMatchers , mode));
        assertEquals("/42/bar", URLRewriterUtils.rewriteResourceURL(directRequest, "/bar", pathMatchers , mode));
        assertEquals("/42/bar?a=1&amp;b=2", URLRewriterUtils.rewriteResourceURL(directRequest, "/bar?a=1&amp;b=2", pathMatchers , mode));
        assertEquals("/42/bar?a=1&amp;b=2#there", URLRewriterUtils.rewriteResourceURL(directRequest, "/bar?a=1&amp;b=2#there", pathMatchers , mode));
        assertEquals("/42/doc/home-welcome?a=1&amp;b=2", URLRewriterUtils.rewriteResourceURL(directRequest, "?a=1&amp;b=2", pathMatchers , mode));
        assertEquals("/" + version + "/ops/bar.png", URLRewriterUtils.rewriteResourceURL(directRequest, "/ops/bar.png", pathMatchers , mode));
        assertEquals("/" + version + "/config/bar.png", URLRewriterUtils.rewriteResourceURL(directRequest, "/config/bar.png", pathMatchers , mode));

        mode = URLRewriter$.MODULE$.REWRITE_MODE_ABSOLUTE_PATH_OR_RELATIVE();
        assertEquals("https://foo.com/bar", URLRewriterUtils.rewriteResourceURL(directRequest, "https://foo.com/bar", pathMatchers , mode));
//        assertEquals("http://example.org/cool/service/relative/sub/path", URLRewriterUtils.rewriteResourceURL(request, "relative/sub/path", pathMatchers , mode));
        assertEquals("/orbeon/42/bar", URLRewriterUtils.rewriteResourceURL(directRequest, "/bar", pathMatchers , mode));
        assertEquals("/orbeon/42/bar?a=1&amp;b=2", URLRewriterUtils.rewriteResourceURL(directRequest, "/bar?a=1&amp;b=2", pathMatchers , mode));
        assertEquals("/orbeon/42/bar?a=1&amp;b=2#there", URLRewriterUtils.rewriteResourceURL(directRequest, "/bar?a=1&amp;b=2#there", pathMatchers , mode));
        assertEquals("/orbeon/42/doc/home-welcome?a=1&amp;b=2", URLRewriterUtils.rewriteResourceURL(directRequest, "?a=1&amp;b=2", pathMatchers , mode));
        assertEquals("/orbeon/" + version + "/ops/bar.png", URLRewriterUtils.rewriteResourceURL(directRequest, "/ops/bar.png", pathMatchers , mode));
        assertEquals("/orbeon/" + version + "/config/bar.png", URLRewriterUtils.rewriteResourceURL(directRequest, "/config/bar.png", pathMatchers , mode));
    }

    @Test
    public void testServiceRewriteForward() {

        final String baseURIProperty = URLRewriterUtils.getServiceBaseURI();

        // Test with oxf.url-rewriting.service.base-uri is set to http://example.org/cool/service
        assertEquals("https://foo.com/bar", URLRewriterImpl.rewriteServiceURL(forwardRequest, "https://foo.com/bar", URLRewriter$.MODULE$.REWRITE_MODE_ABSOLUTE(), baseURIProperty));
//        assertEquals("http://example.org/cool/service/relative/sub/path", URLRewriterImpl.rewriteServiceURL(request, "relative/sub/path", URLRewriter$.MODULE$.REWRITE_MODE_ABSOLUTE()));
        assertEquals("http://example.org/cool/service/bar", URLRewriterImpl.rewriteServiceURL(forwardRequest, "/bar", URLRewriter$.MODULE$.REWRITE_MODE_ABSOLUTE(), baseURIProperty));
        assertEquals("http://example.org/cool/service/bar?a=1&amp;b=2", URLRewriterImpl.rewriteServiceURL(forwardRequest, "/bar?a=1&amp;b=2", URLRewriter$.MODULE$.REWRITE_MODE_ABSOLUTE(), baseURIProperty));
        assertEquals("http://example.org/cool/service/bar?a=1&amp;b=2#there", URLRewriterImpl.rewriteServiceURL(forwardRequest, "/bar?a=1&amp;b=2#there", URLRewriter$.MODULE$.REWRITE_MODE_ABSOLUTE(), baseURIProperty));
        assertEquals("http://example.org/cool/service?a=1&amp;b=2", URLRewriterImpl.rewriteServiceURL(forwardRequest, "?a=1&amp;b=2", URLRewriter$.MODULE$.REWRITE_MODE_ABSOLUTE(), baseURIProperty));

        assertEquals("https://foo.com/bar", URLRewriterImpl.rewriteServiceURL(forwardRequest, "https://foo.com/bar", URLRewriter$.MODULE$.REWRITE_MODE_ABSOLUTE_NO_CONTEXT(), baseURIProperty));
//        assertEquals("http://example.org/cool/service/relative/sub/path", URLRewriterImpl.rewriteServiceURL(request, "relative/sub/path", URLRewriter$.MODULE$.REWRITE_MODE_ABSOLUTE_NO_CONTEXT()));
        assertEquals("http://example.org/bar", URLRewriterImpl.rewriteServiceURL(forwardRequest, "/bar", URLRewriter$.MODULE$.REWRITE_MODE_ABSOLUTE_NO_CONTEXT(), baseURIProperty));
        assertEquals("http://example.org/bar?a=1&amp;b=2", URLRewriterImpl.rewriteServiceURL(forwardRequest, "/bar?a=1&amp;b=2", URLRewriter$.MODULE$.REWRITE_MODE_ABSOLUTE_NO_CONTEXT(), baseURIProperty));
        assertEquals("http://example.org/bar?a=1&amp;b=2#there", URLRewriterImpl.rewriteServiceURL(forwardRequest, "/bar?a=1&amp;b=2#there", URLRewriter$.MODULE$.REWRITE_MODE_ABSOLUTE_NO_CONTEXT(), baseURIProperty));
        // NOTE: Ideally should have a "/" between host name and query
        assertEquals("http://example.org?a=1&amp;b=2", URLRewriterImpl.rewriteServiceURL(forwardRequest, "?a=1&amp;b=2", URLRewriter$.MODULE$.REWRITE_MODE_ABSOLUTE_NO_CONTEXT(), baseURIProperty));

        // TODO: test with oxf.url-rewriting.service.base-uri set to absolute path
        // TODO: test without oxf.url-rewriting.service.base-uri set
    }

    @Test
    public void testRewriteForward() {
        // Test against request
        int mode = URLRewriter$.MODULE$.REWRITE_MODE_ABSOLUTE();
        assertEquals("https://foo.com/bar", URLRewriterImpl.rewriteURL(forwardRequest, "https://foo.com/bar", mode));
//        assertEquals("http://example.org/cool/service/relative/sub/path", URLRewriterImpl.rewriteURL(request, "relative/sub/path", mode));
        assertEquals("http://localhost:8080/myapp/bar", URLRewriterImpl.rewriteURL(forwardRequest, "/bar", mode));
        assertEquals("http://localhost:8080/myapp/bar?a=1&amp;b=2", URLRewriterImpl.rewriteURL(forwardRequest, "/bar?a=1&amp;b=2", mode));
        assertEquals("http://localhost:8080/myapp/bar?a=1&amp;b=2#there", URLRewriterImpl.rewriteURL(forwardRequest, "/bar?a=1&amp;b=2#there", mode));
        assertEquals("http://localhost:8080/myapp/doc/home-welcome?a=1&amp;b=2", URLRewriterImpl.rewriteURL(forwardRequest, "?a=1&amp;b=2", mode));

        mode = URLRewriter$.MODULE$.REWRITE_MODE_ABSOLUTE_PATH();
        assertEquals("https://foo.com/bar", URLRewriterImpl.rewriteURL(forwardRequest, "https://foo.com/bar", mode));
//        assertEquals("http://example.org/cool/service/relative/sub/path", URLRewriterImpl.rewriteURL(request, "relative/sub/path", mode));
        assertEquals("/myapp/bar", URLRewriterImpl.rewriteURL(forwardRequest, "/bar", mode));
        assertEquals("/myapp/bar?a=1&amp;b=2", URLRewriterImpl.rewriteURL(forwardRequest, "/bar?a=1&amp;b=2", mode));
        assertEquals("/myapp/bar?a=1&amp;b=2#there", URLRewriterImpl.rewriteURL(forwardRequest, "/bar?a=1&amp;b=2#there", mode));
        assertEquals("/myapp/doc/home-welcome?a=1&amp;b=2", URLRewriterImpl.rewriteURL(forwardRequest, "?a=1&amp;b=2", mode));

        mode = URLRewriter$.MODULE$.REWRITE_MODE_ABSOLUTE_PATH_NO_CONTEXT();
        assertEquals("https://foo.com/bar", URLRewriterImpl.rewriteURL(forwardRequest, "https://foo.com/bar", mode));
//        assertEquals("http://example.org/cool/service/relative/sub/path", URLRewriterImpl.rewriteURL(request, "relative/sub/path", mode));
        assertEquals("/bar", URLRewriterImpl.rewriteURL(forwardRequest, "/bar", mode));
        assertEquals("/bar?a=1&amp;b=2", URLRewriterImpl.rewriteURL(forwardRequest, "/bar?a=1&amp;b=2", mode));
        assertEquals("/bar?a=1&amp;b=2#there", URLRewriterImpl.rewriteURL(forwardRequest, "/bar?a=1&amp;b=2#there", mode));
        assertEquals("/doc/home-welcome?a=1&amp;b=2", URLRewriterImpl.rewriteURL(forwardRequest, "?a=1&amp;b=2", mode));

        mode = URLRewriter$.MODULE$.REWRITE_MODE_ABSOLUTE_PATH_OR_RELATIVE();
        assertEquals("https://foo.com/bar", URLRewriterImpl.rewriteURL(forwardRequest, "https://foo.com/bar", mode));
//        assertEquals("http://example.org/cool/service/relative/sub/path", URLRewriterImpl.rewriteURL(request, "relative/sub/path", mode));
        assertEquals("/myapp/bar", URLRewriterImpl.rewriteURL(forwardRequest, "/bar", mode));
        assertEquals("/myapp/bar?a=1&amp;b=2", URLRewriterImpl.rewriteURL(forwardRequest, "/bar?a=1&amp;b=2", mode));
        assertEquals("/myapp/bar?a=1&amp;b=2#there", URLRewriterImpl.rewriteURL(forwardRequest, "/bar?a=1&amp;b=2#there", mode));
        assertEquals("/myapp/doc/home-welcome?a=1&amp;b=2", URLRewriterImpl.rewriteURL(forwardRequest, "?a=1&amp;b=2", mode));
    }

    @Test
    public void testResourceRewriteForward() {

        final List<PathMatcher> pathMatchers = URLRewriterUtils.MATCH_ALL_PATH_MATCHERS;
        final String version = URLRewriterUtils.getOrbeonVersionForClient();

        // Test against request
        int mode = URLRewriter$.MODULE$.REWRITE_MODE_ABSOLUTE();
        assertEquals("https://foo.com/bar", URLRewriterUtils.rewriteResourceURL(forwardRequest, "https://foo.com/bar", pathMatchers , mode));
//        assertEquals("http://example.org/cool/service/relative/sub/path", URLRewriterUtils.rewriteResourceURL(request, "relative/sub/path", pathMatchers , mode));
        assertEquals("http://localhost:8080/myapp/bar", URLRewriterUtils.rewriteResourceURL(forwardRequest, "/bar", pathMatchers , mode));
        assertEquals("http://localhost:8080/myapp/bar?a=1&amp;b=2", URLRewriterUtils.rewriteResourceURL(forwardRequest, "/bar?a=1&amp;b=2", pathMatchers , mode));
        assertEquals("http://localhost:8080/myapp/bar?a=1&amp;b=2#there", URLRewriterUtils.rewriteResourceURL(forwardRequest, "/bar?a=1&amp;b=2#there", pathMatchers , mode));
        assertEquals("http://localhost:8080/myapp/doc/home-welcome?a=1&amp;b=2", URLRewriterUtils.rewriteResourceURL(forwardRequest, "?a=1&amp;b=2", pathMatchers , mode));
        assertEquals("http://localhost:8080/myapp/orbeon/" + version + "/ops/bar.png", URLRewriterUtils.rewriteResourceURL(forwardRequest, "/ops/bar.png", pathMatchers , mode));
        assertEquals("http://localhost:8080/myapp/orbeon/" + version + "/config/bar.png", URLRewriterUtils.rewriteResourceURL(forwardRequest, "/config/bar.png", pathMatchers , mode));

        mode = URLRewriter$.MODULE$.REWRITE_MODE_ABSOLUTE_PATH();
        assertEquals("https://foo.com/bar", URLRewriterUtils.rewriteResourceURL(forwardRequest, "https://foo.com/bar", pathMatchers , mode));
//        assertEquals("http://example.org/cool/service/relative/sub/path", URLRewriterUtils.rewriteResourceURL(request, "relative/sub/path", pathMatchers , mode));
        assertEquals("/myapp/bar", URLRewriterUtils.rewriteResourceURL(forwardRequest, "/bar", pathMatchers , mode));
        assertEquals("/myapp/bar?a=1&amp;b=2", URLRewriterUtils.rewriteResourceURL(forwardRequest, "/bar?a=1&amp;b=2", pathMatchers , mode));
        assertEquals("/myapp/bar?a=1&amp;b=2#there", URLRewriterUtils.rewriteResourceURL(forwardRequest, "/bar?a=1&amp;b=2#there", pathMatchers , mode));
        assertEquals("/myapp/doc/home-welcome?a=1&amp;b=2", URLRewriterUtils.rewriteResourceURL(forwardRequest, "?a=1&amp;b=2", pathMatchers , mode));
        assertEquals("/myapp/orbeon/" + version + "/ops/bar.png", URLRewriterUtils.rewriteResourceURL(forwardRequest, "/ops/bar.png", pathMatchers , mode));
        assertEquals("/myapp/orbeon/" + version + "/config/bar.png", URLRewriterUtils.rewriteResourceURL(forwardRequest, "/config/bar.png", pathMatchers , mode));

        mode = URLRewriter$.MODULE$.REWRITE_MODE_ABSOLUTE_PATH_NO_CONTEXT();
        assertEquals("https://foo.com/bar", URLRewriterUtils.rewriteResourceURL(forwardRequest, "https://foo.com/bar", pathMatchers , mode));
//        assertEquals("http://example.org/cool/service/relative/sub/path", URLRewriterUtils.rewriteResourceURL(request, "relative/sub/path", pathMatchers , mode));
        assertEquals("/bar", URLRewriterUtils.rewriteResourceURL(forwardRequest, "/bar", pathMatchers , mode));
        assertEquals("/bar?a=1&amp;b=2", URLRewriterUtils.rewriteResourceURL(forwardRequest, "/bar?a=1&amp;b=2", pathMatchers , mode));
        assertEquals("/bar?a=1&amp;b=2#there", URLRewriterUtils.rewriteResourceURL(forwardRequest, "/bar?a=1&amp;b=2#there", pathMatchers , mode));
        assertEquals("/doc/home-welcome?a=1&amp;b=2", URLRewriterUtils.rewriteResourceURL(forwardRequest, "?a=1&amp;b=2", pathMatchers , mode));
        assertEquals("/" + version + "/ops/bar.png", URLRewriterUtils.rewriteResourceURL(forwardRequest, "/ops/bar.png", pathMatchers , mode));
        assertEquals("/" + version + "/config/bar.png", URLRewriterUtils.rewriteResourceURL(forwardRequest, "/config/bar.png", pathMatchers , mode));

        mode = URLRewriter$.MODULE$.REWRITE_MODE_ABSOLUTE_PATH_OR_RELATIVE();
        assertEquals("https://foo.com/bar", URLRewriterUtils.rewriteResourceURL(forwardRequest, "https://foo.com/bar", pathMatchers , mode));
//        assertEquals("http://example.org/cool/service/relative/sub/path", URLRewriterUtils.rewriteResourceURL(request, "relative/sub/path", pathMatchers , mode));
        assertEquals("/myapp/bar", URLRewriterUtils.rewriteResourceURL(forwardRequest, "/bar", pathMatchers , mode));
        assertEquals("/myapp/bar?a=1&amp;b=2", URLRewriterUtils.rewriteResourceURL(forwardRequest, "/bar?a=1&amp;b=2", pathMatchers , mode));
        assertEquals("/myapp/bar?a=1&amp;b=2#there", URLRewriterUtils.rewriteResourceURL(forwardRequest, "/bar?a=1&amp;b=2#there", pathMatchers , mode));
        assertEquals("/myapp/doc/home-welcome?a=1&amp;b=2", URLRewriterUtils.rewriteResourceURL(forwardRequest, "?a=1&amp;b=2", pathMatchers , mode));
        assertEquals("/myapp/orbeon/" + version + "/ops/bar.png", URLRewriterUtils.rewriteResourceURL(forwardRequest, "/ops/bar.png", pathMatchers , mode));
        assertEquals("/myapp/orbeon/" + version + "/config/bar.png", URLRewriterUtils.rewriteResourceURL(forwardRequest, "/config/bar.png", pathMatchers , mode));
    }

    @Test
    public void testResourceRewriteFilter() {

        final List<PathMatcher> pathMatchers = URLRewriterUtils.MATCH_ALL_PATH_MATCHERS;
        final String version = URLRewriterUtils.getOrbeonVersionForClient();

        // Test against request
        int mode = URLRewriter$.MODULE$.REWRITE_MODE_ABSOLUTE();
        assertEquals("https://foo.com/bar", URLRewriterUtils.rewriteResourceURL(filterRequest, "https://foo.com/bar", pathMatchers , mode));
//        assertEquals("http://example.org/cool/service/relative/sub/path", URLRewriterUtils.rewriteResourceURL(request, "relative/sub/path", pathMatchers , mode));
        assertEquals("http://localhost:8080/myapp/bar", URLRewriterUtils.rewriteResourceURL(filterRequest, "/bar", pathMatchers , mode));
        assertEquals("http://localhost:8080/myapp/bar?a=1&amp;b=2", URLRewriterUtils.rewriteResourceURL(filterRequest, "/bar?a=1&amp;b=2", pathMatchers , mode));
        assertEquals("http://localhost:8080/myapp/bar?a=1&amp;b=2#there", URLRewriterUtils.rewriteResourceURL(filterRequest, "/bar?a=1&amp;b=2#there", pathMatchers , mode));
        assertEquals("http://localhost:8080/myapp/doc/home-welcome?a=1&amp;b=2", URLRewriterUtils.rewriteResourceURL(filterRequest, "?a=1&amp;b=2", pathMatchers , mode));
        assertEquals("http://localhost:8080/myapp/orbeon/" + version + "/ops/bar.png", URLRewriterUtils.rewriteResourceURL(filterRequest, "/ops/bar.png", pathMatchers , mode));
        assertEquals("http://localhost:8080/myapp/orbeon/" + version + "/config/bar.png", URLRewriterUtils.rewriteResourceURL(filterRequest, "/config/bar.png", pathMatchers , mode));

        mode = URLRewriter$.MODULE$.REWRITE_MODE_ABSOLUTE_PATH();
        assertEquals("https://foo.com/bar", URLRewriterUtils.rewriteResourceURL(filterRequest, "https://foo.com/bar", pathMatchers , mode));
//        assertEquals("http://example.org/cool/service/relative/sub/path", URLRewriterUtils.rewriteResourceURL(request, "relative/sub/path", pathMatchers , mode));
        assertEquals("/myapp/bar", URLRewriterUtils.rewriteResourceURL(filterRequest, "/bar", pathMatchers , mode));
        assertEquals("/myapp/bar?a=1&amp;b=2", URLRewriterUtils.rewriteResourceURL(filterRequest, "/bar?a=1&amp;b=2", pathMatchers , mode));
        assertEquals("/myapp/bar?a=1&amp;b=2#there", URLRewriterUtils.rewriteResourceURL(filterRequest, "/bar?a=1&amp;b=2#there", pathMatchers , mode));
        assertEquals("/myapp/doc/home-welcome?a=1&amp;b=2", URLRewriterUtils.rewriteResourceURL(filterRequest, "?a=1&amp;b=2", pathMatchers , mode));
        assertEquals("/myapp/orbeon/" + version + "/ops/bar.png", URLRewriterUtils.rewriteResourceURL(filterRequest, "/ops/bar.png", pathMatchers , mode));
        assertEquals("/myapp/orbeon/" + version + "/config/bar.png", URLRewriterUtils.rewriteResourceURL(filterRequest, "/config/bar.png", pathMatchers , mode));

        mode = URLRewriter$.MODULE$.REWRITE_MODE_ABSOLUTE_PATH_NO_CONTEXT();
        assertEquals("https://foo.com/bar", URLRewriterUtils.rewriteResourceURL(filterRequest, "https://foo.com/bar", pathMatchers , mode));
//        assertEquals("http://example.org/cool/service/relative/sub/path", URLRewriterUtils.rewriteResourceURL(request, "relative/sub/path", pathMatchers , mode));
        assertEquals("/bar", URLRewriterUtils.rewriteResourceURL(filterRequest, "/bar", pathMatchers , mode));
        assertEquals("/bar?a=1&amp;b=2", URLRewriterUtils.rewriteResourceURL(filterRequest, "/bar?a=1&amp;b=2", pathMatchers , mode));
        assertEquals("/bar?a=1&amp;b=2#there", URLRewriterUtils.rewriteResourceURL(filterRequest, "/bar?a=1&amp;b=2#there", pathMatchers , mode));
        assertEquals("/doc/home-welcome?a=1&amp;b=2", URLRewriterUtils.rewriteResourceURL(filterRequest, "?a=1&amp;b=2", pathMatchers , mode));
        assertEquals("/" + version + "/ops/bar.png", URLRewriterUtils.rewriteResourceURL(filterRequest, "/ops/bar.png", pathMatchers , mode));
        assertEquals("/" + version + "/config/bar.png", URLRewriterUtils.rewriteResourceURL(filterRequest, "/config/bar.png", pathMatchers , mode));

        mode = URLRewriter$.MODULE$.REWRITE_MODE_ABSOLUTE_PATH_OR_RELATIVE();
        assertEquals("https://foo.com/bar", URLRewriterUtils.rewriteResourceURL(filterRequest, "https://foo.com/bar", pathMatchers , mode));
//        assertEquals("http://example.org/cool/service/relative/sub/path", URLRewriterUtils.rewriteResourceURL(request, "relative/sub/path", pathMatchers , mode));
        assertEquals("/myapp/bar", URLRewriterUtils.rewriteResourceURL(filterRequest, "/bar", pathMatchers , mode));
        assertEquals("/myapp/bar?a=1&amp;b=2", URLRewriterUtils.rewriteResourceURL(filterRequest, "/bar?a=1&amp;b=2", pathMatchers , mode));
        assertEquals("/myapp/bar?a=1&amp;b=2#there", URLRewriterUtils.rewriteResourceURL(filterRequest, "/bar?a=1&amp;b=2#there", pathMatchers , mode));
        assertEquals("/myapp/doc/home-welcome?a=1&amp;b=2", URLRewriterUtils.rewriteResourceURL(filterRequest, "?a=1&amp;b=2", pathMatchers , mode));
        assertEquals("/myapp/orbeon/" + version + "/ops/bar.png", URLRewriterUtils.rewriteResourceURL(filterRequest, "/ops/bar.png", pathMatchers , mode));
        assertEquals("/myapp/orbeon/" + version + "/config/bar.png", URLRewriterUtils.rewriteResourceURL(filterRequest, "/config/bar.png", pathMatchers , mode));
    }

    @Test
    public void testDecodeResourceURI() {

        // NOTE: Unclear case: /xforms-server/foobar. URLRewriterUtils.rewriteResourceURL() does not rewrite
        // /xforms-server/foobar as a resource URL and it is not clear why.

        final String orbeonVersion = URLRewriterUtils.getOrbeonVersionForClient();
        final String[] propertiesURLs = { "oxf:/ops/unit-tests/properties-versioned-all.xml", "oxf:/ops/unit-tests/properties-versioned-orbeon.xml" };
        final String[] platformPaths = { "/ops/bar", "/config/bar", "/xbl/orbeon/bar", "/forms/orbeon/bar", "/apps/fr/bar", "/xforms-server" };

        for (final String propertiesURL: propertiesURLs) {

            // Reinitialize properties
            Properties.invalidate();
            org.orbeon.oxf.properties.Properties.init(propertiesURL);

            // Check platform paths
            for (final String path: platformPaths) {
                final String versionedPath = "/" + orbeonVersion + path;
                // Make sure this is recognized as a platform path
                assertTrue(URLRewriterUtils.isPlatformPath(path));
                // Just decoding
                assertEquals(path, URLRewriterUtils.decodeResourceURI(versionedPath, true));
                // Encoding/decoding
                assertEquals(path, URLRewriterUtils.decodeResourceURI(URLRewriterUtils.rewriteResourceURL(directRequest, path,
                        URLRewriterUtils.MATCH_ALL_PATH_MATCHERS, URLRewriter$.MODULE$.REWRITE_MODE_ABSOLUTE_PATH_NO_CONTEXT()), true));
            }

            // Check non-platform paths
            final String[] customPaths = { "/opsla", "/configuration", "/xbl/acme/bar", "/forms/acme/bar", "/apps/myapp/bar", "/xforms-foo" };
            final String[] decodedCustomPaths = { "/apps/opsla", "/apps/configuration", "/xbl/acme/bar", "/forms/acme/bar", "/apps/myapp/bar", "/apps/xforms-foo" };
            final String appVersion = URLRewriterUtils.getApplicationResourceVersion();
            int i = 0;
            for (final String path: customPaths) {
                // Make sure this is recognized as a non-platform path
                assertFalse(URLRewriterUtils.isPlatformPath(path));

                final String decodedCustomPath = decodedCustomPaths[i];

                if (appVersion != null) {
                    // Case where there is an app version number

                    final String versionedPath = "/" + appVersion + path;
                    // Just decoding
                    assertEquals(decodedCustomPath, URLRewriterUtils.decodeResourceURI(versionedPath, true));
                    // Encoding/decoding
                    assertEquals(decodedCustomPath, URLRewriterUtils.decodeResourceURI(URLRewriterUtils.rewriteResourceURL(directRequest, path,
                            URLRewriterUtils.MATCH_ALL_PATH_MATCHERS, URLRewriter$.MODULE$.REWRITE_MODE_ABSOLUTE_PATH_NO_CONTEXT()), true));
                } else {
                    // Case where there is NO app version number

                    // Just decoding
                    assertEquals(decodedCustomPath, URLRewriterUtils.decodeResourceURI(path, true));
                    // Encoding/decoding
                    assertEquals(decodedCustomPath, URLRewriterUtils.decodeResourceURI(URLRewriterUtils.rewriteResourceURL(directRequest, path,
                            URLRewriterUtils.MATCH_ALL_PATH_MATCHERS, URLRewriter$.MODULE$.REWRITE_MODE_ABSOLUTE_PATH_NO_CONTEXT()), true));
                }
                i++;
            }
        }
    }

    @Test
    public void testHRRI() {
        // Test for spaces
        assertEquals("http://localhost:8080/myapp/a%20b", MarkupUtils.encodeHRRI("http://localhost:8080/myapp/a b", true));
        assertEquals("http://localhost:8080/myapp/a b", MarkupUtils.encodeHRRI("http://localhost:8080/myapp/a b", false));
        // Test for trim()
        assertEquals("http://localhost:8080/", MarkupUtils.encodeHRRI("  http://localhost:8080/  ", true));
        // Test for other characters
        assertEquals("http://localhost:8080/myapp/%3C%3E%22%7B%7D%7C%5C%5E%60", MarkupUtils.encodeHRRI("http://localhost:8080/myapp/<>\"{}|\\^`", true));
    }

    @Test
    public void testResolveURI() {
        assertEquals("http://localhost:8080/myapp/a%20b", NetUtils.resolveURI("a b", "http://localhost:8080/myapp/"));
        assertEquals("http://localhost:8080/myapp/a%20b", NetUtils.resolveURI("http://localhost:8080/myapp/a b", null));
    }
}
