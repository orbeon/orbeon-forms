/**
 * Copyright (C) 2015 Orbeon, Inc.
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
package org.orbeon.builder

import org.orbeon.fr._

object StaticUpload {

  private val SpacerImagePath = "/ops/images/xforms/spacer.gif"
  private val PhotoImagePath  = "/apps/fr/style/images/silk/photo.png"

  private def findAndReplaceAllSpacersWithImages() =
    $(s"#fr-form-group .fb-upload img.xforms-output-output[src $$= '$SpacerImagePath']").toArray foreach { image ⇒
      $(image).attr("src") foreach { src ⇒
        val prefix = src.substring(0, src.indexOf(SpacerImagePath))
        $(image).attr("src", prefix + PhotoImagePath)
      }
    }

  // Initial run when the form is first loaded
  findAndReplaceAllSpacersWithImages()

  // Run again after every Ajax request
  Events.ajaxResponseProcessedEvent.subscribe(findAndReplaceAllSpacersWithImages _)
}
