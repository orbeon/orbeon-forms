/**
  * Copyright (C) 2019 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.submission

import org.orbeon.io.FileUtils
import org.orbeon.oxf.util.ConnectionResult
import org.orbeon.oxf.xforms.control.controls.XFormsUploadControl.hmacURL
import org.orbeon.xforms.XFormsCrossPlatformSupport

import java.net.URI


// Handle `replace="xxf:binary"`
object BinaryReplacer extends Replacer {

  type DeserializeType = Option[URI]

  def deserialize(
    submission: XFormsModelSubmission,
    cxr       : ConnectionResult,
    p         : SubmissionParameters,
    p2        : SecondPassParameters
  ): Option[URI] =
    XFormsCrossPlatformSupport.inputStreamToSessionUri(
      cxr.content.inputStream)(
      submission.getDetailsLogger(p, p2)
    )

  def replace(
    submission: XFormsModelSubmission,
    cxr       : ConnectionResult,
    p         : SubmissionParameters,
    p2        : SecondPassParameters,
    value     : DeserializeType
  ): ReplaceResult = {

    def filenameFromValue  : Option[String] = None // MAYBE: `xxf:filenamevalue`
    def filenameFromHeader : Option[String] = None // MAYBE: `Content-Disposition`'s `filename`.

    def mediatypeFromValue : Option[String] = None // MAYBE: `xxf:mediatypevalue`
    def mediatypeFromHeader: Option[String] = cxr.content.contentType

    val filenameOpt  = filenameFromValue  orElse filenameFromHeader
    val mediatypeOpt = mediatypeFromValue orElse mediatypeFromHeader

    value foreach { contentUrl =>

      val sizeFromContentOpt = FileUtils.findFileUriPath(contentUrl) map XFormsCrossPlatformSupport.tempFileSize

      val macValue = hmacURL(contentUrl.toString, filenameOpt, mediatypeOpt, sizeFromContentOpt map (_.toString))

      TextReplacer.replaceText(
        submission         = submission,
        containingDocument = submission.containingDocument,
        connectionResult   = cxr,
        p                  = p,
        value              = macValue
      )
    }

    // MAYBE `xxf:filenameref`
    // MAYBE `xxf:mediatyperef` (also with other replacers!)
    // MAYBE `xxf:sizeref`      (also with other replacers!)

    ReplaceResult.SendDone(cxr, p.tunnelProperties)
  }
}