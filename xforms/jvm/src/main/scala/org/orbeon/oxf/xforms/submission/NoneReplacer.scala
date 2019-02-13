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

import org.orbeon.oxf.util.ConnectionResult
import org.orbeon.oxf.xforms.XFormsContainingDocument

/**
  * Handle replace="none".
  */
class NoneReplacer(
  val submission         : XFormsModelSubmission,
  val containingDocument : XFormsContainingDocument
) extends BaseReplacer(submission, containingDocument) {

  // NOP
  def deserialize(connectionResult: ConnectionResult, p: SubmissionParameters, p2: SecondPassParameters): Unit = ()

  // Just notify that processing is terminated by dispatching xforms-submit-done
  def replace(
    connectionResult: ConnectionResult,
    p: SubmissionParameters,
    p2: SecondPassParameters
  ): Runnable =
    submission.sendSubmitDone(connectionResult)
}