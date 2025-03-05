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
package org.orbeon.oxf.util

import org.junit.Assert.*
import org.junit.{After, Before, Test}
import org.orbeon.oxf.externalcontext.{ExternalContext, TestExternalContext, URLRewriterImpl, UrlRewriteMode}
import org.orbeon.oxf.pipeline.api.PipelineContext
import org.orbeon.oxf.processor.ProcessorUtils
import org.orbeon.oxf.properties.Properties
import org.orbeon.oxf.test.ResourceManagerTestBase


class URLRewriterTest extends ResourceManagerTestBase {

  private var directPipelineContext: PipelineContext = null
  private var forwardPipelineContext: PipelineContext = null
  private var filterPipelineContext: PipelineContext = null

  private var directRequest: ExternalContext.Request = null
  private var forwardRequest: ExternalContext.Request = null
  private var filterRequest: ExternalContext.Request = null

  @Before override def setupResourceManagerTestPipelineContext(): Unit = {
    locally {
      directPipelineContext = new PipelineContext
      val requestDocument = ProcessorUtils.createDocumentFromURL("oxf:/org/orbeon/oxf/util/url-rewriter-test-request.xml", null)
      val externalContext = new TestExternalContext(directPipelineContext, requestDocument)
      directRequest = externalContext.getRequest
      // NOTE: PipelineContext is not really used by TestExternalContext in this test suite
      directPipelineContext.setAttribute(PipelineContext.EXTERNAL_CONTEXT, externalContext)
    }
    locally {
      forwardPipelineContext = new PipelineContext
      val requestDocument = ProcessorUtils.createDocumentFromURL("oxf:/org/orbeon/oxf/util/url-rewriter-test-request-forward.xml", null)
      val externalContext = new TestExternalContext(forwardPipelineContext, requestDocument)
      forwardRequest = externalContext.getRequest
      // NOTE: PipelineContext is not really used by TestExternalContext in this test suite
      forwardPipelineContext.setAttribute(PipelineContext.EXTERNAL_CONTEXT, externalContext)
    }
    locally {
      filterPipelineContext = new PipelineContext
      val requestDocument = ProcessorUtils.createDocumentFromURL("oxf:/org/orbeon/oxf/util/url-rewriter-test-request-filter.xml", null)
      val externalContext = new TestExternalContext(filterPipelineContext, requestDocument)
      filterRequest = externalContext.getRequest
      // NOTE: PipelineContext is not really used by TestExternalContext in this test suite
      filterPipelineContext.setAttribute(PipelineContext.EXTERNAL_CONTEXT, externalContext)
    }

    // Reinitialize properties to enable versioned resources
    Properties.invalidate()
    org.orbeon.oxf.properties.Properties.init("oxf:/ops/unit-tests/properties-versioned-all.xml")
  }

  @After override def tearDownResourceManagerTestPipelineContext(): Unit = {
    directPipelineContext.destroy(true)
    forwardPipelineContext.destroy(true)
    filterPipelineContext.destroy(true)
  }

  @Test def testServiceRewrite(): Unit = {
    val baseURIProperty = URLRewriterUtils.getServiceBaseURI
    // Test with oxf.url-rewriting.service.base-uri is set to http://example.org/cool/service
    assertEquals("https://foo.com/bar", URLRewriterImpl.rewriteServiceUrl(directRequest, "https://foo.com/bar", UrlRewriteMode.Absolute, baseURIProperty))
    //        assertEquals("http://example.org/cool/service/relative/sub/path", URLRewriterImpl.rewriteServiceURL(request, "relative/sub/path", UrlRewriteMode.Absolute));
    assertEquals("http://example.org/cool/service/bar", URLRewriterImpl.rewriteServiceUrl(directRequest, "/bar", UrlRewriteMode.Absolute, baseURIProperty))
    assertEquals("http://example.org/cool/service/bar?a=1&amp;b=2", URLRewriterImpl.rewriteServiceUrl(directRequest, "/bar?a=1&amp;b=2", UrlRewriteMode.Absolute, baseURIProperty))
    assertEquals("http://example.org/cool/service/bar?a=1&amp;b=2#there", URLRewriterImpl.rewriteServiceUrl(directRequest, "/bar?a=1&amp;b=2#there", UrlRewriteMode.Absolute, baseURIProperty))
    assertEquals("http://example.org/cool/service?a=1&amp;b=2", URLRewriterImpl.rewriteServiceUrl(directRequest, "?a=1&amp;b=2", UrlRewriteMode.Absolute, baseURIProperty))
    assertEquals("https://foo.com/bar", URLRewriterImpl.rewriteServiceUrl(directRequest, "https://foo.com/bar", UrlRewriteMode.AbsoluteNoContext, baseURIProperty))
    //        assertEquals("http://example.org/cool/service/relative/sub/path", URLRewriterImpl.rewriteServiceURL(request, "relative/sub/path", UrlRewriteMode.AbsoluteNoContext));
    assertEquals("http://example.org/bar", URLRewriterImpl.rewriteServiceUrl(directRequest, "/bar", UrlRewriteMode.AbsoluteNoContext, baseURIProperty))
    assertEquals("http://example.org/bar?a=1&amp;b=2", URLRewriterImpl.rewriteServiceUrl(directRequest, "/bar?a=1&amp;b=2", UrlRewriteMode.AbsoluteNoContext, baseURIProperty))
    assertEquals("http://example.org/bar?a=1&amp;b=2#there", URLRewriterImpl.rewriteServiceUrl(directRequest, "/bar?a=1&amp;b=2#there", UrlRewriteMode.AbsoluteNoContext, baseURIProperty))
    // NOTE: Ideally should have a "/" between host name and query
    assertEquals("http://example.org?a=1&amp;b=2", URLRewriterImpl.rewriteServiceUrl(directRequest, "?a=1&amp;b=2", UrlRewriteMode.AbsoluteNoContext, baseURIProperty))
    // TODO: test with oxf.url-rewriting.service.base-uri set to absolute path
    // TODO: test without oxf.url-rewriting.service.base-uri set
  }

  @Test def testRewrite(): Unit = {
    // Test against request
    var mode: UrlRewriteMode = UrlRewriteMode.Absolute
    assertEquals("https://foo.com/bar", URLRewriterImpl.rewriteURL(directRequest, "https://foo.com/bar", mode))
    //        assertEquals("http://example.org/cool/service/relative/sub/path", URLRewriterImpl.rewriteURL(request, "relative/sub/path", mode));
    assertEquals("http://localhost:8080/orbeon/bar", URLRewriterImpl.rewriteURL(directRequest, "/bar", mode))
    assertEquals("http://localhost:8080/orbeon/bar?a=1&amp;b=2", URLRewriterImpl.rewriteURL(directRequest, "/bar?a=1&amp;b=2", mode))
    assertEquals("http://localhost:8080/orbeon/bar?a=1&amp;b=2#there", URLRewriterImpl.rewriteURL(directRequest, "/bar?a=1&amp;b=2#there", mode))
    assertEquals("http://localhost:8080/orbeon/doc/home-welcome?a=1&amp;b=2", URLRewriterImpl.rewriteURL(directRequest, "?a=1&amp;b=2", mode))
    mode = UrlRewriteMode.AbsolutePath
    assertEquals("https://foo.com/bar", URLRewriterImpl.rewriteURL(directRequest, "https://foo.com/bar", mode))
    //        assertEquals("http://example.org/cool/service/relative/sub/path", URLRewriterImpl.rewriteURL(request, "relative/sub/path", mode));
    assertEquals("/orbeon/bar", URLRewriterImpl.rewriteURL(directRequest, "/bar", mode))
    assertEquals("/orbeon/bar?a=1&amp;b=2", URLRewriterImpl.rewriteURL(directRequest, "/bar?a=1&amp;b=2", mode))
    assertEquals("/orbeon/bar?a=1&amp;b=2#there", URLRewriterImpl.rewriteURL(directRequest, "/bar?a=1&amp;b=2#there", mode))
    assertEquals("/orbeon/doc/home-welcome?a=1&amp;b=2", URLRewriterImpl.rewriteURL(directRequest, "?a=1&amp;b=2", mode))
    mode = UrlRewriteMode.AbsolutePathNoContext
    assertEquals("https://foo.com/bar", URLRewriterImpl.rewriteURL(directRequest, "https://foo.com/bar", mode))
    //        assertEquals("http://example.org/cool/service/relative/sub/path", URLRewriterImpl.rewriteURL(request, "relative/sub/path", mode));
    assertEquals("/bar", URLRewriterImpl.rewriteURL(directRequest, "/bar", mode))
    assertEquals("/bar?a=1&amp;b=2", URLRewriterImpl.rewriteURL(directRequest, "/bar?a=1&amp;b=2", mode))
    assertEquals("/bar?a=1&amp;b=2#there", URLRewriterImpl.rewriteURL(directRequest, "/bar?a=1&amp;b=2#there", mode))
    assertEquals("/doc/home-welcome?a=1&amp;b=2", URLRewriterImpl.rewriteURL(directRequest, "?a=1&amp;b=2", mode))
    mode = UrlRewriteMode.AbsolutePathOrRelative
    assertEquals("https://foo.com/bar", URLRewriterImpl.rewriteURL(directRequest, "https://foo.com/bar", mode))
    //        assertEquals("http://example.org/cool/service/relative/sub/path", URLRewriterImpl.rewriteURL(request, "relative/sub/path", mode));
    assertEquals("/orbeon/bar", URLRewriterImpl.rewriteURL(directRequest, "/bar", mode))
    assertEquals("/orbeon/bar?a=1&amp;b=2", URLRewriterImpl.rewriteURL(directRequest, "/bar?a=1&amp;b=2", mode))
    assertEquals("/orbeon/bar?a=1&amp;b=2#there", URLRewriterImpl.rewriteURL(directRequest, "/bar?a=1&amp;b=2#there", mode))
    assertEquals("/orbeon/doc/home-welcome?a=1&amp;b=2", URLRewriterImpl.rewriteURL(directRequest, "?a=1&amp;b=2", mode))
  }

  @Test def testResourceRewrite(): Unit = {
    val pathMatchers = URLRewriterUtils.MATCH_ALL_PATH_MATCHERS
    val version = URLRewriterUtils.getOrbeonVersionForClient
    // Test against request
    var mode: UrlRewriteMode = UrlRewriteMode.Absolute
    assertEquals("https://foo.com/bar", URLRewriterUtils.rewriteResourceURL(directRequest, "https://foo.com/bar", pathMatchers, mode))
    //        assertEquals("http://example.org/cool/service/relative/sub/path", URLRewriterUtils.rewriteResourceURL(request, "relative/sub/path", pathMatchers , mode));
    assertEquals("http://localhost:8080/orbeon/42/bar", URLRewriterUtils.rewriteResourceURL(directRequest, "/bar", pathMatchers, mode))
    assertEquals("http://localhost:8080/orbeon/42/bar?a=1&amp;b=2", URLRewriterUtils.rewriteResourceURL(directRequest, "/bar?a=1&amp;b=2", pathMatchers, mode))
    assertEquals("http://localhost:8080/orbeon/42/bar?a=1&amp;b=2#there", URLRewriterUtils.rewriteResourceURL(directRequest, "/bar?a=1&amp;b=2#there", pathMatchers, mode))
    assertEquals("http://localhost:8080/orbeon/42/doc/home-welcome?a=1&amp;b=2", URLRewriterUtils.rewriteResourceURL(directRequest, "?a=1&amp;b=2", pathMatchers, mode))
    assertEquals("http://localhost:8080/orbeon/" + version + "/ops/bar.png", URLRewriterUtils.rewriteResourceURL(directRequest, "/ops/bar.png", pathMatchers, mode))
    assertEquals("http://localhost:8080/orbeon/" + version + "/config/bar.png", URLRewriterUtils.rewriteResourceURL(directRequest, "/config/bar.png", pathMatchers, mode))
    mode = UrlRewriteMode.AbsolutePath
    assertEquals("https://foo.com/bar", URLRewriterUtils.rewriteResourceURL(directRequest, "https://foo.com/bar", pathMatchers, mode))
    //        assertEquals("http://example.org/cool/service/relative/sub/path", URLRewriterUtils.rewriteResourceURL(request, "relative/sub/path", pathMatchers , mode));
    assertEquals("/orbeon/42/bar", URLRewriterUtils.rewriteResourceURL(directRequest, "/bar", pathMatchers, mode))
    assertEquals("/orbeon/42/bar?a=1&amp;b=2", URLRewriterUtils.rewriteResourceURL(directRequest, "/bar?a=1&amp;b=2", pathMatchers, mode))
    assertEquals("/orbeon/42/bar?a=1&amp;b=2#there", URLRewriterUtils.rewriteResourceURL(directRequest, "/bar?a=1&amp;b=2#there", pathMatchers, mode))
    assertEquals("/orbeon/42/doc/home-welcome?a=1&amp;b=2", URLRewriterUtils.rewriteResourceURL(directRequest, "?a=1&amp;b=2", pathMatchers, mode))
    assertEquals("/orbeon/" + version + "/ops/bar.png", URLRewriterUtils.rewriteResourceURL(directRequest, "/ops/bar.png", pathMatchers, mode))
    assertEquals("/orbeon/" + version + "/config/bar.png", URLRewriterUtils.rewriteResourceURL(directRequest, "/config/bar.png", pathMatchers, mode))
    mode = UrlRewriteMode.AbsolutePathNoContext
    assertEquals("https://foo.com/bar", URLRewriterUtils.rewriteResourceURL(directRequest, "https://foo.com/bar", pathMatchers, mode))
    //        assertEquals("http://example.org/cool/service/relative/sub/path", URLRewriterUtils.rewriteResourceURL(request, "relative/sub/path", pathMatchers , mode));
    assertEquals("/42/bar", URLRewriterUtils.rewriteResourceURL(directRequest, "/bar", pathMatchers, mode))
    assertEquals("/42/bar?a=1&amp;b=2", URLRewriterUtils.rewriteResourceURL(directRequest, "/bar?a=1&amp;b=2", pathMatchers, mode))
    assertEquals("/42/bar?a=1&amp;b=2#there", URLRewriterUtils.rewriteResourceURL(directRequest, "/bar?a=1&amp;b=2#there", pathMatchers, mode))
    assertEquals("/42/doc/home-welcome?a=1&amp;b=2", URLRewriterUtils.rewriteResourceURL(directRequest, "?a=1&amp;b=2", pathMatchers, mode))
    assertEquals("/" + version + "/ops/bar.png", URLRewriterUtils.rewriteResourceURL(directRequest, "/ops/bar.png", pathMatchers, mode))
    assertEquals("/" + version + "/config/bar.png", URLRewriterUtils.rewriteResourceURL(directRequest, "/config/bar.png", pathMatchers, mode))
    mode = UrlRewriteMode.AbsolutePathOrRelative
    assertEquals("https://foo.com/bar", URLRewriterUtils.rewriteResourceURL(directRequest, "https://foo.com/bar", pathMatchers, mode))
    //        assertEquals("http://example.org/cool/service/relative/sub/path", URLRewriterUtils.rewriteResourceURL(request, "relative/sub/path", pathMatchers , mode));
    assertEquals("/orbeon/42/bar", URLRewriterUtils.rewriteResourceURL(directRequest, "/bar", pathMatchers, mode))
    assertEquals("/orbeon/42/bar?a=1&amp;b=2", URLRewriterUtils.rewriteResourceURL(directRequest, "/bar?a=1&amp;b=2", pathMatchers, mode))
    assertEquals("/orbeon/42/bar?a=1&amp;b=2#there", URLRewriterUtils.rewriteResourceURL(directRequest, "/bar?a=1&amp;b=2#there", pathMatchers, mode))
    assertEquals("/orbeon/42/doc/home-welcome?a=1&amp;b=2", URLRewriterUtils.rewriteResourceURL(directRequest, "?a=1&amp;b=2", pathMatchers, mode))
    assertEquals("/orbeon/" + version + "/ops/bar.png", URLRewriterUtils.rewriteResourceURL(directRequest, "/ops/bar.png", pathMatchers, mode))
    assertEquals("/orbeon/" + version + "/config/bar.png", URLRewriterUtils.rewriteResourceURL(directRequest, "/config/bar.png", pathMatchers, mode))
    mode = UrlRewriteMode.AbsolutePathNoPrefix
    assertEquals("https://foo.com/bar", URLRewriterUtils.rewriteResourceURL(directRequest, "https://foo.com/bar", pathMatchers, mode))
    //        assertEquals("http://example.org/cool/service/relative/sub/path", URLRewriterUtils.rewriteResourceURL(request, "relative/sub/path", pathMatchers , mode));
    assertEquals("/bar", URLRewriterUtils.rewriteResourceURL(directRequest, "/bar", pathMatchers, mode))
    assertEquals("/bar?a=1&amp;b=2", URLRewriterUtils.rewriteResourceURL(directRequest, "/bar?a=1&amp;b=2", pathMatchers, mode))
    assertEquals("/bar?a=1&amp;b=2#there", URLRewriterUtils.rewriteResourceURL(directRequest, "/bar?a=1&amp;b=2#there", pathMatchers, mode))
    assertEquals("/doc/home-welcome?a=1&amp;b=2", URLRewriterUtils.rewriteResourceURL(directRequest, "?a=1&amp;b=2", pathMatchers, mode))
    assertEquals("/ops/bar.png", URLRewriterUtils.rewriteResourceURL(directRequest, "/ops/bar.png", pathMatchers, mode))
    assertEquals("/config/bar.png", URLRewriterUtils.rewriteResourceURL(directRequest, "/config/bar.png", pathMatchers, mode))
  }

  @Test def testServiceRewriteForward(): Unit = {
    val baseURIProperty = URLRewriterUtils.getServiceBaseURI
    // Test with oxf.url-rewriting.service.base-uri is set to http://example.org/cool/service
    assertEquals("https://foo.com/bar", URLRewriterImpl.rewriteServiceUrl(forwardRequest, "https://foo.com/bar", UrlRewriteMode.Absolute, baseURIProperty))
    //        assertEquals("http://example.org/cool/service/relative/sub/path", URLRewriterImpl.rewriteServiceURL(request, "relative/sub/path", UrlRewriteMode.Absolute));
    assertEquals("http://example.org/cool/service/bar", URLRewriterImpl.rewriteServiceUrl(forwardRequest, "/bar", UrlRewriteMode.Absolute, baseURIProperty))
    assertEquals("http://example.org/cool/service/bar?a=1&amp;b=2", URLRewriterImpl.rewriteServiceUrl(forwardRequest, "/bar?a=1&amp;b=2", UrlRewriteMode.Absolute, baseURIProperty))
    assertEquals("http://example.org/cool/service/bar?a=1&amp;b=2#there", URLRewriterImpl.rewriteServiceUrl(forwardRequest, "/bar?a=1&amp;b=2#there", UrlRewriteMode.Absolute, baseURIProperty))
    assertEquals("http://example.org/cool/service?a=1&amp;b=2", URLRewriterImpl.rewriteServiceUrl(forwardRequest, "?a=1&amp;b=2", UrlRewriteMode.Absolute, baseURIProperty))
    assertEquals("https://foo.com/bar", URLRewriterImpl.rewriteServiceUrl(forwardRequest, "https://foo.com/bar", UrlRewriteMode.AbsoluteNoContext, baseURIProperty))
    //        assertEquals("http://example.org/cool/service/relative/sub/path", URLRewriterImpl.rewriteServiceURL(request, "relative/sub/path", UrlRewriteMode.AbsoluteNoContext));
    assertEquals("http://example.org/bar", URLRewriterImpl.rewriteServiceUrl(forwardRequest, "/bar", UrlRewriteMode.AbsoluteNoContext, baseURIProperty))
    assertEquals("http://example.org/bar?a=1&amp;b=2", URLRewriterImpl.rewriteServiceUrl(forwardRequest, "/bar?a=1&amp;b=2", UrlRewriteMode.AbsoluteNoContext, baseURIProperty))
    assertEquals("http://example.org/bar?a=1&amp;b=2#there", URLRewriterImpl.rewriteServiceUrl(forwardRequest, "/bar?a=1&amp;b=2#there", UrlRewriteMode.AbsoluteNoContext, baseURIProperty))
    // NOTE: Ideally should have a "/" between host name and query
    assertEquals("http://example.org?a=1&amp;b=2", URLRewriterImpl.rewriteServiceUrl(forwardRequest, "?a=1&amp;b=2", UrlRewriteMode.AbsoluteNoContext, baseURIProperty))
    // TODO: test with oxf.url-rewriting.service.base-uri set to absolute path
    // TODO: test without oxf.url-rewriting.service.base-uri set
  }

  @Test def testRewriteForward(): Unit = {
    // Test against request
    var mode: UrlRewriteMode = UrlRewriteMode.Absolute
    assertEquals("https://foo.com/bar", URLRewriterImpl.rewriteURL(forwardRequest, "https://foo.com/bar", mode))
    //        assertEquals("http://example.org/cool/service/relative/sub/path", URLRewriterImpl.rewriteURL(request, "relative/sub/path", mode));
    assertEquals("http://localhost:8080/myapp/bar", URLRewriterImpl.rewriteURL(forwardRequest, "/bar", mode))
    assertEquals("http://localhost:8080/myapp/bar?a=1&amp;b=2", URLRewriterImpl.rewriteURL(forwardRequest, "/bar?a=1&amp;b=2", mode))
    assertEquals("http://localhost:8080/myapp/bar?a=1&amp;b=2#there", URLRewriterImpl.rewriteURL(forwardRequest, "/bar?a=1&amp;b=2#there", mode))
    assertEquals("http://localhost:8080/myapp/doc/home-welcome?a=1&amp;b=2", URLRewriterImpl.rewriteURL(forwardRequest, "?a=1&amp;b=2", mode))
    mode = UrlRewriteMode.AbsolutePath
    assertEquals("https://foo.com/bar", URLRewriterImpl.rewriteURL(forwardRequest, "https://foo.com/bar", mode))
    //        assertEquals("http://example.org/cool/service/relative/sub/path", URLRewriterImpl.rewriteURL(request, "relative/sub/path", mode));
    assertEquals("/myapp/bar", URLRewriterImpl.rewriteURL(forwardRequest, "/bar", mode))
    assertEquals("/myapp/bar?a=1&amp;b=2", URLRewriterImpl.rewriteURL(forwardRequest, "/bar?a=1&amp;b=2", mode))
    assertEquals("/myapp/bar?a=1&amp;b=2#there", URLRewriterImpl.rewriteURL(forwardRequest, "/bar?a=1&amp;b=2#there", mode))
    assertEquals("/myapp/doc/home-welcome?a=1&amp;b=2", URLRewriterImpl.rewriteURL(forwardRequest, "?a=1&amp;b=2", mode))
    mode = UrlRewriteMode.AbsolutePathNoContext
    assertEquals("https://foo.com/bar", URLRewriterImpl.rewriteURL(forwardRequest, "https://foo.com/bar", mode))
    //        assertEquals("http://example.org/cool/service/relative/sub/path", URLRewriterImpl.rewriteURL(request, "relative/sub/path", mode));
    assertEquals("/bar", URLRewriterImpl.rewriteURL(forwardRequest, "/bar", mode))
    assertEquals("/bar?a=1&amp;b=2", URLRewriterImpl.rewriteURL(forwardRequest, "/bar?a=1&amp;b=2", mode))
    assertEquals("/bar?a=1&amp;b=2#there", URLRewriterImpl.rewriteURL(forwardRequest, "/bar?a=1&amp;b=2#there", mode))
    assertEquals("/doc/home-welcome?a=1&amp;b=2", URLRewriterImpl.rewriteURL(forwardRequest, "?a=1&amp;b=2", mode))
    mode = UrlRewriteMode.AbsolutePathOrRelative
    assertEquals("https://foo.com/bar", URLRewriterImpl.rewriteURL(forwardRequest, "https://foo.com/bar", mode))
    //        assertEquals("http://example.org/cool/service/relative/sub/path", URLRewriterImpl.rewriteURL(request, "relative/sub/path", mode));
    assertEquals("/myapp/bar", URLRewriterImpl.rewriteURL(forwardRequest, "/bar", mode))
    assertEquals("/myapp/bar?a=1&amp;b=2", URLRewriterImpl.rewriteURL(forwardRequest, "/bar?a=1&amp;b=2", mode))
    assertEquals("/myapp/bar?a=1&amp;b=2#there", URLRewriterImpl.rewriteURL(forwardRequest, "/bar?a=1&amp;b=2#there", mode))
    assertEquals("/myapp/doc/home-welcome?a=1&amp;b=2", URLRewriterImpl.rewriteURL(forwardRequest, "?a=1&amp;b=2", mode))
  }

  @Test def testResourceRewriteForward(): Unit = {
    val pathMatchers = URLRewriterUtils.MATCH_ALL_PATH_MATCHERS
    val version = URLRewriterUtils.getOrbeonVersionForClient
    // Test against request
    var mode: UrlRewriteMode = UrlRewriteMode.Absolute
    assertEquals("https://foo.com/bar", URLRewriterUtils.rewriteResourceURL(forwardRequest, "https://foo.com/bar", pathMatchers, mode))
    //        assertEquals("http://example.org/cool/service/relative/sub/path", URLRewriterUtils.rewriteResourceURL(request, "relative/sub/path", pathMatchers , mode));
    assertEquals("http://localhost:8080/myapp/bar", URLRewriterUtils.rewriteResourceURL(forwardRequest, "/bar", pathMatchers, mode))
    assertEquals("http://localhost:8080/myapp/bar?a=1&amp;b=2", URLRewriterUtils.rewriteResourceURL(forwardRequest, "/bar?a=1&amp;b=2", pathMatchers, mode))
    assertEquals("http://localhost:8080/myapp/bar?a=1&amp;b=2#there", URLRewriterUtils.rewriteResourceURL(forwardRequest, "/bar?a=1&amp;b=2#there", pathMatchers, mode))
    assertEquals("http://localhost:8080/myapp/doc/home-welcome?a=1&amp;b=2", URLRewriterUtils.rewriteResourceURL(forwardRequest, "?a=1&amp;b=2", pathMatchers, mode))
    assertEquals("http://localhost:8080/myapp/orbeon/" + version + "/ops/bar.png", URLRewriterUtils.rewriteResourceURL(forwardRequest, "/ops/bar.png", pathMatchers, mode))
    assertEquals("http://localhost:8080/myapp/orbeon/" + version + "/config/bar.png", URLRewriterUtils.rewriteResourceURL(forwardRequest, "/config/bar.png", pathMatchers, mode))
    mode = UrlRewriteMode.AbsolutePath
    assertEquals("https://foo.com/bar", URLRewriterUtils.rewriteResourceURL(forwardRequest, "https://foo.com/bar", pathMatchers, mode))
    //        assertEquals("http://example.org/cool/service/relative/sub/path", URLRewriterUtils.rewriteResourceURL(request, "relative/sub/path", pathMatchers , mode));
    assertEquals("/myapp/bar", URLRewriterUtils.rewriteResourceURL(forwardRequest, "/bar", pathMatchers, mode))
    assertEquals("/myapp/bar?a=1&amp;b=2", URLRewriterUtils.rewriteResourceURL(forwardRequest, "/bar?a=1&amp;b=2", pathMatchers, mode))
    assertEquals("/myapp/bar?a=1&amp;b=2#there", URLRewriterUtils.rewriteResourceURL(forwardRequest, "/bar?a=1&amp;b=2#there", pathMatchers, mode))
    assertEquals("/myapp/doc/home-welcome?a=1&amp;b=2", URLRewriterUtils.rewriteResourceURL(forwardRequest, "?a=1&amp;b=2", pathMatchers, mode))
    assertEquals("/myapp/orbeon/" + version + "/ops/bar.png", URLRewriterUtils.rewriteResourceURL(forwardRequest, "/ops/bar.png", pathMatchers, mode))
    assertEquals("/myapp/orbeon/" + version + "/config/bar.png", URLRewriterUtils.rewriteResourceURL(forwardRequest, "/config/bar.png", pathMatchers, mode))
    mode = UrlRewriteMode.AbsolutePathNoContext
    assertEquals("https://foo.com/bar", URLRewriterUtils.rewriteResourceURL(forwardRequest, "https://foo.com/bar", pathMatchers, mode))
    //        assertEquals("http://example.org/cool/service/relative/sub/path", URLRewriterUtils.rewriteResourceURL(request, "relative/sub/path", pathMatchers , mode));
    assertEquals("/bar", URLRewriterUtils.rewriteResourceURL(forwardRequest, "/bar", pathMatchers, mode))
    assertEquals("/bar?a=1&amp;b=2", URLRewriterUtils.rewriteResourceURL(forwardRequest, "/bar?a=1&amp;b=2", pathMatchers, mode))
    assertEquals("/bar?a=1&amp;b=2#there", URLRewriterUtils.rewriteResourceURL(forwardRequest, "/bar?a=1&amp;b=2#there", pathMatchers, mode))
    assertEquals("/doc/home-welcome?a=1&amp;b=2", URLRewriterUtils.rewriteResourceURL(forwardRequest, "?a=1&amp;b=2", pathMatchers, mode))
    assertEquals("/" + version + "/ops/bar.png", URLRewriterUtils.rewriteResourceURL(forwardRequest, "/ops/bar.png", pathMatchers, mode))
    assertEquals("/" + version + "/config/bar.png", URLRewriterUtils.rewriteResourceURL(forwardRequest, "/config/bar.png", pathMatchers, mode))
    mode = UrlRewriteMode.AbsolutePathOrRelative
    assertEquals("https://foo.com/bar", URLRewriterUtils.rewriteResourceURL(forwardRequest, "https://foo.com/bar", pathMatchers, mode))
    //        assertEquals("http://example.org/cool/service/relative/sub/path", URLRewriterUtils.rewriteResourceURL(request, "relative/sub/path", pathMatchers , mode));
    assertEquals("/myapp/bar", URLRewriterUtils.rewriteResourceURL(forwardRequest, "/bar", pathMatchers, mode))
    assertEquals("/myapp/bar?a=1&amp;b=2", URLRewriterUtils.rewriteResourceURL(forwardRequest, "/bar?a=1&amp;b=2", pathMatchers, mode))
    assertEquals("/myapp/bar?a=1&amp;b=2#there", URLRewriterUtils.rewriteResourceURL(forwardRequest, "/bar?a=1&amp;b=2#there", pathMatchers, mode))
    assertEquals("/myapp/doc/home-welcome?a=1&amp;b=2", URLRewriterUtils.rewriteResourceURL(forwardRequest, "?a=1&amp;b=2", pathMatchers, mode))
    assertEquals("/myapp/orbeon/" + version + "/ops/bar.png", URLRewriterUtils.rewriteResourceURL(forwardRequest, "/ops/bar.png", pathMatchers, mode))
    assertEquals("/myapp/orbeon/" + version + "/config/bar.png", URLRewriterUtils.rewriteResourceURL(forwardRequest, "/config/bar.png", pathMatchers, mode))
  }

  @Test def testResourceRewriteFilter(): Unit = {
    val pathMatchers = URLRewriterUtils.MATCH_ALL_PATH_MATCHERS
    val version = URLRewriterUtils.getOrbeonVersionForClient
    // Test against request
    var mode: UrlRewriteMode = UrlRewriteMode.Absolute
    assertEquals("https://foo.com/bar", URLRewriterUtils.rewriteResourceURL(filterRequest, "https://foo.com/bar", pathMatchers, mode))
    //        assertEquals("http://example.org/cool/service/relative/sub/path", URLRewriterUtils.rewriteResourceURL(request, "relative/sub/path", pathMatchers , mode));
    assertEquals("http://localhost:8080/myapp/bar", URLRewriterUtils.rewriteResourceURL(filterRequest, "/bar", pathMatchers, mode))
    assertEquals("http://localhost:8080/myapp/bar?a=1&amp;b=2", URLRewriterUtils.rewriteResourceURL(filterRequest, "/bar?a=1&amp;b=2", pathMatchers, mode))
    assertEquals("http://localhost:8080/myapp/bar?a=1&amp;b=2#there", URLRewriterUtils.rewriteResourceURL(filterRequest, "/bar?a=1&amp;b=2#there", pathMatchers, mode))
    assertEquals("http://localhost:8080/myapp/doc/home-welcome?a=1&amp;b=2", URLRewriterUtils.rewriteResourceURL(filterRequest, "?a=1&amp;b=2", pathMatchers, mode))
    assertEquals("http://localhost:8080/myapp/orbeon/" + version + "/ops/bar.png", URLRewriterUtils.rewriteResourceURL(filterRequest, "/ops/bar.png", pathMatchers, mode))
    assertEquals("http://localhost:8080/myapp/orbeon/" + version + "/config/bar.png", URLRewriterUtils.rewriteResourceURL(filterRequest, "/config/bar.png", pathMatchers, mode))
    mode = UrlRewriteMode.AbsolutePath
    assertEquals("https://foo.com/bar", URLRewriterUtils.rewriteResourceURL(filterRequest, "https://foo.com/bar", pathMatchers, mode))
    //        assertEquals("http://example.org/cool/service/relative/sub/path", URLRewriterUtils.rewriteResourceURL(request, "relative/sub/path", pathMatchers , mode));
    assertEquals("/myapp/bar", URLRewriterUtils.rewriteResourceURL(filterRequest, "/bar", pathMatchers, mode))
    assertEquals("/myapp/bar?a=1&amp;b=2", URLRewriterUtils.rewriteResourceURL(filterRequest, "/bar?a=1&amp;b=2", pathMatchers, mode))
    assertEquals("/myapp/bar?a=1&amp;b=2#there", URLRewriterUtils.rewriteResourceURL(filterRequest, "/bar?a=1&amp;b=2#there", pathMatchers, mode))
    assertEquals("/myapp/doc/home-welcome?a=1&amp;b=2", URLRewriterUtils.rewriteResourceURL(filterRequest, "?a=1&amp;b=2", pathMatchers, mode))
    assertEquals("/myapp/orbeon/" + version + "/ops/bar.png", URLRewriterUtils.rewriteResourceURL(filterRequest, "/ops/bar.png", pathMatchers, mode))
    assertEquals("/myapp/orbeon/" + version + "/config/bar.png", URLRewriterUtils.rewriteResourceURL(filterRequest, "/config/bar.png", pathMatchers, mode))
    mode = UrlRewriteMode.AbsolutePathNoContext
    assertEquals("https://foo.com/bar", URLRewriterUtils.rewriteResourceURL(filterRequest, "https://foo.com/bar", pathMatchers, mode))
    //        assertEquals("http://example.org/cool/service/relative/sub/path", URLRewriterUtils.rewriteResourceURL(request, "relative/sub/path", pathMatchers , mode));
    assertEquals("/bar", URLRewriterUtils.rewriteResourceURL(filterRequest, "/bar", pathMatchers, mode))
    assertEquals("/bar?a=1&amp;b=2", URLRewriterUtils.rewriteResourceURL(filterRequest, "/bar?a=1&amp;b=2", pathMatchers, mode))
    assertEquals("/bar?a=1&amp;b=2#there", URLRewriterUtils.rewriteResourceURL(filterRequest, "/bar?a=1&amp;b=2#there", pathMatchers, mode))
    assertEquals("/doc/home-welcome?a=1&amp;b=2", URLRewriterUtils.rewriteResourceURL(filterRequest, "?a=1&amp;b=2", pathMatchers, mode))
    assertEquals("/" + version + "/ops/bar.png", URLRewriterUtils.rewriteResourceURL(filterRequest, "/ops/bar.png", pathMatchers, mode))
    assertEquals("/" + version + "/config/bar.png", URLRewriterUtils.rewriteResourceURL(filterRequest, "/config/bar.png", pathMatchers, mode))
    mode = UrlRewriteMode.AbsolutePathOrRelative
    assertEquals("https://foo.com/bar", URLRewriterUtils.rewriteResourceURL(filterRequest, "https://foo.com/bar", pathMatchers, mode))
    //        assertEquals("http://example.org/cool/service/relative/sub/path", URLRewriterUtils.rewriteResourceURL(request, "relative/sub/path", pathMatchers , mode));
    assertEquals("/myapp/bar", URLRewriterUtils.rewriteResourceURL(filterRequest, "/bar", pathMatchers, mode))
    assertEquals("/myapp/bar?a=1&amp;b=2", URLRewriterUtils.rewriteResourceURL(filterRequest, "/bar?a=1&amp;b=2", pathMatchers, mode))
    assertEquals("/myapp/bar?a=1&amp;b=2#there", URLRewriterUtils.rewriteResourceURL(filterRequest, "/bar?a=1&amp;b=2#there", pathMatchers, mode))
    assertEquals("/myapp/doc/home-welcome?a=1&amp;b=2", URLRewriterUtils.rewriteResourceURL(filterRequest, "?a=1&amp;b=2", pathMatchers, mode))
    assertEquals("/myapp/orbeon/" + version + "/ops/bar.png", URLRewriterUtils.rewriteResourceURL(filterRequest, "/ops/bar.png", pathMatchers, mode))
    assertEquals("/myapp/orbeon/" + version + "/config/bar.png", URLRewriterUtils.rewriteResourceURL(filterRequest, "/config/bar.png", pathMatchers, mode))
  }

  @Test def testDecodeResourceURI(): Unit = {
    // NOTE: Unclear case: /xforms-server/foobar. URLRewriterUtils.rewriteResourceURL() does not rewrite
    // /xforms-server/foobar as a resource URL and it is not clear why.
    val orbeonVersion = URLRewriterUtils.getOrbeonVersionForClient
    val propertiesURLs = Array("oxf:/ops/unit-tests/properties-versioned-all.xml", "oxf:/ops/unit-tests/properties-versioned-orbeon.xml")
    val platformPaths = Array("/ops/bar", "/config/bar", "/xbl/orbeon/bar", "/forms/orbeon/bar", "/apps/fr/bar", "/xforms-server")
    for (propertiesURL <- propertiesURLs) {
      // Reinitialize properties
      Properties.invalidate()
      org.orbeon.oxf.properties.Properties.init(propertiesURL)
      // Check platform paths
      for (path <- platformPaths) {
        val versionedPath = "/" + orbeonVersion + path
        // Make sure this is recognized as a platform path
        assertTrue(URLRewriterUtils.isPlatformPath(path))
        // Just decoding
        assertEquals(path, URLRewriterUtils.decodeResourceURI(versionedPath, isVersioned = true))
        // Encoding/decoding
        assertEquals(path, URLRewriterUtils.decodeResourceURI(URLRewriterUtils.rewriteResourceURL(directRequest, path, URLRewriterUtils.MATCH_ALL_PATH_MATCHERS, UrlRewriteMode.AbsolutePathNoContext), isVersioned = true))
      }
      // Check non-platform paths
      val customPaths = Array("/opsla", "/configuration", "/xbl/acme/bar", "/forms/acme/bar", "/apps/myapp/bar", "/xforms-foo")
      val decodedCustomPaths = Array("/apps/opsla", "/apps/configuration", "/xbl/acme/bar", "/forms/acme/bar", "/apps/myapp/bar", "/apps/xforms-foo")
      val appVersionOpt = URLRewriterUtils.getApplicationResourceVersion
      var i = 0
      for (path <- customPaths) {
        // Make sure this is recognized as a non-platform path
        assertFalse(URLRewriterUtils.isPlatformPath(path))
        val decodedCustomPath = decodedCustomPaths(i)
        if (appVersionOpt.isDefined) {
          // Case where there is an app version number
          val versionedPath = "/" + appVersionOpt.get + path
          // Just decoding
          assertEquals(decodedCustomPath, URLRewriterUtils.decodeResourceURI(versionedPath, isVersioned = true))
          // Encoding/decoding
          assertEquals(decodedCustomPath, URLRewriterUtils.decodeResourceURI(URLRewriterUtils.rewriteResourceURL(directRequest, path, URLRewriterUtils.MATCH_ALL_PATH_MATCHERS, UrlRewriteMode.AbsolutePathNoContext), isVersioned = true))
        }
        else {
          // Case where there is NO app version number
          // Just decoding
          assertEquals(decodedCustomPath, URLRewriterUtils.decodeResourceURI(path, isVersioned = true))
          // Encoding/decoding
          assertEquals(decodedCustomPath, URLRewriterUtils.decodeResourceURI(URLRewriterUtils.rewriteResourceURL(directRequest, path, URLRewriterUtils.MATCH_ALL_PATH_MATCHERS, UrlRewriteMode.AbsolutePathNoContext), isVersioned = true))
        }
        i += 1
      }
    }
  }

  @Test def testHRRI(): Unit = {
    // Test for spaces
    assertEquals("http://localhost:8080/myapp/a%20b", MarkupUtils.encodeHRRI("http://localhost:8080/myapp/a b", processSpace = true))
    assertEquals("http://localhost:8080/myapp/a b", MarkupUtils.encodeHRRI("http://localhost:8080/myapp/a b", processSpace = false))
    // Test for trim()
    assertEquals("http://localhost:8080/", MarkupUtils.encodeHRRI("  http://localhost:8080/  ", processSpace = true))
    // Test for other characters
    assertEquals("http://localhost:8080/myapp/%3C%3E%22%7B%7D%7C%5C%5E%60", MarkupUtils.encodeHRRI("http://localhost:8080/myapp/<>\"{}|\\^`", processSpace = true))
  }

  @Test def testResolveURI(): Unit = {
    assertEquals("http://localhost:8080/myapp/a%20b", NetUtils.resolveURI("a b", "http://localhost:8080/myapp/"))
    assertEquals("http://localhost:8080/myapp/a%20b", NetUtils.resolveURI("http://localhost:8080/myapp/a b", null))
  }
}