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

import java.io.File
import java.net.URI

import cats.Eval
import cats.syntax.option._
import org.orbeon.io.FileUtils
import org.orbeon.oxf.util.ConnectionResult
import org.orbeon.oxf.xforms.XFormsContainingDocument
import org.orbeon.oxf.xforms.control.controls.XFormsUploadControl.hmacURL
import org.orbeon.xforms.CrossPlatformSupport

// Handle replace="xxf:binary"
class BinaryReplacer(
  submission         : XFormsModelSubmission,
  containingDocument : XFormsContainingDocument
) extends Replacer {

  private var contentUrlOpt: Option[String] = None

  def deserialize(
    connectionResult : ConnectionResult,
    p                : SubmissionParameters,
    p2               : SecondPassParameters
  ): Unit =
    contentUrlOpt = CrossPlatformSupport.inputStreamToSessionUri(
      connectionResult.content.inputStream)(
      submission.getDetailsLogger(p, p2)
    )

  def replace(
    connectionResult : ConnectionResult,
    p                : SubmissionParameters,
    p2               : SecondPassParameters
  ): Option[Eval[Unit]] = {

    def filenameFromValue  : Option[String] = None // MAYBE: `xxf:filenamevalue`
    def filenameFromHeader : Option[String] = None // MAYBE: `Content-Disposition`'s `filename`.

    def mediatypeFromValue : Option[String] = None // MAYBE: `xxf:mediatypevalue`
    def mediatypeFromHeader: Option[String] = connectionResult.content.contentType

    val filenameOpt  = filenameFromValue  orElse filenameFromHeader
    val mediatypeOpt = mediatypeFromValue orElse mediatypeFromHeader

    contentUrlOpt foreach { contentUrl =>

      val sizeFromContentOpt = FileUtils.findFileUriPath(new URI(contentUrl)) map (new File(_).length)

      val macValue = hmacURL(contentUrl, filenameOpt, mediatypeOpt, sizeFromContentOpt map (_.toString))

      TextReplacer.replaceText(
        submission         = submission,
        containingDocument = containingDocument,
        connectionResult   = connectionResult,
        p                  = p,
        value              = macValue
      )
    }

    // MAYBE `xxf:filenameref`
    // MAYBE `xxf:mediatyperef` (also with other replacers!)
    // MAYBE `xxf:sizeref`      (also with other replacers!)

    submission.sendSubmitDone(connectionResult).some
  }
}