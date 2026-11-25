/**
 * Copyright (C) 2011 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.processor

import cats.syntax.option.*
import org.orbeon.io.{CharsetNames, IOUtils}
import org.orbeon.oxf.externalcontext.*
import org.orbeon.oxf.http.{Headers, HttpMethod, StatusCode}
import org.orbeon.oxf.pipeline.InitUtils
import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport, ResourceManagerTestBase, TestHttpClient, XFormsSupport}
import org.orbeon.oxf.util.{ContentTypes, CoreCrossPlatformSupport, LoggerFactory}
import org.orbeon.oxf.xforms.XFormsStores
import org.orbeon.xforms.route.XFormsAssetServerRoute
import org.scalatest.funspec.AnyFunSpecLike

import scala.collection.mutable


class XFormsAssetServerTest
  extends DocumentTestBase
     with ResourceManagerSupport
     with AnyFunSpecLike
     with XFormsSupport {

  describe("The `proxyURI` function") {

    implicit val indentedLogger = ResourceManagerTestBase.newIndentedLogger

    it("must work for an absolute path") {
      withTestExternalContext { _ =>
        assert("/xforms-server/dynamic/6db2b6931155fa285c94ef20c5fa7ea190e24163" ==
          XFormsAssetServerRoute.proxyURI("/foo/bar.png", None, None, -1, Map(), Set(), _ => None).get.toString)
      }
    }

    it("must work for an absolute URL") {
      withTestExternalContext { _ =>
        assert("/xforms-server/dynamic/f4e9ff96622e4257216504cf4457468a912b3ae7" ==
          XFormsAssetServerRoute.proxyURI("http://example.org/foo/bar.png", None, None, -1, Map(), Set(), _ => None).get.toString)
      }
    }
  }

  // https://github.com/orbeon/orbeon-forms/issues/7641
  describe("Serving inline stylesheets") {

    val Logger = LoggerFactory.createLogger(classOf[XFormsAssetServerTest])

    case class Served(status: Int, headers: Map[String, List[String]], body: String)

    def serve(path: String): Served =
      InitUtils.withNewPipelineContext("XFormsAssetServerTest") { implicit pc =>

        val webAppContext = new TestWebAppContext(Logger, mutable.LinkedHashMap[String, AnyRef]())

        val request =
          LocalRequest(
            safeRequestCtx          = SafeRequestContext(webAppContext, TestHttpClient.makeBaseRequest()),
            pathQuery               = path,
            method                  = HttpMethod.GET,
            headersMaybeCapitalized = Map.empty,
            content                 = None
          )

        val response = new LocalResponse(new ServletURLRewriter(request))

        implicit val ec: ExternalContext = new LocalExternalContext(webAppContext, request, response)

        CoreCrossPlatformSupport.withExternalContext(ec) {
          XFormsAssetServerRoute.process()
        }

        Served(
          response.getStatus,
          response.capitalizedHeaders,
          IOUtils.readStreamAsStringAndClose(response.getInputStream, CharsetNames.Utf8.some)
        )
      }

    def header(served: Served, name: String): Option[String] =
      Headers.firstItemIgnoreCase(served.headers, name)

    val Css = ".t7641-serve { color: rgb(1 2 3); content: \"é\" }"

    it("must serve the stored content as CSS, with `nosniff`") {
      withTestExternalContext { _ =>

        val path   = XFormsAssetServerRoute.storeInlineStylesheet(Css)
        val served = serve(path)

        assert(served.status == StatusCode.Ok)
        assert(served.body == Css)
        assert(header(served, Headers.ContentType).contains(ContentTypes.CssContentTypeWithCharset))
        assert(header(served, "X-Content-Type-Options").contains("nosniff"))
      }
    }

    it("must return the same path for the same content") {
      withTestExternalContext { _ =>
        assert(XFormsAssetServerRoute.storeInlineStylesheet(Css) == XFormsAssetServerRoute.storeInlineStylesheet(Css))
        assert(XFormsAssetServerRoute.storeInlineStylesheet(Css) != XFormsAssetServerRoute.storeInlineStylesheet(Css + " "))
      }
    }

    it("must return 404 for an unknown digest, or a path which isn't a digest") {
      withTestExternalContext { _ =>
        List(
          "/xforms-server/form/style/0123456789abcdef0123456789abcdef01234567.css",
          "/xforms-server/form/style/0123456789ABCDEF0123456789ABCDEF01234567.css",
          "/xforms-server/form/style/style-0123456789abcdef.css",
          "/xforms-server/form/style/.css",
          "/xforms-server/form/style/0123456789abcdef.js"
        ) foreach { path =>
          val served = serve(path)
          assert(served.status == StatusCode.NotFound, path)
          assert(served.body.isEmpty, path)
        }
      }
    }

    it("must not serve the paths of an asset bundle as a stylesheet") {
      withTestExternalContext { _ =>

        val bundleKey   = "89abcdef0123456789abcdef0123456789abcdef"
        val bundlePaths = Array("/ops/javascript/orbeon/util/jquery-orbeon.js")
        XFormsStores.assetBundlesStore.put(bundleKey, bundlePaths)

        assert(serve(s"/xforms-server/form/style/$bundleKey.css").status == StatusCode.NotFound)
      }
    }

    it("must not serve a stylesheet as an asset bundle") {
      withTestExternalContext { _ =>

        val stylePath   = XFormsAssetServerRoute.storeInlineStylesheet(Css)
        val styleDigest = stylePath.stripPrefix("/xforms-server/form/style/").stripSuffix(".css")

        assert(styleDigest.matches("[0-9a-f]{40}"))
        assert(serve(stylePath).status == StatusCode.Ok)
        assert(serve(s"/xforms-server/orbeon-$styleDigest.css").status == StatusCode.NotFound)
      }
    }
  }
}
