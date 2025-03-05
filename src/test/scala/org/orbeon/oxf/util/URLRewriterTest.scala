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

import org.orbeon.oxf.externalcontext.{ExternalContext, TestExternalContext, URLRewriterImpl, UrlRewriteMode}
import org.orbeon.oxf.pipeline.api.PipelineContext
import org.orbeon.oxf.processor.ProcessorUtils
import org.orbeon.oxf.properties.Properties
import org.orbeon.oxf.test.ResourceManagerSupport
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funspec.AnyFunSpecLike


class URLRewriterTest
  extends ResourceManagerSupport
     with AnyFunSpecLike
     with BeforeAndAfterAll {

  private val ServiceBaseUri = "http://example.org/cool/service"

  private val directRequest: ExternalContext.Request =
    new TestExternalContext(
      new PipelineContext,
      ProcessorUtils.createDocumentFromURL("oxf:/org/orbeon/oxf/util/url-rewriter-test-request.xml", null)
    ).getRequest

  private val forwardRequest: ExternalContext.Request =
    new TestExternalContext(
      new PipelineContext,
      ProcessorUtils.createDocumentFromURL("oxf:/org/orbeon/oxf/util/url-rewriter-test-request-forward.xml", null)
    ).getRequest

  private val filterRequest: ExternalContext.Request =
    new TestExternalContext(
      new PipelineContext,
      ProcessorUtils.createDocumentFromURL("oxf:/org/orbeon/oxf/util/url-rewriter-test-request-filter.xml", null)
    ).getRequest

  // For most tests of the suite, use `properties-versioned-all.xml`
  override protected def beforeAll(): Unit = {
    Properties.invalidate()
    Properties.init("oxf:/ops/unit-tests/properties-versioned-all.xml")
  }

  describe("The `rewriteServiceUrl()` with a direct request") {

    val Expected = List(
      ("https://foo.com/bar"                                  , "https://foo.com/bar"   , UrlRewriteMode.Absolute),
      ("http://example.org/cool/service/bar"                  , "/bar"                  , UrlRewriteMode.Absolute),
      ("http://example.org/cool/service/bar?a=1&amp;b=2"      , "/bar?a=1&amp;b=2"      , UrlRewriteMode.Absolute),
      ("http://example.org/cool/service/bar?a=1&amp;b=2#there", "/bar?a=1&amp;b=2#there", UrlRewriteMode.Absolute),
      ("http://example.org/cool/service?a=1&amp;b=2"          , "?a=1&amp;b=2"          , UrlRewriteMode.Absolute),
      ("https://foo.com/bar"                                  , "https://foo.com/bar"   , UrlRewriteMode.AbsoluteNoContext),
      ("http://example.org/bar"                               , "/bar"                  , UrlRewriteMode.AbsoluteNoContext),
      ("http://example.org/bar?a=1&amp;b=2"                   , "/bar?a=1&amp;b=2"      , UrlRewriteMode.AbsoluteNoContext),
      ("http://example.org/bar?a=1&amp;b=2#there"             , "/bar?a=1&amp;b=2#there", UrlRewriteMode.AbsoluteNoContext),
      ("http://example.org?a=1&amp;b=2"                       , "?a=1&amp;b=2"          , UrlRewriteMode.AbsoluteNoContext), // ideally should have a "/" between host name and query
    )

  for ((expected, url, mode) <- Expected)
    it (s"must pass for $url and $mode") {
      assert(expected == URLRewriterImpl.rewriteServiceUrl(directRequest, url, mode, ServiceBaseUri))
    }

    // assertEquals("http://example.org/cool/service/relative/sub/path", URLRewriterImpl.rewriteServiceURL(request, "relative/sub/path", mode))
    // TODO: test with oxf.url-rewriting.service.base-uri set to absolute path
    // TODO: test without oxf.url-rewriting.service.base-uri set
  }

  describe("The `rewriteURL()` with a direct request") {

    val Expected = List(
      ("https://foo.com/bar"                                      , "https://foo.com/bar"   , UrlRewriteMode.Absolute),
      ("http://localhost:8080/orbeon/bar"                         , "/bar"                  , UrlRewriteMode.Absolute),
      ("http://localhost:8080/orbeon/bar?a=1&amp;b=2"             , "/bar?a=1&amp;b=2"      , UrlRewriteMode.Absolute),
      ("http://localhost:8080/orbeon/bar?a=1&amp;b=2#there"       , "/bar?a=1&amp;b=2#there", UrlRewriteMode.Absolute),
      ("http://localhost:8080/orbeon/doc/home-welcome?a=1&amp;b=2", "?a=1&amp;b=2"          , UrlRewriteMode.Absolute),

      ("https://foo.com/bar"                                      , "https://foo.com/bar"   , UrlRewriteMode.AbsolutePath),
      ("/orbeon/bar"                                              , "/bar"                  , UrlRewriteMode.AbsolutePath),
      ("/orbeon/bar?a=1&amp;b=2"                                  , "/bar?a=1&amp;b=2"      , UrlRewriteMode.AbsolutePath),
      ("/orbeon/bar?a=1&amp;b=2#there"                            , "/bar?a=1&amp;b=2#there", UrlRewriteMode.AbsolutePath),
      ("/orbeon/doc/home-welcome?a=1&amp;b=2"                     , "?a=1&amp;b=2"          , UrlRewriteMode.AbsolutePath),

      ("https://foo.com/bar"                                      , "https://foo.com/bar"   , UrlRewriteMode.AbsolutePathNoContext),
      ("/bar"                                                     , "/bar"                  , UrlRewriteMode.AbsolutePathNoContext),
      ("/bar?a=1&amp;b=2"                                         , "/bar?a=1&amp;b=2"      , UrlRewriteMode.AbsolutePathNoContext),
      ("/bar?a=1&amp;b=2#there"                                   , "/bar?a=1&amp;b=2#there", UrlRewriteMode.AbsolutePathNoContext),
      ("/doc/home-welcome?a=1&amp;b=2"                            , "?a=1&amp;b=2"          , UrlRewriteMode.AbsolutePathNoContext),

      ("https://foo.com/bar"                                      , "https://foo.com/bar"   , UrlRewriteMode.AbsolutePathOrRelative),
      ("/orbeon/bar"                                              , "/bar"                  , UrlRewriteMode.AbsolutePathOrRelative),
      ("/orbeon/bar?a=1&amp;b=2"                                  , "/bar?a=1&amp;b=2"      , UrlRewriteMode.AbsolutePathOrRelative),
      ("/orbeon/bar?a=1&amp;b=2#there"                            , "/bar?a=1&amp;b=2#there", UrlRewriteMode.AbsolutePathOrRelative),
      ("/orbeon/doc/home-welcome?a=1&amp;b=2"                     , "?a=1&amp;b=2"          , UrlRewriteMode.AbsolutePathOrRelative),
    )

    for ((expected, url, mode) <- Expected)
      it (s"must pass for $url and $mode") {
        assert(expected == URLRewriterImpl.rewriteURL(directRequest, url, mode))
      }

    // assertEquals("http://example.org/cool/service/relative/sub/path", URLRewriterImpl.rewriteURL(request, "relative/sub/path", mode))
  }

  describe("The `rewriteResourceUrl()` with a direct request") {

    val pathMatchers = URLRewriterUtils.MATCH_ALL_PATH_MATCHERS
    val version = URLRewriterUtils.getOrbeonVersionForClient

    val Expected = List(
      ("https://foo.com/bar"                                         , "https://foo.com/bar"   , UrlRewriteMode.Absolute),
      ("http://localhost:8080/orbeon/42/bar"                         , "/bar"                  , UrlRewriteMode.Absolute),
      ("http://localhost:8080/orbeon/42/bar?a=1&amp;b=2"             , "/bar?a=1&amp;b=2"      , UrlRewriteMode.Absolute),
      ("http://localhost:8080/orbeon/42/bar?a=1&amp;b=2#there"       , "/bar?a=1&amp;b=2#there", UrlRewriteMode.Absolute),
      ("http://localhost:8080/orbeon/42/doc/home-welcome?a=1&amp;b=2", "?a=1&amp;b=2"          , UrlRewriteMode.Absolute),
      ("http://localhost:8080/orbeon/" + version + "/ops/bar.png"    , "/ops/bar.png"          , UrlRewriteMode.Absolute),
      ("http://localhost:8080/orbeon/" + version + "/config/bar.png" , "/config/bar.png"       , UrlRewriteMode.Absolute),

      ("https://foo.com/bar"                                         , "https://foo.com/bar"   , UrlRewriteMode.AbsolutePath),
      ("/orbeon/42/bar"                                              , "/bar"                  , UrlRewriteMode.AbsolutePath),
      ("/orbeon/42/bar?a=1&amp;b=2"                                  , "/bar?a=1&amp;b=2"      , UrlRewriteMode.AbsolutePath),
      ("/orbeon/42/bar?a=1&amp;b=2#there"                            , "/bar?a=1&amp;b=2#there", UrlRewriteMode.AbsolutePath),
      ("/orbeon/42/doc/home-welcome?a=1&amp;b=2"                     , "?a=1&amp;b=2"          , UrlRewriteMode.AbsolutePath),
      ("/orbeon/" + version + "/ops/bar.png"                         , "/ops/bar.png"          , UrlRewriteMode.AbsolutePath),
      ("/orbeon/" + version + "/config/bar.png"                      , "/config/bar.png"       , UrlRewriteMode.AbsolutePath),

      ("https://foo.com/bar"                                         , "https://foo.com/bar"   , UrlRewriteMode.AbsolutePathNoContext),
      ("/42/bar"                                                     , "/bar"                  , UrlRewriteMode.AbsolutePathNoContext),
      ("/42/bar?a=1&amp;b=2"                                         , "/bar?a=1&amp;b=2"      , UrlRewriteMode.AbsolutePathNoContext),
      ("/42/bar?a=1&amp;b=2#there"                                   , "/bar?a=1&amp;b=2#there", UrlRewriteMode.AbsolutePathNoContext),
      ("/42/doc/home-welcome?a=1&amp;b=2"                            , "?a=1&amp;b=2"          , UrlRewriteMode.AbsolutePathNoContext),
      ("/" + version + "/ops/bar.png"                                , "/ops/bar.png"          , UrlRewriteMode.AbsolutePathNoContext),
      ("/" + version + "/config/bar.png"                             , "/config/bar.png"       , UrlRewriteMode.AbsolutePathNoContext),

      ("https://foo.com/bar"                                         , "https://foo.com/bar"   , UrlRewriteMode.AbsolutePathOrRelative),
      ("/orbeon/42/bar"                                              , "/bar"                  , UrlRewriteMode.AbsolutePathOrRelative),
      ("/orbeon/42/bar?a=1&amp;b=2"                                  , "/bar?a=1&amp;b=2"      , UrlRewriteMode.AbsolutePathOrRelative),
      ("/orbeon/42/bar?a=1&amp;b=2#there"                            , "/bar?a=1&amp;b=2#there", UrlRewriteMode.AbsolutePathOrRelative),
      ("/orbeon/42/doc/home-welcome?a=1&amp;b=2"                     , "?a=1&amp;b=2"          , UrlRewriteMode.AbsolutePathOrRelative),
      ("/orbeon/" + version + "/ops/bar.png"                         , "/ops/bar.png"          , UrlRewriteMode.AbsolutePathOrRelative),
      ("/orbeon/" + version + "/config/bar.png"                      , "/config/bar.png"       , UrlRewriteMode.AbsolutePathOrRelative),

      ("https://foo.com/bar"                                         , "https://foo.com/bar"   , UrlRewriteMode.AbsolutePathNoPrefix),
      ("/bar"                                                        , "/bar"                  , UrlRewriteMode.AbsolutePathNoPrefix),
      ("/bar?a=1&amp;b=2"                                            , "/bar?a=1&amp;b=2"      , UrlRewriteMode.AbsolutePathNoPrefix),
      ("/bar?a=1&amp;b=2#there"                                      , "/bar?a=1&amp;b=2#there", UrlRewriteMode.AbsolutePathNoPrefix),
      ("/doc/home-welcome?a=1&amp;b=2"                               , "?a=1&amp;b=2"          , UrlRewriteMode.AbsolutePathNoPrefix),
      ("/ops/bar.png"                                                , "/ops/bar.png"          , UrlRewriteMode.AbsolutePathNoPrefix),
      ("/config/bar.png"                                             , "/config/bar.png"       , UrlRewriteMode.AbsolutePathNoPrefix),
    )

    for ((expected, url, mode) <- Expected)
      it (s"must pass for $url and $mode") {
        assert(expected == URLRewriterUtils.rewriteResourceURL(directRequest, url, pathMatchers, mode))
      }

    // ("http://example.org/cool/service/relative/sub/path", URLRewriterUtils.rewriteResourceURL(request, "relative/sub/path", pathMatchers , mode)
  }

  describe("The `rewriteServiceUrl()` with a forward request") {

    val Expected = List(
      ("https://foo.com/bar"                                  , "https://foo.com/bar"   , UrlRewriteMode.Absolute),
      ("http://example.org/cool/service/bar"                  , "/bar"                  , UrlRewriteMode.Absolute),
      ("http://example.org/cool/service/bar?a=1&amp;b=2"      , "/bar?a=1&amp;b=2"      , UrlRewriteMode.Absolute),
      ("http://example.org/cool/service/bar?a=1&amp;b=2#there", "/bar?a=1&amp;b=2#there", UrlRewriteMode.Absolute),
      ("http://example.org/cool/service?a=1&amp;b=2"          , "?a=1&amp;b=2"          , UrlRewriteMode.Absolute),

      ("https://foo.com/bar"                                  , "https://foo.com/bar"   , UrlRewriteMode.AbsoluteNoContext),
      ("http://example.org/bar"                               , "/bar"                  , UrlRewriteMode.AbsoluteNoContext),
      ("http://example.org/bar?a=1&amp;b=2"                   , "/bar?a=1&amp;b=2"      , UrlRewriteMode.AbsoluteNoContext),
      ("http://example.org/bar?a=1&amp;b=2#there"             , "/bar?a=1&amp;b=2#there", UrlRewriteMode.AbsoluteNoContext),
      ("http://example.org?a=1&amp;b=2"                       , "?a=1&amp;b=2"          , UrlRewriteMode.AbsoluteNoContext), // ideally should have a "/" between host name and query
      // TODO: test with oxf.url-rewriting.service.base-uri set to absolute path
      // TODO: test without oxf.url-rewriting.service.base-uri set
    )

    for ((expected, url, mode) <- Expected)
      it(s"must pass for $url and $mode") {
        assert(expected == URLRewriterImpl.rewriteServiceUrl(forwardRequest, url, mode, ServiceBaseUri))
      }

    // assertEquals("http://example.org/cool/service/relative/sub/path", URLRewriterImpl.rewriteServiceURL(request, "relative/sub/path", mode))
  }

  describe("The `rewriteURL()` with a forward request") {

    val Expected = List(
      ("https://foo.com/bar"                                     , "https://foo.com/bar"   , UrlRewriteMode.Absolute),
      ("http://localhost:8080/myapp/bar"                         , "/bar"                  , UrlRewriteMode.Absolute),
      ("http://localhost:8080/myapp/bar?a=1&amp;b=2"             , "/bar?a=1&amp;b=2"      , UrlRewriteMode.Absolute),
      ("http://localhost:8080/myapp/bar?a=1&amp;b=2#there"       , "/bar?a=1&amp;b=2#there", UrlRewriteMode.Absolute),
      ("http://localhost:8080/myapp/doc/home-welcome?a=1&amp;b=2", "?a=1&amp;b=2"          , UrlRewriteMode.Absolute),

      ("https://foo.com/bar"                                     , "https://foo.com/bar"   , UrlRewriteMode.AbsolutePath),
      ("/myapp/bar"                                              , "/bar"                  , UrlRewriteMode.AbsolutePath),
      ("/myapp/bar?a=1&amp;b=2"                                  , "/bar?a=1&amp;b=2"      , UrlRewriteMode.AbsolutePath),
      ("/myapp/bar?a=1&amp;b=2#there"                            , "/bar?a=1&amp;b=2#there", UrlRewriteMode.AbsolutePath),
      ("/myapp/doc/home-welcome?a=1&amp;b=2"                     , "?a=1&amp;b=2"          , UrlRewriteMode.AbsolutePath),

      ("https://foo.com/bar"                                     , "https://foo.com/bar"   , UrlRewriteMode.AbsolutePathNoContext),
      ("/bar"                                                    , "/bar"                  , UrlRewriteMode.AbsolutePathNoContext),
      ("/bar?a=1&amp;b=2"                                        , "/bar?a=1&amp;b=2"      , UrlRewriteMode.AbsolutePathNoContext),
      ("/bar?a=1&amp;b=2#there"                                  , "/bar?a=1&amp;b=2#there", UrlRewriteMode.AbsolutePathNoContext),
      ("/doc/home-welcome?a=1&amp;b=2"                           , "?a=1&amp;b=2"          , UrlRewriteMode.AbsolutePathNoContext),

      ("https://foo.com/bar"                                     , "https://foo.com/bar"   , UrlRewriteMode.AbsolutePathOrRelative),
      ("/myapp/bar"                                              , "/bar"                  , UrlRewriteMode.AbsolutePathOrRelative),
      ("/myapp/bar?a=1&amp;b=2"                                  , "/bar?a=1&amp;b=2"      , UrlRewriteMode.AbsolutePathOrRelative),
      ("/myapp/bar?a=1&amp;b=2#there"                            , "/bar?a=1&amp;b=2#there", UrlRewriteMode.AbsolutePathOrRelative),
      ("/myapp/doc/home-welcome?a=1&amp;b=2"                     , "?a=1&amp;b=2"          , UrlRewriteMode.AbsolutePathOrRelative),
    )

    for ((expected, url, mode) <- Expected)
      it(s"must pass for $url and $mode") {
        assert(expected == URLRewriterImpl.rewriteURL(forwardRequest, url, mode))
      }

    // assertEquals("http://example.org/cool/service/relative/sub/path", URLRewriterImpl.rewriteURL(request, "relative/sub/path", mode))
  }

  describe("The `rewriteResourceURL()` with a forward request") {

    val pathMatchers = URLRewriterUtils.MATCH_ALL_PATH_MATCHERS
    val version = URLRewriterUtils.getOrbeonVersionForClient

    val Expected = List(
      ("https://foo.com/bar"                                              , "https://foo.com/bar"   , UrlRewriteMode.Absolute),
      ("http://localhost:8080/myapp/bar"                                  , "/bar"                  , UrlRewriteMode.Absolute),
      ("http://localhost:8080/myapp/bar?a=1&amp;b=2"                      , "/bar?a=1&amp;b=2"      , UrlRewriteMode.Absolute),
      ("http://localhost:8080/myapp/bar?a=1&amp;b=2#there"                , "/bar?a=1&amp;b=2#there", UrlRewriteMode.Absolute),
      ("http://localhost:8080/myapp/doc/home-welcome?a=1&amp;b=2"         , "?a=1&amp;b=2"          , UrlRewriteMode.Absolute),
      ("http://localhost:8080/myapp/orbeon/" + version + "/ops/bar.png"   , "/ops/bar.png"          , UrlRewriteMode.Absolute),
      ("http://localhost:8080/myapp/orbeon/" + version + "/config/bar.png", "/config/bar.png"       , UrlRewriteMode.Absolute),

      ("https://foo.com/bar"                                              , "https://foo.com/bar"   , UrlRewriteMode.AbsolutePath),
      ("/myapp/bar"                                                       , "/bar"                  , UrlRewriteMode.AbsolutePath),
      ("/myapp/bar?a=1&amp;b=2"                                           , "/bar?a=1&amp;b=2"      , UrlRewriteMode.AbsolutePath),
      ("/myapp/bar?a=1&amp;b=2#there"                                     , "/bar?a=1&amp;b=2#there", UrlRewriteMode.AbsolutePath),
      ("/myapp/doc/home-welcome?a=1&amp;b=2"                              , "?a=1&amp;b=2"          , UrlRewriteMode.AbsolutePath),
      ("/myapp/orbeon/" + version + "/ops/bar.png"                        , "/ops/bar.png"          , UrlRewriteMode.AbsolutePath),
      ("/myapp/orbeon/" + version + "/config/bar.png"                     , "/config/bar.png"       , UrlRewriteMode.AbsolutePath),

      ("https://foo.com/bar"                                              , "https://foo.com/bar"   , UrlRewriteMode.AbsolutePathNoContext),
      ("/bar"                                                             , "/bar"                  , UrlRewriteMode.AbsolutePathNoContext),
      ("/bar?a=1&amp;b=2"                                                 , "/bar?a=1&amp;b=2"      , UrlRewriteMode.AbsolutePathNoContext),
      ("/bar?a=1&amp;b=2#there"                                           , "/bar?a=1&amp;b=2#there", UrlRewriteMode.AbsolutePathNoContext),
      ("/doc/home-welcome?a=1&amp;b=2"                                    , "?a=1&amp;b=2"          , UrlRewriteMode.AbsolutePathNoContext),
      ("/" + version + "/ops/bar.png"                                     , "/ops/bar.png"          , UrlRewriteMode.AbsolutePathNoContext),
      ("/" + version + "/config/bar.png"                                  , "/config/bar.png"       , UrlRewriteMode.AbsolutePathNoContext),

      ("https://foo.com/bar"                                              , "https://foo.com/bar"   , UrlRewriteMode.AbsolutePathOrRelative),
      ("/myapp/bar"                                                       , "/bar"                  , UrlRewriteMode.AbsolutePathOrRelative),
      ("/myapp/bar?a=1&amp;b=2"                                           , "/bar?a=1&amp;b=2"      , UrlRewriteMode.AbsolutePathOrRelative),
      ("/myapp/bar?a=1&amp;b=2#there",                                     "/bar?a=1&amp;b=2#there" , UrlRewriteMode.AbsolutePathOrRelative),
      ("/myapp/doc/home-welcome?a=1&amp;b=2"                              , "?a=1&amp;b=2"          , UrlRewriteMode.AbsolutePathOrRelative),
      ("/myapp/orbeon/" + version + "/ops/bar.png"                        , "/ops/bar.png"          , UrlRewriteMode.AbsolutePathOrRelative),
      ("/myapp/orbeon/" + version + "/config/bar.png"                     , "/config/bar.png"       , UrlRewriteMode.AbsolutePathOrRelative),
    )

    for ((expected, url, mode) <- Expected)
      it(s"must pass for $url and $mode") {
        assert(expected == URLRewriterUtils.rewriteResourceURL(forwardRequest, url, pathMatchers, mode))
      }

    // assertEquals("http://example.org/cool/service/relative/sub/path", URLRewriterUtils.rewriteResourceURL(request, "relative/sub/path", pathMatchers , mode))
  }

  describe("The `rewriteResourceURL()` with a filter request") {

    val pathMatchers = URLRewriterUtils.MATCH_ALL_PATH_MATCHERS
    val version = URLRewriterUtils.getOrbeonVersionForClient

    val Expected = List(
      ("https://foo.com/bar"                                              , "https://foo.com/bar"   , UrlRewriteMode.Absolute),
      ("http://localhost:8080/myapp/bar"                                  , "/bar"                  , UrlRewriteMode.Absolute),
      ("http://localhost:8080/myapp/bar?a=1&amp;b=2"                      , "/bar?a=1&amp;b=2"      , UrlRewriteMode.Absolute),
      ("http://localhost:8080/myapp/bar?a=1&amp;b=2#there"                , "/bar?a=1&amp;b=2#there", UrlRewriteMode.Absolute),
      ("http://localhost:8080/myapp/doc/home-welcome?a=1&amp;b=2"         , "?a=1&amp;b=2"          , UrlRewriteMode.Absolute),
      ("http://localhost:8080/myapp/orbeon/" + version + "/ops/bar.png"   , "/ops/bar.png"          , UrlRewriteMode.Absolute),
      ("http://localhost:8080/myapp/orbeon/" + version + "/config/bar.png", "/config/bar.png"       , UrlRewriteMode.Absolute),

      ("https://foo.com/bar"                                              , "https://foo.com/bar"   , UrlRewriteMode.AbsolutePath),
      ("/myapp/bar"                                                       , "/bar"                  , UrlRewriteMode.AbsolutePath),
      ("/myapp/bar?a=1&amp;b=2"                                           , "/bar?a=1&amp;b=2"      , UrlRewriteMode.AbsolutePath),
      ("/myapp/bar?a=1&amp;b=2#there"                                     , "/bar?a=1&amp;b=2#there", UrlRewriteMode.AbsolutePath),
      ("/myapp/doc/home-welcome?a=1&amp;b=2"                              , "?a=1&amp;b=2"          , UrlRewriteMode.AbsolutePath),
      ("/myapp/orbeon/" + version + "/ops/bar.png"                        , "/ops/bar.png"          , UrlRewriteMode.AbsolutePath),
      ("/myapp/orbeon/" + version + "/config/bar.png"                     , "/config/bar.png"       , UrlRewriteMode.AbsolutePath),

      ("https://foo.com/bar"                                              , "https://foo.com/bar"   , UrlRewriteMode.AbsolutePathNoContext),
      ("/bar"                                                             , "/bar"                  , UrlRewriteMode.AbsolutePathNoContext),
      ("/bar?a=1&amp;b=2"                                                 , "/bar?a=1&amp;b=2"      , UrlRewriteMode.AbsolutePathNoContext),
      ("/bar?a=1&amp;b=2#there"                                           , "/bar?a=1&amp;b=2#there", UrlRewriteMode.AbsolutePathNoContext),
      ("/doc/home-welcome?a=1&amp;b=2"                                    , "?a=1&amp;b=2"          , UrlRewriteMode.AbsolutePathNoContext),
      ("/" + version + "/ops/bar.png"                                     , "/ops/bar.png"          , UrlRewriteMode.AbsolutePathNoContext),
      ("/" + version + "/config/bar.png"                                  , "/config/bar.png"       , UrlRewriteMode.AbsolutePathNoContext),

      ("https://foo.com/bar"                                              , "https://foo.com/bar"   , UrlRewriteMode.AbsolutePathOrRelative),
      ("/myapp/bar"                                                       , "/bar"                  , UrlRewriteMode.AbsolutePathOrRelative),
      ("/myapp/bar?a=1&amp;b=2"                                           , "/bar?a=1&amp;b=2"      , UrlRewriteMode.AbsolutePathOrRelative),
      ("/myapp/bar?a=1&amp;b=2#there"                                     , "/bar?a=1&amp;b=2#there", UrlRewriteMode.AbsolutePathOrRelative),
      ("/myapp/doc/home-welcome?a=1&amp;b=2"                              , "?a=1&amp;b=2"          , UrlRewriteMode.AbsolutePathOrRelative),
      ("/myapp/orbeon/" + version + "/ops/bar.png"                        , "/ops/bar.png"          , UrlRewriteMode.AbsolutePathOrRelative),
      ("/myapp/orbeon/" + version + "/config/bar.png"                     , "/config/bar.png"       , UrlRewriteMode.AbsolutePathOrRelative),
    )

    for ((expected, url, mode) <- Expected)
      it(s"must pass for $url and $mode") {
        assert(expected == URLRewriterUtils.rewriteResourceURL(filterRequest, url, pathMatchers, mode))
      }

    // assertEquals("http://example.org/cool/service/relative/sub/path", URLRewriterUtils.rewriteResourceURL(request, "relative/sub/path", pathMatchers , mode))
  }

  describe("The `decodeResourceURI()` function") {

    // NOTE: Unclear case: `/xforms-server/foobar`. `URLRewriterUtils.rewriteResourceURL()` does not rewrite
    // `/xforms-server/foobar` as a resource URL and it is not clear why.

    val orbeonVersion = URLRewriterUtils.getOrbeonVersionForClient

    val propertiesURLs = List(
      "oxf:/ops/unit-tests/properties-versioned-all.xml",
      "oxf:/ops/unit-tests/properties-versioned-orbeon.xml",
    )

    val platformPaths = List(
      "/ops/bar",
      "/config/bar",
      "/xbl/orbeon/bar",
      "/forms/orbeon/bar",
      "/apps/fr/bar",
      "/xforms-server",
    )

    for (propertiesURL <- propertiesURLs) {

      // Reinitialize properties
      Properties.invalidate()
      Properties.init(propertiesURL)

      // Check platform paths
      for (path <- platformPaths) {
        val versionedPath = s"/$orbeonVersion$path"
        it(s"must pass platform paths for $path (${if (propertiesURL == propertiesURLs.head) "versioned all" else "versioned Orbeon"})") {
          // Make sure this is recognized as a platform path
          assert(URLRewriterUtils.isPlatformPath(path))
          // Just decoding
          assert(path == URLRewriterUtils.decodeResourceURI(versionedPath, isVersioned = true))
          // Encoding/decoding
          assert(
            path ==
              URLRewriterUtils.decodeResourceURI(
                URLRewriterUtils.rewriteResourceURL(
                  request      = directRequest,
                  urlString    = path,
                  pathMatchers = URLRewriterUtils.MATCH_ALL_PATH_MATCHERS,
                  rewriteMode  = UrlRewriteMode.AbsolutePathNoContext
                ),
                isVersioned = true
              )
          )
        }
      }

      // Check non-platform paths
      val customPaths = List(
        "/opsla"          -> "/apps/opsla",
        "/configuration"  -> "/apps/configuration",
        "/xbl/acme/bar"   -> "/xbl/acme/bar",
        "/forms/acme/bar" -> "/forms/acme/bar",
        "/apps/myapp/bar" -> "/apps/myapp/bar",
        "/xforms-foo"     -> "/apps/xforms-foo",
      )

      for ((path, decodedCustomPath) <- customPaths) {
        it(s"must pass non-platform paths for $path (${if (propertiesURL == propertiesURLs.head) "versioned all" else "versioned Orbeon"})") {
          // Make sure this is recognized as a non-platform path
          assert(! URLRewriterUtils.isPlatformPath(path))
          URLRewriterUtils.getApplicationResourceVersion match {
            case Some(appVersion) =>
              // Case where there is an app version number
              val versionedPath = s"/$appVersion$path"
              // Just decoding
              assert(decodedCustomPath == URLRewriterUtils.decodeResourceURI(versionedPath, isVersioned = true))
              // Encoding/decoding
              assert(
                decodedCustomPath ==
                  URLRewriterUtils.decodeResourceURI(
                    URLRewriterUtils.rewriteResourceURL(
                      request      = directRequest,
                      urlString    = path,
                      pathMatchers = URLRewriterUtils.MATCH_ALL_PATH_MATCHERS,
                      rewriteMode  = UrlRewriteMode.AbsolutePathNoContext
                    ),
                    isVersioned = true
                  )
              )
          case None =>
            // Case where there is NO app version number
            // Just decoding
            assert(decodedCustomPath == URLRewriterUtils.decodeResourceURI(path, isVersioned = true))
            // Encoding/decoding
            assert(
              decodedCustomPath ==
              URLRewriterUtils.decodeResourceURI(
                URLRewriterUtils.rewriteResourceURL(
                  request      = directRequest,
                  urlString    = path,
                  pathMatchers = URLRewriterUtils.MATCH_ALL_PATH_MATCHERS,
                  rewriteMode  = UrlRewriteMode.AbsolutePathNoContext
                ),
                isVersioned = true
              )
            )
          }
        }
      }
    }
  }

  describe("HRRI encoding") {
    it("must process spaces") {
      assert("http://localhost:8080/myapp/a%20b" == MarkupUtils.encodeHRRI("http://localhost:8080/myapp/a b", processSpace = true))
      assert("http://localhost:8080/myapp/a b"   == MarkupUtils.encodeHRRI("http://localhost:8080/myapp/a b", processSpace = false))
    }
    it("must trim the input") {
      assert("http://localhost:8080/" == MarkupUtils.encodeHRRI("  http://localhost:8080/  ", processSpace = true))
    }
    it("must handle other characters") {
      assert("http://localhost:8080/myapp/%3C%3E%22%7B%7D%7C%5C%5E%60" == MarkupUtils.encodeHRRI("http://localhost:8080/myapp/<>\"{}|\\^`", processSpace = true))
    }
  }

  describe("The `resolveURI()` function") {
    it("must resolve URIs") {
      assert("http://localhost:8080/myapp/a%20b" == NetUtils.resolveURI("a b", "http://localhost:8080/myapp/"))
      assert("http://localhost:8080/myapp/a%20b" == NetUtils.resolveURI("http://localhost:8080/myapp/a b", null))
    }
  }
}