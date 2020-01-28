/**
 * Copyright (C) 2013 Orbeon, Inc.
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

import org.orbeon.oxf.test.ResourceManagerTestBase
import org.junit.Test
import org.orbeon.oxf.resources.URLFactory
import ImageMetadata._
import org.orbeon.saxon.value.Int64Value
import org.scalatestplus.junit.AssertionsForJUnit

class ImageMetadataTest extends ResourceManagerTestBase with AssertionsForJUnit {

  @Test def readMetadata(): Unit = {

    val URLPrefix = "oxf:/org/orbeon/oxf/util/hs-2010-13-a-web"

    val ExtensionsToMediatypes = Map(
      "jpg" -> "image/jpeg",
      "png" -> "image/png",
      "gif" -> "image/gif",
      "bmp" -> "image/bmp")

    for (extension <- ExtensionsToMediatypes.keys) {
      def openStream = URLFactory.createURL(URLPrefix + "." + extension).openStream()

      val mediatype = findImageMediatype(openStream).get

      assert(ExtensionsToMediatypes(extension) === mediatype)

      if (extension != "bmp") { // some .bmp don't have the width/height metadata

        val width  = findKnownMetadata(openStream, "width").get.asInstanceOf[Int64Value].longValue
        val height = findKnownMetadata(openStream, "height").get.asInstanceOf[Int64Value].longValue

        assert(400L === width)
        assert(368L === height)
      }
    }
  }
}
