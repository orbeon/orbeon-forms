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

import org.orbeon.xforms.{$, AjaxClient}

object StaticUpload {

  private val SpacerImagePath = "/ops/images/xforms/spacer.gif"
  private val PhotoImageHtml  = """<i class="fa fa-fw fa-lg fa-photo"></i>"""

  // This is a bit of a hack: if we find a spacer, we insert an icon right after that if it is not yet present.
  private def findAndReplaceAllSpacersWithImages(): Unit =
    $(s"#fr-form-group .fb-upload .xforms-mediatype-image .xforms-output-output[src $$= '$SpacerImagePath']").toArray filter
      ($(_).closest(".xforms-output").find("i.fa").toArray.isEmpty) foreach
      $(PhotoImageHtml).insertAfter

  // Initial run when the form is first loaded
  findAndReplaceAllSpacersWithImages()

  // Run again after every Ajax request
  AjaxClient.ajaxResponseProcessed.add(_ => findAndReplaceAllSpacersWithImages())
}
