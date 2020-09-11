/**
  * Copyright (C) 2010 Orbeon, Inc.
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

import org.orbeon.oxf.externalcontext.ExternalContext
import org.orbeon.oxf.util.{ConnectionResult, NetUtils}
import org.orbeon.oxf.xforms.XFormsContainingDocument

object RedirectReplacer {

  def doReplace(connectionResult: ConnectionResult, response: ExternalContext.Response): Unit = {
    SubmissionUtils.forwardResponseHeaders(connectionResult, response)
    response.setStatus(connectionResult.statusCode)
  }
}

class RedirectReplacer(submission: XFormsModelSubmission, containingDocument: XFormsContainingDocument)
  extends Replacer {

  // NOP
  def deserialize(connectionResult: ConnectionResult, p: SubmissionParameters, p2: SecondPassParameters): Unit = ()

  def replace(connectionResult: ConnectionResult, p: SubmissionParameters, p2: SecondPassParameters): Option[Runnable] = {
    val response = NetUtils.getExternalContext.getResponse
    containingDocument.setGotSubmissionRedirect()
    RedirectReplacer.doReplace(connectionResult, response)
    None
  }
}