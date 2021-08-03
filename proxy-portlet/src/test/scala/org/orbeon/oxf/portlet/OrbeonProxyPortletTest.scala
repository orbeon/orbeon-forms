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


class OrbeonProxyPortletTest extends AnyFunSpec {

  import APISupport._

  val Versioned = "0f55c3fc5685f7ed8e45e4e18f1ab8912ecb227c/"

  def allowedPaths(versioned: Boolean) = {

    val optionalPath = if (versioned) Versioned else ""

    List(
      s"/xforms-server/${optionalPath}orbeon-6894eff3d69256b3d8e076b0b689b84d810606af.css",
      s"/xforms-server/${optionalPath}orbeon-b1e8ca0abe4480447f361045b4620f13e92a0953.js",

      s"/xforms-server",
      s"/xforms-server/upload",
      s"/xforms-server/dynamic/d921062a9e04c30f098cce5659d3fcd769d161d7",

      s"/${optionalPath}apps/fr/style/bootstrap/img/glyphicons-halflings.png",
      s"/${optionalPath}apps/fr/style/images/pixelmixer/bubble_64.png",
      s"/${optionalPath}apps/fr/style/orbeon-navbar-logo.png",
      s"/${optionalPath}ops/images/xforms/calendar.png",
      s"/${optionalPath}ops/yui/assets/skins/sam/sprite.png",
      s"/${optionalPath}xbl/orbeon/california-plate/images/platetahoe_small.jpg",

      s"/$Versioned../xbl/orbeon/california-plate/images/platetahoe_small.jpg",
      s"/$optionalPath./xbl/orbeon/california-plate/images/platetahoe_small.jpg",
      s"/$Versioned../apps/fr/style/bootstrap/img/glyphicons-halflings.png",
      s"/$Versioned../${optionalPath}apps/fr/style/bootstrap/img/glyphicons-halflings.png",

      s"/fr/service/acme/sales/pdf/0f8f0ed8d1b3e24392bffc440feb882cfab53a4c/4a9573727be04cf59662f61f7c1574055c094dbc/abc.pdf",
      s"/fr/service/acme/sales/tiff/0f8f0ed8d1b3e24392bffc440feb882cfab53a4c/4a9573727be04cf59662f61f7c1574055c094dbc/abc.tiff",

      s"/fr/orbeon/bookshelf/pdf/fd5dfce7a236758e72910d333bf832f8?fr-language=fr&",
      s"/fr/orbeon/bookshelf/tiff/fd5dfce7a236758e72910d333bf832f8?fr-language=fr&"
    )
  }

  def distinctAllowedPaths =
    List(false, true) flatMap allowedPaths distinct

  def rejectedPaths(versioned: Boolean) = {

    val optionalPath = if (versioned) Versioned else ""

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

      s"/$Versioned../../xbl/orbeon/california-plate/images/platetahoe_small.jpg",

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

  def distinctRejectedPaths =
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
}
