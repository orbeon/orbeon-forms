/**
  * Copyright (C) 2009 Orbeon, Inc.
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

import org.orbeon.oxf.util.PathUtils
import org.orbeon.oxf.xforms.action.actions.XFormsLoadAction
import org.orbeon.xforms.UrlType

import scala.concurrent.Future


/**
  * Client-side GET all submission. It stores the resulting location directly into the containing document.
  */
class ClientGetAllSubmission(submission: XFormsModelSubmission)
  extends BaseSubmission(submission) {

  def getType = "get all"

  def isMatch(p: SubmissionParameters, p2: SecondPassParameters, sp: SerializationParameters): Boolean =
    p.isHandlingClientGetAll

  def connect(p: SubmissionParameters, p2: SecondPassParameters, sp: SerializationParameters): Option[ConnectResult Either Future[ConnectResult]] = {
    XFormsLoadAction.resolveStoreLoadValue(
      containingDocument           = submission.containingDocument,
      currentElem                  = Option(submission.staticSubmission.element),
      doReplace                    = true,
      value                        = PathUtils.appendQueryString(p2.actionOrResource, Option(sp.queryString) getOrElse ""),
      target                       = None,
      urlType                      = UrlType.Render,
      urlNorewrite                 = p.urlNorewrite,
      isShowProgressOpt            = Some(submission.containingDocument.findTwoPassSubmitEvents forall (_.showProgress)),
      mustHonorDeferredUpdateFlags = true
    )
    None
  }
}
