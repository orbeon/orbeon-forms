/**
 *  Copyright (C) 2012 Orbeon, Inc.
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
package org.orbeon.oxf.portlet

import org.orbeon.oxf.portlet.liferay.LiferayURL
import org.orbeon.oxf.util.NetUtils
import org.scalatest.funspec.AnyFunSpec

import scala.jdk.CollectionConverters._

class WSRP2UtilsTest extends AnyFunSpec {

  describe("The `decodeQueryStringPortlet()` function") {

    val expected = Seq(
      """filename=data.html&orbeon.path=/fr/service/import-export/serve&uuid=""" ->
        Map("filename"    -> Seq("data.html"),
          "orbeon.path" -> Seq("/fr/service/import-export/serve"),
          "uuid"        -> Seq("")),
      """p1=v11&p2=v21&p1=v12&p2=&p2=v23&p1=""" ->
        Map("p1" -> Seq("v11", "v12", ""),
          "p2" -> Seq("v21", "", "v23"))
    )

    def decode(s: String) = NetUtils.decodeQueryStringPortlet(s).asScala.mapValues(_.toList)

    it ("must satisfy expectations") {
      for ((query, extracted) <- expected) {
        // Test with both separators
        assert(extracted === decode(query))
        assert(extracted === decode(query.replace("&", "&amp;")))
      }
    }
  }

  describe("The `moveMagicResourceId()` function") {

    val expected = Seq(
      // No magic, no p_p_resource_id
      "http://localhost:8080/my/path?p1=v11&p2=v21&p1=v12&p2=&p2=v23&p1=" ->
        "http://localhost:8080/my/path?p1=v11&p2=v21&p1=v12&p2=&p2=v23&p1=",
      // No magic, but non-matching p_p_resource_id
      "http://localhost:8080/my/path/p_p_resource_id?p1=v11&p2=v21&p1=v12&p2=&p2=v23&p1=&gotcha=p_p_resource_id" ->
        "http://localhost:8080/my/path/p_p_resource_id?p1=v11&p2=v21&p1=v12&p2=&p2=v23&p1=&gotcha=p_p_resource_id",
      // p_p_resource_id with magic in the middle
      "http://localhost:8080/my/path?p1=v11&p2=v21&p1=v12&p2=&p2=v23&p1=&p_p_resource_id=1b713b2e6d7fd45753f4b8a6270b776e.js" ->
        "http://localhost:8080/my/path?p1=v11&p2=v21&p1=v12&p_p_resource_id=1b713b2e6d7fd45753f4b8a6270b776e.js&p2=&p2=v23&p1=",
      // Extra p_p_resource_id without magic gets ignored
      "http://localhost:8080/my/path?p1=v11&p2=v21&p1=v12&p2=&p2=v23&p1=&p_p_resource_id=1b713b2e6d7fd45753f4b8a6270b776e.js" ->
        "http://localhost:8080/my/path?p1=v11&p2=v21&p1=v12&p_p_resource_id=1b713b2e6d7fd45753f4b8a6270b776e.js&p2=&p2=v23&p1=&p_p_resource_id=other-resource-id",
      // Extra p_p_resource_id after gets ignored
      "http://localhost:8080/my/path?p1=v11&p2=v21&p1=v12&p2=&p2=v23&p1=&p_p_resource_id=1b713b2e6d7fd45753f4b8a6270b776e.js" ->
        "http://localhost:8080/my/path?p1=v11&p2=v21&p1=v12&p_p_resource_id=other-resource-id&p2=&p2=v23&p1=&p_p_resource_id=1b713b2e6d7fd45753f4b8a6270b776e.js"
    )

    it ("must satisfy expectations") {
      for ((expected, initial) <- expected)
        assert(expected === LiferayURL.moveMagicResourceId(initial))
    }
  }
}
