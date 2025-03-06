/**
  * Copyright (C) 2017 Orbeon, Inc.
  *
  * This program is free software; you can redistribute it and/or modify it under the terms of the
  * GNU Lesser General Public License as published by the Free Software Foundation; either version
  *  2.1 of the License, or (at your option) any later version.
  *
  * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  * See the GNU Lesser General Public License for more details.
  *
  * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
  */
package org.orbeon.oxf.portlet

import org.orbeon.oxf.fr.embedding.APISupport
import org.scalatest.funspec.AnyFunSpec


class EmbeddingApiSupportTest extends AnyFunSpec {

  import APISupport.*

  val Versioned1 = "0f55c3fc5685f7ed8e45e4e18f1ab8912ecb227c/"
  val Versioned2 = "2024.1-SNAPSHOT/"

  def allowedPaths(versioned: Boolean) = {

    val optionalPath1 = if (versioned) Versioned1 else ""
    val optionalPath2 = if (versioned) Versioned2 else ""

    List(
      s"/xforms-server/${optionalPath1}orbeon-6894eff3d69256b3d8e076b0b689b84d810606af.css",
      s"/xforms-server/${optionalPath1}orbeon-b1e8ca0abe4480447f361045b4620f13e92a0953.js",

      s"/xforms-server",
      s"/xforms-server/upload",
      s"/xforms-server/dynamic/d921062a9e04c30f098cce5659d3fcd769d161d7",

      s"/${optionalPath1}apps/fr/style/bootstrap/img/glyphicons-halflings.png",
      s"/${optionalPath1}apps/fr/style/images/pixelmixer/bubble_64.png",
      s"/${optionalPath1}apps/fr/style/orbeon-navbar-logo.png",
      s"/${optionalPath1}ops/images/xforms/calendar.png",
      s"/${optionalPath1}ops/yui/assets/skins/sam/sprite.png",
      s"/${optionalPath1}xbl/orbeon/california-plate/images/platetahoe_small.jpg",

      s"/$Versioned1../xbl/orbeon/california-plate/images/platetahoe_small.jpg",
      s"/$optionalPath1./xbl/orbeon/california-plate/images/platetahoe_small.jpg",
      s"/$Versioned1../apps/fr/style/bootstrap/img/glyphicons-halflings.png",
      s"/$Versioned1../${optionalPath1}apps/fr/style/bootstrap/img/glyphicons-halflings.png",

      s"/fr/service/acme/sales/pdf/0f8f0ed8d1b3e24392bffc440feb882cfab53a4c/4a9573727be04cf59662f61f7c1574055c094dbc/abc.pdf",
      s"/fr/service/acme/sales/tiff/0f8f0ed8d1b3e24392bffc440feb882cfab53a4c/4a9573727be04cf59662f61f7c1574055c094dbc/abc.tiff",

      s"/fr/orbeon/bookshelf/pdf/fd5dfce7a236758e72910d333bf832f8?fr-language=fr&",
      s"/fr/orbeon/bookshelf/tiff/fd5dfce7a236758e72910d333bf832f8?fr-language=fr&",

      s"/${optionalPath2}ops/images/xforms/section-closed.png",
    )
  }

  def distinctAllowedPaths: List[String] =
    List(false, true) flatMap allowedPaths distinct

  def rejectedPaths(versioned: Boolean): List[String] = {

    val optionalPath = if (versioned) Versioned1 else ""

    List(
      s"xforms-server",
      s"/xforms-server/",
      s"/xforms-server/..",
      s"/xforms-server/../",
      s"/xforms-server/dynamic",
      s"/xforms-server/dynamic/",
      s"/xforms-server/upload/",
      s"/xforms-server/upload/foo",
      s"/xforms-server/dynamic/d921062a9e04c30f098cce5659d3fcd769d161d7/fake.png",
      s"/abc/def.png",
      s"/xforms-server/${optionalPath}orbeon-6894eff3d69256b3d8e076b0b689b84d810606af.xml",
      s"/xforms-server/${optionalPath}orbeon-6894eff3d69256b3d8e076b0b689b84d810606af.xml#fake.png",
      s"/xforms-server/${optionalPath}orbeon-6894eff3d69256b3d8e076b0b689b84d810606af.xml?a=fake.png",
      s"/xforms-server/${optionalPath}orbeon-6894eff3d69256b3d8e076b0b689b84d810606af.xml??a=fake.png",
      s"/xforms-server/${optionalPath}orbeon-6894eff3d69256b3d8e076b0b689b84d810606af.xml?a=fake.png?b=fake.jpg",
      s"/${optionalPath}apps/fr/orbeon/bookshelf/new",
      s"/apps/fr/orbeon/bookshelf/new",

      s"/$Versioned1../../xbl/orbeon/california-plate/images/platetahoe_small.jpg",

      s"/${optionalPath}xbl/orbeon/california-plate/images/platetahoe_small.xml",
      s"/${optionalPath}xbl/orbeon/california-plate/images/platetahoe_small",
      s"/${optionalPath}xbl/orbeon/california-plate/images/platetahoe_small/",

      s"/fr/service/acme/sales/pdf/a/b/c/d.pdf",
      s"/fr/service/acme/sales/tiff/a/b/c/d.tiff",
      s"/fr/service/acme/sales/pdf/a.b/c/d.pdf",
      s"/fr/service/acme/sales/tiff/a.b/c/d.tiff",

      s"/fr/service/acme/sales/other/service",
      s"/fr/service/persistence/crud/acme/sales/0f8f0ed8d1b3e24392bffc440feb882cfab53a4c/data.xml"
    )
  }

  def distinctRejectedPaths: List[String] =
    List(false, true) flatMap rejectedPaths distinct

  val FormRunnerResourcePath = DefaultFormRunnerResourcePath.r

  describe("Allowed paths") {
    for (r <- distinctAllowedPaths)
      it(s"must allow `$r`") {
        assert(sanitizeResourceId(r, FormRunnerResourcePath).isDefined)
      }
  }

  describe("Rejected paths") {
    for (r <- distinctRejectedPaths)
      it(s"must reject `$r`") {
        assert(sanitizeResourceId(r, FormRunnerResourcePath).isEmpty)
      }
  }

  describe("#7227: Proxy portlet: filter out non-configured paths") {

    val Configurations = List(
      ("home",    "acme", "sales") -> List(
        // Root pages
        "/"                                -> (false, false),
        "/fr/"                             -> (true,  false),
        // Configured form
        "/fr/acme/sales/new"               -> (true,  false),
        "/fr/acme/sales/summary"           -> (true,  false),
        "/fr/acme/sales/edit/12345"        -> (true,  false),
        "/fr/acme/sales/view/12345"        -> (true,  false),
        "/fr/acme/sales/pdf/12345"         -> (false, true),
        "/fr/service/acme/sales/pdf/12345" -> (false, false),
        "/fr/acme/sales/import"            -> (false, false),
        // Other app/form
        "/fr/acme/order/new"               -> (true,  false),
        "/fr/acme/order/summary"           -> (true,  false),
        "/fr/acme/order/edit/12345"        -> (true,  false),
        "/fr/acme/order/view/12345"        -> (true,  false),
        "/fr/acme/order/pdf/12345"         -> (false, true),
        "/fr/service/acme/order/pdf/12345" -> (false, false),
        // Other pages
        "/fr/acme/order/import"            -> (false, false),
        "/fr/admin"                        -> (false, false),
        "/fr/forms"                        -> (false, false),
      ),
      ("summary", "acme", "sales") -> List(
         // Root pages
        "/"                                -> (false, false),
        "/fr/"                             -> (false, false),
        // Configured form
        "/fr/acme/sales/new"               -> (true,  false),
        "/fr/acme/sales/summary"           -> (true,  false),
        "/fr/acme/sales/edit/12345"        -> (true,  false),
        "/fr/acme/sales/view/12345"        -> (true,  false),
        "/fr/acme/sales/pdf/12345"         -> (false, true),
        "/fr/service/acme/sales/pdf/12345" -> (false, false),
        // Other app/form
        "/fr/acme/order/new"               -> (false, false),
        "/fr/acme/order/summary"           -> (false, false),
        "/fr/acme/order/edit/12345"        -> (false, false),
        "/fr/acme/order/view/12345"        -> (false, false),
        "/fr/acme/order/pdf/12345"         -> (false, false),
        "/fr/service/acme/order/pdf/12345" -> (false, false),
        // Other pages
        "/fr/acme/sales/import"            -> (false, false),
        "/fr/admin"                        -> (false, false),
        "/fr/forms"                        -> (false, false),
      ),
      ("new",     "acme", "sales") -> List(
         // Root pages
        "/"                                -> (false, false),
        "/fr/"                             -> (false, false),
        // Configured form
        "/fr/acme/sales/new"               -> (true,  false),
        "/fr/acme/sales/summary"           -> (false, false),
        "/fr/acme/sales/edit/12345"        -> (false, false),
        "/fr/acme/sales/view/12345"        -> (false, false),
        "/fr/acme/sales/pdf/12345"         -> (false, false),
        "/fr/service/acme/sales/pdf/12345" -> (false, false),
        // Other app/form
        "/fr/acme/order/new"               -> (false, false),
        "/fr/acme/order/summary"           -> (false, false),
        "/fr/acme/order/edit/12345"        -> (false, false),
        "/fr/acme/order/view/12345"        -> (false, false),
        "/fr/acme/order/pdf/12345"         -> (false, false),
        "/fr/service/acme/order/pdf/12345" -> (false, false),
        // Other pages
        "/fr/acme/sales/import"            -> (false, false),
        "/fr/admin"                        -> (false, false),
        "/fr/forms"                        -> (false, false),
      ),
    )

    for {
      ((configuredPage, configuredAppName, configuredFormName), paths) <- Configurations
      (incomingPath, (expectedForRender, expectedForResource))         <- paths
      configuredReadonly                                               <- List(true, false)
    } locally {
      it(s"must return $expectedForRender/$expectedForResource for incoming path `$incomingPath` with configuration page=`$configuredPage`, app=`$configuredAppName`, form=`$configuredFormName`, readonly=`$configuredReadonly`") {
        assert(
          OrbeonProxyPortlet.sanitizeRenderPath(
            incomingPath       = incomingPath,
            configuredPage     = configuredPage,
            configuredAppName  = Some(configuredAppName),
            configuredFormName = Some(configuredFormName),
            configuredReadonly = configuredReadonly
          ).isDefined == expectedForRender
        )
        assert(
          OrbeonProxyPortlet.sanitizeResourcePath(
            incomingPath       = incomingPath,
            resourcePathRegex  = APISupport.DefaultFormRunnerResourcePath.r,
            configuredPage     = configuredPage,
            configuredAppName  = Some(configuredAppName),
            configuredFormName = Some(configuredFormName)
          ).isDefined == expectedForResource
        )
      }
    }
  }
}
