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

import org.orbeon.io.IOUtils
import org.orbeon.oxf.resources.URLFactory
import org.orbeon.oxf.test.ResourceManagerSupport
import org.orbeon.oxf.util.ImageMetadata._
import org.scalatest.funspec.AnyFunSpecLike

class ImageMetadataTest
  extends ResourceManagerSupport
     with AnyFunSpecLike {

  describe("Reading metadata") {

    val URLPrefix = "oxf:/org/orbeon/oxf/util/hs-2010-13-a-web"

    val ExtensionsToMediatypes = List(
      "jpg" -> "image/jpeg",
      "png" -> "image/png",
      "gif" -> "image/gif",
      "bmp" -> "image/bmp"
    )

    for ((extension, mediatype) <- ExtensionsToMediatypes) {

      def openStream = URLFactory.createURL(URLPrefix + "." + extension).openStream()

      it(s"must find the `$mediatype` mediatype") {
        IOUtils.useAndClose(openStream) { is =>
          assert(findImageMediatype(is).contains(mediatype))
        }
      }

      if (extension != "bmp") { // some .bmp don't have the width/height metadata
        it(s"must find the width and height for mediatype `$mediatype`") {
          IOUtils.useAndClose(openStream) { is =>
            assert(findKnownMetadata(is, MetadataType.Width).contains(400))
          }
          IOUtils.useAndClose(openStream) { is =>
            assert(findKnownMetadata(is, MetadataType.Height).contains(368))
          }
        }
      }
    }
  }
}
