/**
 * Copyright (C) 2009 Orbeon, Inc.
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

import org.dom4j.Document;
import org.orbeon.oxf.common.Version;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.ProcessorUtils;
import org.orbeon.oxf.processor.test.TestExternalContext;
import org.orbeon.oxf.test.ResourceManagerTestBase;

import java.util.List;

public class URLRewriterTest extends ResourceManagerTestBase {

    private ExternalContext.Request directRequest;
    private ExternalContext.Request forwardRequest;
    private ExternalContext.Request filterRequest;

    protected void setUp() throws Exception {
        {
            final PipelineContext pipelineContext = new PipelineContext();
            final Document requestDocument = ProcessorUtils.createDocumentFromURL("oxf:/org/orbeon/oxf/util/url-rewriter-test-request.xml", null);
            final ExternalContext externalContext = new TestExternalContext(pipelineContext, requestDocument);
            directRequest = externalContext.getRequest();
            // NOTE: PipelineContext is not really used by TestExternalContext in this test suite
            pipelineContext.setAttribute(PipelineContext.EXTERNAL_CONTEXT, externalContext);
        }
        {
            final PipelineContext pipelineContext = new PipelineContext();
            final Document requestDocument = ProcessorUtils.createDocumentFromURL("oxf:/org/orbeon/oxf/util/url-rewriter-test-request-forward.xml", null);
            final ExternalContext externalContext = new TestExternalContext(pipelineContext, requestDocument);
            forwardRequest = externalContext.getRequest();
            // NOTE: PipelineContext is not really used by TestExternalContext in this test suite
            pipelineContext.setAttribute(PipelineContext.EXTERNAL_CONTEXT, externalContext);
        }
        {
            final PipelineContext pipelineContext = new PipelineContext();
            final Document requestDocument = ProcessorUtils.createDocumentFromURL("oxf:/org/orbeon/oxf/util/url-rewriter-test-request-filter.xml", null);
            final ExternalContext externalContext = new TestExternalContext(pipelineContext, requestDocument);
            filterRequest = externalContext.getRequest();
            // NOTE: PipelineContext is not really used by TestExternalContext in this test suite
            pipelineContext.setAttribute(PipelineContext.EXTERNAL_CONTEXT, externalContext);
        }
    }

    public void testServiceRewrite() {

        // Test with oxf.url-rewriting.service.base-uri is set to http://example.org/cool/service
        assertEquals("https://foo.com/bar", URLRewriterUtils.rewriteServiceURL(directRequest, "https://foo.com/bar", true));
//        assertEquals("http://example.org/cool/service/relative/sub/path", URLRewriterUtils.rewriteServiceURL(request, "relative/sub/path", true));
        assertEquals("http://example.org/cool/service/bar", URLRewriterUtils.rewriteServiceURL(directRequest, "/bar", true));
        assertEquals("http://example.org/cool/service/bar?a=1&amp;b=2", URLRewriterUtils.rewriteServiceURL(directRequest, "/bar?a=1&amp;b=2", true));
        assertEquals("http://example.org/cool/service/bar?a=1&amp;b=2#there", URLRewriterUtils.rewriteServiceURL(directRequest, "/bar?a=1&amp;b=2#there", true));
        assertEquals("http://example.org/cool/service?a=1&amp;b=2", URLRewriterUtils.rewriteServiceURL(directRequest, "?a=1&amp;b=2", true));

        // TODO: test with oxf.url-rewriting.service.base-uri set to absolute path
        // TODO: test without oxf.url-rewriting.service.base-uri set
    }

    public void testRewrite() {
        // Test against request
        int mode = ExternalContext.Response.REWRITE_MODE_ABSOLUTE;
        assertEquals("https://foo.com/bar", URLRewriterUtils.rewriteURL(directRequest, "https://foo.com/bar", mode));
//        assertEquals("http://example.org/cool/service/relative/sub/path", URLRewriterUtils.rewriteURL(request, "relative/sub/path", mode));
        assertEquals("http://localhost:8080/orbeon/bar", URLRewriterUtils.rewriteURL(directRequest, "/bar", mode));
        assertEquals("http://localhost:8080/orbeon/bar?a=1&amp;b=2", URLRewriterUtils.rewriteURL(directRequest, "/bar?a=1&amp;b=2", mode));
        assertEquals("http://localhost:8080/orbeon/bar?a=1&amp;b=2#there", URLRewriterUtils.rewriteURL(directRequest, "/bar?a=1&amp;b=2#there", mode));
        assertEquals("http://localhost:8080/orbeon/doc/home-welcome?a=1&amp;b=2", URLRewriterUtils.rewriteURL(directRequest, "?a=1&amp;b=2", mode));

        mode = ExternalContext.Response.REWRITE_MODE_ABSOLUTE_PATH;
        assertEquals("https://foo.com/bar", URLRewriterUtils.rewriteURL(directRequest, "https://foo.com/bar", mode));
//        assertEquals("http://example.org/cool/service/relative/sub/path", URLRewriterUtils.rewriteURL(request, "relative/sub/path", mode));
        assertEquals("/orbeon/bar", URLRewriterUtils.rewriteURL(directRequest, "/bar", mode));
        assertEquals("/orbeon/bar?a=1&amp;b=2", URLRewriterUtils.rewriteURL(directRequest, "/bar?a=1&amp;b=2", mode));
        assertEquals("/orbeon/bar?a=1&amp;b=2#there", URLRewriterUtils.rewriteURL(directRequest, "/bar?a=1&amp;b=2#there", mode));
        assertEquals("/orbeon/doc/home-welcome?a=1&amp;b=2", URLRewriterUtils.rewriteURL(directRequest, "?a=1&amp;b=2", mode));

        mode = ExternalContext.Response.REWRITE_MODE_ABSOLUTE_PATH_NO_CONTEXT;
        assertEquals("https://foo.com/bar", URLRewriterUtils.rewriteURL(directRequest, "https://foo.com/bar", mode));
//        assertEquals("http://example.org/cool/service/relative/sub/path", URLRewriterUtils.rewriteURL(request, "relative/sub/path", mode));
        assertEquals("/bar", URLRewriterUtils.rewriteURL(directRequest, "/bar", mode));
        assertEquals("/bar?a=1&amp;b=2", URLRewriterUtils.rewriteURL(directRequest, "/bar?a=1&amp;b=2", mode));
        assertEquals("/bar?a=1&amp;b=2#there", URLRewriterUtils.rewriteURL(directRequest, "/bar?a=1&amp;b=2#there", mode));
        assertEquals("/doc/home-welcome?a=1&amp;b=2", URLRewriterUtils.rewriteURL(directRequest, "?a=1&amp;b=2", mode));

        mode = ExternalContext.Response.REWRITE_MODE_ABSOLUTE_PATH_OR_RELATIVE;
        assertEquals("https://foo.com/bar", URLRewriterUtils.rewriteURL(directRequest, "https://foo.com/bar", mode));
//        assertEquals("http://example.org/cool/service/relative/sub/path", URLRewriterUtils.rewriteURL(request, "relative/sub/path", mode));
        assertEquals("/orbeon/bar", URLRewriterUtils.rewriteURL(directRequest, "/bar", mode));
        assertEquals("/orbeon/bar?a=1&amp;b=2", URLRewriterUtils.rewriteURL(directRequest, "/bar?a=1&amp;b=2", mode));
        assertEquals("/orbeon/bar?a=1&amp;b=2#there", URLRewriterUtils.rewriteURL(directRequest, "/bar?a=1&amp;b=2#there", mode));
        assertEquals("/orbeon/doc/home-welcome?a=1&amp;b=2", URLRewriterUtils.rewriteURL(directRequest, "?a=1&amp;b=2", mode));
    }

    public void testResourceRewrite() {

        final List<URLRewriterUtils.PathMatcher> pathMatchers = URLRewriterUtils.getMatchAllPathMatcher();
        final String version = Version.getVersion();

        // Test against request
        int mode = ExternalContext.Response.REWRITE_MODE_ABSOLUTE;
        assertEquals("https://foo.com/bar", URLRewriterUtils.rewriteResourceURL(directRequest, "https://foo.com/bar", pathMatchers , mode));
//        assertEquals("http://example.org/cool/service/relative/sub/path", URLRewriterUtils.rewriteResourceURL(request, "relative/sub/path", pathMatchers , mode));
        assertEquals("http://localhost:8080/orbeon/42/bar", URLRewriterUtils.rewriteResourceURL(directRequest, "/bar", pathMatchers , mode));
        assertEquals("http://localhost:8080/orbeon/42/bar?a=1&amp;b=2", URLRewriterUtils.rewriteResourceURL(directRequest, "/bar?a=1&amp;b=2", pathMatchers , mode));
        assertEquals("http://localhost:8080/orbeon/42/bar?a=1&amp;b=2#there", URLRewriterUtils.rewriteResourceURL(directRequest, "/bar?a=1&amp;b=2#there", pathMatchers , mode));
        assertEquals("http://localhost:8080/orbeon/42/doc/home-welcome?a=1&amp;b=2", URLRewriterUtils.rewriteResourceURL(directRequest, "?a=1&amp;b=2", pathMatchers , mode));
        assertEquals("http://localhost:8080/orbeon/" + version + "/ops/bar.png", URLRewriterUtils.rewriteResourceURL(directRequest, "/ops/bar.png", pathMatchers , mode));
        assertEquals("http://localhost:8080/orbeon/" + version + "/config/bar.png", URLRewriterUtils.rewriteResourceURL(directRequest, "/config/bar.png", pathMatchers , mode));

        mode = ExternalContext.Response.REWRITE_MODE_ABSOLUTE_PATH;
        assertEquals("https://foo.com/bar", URLRewriterUtils.rewriteResourceURL(directRequest, "https://foo.com/bar", pathMatchers , mode));
//        assertEquals("http://example.org/cool/service/relative/sub/path", URLRewriterUtils.rewriteResourceURL(request, "relative/sub/path", pathMatchers , mode));
        assertEquals("/orbeon/42/bar", URLRewriterUtils.rewriteResourceURL(directRequest, "/bar", pathMatchers , mode));
        assertEquals("/orbeon/42/bar?a=1&amp;b=2", URLRewriterUtils.rewriteResourceURL(directRequest, "/bar?a=1&amp;b=2", pathMatchers , mode));
        assertEquals("/orbeon/42/bar?a=1&amp;b=2#there", URLRewriterUtils.rewriteResourceURL(directRequest, "/bar?a=1&amp;b=2#there", pathMatchers , mode));
        assertEquals("/orbeon/42/doc/home-welcome?a=1&amp;b=2", URLRewriterUtils.rewriteResourceURL(directRequest, "?a=1&amp;b=2", pathMatchers , mode));
        assertEquals("/orbeon/" + version + "/ops/bar.png", URLRewriterUtils.rewriteResourceURL(directRequest, "/ops/bar.png", pathMatchers , mode));
        assertEquals("/orbeon/" + version + "/config/bar.png", URLRewriterUtils.rewriteResourceURL(directRequest, "/config/bar.png", pathMatchers , mode));

        mode = ExternalContext.Response.REWRITE_MODE_ABSOLUTE_PATH_NO_CONTEXT;
        assertEquals("https://foo.com/bar", URLRewriterUtils.rewriteResourceURL(directRequest, "https://foo.com/bar", pathMatchers , mode));
//        assertEquals("http://example.org/cool/service/relative/sub/path", URLRewriterUtils.rewriteResourceURL(request, "relative/sub/path", pathMatchers , mode));
        assertEquals("/42/bar", URLRewriterUtils.rewriteResourceURL(directRequest, "/bar", pathMatchers , mode));
        assertEquals("/42/bar?a=1&amp;b=2", URLRewriterUtils.rewriteResourceURL(directRequest, "/bar?a=1&amp;b=2", pathMatchers , mode));
        assertEquals("/42/bar?a=1&amp;b=2#there", URLRewriterUtils.rewriteResourceURL(directRequest, "/bar?a=1&amp;b=2#there", pathMatchers , mode));
        assertEquals("/42/doc/home-welcome?a=1&amp;b=2", URLRewriterUtils.rewriteResourceURL(directRequest, "?a=1&amp;b=2", pathMatchers , mode));
        assertEquals("/" + version + "/ops/bar.png", URLRewriterUtils.rewriteResourceURL(directRequest, "/ops/bar.png", pathMatchers , mode));
        assertEquals("/" + version + "/config/bar.png", URLRewriterUtils.rewriteResourceURL(directRequest, "/config/bar.png", pathMatchers , mode));

        mode = ExternalContext.Response.REWRITE_MODE_ABSOLUTE_PATH_OR_RELATIVE;
        assertEquals("https://foo.com/bar", URLRewriterUtils.rewriteResourceURL(directRequest, "https://foo.com/bar", pathMatchers , mode));
//        assertEquals("http://example.org/cool/service/relative/sub/path", URLRewriterUtils.rewriteResourceURL(request, "relative/sub/path", pathMatchers , mode));
        assertEquals("/orbeon/42/bar", URLRewriterUtils.rewriteResourceURL(directRequest, "/bar", pathMatchers , mode));
        assertEquals("/orbeon/42/bar?a=1&amp;b=2", URLRewriterUtils.rewriteResourceURL(directRequest, "/bar?a=1&amp;b=2", pathMatchers , mode));
        assertEquals("/orbeon/42/bar?a=1&amp;b=2#there", URLRewriterUtils.rewriteResourceURL(directRequest, "/bar?a=1&amp;b=2#there", pathMatchers , mode));
        assertEquals("/orbeon/42/doc/home-welcome?a=1&amp;b=2", URLRewriterUtils.rewriteResourceURL(directRequest, "?a=1&amp;b=2", pathMatchers , mode));
        assertEquals("/orbeon/" + version + "/ops/bar.png", URLRewriterUtils.rewriteResourceURL(directRequest, "/ops/bar.png", pathMatchers , mode));
        assertEquals("/orbeon/" + version + "/config/bar.png", URLRewriterUtils.rewriteResourceURL(directRequest, "/config/bar.png", pathMatchers , mode));
    }
    
    public void testServiceRewriteForward() {

        // Test with oxf.url-rewriting.service.base-uri is set to http://example.org/cool/service
        assertEquals("https://foo.com/bar", URLRewriterUtils.rewriteServiceURL(forwardRequest, "https://foo.com/bar", true));
//        assertEquals("http://example.org/cool/service/relative/sub/path", URLRewriterUtils.rewriteServiceURL(request, "relative/sub/path", true));
        assertEquals("http://example.org/cool/service/bar", URLRewriterUtils.rewriteServiceURL(forwardRequest, "/bar", true));
        assertEquals("http://example.org/cool/service/bar?a=1&amp;b=2", URLRewriterUtils.rewriteServiceURL(forwardRequest, "/bar?a=1&amp;b=2", true));
        assertEquals("http://example.org/cool/service/bar?a=1&amp;b=2#there", URLRewriterUtils.rewriteServiceURL(forwardRequest, "/bar?a=1&amp;b=2#there", true));
        assertEquals("http://example.org/cool/service?a=1&amp;b=2", URLRewriterUtils.rewriteServiceURL(forwardRequest, "?a=1&amp;b=2", true));

        // TODO: test with oxf.url-rewriting.service.base-uri set to absolute path
        // TODO: test without oxf.url-rewriting.service.base-uri set
    }

    public void testRewriteForward() {
        // Test against request
        int mode = ExternalContext.Response.REWRITE_MODE_ABSOLUTE;
        assertEquals("https://foo.com/bar", URLRewriterUtils.rewriteURL(forwardRequest, "https://foo.com/bar", mode));
//        assertEquals("http://example.org/cool/service/relative/sub/path", URLRewriterUtils.rewriteURL(request, "relative/sub/path", mode));
        assertEquals("http://localhost:8080/myapp/bar", URLRewriterUtils.rewriteURL(forwardRequest, "/bar", mode));
        assertEquals("http://localhost:8080/myapp/bar?a=1&amp;b=2", URLRewriterUtils.rewriteURL(forwardRequest, "/bar?a=1&amp;b=2", mode));
        assertEquals("http://localhost:8080/myapp/bar?a=1&amp;b=2#there", URLRewriterUtils.rewriteURL(forwardRequest, "/bar?a=1&amp;b=2#there", mode));
        assertEquals("http://localhost:8080/myapp/doc/home-welcome?a=1&amp;b=2", URLRewriterUtils.rewriteURL(forwardRequest, "?a=1&amp;b=2", mode));

        mode = ExternalContext.Response.REWRITE_MODE_ABSOLUTE_PATH;
        assertEquals("https://foo.com/bar", URLRewriterUtils.rewriteURL(forwardRequest, "https://foo.com/bar", mode));
//        assertEquals("http://example.org/cool/service/relative/sub/path", URLRewriterUtils.rewriteURL(request, "relative/sub/path", mode));
        assertEquals("/myapp/bar", URLRewriterUtils.rewriteURL(forwardRequest, "/bar", mode));
        assertEquals("/myapp/bar?a=1&amp;b=2", URLRewriterUtils.rewriteURL(forwardRequest, "/bar?a=1&amp;b=2", mode));
        assertEquals("/myapp/bar?a=1&amp;b=2#there", URLRewriterUtils.rewriteURL(forwardRequest, "/bar?a=1&amp;b=2#there", mode));
        assertEquals("/myapp/doc/home-welcome?a=1&amp;b=2", URLRewriterUtils.rewriteURL(forwardRequest, "?a=1&amp;b=2", mode));

        mode = ExternalContext.Response.REWRITE_MODE_ABSOLUTE_PATH_NO_CONTEXT;
        assertEquals("https://foo.com/bar", URLRewriterUtils.rewriteURL(forwardRequest, "https://foo.com/bar", mode));
//        assertEquals("http://example.org/cool/service/relative/sub/path", URLRewriterUtils.rewriteURL(request, "relative/sub/path", mode));
        assertEquals("/bar", URLRewriterUtils.rewriteURL(forwardRequest, "/bar", mode));
        assertEquals("/bar?a=1&amp;b=2", URLRewriterUtils.rewriteURL(forwardRequest, "/bar?a=1&amp;b=2", mode));
        assertEquals("/bar?a=1&amp;b=2#there", URLRewriterUtils.rewriteURL(forwardRequest, "/bar?a=1&amp;b=2#there", mode));
        assertEquals("/doc/home-welcome?a=1&amp;b=2", URLRewriterUtils.rewriteURL(forwardRequest, "?a=1&amp;b=2", mode));

        mode = ExternalContext.Response.REWRITE_MODE_ABSOLUTE_PATH_OR_RELATIVE;
        assertEquals("https://foo.com/bar", URLRewriterUtils.rewriteURL(forwardRequest, "https://foo.com/bar", mode));
//        assertEquals("http://example.org/cool/service/relative/sub/path", URLRewriterUtils.rewriteURL(request, "relative/sub/path", mode));
        assertEquals("/myapp/bar", URLRewriterUtils.rewriteURL(forwardRequest, "/bar", mode));
        assertEquals("/myapp/bar?a=1&amp;b=2", URLRewriterUtils.rewriteURL(forwardRequest, "/bar?a=1&amp;b=2", mode));
        assertEquals("/myapp/bar?a=1&amp;b=2#there", URLRewriterUtils.rewriteURL(forwardRequest, "/bar?a=1&amp;b=2#there", mode));
        assertEquals("/myapp/doc/home-welcome?a=1&amp;b=2", URLRewriterUtils.rewriteURL(forwardRequest, "?a=1&amp;b=2", mode));
    }

    public void testResourceRewriteForward() {

        final List<URLRewriterUtils.PathMatcher> pathMatchers = URLRewriterUtils.getMatchAllPathMatcher();
        final String version = Version.getVersion();

        // Test against request
        int mode = ExternalContext.Response.REWRITE_MODE_ABSOLUTE;
        assertEquals("https://foo.com/bar", URLRewriterUtils.rewriteResourceURL(forwardRequest, "https://foo.com/bar", pathMatchers , mode));
//        assertEquals("http://example.org/cool/service/relative/sub/path", URLRewriterUtils.rewriteResourceURL(request, "relative/sub/path", pathMatchers , mode));
        assertEquals("http://localhost:8080/myapp/42/bar", URLRewriterUtils.rewriteResourceURL(forwardRequest, "/bar", pathMatchers , mode));
        assertEquals("http://localhost:8080/myapp/42/bar?a=1&amp;b=2", URLRewriterUtils.rewriteResourceURL(forwardRequest, "/bar?a=1&amp;b=2", pathMatchers , mode));
        assertEquals("http://localhost:8080/myapp/42/bar?a=1&amp;b=2#there", URLRewriterUtils.rewriteResourceURL(forwardRequest, "/bar?a=1&amp;b=2#there", pathMatchers , mode));
        assertEquals("http://localhost:8080/myapp/42/doc/home-welcome?a=1&amp;b=2", URLRewriterUtils.rewriteResourceURL(forwardRequest, "?a=1&amp;b=2", pathMatchers , mode));
        assertEquals("http://localhost:8080/orbeon/" + version + "/ops/bar.png", URLRewriterUtils.rewriteResourceURL(forwardRequest, "/ops/bar.png", pathMatchers , mode));
        assertEquals("http://localhost:8080/orbeon/" + version + "/config/bar.png", URLRewriterUtils.rewriteResourceURL(forwardRequest, "/config/bar.png", pathMatchers , mode));

        mode = ExternalContext.Response.REWRITE_MODE_ABSOLUTE_PATH;
        assertEquals("https://foo.com/bar", URLRewriterUtils.rewriteResourceURL(forwardRequest, "https://foo.com/bar", pathMatchers , mode));
//        assertEquals("http://example.org/cool/service/relative/sub/path", URLRewriterUtils.rewriteResourceURL(request, "relative/sub/path", pathMatchers , mode));
        assertEquals("/myapp/42/bar", URLRewriterUtils.rewriteResourceURL(forwardRequest, "/bar", pathMatchers , mode));
        assertEquals("/myapp/42/bar?a=1&amp;b=2", URLRewriterUtils.rewriteResourceURL(forwardRequest, "/bar?a=1&amp;b=2", pathMatchers , mode));
        assertEquals("/myapp/42/bar?a=1&amp;b=2#there", URLRewriterUtils.rewriteResourceURL(forwardRequest, "/bar?a=1&amp;b=2#there", pathMatchers , mode));
        assertEquals("/myapp/42/doc/home-welcome?a=1&amp;b=2", URLRewriterUtils.rewriteResourceURL(forwardRequest, "?a=1&amp;b=2", pathMatchers , mode));
        assertEquals("/orbeon/" + version + "/ops/bar.png", URLRewriterUtils.rewriteResourceURL(forwardRequest, "/ops/bar.png", pathMatchers , mode));
        assertEquals("/orbeon/" + version + "/config/bar.png", URLRewriterUtils.rewriteResourceURL(forwardRequest, "/config/bar.png", pathMatchers , mode));

        mode = ExternalContext.Response.REWRITE_MODE_ABSOLUTE_PATH_NO_CONTEXT;
        assertEquals("https://foo.com/bar", URLRewriterUtils.rewriteResourceURL(forwardRequest, "https://foo.com/bar", pathMatchers , mode));
//        assertEquals("http://example.org/cool/service/relative/sub/path", URLRewriterUtils.rewriteResourceURL(request, "relative/sub/path", pathMatchers , mode));
        assertEquals("/42/bar", URLRewriterUtils.rewriteResourceURL(forwardRequest, "/bar", pathMatchers , mode));
        assertEquals("/42/bar?a=1&amp;b=2", URLRewriterUtils.rewriteResourceURL(forwardRequest, "/bar?a=1&amp;b=2", pathMatchers , mode));
        assertEquals("/42/bar?a=1&amp;b=2#there", URLRewriterUtils.rewriteResourceURL(forwardRequest, "/bar?a=1&amp;b=2#there", pathMatchers , mode));
        assertEquals("/42/doc/home-welcome?a=1&amp;b=2", URLRewriterUtils.rewriteResourceURL(forwardRequest, "?a=1&amp;b=2", pathMatchers , mode));
        assertEquals("/" + version + "/ops/bar.png", URLRewriterUtils.rewriteResourceURL(forwardRequest, "/ops/bar.png", pathMatchers , mode));
        assertEquals("/" + version + "/config/bar.png", URLRewriterUtils.rewriteResourceURL(forwardRequest, "/config/bar.png", pathMatchers , mode));

        mode = ExternalContext.Response.REWRITE_MODE_ABSOLUTE_PATH_OR_RELATIVE;
        assertEquals("https://foo.com/bar", URLRewriterUtils.rewriteResourceURL(forwardRequest, "https://foo.com/bar", pathMatchers , mode));
//        assertEquals("http://example.org/cool/service/relative/sub/path", URLRewriterUtils.rewriteResourceURL(request, "relative/sub/path", pathMatchers , mode));
        assertEquals("/myapp/42/bar", URLRewriterUtils.rewriteResourceURL(forwardRequest, "/bar", pathMatchers , mode));
        assertEquals("/myapp/42/bar?a=1&amp;b=2", URLRewriterUtils.rewriteResourceURL(forwardRequest, "/bar?a=1&amp;b=2", pathMatchers , mode));
        assertEquals("/myapp/42/bar?a=1&amp;b=2#there", URLRewriterUtils.rewriteResourceURL(forwardRequest, "/bar?a=1&amp;b=2#there", pathMatchers , mode));
        assertEquals("/myapp/42/doc/home-welcome?a=1&amp;b=2", URLRewriterUtils.rewriteResourceURL(forwardRequest, "?a=1&amp;b=2", pathMatchers , mode));
        assertEquals("/orbeon/" + version + "/ops/bar.png", URLRewriterUtils.rewriteResourceURL(forwardRequest, "/ops/bar.png", pathMatchers , mode));
        assertEquals("/orbeon/" + version + "/config/bar.png", URLRewriterUtils.rewriteResourceURL(forwardRequest, "/config/bar.png", pathMatchers , mode));
    }
    
    public void testResourceRewriteFilter() {

        final List<URLRewriterUtils.PathMatcher> pathMatchers = URLRewriterUtils.getMatchAllPathMatcher();
        final String version = Version.getVersion();

        // Test against request
        int mode = ExternalContext.Response.REWRITE_MODE_ABSOLUTE;
        assertEquals("https://foo.com/bar", URLRewriterUtils.rewriteResourceURL(filterRequest, "https://foo.com/bar", pathMatchers , mode));
//        assertEquals("http://example.org/cool/service/relative/sub/path", URLRewriterUtils.rewriteResourceURL(request, "relative/sub/path", pathMatchers , mode));
        assertEquals("http://localhost:8080/myapp/42/bar", URLRewriterUtils.rewriteResourceURL(filterRequest, "/bar", pathMatchers , mode));
        assertEquals("http://localhost:8080/myapp/42/bar?a=1&amp;b=2", URLRewriterUtils.rewriteResourceURL(filterRequest, "/bar?a=1&amp;b=2", pathMatchers , mode));
        assertEquals("http://localhost:8080/myapp/42/bar?a=1&amp;b=2#there", URLRewriterUtils.rewriteResourceURL(filterRequest, "/bar?a=1&amp;b=2#there", pathMatchers , mode));
        assertEquals("http://localhost:8080/myapp/42/doc/home-welcome?a=1&amp;b=2", URLRewriterUtils.rewriteResourceURL(filterRequest, "?a=1&amp;b=2", pathMatchers , mode));
        assertEquals("http://localhost:8080/myapp/orbeon/" + version + "/ops/bar.png", URLRewriterUtils.rewriteResourceURL(filterRequest, "/ops/bar.png", pathMatchers , mode));
        assertEquals("http://localhost:8080/myapp/orbeon/" + version + "/config/bar.png", URLRewriterUtils.rewriteResourceURL(filterRequest, "/config/bar.png", pathMatchers , mode));

        mode = ExternalContext.Response.REWRITE_MODE_ABSOLUTE_PATH;
        assertEquals("https://foo.com/bar", URLRewriterUtils.rewriteResourceURL(filterRequest, "https://foo.com/bar", pathMatchers , mode));
//        assertEquals("http://example.org/cool/service/relative/sub/path", URLRewriterUtils.rewriteResourceURL(request, "relative/sub/path", pathMatchers , mode));
        assertEquals("/myapp/42/bar", URLRewriterUtils.rewriteResourceURL(filterRequest, "/bar", pathMatchers , mode));
        assertEquals("/myapp/42/bar?a=1&amp;b=2", URLRewriterUtils.rewriteResourceURL(filterRequest, "/bar?a=1&amp;b=2", pathMatchers , mode));
        assertEquals("/myapp/42/bar?a=1&amp;b=2#there", URLRewriterUtils.rewriteResourceURL(filterRequest, "/bar?a=1&amp;b=2#there", pathMatchers , mode));
        assertEquals("/myapp/42/doc/home-welcome?a=1&amp;b=2", URLRewriterUtils.rewriteResourceURL(filterRequest, "?a=1&amp;b=2", pathMatchers , mode));
        assertEquals("/myapp/orbeon/" + version + "/ops/bar.png", URLRewriterUtils.rewriteResourceURL(filterRequest, "/ops/bar.png", pathMatchers , mode));
        assertEquals("/myapp/orbeon/" + version + "/config/bar.png", URLRewriterUtils.rewriteResourceURL(filterRequest, "/config/bar.png", pathMatchers , mode));

        mode = ExternalContext.Response.REWRITE_MODE_ABSOLUTE_PATH_NO_CONTEXT;
        assertEquals("https://foo.com/bar", URLRewriterUtils.rewriteResourceURL(filterRequest, "https://foo.com/bar", pathMatchers , mode));
//        assertEquals("http://example.org/cool/service/relative/sub/path", URLRewriterUtils.rewriteResourceURL(request, "relative/sub/path", pathMatchers , mode));
        assertEquals("/42/bar", URLRewriterUtils.rewriteResourceURL(filterRequest, "/bar", pathMatchers , mode));
        assertEquals("/42/bar?a=1&amp;b=2", URLRewriterUtils.rewriteResourceURL(filterRequest, "/bar?a=1&amp;b=2", pathMatchers , mode));
        assertEquals("/42/bar?a=1&amp;b=2#there", URLRewriterUtils.rewriteResourceURL(filterRequest, "/bar?a=1&amp;b=2#there", pathMatchers , mode));
        assertEquals("/42/doc/home-welcome?a=1&amp;b=2", URLRewriterUtils.rewriteResourceURL(filterRequest, "?a=1&amp;b=2", pathMatchers , mode));
        assertEquals("/" + version + "/ops/bar.png", URLRewriterUtils.rewriteResourceURL(filterRequest, "/ops/bar.png", pathMatchers , mode));
        assertEquals("/" + version + "/config/bar.png", URLRewriterUtils.rewriteResourceURL(filterRequest, "/config/bar.png", pathMatchers , mode));

        mode = ExternalContext.Response.REWRITE_MODE_ABSOLUTE_PATH_OR_RELATIVE;
        assertEquals("https://foo.com/bar", URLRewriterUtils.rewriteResourceURL(filterRequest, "https://foo.com/bar", pathMatchers , mode));
//        assertEquals("http://example.org/cool/service/relative/sub/path", URLRewriterUtils.rewriteResourceURL(request, "relative/sub/path", pathMatchers , mode));
        assertEquals("/myapp/42/bar", URLRewriterUtils.rewriteResourceURL(filterRequest, "/bar", pathMatchers , mode));
        assertEquals("/myapp/42/bar?a=1&amp;b=2", URLRewriterUtils.rewriteResourceURL(filterRequest, "/bar?a=1&amp;b=2", pathMatchers , mode));
        assertEquals("/myapp/42/bar?a=1&amp;b=2#there", URLRewriterUtils.rewriteResourceURL(filterRequest, "/bar?a=1&amp;b=2#there", pathMatchers , mode));
        assertEquals("/myapp/42/doc/home-welcome?a=1&amp;b=2", URLRewriterUtils.rewriteResourceURL(filterRequest, "?a=1&amp;b=2", pathMatchers , mode));
        assertEquals("/myapp/orbeon/" + version + "/ops/bar.png", URLRewriterUtils.rewriteResourceURL(filterRequest, "/ops/bar.png", pathMatchers , mode));
        assertEquals("/myapp/orbeon/" + version + "/config/bar.png", URLRewriterUtils.rewriteResourceURL(filterRequest, "/config/bar.png", pathMatchers , mode));
    }

//    public void testXMLBase() {
////        TODO: test XFormsUtils.resolveXMLBase()
//    }

//    public void testHRRI() {
////        TODO: test XFormsUtils.encodeHRRI()
//    }

//    public void testXFormsRewrite() {
//        // TODO
//        resolveRenderURL()
//        resolveServiceURL()
//        resolveResourceURL()
//    }
}
